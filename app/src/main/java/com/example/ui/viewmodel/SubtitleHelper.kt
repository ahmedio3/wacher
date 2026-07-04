package com.example.ui.viewmodel

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

object SubtitleHelper {
    private const val API_KEY = "subdl_BLYRvzz54CHwxZVHbNW_eA7iDySnKeFhvc8XYnAXPZ0"
    private const val SUBDL_API_KEY = "subdl_BLYRvzz54CHwxZVHbNW_eA7iDySnKeFhvc8XYnAXPZ0"
    private const val OPENSUBTITLES_API_KEY = "JbFq0O0rQD6DsOCciqPmZIoMVFEPcSpT"
    private const val USER_AGENT = "Cinemios v1.0"
    
    data class SubtitleItem(
        val name: String,
        val lang: String,
        val url: String,
        val langCode: String,
        val source: String = "MovieBox",
        val fileId: String? = null,
        val downloadCount: Int = 0
    )
    
    suspend fun fetchSubtitles(tmdbId: String, isTv: Boolean, season: Int = 0, episode: Int = 0, searchTitle: String? = null): List<SubtitleItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<SubtitleItem>()

        // First try: use tmdbId directly as subject_id (works for MovieBox IDs)
        val resourceId = findResourceId(tmdbId, isTv, season, episode)
        if (resourceId != null) {
            fetchSubtitlesByResource(tmdbId, resourceId, list)
        }

        // If no results and we have a title, try searching MovieBox to find the subject_id
        if (list.isEmpty() && !searchTitle.isNullOrBlank()) {
            try {
                val searchUrl = "https://moviebox-fastapi.vercel.app/search?query=${java.net.URLEncoder.encode(searchTitle, "UTF-8")}&limit=5"
                val searchConn = URL(searchUrl).openConnection() as HttpURLConnection
                searchConn.requestMethod = "GET"
                searchConn.connect()
                if (searchConn.responseCode == 200) {
                    val searchResponse = searchConn.inputStream.bufferedReader().readText()
                    val searchJson = JSONObject(searchResponse)
                    if (searchJson.optString("status") == "success") {
                        val results = searchJson.optJSONArray("results")
                        if (results != null) {
                            for (i in 0 until results.length()) {
                                val item = results.getJSONObject(i)
                                val subjectId = item.optString("subject_id", "")
                                if (subjectId.isNotEmpty()) {
                                    val rid = findResourceId(subjectId, isTv, season, episode)
                                    if (rid != null) {
                                        fetchSubtitlesByResource(subjectId, rid, list)
                                        if (list.isNotEmpty()) break
                                    }
                                }
                            }
                        }
                    }
                }
                searchConn.disconnect()
            } catch (_: Exception) { }
        }

        // Sort by 'AR' first, then others
        list.distinctBy { it.url }.sortedByDescending { it.langCode.lowercase() == "ar" }
    }

    private suspend fun findResourceId(subjectId: String, isTv: Boolean, season: Int, episode: Int): String? = withContext(Dispatchers.IO) {
        try {
            val urlStr = "https://moviebox-fastapi.vercel.app/get_download_links?subject_id=$subjectId"
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connect()
            var resourceId: String? = null
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                if (json.optString("status") == "success") {
                    val links = json.optJSONArray("download_links")
                    if (links != null) {
                        for (i in 0 until links.length()) {
                            val linkObj = links.getJSONObject(i)
                            val rSeason = linkObj.optInt("season", 0)
                            val rEpisode = linkObj.optInt("episode", 0)

                            if (isTv) {
                                if (rSeason == season && rEpisode == episode) {
                                    resourceId = linkObj.optString("resource_id")
                                    break
                                }
                            } else {
                                resourceId = linkObj.optString("resource_id")
                                break
                            }
                        }
                    }
                }
            }
            conn.disconnect()
            resourceId
        } catch (_: Exception) { null }
    }

    private suspend fun fetchSubtitlesByResource(subjectId: String, resourceId: String, list: MutableList<SubtitleItem>) = withContext(Dispatchers.IO) {
        try {
            val subUrlStr = "https://moviebox-fastapi.vercel.app/get_subtitles?subject_id=$subjectId&resource_id=$resourceId"
            val subConn = URL(subUrlStr).openConnection() as HttpURLConnection
            subConn.requestMethod = "GET"
            subConn.connect()
            if (subConn.responseCode == 200) {
                val subResponse = subConn.inputStream.bufferedReader().readText()
                val subJson = JSONObject(subResponse)
                if (subJson.optString("status") == "success") {
                    val hasArabic = subJson.optBoolean("has_arabic", false)
                    if (hasArabic) {
                        val arSub = subJson.optJSONObject("arabic_subtitle")
                        if (arSub != null) {
                            val url = arSub.optString("url", "")
                            val langName = arSub.optString("language_name", "العربية")
                            if (url.isNotEmpty()) {
                                list.add(SubtitleItem(langName, "Arabic", url, "ar"))
                            }
                        }
                    }
                    val allSubs = subJson.optJSONArray("all_subtitles")
                    if (allSubs != null) {
                        for (i in 0 until allSubs.length()) {
                            val s = allSubs.getJSONObject(i)
                            val url = s.optString("url", "")
                            val langName = s.optString("language_name", "")
                            val langCode = s.optString("language_code", "")
                            if (url.isNotEmpty()) {
                                list.add(SubtitleItem(langName, langName, url, langCode))
                            }
                        }
                    }
                }
            }
            subConn.disconnect()
        } catch (_: Exception) { }
    }

    // Downloads and extracts to local storage, returns the local extracted File (.srt or .vtt)
    suspend fun downloadAndExtractSubtitle(context: Context, downloadUrlPath: String, mediaId: String): File? = withContext(Dispatchers.IO) {
        try {
            val isDirectUrl = downloadUrlPath.startsWith("http")
            // Moviebox and direct paths usually aren't zips. If it's direct HTTP, we save it directly assuming it's SRT/VTT.
            val downloadUrl = if (isDirectUrl) downloadUrlPath else "https://dl.subdl.com$downloadUrlPath"
            
            val conn = URL(downloadUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connect()
            
            if (conn.responseCode == 200) {
                val input = BufferedInputStream(conn.inputStream)
                val downloadsDir = File(context.filesDir, "downloads")
                if (!downloadsDir.exists()) downloadsDir.mkdirs()

                if (isDirectUrl) {
                    val ext = if (downloadUrlPath.endsWith(".vtt")) ".vtt" else ".srt"
                    val file = File(downloadsDir, "$mediaId$ext")
                    val fos = FileOutputStream(file)
                    input.copyTo(fos)
                    fos.close()
                    input.close()
                    conn.disconnect()
                    return@withContext file
                }

                val zis = ZipInputStream(input)
                var zipEntry = zis.nextEntry
                
                val isTv = mediaId.contains("-s") && mediaId.contains("-e")
                // Extract episode number — handles both "12345-s1-e3" (TMDB) and "abc123-s1-e3-s1-e3" (MovieBox double-wrapped)
                val epStr = if (isTv) {
                    val afterE = mediaId.substringAfter("-e")
                    afterE.substringBefore("-s").takeIf { it != afterE } ?: afterE
                } else ""
                val epNum = epStr.toIntOrNull() ?: -1

                val extractedFiles = mutableListOf<File>()

                while (zipEntry != null) {
                    val name = zipEntry.name
                    if (!zipEntry.isDirectory && (name.endsWith(".srt") || name.endsWith(".vtt"))) {
                        val tmpName = File(name).name
                        val tmpFile = File(downloadsDir, "tmp_${System.currentTimeMillis()}_$tmpName")
                        val fos = FileOutputStream(tmpFile)
                        val buffer = ByteArray(2048)
                        var bytesRead: Int
                        while (zis.read(buffer).also { bytesRead = it } != -1) {
                            fos.write(buffer, 0, bytesRead)
                        }
                        fos.close()
                        extractedFiles.add(tmpFile)
                    }
                    zipEntry = zis.nextEntry
                }
                zis.close()
                conn.disconnect()
                
                if (extractedFiles.isEmpty()) return@withContext null

                var bestMatch = extractedFiles.first()
                val tmdbIdString = if (isTv) mediaId.substringBefore("-s") else mediaId
                val seasonString = if (isTv) mediaId.substringAfter("-s").substringBefore("-e") else "1"

                if (isTv) {
                    // Try to map every extracted file to an episode number and save it independently!
                    val generalEpPattern = java.util.regex.Pattern.compile("(?i)(?:E|EP|Episode)[\\s_\\.-]*0*(\\d+)\\b")
                    for (file in extractedFiles) {
                        val matcher = generalEpPattern.matcher(file.name)
                        if (matcher.find()) {
                            val matchedEpNum = matcher.group(1).toIntOrNull()
                            if (matchedEpNum != null) {
                                val ext = if (file.name.endsWith(".srt")) ".srt" else ".vtt"
                                val targetMediaId = "$tmdbIdString-s$seasonString-e$matchedEpNum"
                                val targetFinalFile = File(downloadsDir, "$targetMediaId$ext")
                                if (targetFinalFile.exists()) targetFinalFile.delete()
                                // Copy content so we don't rename and lose it for other operations
                                file.copyTo(targetFinalFile)
                                
                                if (matchedEpNum == epNum) {
                                    bestMatch = file
                                }
                            }
                        }
                    }
                    // In case we couldn't match the specific episode regex but only have one general file or season pack
                    if (!extractedFiles.contains(bestMatch) && epNum != -1) {
                         val exactPattern = java.util.regex.Pattern.compile("(?i)(?:E|EP|Episode)[\\s_\\.-]*0*$epNum\\b")
                         for (file in extractedFiles) {
                             if (exactPattern.matcher(file.name).find()) {
                                 bestMatch = file
                                 break
                             }
                         }
                    }
                }

                val ext = if (bestMatch.name.endsWith(".srt")) ".srt" else ".vtt"
                val finalFile = File(downloadsDir, "$mediaId$ext")
                if (finalFile.exists()) finalFile.delete()
                bestMatch.renameTo(finalFile)
                
                for (file in extractedFiles) {
                    if (file.exists()) file.delete() // delete all tmps
                }

                return@withContext finalFile
            }
            conn.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    // ===== NEW: Direct Subdl API =====
    suspend fun fetchSubdlSubtitles(tmdbId: String, isTv: Boolean, season: Int = 0, episode: Int = 0): List<SubtitleItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<SubtitleItem>()
        try {
            val url = StringBuilder("https://api.subdl.com/api/v1/subtitles?api_key=$SUBDL_API_KEY&tmdb_id=$tmdbId&type=${if (isTv) "tv" else "movie"}&languages=ar&unpack=1")
            if (isTv && season > 0) {
                url.append("&season_number=$season")
                if (episode > 0) url.append("&episode_number=$episode")
            }
            val conn = URL(url.toString()).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connect()
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                if (json.optBoolean("status")) {
                    val subtitles = json.optJSONArray("subtitles")
                    if (subtitles != null) {
                        for (i in 0 until subtitles.length()) {
                            val sub = subtitles.getJSONObject(i)
                            val subUrl = sub.optString("url", "")
                            val lang = sub.optString("language", "")
                            val releaseName = sub.optString("release_name", "")
                            val name = sub.optString("name", releaseName)
                            // Check for unpacked files (individual files)
                            val unpackFiles = sub.optJSONArray("unpack_files")
                            if (unpackFiles != null && unpackFiles.length() > 0) {
                                for (j in 0 until unpackFiles.length()) {
                                    val f = unpackFiles.getJSONObject(j)
                                    val fileUrl = f.optString("url", "")
                                    val fileName = f.optString("name", "")
                                    val ep = f.optInt("episode", episode)
                                    if (fileUrl.isNotEmpty() && (!isTv || ep == episode)) {
                                        list.add(SubtitleItem(
                                            name = fileName.ifEmpty { name },
                                            lang = when (lang.uppercase()) { "AR" -> "العربية"; "EN" -> "English"; else -> lang },
                                            url = "https://dl.subdl.com$fileUrl",
                                            langCode = lang.lowercase(),
                                            source = "Subdl"
                                        ))
                                    }
                                }
                            } else if (subUrl.isNotEmpty()) {
                                list.add(SubtitleItem(
                                    name = name,
                                    lang = when (lang.uppercase()) { "AR" -> "العربية"; "EN" -> "English"; else -> lang },
                                    url = "https://dl.subdl.com$subUrl",
                                    langCode = lang.lowercase(),
                                    source = "Subdl"
                                ))
                            }
                        }
                    }
                }
            }
            conn.disconnect()
        } catch (_: Exception) { }
        list.distinctBy { it.url }
    }

    // ===== NEW: OpenSubtitles API =====
    suspend fun fetchOpenSubtitles(tmdbId: String, isTv: Boolean, season: Int = 0, episode: Int = 0): List<SubtitleItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<SubtitleItem>()
        try {
            val url = StringBuilder("https://api.opensubtitles.com/api/v1/subtitles?languages=ar")
            if (isTv) {
                url.append("&parent_tmdb_id=$tmdbId&type=episode")
                if (season > 0) url.append("&season_number=$season")
                if (episode > 0) url.append("&episode_number=$episode")
            } else {
                url.append("&tmdb_id=$tmdbId&type=movie")
            }
            val conn = URL(url.toString()).openConnection() as HttpURLConnection
            conn.setRequestProperty("Api-Key", OPENSUBTITLES_API_KEY)
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            conn.connect()
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                val data = json.optJSONArray("data")
                if (data != null) {
                    for (i in 0 until data.length()) {
                        val item = data.getJSONObject(i)
                        val attrs = item.optJSONObject("attributes")
                        if (attrs != null) {
                            val lang = attrs.optString("language", "")
                            val subId = attrs.optString("subtitle_id", "")
                            val downloadCount = attrs.optInt("download_count", 0)
                            val files = attrs.optJSONArray("files")
                            if (files != null && files.length() > 0) {
                                for (j in 0 until files.length()) {
                                    val file = files.getJSONObject(j)
                                    val fileId = file.optInt("file_id", 0)
                                    val fileName = file.optString("file_name", "")
                                    if (fileId > 0) {
                                        list.add(SubtitleItem(
                                            name = fileName.ifEmpty { "OpenSubtitles #$subId" },
                                            lang = when (lang.uppercase()) { "ARABIC" -> "العربية"; "ENGLISH" -> "English"; else -> lang },
                                            url = "",
                                            langCode = when (lang.uppercase()) { "ARABIC" -> "ar"; "ENGLISH" -> "en"; else -> lang.lowercase().take(2) },
                                            source = "OpenSubtitles",
                                            fileId = fileId.toString(),
                                            downloadCount = downloadCount
                                        ))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            conn.disconnect()
        } catch (_: Exception) { }
        list
    }

    // ===== NEW: Get actual download URL from OpenSubtitles =====
    suspend fun getOpenSubtitleDownloadUrl(fileId: String): String? = withContext(Dispatchers.IO) {
        try {
            val conn = URL("https://api.opensubtitles.com/api/v1/download").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Api-Key", OPENSUBTITLES_API_KEY)
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            val body = "{\"file_id\":$fileId}"
            conn.outputStream.write(body.toByteArray())
            conn.connect()
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                json.optString("link", null)
            } else {
                null
            }
        } catch (_: Exception) { null }
    }
}
