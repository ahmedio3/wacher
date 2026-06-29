package com.example.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class AiChatRepository {

    data class AiStreamEvent(
        val content: String = "",
        val isDone: Boolean = false,
        val error: String? = null
    )

    /**
     * Sends a streaming chat completion request to an OpenAI-compatible endpoint.
     */
    suspend fun chatCompletion(
        provider: AiProvider,
        model: AiModel,
        messages: List<ChatMessage>,
        onEvent: (AiStreamEvent) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val url = URL(provider.endpoint.trimEnd('/') + "/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer ${provider.apiKey}")
            conn.setRequestProperty("Accept", "text/event-stream")
            conn.doOutput = true
            conn.connectTimeout = 60000
            conn.readTimeout = 60000

            // Build request body
            val body = JSONObject().apply {
                put("model", model.name)
                put("stream", true)

                val msgsArr = JSONArray()
                messages.forEach { msg ->
                    msgsArr.put(JSONObject().apply {
                        put("role", msg.role)
                        put("content", msg.content)
                    })
                }
                put("messages", msgsArr)

                // Thinking effort
                if (model.thinkingEffort) {
                    put("thinking", JSONObject().apply {
                        put("type", "enabled")
                        put("budget_tokens", 4096)
                    })
                }
            }

            conn.outputStream.write(body.toString().toByteArray())

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                val errorBody = try {
                    conn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                } catch (e: Exception) { "HTTP $responseCode" }
                onEvent(AiStreamEvent(error = "خطأ $responseCode: $errorBody"))
                return@withContext
            }

            // Read SSE stream
            val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                if (l.startsWith("data: ")) {
                    val data = l.removePrefix("data: ").trim()
                    if (data == "[DONE]") {
                        onEvent(AiStreamEvent(isDone = true))
                        continue
                    }
                    try {
                        val json = JSONObject(data)
                        val choices = json.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val delta = choices.getJSONObject(0).optJSONObject("delta")
                            val content = delta?.optString("content", "") ?: ""
                            if (content.isNotEmpty()) {
                                onEvent(AiStreamEvent(content = content))
                            }
                            // Check finish reason
                            val finishReason = choices.getJSONObject(0).optString("finish_reason", "")
                            if (finishReason == "stop" || finishReason == "length") {
                                onEvent(AiStreamEvent(isDone = true))
                            }
                        }
                    } catch (e: Exception) {
                        // Skip malformed JSON
                    }
                }
            }
            reader.close()
            onEvent(AiStreamEvent(isDone = true))

        } catch (e: Exception) {
            onEvent(AiStreamEvent(error = "خطأ في الاتصال: ${e.localizedMessage ?: "يرجى التحقق من المزود"}"))
        }
    }

    /**
     * Performs a web search for a query (using a simulated search or API).
     * For now, returns a formatted search instruction for the AI.
     */
    fun buildWebSearchInstruction(query: String): String {
        return """[مطلوب بحث في الويب]
المستخدم يطلب معلومات عن: "$query"
يرجى استخدام معلوماتك الحالية للإجابة. إذا كنت لا تعرف الإجابة، اذكر ذلك بوضوح.
[نهاية طلب البحث]"""
    }
}
