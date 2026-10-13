package com.example.gemini.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Dual-Engine Neural Text-to-Speech manager for JIN AI Chat & Jarvis/Maya Live Voice Assistant.
 * 1. Primary: Streams high-definition Neural TTS MP3 audio (`tl=bn` for Bangla, `tl=en` for English)
 *    and plays via Android [MediaPlayer] so the AI speaks with a natural, clear human voice on 100% of devices,
 *    even when offline Bangla TTS packs are not installed on the phone.
 * 2. Fallback: Automatically falls back to Android's native [TextToSpeech] engine when offline.
 */
object ChatTtsHelper {
    private const val TAG = "ChatTtsHelper"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeSpeechJob: Job? = null
    private var activeMediaPlayer: MediaPlayer? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private var tts: TextToSpeech? = null
    @Volatile
    private var isInitialized = false
    @Volatile
    private var isInitializing = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _currentMessageId = MutableStateFlow<String?>(null)
    val currentMessageId: StateFlow<String?> = _currentMessageId.asStateFlow()

    fun init(context: Context) {
        if (isInitialized && tts != null) return
        if (isInitializing) return
        isInitializing = true
        val appContext = context.applicationContext
        try {
            tts = TextToSpeech(appContext) { status ->
                isInitializing = false
                if (status == TextToSpeech.SUCCESS) {
                    isInitialized = true
                    try {
                        tts?.language = Locale.getDefault()
                        tts?.setSpeechRate(1.03f)
                        tts?.setPitch(1.0f)
                    } catch (e: Exception) {
                        Log.w(TAG, "TTS default language config warning: ${e.message}")
                    }
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _isSpeaking.value = true
                        }

                        override fun onDone(utteranceId: String?) {
                            _isSpeaking.value = false
                            _currentMessageId.value = null
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            _isSpeaking.value = false
                            _currentMessageId.value = null
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            _isSpeaking.value = false
                            _currentMessageId.value = null
                        }
                    })
                } else {
                    isInitialized = false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "TextToSpeech creation exception: ${e.message}")
            isInitializing = false
            isInitialized = false
        }
    }

    fun speak(context: Context, messageId: String, text: String) {
        val appContext = context.applicationContext
        val cleaned = cleanMarkdownForSpeech(text).take(1600)
        if (cleaned.isBlank()) {
            _isSpeaking.value = false
            _currentMessageId.value = null
            return
        }

        stop()
        init(appContext)

        _currentMessageId.value = messageId
        _isSpeaking.value = true

        activeSpeechJob = scope.launch {
            try {
                val chunks = splitIntoSpeechChunks(cleaned, maxChunkLen = 175)
                var playedAnyNeural = false

                for (chunk in chunks) {
                    if (!isActive || !_isSpeaking.value) break
                    val lang = if (chunk.any { it in '\u0980'..'\u09FF' }) "bn" else "en"
                    val mp3File = fetchNeuralTtsMp3(appContext, chunk, lang)
                    if (mp3File != null && mp3File.exists() && mp3File.length() > 128L) {
                        playedAnyNeural = true
                        playMp3FileSuspend(mp3File)
                        mp3File.delete()
                    } else {
                        break
                    }
                }

                if (!playedAnyNeural && isActive && _isSpeaking.value) {
                    withContext(Dispatchers.Main) {
                        executeNativeSpeakFallback(messageId, cleaned)
                    }
                } else {
                    _isSpeaking.value = false
                    _currentMessageId.value = null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Neural TTS stream error, falling back to native TTS: ${e.message}")
                withContext(Dispatchers.Main) {
                    executeNativeSpeakFallback(messageId, cleaned)
                }
            }
        }
    }

    private fun fetchNeuralTtsMp3(context: Context, chunk: String, lang: String): File? {
        return try {
            val encoded = URLEncoder.encode(chunk, "UTF-8")
            val url = "https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl=$lang&q=$encoded"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val outFile = File(context.cacheDir, "jin_tts_${System.nanoTime()}.mp3")
                resp.body?.byteStream()?.use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
                outFile
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchNeuralTtsMp3 failed: ${e.message}")
            null
        }
    }

    private suspend fun playMp3FileSuspend(file: File): Boolean = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            try {
                activeMediaPlayer?.release()
                val mp = MediaPlayer()
                activeMediaPlayer = mp
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                mp.setDataSource(file.absolutePath)
                mp.setOnCompletionListener { player ->
                    try { player.release() } catch (_: Exception) {}
                    if (activeMediaPlayer === player) activeMediaPlayer = null
                    if (cont.isActive) cont.resume(true)
                }
                mp.setOnErrorListener { player, _, _ ->
                    try { player.release() } catch (_: Exception) {}
                    if (activeMediaPlayer === player) activeMediaPlayer = null
                    if (cont.isActive) cont.resume(false)
                    true
                }
                mp.prepare()
                mp.start()

                cont.invokeOnCancellation {
                    try {
                        mp.stop()
                    } catch (_: Exception) {}
                    try {
                        mp.release()
                    } catch (_: Exception) {}
                    if (activeMediaPlayer === mp) activeMediaPlayer = null
                }
            } catch (e: Exception) {
                Log.w(TAG, "MediaPlayer playback error: ${e.message}")
                if (cont.isActive) cont.resume(false)
            }
        }
    }

    private fun splitIntoSpeechChunks(text: String, maxChunkLen: Int): List<String> {
        val result = mutableListOf<String>()
        val sentences = text.split(Regex("(?<=[।.!?\\n])\\s+"))
        var current = StringBuilder()
        for (sentence in sentences) {
            val trimmed = sentence.trim()
            if (trimmed.isEmpty()) continue
            if (current.length + trimmed.length + 1 <= maxChunkLen) {
                if (current.isNotEmpty()) current.append(" ")
                current.append(trimmed)
            } else {
                if (current.isNotEmpty()) {
                    result.add(current.toString())
                    current = StringBuilder()
                }
                if (trimmed.length <= maxChunkLen) {
                    current.append(trimmed)
                } else {
                    var idx = 0
                    while (idx < trimmed.length) {
                        val end = (idx + maxChunkLen).coerceAtMost(trimmed.length)
                        result.add(trimmed.substring(idx, end).trim())
                        idx = end
                    }
                }
            }
        }
        if (current.isNotEmpty()) {
            result.add(current.toString())
        }
        return result
    }

    private fun executeNativeSpeakFallback(messageId: String, cleaned: String) {
        val engine = tts
        if (engine == null || !isInitialized) {
            _isSpeaking.value = false
            _currentMessageId.value = null
            return
        }

        try {
            engine.stop()
            val containsBangla = cleaned.any { it in '\u0980'..'\u09FF' }
            if (containsBangla) {
                val bnBd = Locale("bn", "BD")
                val bnIn = Locale("bn", "IN")
                if (engine.isLanguageAvailable(bnBd) >= TextToSpeech.LANG_AVAILABLE) {
                    engine.language = bnBd
                } else if (engine.isLanguageAvailable(bnIn) >= TextToSpeech.LANG_AVAILABLE) {
                    engine.language = bnIn
                }
            } else {
                val def = Locale.getDefault()
                if (engine.isLanguageAvailable(def) >= TextToSpeech.LANG_AVAILABLE) {
                    engine.language = def
                } else {
                    engine.language = Locale.US
                }
            }

            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, messageId)
            }
            val result = engine.speak(cleaned, TextToSpeech.QUEUE_FLUSH, params, messageId)
            if (result == TextToSpeech.ERROR) {
                _isSpeaking.value = false
                _currentMessageId.value = null
            }
        } catch (e: Exception) {
            _isSpeaking.value = false
            _currentMessageId.value = null
        }
    }

    fun stop() {
        activeSpeechJob?.cancel()
        activeSpeechJob = null
        try {
            activeMediaPlayer?.stop()
        } catch (_: Exception) {}
        try {
            activeMediaPlayer?.release()
        } catch (_: Exception) {}
        activeMediaPlayer = null
        try {
            tts?.stop()
        } catch (_: Exception) {}
        _isSpeaking.value = false
        _currentMessageId.value = null
    }

    fun toggleSpeak(context: Context, messageId: String, text: String) {
        if (_isSpeaking.value && _currentMessageId.value == messageId) {
            stop()
        } else {
            speak(context, messageId, text)
        }
    }

    fun cleanMarkdownForSpeech(raw: String): String {
        var s = raw
        s = s.replace(Regex("<think>[\\s\\S]*?</think>"), "")
        s = s.replace(Regex("```[\\s\\S]*?```"), " Code block generated. ")
        s = s.replace(Regex("`([^`]+)`"), "$1")
        s = s.replace(Regex("!\\[[^\\]]*\\]\\([^)]+\\)"), "")
        s = s.replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")
        s = s.replace(Regex("(?m)^#+\\s+"), "")
        s = s.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        s = s.replace(Regex("\\*([^*]+)\\*"), "$1")
        s = s.replace(Regex("__([^_]+)__"), "$1")
        s = s.replace(Regex("_([^_]+)_"), "$1")
        s = s.replace(Regex("(?m)^>\\s+"), "")
        s = s.replace(Regex("(?m)^[-*_]{3,}$"), "")
        s = s.replace(Regex("(?m)^[\\*\\-\\+]\\s+"), "")
        s = s.replace(Regex("\\n{2,}"), ". ")
        s = s.replace(Regex("\\s+"), " ").trim()
        return s
    }
}
