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
    suspend fun getDownloadLinks(
        subjectId: String,
        resolution: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        page: Int? = null,
        limit: Int? = null,
        forceRefresh: Boolean = false
    ): Result<List<VideoFile>>
    suspend fun getSubtitles(subjectId: String, resourceId: String): Result<SubtitleResponse>
    suspend fun browse(genre: String?, type: String?, sort: String?, safeMode: Boolean?, limit: Int): Result<List<SearchResult>>
    suspend fun trending(tab: String?, page: Int, safeMode: Boolean = true, limit: Int, forceRefresh: Boolean = false): Result<List<SearchResult>>
    suspend fun randomContent(type: String?, safeMode: Boolean?, limit: Int): Result<List<SearchResult>>
    suspend fun adult(type: String?, queries: String?, sort: String, limit: Int): Result<List<SearchResult>>
    suspend fun adultKeywords(): Result<List<String>>
    suspend fun itemDetails(subjectId: String): Result<ItemDetailResult>
    suspend fun suggest(query: String, limit: Int = 10): Result<List<SearchSuggestion>>
    suspend fun recommend(subjectId: String, page: Int = 1, limit: Int = 12): Result<List<SearchResult>>
    suspend fun home(page: Int = 1, limitPerSection: Int = 20): Result<List<HomeSection>>
    suspend fun popularSearches(limit: Int = 10): Result<PopularSearches>
    suspend fun searchV2(query: String, tab: String = "all", page: Int = 1, limit: Int = 15): Result<SearchV2Result>
}

class MovieBoxRepositoryImpl(
    private val context: Context,
    private val api: MovieBoxApi
) : MovieBoxRepository {

    private val searchCache = ConcurrentHashMap<String, List<SearchResult>>()
    private val suggestCache = ConcurrentHashMap<String, List<SearchSuggestion>>()
    private val searchV2Cache = ConcurrentHashMap<String, SearchV2Result>()
    private val homeCache = ConcurrentHashMap<String, List<HomeSection>>()
    @Volatile
    private var popularSearchesCache: PopularSearches? = null
    private data class CachedDownloadLinks(
        val timestamp: Long,
        val files: List<VideoFile>
    )
    private val linkCache = ConcurrentHashMap<String, CachedDownloadLinks>()
    private val LINK_CACHE_TTL_MS = 15 * 60 * 1000L // 15 minutes TTL for in-memory links
    @Volatile
    private var adultKeywordsCache: List<String>? = null
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
        season: Int?,
        episode: Int?,
        page: Int?,
        limit: Int?,
        forceRefresh: Boolean
    ): Result<List<VideoFile>> {
        val cacheKey = "links_${subjectId}_${resolution}_s${season}_e${episode}_p${page}_l${limit}"
        if (!forceRefresh) {
            val cached = linkCache[cacheKey]
            if (cached != null && (System.currentTimeMillis() - cached.timestamp < LINK_CACHE_TTL_MS)) {
                return Result.success(cached.files)
            }
        }

        val result = api.getDownloadLinks(subjectId, resolution, season, episode, page, limit)
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

    override suspend fun trending(tab: String?, page: Int, safeMode: Boolean, limit: Int, forceRefresh: Boolean): Result<List<SearchResult>> {
        val cacheKey = "trending_${tab}_${safeMode}_${page}_$limit"
        if (!forceRefresh) {
            searchCache[cacheKey]?.let { return Result.success(it) }
        }

        return api.trending(tab, page, safeMode, limit).onSuccess {
            searchCache[cacheKey] = it
        }
    }

    override suspend fun adult(type: String?, queries: String?, sort: String, limit: Int): Result<List<SearchResult>> {
        // Adult discovery is intentionally not cached: random sorting is meant to
        // surface a fresh mix every time the user refreshes.
        return api.adult(type, queries, sort, limit)
    }

    override suspend fun adultKeywords(): Result<List<String>> {
        adultKeywordsCache?.let { return Result.success(it) }
        return api.adultKeywords().onSuccess { adultKeywordsCache = it }
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

    override suspend fun suggest(query: String, limit: Int): Result<List<SearchSuggestion>> {
        val cacheKey = "${query.trim().lowercase()}_$limit"
        suggestCache[cacheKey]?.let { return Result.success(it) }
        return api.suggest(query, limit).onSuccess { suggestCache[cacheKey] = it }
    }

    override suspend fun recommend(subjectId: String, page: Int, limit: Int): Result<List<SearchResult>> {
        val cacheKey = "recommend_${subjectId}_${page}_$limit"
        searchCache[cacheKey]?.let { return Result.success(it) }
        return api.recommend(subjectId, page, limit).onSuccess { searchCache[cacheKey] = it }
    }

    override suspend fun home(page: Int, limitPerSection: Int): Result<List<HomeSection>> {
        val cacheKey = "home_${page}_$limitPerSection"
        homeCache[cacheKey]?.let { return Result.success(it) }
        return api.home(page, limitPerSection).onSuccess { homeCache[cacheKey] = it }
    }

    override suspend fun popularSearches(limit: Int): Result<PopularSearches> {
        popularSearchesCache?.let { return Result.success(it) }
        return api.popularSearches(limit).onSuccess { popularSearchesCache = it }
    }

    override suspend fun searchV2(query: String, tab: String, page: Int, limit: Int): Result<SearchV2Result> {
        val cacheKey = "v2_${query.trim().lowercase()}_${tab}_${page}_$limit"
        searchV2Cache[cacheKey]?.let { return Result.success(it) }
        return api.searchV2(query, tab, page, limit).onSuccess { searchV2Cache[cacheKey] = it }
    }

}
