package com.example.gemini.data.remote.core

import com.example.gemini.data.preferences.AuthPreferences
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Shared singleton OkHttpClient provider for gRPC-Web and Wire transport.
 * Configured with high multiplexing dispatcher, connection pooling, timeouts, and dynamic CSRF token evaluation.
 */
object AgyOkHttpClient {
    val client: OkHttpClient by lazy {
        val dispatcher = Dispatcher().apply {
            maxRequests = 256
            maxRequestsPerHost = 256
        }
        val connectionPool = ConnectionPool(32, 5, TimeUnit.MINUTES)

        val builder = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(connectionPool)
            .protocols(listOf(okhttp3.Protocol.H2_PRIOR_KNOWLEDGE))
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // Indefinite read timeout for streaming
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        if (com.example.gemini.data.remote.inspector.NetworkInspectorManager.isEnabled) {
            builder.addInterceptor(com.example.gemini.data.remote.inspector.NetworkInspectorInterceptor("AGY Daemon (gRPC)"))
        }

        builder.addInterceptor { chain ->
                val hubUrl = AuthPreferences.currentHubUrl
                var token = AgyCsrfManager.instance.token
                if (token.isBlank()) {
                    try {
                        val status = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                            com.example.gemini.data.remote.AgyBridgeService.instance.fetchServerStatus(AuthPreferences.currentBridgeHttpUrl)
                        }
                        if (!status?.csrfToken.isNullOrBlank()) {
                            token = status.csrfToken
                        }
                    } catch (_: Exception) {}
                }

                val request = chain.request().newBuilder()
                    .header("User-Agent", "antiGem-Android-Native")
                    .header("X-Grpc-Web", "1")
                    .header(AuthPreferences.currentFramedHeader, token.ifBlank { "true" })
                    .build()

                val response = chain.proceed(request)

                // Only invalidate CSRF token on HTTP 403 or explicit CSRF message, NEVER on generic gRPC status 16 (not logged in)
                val grpcMsg = response.header("grpc-message").orEmpty()
                val isCsrfError = response.code == 403 || grpcMsg.contains("csrf", ignoreCase = true)
                if (isCsrfError) {
                    AgyCsrfManager.instance.notifyCsrfExpired(hubUrl, chain.request().url.encodedPath)
                }

                response
            }
            .build()
    }
}
