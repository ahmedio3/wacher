package com.example.data.remote.moviebox.repository

import android.content.Context
import android.util.Log
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

class MovieBoxRepositoryImpl(
    private val context: Context,
    private val api: MovieBoxApi
) : MovieBoxRepository {

    private val searchCache = ConcurrentHashMap<String, List<SearchResult>>()
    private val linkCache = ConcurrentHashMap<String, List<VideoFile>>()
    private val prefs = context.getSharedPreferences("moviebox_link_cache", Context.MODE_PRIVATE)
    private val tag = "MovieBoxRepo"

    override suspend fun search(query: String, originalLanguage: String?, limit: Int): Result<List<SearchResult>> {
        val cacheKey = "${query}_${originalLanguage}_$limit"
        searchCache[cacheKey]?.let { return Result.success(it) }

        return api.search(query, originalLanguage, limit).onSuccess {
            searchCache[cacheKey] = it
        }
    }

    override suspend fun getDownloadLinks(subjectId: String, resolution: Int?): Result<List<VideoFile>> {
        val cacheKey = "links_${subjectId}_${resolution}"
        linkCache[cacheKey]?.let { return Result.success(it) }

        // Try to load from disk cache
        prefs.getString(cacheKey, null)?.let { cached ->
            try {
                val list = parseDownloadLinks(JSONArray(cached))
                if (list.isNotEmpty()) {
                    linkCache[cacheKey] = list
                    return Result.success(list)
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to parse cached links: ${e.message}")
            }
        }

        val result = api.getDownloadLinks(subjectId, resolution)
        result.onSuccess { files ->
            // For any file that doesn't have subtitles, attempt the
            // get_subtitles fallback so the player can show Arabic
            // subs even when the API didn't include them inline.
            val enriched = files.map { vf ->
                if (!vf.subtitlesAvailable && vf.resourceId.isNotEmpty()) {
                    val fallback = api.getSubtitles(subjectId, vf.resourceId).getOrNull()
                    if (fallback != null && fallback.allSubtitles.isNotEmpty()) {
                        val arSub = fallback.arabicSubtitle
                            ?: fallback.allSubtitles.firstOrNull { it.languageCode.equals("ar", true) }
                        vf.copy(
                            subtitlesAvailable = true,
                            allSubtitles = fallback.allSubtitles,
                            hasArabicSubtitle = arSub != null,
                            arabicSubtitleUrl = arSub?.url
                        )
                    } else vf
                } else vf
            }

            linkCache[cacheKey] = enriched
            try {
                prefs.edit().putString(cacheKey, serializeDownloadLinks(enriched).toString()).apply()
            } catch (e: Exception) {
                Log.w(tag, "Failed to cache links: ${e.message}")
            }
        }
        return result
    }

    override suspend fun getSubtitles(subjectId: String, resourceId: String): Result<SubtitleResponse> {
        return api.getSubtitles(subjectId, resourceId)
    }

    // ─── Serialization helpers ────────────────────────────────────────────

    private fun serializeDownloadLinks(list: List<VideoFile>): JSONArray {
        val array = JSONArray()
        list.forEach { f ->
            val obj = JSONObject()
            obj.put("url", f.url)
            obj.put("resolution", f.resolution)
            obj.put("size", f.size)
            obj.put("sizeString", f.sizeString)
            obj.put("season", f.season)
            obj.put("episode", f.episode)
            obj.put("resource_id", f.resourceId)
            obj.put("subtitles_available", f.subtitlesAvailable)
            obj.put("has_arabic_subtitle", f.hasArabicSubtitle)
            obj.put("arabic_subtitle_url", f.arabicSubtitleUrl ?: JSONObject.NULL)
            obj.put("codec", f.codec ?: JSONObject.NULL)
            obj.put("duration", f.duration)
            obj.put("source_url", f.sourceUrl ?: JSONObject.NULL)
            val subs = JSONArray()
            f.allSubtitles.forEach { s ->
                val sObj = JSONObject()
                sObj.put("language_code", s.languageCode)
                sObj.put("language_name", s.languageName)
                sObj.put("url", s.url)
                sObj.put("size", s.size)
                sObj.put("delay", s.delay)
                subs.put(sObj)
            }
            obj.put("all_subtitles", subs)
            array.put(obj)
        }
        return array
    }

    private fun parseDownloadLinks(array: JSONArray): List<VideoFile> {
        val list = mutableListOf<VideoFile>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)

            val subs = mutableListOf<Subtitle>()
            val allSubsArr = obj.optJSONArray("all_subtitles")
            if (allSubsArr != null) {
                for (j in 0 until allSubsArr.length()) {
                    val s = allSubsArr.getJSONObject(j)
                    subs.add(
                        Subtitle(
                            languageCode = s.optString("language_code"),
                            languageName = s.optString("language_name"),
                            url = s.optString("url"),
                            size = s.optInt("size", 0),
                            delay = s.optInt("delay", 0)
                        )
                    )
                }
            }

            val arUrl: String? = if (obj.isNull("arabic_subtitle_url")) null
                else obj.optString("arabic_subtitle_url").takeIf { it.isNotEmpty() }

            list.add(
                VideoFile(
                    url = obj.optString("url"),
                    resolution = obj.optInt("resolution", 0),
                    size = obj.optLong("size", 0L),
                    sizeString = obj.optString("sizeString"),
                    season = obj.optInt("season", 0),
                    episode = obj.optInt("episode", 0),
                    resourceId = obj.optString("resource_id", ""),
                    subtitlesAvailable = obj.optBoolean("subtitles_available", false),
                    hasArabicSubtitle = obj.optBoolean("has_arabic_subtitle", false),
                    arabicSubtitleUrl = arUrl,
                    allSubtitles = subs,
                    codec = if (obj.isNull("codec")) null else obj.optString("codec").takeIf { it.isNotEmpty() },
                    duration = obj.optInt("duration", 0),
                    sourceUrl = if (obj.isNull("source_url")) null else obj.optString("source_url").takeIf { it.isNotEmpty() }
                )
            )
        }
        return list
    }
}
