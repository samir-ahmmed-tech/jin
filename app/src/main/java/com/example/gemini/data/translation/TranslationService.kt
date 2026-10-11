package com.example.gemini.data.translation

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class SupportedLanguage(
    val code: String,
    val name: String,
    val nativeName: String
)

object TranslationService {

    private const val TAG = "TranslationService"

    val LANGUAGES = listOf(
        SupportedLanguage("bn", "Bengali", "বাংলা"),
        SupportedLanguage("en", "English", "English"),
        SupportedLanguage("hi", "Hindi", "हिन्दी"),
        SupportedLanguage("ar", "Arabic", "العربية"),
        SupportedLanguage("es", "Spanish", "Español"),
        SupportedLanguage("fr", "French", "Français"),
        SupportedLanguage("de", "German", "Deutsch"),
        SupportedLanguage("ja", "Japanese", "日本語"),
        SupportedLanguage("zh-CN", "Chinese (Simplified)", "中文 (简体)"),
        SupportedLanguage("ur", "Urdu", "اردو"),
        SupportedLanguage("ru", "Russian", "Русский"),
        SupportedLanguage("pt", "Portuguese", "Português"),
        SupportedLanguage("tr", "Turkish", "Türkçe"),
        SupportedLanguage("it", "Italian", "Italiano"),
        SupportedLanguage("ko", "Korean", "한국어"),
        SupportedLanguage("id", "Indonesian", "Bahasa Indonesia"),
        SupportedLanguage("ms", "Malay", "Bahasa Melayu"),
        SupportedLanguage("vi", "Vietnamese", "Tiếng Việt"),
        SupportedLanguage("fa", "Persian", "فارسی")
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * Translates input text into target language using free public API with automatic chunking.
     */
    suspend fun translateText(
        text: String,
        targetLang: String = "bn",
        sourceLang: String = "en"
    ): Result<String> = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@withContext Result.success("")

        try {
            // Split into manageable chunks (under 400 chars each) to prevent URL length limits
            val chunks = splitTextIntoChunks(trimmed, maxChars = 350)
            val translatedChunks = mutableListOf<String>()

            for (chunk in chunks) {
                val chunkResult = translateChunk(chunk, targetLang, sourceLang)
                translatedChunks.add(chunkResult)
            }

            Result.success(translatedChunks.joinToString("\n\n"))
        } catch (e: Exception) {
            Log.e(TAG, "Translation error", e)
            Result.failure(e)
        }
    }

    private fun splitTextIntoChunks(text: String, maxChars: Int): List<String> {
        val paragraphs = text.split("\n\n")
        val result = mutableListOf<String>()
        var current = StringBuilder()

        for (para in paragraphs) {
            if (current.length + para.length > maxChars && current.isNotEmpty()) {
                result.add(current.toString().trim())
                current = StringBuilder()
            }
            if (current.isNotEmpty()) current.append("\n\n")
            current.append(para)
        }
        if (current.isNotEmpty()) {
            result.add(current.toString().trim())
        }
        return if (result.isEmpty()) listOf(text) else result
    }

    private fun translateChunk(chunk: String, targetLang: String, sourceLang: String): String {
        val encodedText = URLEncoder.encode(chunk, "UTF-8")
        val effectiveSource = if (sourceLang == "auto") "en" else sourceLang
        val langPair = "$effectiveSource|$targetLang"
        val url = "https://api.mymemory.translated.net/get?q=$encodedText&langpair=$langPair"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; JinApp) AppleWebKit/537.36")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return chunk
            }
            val bodyStr = response.body?.string().orEmpty()
            val json = JSONObject(bodyStr)
            val responseData = json.optJSONObject("responseData")
            val translated = responseData?.optString("translatedText").orEmpty()
            return if (translated.isNotBlank()) translated.trim() else chunk
        }
    }
}
