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

package com.skt.nugu.sdk.core.focus

import com.skt.nugu.sdk.core.interfaces.focus.ChannelObserver
import com.skt.nugu.sdk.core.interfaces.focus.SeamlessFocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.focus.SeamlessFocusManagerInterface.Requester
import com.skt.nugu.sdk.core.interfaces.focus.FocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.focus.FocusState
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class SeamlessFocusManager(private val focusManager: FocusManagerInterface, private val holderChannelName: String)
    : SeamlessFocusManagerInterface
    , FocusManagerInterface.OnFocusChangedListener
    , ChannelObserver {
    companion object {
        private const val TAG = "SeamlessFocusManager"
        private const val HOLDER_INTERFACE_NAME = "FocusHolder"
    }

    private val lock = ReentrantLock()
    private val shouldBeAcquireRequesterSet = HashSet<Requester>()
    private val shouldBeReleaseRequesterSet = HashSet<Requester>()
    private var focus = FocusState.NONE
    private var currentForegroundFocusInterfaceName: String? = null
    private var lastAcquiringFocusInterfaceName: String? = null

    init {
        focusManager.addListener(this)
    }

    override fun prepare(requester: Requester) {
        lock.withLock {
            val addedAtAcquire = shouldBeAcquireRequesterSet.add(requester)
            val addedAtRelease = shouldBeReleaseRequesterSet.add(requester)
            Logger.d(TAG, "[prepare] addedAtAcquire: $addedAtAcquire, addedAtRelease: $addedAtRelease, requester: $requester")
        }
    }

    override fun cancel(requester: Requester) {
        lock.withLock {
            val removedFromAcquire = shouldBeAcquireRequesterSet.remove(requester)
            val removedFromRelease = shouldBeReleaseRequesterSet.remove(requester)
            Logger.d(TAG, "[cancel] removedFromAcquire: $removedFromAcquire, removedFromRelease: $removedFromRelease, requester: $requester")
            
            if(shouldBeAcquireRequesterSet.isEmpty() && focus == FocusState.FOREGROUND) {
                focusManager.releaseChannel(holderChannelName, this)
            }
        }
    }

    override fun acquire(
        requester: Requester,
        channel: SeamlessFocusManagerInterface.Channel
    ): Boolean {
        lock.withLock {
            val removed = shouldBeAcquireRequesterSet.remove(requester)
            val result = focusManager.acquireChannel(channel.channelName, channel.channelObserver, channel.interfaceName, channel.finishListener)
            if(result) {
                lastAcquiringFocusInterfaceName = channel.interfaceName
            }
            Logger.d(TAG, "[acquire] result: $result, requester: $requester, channel: $channel, removed: $removed")
            return result
        }
    }

    override fun release(
        requester: Requester,
        channel: SeamlessFocusManagerInterface.Channel
    ) {
        lock.withLock {
            val removed = shouldBeReleaseRequesterSet.remove(requester)
            if(shouldBeAcquireRequesterSet.isNotEmpty()
                && focus == FocusState.NONE
                && channel.interfaceName == currentForegroundFocusInterfaceName
                && lastAcquiringFocusInterfaceName == null
                && removed
            ) {
                Logger.d(TAG, "[release] acquire group channel before release requester")
                focusManager.acquireChannel(holderChannelName, this, HOLDER_INTERFACE_NAME)
            }
            focusManager.releaseChannel(channel.channelName, channel.channelObserver)
            Logger.d(TAG, "[release] requester: $requester, channel, $channel, removed: $removed")

            if(shouldBeAcquireRequesterSet.isEmpty() && focus == FocusState.FOREGROUND) {
                focusManager.releaseChannel(holderChannelName, this)
            }
        }
    }

    override fun onFocusChanged(newFocus: FocusState) {
        lock.withLock {
            focus = newFocus

            Logger.d(TAG, "[onFocusChanged] newFocus: $newFocus")
            if((newFocus == FocusState.FOREGROUND && shouldBeAcquireRequesterSet.isEmpty()) || newFocus == FocusState.BACKGROUND) {
                focusManager.releaseChannel(holderChannelName, this)
            }
        }
    }

    override fun onFocusChanged(
        channelConfiguration: FocusManagerInterface.ChannelConfiguration,
        newFocus: FocusState,
        interfaceName: String
    ) {
        lock.withLock {
            if(newFocus == FocusState.FOREGROUND || newFocus == FocusState.BACKGROUND) {
                if(interfaceName == lastAcquiringFocusInterfaceName) {
                    lastAcquiringFocusInterfaceName = null
                }
            }

            if(newFocus == FocusState.FOREGROUND) {
                currentForegroundFocusInterfaceName = interfaceName
            } else {
                if(interfaceName == currentForegroundFocusInterfaceName) {
                    currentForegroundFocusInterfaceName = null
                }
            }

            Logger.d(TAG, "[onFocusChanged] currentForegroundFocusInterfaceName: $currentForegroundFocusInterfaceName")
        }
    }
}