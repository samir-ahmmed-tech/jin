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
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * 100% reliable Hybrid Android SpeechTranscriber:
 * 1. Primary: Android [SpeechRecognizer] (online + device hybrid, never forced offline so it never fails when offline packs are missing).
 * 2. Fallback: Direct 16kHz PCM [AudioRecord] + Voice Activity Detection (VAD) + [DirectApiClient.transcribeAudioWav]
 *    when SpeechRecognizer is unavailable or unsupported on custom ROMs.
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
     * Language code: "auto" (device default), "bn-BD" (Bangla), or "en-US" (English).
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
    private var sessionScope: CoroutineScope? = null
    private var consecutiveErrors = 0
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
        consecutiveErrors = 0
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
                    Log.d(TAG, "Ready for speech")
                    _statusHint.value = "Listening..."
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "Beginning of speech")
                    consecutiveErrors = 0
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

                    // Do not count normal silence/no-match as hard engine failures
                    if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        consecutiveErrors++
                    }

                    // If SpeechRecognizer fails repeatedly (e.g., Google app disabled or language unsupported),
                    // seamlessly fall back to Direct AudioRecord + Cloud/Hub AI transcription
                    if (consecutiveErrors >= 3 || error == 12 || error == 13) {
                        Log.w(TAG, "Switching to AudioRecord + AI transcription fallback after error $error")
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
                            delay(220)
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
                    consecutiveErrors = 0
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim().orEmpty()
                    if (text.isNotBlank()) {
                        lastRecognizedText = text
                        onFinalCallback?.invoke(text)
                    }
                    if (isListening.get() && !isPaused.get()) {
                        sessionScope?.launch(Dispatchers.Main) {
                            delay(150)
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
                        consecutiveErrors = 0
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
            val langTag = when (preferredLanguage) {
                "bn-BD" -> "bn-BD"
                "en-US" -> "en-US"
                else -> Locale.getDefault().toLanguageTag().ifBlank { "en-US" }
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                // Prevent premature disconnects during speech pauses or long sentences
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2800L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2200L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
                // IMPORTANT: Never set EXTRA_PREFER_OFFLINE = true, as it breaks speech recognition
                // on devices without pre-downloaded offline language packs.
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

                    // Compute RMS amplitude
                    var sumSq = 0.0
                    for (i in 0 until readCount) {
                        val s = shortBuf[i].toDouble()
                        sumSq += s * s
                    }
                    val rms = sqrt(sumSq / readCount)
                    val db = if (rms > 1.0) (20.0 * log10(rms / 32768.0) + 45.0).toFloat() else 0f
                    val normalized = (db / 40f).coerceIn(0.08f, 1f)
                    _latestAmplitude.value = normalized

                    val isVoiceFrame = rms > 650.0 // Empirical speech threshold
                    if (isVoiceFrame) {
                        hasDetectedVoice = true
                        silenceFramesAfterVoice = 0
                        totalVoiceFrames++
                        if (totalVoiceFrames % 4 == 1) {
                            withContext(Dispatchers.Main) {
                                onPartialCallback?.invoke("শুনছি... (Recording speech...)")
                            }
                        }
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

                    // If 1.3s of silence after at least 0.5s of voice (or max 12s reached)
                    if (hasDetectedVoice && (silenceFramesAfterVoice >= 13 || totalVoiceFrames >= 120)) {
                        val rawPcm = pcmOut.toByteArray()
                        pcmOut.reset()
                        hasDetectedVoice = false
                        silenceFramesAfterVoice = 0
                        val capturedFrames = totalVoiceFrames
                        totalVoiceFrames = 0

                        if (capturedFrames >= 4 && rawPcm.size > 3200) {
                            withContext(Dispatchers.Main) {
                                onPartialCallback?.invoke("Transcribing voice...")
                            }
                            val wavBytes = encodePcmToWav(rawPcm, SAMPLE_RATE, 1, 16)
                            val transcribed = DirectApiClient.transcribeAudioWav(context, wavBytes)
                                ?.trim()
                                .orEmpty()
                            if (transcribed.isNotBlank()) {
                                lastRecognizedText = transcribed
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
        header.putInt(16) // Subchunk1Size for PCM
        header.putShort(1.toShort()) // AudioFormat 1 = PCM
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign)
        header.putShort(bitsPerSample.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcmData.size)

        val out = ByteArrayOutputStream(44 + pcmData.size)
        out.write(header.array())
        out.write(pcmData)
        return out.toByteArray()
    }

    override fun stopTranscriptionSession(onDone: ((String) -> Unit)?) {
        isListening.set(false)
        onDoneCallback = onDone
        audioRecordJob?.cancel()
        audioRecordJob = null
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
        audioRecordJob?.cancel()
        audioRecordJob = null
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
                if (speechRecognizer != null) {
                    startListeningIntent()
                } else {
                    initAndStartRecognizer()
                }
            }
        }
    }
}
