/**
 * Copyright (c) 2019 SK Telecom Co., Ltd. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http:www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.skt.nugu.sdk.core.network

import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener
import com.skt.nugu.sdk.core.interfaces.auth.AuthDelegate
import com.skt.nugu.sdk.core.interfaces.message.*
import com.skt.nugu.sdk.core.interfaces.transport.TransportFactory
import com.skt.nugu.sdk.core.interfaces.transport.Transport
import com.skt.nugu.sdk.core.interfaces.transport.TransportListener
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import com.skt.nugu.sdk.core.interfaces.message.Call
import com.skt.nugu.sdk.core.interfaces.message.Status.Companion.withDescription
import com.skt.nugu.sdk.core.interfaces.transport.FixedStateCall

/**
 * This class which specifies the interface to manage an connection over DeviceGateway.
 */
class MessageRouter(
    private val transportFactory: TransportFactory,
    private val authDelegate: AuthDelegate
) : MessageRouterInterface, TransportListener, MessageConsumer, MessageSender.OnSendMessageListener {
    companion object {
        private const val TAG = "MessageRouter"
    }

    /** The current active transport */
    private var activeTransport: Transport? = null
    /** The handoff transport */
    private var handoffTransport: Transport? = null

    /** The observer object.*/
    private var observer: MessageRouterObserverInterface? = null

    /**
     * The listener for MessageSender
     */
    private val messageSenderListeners = CopyOnWriteArraySet<MessageSender.OnSendMessageListener>()

    /** The current connection status. */
    private var status: ConnectionStatusListener.Status = ConnectionStatusListener.Status.DISCONNECTED
    private var reason: ConnectionStatusListener.ChangedReason = ConnectionStatusListener.ChangedReason.NONE
    /**
     * lock for create transport
     */
    private val lock = ReentrantLock()

    /**
     * Begin the process of establishing an DeviceGateway connection.
     */
    override fun enable() {
        Logger.d(TAG, "[enable] called")
        val isConnectedOrConnecting = activeTransport?.isConnectedOrConnecting() ?: false
        if (!isConnectedOrConnecting) {
            setConnectionStatus(
                ConnectionStatusListener.Status.CONNECTING,
                ConnectionStatusListener.ChangedReason.CLIENT_REQUEST
            )
            createActiveTransport()
        }
    }

    /**
     * disconnect all transport
     */
    private fun disconnectAllTransport() {
        lock.withLock {
            activeTransport?.disconnect()
            handoffTransport?.disconnect()
        }
    }

    /**
     * create a new transport
     */
    private fun createActiveTransport() {
        lock.withLock {
            transportFactory.createTransport(authDelegate, this, this).apply {
                activeTransport = this
            }
        }.connect()
    }

    /**
     * Close the DeviceGateway connection.
     */
    override fun disable() {
        Logger.d(TAG, "[disable] called")
        disconnectAllTransport()
    }

    /**
     * Set the observer to this object.
     */
    override fun setObserver(observer: MessageRouterObserverInterface) {
        this.observer = observer
    }

    /**
     * Prepares the [MessageRequest] to be executed at some point in the future.
     */
    override fun newCall(request: MessageRequest, headers: Map<String, String>?): Call {
        return activeTransport?.newCall(activeTransport, request, headers, this) ?: FixedStateCall(Status(
            Status.Code.FAILED_PRECONDITION
        ).withDescription("Transport is not initialized"), request, this)
    }

    override fun addOnSendMessageListener(listener: MessageSender.OnSendMessageListener) {
        messageSenderListeners.add(listener)
    }

    override fun removeOnSendMessageListener(listener: MessageSender.OnSendMessageListener) {
        messageSenderListeners.remove(listener)
    }

    /**
     * Notify the connection observer when the status has changed.
     */
    private fun notifyObserverOnConnectionStatusChanged(
        status: ConnectionStatusListener.Status,
        reason: ConnectionStatusListener.ChangedReason
    ) {
        observer?.onConnectionStatusChanged(status, reason)
    }

    /**
     * Get the status of the connection.
     */
    override fun getConnectionStatus(): ConnectionStatusListener.Status {
        return this.status
    }

    /**
     * Set the connection state. If it changes, notify the connection observer.
     */
    private fun setConnectionStatus(
        status: ConnectionStatusListener.Status,
        reason: ConnectionStatusListener.ChangedReason
    ) {
        if (status != this.status) {
            this.status = status
            this.reason = reason
            notifyObserverOnConnectionStatusChanged(status, reason)
        }
    }

    /**
     * Notify the onConnected observer When connected.
     * @see [setConnectionStatus]
     * @param transport is interface.....  not used yet..
     */
    override fun onConnected(transport: Transport) {
        Logger.d(TAG, "[onConnected] $transport")

        // Switch from handoffTransport to activeTransport.
        lock.withLock {
            if (handoffTransport == transport) {
                activeTransport?.shutdown()
                activeTransport = handoffTransport
                handoffTransport = null
            }
        }

        setConnectionStatus(
            ConnectionStatusListener.Status.CONNECTED,
            ConnectionStatusListener.ChangedReason.SUCCESS
        )
    }

    /**
     * Notify the onDisconnected observer When disconnected.
     * @see [setConnectionStatus]
     * @param transport is Interface
     */
    override fun onDisconnected(
        transport: Transport,
        reason: ConnectionStatusListener.ChangedReason
    ) {
        lock.withLock {
            if (transport == activeTransport) {
                activeTransport?.shutdown()
                activeTransport = null
            } else if (transport == handoffTransport) {
                // handoff fails
                handoffTransport?.shutdown()
                handoffTransport = null
                activeTransport?.shutdown()
                activeTransport = null
            }
        }
        setConnectionStatus(ConnectionStatusListener.Status.DISCONNECTED, reason)
    }

    /**
     * Notify the onConnecting observer When connecting.
     * @see [setConnectionStatus]
     * @param transport is Interface
     */
    override fun onConnecting(
        transport: Transport,
        reason: ConnectionStatusListener.ChangedReason
    ) {
        setConnectionStatus(
            ConnectionStatusListener.Status.CONNECTING,
            reason
        )
    }

    override fun consumeDirectives(directives: List<DirectiveMessage>) {
        observer?.receiveDirectives(directives)
    }

    override fun consumeAttachment(attachment: AttachmentMessage) {
        observer?.receiveAttachment(attachment)
    }

    /**
     * forwarding Handoff to transport
     */
    override fun handoffConnection(
        protocol: String,
        hostname: String,
        address: String,
        port: Int,
        retryCountLimit: Int,
        connectionTimeout: Int,
        charge: String
    ) {
        lock.withLock {
            transportFactory.createTransport(authDelegate, this, this).apply {
                handoffTransport = this
            }
        }.handoffConnection(protocol, hostname, address, port, retryCountLimit, connectionTimeout, charge)
    }

    override fun resetConnection(description: String?) {
        Logger.d(TAG, "[resetConnection] description=$description")
        disconnectAllTransport()
        createActiveTransport()
    }

    /**
     * Returns a string representation of the object.
     */
    override fun toString(): String {
        val builder = StringBuilder("MessageRouter : ")
            .append("activeTransport: ").append(activeTransport)
            .append(", handoffTransport: ").append(handoffTransport)
            .append(", observer: ").append(observer)
            .append(", messageSenderListeners: ").append(messageSenderListeners.size)
            .append(", status: ").append(status)
            .append(", reason: ").append(reason)
        return builder.toString()
    }

    override fun onPreSendMessage(request: MessageRequest) {
        messageSenderListeners.forEach {
            it.onPreSendMessage(request)
        }
    }

    override fun onPostSendMessage(request: MessageRequest, status: Status) {
        messageSenderListeners.forEach {
            it.onPostSendMessage(request, status)
        }
    }

    override fun keepConnection(enabled: Boolean) {
        if(!transportFactory.keepConnection(enabled)) {
            Logger.w(TAG, "[keepConnection] enabled is not changed (enabled=$enabled)")
            return
        }
        Logger.d(TAG, "[keepConnection] enabled=$enabled")
        disconnectAllTransport()
        createActiveTransport()
    }
}