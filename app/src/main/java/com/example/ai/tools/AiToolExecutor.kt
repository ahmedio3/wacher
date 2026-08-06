package com.example.ai.tools

import android.content.Context
import com.example.BuildConfig
import com.example.ai.AiToolResult
import com.example.data.local.DownloadEntity
import com.example.data.local.MovieDatabase
import com.example.data.local.WatchlistEntity
import com.example.data.remote.RetrofitClient
import com.example.data.remote.TmdbMovieDetails
import com.example.data.remote.TmdbTvDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

sealed class ToolResult {
    data class Success(val data: String) : ToolResult()
    data class Error(val message: String) : ToolResult()
    data class NeedsApproval(val description: String, val executeData: Map<String, Any>) : ToolResult()
}

class AiToolExecutor(private val context: Context) {

    private val database = MovieDatabase.getDatabase(context)
    private val repository = com.example.data.repository.MovieRepository(database.movieDao)
    private val tmdbService = RetrofitClient.tmdbService

    suspend fun executeTool(name: String, args: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        try {
            when (name) {
                "search_tmdb" -> searchTmdb(args)
                "get_watchlist" -> getWatchlist(args)
                "get_downloads" -> getDownloads(args)
                "add_to_watchlist" -> addToWatchlist(args)
                "get_tmdb_details" -> getTmdbDetails(args)
                else -> ToolResult.Error("Unknown tool: $name")
            }
        } catch (e: Exception) {
            ToolResult.Error("Error executing $name: ${e.message}")
        }
    }

    private suspend fun searchTmdb(args: Map<String, Any>): ToolResult {
        val query = args["query"]?.toString() ?: return ToolResult.Error("Missing query parameter")
        val response = tmdbService.searchMulti(
            apiKey = BuildConfig.TMDB_API_KEY,
            query = query,
            language = "en"
        )
        val results = response.results?.filter {
            it.mediaType == "movie" || it.mediaType == "tv"
        }?.take(8) ?: emptyList()

        if (results.isEmpty()) return ToolResult.Success("لا توجد نتائج لـ \"$query\"")

        val json = JSONArray()
        for (item in results) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("title", item.title ?: item.name ?: "Unknown")
            obj.put("media_type", item.mediaType ?: "unknown")
            obj.put("overview", item.overview ?: "")
            obj.put("rating", item.voteAverage ?: 0.0)
            obj.put("release_date", item.releaseDate ?: item.firstAirDate ?: "")
            obj.put("poster_path", item.posterPath ?: "")
            json.put(obj)
        }
        return ToolResult.Success(json.toString(2))
    }

    private suspend fun getWatchlist(args: Map<String, Any>): ToolResult {
        val statusFilter = args["status"]?.toString()?.ifBlank { null }
        val allItems = database.movieDao.getAllWatchlistItems()
        val filtered = if (statusFilter != null) allItems.filter { it.status == statusFilter } else allItems

        if (filtered.isEmpty()) return ToolResult.Success("قائمة المشاهدة فارغة")

        val json = JSONArray()
        for (item in filtered) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("title", item.title)
            obj.put("media_type", item.mediaType)
            obj.put("status", item.status)
            obj.put("rating", item.rating)
            obj.put("poster_path", item.posterPath)
            json.put(obj)
        }
        return ToolResult.Success(json.toString(2))
    }

    private suspend fun getDownloads(args: Map<String, Any>): ToolResult {
        val statusFilter = args["status"]?.toString()?.ifBlank { null }
        val allDownloads = mutableListOf<DownloadEntity>()
        try {
            database.movieDao.getDownloads().collect { allDownloads.addAll(it) }
        } catch (_: Exception) {}
        val filtered = if (statusFilter != null) allDownloads.filter { it.status == statusFilter } else allDownloads

        if (filtered.isEmpty()) return ToolResult.Success("لا توجد تحميلات")

        val json = JSONArray()
        for (item in filtered) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("title", item.title)
            obj.put("media_type", item.mediaType)
            obj.put("status", item.status)
            obj.put("progress", item.progress)
            obj.put("quality", item.quality)
            json.put(obj)
        }
        return ToolResult.Success(json.toString(2))
    }

    private suspend fun addToWatchlist(args: Map<String, Any>): ToolResult {
        val tmdbId = args["tmdb_id"]?.toString() ?: return ToolResult.Error("Missing tmdb_id")
        val mediaType = args["media_type"]?.toString() ?: return ToolResult.Error("Missing media_type")
        val title = args["title"]?.toString() ?: return ToolResult.Error("Missing title")
        val posterPath = args["poster_path"]?.toString() ?: ""
        val rating = (args["rating"] as? Number)?.toDouble() ?: 0.0

        val existing = repository.getWatchlistById(tmdbId)
        if (existing != null && !existing.isDeleted) {
            return ToolResult.Success("العنوان \"$title\" موجود بالفعل في قائمة المشاهدة")
        }

        repository.addToWatchlist(
            WatchlistEntity(
                id = tmdbId,
                title = title,
                posterPath = posterPath,
                mediaType = mediaType,
                rating = rating,
                status = "PLAN_TO_WATCH",
                isDeleted = false
            )
        )
        return ToolResult.Success("تم إضافة \"$title\" إلى قائمة المشاهدة بنجاح ✅")
    }

    private suspend fun getTmdbDetails(args: Map<String, Any>): ToolResult {
        val tmdbId = (args["tmdb_id"] as? Number)?.toInt()
            ?: return ToolResult.Error("Missing or invalid tmdb_id")
        val mediaType = args["media_type"]?.toString() ?: return ToolResult.Error("Missing media_type")

        return if (mediaType == "movie") {
            val details = tmdbService.getMovieDetails(
                movieId = tmdbId,
                apiKey = BuildConfig.TMDB_API_KEY,
                language = "en",
                appendToResponse = "credits"
            )
            formatMovieDetails(details)
        } else {
            val details = tmdbService.getTvDetails(
                tvId = tmdbId,
                apiKey = BuildConfig.TMDB_API_KEY,
                language = "en",
                appendToResponse = "credits"
            )
            formatTvDetails(details)
        }
    }

    private fun formatMovieDetails(details: TmdbMovieDetails): ToolResult {
        val json = JSONObject()
        json.put("id", details.id)
        json.put("title", details.title ?: "Unknown")
        json.put("overview", details.overview ?: "")
        json.put("rating", details.voteAverage ?: 0.0)
        json.put("release_date", details.releaseDate ?: "")
        json.put("runtime_minutes", details.runtime ?: 0)
        val genreNames: List<String> = details.genres?.mapNotNull { it.name } ?: emptyList()
        json.put("genres", JSONArray(genreNames))
        val castNames: List<String> = details.credits?.cast?.take(5)?.mapNotNull { it.name } ?: emptyList()
        json.put("cast", JSONArray(castNames))
        return ToolResult.Success(json.toString(2))
    }

    private fun formatTvDetails(details: TmdbTvDetails): ToolResult {
        val json = JSONObject()
        json.put("id", details.id)
        json.put("title", details.name ?: "Unknown")
        json.put("overview", details.overview ?: "")
        json.put("rating", details.voteAverage ?: 0.0)
        json.put("first_air_date", details.firstAirDate ?: "")
        val tvGenreNames: List<String> = details.genres?.mapNotNull { it.name } ?: emptyList()
        json.put("genres", JSONArray(tvGenreNames))
        val seasonSummaries: List<String> = details.seasons?.map { "${it.name ?: "?"} (${it.episodeCount ?: 0} episodes)" } ?: emptyList()
        json.put("seasons", JSONArray(seasonSummaries))
        val tvCastNames: List<String> = details.credits?.cast?.take(5)?.mapNotNull { it.name } ?: emptyList()
        json.put("cast", JSONArray(tvCastNames))
        return ToolResult.Success(json.toString(2))
    }

}
