package com.example.data.remote.moviebox.api

import com.example.data.remote.moviebox.models.*
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
                                hasResource = item.optBoolean("has_resource", false)
                            )
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
                                    url = subItem.optString("url")
                                )
                            )
                        }
                    }

                    list.add(
                        VideoFile(
                            url = item.optString("url"),
                            resolution = item.optInt("resolution"),
                            size = item.optLong("size", 0L),
                            season = item.optInt("season", 0),
                            episode = item.optInt("episode", 0),
                            resourceId = item.optString("resource_id", ""),
                            subtitlesAvailable = item.optBoolean("subtitles_available", false),
                            hasArabicSubtitle = item.optBoolean("has_arabic_subtitle", false),
                            arabicSubtitleUrl = if (item.isNull("arabic_subtitle_url")) null else item.optString("arabic_subtitle_url"),
                            allSubtitles = subtitlesList
                        )
                    )
                }

                Result.success(list)

            } catch (e: Exception) {
                // Return failure if the response is not a JSONArray, maybe it's an err obj
                try {
                    // Fallback parse error
                } catch (e2: Exception) {}
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
                                url = subItem.optString("url")
                            )
                        )
                    }
                }

                val arabicSubObj = item.optJSONObject("arabic_subtitle")
                val arabicSub = if (arabicSubObj != null) {
                    Subtitle(
                        languageCode = arabicSubObj.optString("language_code"),
                        languageName = arabicSubObj.optString("language_name"),
                        url = arabicSubObj.optString("url")
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
}

