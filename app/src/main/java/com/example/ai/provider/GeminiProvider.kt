package com.example.ai.provider

import com.example.ai.*
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

class GeminiProvider(private val apiKey: String) : AiProviderService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json".toMediaType()

    override suspend fun streamChat(
        messages: List<AiChatMessage>,
        model: String,
        toolsJson: String?,
        thinkingLevel: ThinkingLevel,
        onEvent: (AiStreamEvent) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            onEvent(AiStreamEvent(AiStreamEventType.ERROR, content = "مفتاح Gemini API غير مضبوط (GEMINI_API_KEY)"))
            return@withContext
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse&key=$apiKey"
            val body = buildGeminiRequestBody(messages, toolsJson, thinkingLevel)

            val request = Request.Builder()
                .url(url)
                .post(body.toRequestBody(jsonMediaType))
                .header("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body ?: run {
                onEvent(AiStreamEvent(AiStreamEventType.ERROR, content = "Empty response body"))
                return@withContext
            }

            val contentType = response.header("Content-Type") ?: ""
            if (!response.isSuccessful) {
                val errorStr = sanitizeError(responseBody.string())
                onEvent(AiStreamEvent(AiStreamEventType.ERROR, content = "Gemini error (${response.code}): $errorStr"))
                return@withContext
            }

            // Non-SSE fallback
            if (!contentType.contains("text/event-stream") && !contentType.contains("application/json")) {
                val raw = responseBody.string()
                onEvent(AiStreamEvent(AiStreamEventType.ERROR, content = sanitizeError(raw)))
                return@withContext
            }

            val reader = BufferedReader(InputStreamReader(responseBody.byteStream()))
            var line: String?
            var emittedAny = false
            var lastFinish: String? = null

            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                if (l.isBlank()) continue

                val jsonStr = when {
                    l.startsWith("data: ") -> l.removePrefix("data: ").trim()
                    l.startsWith("{") -> l.trim()
                    else -> continue
                }
                if (jsonStr == "[DONE]" || jsonStr.isEmpty()) continue

                try {
                    val json = JSONObject(jsonStr)

                    if (json.has("error")) {
                        val err = json.optJSONObject("error")
                        val msg = err?.optString("message") ?: json.toString()
                        onEvent(AiStreamEvent(AiStreamEventType.ERROR, content = msg))
                        reader.close()
                        return@withContext
                    }

                    val candidates = json.optJSONArray("candidates") ?: continue
                    if (candidates.length() == 0) continue
                    val candidate = candidates.getJSONObject(0)
                    val finishReason = candidate.optString("finishReason", "")
                    if (finishReason.isNotEmpty()) lastFinish = finishReason

                    val content = candidate.optJSONObject("content") ?: continue
                    val parts = content.optJSONArray("parts") ?: continue
                    val toolCalls = mutableListOf<AiToolCall>()

                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)

                        if (part.has("functionCall")) {
                            val fc = part.getJSONObject("functionCall")
                            val name = fc.optString("name", "")
                            if (name.isNotEmpty()) {
                                toolCalls.add(
                                    AiToolCall(
                                        id = "fc_${name}_${System.currentTimeMillis()}",
                                        name = name,
                                        arguments = fc.optJSONObject("args")?.toMap() ?: emptyMap()
                                    )
                                )
                            }
                        }

                        if (part.has("text")) {
                            val text = part.optString("text", "")
                            if (text.isNotEmpty()) {
                                emittedAny = true
                                if (part.optBoolean("thought", false) && thinkingLevel != ThinkingLevel.NONE) {
                                    onEvent(AiStreamEvent(AiStreamEventType.REASONING_CHUNK, reasoningContent = text))
                                } else {
                                    onEvent(AiStreamEvent(AiStreamEventType.TEXT_CHUNK, content = text))
                                }
                            }
                        }
                    }

                    if (toolCalls.isNotEmpty()) {
                        emittedAny = true
                        onEvent(AiStreamEvent(AiStreamEventType.TOOL_CALLS, toolCalls = toolCalls))
                    }
                } catch (_: Exception) {
                    // skip malformed chunks
                }
            }
            reader.close()

            if (!emittedAny && lastFinish == null) {
                onEvent(AiStreamEvent(AiStreamEventType.ERROR, content = "لم يتم استلام رد من Gemini"))
            } else {
                onEvent(AiStreamEvent(AiStreamEventType.DONE, finishReason = lastFinish ?: "STOP"))
            }
        } catch (e: Exception) {
            onEvent(AiStreamEvent(AiStreamEventType.ERROR, content = e.message ?: "Unknown error"))
        }
    }

    private fun buildGeminiRequestBody(messages: List<AiChatMessage>, toolsJson: String?, thinkingLevel: ThinkingLevel): String {
        val json = JSONObject()
        val contents = JSONArray()
        var systemInstruction: String? = null

        for (msg in messages) {
            when (msg.role) {
                AiMessageRole.SYSTEM -> {
                    systemInstruction = msg.content
                }
                AiMessageRole.USER -> {
                    val content = JSONObject()
                    content.put("role", "user")
                    val parts = JSONArray()
                    if (msg.content.isNotEmpty()) {
                        parts.put(JSONObject().put("text", msg.content))
                    }
                    if (!msg.imageUrls.isNullOrEmpty()) {
                        for (b64 in msg.imageUrls) {
                            parts.put(
                                JSONObject().put(
                                    "inline_data",
                                    JSONObject()
                                        .put("mime_type", "image/jpeg")
                                        .put("data", b64)
                                )
                            )
                        }
                    }
                    if (parts.length() == 0) {
                        parts.put(JSONObject().put("text", " "))
                    }
                    content.put("parts", parts)
                    contents.put(content)
                }
                AiMessageRole.ASSISTANT -> {
                    val content = JSONObject()
                    content.put("role", "model")
                    val parts = JSONArray()
                    if (!msg.toolCalls.isNullOrEmpty()) {
                        for (tc in msg.toolCalls) {
                            parts.put(
                                JSONObject().put(
                                    "functionCall",
                                    JSONObject()
                                        .put("name", tc.name)
                                        .put("args", JSONObject(tc.arguments))
                                )
                            )
                        }
                    }
                    if (msg.content.isNotEmpty()) {
                        parts.put(JSONObject().put("text", msg.content))
                    }
                    if (parts.length() == 0) {
                        parts.put(JSONObject().put("text", " "))
                    }
                    content.put("parts", parts)
                    contents.put(content)
                }
                AiMessageRole.TOOL -> {
                    // Gemini function response format
                    val content = JSONObject()
                    content.put("role", "user")
                    val parts = JSONArray()
                    val responseObj = JSONObject()
                    try {
                        responseObj.put("result", JSONObject(msg.content))
                    } catch (_: Exception) {
                        responseObj.put("result", msg.content)
                    }
                    parts.put(
                        JSONObject().put(
                            "functionResponse",
                            JSONObject()
                                .put("name", msg.toolCallId ?: "tool")
                                .put("response", responseObj)
                        )
                    )
                    content.put("parts", parts)
                    contents.put(content)
                }
            }
        }

        if (contents.length() == 0) {
            contents.put(
                JSONObject()
                    .put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", "Hello")))
            )
        }

        json.put("contents", contents)

        if (!systemInstruction.isNullOrBlank()) {
            json.put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
            )
        }

        if (!toolsJson.isNullOrBlank()) {
            val functionDeclarations = JSONArray()
            val parsedArray = JSONArray(toolsJson)
            for (i in 0 until parsedArray.length()) {
                val tool = parsedArray.getJSONObject(i)
                functionDeclarations.put(
                    JSONObject()
                        .put("name", tool.getString("name"))
                        .put("description", tool.optString("description", ""))
                        .put("parameters", tool.optJSONObject("parameters") ?: JSONObject())
                )
            }
            json.put(
                "tools",
                JSONArray().put(JSONObject().put("functionDeclarations", functionDeclarations))
            )
        }

        val generationConfig = JSONObject()
            .put("temperature", 0.7)
            .put("maxOutputTokens", 8192)

        if (thinkingLevel != ThinkingLevel.NONE) {
            val budget = when (thinkingLevel) {
                ThinkingLevel.LOW -> 1024
                ThinkingLevel.MEDIUM -> 8192
                ThinkingLevel.HIGH -> 24576
                else -> 0
            }
            generationConfig.put(
                "thinkingConfig",
                JSONObject().put("thinkingBudget", budget)
            )
        }
        json.put("generationConfig", generationConfig)

        return json.toString()
    }

    private fun sanitizeError(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("<!DOCTYPE", ignoreCase = true) ||
            trimmed.startsWith("<html", ignoreCase = true)
        ) {
            return "استجابة HTML غير متوقعة — تحقق من المفتاح أو اسم الموديل"
        }
        return try {
            val json = JSONObject(trimmed)
            json.optJSONObject("error")?.optString("message")
                ?: json.optString("message").ifBlank { trimmed.take(300) }
        } catch (_: Exception) {
            trimmed.take(300)
        }
    }
}

private fun JSONObject.toMap(): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    keys().forEach { key ->
        val value = get(key)
        map[key] = when (value) {
            is JSONObject -> value.toMap()
            is JSONArray -> {
                (0 until value.length()).map { idx ->
                    val item = value[idx]
                    if (item is JSONObject) item.toMap() else item
                }
            }
            else -> value
        }
    }
    return map
}
