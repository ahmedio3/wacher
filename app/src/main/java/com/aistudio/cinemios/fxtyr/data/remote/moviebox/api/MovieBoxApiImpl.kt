package com.aistudio.cinemios.fxtyr.data.remote.moviebox.api

import com.aistudio.cinemios.fxtyr.data.remote.moviebox.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

interface MovieBoxApi {
    suspend fun search(query: String, originalLanguage: String? = null, limit: Int = 8): Result<List<SearchResult>>
    suspend fun getDownloadLinks(subjectId: String, resolution: Int? = null): Result<List<VideoFile>>
    suspend fun getSubtitles(subjectId: String, resourceId: String): Result<SubtitleResponse>
    suspend fun browse(genre: String?, type: String?, sort: String?, safeMode: Boolean?, limit: Int): Result<List<SearchResult>>
    suspend fun trending(genre: String?, page: Int, limit: Int): Result<List<SearchResult>>
    suspend fun randomContent(type: String?, safeMode: Boolean?, limit: Int): Result<List<SearchResult>>
    suspend fun itemDetails(subjectId: String): Result<ItemDetailResult>
}

class MovieBoxApiImpl : MovieBoxApi {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val baseUrl = "https://moviebox-fastapi.vercel.app"

    override suspend fun search(query: String, originalLanguage: String?, limit: Int): Result<List<SearchResult>> {
        return withContext(Dispatchers.IO) {
            try {
                val urlBuilder = "$baseUrl/search".toHttpUrlOrNull()?.newBuilder()?.apply {
                    addQueryParameter("query", query)
                    addQueryParameter("limit", limit.toString())
                    if (originalLanguage != null) {
                        addQueryParameter("original_language", originalLanguage)
                    }
                } ?: return@withContext Result.failure(Exception("Invalid URL"))

                val request = Request.Builder().url(urlBuilder.build()).get().build()
                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP error: ${response.code}"))
                }

                val bodyStr = response.body?.string() ?: ""
                val rootJson = JSONObject(bodyStr)
                if (rootJson.optString("status") != "success") {
                    return@withContext Result.failure(Exception("API returned error: $bodyStr"))
                }

                val resultsArray = rootJson.optJSONArray("results")
                val list = mutableListOf<SearchResult>()
                if (resultsArray != null) {
                    for (i in 0 until resultsArray.length()) {
                        val item = resultsArray.optJSONObject(i) ?: continue
                        list.add(
                            SearchResult(
                                subjectId = item.optString("subject_id"),
                                title = item.optString("title"),
                                type = item.optString("type"),
                                posterUrl = item.optString("poster"),
                                year = item.optString("year"),
                                hasResource = item.optBoolean("has_resource", false),
                                rating = item.optDouble("rating", 0.0),
                                seasons = item.optInt("seasons", 0),
                                country = item.optString("country"),
                                description = item.optString("description"),
                                durationSeconds = item.optInt("duration_seconds", 0)
                            ).withParsedLanguages(item.optJSONArray("languages"))
                             .withParsedGenre(item.optJSONArray("genre"))
                        )
                    }
                }
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun getDownloadLinks(subjectId: String, resolution: Int?): Result<List<VideoFile>> {
        return withContext(Dispatchers.IO) {
            try {
                val urlBuilder = "$baseUrl/get_download_links".toHttpUrlOrNull()?.newBuilder()?.apply {
                    addQueryParameter("subject_id", subjectId)
                    if (resolution != null) {
                        addQueryParameter("resolution", resolution.toString())
                    }
                } ?: return@withContext Result.failure(Exception("Invalid URL"))

                val request = Request.Builder().url(urlBuilder.build()).get().build()
                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP error: ${response.code}"))
                }

                val bodyStr = response.body?.string() ?: ""
                val rootJson = org.json.JSONObject(bodyStr)
                if (rootJson.optString("status") != "success") {
                    return@withContext Result.failure(Exception("API Error: $bodyStr"))
                }
                val jsonArray = rootJson.optJSONArray("download_links") ?: org.json.JSONArray()

                val list = mutableListOf<VideoFile>()
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.optJSONObject(i) ?: continue

                    val allSubtitlesArray = item.optJSONArray("all_subtitles")
                    val subtitlesList = mutableListOf<Subtitle>()
                    if (allSubtitlesArray != null) {
                        for (j in 0 until allSubtitlesArray.length()) {
                            val subItem = allSubtitlesArray.optJSONObject(j) ?: continue
                            subtitlesList.add(
                                Subtitle(
                                    languageCode = subItem.optString("language_code"),
                                    languageName = subItem.optString("language_name"),
                                    url = subItem.optString("url"),
                                    size = subItem.optInt("size", 0),
                                    delay = subItem.optInt("delay", 0)
                                )
                            )
                        }
                    }

                    val sizeStr = item.optString("size", "")
                    val sizeBytes = parseSizeString(sizeStr)

                    list.add(
                        VideoFile(
                            url = item.optString("url") ?: "",
                            resolution = item.optInt("resolution", 0),
                            size = sizeBytes,
                            sizeString = sizeStr,
                            season = item.optInt("season", 0),
                            episode = item.optInt("episode", 0),
                            resourceId = item.optString("resource_id", ""),
                            subtitlesAvailable = item.optBoolean("subtitles_available", false),
                            hasArabicSubtitle = item.optBoolean("has_arabic_subtitle", false),
                            arabicSubtitleUrl = if (item.isNull("arabic_subtitle_url")) null else item.optString("arabic_subtitle_url"),
                            allSubtitles = subtitlesList,
                            codec = item.optString("codec").takeIf { it.isNotEmpty() },
                            duration = item.optInt("duration", 0),
                            sourceUrl = if (item.isNull("source_url")) null else item.optString("source_url").takeIf { it.isNotEmpty() }
                        )
                    )
                }

                Result.success(list)

            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun getSubtitles(subjectId: String, resourceId: String): Result<SubtitleResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val urlBuilder = "$baseUrl/get_subtitles".toHttpUrlOrNull()?.newBuilder()?.apply {
                    addQueryParameter("subject_id", subjectId)
                    addQueryParameter("resource_id", resourceId)
                } ?: return@withContext Result.failure(Exception("Invalid URL"))

                val request = Request.Builder().url(urlBuilder.build()).get().build()
                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP error: ${response.code}"))
                }

                val bodyStr = response.body?.string() ?: ""
                val item = JSONObject(bodyStr)

                val allSubtitlesArray = item.optJSONArray("all_subtitles")
                val subtitlesList = mutableListOf<Subtitle>()
                if (allSubtitlesArray != null) {
                    for (j in 0 until allSubtitlesArray.length()) {
                        val subItem = allSubtitlesArray.optJSONObject(j) ?: continue
                        subtitlesList.add(
                            Subtitle(
                                languageCode = subItem.optString("language_code"),
                                languageName = subItem.optString("language_name"),
                                url = subItem.optString("url"),
                                size = subItem.optInt("size", 0),
                                delay = subItem.optInt("delay", 0)
                            )
                        )
                    }
                }

                val arabicSubObj = item.optJSONObject("arabic_subtitle")
                val arabicSub = if (arabicSubObj != null) {
                    Subtitle(
                        languageCode = arabicSubObj.optString("language_code"),
                        languageName = arabicSubObj.optString("language_name"),
                        url = arabicSubObj.optString("url"),
                        size = arabicSubObj.optInt("size", 0),
                        delay = arabicSubObj.optInt("delay", 0)
                    )
                } else null

                Result.success(
                    SubtitleResponse(
                        hasArabic = item.optBoolean("has_arabic", false),
                        arabicSubtitle = arabicSub,
                        allSubtitles = subtitlesList,
                        totalLanguages = item.optInt("total_languages", 0)
                    )
                )

            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun browse(genre: String?, type: String?, sort: String?, safeMode: Boolean?, limit: Int): Result<List<SearchResult>> {
        return withContext(Dispatchers.IO) {
            try {
                val urlBuilder = "$baseUrl/browse".toHttpUrlOrNull()?.newBuilder()?.apply {
                    addQueryParameter("limit", limit.toString())
                    if (genre != null) addQueryParameter("genre", genre)
                    if (type != null) addQueryParameter("type", type)
                    if (sort != null) addQueryParameter("sort", sort)
                    if (safeMode != null) addQueryParameter("safe_mode", safeMode.toString())
                } ?: return@withContext Result.failure(Exception("Invalid URL"))

                val request = Request.Builder().url(urlBuilder.build()).get().build()
                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP error: ${response.code}"))
                }

                val bodyStr = response.body?.string() ?: ""
                val rootJson = JSONObject(bodyStr)
                if (rootJson.optString("status") != "success") {
                    return@withContext Result.failure(Exception("API returned error: $bodyStr"))
                }

                val resultsArray = rootJson.optJSONArray("results")
                val list = mutableListOf<SearchResult>()
                if (resultsArray != null) {
                    for (i in 0 until resultsArray.length()) {
                        val item = resultsArray.optJSONObject(i) ?: continue
                        list.add(
                            SearchResult(
                                subjectId = item.optString("subject_id"),
                                title = item.optString("title"),
                                type = item.optString("type"),
                                posterUrl = item.optString("poster"),
                                year = item.optString("year"),
                                hasResource = item.optBoolean("has_resource", false),
                                rating = item.optDouble("rating", 0.0),
                                seasons = item.optInt("seasons", 0),
                                country = item.optString("country"),
                                description = item.optString("description"),
                                durationSeconds = item.optInt("duration_seconds", 0)
                            ).withParsedLanguages(item.optJSONArray("languages"))
                             .withParsedGenre(item.optJSONArray("genre"))
                        )
                    }
                }
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun trending(genre: String?, page: Int, limit: Int): Result<List<SearchResult>> {
        return withContext(Dispatchers.IO) {
            try {
                val urlBuilder = "$baseUrl/trending".toHttpUrlOrNull()?.newBuilder()?.apply {
                    addQueryParameter("page", page.toString())
                    addQueryParameter("limit", limit.toString())
                    if (genre != null) addQueryParameter("genre", genre)
                } ?: return@withContext Result.failure(Exception("Invalid URL"))

                val request = Request.Builder().url(urlBuilder.build()).get().build()
                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP error: ${response.code}"))
                }

                val bodyStr = response.body?.string() ?: ""
                val rootJson = JSONObject(bodyStr)
                if (rootJson.optString("status") != "success") {
                    return@withContext Result.failure(Exception("API returned error: $bodyStr"))
                }

                val resultsArray = rootJson.optJSONArray("results")
                val list = mutableListOf<SearchResult>()
                if (resultsArray != null) {
                    for (i in 0 until resultsArray.length()) {
                        val item = resultsArray.optJSONObject(i) ?: continue
                        list.add(
                            SearchResult(
                                subjectId = item.optString("subject_id"),
                                title = item.optString("title"),
                                type = item.optString("type"),
                                posterUrl = item.optString("poster"),
                                year = item.optString("year"),
                                hasResource = item.optBoolean("has_resource", false),
                                rating = item.optDouble("rating", 0.0),
                                seasons = item.optInt("seasons", 0),
                                country = item.optString("country"),
                                description = item.optString("description"),
                                durationSeconds = item.optInt("duration_seconds", 0)
                            ).withParsedLanguages(item.optJSONArray("languages"))
                             .withParsedGenre(item.optJSONArray("genre"))
                        )
                    }
                }
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun randomContent(type: String?, safeMode: Boolean?, limit: Int): Result<List<SearchResult>> {
        return withContext(Dispatchers.IO) {
            try {
                val urlBuilder = "$baseUrl/random".toHttpUrlOrNull()?.newBuilder()?.apply {
                    addQueryParameter("limit", limit.toString())
                    if (type != null) addQueryParameter("type", type)
                    if (safeMode != null) addQueryParameter("safe_mode", safeMode.toString())
                } ?: return@withContext Result.failure(Exception("Invalid URL"))

                val request = Request.Builder().url(urlBuilder.build()).get().build()
                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP error: ${response.code}"))
                }

                val bodyStr = response.body?.string() ?: ""
                val rootJson = JSONObject(bodyStr)
                if (rootJson.optString("status") != "success") {
                    return@withContext Result.failure(Exception("API returned error: $bodyStr"))
                }

                val resultsArray = rootJson.optJSONArray("results")
                val list = mutableListOf<SearchResult>()
                if (resultsArray != null) {
                    for (i in 0 until resultsArray.length()) {
                        val item = resultsArray.optJSONObject(i) ?: continue
                        list.add(
                            SearchResult(
                                subjectId = item.optString("subject_id"),
                                title = item.optString("title"),
                                type = item.optString("type"),
                                posterUrl = item.optString("poster"),
                                year = item.optString("year"),
                                hasResource = item.optBoolean("has_resource", false),
                                rating = item.optDouble("rating", 0.0),
                                seasons = item.optInt("seasons", 0),
                                country = item.optString("country"),
                                description = item.optString("description"),
                                durationSeconds = item.optInt("duration_seconds", 0)
                            ).withParsedLanguages(item.optJSONArray("languages"))
                             .withParsedGenre(item.optJSONArray("genre"))
                        )
                    }
                }
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun itemDetails(subjectId: String): Result<ItemDetailResult> {
        return withContext(Dispatchers.IO) {
            try {
                val urlBuilder = "$baseUrl/item_details".toHttpUrlOrNull()?.newBuilder()?.apply {
                    addQueryParameter("subject_id", subjectId)
                } ?: return@withContext Result.failure(Exception("Invalid URL"))

                val request = Request.Builder().url(urlBuilder.build()).get().build()
                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP error: ${response.code}"))
                }

                val bodyStr = response.body?.string() ?: ""
                val item = JSONObject(bodyStr)
                if (item.optString("status") != "success") {
                    return@withContext Result.failure(Exception("API returned error: $bodyStr"))
                }

                val langsArray = item.optJSONArray("languages")
                val langsList = if (langsArray != null) {
                    (0 until langsArray.length()).mapNotNull { langsArray.optString(it).takeIf { s -> s.isNotEmpty() } }
                } else emptyList()

                val genreArray = item.optJSONArray("genre")
                val genreList = if (genreArray != null) {
                    (0 until genreArray.length()).mapNotNull { genreArray.optString(it).takeIf { s -> s.isNotEmpty() } }
                } else emptyList()

                Result.success(
                    ItemDetailResult(
                        subjectId = item.optString("subject_id"),
                        title = item.optString("title"),
                        description = item.optString("description"),
                        posterUrl = item.optString("poster"),
                        rating = item.optDouble("rating", 0.0),
                        year = item.optString("year"),
                        type = item.optString("type"),
                        languages = langsList,
                        country = item.optString("country"),
                        genre = genreList,
                        seasonsCount = item.optInt("seasons", 0),
                        durationSeconds = item.optInt("duration_seconds", 0),
                        hasResource = item.optBoolean("has_resource", false)
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Parses size strings like "1.2GB", "850MB", "1.5KB" into bytes.
     * Also handles plain number strings like "2414667149" (bytes).
     * Returns 0 on failure.
     */
    private fun parseSizeString(sizeStr: String): Long {
        if (sizeStr.isBlank()) return 0L
        return try {
            // Try format with unit suffix first: "1.2GB", "850MB"
            val regex = Regex("""([\d.]+)\s*([KMGT]?B)""", RegexOption.IGNORE_CASE)
            val match = regex.find(sizeStr)
            if (match != null) {
                val number = match.groupValues[1].toDoubleOrNull() ?: return 0L
                val unit = match.groupValues[2].uppercase()
                val multiplier = when (unit) {
                    "B" -> 1L
                    "KB" -> 1024L
                    "MB" -> 1024L * 1024
                    "GB" -> 1024L * 1024 * 1024
                    "TB" -> 1024L * 1024 * 1024 * 1024
                    else -> 1L
                }
                return (number * multiplier).toLong()
            }
            // Fallback: try parsing as a plain number (bytes)
            // e.g. "2414667149" from the MovieBox API
            sizeStr.trim().toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}

/**
 * Helper to copy a SearchResult with parsed languages array from JSON.
 */
private fun SearchResult.withParsedLanguages(arr: org.json.JSONArray?): SearchResult {
    if (arr == null) return this
    val list = (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotEmpty() } }
    return this.copy(languages = list)
}

private fun SearchResult.withParsedGenre(arr: org.json.JSONArray?): SearchResult {
    if (arr == null) return this
    val list = (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotEmpty() } }
    return this.copy(genre = list)
}
