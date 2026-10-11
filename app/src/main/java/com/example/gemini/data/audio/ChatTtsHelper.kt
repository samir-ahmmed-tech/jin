package com.example.gemini.data.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Text-to-Speech manager for Jin AI Chat.
 * Converts assistant responses into spoken audio using Android's native TTS engine.
 */
object ChatTtsHelper {
    private const val TAG = "ChatTtsHelper"

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _currentMessageId = MutableStateFlow<String?>(null)
    val currentMessageId: StateFlow<String?> = _currentMessageId.asStateFlow()

    fun init(context: Context) {
        if (tts != null) return
        val appContext = context.applicationContext
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                tts?.language = Locale.getDefault()
                tts?.setSpeechRate(1.05f)
                tts?.setPitch(1.0f)
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
                Log.w(TAG, "TextToSpeech init failed with status: $status")
            }
        }
    }

    fun speak(context: Context, messageId: String, text: String) {
        init(context)
        val cleaned = cleanMarkdownForSpeech(text)
        if (cleaned.isBlank()) return

        stop()
        _currentMessageId.value = messageId
        _isSpeaking.value = true

        tts?.speak(cleaned, TextToSpeech.QUEUE_FLUSH, null, messageId)
    }

    fun stop() {
        tts?.stop()
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
        // Remove code blocks
        s = s.replace(Regex("```[\\s\\S]*?```"), "Code snippet omitted.")
        // Remove inline code
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
