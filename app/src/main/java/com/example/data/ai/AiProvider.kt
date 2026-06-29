package com.example.data.ai

import org.json.JSONArray
import org.json.JSONObject

data class AiProvider(
    val id: String = java.util.UUID.randomUUID().toString(),
    val displayName: String = "",
    val endpoint: String = "",
    val apiKey: String = "",
    val models: List<AiModel> = emptyList(),
    val isDefault: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("displayName", displayName)
        put("endpoint", endpoint)
        put("apiKey", apiKey)
        put("isDefault", isDefault)
        val modelsArr = JSONArray()
        models.forEach { modelsArr.put(it.toJson()) }
        put("models", modelsArr)
    }

    companion object {
        fun fromJson(json: JSONObject): AiProvider = AiProvider(
            id = json.optString("id", java.util.UUID.randomUUID().toString()),
            displayName = json.optString("displayName", ""),
            endpoint = json.optString("endpoint", ""),
            apiKey = json.optString("apiKey", ""),
            isDefault = json.optBoolean("isDefault", false),
            models = run {
                val arr = json.optJSONArray("models") ?: JSONArray()
                (0 until arr.length()).map { AiModel.fromJson(arr.getJSONObject(it)) }
            }
        )
    }
}

data class AiModel(
    val name: String = "",
    val thinkingEffort: Boolean = false,
    val webSearch: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("thinkingEffort", thinkingEffort)
        put("webSearch", webSearch)
    }

    companion object {
        fun fromJson(json: JSONObject): AiModel = AiModel(
            name = json.optString("name", ""),
            thinkingEffort = json.optBoolean("thinkingEffort", false),
            webSearch = json.optBoolean("webSearch", false)
        )
    }
}

data class ChatMessage(
    val role: String,  // "user", "assistant", "system"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
