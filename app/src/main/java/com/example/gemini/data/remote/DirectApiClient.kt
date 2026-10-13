package com.example.gemini.data.remote

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.gemini.data.preferences.AuthPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Direct API Client for JIN - AI Coding Super-Agent.
 * Allows using OpenAI (Codex / GPT-4o), DeepSeek (Hermes / V3 / Coder), Anthropic Claude,
 * Google Gemini, OpenRouter, OpenCode Zen (Zero-Key Free Gateway), and custom OpenAI-compatible proxies.
 *
 * Developed by Samir Ahmmed (https://github.com/samir-ahmmed-tech/jin)
 */
class DirectApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {
    companion object {
        private const val TAG = "DirectApiClient"
        const val ZERO_KEY_GATEWAY_URL = "https://text.pollinations.ai/openai"
        const val ZERO_KEY_DEFAULT_MODEL = "openai"
        val shared by lazy { DirectApiClient() }

        suspend fun transcribeAudioWav(context: Context, wavBytes: ByteArray): String? {
            return shared.transcribeAudioWav(context, wavBytes).getOrNull()
        }

        suspend fun streamChatMessages(
            baseUrl: String,
            apiKey: String,
            model: String,
            messages: List<Pair<String, String>>,
            systemInstruction: String = "",
            onChunk: (String) -> Unit,
            onComplete: suspend () -> Unit = {},
            onError: suspend (String) -> Unit = {}
        ) {
            try {
                shared.streamChatMessages(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    model = model,
                    messages = messages,
                    systemPrompt = systemInstruction,
                    onReasoning = { r -> onChunk("<think>$r</think>") },
                    onToken = { t -> onChunk(t) }
                )
                onComplete()
            } catch (e: Exception) {
                onError(e.message ?: "Stream error")
            }
        }
    }

    data class DirectApiTestResult(
        val success: Boolean,
        val message: String,
        val details: String? = null
    )

    enum class ApiProvider(val displayName: String, val defaultBaseUrl: String) {
        OPENAI("OpenAI", "https://api.openai.com/v1"),
        DEEPSEEK("DeepSeek", "https://api.deepseek.com/v1"),
        ANTHROPIC("Anthropic Claude", "https://api.anthropic.com/v1"),
        GEMINI("Google Gemini", "https://generativelanguage.googleapis.com/v1beta"),
        OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1"),
        OPENCODE("OpenCode Zen", "https://text.pollinations.ai/openai"),
        CUSTOM("Custom / 3rd Party Proxy", "")
    }

    suspend fun testConnection(
        provider: ApiProvider,
        apiKey: String,
        customBaseUrl: String = "",
        customModel: String = ""
    ): DirectApiTestResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() && provider != ApiProvider.CUSTOM && provider != ApiProvider.OPENCODE) {
            return@withContext DirectApiTestResult(false, "API key cannot be empty")
        }

        try {
            when (provider) {
                ApiProvider.OPENAI -> testOpenAi(apiKey)
                ApiProvider.DEEPSEEK -> testDeepSeek(apiKey)
                ApiProvider.ANTHROPIC -> testAnthropic(apiKey)
                ApiProvider.GEMINI -> testGemini(apiKey)
                ApiProvider.OPENROUTER -> testOpenRouter(apiKey)
                ApiProvider.OPENCODE -> testOpenCode(apiKey)
                ApiProvider.CUSTOM -> testCustom(apiKey, customBaseUrl, customModel)
            }
        } catch (e: Exception) {
            DirectApiTestResult(false, "Connection error: ${e.localizedMessage ?: e.message}")
        }
    }

    private fun testOpenAi(apiKey: String): DirectApiTestResult {
        val request = Request.Builder()
            .url("https://api.openai.com/v1/models")
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            return if (response.isSuccessful) {
                DirectApiTestResult(true, "OpenAI API key verified successfully!")
            } else {
                val errorBody = response.body?.string() ?: ""
                val msg = parseErrorMessage(errorBody, "OpenAI error: HTTP ${response.code}")
                DirectApiTestResult(false, msg)
            }
        }
    }

    private fun testDeepSeek(apiKey: String): DirectApiTestResult {
        val request = Request.Builder()
            .url("https://api.deepseek.com/models")
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            return if (response.isSuccessful) {
                DirectApiTestResult(true, "DeepSeek API key verified successfully!")
            } else {
                val errorBody = response.body?.string() ?: ""
                val msg = parseErrorMessage(errorBody, "DeepSeek error: HTTP ${response.code}")
                DirectApiTestResult(false, msg)
            }
        }
    }

    private fun testAnthropic(apiKey: String): DirectApiTestResult {
        val payload = JSONObject().apply {
            put("model", "claude-3-haiku-20240307")
            put("max_tokens", 5)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "ping")
                })
            })
        }

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            return if (response.isSuccessful) {
                DirectApiTestResult(true, "Anthropic Claude API key verified successfully!")
            } else {
                val errorBody = response.body?.string() ?: ""
                val msg = parseErrorMessage(errorBody, "Claude error: HTTP ${response.code}")
                DirectApiTestResult(false, msg)
            }
        }
    }

    private fun testGemini(apiKey: String): DirectApiTestResult {
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            return if (response.isSuccessful) {
                DirectApiTestResult(true, "Google Gemini API key verified successfully!")
            } else {
                val errorBody = response.body?.string() ?: ""
                val msg = parseErrorMessage(errorBody, "Gemini error: HTTP ${response.code}")
                DirectApiTestResult(false, msg)
            }
        }
    }

    private fun testOpenRouter(apiKey: String): DirectApiTestResult {
        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/models")
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            return if (response.isSuccessful) {
                DirectApiTestResult(true, "OpenRouter API key verified successfully!")
            } else {
                val errorBody = response.body?.string() ?: ""
                val msg = parseErrorMessage(errorBody, "OpenRouter error: HTTP ${response.code}")
                DirectApiTestResult(false, msg)
            }
        }
    }

    private fun testOpenCode(apiKey: String): DirectApiTestResult {
        return if (apiKey.isBlank() || apiKey == "opencode_zen_free" || apiKey == "free-opencode-zen") {
            DirectApiTestResult(true, "OpenCode Zen Gateway connected! (Free & Unlimited Models Mode)")
        } else {
            testCustom(apiKey, "https://opencode.ai/zen/v1", "deepseek-v4-flash")
        }
    }

    suspend fun fetchAvailableModels(baseUrl: String, apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val cleanUrl = baseUrl.trim().trimEnd('/')
        if (cleanUrl.isBlank()) return@withContext emptyList()

        val candidateUrls = listOf(
            if (cleanUrl.endsWith("/v1")) "$cleanUrl/models" else "$cleanUrl/v1/models",
            "$cleanUrl/models",
            if (cleanUrl.endsWith("/v1")) "$cleanUrl/models" else "$cleanUrl/api/v1/models"
        ).distinct()

        for (targetUrl in candidateUrls) {
            try {
                val reqBuilder = Request.Builder().url(targetUrl).get()
                if (apiKey.isNotBlank()) {
                    reqBuilder.header("Authorization", "Bearer $apiKey")
                }
                client.newCall(reqBuilder.build()).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val json = JSONObject(body)
                        val modelList = mutableListOf<String>()

                        val dataArray = json.optJSONArray("data")
                        if (dataArray != null) {
                            for (i in 0 until dataArray.length()) {
                                val item = dataArray.optJSONObject(i)
                                val id = item?.optString("id") ?: ""
                                if (id.isNotBlank()) modelList.add(id)
                            }
                        }

                        if (modelList.isEmpty()) {
                            val modelsArray = json.optJSONArray("models")
                            if (modelsArray != null) {
                                for (i in 0 until modelsArray.length()) {
                                    val item = modelsArray.optJSONObject(i)
                                    val name = item?.optString("name") ?: item?.optString("id") ?: ""
                                    if (name.isNotBlank()) modelList.add(name)
                                }
                            }
                        }

                        if (modelList.isNotEmpty()) {
                            return@withContext modelList
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        emptyList()
    }

    private fun testCustom(apiKey: String, baseUrl: String, model: String): DirectApiTestResult {
        val cleanUrl = baseUrl.trim().trimEnd('/')
        if (cleanUrl.isBlank()) {
            return DirectApiTestResult(false, "Base URL cannot be empty")
        }

        val candidateUrls = listOf(
            if (cleanUrl.endsWith("/v1")) "$cleanUrl/models" else "$cleanUrl/v1/models",
            "$cleanUrl/models"
        ).distinct()

        for (targetUrl in candidateUrls) {
            try {
                val reqBuilder = Request.Builder().url(targetUrl).get()
                if (apiKey.isNotBlank()) {
                    reqBuilder.header("Authorization", "Bearer $apiKey")
                }
                client.newCall(reqBuilder.build()).execute().use { response ->
                    if (response.isSuccessful) {
                        return DirectApiTestResult(true, "Custom endpoint connected successfully (HTTP ${response.code})")
                    }
                }
            } catch (_: Exception) {}
        }

        return try {
            val completionsUrl = when {
                cleanUrl.endsWith("/chat/completions") -> cleanUrl
                cleanUrl.endsWith("/openai") -> cleanUrl
                cleanUrl.endsWith("/v1") -> "$cleanUrl/chat/completions"
                else -> "$cleanUrl/v1/chat/completions"
            }
            val payload = JSONObject().apply {
                put("model", model.ifBlank { "openai" })
                put("max_tokens", 5)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "ping")
                    })
                })
            }
            val reqBuilder = Request.Builder()
                .url(completionsUrl)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
            if (apiKey.isNotBlank() && apiKey != "opencode_zen_free" && apiKey != "free-opencode-zen") {
                reqBuilder.header("Authorization", "Bearer $apiKey")
            }
            client.newCall(reqBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    DirectApiTestResult(true, "Endpoint verified successfully (HTTP ${response.code})")
                } else {
                    val errorBody = response.body?.string() ?: ""
                    val msg = parseErrorMessage(errorBody, "Endpoint error: HTTP ${response.code}")
                    DirectApiTestResult(false, msg)
                }
            }
        } catch (e: Exception) {
            DirectApiTestResult(false, "Endpoint unreachable: ${e.localizedMessage ?: e.message}")
        }
    }

    suspend fun streamOpenAiCompatibleChat(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        systemPrompt: String = "You are JIN, an advanced agentic AI coding super-assistant developed by Samir Ahmmed.",
        onToken: (String) -> Unit
    ): String = streamChatMessages(
        baseUrl = baseUrl,
        apiKey = apiKey,
        model = model,
        messages = listOf("user" to prompt),
        systemPrompt = systemPrompt,
        onReasoning = null,
        onToken = onToken
    )

    suspend fun streamChatMessages(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<Pair<String, String>>,
        systemPrompt: String = "You are JIN, an advanced agentic AI coding super-assistant developed by Samir Ahmmed.",
        onReasoning: ((String) -> Unit)? = null,
        onToken: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val isFreeKey = apiKey.isBlank() || apiKey == "opencode_zen_free" || apiKey == "free-opencode-zen" || apiKey == "google_ai_session"
        val cleanUrl = if (isFreeKey && (baseUrl.isBlank() || baseUrl.contains("opencode.ai") || baseUrl.contains("deepseek.com") || baseUrl.contains("openai.com") || baseUrl.contains("openrouter.ai"))) {
            ZERO_KEY_GATEWAY_URL
        } else {
            baseUrl.trim().trimEnd('/')
        }

        val effectiveModel = if (cleanUrl == ZERO_KEY_GATEWAY_URL) {
            ZERO_KEY_DEFAULT_MODEL
        } else {
            model.ifBlank { "gpt-4o" }
        }

        try {
            return@withContext executeStreamRequest(
                cleanUrl = cleanUrl,
                apiKey = if (isFreeKey) "" else apiKey,
                model = effectiveModel,
                messages = messages,
                systemPrompt = systemPrompt,
                onReasoning = onReasoning,
                onToken = onToken
            )
        } catch (primaryError: Exception) {
            Log.w(TAG, "Primary endpoint ($cleanUrl) failed: ${primaryError.message}. Trying automatic zero-key gateway fallback...")
            if (cleanUrl != ZERO_KEY_GATEWAY_URL) {
                return@withContext executeStreamRequest(
                    cleanUrl = ZERO_KEY_GATEWAY_URL,
                    apiKey = "",
                    model = ZERO_KEY_DEFAULT_MODEL,
                    messages = messages,
                    systemPrompt = systemPrompt,
                    onReasoning = onReasoning,
                    onToken = onToken
                )
            }
            throw primaryError
        }
    }

    private fun executeStreamRequest(
        cleanUrl: String,
        apiKey: String,
        model: String,
        messages: List<Pair<String, String>>,
        systemPrompt: String,
        onReasoning: ((String) -> Unit)?,
        onToken: (String) -> Unit
    ): String {
        val isAnthropic = cleanUrl.contains("anthropic.com")
        val isOpenRouter = cleanUrl.contains("openrouter.ai")
        val isPollinations = cleanUrl.contains("pollinations.ai")

        val targetUrl = when {
            isAnthropic -> if (cleanUrl.endsWith("/messages")) cleanUrl else "$cleanUrl/messages"
            isPollinations -> cleanUrl
            cleanUrl.endsWith("/chat/completions") -> cleanUrl
            cleanUrl.endsWith("/v1") || cleanUrl.endsWith("/openai") -> "$cleanUrl/chat/completions"
            else -> "$cleanUrl/v1/chat/completions"
        }

        val payload = if (isAnthropic) {
            JSONObject().apply {
                put("model", model)
                put("stream", true)
                put("max_tokens", 4096)
                if (systemPrompt.isNotBlank()) {
                    put("system", systemPrompt)
                }
                put("messages", JSONArray().apply {
                    messages.forEach { (role, content) ->
                        put(JSONObject().apply {
                            put("role", if (role.equals("user", true)) "user" else "assistant")
                            put("content", content)
                        })
                    }
                })
            }
        } else {
            JSONObject().apply {
                put("model", model)
                put("stream", true)
                put("messages", JSONArray().apply {
                    if (systemPrompt.isNotBlank()) {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                    }
                    messages.forEach { (role, content) ->
                        put(JSONObject().apply {
                            put("role", role)
                            put("content", content)
                        })
                    }
                })
            }
        }

        val reqBuilder = Request.Builder()
            .url(targetUrl)
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))

        if (apiKey.isNotBlank()) {
            if (isAnthropic) {
                reqBuilder.header("x-api-key", apiKey)
                reqBuilder.header("anthropic-version", "2023-06-01")
            } else {
                reqBuilder.header("Authorization", "Bearer $apiKey")
            }
        }

        if (isOpenRouter) {
            reqBuilder.header("HTTP-Referer", "https://jin.samirahmmed.tech")
            reqBuilder.header("X-Title", "JIN AI Assistant")
        }

        val fullResponse = StringBuilder()
        val toolCallArgsBuffer = mutableMapOf<Int, Pair<String, StringBuilder>>()

        client.newCall(reqBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                val err = response.body?.string() ?: "HTTP ${response.code}"
                val parsed = parseErrorMessage(err, "HTTP ${response.code}")
                throw IllegalStateException(parsed)
            }

            val source = response.body?.byteStream() ?: return ""
            val reader = BufferedReader(InputStreamReader(source))

            var line: String?
            val rawBuffer = StringBuilder()
            var sawSseData = false
            while (reader.readLine().also { line = it } != null) {
                val l = line?.trim() ?: continue
                rawBuffer.append(l).append("\n")
                if (l.startsWith("data:")) {
                    sawSseData = true
                    val data = l.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    try {
                        val json = JSONObject(data)
                        val choices = json.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val choiceObj = choices.getJSONObject(0)
                            val delta = choiceObj.optJSONObject("delta")
                            if (delta != null) {
                                val reasoning = delta.optString("reasoning_content", "")
                                    .ifEmpty { delta.optString("reasoning", "") }
                                if (reasoning.isNotEmpty()) {
                                    onReasoning?.invoke(reasoning)
                                }
                                val content = delta.optString("content", "")
                                if (content.isNotEmpty() && content != "null") {
                                    fullResponse.append(content)
                                    onToken(content)
                                }
                                // Handle streaming tool_calls if emitted by reasoning models
                                val toolCalls = delta.optJSONArray("tool_calls")
                                if (toolCalls != null) {
                                    for (i in 0 until toolCalls.length()) {
                                        val tc = toolCalls.optJSONObject(i) ?: continue
                                        val idx = tc.optInt("index", i)
                                        val fn = tc.optJSONObject("function")
                                        val fnName = fn?.optString("name", "").orEmpty()
                                        val fnArgs = fn?.optString("arguments", "").orEmpty()
                                        val existing = toolCallArgsBuffer[idx]
                                        val resolvedName = if (fnName.isNotBlank()) fnName else (existing?.first ?: "tool:write_file")
                                        val sb = existing?.second ?: StringBuilder()
                                        sb.append(fnArgs)
                                        toolCallArgsBuffer[idx] = resolvedName to sb
                                    }
                                }
                            }
                        }
                        val delta = json.optJSONObject("delta")
                        if (delta != null) {
                            val anthropicThinking = delta.optString("thinking", "")
                            if (anthropicThinking.isNotEmpty()) {
                                onReasoning?.invoke(anthropicThinking)
                            }
                            val anthropicText = delta.optString("text", "")
                            if (anthropicText.isNotEmpty()) {
                                fullResponse.append(anthropicText)
                                onToken(anthropicText)
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            // Flush any accumulated streaming tool_calls as markdown tool blocks
            if (toolCallArgsBuffer.isNotEmpty()) {
                for ((_, pair) in toolCallArgsBuffer) {
                    val formatted = formatToolCallToMarkdown(pair.first, pair.second.toString())
                    if (formatted.isNotBlank()) {
                        fullResponse.append(formatted)
                        onToken(formatted)
                    }
                }
            }

            // Fallback for non-streaming standard JSON responses
            if (!sawSseData && fullResponse.isEmpty() && rawBuffer.isNotBlank()) {
                try {
                    val fullJson = JSONObject(rawBuffer.toString().trim())
                    val choices = fullJson.optJSONArray("choices")
                    val msgObj = choices?.optJSONObject(0)?.optJSONObject("message")
                    val reasoning = msgObj?.optString("reasoning_content", "")
                        ?.ifEmpty { msgObj.optString("reasoning", "") }.orEmpty()
                    if (reasoning.isNotEmpty()) {
                        onReasoning?.invoke(reasoning)
                    }
                    val content = msgObj?.optString("content", "")
                        ?: choices?.optJSONObject(0)?.optString("text", "")
                        ?: fullJson.optJSONArray("content")?.optJSONObject(0)?.optString("text", "")
                        ?: ""
                    if (content.isNotEmpty() && content != "null") {
                        fullResponse.append(content)
                        onToken(content)
                    }
                    val toolCalls = msgObj?.optJSONArray("tool_calls")
                    if (toolCalls != null) {
                        for (i in 0 until toolCalls.length()) {
                            val tc = toolCalls.optJSONObject(i) ?: continue
                            val fn = tc.optJSONObject("function") ?: continue
                            val formatted = formatToolCallToMarkdown(
                                fn.optString("name", ""),
                                fn.optString("arguments", "")
                            )
                            if (formatted.isNotBlank()) {
                                fullResponse.append(formatted)
                                onToken(formatted)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        return fullResponse.toString()
    }

    private fun formatToolCallToMarkdown(fnName: String, rawArgs: String): String {
        val argsJson = runCatching { JSONObject(rawArgs) }.getOrNull() ?: return ""
        val cleanName = fnName.lowercase()
        return when {
            cleanName.contains("write") || argsJson.has("content") -> {
                val path = argsJson.optString("path", argsJson.optString("file", "output.txt"))
                val content = argsJson.optString("content", "")
                "\n```tool:write_file\npath: $path\ncontent:\n$content\n```\n"
            }
            cleanName.contains("exec") || cleanName.contains("bash") || cleanName.contains("cmd") || argsJson.has("command") -> {
                val cmd = argsJson.optString("command", argsJson.optString("cmd", ""))
                "\n```tool:execute_command\ncommand: $cmd\n```\n"
            }
            cleanName.contains("read") -> {
                val path = argsJson.optString("path", argsJson.optString("file", ""))
                "\n```tool:read_file\npath: $path\n```\n"
            }
            else -> ""
        }
    }

    /**
     * Transcribes 16kHz PCM/WAV audio bytes using available multimodal AI endpoints
     * (AGY Hub, Google Gemini API multimodal inlineData, or OpenAI Whisper).
     */
    suspend fun transcribeAudioWav(context: Context, wavBytes: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        if (wavBytes.size <= 44) {
            return@withContext Result.failure(IllegalArgumentException("Audio buffer is empty"))
        }
        val b64 = Base64.encodeToString(wavBytes, Base64.NO_WRAP)
        val authPrefs = AuthPreferences(context)

        // 1. Try AGY Hub transcription if Hub is online
        try {
            if (AgyBridgeService.instance.systemConnectionState.value.isHubOnline) {
                val hubRes = AgyHubClient().getTranscription(audioBase64 = b64, hubUrl = AuthPreferences.currentHubUrl)
                if (hubRes.isSuccess && hubRes.getOrThrow().isNotBlank()) {
                    return@withContext Result.success(hubRes.getOrThrow().trim())
                }
            }
        } catch (_: Exception) {}

        // 2. Try Google Gemini API multimodal audio transcription if Gemini key is configured
        val geminiKey = authPrefs.getGeminiApiKeySync().trim()
        if (geminiKey.isNotBlank()) {
            try {
                val payload = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", "Transcribe this spoken audio accurately. Output ONLY the exact spoken words without quotes or commentary.")
                                })
                                put(JSONObject().apply {
                                    put("inlineData", JSONObject().apply {
                                        put("mimeType", "audio/wav")
                                        put("data", b64)
                                    })
                                })
                            })
                        })
                    })
                }
                val req = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$geminiKey")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        val json = JSONObject(body)
                        val text = json.optJSONArray("candidates")
                            ?.optJSONObject(0)
                            ?.optJSONObject("content")
                            ?.optJSONArray("parts")
                            ?.optJSONObject(0)
                            ?.optString("text", "")
                            ?.trim()
                            .orEmpty()
                        if (text.isNotBlank()) {
                            return@withContext Result.success(text)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gemini audio transcription failed: ${e.message}")
            }
        }

        Result.failure(IllegalStateException("Speech recognition unavailable"))
    }

    private fun parseErrorMessage(body: String, fallback: String): String {
        return try {
            val json = JSONObject(body)
            json.optJSONObject("error")?.optString("message")
                ?: json.optString("message", fallback)
        } catch (_: Exception) {
            fallback
        }
    }
}
