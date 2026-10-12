package com.example.gemini.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Direct API Client for JIN - AI Coding Super-Agent.
 * Allows using OpenAI (Codex / GPT-4o), DeepSeek (Hermes / V3 / Coder), Anthropic Claude,
 * Google Gemini, OpenRouter, and custom OpenAI-compatible proxies directly with user-supplied API keys.
 *
 * Developed by Samir Ahmmed (https://github.com/samir-ahmmed-tech/jin)
 */
class DirectApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {

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
        OPENCODE("OpenCode Zen", "https://opencode.ai/zen/v1"),
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
        return if (apiKey.isBlank() || apiKey == "opencode_zen_free") {
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

                        // Standard OpenAI format: {"data": [{"id": "..."}, ...]}
                        val dataArray = json.optJSONArray("data")
                        if (dataArray != null) {
                            for (i in 0 until dataArray.length()) {
                                val item = dataArray.optJSONObject(i)
                                val id = item?.optString("id") ?: ""
                                if (id.isNotBlank()) modelList.add(id)
                            }
                        }

                        // Ollama / custom format: {"models": [{"name": "..."}, ...]}
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

        // Fallback test: attempt a minimal completions ping
        return try {
            val completionsUrl = when {
                cleanUrl.endsWith("/chat/completions") -> cleanUrl
                cleanUrl.endsWith("/v1") -> "$cleanUrl/chat/completions"
                else -> "$cleanUrl/v1/chat/completions"
            }
            val payload = JSONObject().apply {
                put("model", model.ifBlank { "gpt-4o" })
                put("max_tokens", 2)
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
            if (apiKey.isNotBlank()) {
                reqBuilder.header("Authorization", "Bearer $apiKey")
            }
            client.newCall(reqBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    DirectApiTestResult(true, "Custom endpoint verified successfully (HTTP ${response.code})")
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
    ): String = withContext(Dispatchers.IO) {
        val cleanUrl = baseUrl.trim().trimEnd('/')
        val completionsUrl = when {
            cleanUrl.endsWith("/chat/completions") -> cleanUrl
            cleanUrl.endsWith("/v1") -> "$cleanUrl/chat/completions"
            else -> "$cleanUrl/v1/chat/completions"
        }

        val payload = JSONObject().apply {
            put("model", model)
            put("stream", true)
            put("messages", JSONArray().apply {
                if (systemPrompt.isNotBlank()) {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                }
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        val reqBuilder = Request.Builder()
            .url(completionsUrl)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))

        if (apiKey.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer $apiKey")
        }

        val fullResponse = StringBuilder()

        client.newCall(reqBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                val err = response.body?.string() ?: "HTTP ${response.code}"
                throw IllegalStateException("API error: $err")
            }

            val source = response.body?.byteStream() ?: return@withContext ""
            val reader = BufferedReader(InputStreamReader(source))

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line?.trim() ?: continue
                if (l.startsWith("data:")) {
                    val data = l.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    try {
                        val json = JSONObject(data)
                        val choices = json.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val delta = choices.getJSONObject(0).optJSONObject("delta")
                            val content = delta?.optString("content", "") ?: ""
                            if (content.isNotEmpty()) {
                                fullResponse.append(content)
                                onToken(content)
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        fullResponse.toString()
    }

    suspend fun streamChatMessages(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<Pair<String, String>>,
        systemPrompt: String = "You are JIN, an advanced agentic AI coding super-assistant developed by Samir Ahmmed.",
        onReasoning: ((String) -> Unit)? = null,
        onToken: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val cleanUrl = baseUrl.trim().trimEnd('/')
        val isAnthropic = cleanUrl.contains("anthropic.com")
        val isOpenRouter = cleanUrl.contains("openrouter.ai")
        val isOpenCode = cleanUrl.contains("opencode.ai")

        val targetUrl = when {
            isAnthropic -> if (cleanUrl.endsWith("/messages")) cleanUrl else "$cleanUrl/messages"
            isOpenCode -> if (cleanUrl.endsWith("/chat/completions")) cleanUrl else "$cleanUrl/chat/completions"
            cleanUrl.endsWith("/chat/completions") -> cleanUrl
            cleanUrl.endsWith("/v1") -> "$cleanUrl/chat/completions"
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

        client.newCall(reqBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                val err = response.body?.string() ?: "HTTP ${response.code}"
                val parsed = parseErrorMessage(err, "HTTP ${response.code}")
                throw IllegalStateException(parsed)
            }

            val source = response.body?.byteStream() ?: return@withContext ""
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
                        // OpenAI / DeepSeek / OpenRouter / OpenCode format
                        val choices = json.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val delta = choices.getJSONObject(0).optJSONObject("delta")
                            val reasoning = delta?.optString("reasoning_content", "") ?: ""
                            if (reasoning.isNotEmpty()) {
                                onReasoning?.invoke(reasoning)
                            }
                            val content = delta?.optString("content", "") ?: ""
                            if (content.isNotEmpty()) {
                                fullResponse.append(content)
                                onToken(content)
                            }
                        }
                        // Anthropic content_block_delta format
                        val delta = json.optJSONObject("delta")
                        val anthropicThinking = delta?.optString("thinking", "") ?: ""
                        if (anthropicThinking.isNotEmpty()) {
                            onReasoning?.invoke(anthropicThinking)
                        }
                        val anthropicText = delta?.optString("text", "") ?: ""
                        if (anthropicText.isNotEmpty()) {
                            fullResponse.append(anthropicText)
                            onToken(anthropicText)
                        }
                    } catch (_: Exception) {}
                }
            }

            // Fallback for non-streaming standard JSON responses
            if (!sawSseData && fullResponse.isEmpty() && rawBuffer.isNotBlank()) {
                try {
                    val fullJson = JSONObject(rawBuffer.toString().trim())
                    val choices = fullJson.optJSONArray("choices")
                    val content = choices?.optJSONObject(0)?.optJSONObject("message")?.optString("content", "")
                        ?: choices?.optJSONObject(0)?.optString("text", "")
                        ?: fullJson.optJSONArray("content")?.optJSONObject(0)?.optString("text", "")
                        ?: ""
                    if (content.isNotEmpty()) {
                        fullResponse.append(content)
                        onToken(content)
                    }
                } catch (_: Exception) {}
            }
        }

        fullResponse.toString()
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
