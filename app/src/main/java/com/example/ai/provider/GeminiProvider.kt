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

class GeminiProvider(private val apiKey: String) : AiProviderService {

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
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse&key=$apiKey"

            val body = buildGeminiRequestBody(messages, tools, reasoningEnabled)

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

            if (!response.isSuccessful) {
                val errorStr = responseBody.string()
                onEvent(AiStreamEvent(AiStreamEventType.ERROR, content = "Gemini API error: $errorStr"))
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
                    val candidates = json.optJSONArray("candidates") ?: continue
                    if (candidates.length() == 0) continue
                    val candidate = candidates.getJSONObject(0)
                    val finishReason = candidate.optString("finishReason", "")
                    if (finishReason.isNotEmpty()) {
                        onEvent(AiStreamEvent(AiStreamEventType.DONE, finishReason = finishReason))
                        continue
                    }

                    val content = candidate.optJSONObject("content") ?: continue
                    val parts = content.optJSONArray("parts") ?: continue

                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)

                        if (part.has("functionCall")) {
                            val fc = part.getJSONObject("functionCall")
                            val call = AiToolCall(
                                id = fc.optString("name", "fc_${System.currentTimeMillis()}"),
                                name = fc.getString("name"),
                                arguments = fc.optJSONObject("args")?.toMap() ?: emptyMap()
                            )
                            onEvent(AiStreamEvent(AiStreamEventType.TOOL_CALLS, toolCalls = listOf(call)))
                        }

                        if (part.has("text")) {
                            val text = part.getString("text")
                            currentText.append(text)
                            onEvent(AiStreamEvent(AiStreamEventType.TEXT_CHUNK, content = text))
                        }

                        if (part.has("thought") && reasoningEnabled) {
                            val thought = part.getString("thought")
                            currentReasoning.append(thought)
                            onEvent(AiStreamEvent(AiStreamEventType.REASONING_CHUNK, reasoningContent = thought))
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

    private fun buildGeminiRequestBody(
        messages: List<AiChatMessage>,
        tools: List<Map<String, Any>>?,
        reasoningEnabled: Boolean
    ): String {
        val json = JSONObject()

        val contents = JSONArray()
        var systemInstruction: AiChatMessage? = null

        for (msg in messages) {
            if (msg.role == AiMessageRole.SYSTEM) {
                systemInstruction = msg
                continue
            }
            val content = JSONObject()
            content.put("role", if (msg.role == AiMessageRole.ASSISTANT) "model" else msg.role.value)

            val parts = JSONArray()

            if (msg.content.isNotEmpty()) {
                parts.put(JSONObject().put("text", msg.content))
            }

            if (!msg.imageUrls.isNullOrEmpty()) {
                for (url in msg.imageUrls) {
                    val inlineData = JSONObject()
                    inlineData.put("mimeType", "image/jpeg")
                    inlineData.put("data", url) // base64
                    parts.put(JSONObject().put("inlineData", inlineData))
                }
            }

            content.put("parts", parts)
            contents.put(content)
        }
        json.put("contents", contents)

        if (systemInstruction != null) {
            json.put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", systemInstruction.content)))
            })
        }

        if (!tools.isNullOrEmpty()) {
            val toolsArray = JSONArray()
            val functionDeclarations = JSONArray()
            for (tool in tools) {
                val fd = JSONObject()
                fd.put("name", tool["name"])
                fd.put("description", tool["description"])
                fd.put("parameters", JSONObject(tool["parameters"] as? Map<*, *> ?: emptyMap<Any, Any>()))
                functionDeclarations.put(fd)
            }
            toolsArray.put(JSONObject().put("functionDeclarations", functionDeclarations))
            json.put("tools", toolsArray)
        }

        val generationConfig = JSONObject()
        generationConfig.put("temperature", 0.7)
        generationConfig.put("maxOutputTokens", 8192)
        json.put("generationConfig", generationConfig)

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
