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

class OpenAiCompatibleProvider(
    private val baseUrl: String,
    private val apiKey: String,
    private val providerName: String = "OpenAI"
) : AiProviderService {

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
        reasoningEnabled: Boolean,
        onEvent: (AiStreamEvent) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            onEvent(
                AiStreamEvent(
                    AiStreamEventType.ERROR,
                    content = "مفتاح $providerName API غير مضبوط"
                )
            )
            return@withContext
        }

        try {
            val endpoint = baseUrl.trimEnd('/') + "/chat/completions"
            val body = buildOpenAiBody(messages, model, toolsJson)

            val request = Request.Builder()
                .url(endpoint)
                .post(body.toRequestBody(jsonMediaType))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $apiKey")
                .header("Accept", "text/event-stream")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body ?: run {
                onEvent(AiStreamEvent(AiStreamEventType.ERROR, content = "Empty response body"))
                return@withContext
            }

            if (!response.isSuccessful) {
                val errorStr = sanitizeError(responseBody.string())
                onEvent(
                    AiStreamEvent(
                        AiStreamEventType.ERROR,
                        content = "$providerName error (${response.code}): $errorStr"
                    )
                )
                return@withContext
            }

            val contentType = response.header("Content-Type") ?: ""
            // Non-streaming JSON response
            if (contentType.contains("application/json") && !contentType.contains("event-stream")) {
                handleNonStreamJson(responseBody.string(), onEvent)
                return@withContext
            }

            val reader = BufferedReader(InputStreamReader(responseBody.byteStream()))
            var line: String?
            var emittedAny = false
            // Accumulate partial tool call deltas
            val toolCallBuffers = mutableMapOf<Int, ToolCallBuffer>()
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

                // HTML error mid-stream
                if (jsonStr.startsWith("<!DOCTYPE", ignoreCase = true) ||
                    jsonStr.startsWith("<html", ignoreCase = true)
                ) {
                    onEvent(
                        AiStreamEvent(
                            AiStreamEventType.ERROR,
                            content = "$providerName: استجابة HTML — تحقق من الـ endpoint أو المفتاح"
                        )
                    )
                    reader.close()
                    return@withContext
                }

                try {
                    val json = JSONObject(jsonStr)

                    if (json.has("error")) {
                        val err = json.optJSONObject("error")
                        val msg = err?.optString("message")
                            ?: err?.optString("code")
                            ?: json.toString()
                        onEvent(AiStreamEvent(AiStreamEventType.ERROR, content = msg))
                        reader.close()
                        return@withContext
                    }

                    val choices = json.optJSONArray("choices") ?: continue
                    if (choices.length() == 0) continue
                    val choice = choices.getJSONObject(0)
                    val finishReason = choice.optString("finish_reason", "")
                    if (finishReason.isNotEmpty() && finishReason != "null") {
                        lastFinish = finishReason
                    }

                    val delta = choice.optJSONObject("delta")
                        ?: choice.optJSONObject("message")
                        ?: continue

                    if (delta.has("reasoning_content")) {
                        val reasoning = delta.optString("reasoning_content", "")
                        if (reasoning.isNotEmpty()) {
                            emittedAny = true
                            onEvent(AiStreamEvent(AiStreamEventType.REASONING_CHUNK, reasoningContent = reasoning))
                        }
                    }

                    // Some models put reasoning in "reasoning"
                    if (delta.has("reasoning")) {
                        val reasoning = delta.optString("reasoning", "")
                        if (reasoning.isNotEmpty()) {
                            emittedAny = true
                            onEvent(AiStreamEvent(AiStreamEventType.REASONING_CHUNK, reasoningContent = reasoning))
                        }
                    }

                    if (delta.has("content") && !delta.isNull("content")) {
                        val text = delta.optString("content", "")
                        if (text.isNotEmpty()) {
                            emittedAny = true
                            onEvent(AiStreamEvent(AiStreamEventType.TEXT_CHUNK, content = text))
                        }
                    }

                    if (delta.has("tool_calls")) {
                        val toolCallsArray = delta.optJSONArray("tool_calls") ?: continue
                        for (i in 0 until toolCallsArray.length()) {
                            val tc = toolCallsArray.getJSONObject(i)
                            val index = tc.optInt("index", i)
                            val buffer = toolCallBuffers.getOrPut(index) { ToolCallBuffer() }
                            if (tc.has("id") && tc.optString("id").isNotEmpty()) {
                                buffer.id = tc.optString("id")
                            }
                            val func = tc.optJSONObject("function")
                            if (func != null) {
                                if (func.has("name") && func.optString("name").isNotEmpty()) {
                                    buffer.name = func.optString("name")
                                }
                                if (func.has("arguments")) {
                                    buffer.argumentsJson.append(func.optString("arguments", ""))
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    // skip malformed chunks
                }
            }
            reader.close()

            // Emit completed tool calls
            if (toolCallBuffers.isNotEmpty()) {
                val calls = toolCallBuffers.values.mapNotNull { buf ->
                    if (buf.name.isEmpty()) return@mapNotNull null
                    val args = try {
                        JSONObject(buf.argumentsJson.toString().ifBlank { "{}" }).toMap()
                    } catch (_: Exception) {
                        emptyMap()
                    }
                    AiToolCall(
                        id = buf.id.ifEmpty { "tc_${buf.name}_${System.currentTimeMillis()}" },
                        name = buf.name,
                        arguments = args
                    )
                }
                if (calls.isNotEmpty()) {
                    emittedAny = true
                    onEvent(AiStreamEvent(AiStreamEventType.TOOL_CALLS, toolCalls = calls))
                    onEvent(AiStreamEvent(AiStreamEventType.DONE, finishReason = "TOOL_CALLS"))
                    return@withContext
                }
            }

            if (!emittedAny) {
                onEvent(
                    AiStreamEvent(
                        AiStreamEventType.ERROR,
                        content = "لم يتم استلام رد من $providerName"
                    )
                )
            } else {
                onEvent(AiStreamEvent(AiStreamEventType.DONE, finishReason = lastFinish ?: "STOP"))
            }
        } catch (e: Exception) {
            onEvent(AiStreamEvent(AiStreamEventType.ERROR, content = e.message ?: "Unknown error"))
        }
    }

    private fun handleNonStreamJson(raw: String, onEvent: (AiStreamEvent) -> Unit) {
        if (raw.trim().startsWith("<!DOCTYPE", ignoreCase = true) ||
            raw.trim().startsWith("<html", ignoreCase = true)
        ) {
            onEvent(
                AiStreamEvent(
                    AiStreamEventType.ERROR,
                    content = "$providerName: استجابة HTML — تحقق من الـ endpoint أو المفتاح"
                )
            )
            return
        }
        try {
            val json = JSONObject(raw)
            if (json.has("error")) {
                val err = json.optJSONObject("error")
                onEvent(
                    AiStreamEvent(
                        AiStreamEventType.ERROR,
                        content = err?.optString("message") ?: raw.take(300)
                    )
                )
                return
            }
            val choices = json.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                onEvent(AiStreamEvent(AiStreamEventType.ERROR, content = "Empty choices from $providerName"))
                return
            }
            val message = choices.getJSONObject(0).optJSONObject("message")
            val content = message?.optString("content") ?: ""
            val reasoning = message?.optString("reasoning_content")
                ?: message?.optString("reasoning")
                ?: ""

            if (reasoning.isNotEmpty()) {
                onEvent(AiStreamEvent(AiStreamEventType.REASONING_CHUNK, reasoningContent = reasoning))
            }
            if (content.isNotEmpty()) {
                onEvent(AiStreamEvent(AiStreamEventType.TEXT_CHUNK, content = content))
            }

            val toolCalls = message?.optJSONArray("tool_calls")
            if (toolCalls != null && toolCalls.length() > 0) {
                val calls = mutableListOf<AiToolCall>()
                for (i in 0 until toolCalls.length()) {
                    val tc = toolCalls.getJSONObject(i)
                    val func = tc.optJSONObject("function") ?: continue
                    calls.add(
                        AiToolCall(
                            id = tc.optString("id", "tc_$i"),
                            name = func.optString("name", ""),
                            arguments = try {
                                JSONObject(func.optString("arguments", "{}")).toMap()
                            } catch (_: Exception) {
                                emptyMap()
                            }
                        )
                    )
                }
                if (calls.isNotEmpty()) {
                    onEvent(AiStreamEvent(AiStreamEventType.TOOL_CALLS, toolCalls = calls))
                    onEvent(AiStreamEvent(AiStreamEventType.DONE, finishReason = "TOOL_CALLS"))
                    return
                }
            }

            onEvent(AiStreamEvent(AiStreamEventType.DONE, finishReason = "STOP"))
        } catch (e: Exception) {
            onEvent(AiStreamEvent(AiStreamEventType.ERROR, content = sanitizeError(raw)))
        }
    }

    private fun buildOpenAiBody(
        messages: List<AiChatMessage>,
        model: String,
        toolsJson: String?
    ): String {
        val json = JSONObject()
        json.put("model", model)
        json.put("stream", true)

        val msgsArray = JSONArray()
        for (msg in messages) {
            val m = JSONObject()
            when (msg.role) {
                AiMessageRole.SYSTEM -> {
                    m.put("role", "system")
                    m.put("content", msg.content)
                }
                AiMessageRole.USER -> {
                    m.put("role", "user")
                    if (!msg.imageUrls.isNullOrEmpty()) {
                        val contentArray = JSONArray()
                        if (msg.content.isNotEmpty()) {
                            contentArray.put(
                                JSONObject()
                                    .put("type", "text")
                                    .put("text", msg.content)
                            )
                        }
                        for (b64 in msg.imageUrls) {
                            contentArray.put(
                                JSONObject()
                                    .put("type", "image_url")
                                    .put(
                                        "image_url",
                                        JSONObject().put("url", "data:image/jpeg;base64,$b64")
                                    )
                            )
                        }
                        m.put("content", contentArray)
                    } else {
                        m.put("content", msg.content)
                    }
                }
                AiMessageRole.ASSISTANT -> {
                    m.put("role", "assistant")
                    if (!msg.toolCalls.isNullOrEmpty()) {
                        val tcs = JSONArray()
                        for (tc in msg.toolCalls) {
                            tcs.put(
                                JSONObject()
                                    .put("id", tc.id)
                                    .put("type", "function")
                                    .put(
                                        "function",
                                        JSONObject()
                                            .put("name", tc.name)
                                            .put("arguments", JSONObject(tc.arguments).toString())
                                    )
                            )
                        }
                        m.put("tool_calls", tcs)
                        if (msg.content.isNotEmpty()) m.put("content", msg.content)
                    } else {
                        m.put("content", msg.content)
                    }
                }
                AiMessageRole.TOOL -> {
                    m.put("role", "tool")
                    m.put("content", msg.content)
                    if (msg.toolCallId != null) {
                        m.put("tool_call_id", msg.toolCallId)
                    }
                }
            }
            msgsArray.put(m)
        }
        json.put("messages", msgsArray)

        if (!toolsJson.isNullOrBlank()) {
            val toolsArray = JSONArray()
            val parsedArray = JSONArray(toolsJson)
            for (i in 0 until parsedArray.length()) {
                val tool = parsedArray.getJSONObject(i)
                toolsArray.put(
                    JSONObject()
                        .put("type", "function")
                        .put(
                            "function",
                            JSONObject()
                                .put("name", tool.getString("name"))
                                .put("description", tool.optString("description", ""))
                                .put("parameters", tool.optJSONObject("parameters") ?: JSONObject())
                        )
                )
            }
            json.put("tools", toolsArray)
        }

        return json.toString()
    }

    private fun sanitizeError(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("<!DOCTYPE", ignoreCase = true) ||
            trimmed.startsWith("<html", ignoreCase = true)
        ) {
            return "استجابة HTML غير متوقعة — تحقق من الـ endpoint أو المفتاح"
        }
        return try {
            val json = JSONObject(trimmed)
            json.optJSONObject("error")?.optString("message")
                ?: json.optString("message").ifBlank { trimmed.take(300) }
        } catch (_: Exception) {
            trimmed.take(300)
        }
    }

    private data class ToolCallBuffer(
        var id: String = "",
        var name: String = "",
        val argumentsJson: StringBuilder = StringBuilder()
    )
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
