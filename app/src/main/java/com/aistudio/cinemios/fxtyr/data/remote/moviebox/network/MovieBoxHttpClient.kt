package com.aistudio.cinemios.fxtyr.data.remote.moviebox.network

import com.aistudio.cinemios.fxtyr.data.remote.moviebox.crypto.MovieBoxSigner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import java.net.ProtocolException
import java.util.concurrent.TimeUnit

class MovieBoxHttpClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .proxy(java.net.Proxy.NO_PROXY) // ✅ يمنع أي سلوك Proxy افتراضي
        .build()

    private val HOST_POOL = listOf(
        "https://api6.aoneroom.com",
        "https://api5.aoneroom.com",
        "https://api4.aoneroom.com",
        "https://api4sg.aoneroom.com",
        "https://api3.aoneroom.com",
        "https://api6sg.aoneroom.com",
        "https://api.inmoviebox.com"
    )

    private val RETRY_STATUS_CODES = setOf(403, 407, 429, 500, 502, 503, 504)

    var runtimeToken: String? = null

    suspend fun get(path: String, params: Map<String, String> = emptyMap()): Result<JSONObject> =
        withContext(Dispatchers.IO) {
            val accept = "application/json"
            val contentType = "application/json"

            for (host in HOST_POOL) {
                val urlBuilder = (host + path).toHttpUrlOrNull()?.newBuilder() ?: continue
                params.entries.sortedBy { it.key }.forEach { (k, v) ->
                    urlBuilder.addQueryParameter(k, v)
                }
                val fullUrl = urlBuilder.build().toString()
                val debugMap = mutableMapOf<String, String>()

                val headersMap = MovieBoxSigner.buildSignedHeaders(
                    method = "GET",
                    url = fullUrl,
                    accept = accept,
                    contentType = contentType,
                    body = "",
                    authToken = runtimeToken,
                    debugOut = debugMap
                )

                val request = Request.Builder().url(fullUrl).get().apply {
                    headersMap.forEach { (k, v) -> addHeader(k, v) }
                }.build()

                try {
                    val response = client.newCall(request).execute()
                    val code = response.code

                    if (code in RETRY_STATUS_CODES) {
                        response.close()
                        continue  // ✅ دلوقتي بيوصل هنا بدل ما يثرو exception
                    }

                    // ✅ استخراج الـ token من x-user header بشكل صح
                    val xUserHeader = response.header("x-user")
                    if (!xUserHeader.isNullOrEmpty()) {
                        try {
                            val parsed = JSONObject(xUserHeader)
                            val token = parsed.optString("token", "")
                            if (token.isNotEmpty()) {
                                runtimeToken = token
                                MovieBoxSigner.cachedRuntimeToken = token
                            }
                        } catch (_: Exception) {}
                    }

                    val bodyStr = response.body?.string() ?: ""
                    response.close()

                    return@withContext if (response.isSuccessful) {
                        val json = JSONObject(bodyStr)
                        Result.success(json.optJSONObject("data") ?: json)
                    } else {
                        val debugStr = debugMap.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                        Result.failure(Exception("HTTP $code: $bodyStr\n$debugStr"))
                    }
                } catch (e: Exception) {
                    if (e is ProtocolException &&
                        (e.message?.contains("407") == true || e.message?.contains("HTTP_PROXY_AUTH") == true)) {
                        continue
                    }
                    if (host == HOST_POOL.last()) {
                        return@withContext Result.failure(e)
                    }
                    continue
                }
            }
            Result.failure(Exception("All hosts failed"))
        }

    suspend fun post(path: String, body: Map<String, Any>): Result<JSONObject> =
        withContext(Dispatchers.IO) {
            val accept = "application/json"
            val contentType = "application/json; charset=utf-8"
            val bodyJsonString = JSONObject(body).toString()

            for (host in HOST_POOL) {
                val fullUrl = host + path
                val debugMap = mutableMapOf<String, String>()

                val headersMap = MovieBoxSigner.buildSignedHeaders(
                    method = "POST",
                    url = fullUrl,
                    accept = accept,
                    contentType = contentType,
                    body = bodyJsonString,
                    authToken = runtimeToken,
                    debugOut = debugMap
                )

                val requestBody = bodyJsonString.toRequestBody(contentType.toMediaTypeOrNull())
                val request = Request.Builder().url(fullUrl).post(requestBody).apply {
                    headersMap.forEach { (k, v) -> addHeader(k, v) }
                }.build()

                try {
                    val response = client.newCall(request).execute()
                    val code = response.code

                    if (code in RETRY_STATUS_CODES) {
                        response.close()
                        continue
                    }

                    val xUserHeader = response.header("x-user")
                    if (!xUserHeader.isNullOrEmpty()) {
                        try {
                            val parsed = JSONObject(xUserHeader)
                            val token = parsed.optString("token", "")
                            if (token.isNotEmpty()) {
                                runtimeToken = token
                                MovieBoxSigner.cachedRuntimeToken = token
                            }
                        } catch (_: Exception) {}
                    }

                    val bodyStr = response.body?.string() ?: ""
                    response.close()

                    return@withContext if (response.isSuccessful) {
                        val json = JSONObject(bodyStr)
                        Result.success(json.optJSONObject("data") ?: json)
                    } else {
                        val debugStr = debugMap.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                        Result.failure(Exception("HTTP $code: $bodyStr\n$debugStr"))
                    }
                } catch (e: Exception) {
                    if (e is ProtocolException &&
                        (e.message?.contains("407") == true || e.message?.contains("HTTP_PROXY_AUTH") == true)) {
                        continue
                    }
                    if (host == HOST_POOL.last()) {
                        return@withContext Result.failure(e)
                    }
                    continue
                }
            }
            Result.failure(Exception("All hosts failed"))
        }
}
