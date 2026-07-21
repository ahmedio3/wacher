package com.example.ai.provider

import com.example.ai.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class OpenAiCompatibleProvider(
    private val baseUrl: String,
    private val apiKey: String
) : AiProviderService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json".toMediaType()

    override suspend fun streamChat(
        messages: List<AiChatMessage>,
        model: String,
        tools: List<Map<String, Any>>?,
        reasoningEnabled: Boolean,
        onEvent: (AiStreamEvent) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/chat/completions"

            val body = buildOpenAiBody(messages, model, tools)

            val request = Request.Builder()
                .url(url)
                .post(body.toRequestBody(jsonMediaType))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $apiKey")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body ?: run {
                onEvent(AiStreamEvent(AiStreamEventType.ERROR, content = "Empty response body"))
                return@withContext
            }

            if (!response.isSuccessful) {
                val errorStr = responseBody.string()
                onEvent(AiStreamEvent(AiStreamEventType.ERROR, content = "API error ($baseUrl): $errorStr"))
                return@withContext
            }

            val reader = BufferedReader(InputStreamReader(responseBody.byteStream()))
            var line: String?
            var currentText = StringBuilder()
            var currentReasoning = StringBuilder()

            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                if (!l.startsWith("data: ")) continue
                val jsonStr = l.removePrefix("data: ").trim()
                if (jsonStr == "[DONE]" || jsonStr.isEmpty()) continue

                try {
                    val json = JSONObject(jsonStr)

                    val choices = json.optJSONArray("choices") ?: continue
                    if (choices.length() == 0) continue
                    val delta = choices.getJSONObject(0).optJSONObject("delta") ?: continue
                    val finishReason = choices.getJSONObject(0).optString("finish_reason", "")
                    if (finishReason == "stop") {
                        onEvent(AiStreamEvent(AiStreamEventType.DONE, finishReason = "STOP"))
                        continue
                    }

                    if (finishReason == "tool_calls") {
                        onEvent(AiStreamEvent(AiStreamEventType.DONE, finishReason = "TOOL_CALLS"))
                        continue
                    }

                    if (delta.has("reasoning_content")) {
                        val reasoning = delta.optString("reasoning_content", "")
                        if (reasoning.isNotEmpty()) {
                            currentReasoning.append(reasoning)
                            onEvent(AiStreamEvent(AiStreamEventType.REASONING_CHUNK, reasoningContent = reasoning))
                        }
                    }

                    if (delta.has("content")) {
                        val text = delta.optString("content", "")
                        if (text.isNotEmpty()) {
                            currentText.append(text)
                            onEvent(AiStreamEvent(AiStreamEventType.TEXT_CHUNK, content = text))
                        }
                    }

                    if (delta.has("tool_calls")) {
                        val toolCallsArray = delta.optJSONArray("tool_calls") ?: continue
                        val calls = mutableListOf<AiToolCall>()
                        for (i in 0 until toolCallsArray.length()) {
                            val tc = toolCallsArray.getJSONObject(i)
                            val func = tc.optJSONObject("function") ?: continue
                            val call = AiToolCall(
                                id = tc.optString("id", "tc_${i}_${System.currentTimeMillis()}"),
                                name = func.optString("name", ""),
                                arguments = try {
                                    JSONObject(func.optString("arguments", "{}")).toMap()
                                } catch (e: Exception) { emptyMap() }
                            )
                            calls.add(call)
                        }
                        if (calls.isNotEmpty()) {
                            onEvent(AiStreamEvent(AiStreamEventType.TOOL_CALLS, toolCalls = calls))
                        }
                    }
                } catch (e: Exception) {
                    // skip malformed chunks
                }
            }

            if (currentText.isEmpty() && currentReasoning.isEmpty()) {
                onEvent(AiStreamEvent(AiStreamEventType.DONE))
            }

            reader.close()
        } catch (e: Exception) {
            onEvent(AiStreamEvent(AiStreamEventType.ERROR, content = e.message ?: "Unknown error"))
        }
    }

    private fun buildOpenAiBody(
        messages: List<AiChatMessage>,
        model: String,
        tools: List<Map<String, Any>>?
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
                        contentArray.put(JSONObject().apply {
                            put("type", "text")
                            put("text", msg.content)
                        })
                        for (url in msg.imageUrls) {
                            val imageObj = JSONObject()
                            imageObj.put("type", "image_url")
                            imageObj.put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$url"))
                            contentArray.put(imageObj)
                        }
                        m.put("content", contentArray)
                    } else {
                        m.put("content", msg.content)
                    }
                }
                AiMessageRole.ASSISTANT -> {
                    m.put("role", "assistant")
                    if (msg.reasoningContent != null && reasoningEnabled) {
                        m.put("reasoning_content", msg.reasoningContent)
                    }
                    if (!msg.toolCalls.isNullOrEmpty()) {
                        val tcs = JSONArray()
                        for (tc in msg.toolCalls) {
                            val tcObj = JSONObject()
                            tcObj.put("id", tc.id)
                            tcObj.put("type", "function")
                            tcObj.put("function", JSONObject().apply {
                                put("name", tc.name)
                                put("arguments", JSONObject(tc.arguments).toString())
                            })
                            tcs.put(tcObj)
                        }
                        m.put("tool_calls", tcs)
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

        if (!tools.isNullOrEmpty()) {
            val toolsArray = JSONArray()
            for (tool in tools) {
                val t = JSONObject()
                t.put("type", "function")
                t.put("function", JSONObject().apply {
                    put("name", tool["name"])
                    put("description", tool["description"])
                    put("parameters", JSONObject(tool["parameters"] as? Map<*, *> ?: emptyMap<Any, Any>()))
                })
                toolsArray.put(t)
            }
            json.put("tools", toolsArray)
        }

        return json.toString()
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
