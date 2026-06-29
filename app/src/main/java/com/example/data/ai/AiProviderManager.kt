package com.example.data.ai

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object AiProviderManager {
    private const val PREFS_NAME = "ai_providers"
    private const val KEY_PROVIDERS = "providers_list"
    private const val KEY_MESSAGES_PREFIX = "ai_messages_"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasProvider(context: Context): Boolean {
        val json = prefs(context).getString(KEY_PROVIDERS, null) ?: return false
        return try {
            val arr = JSONArray(json)
            arr.length() > 0
        } catch (e: Exception) {
            false
        }
    }

    fun getProviders(context: Context): List<AiProvider> {
        val json = prefs(context).getString(KEY_PROVIDERS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { AiProvider.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveProviders(context: Context, providers: List<AiProvider>) {
        val arr = JSONArray()
        providers.forEach { arr.put(it.toJson()) }
        prefs(context).edit().putString(KEY_PROVIDERS, arr.toString()).apply()
    }

    fun getDefaultProvider(context: Context): AiProvider? {
        return getProviders(context).firstOrNull { it.isDefault }
            ?: getProviders(context).firstOrNull()
    }

    fun addProvider(context: Context, provider: AiProvider) {
        val list = getProviders(context).toMutableList()
        list.add(provider)
        saveProviders(context, list)
    }

    fun updateProvider(context: Context, provider: AiProvider) {
        val list = getProviders(context).toMutableList()
        val idx = list.indexOfFirst { it.id == provider.id }
        if (idx >= 0) list[idx] = provider
        saveProviders(context, list)
    }

    fun deleteProvider(context: Context, providerId: String) {
        val list = getProviders(context).toMutableList()
        list.removeAll { it.id == providerId }
        saveProviders(context, list)
    }

    // Messages persistence per provider
    fun saveMessages(context: Context, providerId: String, messages: List<ChatMessage>) {
        val arr = JSONArray()
        messages.forEach { msg ->
            arr.put(JSONObject().apply {
                put("role", msg.role)
                put("content", msg.content)
                put("timestamp", msg.timestamp)
            })
        }
        prefs(context).edit().putString(KEY_MESSAGES_PREFIX + providerId, arr.toString()).apply()
    }

    fun loadMessages(context: Context, providerId: String): List<ChatMessage> {
        val json = prefs(context).getString(KEY_MESSAGES_PREFIX + providerId, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val obj = arr.getJSONObject(it)
                ChatMessage(
                    role = obj.optString("role", "user"),
                    content = obj.optString("content", ""),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearMessages(context: Context, providerId: String) {
        prefs(context).edit().remove(KEY_MESSAGES_PREFIX + providerId).apply()
    }
}
