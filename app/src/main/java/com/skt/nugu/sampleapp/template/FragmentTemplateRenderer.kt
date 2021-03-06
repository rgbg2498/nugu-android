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
package com.skt.nugu.sampleapp.template

import android.os.Handler
import android.os.Looper
import android.support.annotation.IdRes
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.skt.nugu.sdk.agent.display.DisplayAggregatorInterface
import com.skt.nugu.sampleapp.client.ClientManager
import com.skt.nugu.sdk.agent.common.Direction
import com.skt.nugu.sdk.agent.util.deepMerge
import java.lang.NullPointerException
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class FragmentTemplateRenderer(
    fragmentManager: FragmentManager,
    val containerIds: MutableMap<String, Int>
) :
    DisplayAggregatorInterface.Renderer {
    companion object {
        private const val TAG = "TemplateRenderer"

        fun buildFragmentTag(displayType: String, namespace: String) = "$displayType:$namespace"

    }

    private val fragmentManagerRef = WeakReference(fragmentManager)
    private val handler = Handler(Looper.getMainLooper())

    override fun render(
        templateId: String,
        templateType: String,
        templateContent: String,
        dialogRequestId: String,
        displayType: DisplayAggregatorInterface.Type
    ): Boolean {
        Log.d(
            TAG,
            "[render] templateType: $templateType, templateId: $templateId, templateContent: $templateContent, dialogRequestId: $dialogRequestId, displayType: $displayType"
        )

        val countDownLatch = CountDownLatch(1)
        val rendered = AtomicBoolean(false)
        val namespace = templateType.split(".").first()

        handler.post {
            fragmentManagerRef.get()?.let { fragmentManager ->
                val fragment =
                    fragmentManager.findFragmentByTag(displayType.name)
                if (fragment == null || fragment.isRemoving) {
                    Log.d(TAG, "[render] new fragment")
                    addFragment(
                        fragmentManager,
                        TemplateFragment.newInstance(
                            templateType,
                            templateId,
                            templateContent,
                            displayType.name
                        ),
                        namespace,
                        displayType.name
                    )
                } else {
                    if (fragment is TemplateFragment && namespace == fragment.getNamespace()) {
                        Log.d(TAG, "[render] update fragment")
                        val prevTemplateId = fragment.getTemplateId()
                        if (prevTemplateId != templateId) {
                            fragment.updateView(
                                templateType,
                                templateId,
                                templateContent,
                                displayType.name
                            )
                            ClientManager.getClient().getDisplay()
                                ?.displayCardCleared(prevTemplateId)
                            ClientManager.getClient().getDisplay()
                                ?.displayCardRendered(templateId, fragment.controller)
                        }
                    } else {
                        Log.d(TAG, "[render] not match fragment, remove and add newFragment")
                        removeFragment(fragmentManager, fragment)
                        addFragment(
                            fragmentManager,
                            TemplateFragment.newInstance(
                                templateType,
                                templateId,
                                templateContent,
                                displayType.name
                            ),
                            namespace,
                            displayType.name
                        )
                    }
                }
                rendered.set(true)
                countDownLatch.countDown()
                return@post
            }
        }

        countDownLatch.await(10, TimeUnit.SECONDS)
        return rendered.get()
    }

    override fun update(templateId: String, templateContent: String) {
        Log.d(TAG, "[update] templateId: $templateId, templateContent: $templateContent")

        handler.post {
            fragmentManagerRef.get()?.let { fragmentManager ->
                val fragment = findFragmentByTemplateId(fragmentManager, templateId)

                if (fragment is TemplateFragment) {
                    kotlin.runCatching {
                        val jsonContent =
                            com.google.gson.JsonParser.parseString(fragment.getTemplate())
                                .asJsonObject
                        val changeJsonContent =
                            com.google.gson.JsonParser.parseString(templateContent)
                                .asJsonObject

                        jsonContent.deepMerge(
                            if (changeJsonContent.has("template")) changeJsonContent.get("template").asJsonObject
                            else changeJsonContent
                        )

                        fragment.updateView(
                            fragment.getName(),
                            templateId,
                            jsonContent.toString(),
                            fragment.getDisplayType()
                        )
                    }
                }
            }
        }
    }

    override fun clear(templateId: String, force: Boolean) {
        Log.d(TAG, "[clear] templateId: $templateId, force: $force")

        handler.post {
            fragmentManagerRef.get()?.let { fragmentManager ->
                val fragment = findFragmentByTemplateId(fragmentManager, templateId)

                fragment?.let {
                    removeFragment(fragmentManager, it)
                }
            }
        }
    }

    private fun addFragment(
        fragmentManager: FragmentManager,
        fragment: Fragment,
        namespace: String,
        tag: String?
    ) {
        Log.d(TAG, "[addFragment]")
        fragmentManager.fragments.lastOrNull()?.let {
            it.setMenuVisibility(false)
            it.userVisibleHint = false
        }
        val resId = containerIds[namespace]
            ?: throw NullPointerException("containerIds is null($namespace)")

        fragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .add(resId, fragment, tag)
            .commitNowAllowingStateLoss()

        fragment.setMenuVisibility(true)
        fragment.userVisibleHint = true
    }

    private fun removeFragment(fragmentManager: FragmentManager, fragment: Fragment) {
        Log.d(TAG, "[removeFragment]")
        fragment.setMenuVisibility(false)
        fragment.userVisibleHint = false

        fragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .remove(fragment)
            .commitNowAllowingStateLoss()

        fragmentManager.fragments.lastOrNull()?.let {
            it.setMenuVisibility(true)
            it.userVisibleHint = true
        }
    }

    private fun findFragmentByTemplateId(
        fragmentManager: FragmentManager,
        templateId: String
    ): Fragment? {
        return fragmentManager.fragments.find {
            it is TemplateFragment && it.getTemplateId() == templateId
        }
    }
}