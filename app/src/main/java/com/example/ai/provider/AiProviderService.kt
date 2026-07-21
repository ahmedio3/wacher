package com.example.ai.provider

import com.example.ai.AiChatMessage
import com.example.ai.AiStreamEvent
import com.example.ai.AiToolCall

interface AiProviderService {
    suspend fun streamChat(
        messages: List<AiChatMessage>,
        model: String,
        tools: List<Map<String, Any>>? = null,
        reasoningEnabled: Boolean = false,
        onEvent: (AiStreamEvent) -> Unit
    )
}

fun buildToolDeclarations(): List<Map<String, Any>> {
    return listOf(
        mapOf(
            "name" to "search_tmdb",
            "description" to "Search for movies and TV shows on TMDB. Returns results with titles, overviews, ratings, and poster paths.",
            "parameters" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "query" to mapOf("type" to "string", "description" to "The search query (movie or TV show name)")
                ),
                "required" to listOf("query")
            )
        ),
        mapOf(
            "name" to "get_watchlist",
            "description" to "Get the user's watchlist (favorites). Returns saved movies and TV shows with their status.",
            "parameters" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "status" to mapOf("type" to "string", "description" to "Filter by status: PLAN_TO_WATCH, WATCHING, COMPLETED, or empty for all", "enum" to listOf("PLAN_TO_WATCH", "WATCHING", "COMPLETED", ""))
                ),
                "required" to listOf()
            )
        ),
        mapOf(
            "name" to "get_downloads",
            "description" to "Get the user's downloaded content list.",
            "parameters" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "status" to mapOf("type" to "string", "description" to "Filter by status: downloading, completed, paused, queued, or empty for all", "enum" to listOf("downloading", "completed", "paused", "queued", ""))
                ),
                "required" to listOf()
            )
        ),
        mapOf(
            "name" to "get_activity_log",
            "description" to "Get the user's recent activity history (watched, downloaded, etc.).",
            "parameters" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "limit" to mapOf("type" to "integer", "description" to "Number of recent activities to fetch (max 50)")
                ),
                "required" to listOf()
            )
        ),
        mapOf(
            "name" to "add_to_watchlist",
            "description" to "Add a movie or TV show to the user's watchlist (favorites). Requires tmdb_id and media_type.",
            "parameters" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "tmdb_id" to mapOf("type" to "integer", "description" to "The TMDB ID of the movie or TV show"),
                    "media_type" to mapOf("type" to "string", "description" to "Either 'movie' or 'tv'"),
                    "title" to mapOf("type" to "string", "description" to "The title of the media"),
                    "poster_path" to mapOf("type" to "string", "description" to "The poster path (e.g. /abc123.jpg)"),
                    "rating" to mapOf("type" to "number", "description" to "The rating out of 10")
                ),
                "required" to listOf("tmdb_id", "media_type", "title")
            )
        ),
        mapOf(
            "name" to "get_tmdb_details",
            "description" to "Get detailed information about a specific movie or TV show from TMDB including overview, genres, cast, seasons (for TV), and runtime (for movies).",
            "parameters" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "tmdb_id" to mapOf("type" to "integer", "description" to "The TMDB ID"),
                    "media_type" to mapOf("type" to "string", "description" to "Either 'movie' or 'tv'")
                ),
                "required" to listOf("tmdb_id", "media_type")
            )
        ),
        mapOf(
            "name" to "download_content",
            "description" to "Download a movie episode or TV show episode. This action requires user approval before execution.",
            "parameters" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "tmdb_id" to mapOf("type" to "integer", "description" to "The TMDB ID"),
                    "media_type" to mapOf("type" to "string", "description" to "Either 'movie' or 'tv'"),
                    "title" to mapOf("type" to "string", "description" to "The title"),
                    "season" to mapOf("type" to "integer", "description" to "Season number (0 for movies)"),
                    "episode" to mapOf("type" to "integer", "description" to "Episode number (0 for movies)"),
                    "quality" to mapOf("type" to "string", "description" to "Video quality: 720p, 1080p, 4k")
                ),
                "required" to listOf("tmdb_id", "media_type", "title")
            )
        )
    )
}
