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
    
    data class SubtitleItem(
        val name: String,
        val lang: String,
        val url: String,
        val langCode: String
    )
    
    suspend fun fetchSubtitles(tmdbId: String, isTv: Boolean, season: Int = 0, episode: Int = 0): List<SubtitleItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<SubtitleItem>()
        try {
            val urlStr = StringBuilder("https://moviebox-fastapi.vercel.app/get_download_links?subject_id=$tmdbId")
            val conn = URL(urlStr.toString()).openConnection() as HttpURLConnection
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
            
            if (resourceId != null && resourceId.isNotEmpty()) {
                val subUrlStr = "https://moviebox-fastapi.vercel.app/get_subtitles?subject_id=$tmdbId&resource_id=$resourceId"
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
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Sort by 'AR' first, then others
        list.distinctBy { it.url }.sortedByDescending { it.langCode.lowercase() == "ar" }
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
                val epStr = if (isTv) mediaId.substringAfter("-e") else ""
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
}
