package com.carvoice.cockpit.ui.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SpeechRecognitionState {
    data object Idle : SpeechRecognitionState
    data object RequestingPermission : SpeechRecognitionState
    data object Listening : SpeechRecognitionState
    data object Processing : SpeechRecognitionState
    data class PartialResult(val text: String) : SpeechRecognitionState
    data class FinalResult(val text: String) : SpeechRecognitionState
    data class Error(val message: String, val retryable: Boolean) : SpeechRecognitionState
}

/** Android-only boundary for ASR. All public methods must be called from the main thread. */
interface SpeechRecognitionLifecycle {
    fun stopListening()
    /** Drop an in-flight utterance when its owning screen/session disappears. */
    fun cancelListening()
    fun release()
}

interface SpeechRecognitionController : SpeechRecognitionLifecycle {
    val state: StateFlow<SpeechRecognitionState>
    fun startListening()
    fun onPermissionResult(granted: Boolean)
    override fun stopListening()
    /** Acknowledge a terminal result/error after the UI has consumed it. */
    fun reset()
    override fun release()
}

object NoOpSpeechRecognitionLifecycle : SpeechRecognitionLifecycle {
    override fun stopListening() = Unit
    override fun cancelListening() = Unit
    override fun release() = Unit
}

class AndroidSpeechRecognitionController(context: Context) : SpeechRecognitionController {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow<SpeechRecognitionState>(SpeechRecognitionState.Idle)
    override val state: StateFlow<SpeechRecognitionState> = _state.asStateFlow()
    private var recognizer: SpeechRecognizer? = null
    private var released = false
    private var discardCallbacks = false

    override fun startListening() {
        if (released) return
        if (_state.value is SpeechRecognitionState.Listening || _state.value is SpeechRecognitionState.Processing) return
        _state.value = SpeechRecognitionState.RequestingPermission
    }

    override fun onPermissionResult(granted: Boolean) {
        if (released) return
        if (!granted) {
            _state.value = SpeechRecognitionState.Error("未获得麦克风权限，无法使用语音输入", retryable = true)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            _state.value = SpeechRecognitionState.Error("此设备未提供语音识别服务，请安装或启用系统语音服务后重试。", retryable = true)
            return
        }
        discardCallbacks = false
        val speechRecognizer = recognizer ?: SpeechRecognizer.createSpeechRecognizer(appContext).also {
            recognizer = it
            it.setRecognitionListener(listener)
        }
        _state.value = SpeechRecognitionState.Listening
        speechRecognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            },
        )
    }

    override fun stopListening() {
        if (released) return
        if (_state.value is SpeechRecognitionState.Listening || _state.value is SpeechRecognitionState.PartialResult) {
            _state.value = SpeechRecognitionState.Processing
            recognizer?.stopListening()
        }
    }

    override fun cancelListening() {
        if (released) return
        discardCallbacks = true
        recognizer?.cancel()
        _state.value = SpeechRecognitionState.Idle
    }

    override fun reset() {
        if (!released) _state.value = SpeechRecognitionState.Idle
    }

    override fun release() {
        if (released) return
        released = true
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        _state.value = SpeechRecognitionState.Idle
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() { _state.value = SpeechRecognitionState.Processing }
        override fun onPartialResults(partialResults: Bundle?) {
            if (discardCallbacks) return
            partialResults.bestText()?.takeIf(String::isNotBlank)?.let {
                _state.value = SpeechRecognitionState.PartialResult(it)
            }
        }
        override fun onResults(results: Bundle?) {
            if (discardCallbacks) return
            val text = results.bestText().orEmpty()
            _state.value = if (text.isBlank()) {
                SpeechRecognitionState.Error("未识别到语音内容，请再试一次。", retryable = true)
            } else {
                SpeechRecognitionState.FinalResult(text)
            }
        }
        override fun onError(error: Int) {
            if (discardCallbacks) return
            val (message, retryable) = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "未识别到匹配的语音，请再试一次。" to true
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "等待语音超时，请点击麦克风后再说话。" to true
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "语音识别网络不可用，请检查网络后重试。" to true
                SpeechRecognizer.ERROR_SERVER -> "语音识别服务暂时不可用，请稍后重试。" to true
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "语音识别正在忙，请稍后重试。" to true
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "未获得麦克风权限，无法使用语音输入" to true
                else -> "语音识别失败，请重试。" to true
            }
            _state.value = SpeechRecognitionState.Error(message, retryable)
        }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}

private fun Bundle?.bestText(): String? =
    this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
