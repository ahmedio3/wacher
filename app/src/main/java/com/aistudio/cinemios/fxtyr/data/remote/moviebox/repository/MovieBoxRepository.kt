package com.aistudio.cinemios.fxtyr.data.remote.moviebox.repository

import android.content.Context
import android.util.Log
import com.aistudio.cinemios.fxtyr.data.remote.moviebox.api.MovieBoxApi
import com.aistudio.cinemios.fxtyr.data.remote.moviebox.models.*
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

interface MovieBoxRepository {
    suspend fun search(query: String, originalLanguage: String? = null, limit: Int = 8): Result<List<SearchResult>>
    suspend fun getDownloadLinks(subjectId: String, resolution: Int? = null, forceRefresh: Boolean = false): Result<List<VideoFile>>
    suspend fun getSubtitles(subjectId: String, resourceId: String): Result<SubtitleResponse>
    suspend fun browse(genre: String?, type: String?, sort: String?, safeMode: Boolean?, limit: Int): Result<List<SearchResult>>
    suspend fun trending(genre: String?, page: Int, limit: Int): Result<List<SearchResult>>
    suspend fun randomContent(type: String?, safeMode: Boolean?, limit: Int): Result<List<SearchResult>>
    suspend fun itemDetails(subjectId: String): Result<ItemDetailResult>
}

class MovieBoxRepositoryImpl(
    private val context: Context,
    private val api: MovieBoxApi
) : MovieBoxRepository {

    private val searchCache = ConcurrentHashMap<String, List<SearchResult>>()
    private data class CachedDownloadLinks(
        val timestamp: Long,
        val files: List<VideoFile>
    )
    private val linkCache = ConcurrentHashMap<String, CachedDownloadLinks>()
    private val LINK_CACHE_TTL_MS = 15 * 60 * 1000L // 15 minutes TTL for in-memory links
    private val tag = "MovieBoxRepo"

    init {
        // Clear any old disk cache from previous app versions to purge stale/expired tokens
        try {
            val prefs = context.getSharedPreferences("moviebox_link_cache", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
        } catch (_: Exception) {}
    }

    override suspend fun search(query: String, originalLanguage: String?, limit: Int): Result<List<SearchResult>> {
        val cacheKey = "${query}_${originalLanguage}_$limit"
        searchCache[cacheKey]?.let { return Result.success(it) }

        return api.search(query, originalLanguage, limit).onSuccess {
            searchCache[cacheKey] = it
        }
    }

    override suspend fun getDownloadLinks(
        subjectId: String,
        resolution: Int?,
        forceRefresh: Boolean
    ): Result<List<VideoFile>> {
        val cacheKey = "links_${subjectId}_${resolution}"
        if (!forceRefresh) {
            val cached = linkCache[cacheKey]
            if (cached != null && (System.currentTimeMillis() - cached.timestamp < LINK_CACHE_TTL_MS)) {
                return Result.success(cached.files)
            }
        }

        val result = api.getDownloadLinks(subjectId, resolution)
        result.onSuccess { files ->
            linkCache[cacheKey] = CachedDownloadLinks(System.currentTimeMillis(), files)
        }
        return result
    }

    override suspend fun getSubtitles(subjectId: String, resourceId: String): Result<SubtitleResponse> {
        return api.getSubtitles(subjectId, resourceId)
    }

    override suspend fun browse(genre: String?, type: String?, sort: String?, safeMode: Boolean?, limit: Int): Result<List<SearchResult>> {
        val cacheKey = "browse_${genre}_${type}_${sort}_${safeMode}_$limit"
        searchCache[cacheKey]?.let { return Result.success(it) }

        return api.browse(genre, type, sort, safeMode, limit).onSuccess {
            searchCache[cacheKey] = it
        }
    }

    override suspend fun trending(genre: String?, page: Int, limit: Int): Result<List<SearchResult>> {
        val cacheKey = "trending_${genre}_${page}_$limit"
        searchCache[cacheKey]?.let { return Result.success(it) }

        return api.trending(genre, page, limit).onSuccess {
            searchCache[cacheKey] = it
        }
    }

    override suspend fun randomContent(type: String?, safeMode: Boolean?, limit: Int): Result<List<SearchResult>> {
        val cacheKey = "random_${type}_${safeMode}_$limit"
        searchCache[cacheKey]?.let { return Result.success(it) }

        return api.randomContent(type, safeMode, limit).onSuccess {
            searchCache[cacheKey] = it
        }
    }

    override suspend fun itemDetails(subjectId: String): Result<ItemDetailResult> {
        return api.itemDetails(subjectId)
    }

}
