package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.MovieDatabase
import com.example.data.local.SubtitleDownloadEntity
import com.example.ui.viewmodel.SubtitleHelper
import java.io.File

class SubtitleAutoWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val tmdbId = inputData.getString("tmdbId") ?: return Result.failure()
        val downloadId = inputData.getString("downloadId") ?: return Result.failure()
        val title = inputData.getString("title") ?: ""
        val mediaType = inputData.getString("mediaType") ?: "movie"
        val season = inputData.getInt("season", 0)
        val episode = inputData.getInt("episode", 0)
        val posterPath = inputData.getString("posterPath") ?: ""
        val isTv = mediaType == "tv"

        // 1. Fetch from Subdl (primary) — supports season Zips
        val subdlSubs = SubtitleHelper.fetchSubdlSubtitles(tmdbId, isTv, season, episode)
        var arSub = subdlSubs.firstOrNull { it.langCode == "ar" }
            ?: subdlSubs.firstOrNull { it.name.contains("Arabic", true) }

        // 2. Fallback: MovieBox
        if (arSub == null) {
            val movieBoxSubs = SubtitleHelper.fetchSubtitles(tmdbId, isTv, season, episode, title)
            arSub = movieBoxSubs.firstOrNull { it.langCode == "ar" }
                ?: movieBoxSubs.firstOrNull { it.name.contains("Arabic", true) }
        }

        // 3. Fallback: OpenSubtitles
        if (arSub == null) {
            val openSubs = SubtitleHelper.fetchOpenSubtitles(tmdbId, isTv, season, episode)
            arSub = openSubs.firstOrNull { it.langCode == "ar" }
                ?: openSubs.firstOrNull { it.name.contains("Arabic", true) }
        }

        if (arSub == null) {
            return if (runAttemptCount >= 5) Result.failure() else Result.retry()
        }

        // Only save Arabic subtitles automatically
        if (arSub.langCode != "ar") {
            return Result.success()
        }

        // 4. Download & extract using existing helper
        val downloadUrl = if (arSub.fileId != null)
            SubtitleHelper.getOpenSubtitleDownloadUrl(arSub.fileId) ?: arSub.url
        else arSub.url

        val files = SubtitleHelper.downloadSubtitleStandalone(
            applicationContext, downloadUrl, downloadId
        )

        // 5. Save each extracted file to Room with deterministic ID
        val tmdbBase = tmdbId
        val batchId = "auto_${downloadId}"
        val db = MovieDatabase.getDatabase(applicationContext)

        for ((file, matchedEp) in files) {
            val ep: Int

            if (matchedEp == 0 && files.size > 1) {
                // Unmatched file in a multi-file ZIP — skip to avoid wrong episode
                continue
            } else if (matchedEp == 0) {
                ep = episode
            } else {
                ep = matchedEp
            }

            val id = if (isTv)
                "${tmdbBase}_s${season}e${ep}_${arSub.langCode}"
            else
                "${tmdbBase}_${arSub.langCode}"

            val entity = SubtitleDownloadEntity(
                id = id,
                tmdbId = tmdbId,
                title = title,
                mediaType = mediaType,
                posterPath = posterPath,
                season = season,
                episode = ep,
                language = arSub.lang,
                languageCode = arSub.langCode,
                source = arSub.source,
                localFilePath = file.absolutePath,
                fileName = arSub.name,
                batchId = batchId,
                originalUrl = downloadUrl,
                downloadedAt = System.currentTimeMillis()
            )
            db.movieDao.insertSubtitleDownload(entity)

            val playerFileName = if (isTv) "${tmdbBase}-s${season}-e${ep}" else tmdbBase
            val playerExt = if (file.name.endsWith(".vtt")) ".vtt" else ".srt"
            val playerFile = File(
                applicationContext.filesDir,
                "downloads/$playerFileName$playerExt"
            )
            playerFile.parentFile?.mkdirs()
            file.copyTo(playerFile, overwrite = true)
        }

        return Result.success()
    }
}
