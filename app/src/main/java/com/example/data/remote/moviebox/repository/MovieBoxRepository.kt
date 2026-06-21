package com.example.data.remote.moviebox.repository

import android.content.Context
import com.example.data.remote.moviebox.api.MovieBoxApi
import com.example.data.remote.moviebox.models.*
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

interface MovieBoxRepository {
    suspend fun search(query: String, originalLanguage: String? = null, limit: Int = 8): Result<List<SearchResult>>
    suspend fun getDownloadLinks(subjectId: String, resolution: Int? = null): Result<List<VideoFile>>
    suspend fun getSubtitles(subjectId: String, resourceId: String): Result<SubtitleResponse>
}

class MovieBoxRepositoryImpl(private val context: Context, private val api: MovieBoxApi) : MovieBoxRepository {

    private val searchCache = ConcurrentHashMap<String, List<SearchResult>>()
    private val prefs = context.getSharedPreferences("moviebox_link_cache", Context.MODE_PRIVATE)

    override suspend fun search(query: String, originalLanguage: String?, limit: Int): Result<List<SearchResult>> {
        val cacheKey = "${query}_${originalLanguage}_$limit"
        searchCache[cacheKey]?.let { return Result.success(it) }

        return api.search(query, originalLanguage, limit).onSuccess {
            searchCache[cacheKey] = it
        }
    }

    override suspend fun getDownloadLinks(subjectId: String, resolution: Int?): Result<List<VideoFile>> {
        val cacheKey = "links_${subjectId}_${resolution}"
        val cachedData = prefs.getString(cacheKey, null)
        if (cachedData != null) {
            try {
                val array = JSONArray(cachedData)
                val list = mutableListOf<VideoFile>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    
                    val allSubsArray = obj.optJSONArray("all_subtitles")
                    val subsList = mutableListOf<Subtitle>()
                    if (allSubsArray != null) {
                        for (j in 0 until allSubsArray.length()) {
                            val subItem = allSubsArray.getJSONObject(j)
                            subsList.add(Subtitle(
                                languageCode = subItem.getString("language_code"),
                                languageName = subItem.getString("language_name"),
                                url = subItem.getString("url")
                            ))
                        }
                    }

                    list.add(VideoFile(
                        url = obj.getString("url"),
                        resolution = obj.getInt("resolution"),
                        size = obj.optLong("size", 0L),
                        season = obj.optInt("season", 0),
                        episode = obj.optInt("episode", 0),
                        resourceId = obj.optString("resource_id", ""),
                        subtitlesAvailable = obj.optBoolean("subtitles_available", false),
                        hasArabicSubtitle = obj.optBoolean("has_arabic_subtitle", false),
                        arabicSubtitleUrl = if (obj.isNull("arabic_subtitle_url")) null else obj.getString("arabic_subtitle_url"),
                        allSubtitles = subsList
                    ))
                }
                if (list.isNotEmpty()) {
                    return Result.success(list)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        val result = api.getDownloadLinks(subjectId, resolution)
        result.onSuccess { files ->
            try {
                val array = JSONArray()
                files.forEach { f ->
                    val obj = JSONObject()
                    obj.put("url", f.url)
                    obj.put("resolution", f.resolution)
                    obj.put("size", f.size)
                    obj.put("season", f.season)
                    obj.put("episode", f.episode)
                    obj.put("resource_id", f.resourceId)
                    obj.put("subtitles_available", f.subtitlesAvailable)
                    obj.put("has_arabic_subtitle", f.hasArabicSubtitle)
                    obj.put("arabic_subtitle_url", f.arabicSubtitleUrl)
                    
                    val subsArray = JSONArray()
                    f.allSubtitles.forEach { s ->
                        val subObj = JSONObject()
                        subObj.put("language_code", s.languageCode)
                        subObj.put("language_name", s.languageName)
                        subObj.put("url", s.url)
                        subsArray.put(subObj)
                    }
                    obj.put("all_subtitles", subsArray)
                    
                    array.put(obj)
                }
                prefs.edit().putString(cacheKey, array.toString()).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return result
    }

    override suspend fun getSubtitles(subjectId: String, resourceId: String): Result<SubtitleResponse> {
        return api.getSubtitles(subjectId, resourceId)
    }
}

