package com.example.ai.provider

import com.example.ai.AiChatMessage
import com.example.ai.AiStreamEvent
import com.example.ai.ThinkingLevel
import org.json.JSONArray
import org.json.JSONObject

interface AiProviderService {
    suspend fun streamChat(
        messages: List<AiChatMessage>,
        model: String,
        toolsJson: String? = null,
        thinkingLevel: ThinkingLevel = ThinkingLevel.NONE,
        onEvent: (AiStreamEvent) -> Unit
    )
}

fun buildToolDeclarationsJson(): String {
    val tools = JSONArray()

    fun tool(name: String, description: String, params: JSONObject): JSONObject {
        return JSONObject()
            .put("name", name)
            .put("description", description)
            .put("parameters", params)
    }

    fun objProps(vararg pairs: Pair<String, JSONObject>): JSONObject {
        val props = JSONObject()
        pairs.forEach { (k, v) -> props.put(k, v) }
        return JSONObject().put("type", "object").put("properties", props)
    }

    fun prop(type: String, desc: String): JSONObject =
        JSONObject().put("type", type).put("description", desc)

    tools.put(
        tool(
            "search_tmdb",
            "Search for movies and TV shows on TMDB.",
            objProps("query" to prop("string", "Search query")).put("required", JSONArray(listOf("query")))
        )
    )
    tools.put(
        tool(
            "get_watchlist",
            "Get the user's watchlist (favorites).",
            objProps("status" to prop("string", "PLAN_TO_WATCH, WATCHING, COMPLETED, or empty for all"))
                .put("required", JSONArray())
        )
    )
    tools.put(
        tool(
            "get_downloads",
            "Get the user's downloaded content list.",
            objProps("status" to prop("string", "downloading, completed, paused, queued, or empty"))
                .put("required", JSONArray())
        )
    )
    tools.put(
        tool(
            "get_activity_log",
            "Get the user's recent activity history.",
            objProps("limit" to prop("integer", "Number of activities (max 50)")).put("required", JSONArray())
        )
    )
    tools.put(
        tool(
            "add_to_watchlist",
            "Add a movie or TV show to the user's watchlist.",
            objProps(
                "tmdb_id" to prop("integer", "TMDB ID"),
                "media_type" to prop("string", "movie or tv"),
                "title" to prop("string", "Title"),
                "poster_path" to prop("string", "Poster path"),
                "rating" to prop("number", "Rating out of 10")
            ).put("required", JSONArray(listOf("tmdb_id", "media_type", "title")))
        )
    )
    tools.put(
        tool(
            "get_tmdb_details",
            "Get detailed information about a movie or TV show from TMDB.",
            objProps(
                "tmdb_id" to prop("integer", "TMDB ID"),
                "media_type" to prop("string", "movie or tv")
            ).put("required", JSONArray(listOf("tmdb_id", "media_type")))
        )
    )
    tools.put(
        tool(
            "download_content",
            "Download a movie or TV episode. Requires user approval.",
            objProps(
                "tmdb_id" to prop("integer", "TMDB ID"),
                "media_type" to prop("string", "movie or tv"),
                "title" to prop("string", "Title"),
                "season" to prop("integer", "Season (0 for movies)"),
                "episode" to prop("integer", "Episode (0 for movies)"),
                "quality" to prop("string", "720p, 1080p, 4k")
            ).put("required", JSONArray(listOf("tmdb_id", "media_type", "title")))
        )
    )

    return tools.toString()
}
