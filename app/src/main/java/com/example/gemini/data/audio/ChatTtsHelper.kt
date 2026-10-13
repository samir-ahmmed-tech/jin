package com.example.gemini.data.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Text-to-Speech manager for Jin AI Chat & Real-Time Voice Assistant.
 * Converts assistant responses into spoken audio using Android's native TTS engine.
 * Handles asynchronous TTS initialization queuing, automatic Bangla/English locale selection,
 * and fail-safe state transitions so Voice Assistant never hangs.
 */
object ChatTtsHelper {
    private const val TAG = "ChatTtsHelper"

    private var tts: TextToSpeech? = null
    @Volatile
    private var isInitialized = false
    @Volatile
    private var isInitializing = false

    private data class PendingUtterance(
        val messageId: String,
        val cleanedText: String
    )

    @Volatile
    private var pendingUtterance: PendingUtterance? = null

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
                            Log.w(TAG, "TTS utterance error ($errorCode) for $utteranceId")
                            _isSpeaking.value = false
                            _currentMessageId.value = null
                        }
                    })

                    // Play any utterance that was requested while TTS was still initializing
                    val pending = pendingUtterance
                    pendingUtterance = null
                    if (pending != null) {
                        executeSpeakInternal(pending.messageId, pending.cleanedText)
                    }
                } else {
                    Log.w(TAG, "TextToSpeech init failed with status: $status")
                    isInitialized = false
                    pendingUtterance = null
                    _isSpeaking.value = false
                    _currentMessageId.value = null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "TextToSpeech creation exception: ${e.message}")
            isInitializing = false
            isInitialized = false
            _isSpeaking.value = false
            _currentMessageId.value = null
        }
    }

    fun speak(context: Context, messageId: String, text: String) {
        val cleaned = cleanMarkdownForSpeech(text).take(3600)
        if (cleaned.isBlank()) {
            _isSpeaking.value = false
            _currentMessageId.value = null
            return
        }

        _currentMessageId.value = messageId
        _isSpeaking.value = true

        if (!isInitialized || tts == null) {
            pendingUtterance = PendingUtterance(messageId, cleaned)
            init(context)
            return
        }

        executeSpeakInternal(messageId, cleaned)
    }

    private fun executeSpeakInternal(messageId: String, cleaned: String) {
        val engine = tts
        if (engine == null) {
            _isSpeaking.value = false
            _currentMessageId.value = null
            return
        }

        try {
            engine.stop()
            // Auto-detect Bangla characters (\u0980-\u09FF) and switch TTS voice appropriately
            val containsBangla = cleaned.any { it in '\u0980'..'\u09FF' }
            if (containsBangla) {
                val bnBd = Locale("bn", "BD")
                val bnIn = Locale("bn", "IN")
                val availBd = engine.isLanguageAvailable(bnBd)
                if (availBd >= TextToSpeech.LANG_AVAILABLE) {
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
                Log.w(TAG, "engine.speak returned ERROR for $messageId")
                _isSpeaking.value = false
                _currentMessageId.value = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "executeSpeakInternal error: ${e.message}")
            _isSpeaking.value = false
            _currentMessageId.value = null
        }
    }

    fun stop() {
        pendingUtterance = null
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

    /**
     * Cleans markdown formatting, code snippets, and symbols so that TextToSpeech
     * speaks natural-sounding sentences instead of raw syntax.
     */
    fun cleanMarkdownForSpeech(raw: String): String {
        var s = raw
        // Remove thinking blocks if any
        s = s.replace(Regex("<think>[\\s\\S]*?</think>"), "")
        // Remove code blocks
        s = s.replace(Regex("```[\\s\\S]*?```"), " Code block generated. ")
        // Remove inline code backticks
        s = s.replace(Regex("`([^`]+)`"), "$1")
        // Remove markdown images
        s = s.replace(Regex("!\\[[^\\]]*\\]\\([^)]+\\)"), "")
        // Remove markdown links but keep text
        s = s.replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")
        // Remove headings (#, ##, ###)
        s = s.replace(Regex("(?m)^#+\\s+"), "")
        // Remove bold / italics
        s = s.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        s = s.replace(Regex("\\*([^*]+)\\*"), "$1")
        s = s.replace(Regex("__([^_]+)__"), "$1")
        s = s.replace(Regex("_([^_]+)_"), "$1")
        // Remove blockquotes (> )
        s = s.replace(Regex("(?m)^>\\s+"), "")
        // Remove horizontal rules
        s = s.replace(Regex("(?m)^[-*_]{3,}$"), "")
        // Remove bullet markers (*, -, +)
        s = s.replace(Regex("(?m)^[\\*\\-\\+]\\s+"), "")
        // Clean multiple newlines and spaces
        s = s.replace(Regex("\\n{2,}"), ". ")
        s = s.replace(Regex("\\s+"), " ").trim()
        return s
    }
}
