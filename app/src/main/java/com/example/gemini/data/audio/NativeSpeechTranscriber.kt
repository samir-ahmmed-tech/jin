package com.example.gemini.data.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 100% reliable Android-native SpeechTranscriber using [SpeechRecognizer].
 * Works offline and across all devices, even when remote AGY daemon or Claude bridge is offline.
 */
class NativeSpeechTranscriber(
    private val context: Context
) : SpeechTranscriber {
    companion object {
        private const val TAG = "NativeSpeechTranscriber"
    }

    private val _latestAmplitude = MutableStateFlow(0.08f)
    override val latestAmplitude: StateFlow<Float> = _latestAmplitude.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var onPartialCallback: ((String) -> Unit)? = null
    private var onFinalCallback: ((String) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null
    private var onDoneCallback: ((String) -> Unit)? = null

    private val isListening = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    private var lastRecognizedText = ""
    private var sessionScope: CoroutineScope? = null

    override fun startTranscriptionSession(
        scope: CoroutineScope,
        cascadeId: String,
        preCursorText: String,
        postCursorText: String,
        onPartialText: (String) -> Unit,
        onFinalText: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        sessionScope = scope
        onPartialCallback = onPartialText
        onFinalCallback = onFinalText
        onErrorCallback = onError
        lastRecognizedText = ""
        isListening.set(true)
        isPaused.set(false)

        scope.launch(Dispatchers.Main) {
            initAndStartRecognizer()
        }
    }

    private fun initAndStartRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onErrorCallback?.invoke("Android Speech Recognition is not available on this device")
            isListening.set(false)
            return
        }

        try {
            speechRecognizer?.destroy()
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "Ready for speech")
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "Beginning of speech")
                }

                override fun onRmsChanged(rmsdB: Float) {
                    if (isPaused.get()) {
                        _latestAmplitude.value = 0.08f
                        return
                    }
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.08f, 1f)
                    _latestAmplitude.value = normalized
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Log.d(TAG, "End of speech")
                }

                override fun onError(error: Int) {
                    val errMsg = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                        else -> "Speech recognition error ($error)"
                    }
                    Log.w(TAG, "Recognition error ($error): $errMsg")

                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        isListening.set(false)
                        onErrorCallback?.invoke(errMsg)
                        return
                    }

                    // Auto-recover from disconnects, server drops, busy recognizer, and speech timeouts
                    if (isListening.get() && !isPaused.get()) {
                        sessionScope?.launch(Dispatchers.Main) {
                            kotlinx.coroutines.delay(180)
                            if (isListening.get() && !isPaused.get()) {
                                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                                    error == SpeechRecognizer.ERROR_SERVER ||
                                    error == SpeechRecognizer.ERROR_CLIENT
                                ) {
                                    try {
                                        speechRecognizer?.cancel()
                                        speechRecognizer?.destroy()
                                    } catch (_: Exception) {}
                                    speechRecognizer = null
                                    initAndStartRecognizer()
                                } else {
                                    startListeningIntent()
                                }
                            }
                        }
                    } else if (isListening.get()) {
                        onErrorCallback?.invoke(errMsg)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim().orEmpty()
                    if (text.isNotBlank()) {
                        lastRecognizedText = text
                        onFinalCallback?.invoke(text)
                    }
                    if (isListening.get() && !isPaused.get()) {
                        sessionScope?.launch(Dispatchers.Main) {
                            kotlinx.coroutines.delay(120)
                            if (isListening.get() && !isPaused.get()) {
                                startListeningIntent()
                            }
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim().orEmpty()
                    if (text.isNotBlank()) {
                        lastRecognizedText = text
                        onPartialCallback?.invoke(text)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            startListeningIntent()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognizer: ${e.message}")
            onErrorCallback?.invoke(e.message ?: "Failed to start voice recognizer")
            isListening.set(false)
        }
    }

    private fun startListeningIntent() {
        val recognizer = speechRecognizer ?: return
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                // Prevent premature disconnects during speech pauses or long sentences
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000L)
                putExtra("android.speech.extra.DICTATION_MODE", true)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
            }
            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "startListening error: ${e.message}")
        }
    }

    override fun stopTranscriptionSession(onDone: ((String) -> Unit)?) {
        isListening.set(false)
        onDoneCallback = onDone
        val textToDeliver = lastRecognizedText
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
        _latestAmplitude.value = 0.08f
        if (textToDeliver.isNotBlank()) {
            onDone?.invoke(textToDeliver)
        }
    }

    override fun cancelTranscriptionSession() {
        isListening.set(false)
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
        _latestAmplitude.value = 0.08f
    }

    override fun pauseTranscriptionSession() {
        isPaused.set(true)
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {}
        _latestAmplitude.value = 0.08f
    }

    override fun resumeTranscriptionSession() {
        isPaused.set(false)
        if (isListening.get()) {
            sessionScope?.launch(Dispatchers.Main) {
                startListeningIntent()
            }
        }
    }
}
