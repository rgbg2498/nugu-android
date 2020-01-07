package com.skt.nugu.sdk.client.agent.factory

import com.skt.nugu.sdk.core.capabilityagents.impl.*
import com.skt.nugu.sdk.core.interfaces.audio.AudioEndPointDetector
import com.skt.nugu.sdk.core.interfaces.audio.AudioProvider
import com.skt.nugu.sdk.core.interfaces.capability.asr.AbstractASRAgent
import com.skt.nugu.sdk.core.interfaces.capability.audioplayer.AbstractAudioPlayerAgent
import com.skt.nugu.sdk.core.interfaces.capability.delegation.AbstractDelegationAgent
import com.skt.nugu.sdk.core.interfaces.capability.delegation.DelegationClient
import com.skt.nugu.sdk.core.interfaces.capability.display.AbstractDisplayAgent
import com.skt.nugu.sdk.core.interfaces.capability.display.DisplayAgentInterface
import com.skt.nugu.sdk.core.interfaces.capability.extension.AbstractExtensionAgent
import com.skt.nugu.sdk.core.interfaces.capability.light.AbstractLightAgent
import com.skt.nugu.sdk.core.interfaces.capability.light.Light
import com.skt.nugu.sdk.core.interfaces.capability.location.AbstractLocationAgent
import com.skt.nugu.sdk.core.interfaces.capability.microphone.AbstractMicrophoneAgent
import com.skt.nugu.sdk.core.interfaces.capability.microphone.Microphone
import com.skt.nugu.sdk.core.interfaces.capability.movement.AbstractMovementAgent
import com.skt.nugu.sdk.core.interfaces.capability.movement.MovementController
import com.skt.nugu.sdk.core.interfaces.capability.speaker.AbstractSpeakerAgent
import com.skt.nugu.sdk.core.interfaces.capability.system.AbstractSystemAgent
import com.skt.nugu.sdk.core.interfaces.capability.system.BatteryStatusProvider
import com.skt.nugu.sdk.core.interfaces.capability.text.AbstractTextAgent
import com.skt.nugu.sdk.core.interfaces.capability.tts.AbstractTTSAgent
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextGetterInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.dialog.DialogSessionManagerInterface
import com.skt.nugu.sdk.core.interfaces.encoder.Encoder
import com.skt.nugu.sdk.core.interfaces.focus.FocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessorManagerInterface
import com.skt.nugu.sdk.core.interfaces.mediaplayer.MediaPlayerInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.playback.PlaybackRouter
import com.skt.nugu.sdk.core.interfaces.playsynchronizer.PlaySynchronizerInterface

object DefaultAgentFactory {
    val ASR = object : ASRAgentFactory {
        override fun create(
            inputProcessorManager: InputProcessorManagerInterface,
            focusManager: FocusManagerInterface,
            messageSender: MessageSender,
            contextManager: ContextManagerInterface,
            dialogSessionManager: DialogSessionManagerInterface,
            audioProvider: AudioProvider,
            audioEncoder: Encoder,
            endPointDetector: AudioEndPointDetector?,
            defaultEpdTimeoutMillis: Long,
            channelName: String
        ): AbstractASRAgent {
            return DefaultASRAgent(
                inputProcessorManager,
                focusManager,
                messageSender,
                contextManager,
                dialogSessionManager,
                audioProvider,
                audioEncoder,
                endPointDetector,
                defaultEpdTimeoutMillis,
                channelName
            )
        }
    }

    val AUDIO_PLAYER = object : AudioPlayerAgentFactory {
        override fun create(
            mediaPlayer: MediaPlayerInterface,
            messageSender: MessageSender,
            focusManager: FocusManagerInterface,
            contextManager: ContextManagerInterface,
            playbackRouter: PlaybackRouter,
            playSynchronizer: PlaySynchronizerInterface,
            channelName: String,
            displayAgent: DisplayAgentInterface?
        ): AbstractAudioPlayerAgent =
            DefaultAudioPlayerAgent(
                mediaPlayer,
                messageSender,
                focusManager,
                contextManager,
                playbackRouter,
                playSynchronizer,
                channelName
            ).apply {
                setDisplayAgent(displayAgent)
            }
    }

    val DELEGATION = object : DelegationAgentFactory {
        override fun create(
            contextGetter: ContextGetterInterface,
            messageSender: MessageSender,
            inputProcessorManager: InputProcessorManagerInterface,
            defaultClient: DelegationClient
        ): AbstractDelegationAgent =
            DefaultDelegationAgent(
                contextGetter,
                messageSender,
                inputProcessorManager,
                defaultClient
            )
    }


    val TEMPLATE = object : DisplayAgentFactory {
        override fun create(
            focusManager: FocusManagerInterface,
            contextManager: ContextManagerInterface,
            messageSender: MessageSender,
            playSynchronizer: PlaySynchronizerInterface,
            inputProcessorManager: InputProcessorManagerInterface,
            channelName: String
        ): AbstractDisplayAgent = DefaultDisplayAgent(
            focusManager,
            contextManager,
            messageSender,
            playSynchronizer,
            inputProcessorManager,
            channelName
        )
    }

    val EXTENSION = object : ExtensionAgentFactory {
        override fun create(
            contextManager: ContextManagerInterface,
            messageSender: MessageSender
        ): AbstractExtensionAgent =
            DefaultExtensionAgent(
                contextManager,
                messageSender
            )
    }

    val LIGHT = object : LightAgentFactory {
        override fun create(
            messageSender: MessageSender,
            contextManager: ContextManagerInterface,
            light: Light
        ): AbstractLightAgent = DefaultLightAgent(
            messageSender,
            contextManager,
            light
        )
    }

    val LOCATION = object : LocationAgentFactory {
        override fun create(): AbstractLocationAgent = DefaultLocationAgent()
    }

    val MICROPHONE = object : MicrophoneAgentFactory {
        override fun create(
            messageSender: MessageSender,
            contextManager: ContextManagerInterface,
            defaultMicrophone: Microphone?
        ): AbstractMicrophoneAgent =
            DefaultMicrophoneAgent(
                messageSender,
                contextManager,
                defaultMicrophone
            )
    }

    val MOVEMENT = object : MovementAgentFactory {
        override fun create(
            contextManager: ContextManagerInterface,
            messageSender: MessageSender,
            movementController: MovementController
        ): AbstractMovementAgent =
            DefaultMovementAgent(
                contextManager,
                messageSender,
                movementController
            )
    }

    val SPEAKER = object : SpeakerAgentFactory {
        override fun create(
            contextManager: ContextManagerInterface,
            messageSender: MessageSender
        ): AbstractSpeakerAgent = DefaultSpeakerAgent(
            contextManager,
            messageSender
        )
    }

    val SYSTEM = object : SystemAgentFactory {
        /**
         * Create an instance of Impl
         * initializing is performed at default initializer
         */
        override fun create(
            messageSender: MessageSender,
            connectionManager: ConnectionManagerInterface,
            contextManager: ContextManagerInterface,
            batteryStatusProvider: BatteryStatusProvider?
        ): AbstractSystemAgent =
            DefaultSystemAgent(
                messageSender,
                connectionManager,
                contextManager,
                batteryStatusProvider
            )
    }

    val TEXT = object : TextAgentFactory {
        override fun create(
            messageSender: MessageSender,
            contextManager: ContextManagerInterface,
            inputProcessorManager: InputProcessorManagerInterface
        ): AbstractTextAgent = DefaultTextAgent(
            messageSender,
            contextManager,
            inputProcessorManager
        )
    }

    val TTS = object : TTSAgentFactory {
        override fun create(
            speechPlayer: MediaPlayerInterface,
            messageSender: MessageSender,
            focusManager: FocusManagerInterface,
            contextManager: ContextManagerInterface,
            playSynchronizer: PlaySynchronizerInterface,
            inputProcessorManager: InputProcessorManagerInterface,
            channelName: String
        ): AbstractTTSAgent = DefaultTTSAgent(
            speechPlayer,
            messageSender,
            focusManager,
            contextManager,
            playSynchronizer,
            inputProcessorManager,
            channelName
        )
    }
}