package com.example.gemini.data.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.gemini.data.remote.DirectApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * 100% reliable Bilingual (Bangla + English) Hybrid Android SpeechTranscriber:
 * 1. Primary: Android [SpeechRecognizer] configured with bilingual `bn-BD` + `en-US` support so Bangla,
 *    English, and code-switched speech are recognized accurately without needing manual language switching.
 * 2. Fallback: Direct 16kHz PCM [AudioRecord] + Voice Activity Detection (VAD) + [DirectApiClient.transcribeAudioWav]
 *    when SpeechRecognizer is unavailable on custom ROMs.
 * 3. Never injects status strings (like "শুনছি...") into [onPartialCallback] or the user's text field.
 */
class NativeSpeechTranscriber(
    private val context: Context
) : SpeechTranscriber {
    companion object {
        private const val TAG = "NativeSpeechTranscriber"
        private const val SAMPLE_RATE = 16000
    }

    private val _latestAmplitude = MutableStateFlow(0.08f)
    override val latestAmplitude: StateFlow<Float> = _latestAmplitude.asStateFlow()

    private val _statusHint = MutableStateFlow("")
    val statusHint: StateFlow<String> = _statusHint.asStateFlow()

    /**
     * Language code: "auto" (Bilingual Bangla + English), "bn-BD" (Bangla), or "en-US" (English).
     */
    var preferredLanguage: String = "auto"

    private var speechRecognizer: SpeechRecognizer? = null
    private var onPartialCallback: ((String) -> Unit)? = null
    private var onFinalCallback: ((String) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null
    private var onDoneCallback: ((String) -> Unit)? = null

    private val isListening = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    private var lastRecognizedText = ""
    private var lastDeliveredFinalText = ""
    private var sessionScope: CoroutineScope? = null
    private var consecutiveHardErrors = 0
    private var audioRecordJob: Job? = null
    private var audioRecord: AudioRecord? = null

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
        lastDeliveredFinalText = ""
        consecutiveHardErrors = 0
        isListening.set(true)
        isPaused.set(false)

        scope.launch(Dispatchers.Main) {
            initAndStartRecognizer()
        }
    }

    private fun initAndStartRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "SpeechRecognizer unavailable, switching to Direct AudioRecord + AI transcription fallback")
            startAudioRecordVadFallback()
            return
        }

        try {
            speechRecognizer?.destroy()
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _statusHint.value = "Listening (Bangla & English)..."
                }

                override fun onBeginningOfSpeech() {
                    consecutiveHardErrors = 0
                    _statusHint.value = "Capturing speech..."
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
                    _statusHint.value = "Processing speech..."
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
                        12 -> "Language pack not installed"
                        13 -> "Language unavailable"
                        else -> "Speech recognition error ($error)"
                    }
                    Log.w(TAG, "Recognition error ($error): $errMsg")

                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        isListening.set(false)
                        onErrorCallback?.invoke(errMsg)
                        return
                    }

                    // If we captured partial speech before a timeout/no-match/disconnect, flush it as final!
                    if (lastRecognizedText.isNotBlank() && lastRecognizedText != lastDeliveredFinalText) {
                        val flushed = lastRecognizedText
                        lastDeliveredFinalText = flushed
                        lastRecognizedText = ""
                        onFinalCallback?.invoke(flushed)
                    }

                    // Only count genuine audio/service failures as hard errors
                    if (error == SpeechRecognizer.ERROR_AUDIO || error == 12 || error == 13) {
                        consecutiveHardErrors++
                    }

                    if (consecutiveHardErrors >= 4) {
                        Log.w(TAG, "Switching to AudioRecord + AI transcription fallback after hard error $error")
                        try {
                            speechRecognizer?.cancel()
                            speechRecognizer?.destroy()
                        } catch (_: Exception) {}
                        speechRecognizer = null
                        startAudioRecordVadFallback()
                        return
                    }

                    // Auto-recover from disconnects, server drops, busy recognizer, and speech timeouts
                    if (isListening.get() && !isPaused.get()) {
                        sessionScope?.launch(Dispatchers.Main) {
                            delay(320)
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
                    }
                }

                override fun onResults(results: Bundle?) {
                    consecutiveHardErrors = 0
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim().orEmpty()
                    if (text.isNotBlank() && text != lastDeliveredFinalText) {
                        lastRecognizedText = ""
                        lastDeliveredFinalText = text
                        onFinalCallback?.invoke(text)
                    }
                    if (isListening.get() && !isPaused.get()) {
                        sessionScope?.launch(Dispatchers.Main) {
                            delay(220)
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
                        consecutiveHardErrors = 0
                        lastRecognizedText = text
                        onPartialCallback?.invoke(text)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            startListeningIntent()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognizer: ${e.message}, using AudioRecord fallback")
            startAudioRecordVadFallback()
        }
    }

    private fun startListeningIntent() {
        val recognizer = speechRecognizer ?: return
        try {
            // Bilingual Bangla + English recognition:
            // Setting primary language to bn-BD with en-US in EXTRA_ADDITIONAL_LANGUAGES allows
            // Google's ASR engine on Android to recognize both Bangla and English seamlessly.
            val primaryLang = when (preferredLanguage) {
                "en-US" -> "en-US"
                "bn-BD" -> "bn-BD"
                else -> "bn-BD"
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, primaryLang)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, primaryLang)
                putStringArrayListExtra(
                    "android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES",
                    arrayListOf("bn-BD", "en-US")
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2200L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1600L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1200L)
            }
            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "startListening error: ${e.message}")
            startAudioRecordVadFallback()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAudioRecordVadFallback() {
        audioRecordJob?.cancel()
        audioRecordJob = sessionScope?.launch(Dispatchers.IO) {
            val minBufSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(SAMPLE_RATE / 5 * 2)

            val recorder = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufSize * 2
                )
            } catch (e: Exception) {
                Log.e(TAG, "AudioRecord creation failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    onErrorCallback?.invoke("Mic recording error: ${e.message}")
                }
                return@launch
            }

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                withContext(Dispatchers.Main) {
                    onErrorCallback?.invoke("Microphone could not be initialized")
                }
                return@launch
            }

            audioRecord = recorder
            val shortBuf = ShortArray(1600) // 100ms frame at 16kHz
            val pcmOut = ByteArrayOutputStream()
            var hasDetectedVoice = false
            var silenceFramesAfterVoice = 0
            var totalVoiceFrames = 0

            try {
                recorder.startRecording()
                _statusHint.value = "AI Direct Mic Active"

                while (isActive && isListening.get()) {
                    if (isPaused.get()) {
                        _latestAmplitude.value = 0.08f
                        delay(100)
                        continue
                    }

                    val readCount = recorder.read(shortBuf, 0, shortBuf.size)
                    if (readCount <= 0) {
                        delay(20)
                        continue
                    }

                    var sumSq = 0.0
                    for (i in 0 until readCount) {
                        val s = shortBuf[i].toDouble()
                        sumSq += s * s
                    }
                    val rms = sqrt(sumSq / readCount)
                    val db = if (rms > 1.0) (20.0 * log10(rms / 32768.0) + 45.0).toFloat() else 0f
                    val normalized = (db / 40f).coerceIn(0.08f, 1f)
                    _latestAmplitude.value = normalized

                    val isVoiceFrame = rms > 600.0
                    if (isVoiceFrame) {
                        hasDetectedVoice = true
                        silenceFramesAfterVoice = 0
                        totalVoiceFrames++
                        _statusHint.value = "Capturing voice..."
                    } else if (hasDetectedVoice) {
                        silenceFramesAfterVoice++
                    }

                    if (hasDetectedVoice) {
                        val byteBuf = ByteBuffer.allocate(readCount * 2).order(ByteOrder.LITTLE_ENDIAN)
                        for (i in 0 until readCount) {
                            byteBuf.putShort(shortBuf[i])
                        }
                        pcmOut.write(byteBuf.array())
                    }

                    if (hasDetectedVoice && (silenceFramesAfterVoice >= 12 || totalVoiceFrames >= 110)) {
                        val rawPcm = pcmOut.toByteArray()
                        pcmOut.reset()
                        hasDetectedVoice = false
                        silenceFramesAfterVoice = 0
                        val capturedFrames = totalVoiceFrames
                        totalVoiceFrames = 0

                        if (capturedFrames >= 4 && rawPcm.size > 3200) {
                            _statusHint.value = "Transcribing voice..."
                            val wavBytes = encodePcmToWav(rawPcm, SAMPLE_RATE, 1, 16)
                            val transcribed = DirectApiClient.transcribeAudioWav(context, wavBytes)
                                ?.trim()
                                .orEmpty()
                            if (transcribed.isNotBlank()) {
                                lastRecognizedText = transcribed
                                lastDeliveredFinalText = transcribed
                                withContext(Dispatchers.Main) {
                                    onFinalCallback?.invoke(transcribed)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "AudioRecord loop ended: ${e.message}")
            } finally {
                try {
                    recorder.stop()
                } catch (_: Exception) {}
                try {
                    recorder.release()
                } catch (_: Exception) {}
                audioRecord = null
            }
        }
    }

    private fun encodePcmToWav(
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = (channels * bitsPerSample / 8).toShort()
        val totalDataLen = pcmData.size + 36
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(totalDataLen)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1.toShort())
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign)
        header.putShort(bitsPerSample.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcmData.size)

        val out = ByteArray(44 + pcmData.size)
        System.arraycopy(header.array(), 0, out, 0, 44)
        System.arraycopy(pcmData, 0, out, 44, pcmData.size)
        return out
    }

    override fun pauseTranscriptionSession() {
        isPaused.set(true)
        _latestAmplitude.value = 0.08f
        sessionScope?.launch(Dispatchers.Main) {
            try {
                speechRecognizer?.stopListening()
            } catch (_: Exception) {}
        }
    }

    override fun resumeTranscriptionSession() {
        if (!isListening.get()) return
        isPaused.set(false)
        if (audioRecord == null) {
            sessionScope?.launch(Dispatchers.Main) {
                startListeningIntent()
            }
        }
    }

    override fun stopTranscriptionSession(onDone: ((String) -> Unit)?) {
        onDoneCallback = onDone
        isListening.set(false)
        isPaused.set(false)
        _latestAmplitude.value = 0.08f
        audioRecordJob?.cancel()
        audioRecordJob = null
        val pendingText = lastRecognizedText.ifBlank { lastDeliveredFinalText }
        sessionScope?.launch(Dispatchers.Main) {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
            } catch (_: Exception) {}
            speechRecognizer = null
            if (lastRecognizedText.isNotBlank() && lastRecognizedText != lastDeliveredFinalText) {
                lastDeliveredFinalText = lastRecognizedText
                onFinalCallback?.invoke(lastRecognizedText)
            }
            onDone?.invoke(pendingText)
        } ?: onDone?.invoke(pendingText)
    }

    override fun cancelTranscriptionSession() {
        isListening.set(false)
        isPaused.set(false)
        _latestAmplitude.value = 0.08f
        audioRecordJob?.cancel()
        audioRecordJob = null
        sessionScope?.launch(Dispatchers.Main) {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (_: Exception) {}
            speechRecognizer = null
        }
    }
}
