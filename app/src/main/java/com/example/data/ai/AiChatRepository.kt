package com.example.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class AiChatRepository {

    data class AiStreamEvent(
        val content: String = "",
        val isDone: Boolean = false,
        val error: String? = null
    )

    // Accumulated tool calls from the stream
    private data class AccumulatedToolCall(
        val index: Int,
        val id: String,
        val type: String,
        val functionName: String,
        val arguments: StringBuilder
    )

    /**
     * Sends a streaming chat completion request to an OpenAI-compatible endpoint.
     * Handles web_search tool calling natively.
     */
    suspend fun chatCompletion(
        provider: AiProvider,
        model: AiModel,
        messages: List<ChatMessage>,
        onEvent: (AiStreamEvent) -> Unit
    ) = withContext(Dispatchers.IO) {
        // Start with the given messages, potentially do multi-turn for tool calls
        var currentMessages = messages
        var maxTurns = 5  // safety limit for tool-call loops
        var finalContent = StringBuilder()

        while (maxTurns > 0) {
            maxTurns--

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
                    currentMessages.forEach { msg ->
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

                    // Web search via tool calling
                    if (model.webSearch) {
                        val toolsArr = JSONArray()
                        toolsArr.put(JSONObject().apply {
                            put("type", "web_search")
                        })
                        put("tools", toolsArr)
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
                val toolCalls = mutableMapOf<Int, AccumulatedToolCall>()
                var hasToolCalls = false
                var turnContent = StringBuilder()

                while (reader.readLine().also { line = it } != null) {
                    val l = line ?: continue
                    if (l.startsWith("data: ")) {
                        val data = l.removePrefix("data: ").trim()
                        if (data == "[DONE]") {
                            // Stream done — handle tool calls after loop
                            break
                        }
                        try {
                            val json = JSONObject(data)
                            val choices = json.optJSONArray("choices")
                            if (choices != null && choices.length() > 0) {
                                val choice = choices.getJSONObject(0)
                                val delta = choice.optJSONObject("delta")

                                // Extract content
                                val content = delta?.optString("content", "") ?: ""
                                if (content.isNotEmpty()) {
                                    turnContent.append(content)
                                    finalContent.append(content)
                                    onEvent(AiStreamEvent(content = content))
                                }

                                // Extract tool_calls from delta
                                val deltaToolCalls = delta?.optJSONArray("tool_calls")
                                if (deltaToolCalls != null) {
                                    hasToolCalls = true
                                    for (i in 0 until deltaToolCalls.length()) {
                                        val tc = deltaToolCalls.getJSONObject(i)
                                        val index = tc.optInt("index", 0)
                                        val existing = toolCalls.getOrPut(index) {
                                            AccumulatedToolCall(
                                                index = index,
                                                id = tc.optString("id", ""),
                                                type = tc.optString("type", "function"),
                                                functionName = tc.optJSONObject("function")?.optString("name", "") ?: "",
                                                arguments = StringBuilder()
                                            )
                                        }
                                        if (existing.id.isEmpty()) {
                                            existing.id = tc.optString("id", "")
                                        }
                                        if (existing.type == "function") {
                                            existing.type = tc.optString("type", "function")
                                        }
                                        tc.optJSONObject("function")?.let { fn ->
                                            if (existing.functionName.isEmpty()) {
                                                existing.functionName = fn.optString("name", "")
                                            }
                                            existing.arguments.append(fn.optString("arguments", ""))
                                        }
                                    }
                                }

                                // Check finish reason
                                val finishReason = choice.optString("finish_reason", "")
                                if (finishReason == "stop") {
                                    break
                                }
                                if (finishReason == "length") {
                                    break
                                }
                                if (finishReason == "tool_calls") {
                                    break
                                }
                            }
                        } catch (e: Exception) {
                            // Skip malformed JSON
                        }
                    }
                }
                reader.close()

                // If we have tool calls, execute them and continue
                if (hasToolCalls && toolCalls.isNotEmpty()) {
                    // Build tool response messages
                    val toolResults = executeToolCalls(toolCalls.values.toList())

                    // Add the assistant message with tool_calls to the conversation
                    val assistantMsgContent = turnContent.toString()
                    val assistantMsg = buildToolCallMessage(toolCalls.values.toList(), assistantMsgContent)
                    currentMessages = currentMessages + ChatMessage(role = "assistant", content = assistantMsg)

                    // Add tool results
                    toolResults.forEach { result ->
                        currentMessages = currentMessages + ChatMessage(role = "tool", content = result)
                    }

                    // Continue the loop to get the model's response with tool results
                    continue
                }

                if (turnContent.isEmpty() && !hasToolCalls) {
                    // No content and no tool calls — check if there's a non-streaming response
                    // This can happen with some providers
                }

                // Normal completion — done
                onEvent(AiStreamEvent(isDone = true))
                return@withContext

            } catch (e: Exception) {
                onEvent(AiStreamEvent(error = "خطأ في الاتصال: ${e.localizedMessage ?: "يرجى التحقق من المزود"}"))
                return@withContext
            }
        }

        // If we exhausted max turns
        onEvent(AiStreamEvent(isDone = true))
    }

    /**
     * Execute tool calls and return results.
     */
    private fun executeToolCalls(toolCalls: List<AccumulatedToolCall>): List<String> {
        return toolCalls.map { tc ->
            try {
                when {
                    tc.type == "web_search" || tc.functionName.contains("web_search", ignoreCase = true) -> {
                        // Parse the search query from arguments
                        val args = JSONObject(tc.arguments.toString())
                        val query = args.optString("query", "")
                            .ifEmpty { args.optString("q", "") }
                            .ifEmpty { tc.arguments.toString() }
                        performWebSearch(query)
                    }
                    tc.functionName.contains("search", ignoreCase = true) -> {
                        val args = JSONObject(tc.arguments.toString())
                        val query = args.optString("query", "")
                            .ifEmpty { args.optString("q", "") }
                            .ifEmpty { tc.arguments.toString() }
                        performWebSearch(query)
                    }
                    else -> {
                        "{\"result\": \"unsupported tool: ${tc.functionName}\"}"
                    }
                }
            } catch (e: Exception) {
                "{\"error\": \"${e.localizedMessage ?: "tool execution failed"}\"}"
            }
        }
    }

    /**
     * Build a JSON string representing the assistant message with tool_calls.
     */
    private fun buildToolCallMessage(toolCalls: List<AccumulatedToolCall>, content: String): String {
        val obj = JSONObject()
        if (content.isNotEmpty()) {
            obj.put("content", content)
        }
        val tcArr = JSONArray()
        toolCalls.forEach { tc ->
            tcArr.put(JSONObject().apply {
                put("id", tc.id)
                put("type", tc.type)
                put("function", JSONObject().apply {
                    put("name", tc.functionName)
                    put("arguments", tc.arguments.toString())
                })
            })
        }
        obj.put("tool_calls", tcArr)
        return obj.toString()
    }

    /**
     * Perform a real web search and return results as a JSON string.
     * Uses DuckDuckGo-style search via a public API.
     */
    private fun performWebSearch(query: String): String {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            // Try multiple search APIs
            val results = searchDuckDuckGo(encodedQuery)
                .ifEmpty { searchGoogleUnofficial(encodedQuery) }
                .ifEmpty { "[{\"title\": \"No results\", \"snippet\": \"تعذر البحث عن: $query\", \"link\": \"\"}]" }
            "{\"results\": $results}"
        } catch (e: Exception) {
            "{\"error\": \"${e.localizedMessage ?: "search failed"}\"}"
        }
    }

    private fun searchDuckDuckGo(encodedQuery: String): String {
        return try {
            val searchUrl = URL("https://api.duckduckgo.com/?q=$encodedQuery&format=json&no_html=1")
            val conn = searchUrl.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            val body = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(body)

            val results = JSONArray()
            val abstractText = json.optString("AbstractText", "")
            if (abstractText.isNotEmpty()) {
                results.put(JSONObject().apply {
                    put("title", json.optString("Heading", "ملخص"))
                    put("snippet", abstractText)
                    put("link", json.optString("AbstractURL", ""))
                })
            }
            json.optJSONArray("RelatedTopics")?.let { topics ->
                for (i in 0 until topics.length().coerceAtMost(5)) {
                    val topic = topics.getJSONObject(i)
                    val text = topic.optString("Text", "")
                    val firstUrl = topic.optString("FirstURL", "")
                    if (text.isNotEmpty()) {
                        results.put(JSONObject().apply {
                            put("title", text.substringBefore(" - "))
                            put("snippet", text)
                            put("link", firstUrl)
                        })
                    }
                }
            }
            results.toString()
        } catch (e: Exception) {
            ""
        }
    }

    private fun searchGoogleUnofficial(encodedQuery: String): String {
        return try {
            // Fallback: try a different search source
            val searchUrl = URL("https://html.duckduckgo.com/html/?q=$encodedQuery")
            val conn = searchUrl.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            val body = conn.inputStream.bufferedReader().readText()
            // Simple extraction of result snippets from HTML (last resort)
            val results = JSONArray()
            val snippetRegex = Regex("""class="result__snippet"[^>]*>(.*?)</a>""")
            snippetRegex.findAll(body).take(3).forEach { match ->
                val snippet = match.groupValues[1].replace(Regex("<[^>]*>"), "")
                results.put(JSONObject().apply {
                    put("title", "")
                    put("snippet", snippet)
                    put("link", "")
                })
            }
            results.toString()
        } catch (e: Exception) {
            ""
        }
    }
}
