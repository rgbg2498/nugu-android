/**
 * Copyright (c) 2020 SK Telecom Co., Ltd. All rights reserved.
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
package com.skt.nugu.sdk.client.port.transport.grpc

import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.Status
import com.skt.nugu.sdk.core.interfaces.message.Status.Companion.withDescription
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.interfaces.transport.Transport
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.*
import com.skt.nugu.sdk.core.interfaces.message.Call as MessageCall

internal class GrpcCall(
    val timeoutScheduler: ScheduledExecutorService,
    val transport: Transport?,
    val request: MessageRequest,
    val headers: Map<String, String>?,
    listener: MessageSender.OnSendMessageListener
) :
    MessageCall {
    private val timeoutFutures = ConcurrentHashMap<Int, ScheduledFuture<*>>()
    private var executed = false
    private var canceled = false
    private var callback: MessageSender.Callback? = null
    private var listener: MessageSender.OnSendMessageListener? = listener
    private var callTimeoutMillis = 1000 * 10L
    private var noAck = false
    private var invokeStartEvent = true

    companion object{
        private const val TAG = "GrpcCall"
    }

    override fun request() = request
    override fun headers() = headers

    private val hashCode : Int by lazy {
        when(request) {
            is EventMessageRequest -> request.hashCode()
            else -> -1
        }
    }

    override fun enqueue(callback: MessageSender.Callback?): Boolean {
        synchronized(this) {
            if (executed) {
                callback?.onFailure(request(),Status(
                    Status.Code.FAILED_PRECONDITION
                ).withDescription("Already Executed"))
                return false
            }
            if (canceled) {
                callback?.onFailure(request(),Status(
                    Status.Code.CANCELLED
                ).withDescription("Already canceled"))
                return false
            }
            executed = true
        }
        this.callback = callback

        scheduleTimeout()

        if (transport?.send(this) != true) {
            onComplete(Status.FAILED_PRECONDITION.withDescription("send() called while not connected"))
            return false
        }

        if(noAck) {
            onComplete(Status.OK)
        }
        return true
    }

    private fun scheduleTimeout() {
        if(hashCode != -1) {
            timeoutFutures[hashCode] = timeoutScheduler.schedule({
                onComplete(Status.DEADLINE_EXCEEDED)
            }, callTimeoutMillis, TimeUnit.MILLISECONDS)
        }
    }

    private fun cancelScheduledTimeout() {
        timeoutFutures.remove(hashCode)?.cancel(true)
    }

    override fun isCanceled() = synchronized(this) {
        canceled
    }

    override fun cancel() {
        synchronized(this) {
            if (canceled) {
                Logger.d(TAG, "already cancel")
                return
            } // Already canceled.
            canceled = true
        }
        Logger.d(TAG, "cancel")
        onComplete(Status.CANCELLED.withDescription("Client Closed Request"))
    }

    override fun execute(): Status {
        synchronized(this) {
            if (executed) {
                return Status(
                    Status.Code.FAILED_PRECONDITION
                ).withDescription("Already Executed")
            }
            if (canceled) {
                return Status(
                    Status.Code.CANCELLED
                ).withDescription("Already canceled")
            }
            executed = true
        }
        val latch = CountDownLatch(1)
        var result = Status.DEADLINE_EXCEEDED

        this.callback = object : MessageSender.Callback {
            override fun onFailure(request: MessageRequest, status: Status) {
                result = status
                latch.countDown()
            }

            override fun onSuccess(request: MessageRequest) {
                result = Status.OK
                latch.countDown()
            }

            override fun onResponseStart(request: MessageRequest) {
            }
        }

        if (transport?.send(this) != true) {
            onComplete(Status.FAILED_PRECONDITION.withDescription("send() called while not connected"))
        }

        if(noAck) {
            onComplete(Status.OK)
        }

        try {
            latch.await(callTimeoutMillis, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        return result
    }

    override fun noAck(): MessageCall {
        noAck = true
        return this
    }

    override fun onStart() {
        if(invokeStartEvent) {
            callback?.onResponseStart(request())
            invokeStartEvent = false
        }
    }

    override fun onComplete(status: Status) {
        cancelScheduledTimeout()

        // Notify Callback
        if (status.isOk()) {
            callback?.onSuccess(request())
        } else {
            callback?.onFailure(request(), status)
        }
        callback = null

        // Notify Listener
        listener?.onPostSendMessage(request(), status)
        listener = null
    }

    override fun callTimeout(millis: Long): MessageCall {
        callTimeoutMillis = millis
        return this
    }

    override fun callTimeout() = callTimeoutMillis
}