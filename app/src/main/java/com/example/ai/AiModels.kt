package com.example.ai

import org.json.JSONArray
import org.json.JSONObject

enum class AiProviderType(val displayName: String) {
    GEMINI("Gemini"),
    OPENCODE_ZEN("OpenCode Zen"),
    BYNARA("Bynara"),
    AGNES_AI("Agnes AI")
}

/**
 * Thinking effort levels sent to providers in real API requests.
 */
enum class ThinkingLevel(val label: String, val key: String) {
    NONE("بدون", "none"),
    LOW("منخفض", "low"),
    MEDIUM("متوسط", "medium"),
    HIGH("مرتفع", "high");

    companion object {
        fun fromKey(key: String): ThinkingLevel =
            entries.find { it.key == key } ?: NONE
    }
}

data class AiModelConfig(
    val id: String,
    val displayName: String,
    val providerType: AiProviderType,
    val supportsVision: Boolean = false,
    val supportsReasoning: Boolean = true,
    val reasoningLevels: List<ThinkingLevel> = listOf(
        ThinkingLevel.NONE, ThinkingLevel.LOW, ThinkingLevel.MEDIUM, ThinkingLevel.HIGH
    )
)

data class AiProviderConfig(
    val type: AiProviderType,
    val apiKey: String,
    val baseUrl: String? = null,
    val models: List<AiModelConfig>
)

data class AiChatMessage(
    val role: AiMessageRole,
    val content: String = "",
    val reasoningContent: String? = null,
    val toolCalls: List<AiToolCall>? = null,
    val toolCallId: String? = null,
    val imageUrls: List<String>? = null
)

enum class AiMessageRole(val value: String) {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
    TOOL("tool");

    companion object {
        fun fromValue(value: String): AiMessageRole =
            entries.find { it.value == value } ?: USER
    }
}

data class AiToolCall(
    val id: String,
    val name: String,
    val arguments: Map<String, Any>
)

data class AiToolResult(
    val toolCallId: String,
    val name: String,
    val content: String
)

data class AiStreamEvent(
    val type: AiStreamEventType,
    val content: String = "",
    val reasoningContent: String? = null,
    val toolCalls: List<AiToolCall>? = null,
    val toolResults: List<AiToolResult>? = null,
    val finishReason: String? = null
)

enum class AiStreamEventType {
    TEXT_CHUNK,
    REASONING_CHUNK,
    TOOL_CALLS,
    TOOL_RESULTS,
    DONE,
    ERROR
}

fun jsonToToolCalls(json: String?): List<AiToolCall>? {
    if (json == null) return null
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            AiToolCall(
                id = obj.getString("id"),
                name = obj.getString("name"),
                arguments = obj.getJSONObject("arguments").toMap()
            )
        }
    } catch (e: Exception) { null }
}

fun toolCallsToJson(calls: List<AiToolCall>?): String? {
    if (calls == null) return null
    val arr = JSONArray()
    calls.forEach { call ->
        val obj = JSONObject()
        obj.put("id", call.id)
        obj.put("name", call.name)
        obj.put("arguments", JSONObject(call.arguments))
        arr.put(obj)
    }
    return arr.toString()
}

fun jsonToToolResults(json: String?): List<AiToolResult>? {
    if (json == null) return null
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            AiToolResult(
                toolCallId = obj.getString("toolCallId"),
                name = obj.getString("name"),
                content = obj.getString("content")
            )
        }
    } catch (e: Exception) { null }
}

fun toolResultsToJson(results: List<AiToolResult>?): String? {
    if (results == null) return null
    val arr = JSONArray()
    results.forEach { result ->
        val obj = JSONObject()
        obj.put("toolCallId", result.toolCallId)
        obj.put("name", result.name)
        obj.put("content", result.content)
        arr.put(obj)
    }
    return arr.toString()
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

val PROVIDER_CONFIGS: Map<AiProviderType, AiProviderConfig> = mapOf(
    AiProviderType.GEMINI to AiProviderConfig(
        type = AiProviderType.GEMINI,
        apiKey = "",
        models = listOf(
            AiModelConfig("gemini-3.1-flash-lite", "Gemini 3.1 Flash Lite", AiProviderType.GEMINI, supportsVision = true),
            AiModelConfig("gemini-3.5-flash", "Gemini 3.5 Flash", AiProviderType.GEMINI)
        )
    ),
    AiProviderType.OPENCODE_ZEN to AiProviderConfig(
        type = AiProviderType.OPENCODE_ZEN,
        apiKey = "",
        baseUrl = "https://opencode.ai/zen/v1",
        models = listOf(
            AiModelConfig("nemotron-3-ultra-free", "Nemotron 3 Ultra", AiProviderType.OPENCODE_ZEN),
            AiModelConfig("north-mini-code-free", "North Mini Code", AiProviderType.OPENCODE_ZEN),
            AiModelConfig("laguna-s-2.1-free", "Laguna S 2.1 Free", AiProviderType.OPENCODE_ZEN, supportsReasoning = true)
        )
    ),
    AiProviderType.BYNARA to AiProviderConfig(
        type = AiProviderType.BYNARA,
        apiKey = "",
        baseUrl = "https://router.bynara.id/v1",
        models = listOf(
            AiModelConfig("grok-4.5", "Grok 4.5", AiProviderType.BYNARA, supportsVision = true),
            AiModelConfig("mistral-large", "Mistral Large", AiProviderType.BYNARA),
            AiModelConfig("mistral-medium-3-5", "Mistral Medium 3.5", AiProviderType.BYNARA, supportsVision = true)
        )
    ),
    AiProviderType.AGNES_AI to AiProviderConfig(
        type = AiProviderType.AGNES_AI,
        apiKey = "",
        baseUrl = "https://apihub.agnes-ai.com/v1",
        models = listOf(
            AiModelConfig("agnes-2.0-flash", "Agnes 2.0 Flash", AiProviderType.AGNES_AI, supportsVision = true, supportsReasoning = true)
        )
    )
)

fun getDefaultModel(): AiModelConfig = PROVIDER_CONFIGS[AiProviderType.AGNES_AI]!!.models.first()
