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
package com.skt.nugu.sdk.agent.display

interface AudioPlayerDisplayInterface : DisplayInterface<AudioPlayerDisplayInterface.Renderer, AudioPlayerDisplayInterface.Controller> {
    interface Renderer {
        /**
         * Used to notify the renderer when received a display directive.
         *
         * It is a good time to display template.
         *
         * If true returned, the renderer should call [displayCardRendered] after display rendered.
         *
         * @param templateId the unique identifier for the template card
         * @param templateType the template type
         * @param templateContent the content of template in structured JSON
         * @param dialogRequestId the dialog request id
         * @return true: if will render, false: otherwise
         */
        fun render(templateId: String, templateType: String, templateContent: String, dialogRequestId: String): Boolean

        /**
         * Used to notify the renderer when display should be cleared .
         *
         * the renderer should call [displayCardRendered] after display cleared.
         *
         * @param templateId the unique identifier for the template card
         * @param force true: the display should be cleared, false: recommend to clear.
         */
        fun clear(templateId: String, force: Boolean)

        /**
         * Used to notify the renderer when display should be updated. .
         *
         * @param templateId the unique identifier for the template card
         * @param templateContent the content of template in structured JSON which should be updated. The content consist of partial or full elements for templateContent of [render]
         */
        fun update(templateId: String, templateContent: String)
    }

    interface Controller
}