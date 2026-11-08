package com.aistudio.cinemios.fxtyr.ui.viewmodel

import android.content.Context
import com.aistudio.cinemios.fxtyr.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

object SubtitleHelper {
    private const val USER_AGENT = "Cinemios v1.0 (Android)"
    private const val SUBDL_REFERER = "https://subdl.com/"
    private const val SUBDL_DOWNLOAD_HOST = "https://dl.subdl.com"
    private const val MAX_DOWNLOAD_BYTES = 60L * 1024 * 1024
    private const val MAX_ZIP_ENTRIES = 200
    private const val MAX_ZIP_ENTRY_BYTES = 20L * 1024 * 1024
    private const val MAX_ZIP_TOTAL_BYTES = 120L * 1024 * 1024

    /** Typed failure so the UI can distinguish "no results" from a real error. */
    sealed class SubtitleError(val userMessage: String) {
        data object MissingKey : SubtitleError("مفتاح المصدر غير مُعد في هذا الإصدار.")
        data object Unauthorized : SubtitleError("المفتاح غير صالح أو انتهت صلاحيته. تحقق من إعدادات المصدر.")
        data object ServerRefused : SubtitleError("رفض الخادم الطلب. تحقق من إعدادات المصدر.")
        data class RateLimited(val retryAfterSec: Long? = null) :
            SubtitleError("تم تجاوز حد الطلبات. حاول بعد قليل.")
        data class Http(val code: Int) : SubtitleError("فشل الطلب من الخادم (HTTP $code).")
        data class Network(val detail: String) : SubtitleError("تعذر الاتصال بالخادم: $detail")
        data object NotSubtitle : SubtitleError("الملف الذي تم تنزيله ليس ترجمة صالحة.")
        data object UnsupportedFormat : SubtitleError("صيغة الترجمة غير مدعومة أو الملف تالف.")
        data object Empty : SubtitleError("الملف الذي تم تنزيله فارغ.")
        data object NoEpisodeMatch : SubtitleError("لم أجد حلقة مطابقة داخل الملف. لم يتم تطبيق أي ترجمة.")
        data object NoResults : SubtitleError("لا توجد تراجم متاحة من هذا المصدر.")
    }

    data class SubtitleItem(
        val name: String,
        val lang: String,
        val url: String,
        val langCode: String,
        val source: String = "MovieBox",
        val fileId: String? = null,
        val downloadCount: Int = 0,
        val releaseName: String = "",
        val fps: Double? = null,
        val format: String = ""
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

    // ===== Shared download plumbing =====

    private fun normalizeDownloadUrl(path: String): String {
        if (path.startsWith("http://", true) || path.startsWith("https://", true)) return path
        return if (path.startsWith("/")) "$SUBDL_DOWNLOAD_HOST$path" else "$SUBDL_DOWNLOAD_HOST/$path"
    }

    /**
     * Safe file key for `downloads/`. Normal media ids ("123-s1-e2") are kept
     * byte-for-byte so existing lookups keep working; local file paths are
     * hashed instead of creating nested/illegal directories.
     */
    private fun safeMediaKey(mediaId: String): String {
        val trimmed = mediaId.trim()
        val safe = trimmed.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
        if (safe == trimmed && safe.isNotEmpty()) return safe
        return "${safe}_${trimmed.hashCode().toUInt().toString(16)}"
    }

    /** Public format label for Room rows ("srt", "vtt", "ass", "ssa"). */
    fun detectSubtitleFormat(file: File): String {
        return try {
            sniffSubtitleExtension(file.readBytes(), file.name).removePrefix(".")
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Canonical player subtitle files stored under `downloads/` for this media id.
     * Used by deletion so removing a Room row also removes the legacy fallback.
     */
    fun canonicalSubtitleFiles(context: Context, mediaId: String): List<File> {
        if (mediaId.isBlank()) return emptyList()
        val dir = File(context.filesDir, "downloads")
        if (!dir.isDirectory) return emptyList()
        val key = safeMediaKey(mediaId)
        return dir.listFiles()?.filter { file ->
            file.isFile && file.nameWithoutExtension == key && supportedExtension(file.name) != null
        } ?: emptyList()
    }

    /** Deletes canonical player copies for a media id (app storage only). */
    fun deleteCanonicalPlayerSubtitles(context: Context, mediaId: String): Int {
        var deleted = 0
        for (file in canonicalSubtitleFiles(context, mediaId)) {
            try {
                if (file.delete()) deleted++
            } catch (_: Exception) { }
        }
        return deleted
    }

    /**
     * Copies a downloaded subtitle to the canonical player path
     * (`downloads/<mediaId>.<ext>`) and verifies it is parseable.
     */
    suspend fun copyToCanonicalPlayerPath(
        context: Context,
        source: File,
        mediaId: String
    ): File? = withContext(Dispatchers.IO) {
        try {
            if (!source.exists() || source.length() == 0L) return@withContext null
            val bytes = source.readBytes()
            val ext = sniffSubtitleExtension(bytes, source.name)
            val dir = File(context.filesDir, "downloads").apply { mkdirs() }
            val target = File(dir, "${safeMediaKey(mediaId)}$ext")
            if (source.canonicalPath == target.canonicalPath) return@withContext source
            writeBytesAtomic(target, bytes)
            if (!parseLocalCues(target)) {
                target.delete()
                return@withContext null
            }
            target
        } catch (_: Exception) {
            null
        }
    }

    private fun isSubdlDownload(url: String): Boolean =
        url.contains("dl.subdl.com", ignoreCase = true)

    private fun appendSubdlKeyIfNeeded(url: String): String {
        if (!isSubdlDownload(url)) return url
        if (BuildConfig.SUBDL_API_KEY.isBlank()) return url
        if (url.contains("api_key=", ignoreCase = true)) return url
        val separator = if (url.contains("?")) "&" else "?"
        return "$url${separator}api_key=${BuildConfig.SUBDL_API_KEY}"
    }

    private fun openDownloadConnection(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Accept", "*/*")
        // The app handles real gzip payloads from magic bytes, so ask for no
        // transfer-level compression to avoid double decompression.
        conn.setRequestProperty("Accept-Encoding", "identity")
        if (isSubdlDownload(url)) {
            conn.setRequestProperty("Referer", SUBDL_REFERER)
            if (BuildConfig.SUBDL_API_KEY.isNotBlank()) {
                // Authenticated download on dl.subdl.com (documented query form).
                conn.setRequestProperty("x-api-key", BuildConfig.SUBDL_API_KEY)
            }
        }
        conn.connect()
        return conn
    }

    private fun readBodyLimited(conn: HttpURLConnection): ByteArray {
        val input = conn.inputStream
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0L
        try {
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_DOWNLOAD_BYTES) throw IOException("payload too large")
                out.write(buffer, 0, read)
            }
        } finally {
            try { input.close() } catch (_: Exception) { }
        }
        return out.toByteArray()
    }

    private fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()

    private fun isGzip(bytes: ByteArray): Boolean =
        bytes.size >= 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()

    private fun looksLikeHtml(bytes: ByteArray): Boolean {
        val head = String(bytes, 0, minOf(bytes.size, 1024), StandardCharsets.UTF_8)
            .trimStart('\uFEFF', ' ', '\t', '\r', '\n')
            .lowercase()
        return head.startsWith("<!doctype html") ||
            head.startsWith("<html") ||
            head.contains("<title>just a moment") ||
            head.contains("cloudflare")
    }

    private fun extensionFromUrl(url: String): String? {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return when {
            path.endsWith(".srt") -> ".srt"
            path.endsWith(".vtt") -> ".vtt"
            path.endsWith(".ass") -> ".ass"
            path.endsWith(".ssa") -> ".ssa"
            else -> null
        }
    }

    private fun supportedExtension(name: String): String? {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".srt") -> ".srt"
            lower.endsWith(".vtt") -> ".vtt"
            lower.endsWith(".ass") -> ".ass"
            lower.endsWith(".ssa") -> ".ssa"
            else -> null
        }
    }

    private fun localizedLanguageName(code: String): String = when (code.uppercase()) {
        "AR", "ARABIC" -> "العربية"
        "EN", "ENGLISH" -> "English"
        "FR", "FRENCH" -> "Français"
        else -> code
    }

    private fun reportError(onError: ((SubtitleError) -> Unit)?, error: SubtitleError) {
        try { onError?.invoke(error) } catch (_: Exception) { }
    }

    private fun mapHttpError(code: Int, retryAfter: String?): SubtitleError = when (code) {
        401, 403 -> SubtitleError.Unauthorized
        429 -> SubtitleError.RateLimited(retryAfter?.trim()?.toLongOrNull())
        else -> SubtitleError.Http(code)
    }

    /** Sniff the real subtitle format from the payload, not just the URL. */
    private fun sniffSubtitleExtension(bytes: ByteArray, url: String): String {
        val head = String(bytes, 0, minOf(bytes.size, 4096), StandardCharsets.UTF_8)
            .trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        if (head.startsWith("WEBVTT", ignoreCase = true)) return ".vtt"
        if (head.contains("[Script Info]", ignoreCase = true) ||
            head.contains("[Events]", ignoreCase = true) ||
            head.contains("Dialogue:", ignoreCase = true)
        ) {
            return ".ass"
        }
        return extensionFromUrl(url) ?: ".srt"
    }

    private fun writeBytesAtomic(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        FileOutputStream(temp).use { it.write(bytes) }
        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) {
            // Some file systems refuse to rename over an existing file.
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }

    private fun parseLocalCues(file: File): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        return try {
            SubtitleParser.parseBlock(file).isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    private fun extractEpisodeNumber(fileName: String): Int {
        val patterns = listOf(
            Regex("(?i)S\\d{1,2}[\\s._-]*E(\\d{1,3})(?:\\D|$)"),
            Regex("(?i)(?:^|[\\s._-])(?:E|EP|Episode)[\\s._-]*0*(\\d{1,3})(?:\\D|$)"),
            Regex("(?i)(?:^|[\\s._-])\\d{1,2}x0*(\\d{1,3})(?:\\D|$)")
        )
        for (pattern in patterns) {
            val match = pattern.find(fileName) ?: continue
            return match.groupValues[1].toIntOrNull() ?: 0
        }
        return 0
    }

    private fun extractZipEntries(bytes: ByteArray, directory: File): List<Pair<File, Int>> {
        val result = mutableListOf<Pair<File, Int>>()
        var entryCount = 0
        var totalBytes = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entryCount++
                    if (entryCount > MAX_ZIP_ENTRIES) throw IOException("too many zip entries")
                    if (supportedExtension(entry.name) != null) {
                        val safeName = File(entry.name).name
                        val tempFile = File(directory, "tmp_${System.currentTimeMillis()}_${result.size}_$safeName")
                        val out = FileOutputStream(tempFile)
                        try {
                            val buffer = ByteArray(8192)
                            var entryBytes = 0L
                            while (true) {
                                val read = zis.read(buffer)
                                if (read < 0) break
                                entryBytes += read
                                totalBytes += read
                                if (entryBytes > MAX_ZIP_ENTRY_BYTES || totalBytes > MAX_ZIP_TOTAL_BYTES) {
                                    throw IOException("zip entry too large")
                                }
                                out.write(buffer, 0, read)
                            }
                        } finally {
                            out.close()
                        }
                        result.add(Pair(tempFile, extractEpisodeNumber(safeName)))
                    }
                }
                entry = zis.nextEntry
            }
        }
        return result
    }

    private fun cleanupTempFiles(files: List<Pair<File, Int>>) {
        for ((file, _) in files) {
            try { if (file.exists()) file.delete() } catch (_: Exception) { }
        }
    }

    /**
     * Downloads a subtitle and stores it under `downloads/<mediaId>.<ext>` so the
     * player can find it deterministically. ZIP season packs are also expanded to
     * per-episode files. Returns null when the payload is not a usable subtitle.
     */
    suspend fun downloadAndExtractSubtitle(
        context: Context,
        downloadUrlPath: String,
        mediaId: String,
        onError: ((SubtitleError) -> Unit)? = null
    ): File? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val baseUrl = normalizeDownloadUrl(downloadUrlPath)
            conn = openDownloadConnection(appendSubdlKeyIfNeeded(baseUrl))

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                reportError(onError, mapHttpError(responseCode, conn.getHeaderField("Retry-After")))
                return@withContext null
            }

            val body = readBodyLimited(conn)
            if (body.isEmpty()) {
                reportError(onError, SubtitleError.Empty)
                return@withContext null
            }
            if (looksLikeHtml(body)) {
                reportError(onError, SubtitleError.NotSubtitle)
                return@withContext null
            }

            val downloadsDir = File(context.filesDir, "downloads")
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            val isTv = mediaId.contains("-s") && mediaId.contains("-e")
            val tmdbIdString = if (isTv) mediaId.substringBefore("-s") else mediaId
            val seasonString = if (isTv) mediaId.substringAfter("-s").substringBefore("-e") else "1"
            val episodeNumber = if (isTv) mediaId.substringAfter("-e").toIntOrNull() ?: -1 else -1

            // ── GZIP: decompress first, then save as a raw subtitle ──
            if (isGzip(body)) {
                val decompressed = try {
                    GZIPInputStream(ByteArrayInputStream(body)).use { it.readBytes() }
                } catch (_: Exception) {
                    reportError(onError, SubtitleError.UnsupportedFormat)
                    return@withContext null
                }
                if (decompressed.isEmpty()) {
                    reportError(onError, SubtitleError.Empty)
                    return@withContext null
                }
                if (looksLikeHtml(decompressed)) {
                    reportError(onError, SubtitleError.NotSubtitle)
                    return@withContext null
                }
                val ext = sniffSubtitleExtension(decompressed, baseUrl)
                val file = File(downloadsDir, "${safeMediaKey(mediaId)}$ext")
                writeBytesAtomic(file, decompressed)
                if (!parseLocalCues(file)) {
                    file.delete()
                    reportError(onError, SubtitleError.UnsupportedFormat)
                    return@withContext null
                }
                return@withContext file
            }

            // ── Raw subtitle ──
            if (!isZip(body)) {
                val ext = sniffSubtitleExtension(body, baseUrl)
                val file = File(downloadsDir, "${safeMediaKey(mediaId)}$ext")
                writeBytesAtomic(file, body)
                if (!parseLocalCues(file)) {
                    file.delete()
                    reportError(onError, SubtitleError.UnsupportedFormat)
                    return@withContext null
                }
                return@withContext file
            }

            // ── ZIP archive ──
            val extractedFiles = extractZipEntries(body, downloadsDir)
            if (extractedFiles.isEmpty()) {
                reportError(onError, SubtitleError.UnsupportedFormat)
                return@withContext null
            }

            var matchedFile: File? = null
            if (isTv) {
                for ((tempFile, matchedEpisode) in extractedFiles) {
                    if (matchedEpisode <= 0) continue
                    val ext = supportedExtension(tempFile.name) ?: continue
                    val target = File(downloadsDir, "$tmdbIdString-s$seasonString-e$matchedEpisode$ext")
                    writeBytesAtomic(target, tempFile.readBytes())
                    if (matchedEpisode == episodeNumber) matchedFile = target
                }
            }

            // Never guess the first file for a TV episode: that silently applies
            // the wrong episode's dialogue and looks like severe desync.
            val bestMatch: File? = if (isTv) matchedFile else extractedFiles.firstOrNull()?.first
            if (bestMatch == null) {
                cleanupTempFiles(extractedFiles)
                reportError(onError, SubtitleError.NoEpisodeMatch)
                return@withContext null
            }

            val ext = supportedExtension(bestMatch.name)
                ?: sniffSubtitleExtension(bestMatch.readBytes(), bestMatch.name)
            val finalFile = File(downloadsDir, "${safeMediaKey(mediaId)}$ext")
            writeBytesAtomic(finalFile, bestMatch.readBytes())
            cleanupTempFiles(extractedFiles)

            if (!parseLocalCues(finalFile)) {
                finalFile.delete()
                reportError(onError, SubtitleError.UnsupportedFormat)
                return@withContext null
            }
            finalFile
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            reportError(onError, SubtitleError.Network(e.message ?: "خطأ غير متوقع"))
            null
        } finally {
            try { conn?.disconnect() } catch (_: Exception) { }
        }
    }

    /**
     * Downloads a subtitle to the standalone store. Every supported entry in a
     * ZIP is kept with its detected episode number (0 = unknown). The real
     * format extension is preserved, so ASS/SSA no longer become broken `.srt`.
     */
    suspend fun downloadSubtitleStandalone(
        context: Context,
        downloadUrlPath: String,
        subtitleId: String,
        onError: ((SubtitleError) -> Unit)? = null
    ): List<Pair<File, Int>> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val baseUrl = normalizeDownloadUrl(downloadUrlPath)
            conn = openDownloadConnection(appendSubdlKeyIfNeeded(baseUrl))

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                reportError(onError, mapHttpError(responseCode, conn.getHeaderField("Retry-After")))
                return@withContext emptyList()
            }

            val body = readBodyLimited(conn)
            if (body.isEmpty()) {
                reportError(onError, SubtitleError.Empty)
                return@withContext emptyList()
            }
            if (looksLikeHtml(body)) {
                reportError(onError, SubtitleError.NotSubtitle)
                return@withContext emptyList()
            }

            val subsDir = File(context.filesDir, "standalone_subtitles")
            if (!subsDir.exists()) subsDir.mkdirs()

            if (isGzip(body)) {
                val decompressed = try {
                    GZIPInputStream(ByteArrayInputStream(body)).use { it.readBytes() }
                } catch (_: Exception) {
                    reportError(onError, SubtitleError.UnsupportedFormat)
                    return@withContext emptyList()
                }
                if (decompressed.isEmpty()) {
                    reportError(onError, SubtitleError.Empty)
                    return@withContext emptyList()
                }
                if (looksLikeHtml(decompressed)) {
                    reportError(onError, SubtitleError.NotSubtitle)
                    return@withContext emptyList()
                }
                val ext = sniffSubtitleExtension(decompressed, baseUrl)
                val file = File(subsDir, "$subtitleId$ext")
                writeBytesAtomic(file, decompressed)
                if (!parseLocalCues(file)) {
                    file.delete()
                    reportError(onError, SubtitleError.UnsupportedFormat)
                    return@withContext emptyList()
                }
                return@withContext listOf(Pair(file, 0))
            }

            if (!isZip(body)) {
                val ext = sniffSubtitleExtension(body, baseUrl)
                val file = File(subsDir, "$subtitleId$ext")
                writeBytesAtomic(file, body)
                if (!parseLocalCues(file)) {
                    file.delete()
                    reportError(onError, SubtitleError.UnsupportedFormat)
                    return@withContext emptyList()
                }
                return@withContext listOf(Pair(file, 0))
            }

            val extractedFiles = extractZipEntries(body, subsDir)
            if (extractedFiles.isEmpty()) {
                reportError(onError, SubtitleError.UnsupportedFormat)
                return@withContext emptyList()
            }

            val results = mutableListOf<Pair<File, Int>>()
            var index = 0
            for ((tempFile, matchedEpisode) in extractedFiles) {
                val ext = supportedExtension(tempFile.name)
                    ?: sniffSubtitleExtension(tempFile.readBytes(), tempFile.name)
                val finalFile = File(subsDir, "${subtitleId}_f${index}$ext")
                writeBytesAtomic(finalFile, tempFile.readBytes())
                if (parseLocalCues(finalFile)) {
                    results.add(Pair(finalFile, matchedEpisode))
                    index++
                } else {
                    finalFile.delete()
                }
            }
            cleanupTempFiles(extractedFiles)

            if (results.isEmpty()) {
                reportError(onError, SubtitleError.UnsupportedFormat)
                return@withContext emptyList()
            }

            results.sortedWith(compareBy({ if (it.second > 0) 0 else 1 }, { it.second }, { it.first.name }))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            reportError(onError, SubtitleError.Network(e.message ?: "خطأ غير متوقع"))
            emptyList()
        } finally {
            try { conn?.disconnect() } catch (_: Exception) { }
        }
    }

    // ===== NEW: Direct Subdl API =====
    suspend fun fetchSubdlSubtitles(tmdbId: String, isTv: Boolean, season: Int = 0, episode: Int = 0, onError: ((SubtitleError) -> Unit)? = null): List<SubtitleItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<SubtitleItem>()
        var conn: HttpURLConnection? = null
        try {
            if (BuildConfig.SUBDL_API_KEY.isBlank()) {
                reportError(onError, SubtitleError.MissingKey)
                return@withContext emptyList()
            }

            val url = StringBuilder("https://api.subdl.com/api/v1/subtitles")
                .append("?api_key=").append(BuildConfig.SUBDL_API_KEY)
                .append("&tmdb_id=").append(tmdbId)
                .append("&type=").append(if (isTv) "tv" else "movie")
                .append("&languages=AR")
                .append("&unpack=1")
                .append("&subs_per_page=30")
                .append("&client=cinemios")
            if (isTv && season > 0) {
                url.append("&season_number=").append(season)
                if (episode > 0) url.append("&episode_number=").append(episode)
            }

            conn = URL(url.toString()).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            conn.connect()

            if (conn.responseCode != 200) {
                reportError(onError, mapHttpError(conn.responseCode, conn.getHeaderField("Retry-After")))
                return@withContext emptyList()
            }

            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            if (!json.optBoolean("status")) {
                val statusCode = json.optInt("statusCode", 0)
                val error = when (statusCode) {
                    401, 403 -> SubtitleError.Unauthorized
                    429 -> SubtitleError.RateLimited()
                    in 400..599 -> SubtitleError.Http(statusCode)
                    else -> SubtitleError.ServerRefused
                }
                reportError(onError, error)
                return@withContext emptyList()
            }

            val subtitles = json.optJSONArray("subtitles")
            if (subtitles != null) {
                for (i in 0 until subtitles.length()) {
                    val sub = subtitles.getJSONObject(i)
                    val subUrl = sub.optString("url", "")
                    val lang = sub.optString("language", "")
                    val releaseName = sub.optString("release_name", "")
                    val subFormat = sub.optString("format", "")
                    val fps = sub.optDouble("fps", 0.0).takeIf { it > 0.0 }
                        ?: sub.optDouble("framerate", 0.0).takeIf { it > 0.0 }
                    val downloadCount = sub.optInt("download_count", 0)
                    val name = sub.optString("name", releaseName)
                    var matchedAny = false

                    // Check for unpacked files (individual files)
                    val unpackFiles = sub.optJSONArray("unpack_files")
                    if (unpackFiles != null && unpackFiles.length() > 0) {
                        for (j in 0 until unpackFiles.length()) {
                            val f = unpackFiles.getJSONObject(j)
                            val fileUrl = f.optString("url", "")
                            val fileName = f.optString("name", "")
                            val fileLang = f.optString("language", lang)
                            val fileFormat = f.optString("format", subFormat)
                            val fileFps = f.optDouble("fps", 0.0).takeIf { it > 0.0 }
                                ?: f.optDouble("framerate", 0.0).takeIf { it > 0.0 }
                                ?: fps
                            val ep = f.optInt("episode", 0)
                            // 0 / missing means "unknown", not "wrong episode".
                            if (fileUrl.isNotEmpty() && (!isTv || ep <= 0 || ep == episode)) {
                                list.add(SubtitleItem(
                                    name = fileName.ifEmpty { name },
                                    lang = localizedLanguageName(fileLang),
                                    url = normalizeDownloadUrl(fileUrl),
                                    langCode = fileLang.lowercase(),
                                    source = "Subdl",
                                    downloadCount = downloadCount,
                                    releaseName = releaseName,
                                    fps = fileFps,
                                    format = fileFormat
                                ))
                                matchedAny = true
                            }
                        }
                    }

                    // Parent archive is a valid fallback when no per-file match exists.
                    if (!matchedAny && subUrl.isNotEmpty()) {
                        list.add(SubtitleItem(
                            name = name,
                            lang = localizedLanguageName(lang),
                            url = normalizeDownloadUrl(subUrl),
                            langCode = lang.lowercase(),
                            source = "Subdl",
                            downloadCount = downloadCount,
                            releaseName = releaseName,
                            fps = fps,
                            format = subFormat
                        ))
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            reportError(onError, SubtitleError.Network(e.message ?: "خطأ غير متوقع"))
        } finally {
            try { conn?.disconnect() } catch (_: Exception) { }
        }
        list.distinctBy { it.url }
    }

    // ===== NEW: OpenSubtitles API =====
    suspend fun fetchOpenSubtitles(tmdbId: String, isTv: Boolean, season: Int = 0, episode: Int = 0, onError: ((SubtitleError) -> Unit)? = null): List<SubtitleItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<SubtitleItem>()
        var conn: HttpURLConnection? = null
        try {
            if (BuildConfig.OPENSUBTITLES_API_KEY.isBlank()) {
                reportError(onError, SubtitleError.MissingKey)
                return@withContext emptyList()
            }

            val url = StringBuilder("https://api.opensubtitles.com/api/v1/subtitles?languages=ar&order_by=download_count")
            if (isTv) {
                url.append("&parent_tmdb_id=$tmdbId&type=episode")
                if (season > 0) url.append("&season_number=$season")
                if (episode > 0) url.append("&episode_number=$episode")
            } else {
                url.append("&tmdb_id=$tmdbId&type=movie")
            }

            conn = URL(url.toString()).openConnection() as HttpURLConnection
            conn.setRequestProperty("Api-Key", BuildConfig.OPENSUBTITLES_API_KEY)
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.connect()

            if (conn.responseCode != 200) {
                reportError(onError, mapHttpError(conn.responseCode, conn.getHeaderField("Retry-After")))
                return@withContext emptyList()
            }

            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val data = json.optJSONArray("data")
            if (data != null) {
                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    val attrs = item.optJSONObject("attributes") ?: continue
                    val lang = attrs.optString("language", "")
                    val subId = attrs.optString("subtitle_id", "")
                    val downloadCount = attrs.optInt("download_count", 0)
                    val release = attrs.optString("release", "")
                    val subFormat = attrs.optString("format", "")
                    val fps = attrs.optDouble("fps", 0.0).takeIf { it > 0.0 }
                    val featureTitle = attrs.optJSONObject("feature_details")
                        ?.optString("title", "")
                        .orEmpty()
                    val files = attrs.optJSONArray("files")
                    if (files != null && files.length() > 0) {
                        for (j in 0 until files.length()) {
                            val file = files.getJSONObject(j)
                            val fileId = file.optInt("file_id", 0)
                            val fileName = file.optString("file_name", "")
                            if (fileId > 0) {
                                list.add(SubtitleItem(
                                    name = fileName.ifEmpty { "OpenSubtitles #$subId" },
                                    lang = localizedLanguageName(lang),
                                    url = "",
                                    langCode = when (lang.uppercase()) {
                                        "ARABIC" -> "ar"
                                        "ENGLISH" -> "en"
                                        else -> lang.lowercase().take(2)
                                    },
                                    source = "OpenSubtitles",
                                    fileId = fileId.toString(),
                                    downloadCount = downloadCount,
                                    releaseName = release.ifEmpty { featureTitle },
                                    fps = fps,
                                    format = subFormat
                                ))
                            }
                        }
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            reportError(onError, SubtitleError.Network(e.message ?: "خطأ غير متوقع"))
        } finally {
            try { conn?.disconnect() } catch (_: Exception) { }
        }
        // Most downloaded releases first; the UI also shows FPS/release so the
        // user can pick the version that matches the video.
        list.sortedWith(
            compareByDescending<SubtitleItem> { it.downloadCount }
                .thenByDescending { it.releaseName.length }
        )
    }

    // ===== NEW: Get actual download URL from OpenSubtitles =====
    suspend fun getOpenSubtitleDownloadUrl(fileId: String, onError: ((SubtitleError) -> Unit)? = null): String? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            if (BuildConfig.OPENSUBTITLES_API_KEY.isBlank()) {
                reportError(onError, SubtitleError.MissingKey)
                return@withContext null
            }
            conn = URL("https://api.opensubtitles.com/api/v1/download").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Api-Key", BuildConfig.OPENSUBTITLES_API_KEY)
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            val body = "{\"file_id\":$fileId}"
            conn.outputStream.write(body.toByteArray())
            conn.connect()

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                reportError(onError, mapHttpError(responseCode, conn.getHeaderField("Retry-After")))
                return@withContext null
            }

            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val link = json.optString("link", "")
            if (link.isBlank() || link == "null") {
                reportError(onError, SubtitleError.Empty)
                null
            } else {
                link
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            reportError(onError, SubtitleError.Network(e.message ?: "خطأ غير متوقع"))
            null
        } finally {
            try { conn?.disconnect() } catch (_: Exception) { }
        }
    }

    /**
     * Persists a downloaded subtitle as the selected row for its media.
     * Shared by automatic MovieBox downloads and manual player downloads.
     */
    suspend fun registerDownloadedSubtitle(
        context: Context,
        file: File,
        tmdbId: String,
        mediaId: String,
        title: String,
        mediaType: String,
        season: Int,
        episode: Int,
        language: String,
        languageCode: String,
        source: String,
        fileName: String,
        releaseName: String = "",
        fps: Double? = null,
        originalUrl: String = "",
        batchId: String = ""
    ) = withContext(Dispatchers.IO) {
        try {
            val db = com.aistudio.cinemios.fxtyr.data.local.MovieDatabase.getDatabase(context)
            val baseId = if (mediaType == "tv") "${tmdbId}_s${season}e${episode}_$languageCode" else "${tmdbId}_$languageCode"
            val signature = "${languageCode}_${source}_${fileName}"
                .lowercase()
                .hashCode()
                .toUInt()
                .toString(16)
            db.movieDao.saveSelectedSubtitle(
                com.aistudio.cinemios.fxtyr.data.local.SubtitleDownloadEntity(
                    id = "${baseId}_$signature",
                    tmdbId = tmdbId,
                    title = title.substringBefore(" - "),
                    mediaType = mediaType,
                    season = season,
                    episode = episode,
                    language = language,
                    languageCode = languageCode,
                    source = source,
                    localFilePath = file.absolutePath,
                    fileName = fileName,
                    batchId = batchId,
                    originalUrl = originalUrl,
                    downloadedAt = System.currentTimeMillis(),
                    mediaId = mediaId,
                    format = detectSubtitleFormat(file),
                    releaseName = releaseName,
                    fps = fps,
                    isSelected = true
                )
            )
        } catch (_: Exception) { }
    }

    // Shared: fetch MovieBox Arabic subtitle → download → save to Room → return File
    suspend fun fetchAndSaveMovieBoxSubtitle(
        context: Context,
        tmdbId: String,
        isTv: Boolean,
        season: Int,
        episode: Int,
        title: String,
        mediaId: String
    ): File? = withContext(Dispatchers.IO) {
        try {
            val subs = fetchSubtitles(tmdbId, isTv, season, episode, title.substringBefore(" - "))
            val arSub = subs.firstOrNull { it.langCode.equals("ar", ignoreCase = true) }
                ?: subs.firstOrNull { it.lang.contains("AR", ignoreCase = true) }
                ?: subs.firstOrNull() ?: return@withContext null
            val file = downloadAndExtractSubtitle(context, arSub.url, mediaId) ?: return@withContext null

            registerDownloadedSubtitle(
                context = context,
                file = file,
                tmdbId = tmdbId,
                mediaId = mediaId,
                title = title,
                mediaType = if (isTv) "tv" else "movie",
                season = season,
                episode = episode,
                language = arSub.lang,
                languageCode = arSub.langCode,
                source = arSub.source,
                fileName = arSub.name,
                releaseName = arSub.releaseName,
                fps = arSub.fps,
                originalUrl = arSub.url,
                batchId = "auto_$mediaId"
            )

            file
        } catch (_: Exception) { null }
    }
}
