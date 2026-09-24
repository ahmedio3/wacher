package com.aistudio.cinemios.fxtyr.utils

import kotlinx.coroutines.*
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

object MultiThreadDownloader {

    private val activeDownloads = mutableMapOf<String, Job>()

    fun startDownload(
        downloadId: String,
        url: String,
        outputFile: File,
        scope: CoroutineScope,
        headers: Map<String, String>? = null,
        targetQuality: String = "1080p",
        onProgress: (Int, Long, Long, String) -> Unit, // progress, downloadedBytes, totalBytes, speed string
        onComplete: (Boolean) -> Unit
    ) {
        if (activeDownloads.containsKey(downloadId)) return

        val isDash = url.contains(".mpd") || url.contains("/dash/")

        val job = scope.launch(Dispatchers.IO) {
            try {
                if (isDash) {
                    downloadDashStream(
                        downloadId = downloadId,
                        url = url,
                        outputFile = outputFile,
                        headers = headers,
                        targetQuality = targetQuality,
                        onProgress = onProgress,
                        onComplete = onComplete
                    )
                } else {
                    downloadStandardFile(
                        downloadId = downloadId,
                        url = url,
                        outputFile = outputFile,
                        headers = headers,
                        onProgress = onProgress,
                        onComplete = onComplete
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (isActive) {
                    withContext(Dispatchers.Main) { onComplete(false) }
                }
            } finally {
                activeDownloads.remove(downloadId)
            }
        }
        activeDownloads[downloadId] = job
    }

    private data class DashRep(
        val id: String,
        val mimeType: String,
        val height: Int,
        val bandwidth: Long,
        val isAudio: Boolean
    )

    private suspend fun downloadDashStream(
        downloadId: String,
        url: String,
        outputFile: File,
        headers: Map<String, String>?,
        targetQuality: String,
        onProgress: (Int, Long, Long, String) -> Unit,
        onComplete: (Boolean) -> Unit
    ) {
        val downloadDir = if (outputFile.isDirectory) outputFile else (outputFile.parentFile ?: outputFile)
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }

        // 1. Fetch index.mpd
        val mpdConn = URL(url).openConnection() as HttpURLConnection
        mpdConn.connectTimeout = 15000
        mpdConn.readTimeout = 15000
        mpdConn.setRequestProperty("User-Agent", "okhttp/4.10.0")
        headers?.forEach { (k, v) -> mpdConn.setRequestProperty(k, v) }
        val mpdCode = mpdConn.responseCode
        if (mpdCode != HttpURLConnection.HTTP_OK) {
            mpdConn.disconnect()
            withContext(Dispatchers.Main) { onComplete(false) }
            return
        }
        val mpdText = mpdConn.inputStream.bufferedReader().use { it.readText() }
        mpdConn.disconnect()

        // 2. Parse representations
        val reps = mutableListOf<DashRep>()
        val repRegex = Regex("""<Representation\s+([^>]+)>""", RegexOption.IGNORE_CASE)
        for (match in repRegex.findAll(mpdText)) {
            val attrs = match.groupValues[1]
            val id = Regex("""id="([^"]+)"""").find(attrs)?.groupValues?.get(1) ?: continue
            val mime = Regex("""mimeType="([^"]+)"""").find(attrs)?.groupValues?.get(1) ?: ""
            val height = Regex("""height="(\d+)"""").find(attrs)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val bw = Regex("""bandwidth="(\d+)"""").find(attrs)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val isAudio = mime.startsWith("audio") || id == "3" || height == 0
            reps.add(DashRep(id, mime, height, bw, isAudio))
        }

        if (reps.isEmpty()) {
            withContext(Dispatchers.Main) { onComplete(false) }
            return
        }

        // 3. Select target video representation and audio representation
        val targetHeight = Regex("""(\d+)""").find(targetQuality)?.groupValues?.get(1)?.toIntOrNull() ?: 1080
        val videoReps = reps.filter { !it.isAudio }
        val audioReps = reps.filter { it.isAudio }

        val selectedVideo = videoReps.find { it.height == targetHeight }
            ?: videoReps.minByOrNull { Math.abs(it.height - targetHeight) }
            ?: videoReps.firstOrNull()

        val selectedAudio = audioReps.firstOrNull()

        if (selectedVideo == null) {
            withContext(Dispatchers.Main) { onComplete(false) }
            return
        }

        // 4. Calculate total chunks
        var totalChunks = 0
        val timelineMatch = Regex("""<SegmentTimeline>(.*?)</SegmentTimeline>""", RegexOption.DOT_MATCHES_ALL).find(mpdText)
        if (timelineMatch != null) {
            val sElements = Regex("""<S\s+([^>]+)/?>""").findAll(timelineMatch.groupValues[1])
            for (s in sElements) {
                val rAttr = Regex("""r="(\d+)"""").find(s.groupValues[1])?.groupValues?.get(1)?.toIntOrNull() ?: 0
                totalChunks += (1 + rAttr)
            }
        }
        if (totalChunks <= 0) {
            val durMatch = Regex("""mediaPresentationDuration="([^"]+)"""").find(mpdText)
            if (durMatch != null) {
                val durStr = durMatch.groupValues[1]
                val hours = Regex("""(\d+)H""").find(durStr)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                val mins = Regex("""(\d+)M""").find(durStr)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                val secs = Regex("""([\d.]+)S""").find(durStr)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                val totalSeconds = hours * 3600 + mins * 60 + secs
                totalChunks = Math.ceil(totalSeconds / 6.0).toInt().coerceAtLeast(1)
            }
        }
        if (totalChunks <= 0) {
            withContext(Dispatchers.Main) { onComplete(false) }
            return
        }

        // 5. Write filtered index.mpd locally so ExoPlayer only queries the downloaded representations
        var cleanMpd = mpdText
        for (rep in reps) {
            if (rep.id != selectedVideo.id && (selectedAudio == null || rep.id != selectedAudio.id)) {
                val blockPattern = Regex("""<Representation\s+[^>]*id="${rep.id}"[^>]*>.*?</Representation>""", RegexOption.DOT_MATCHES_ALL)
                cleanMpd = if (blockPattern.containsMatchIn(cleanMpd)) {
                    blockPattern.replace(cleanMpd, "")
                } else {
                    val selfClosingPattern = Regex("""<Representation\s+[^>]*id="${rep.id}"[^>]*/>""", RegexOption.DOT_MATCHES_ALL)
                    selfClosingPattern.replace(cleanMpd, "")
                }
            }
        }
        val localMpdFile = File(downloadDir, "index.mpd")
        localMpdFile.writeText(cleanMpd)

        // 6. Build file list
        val baseUrl = if (url.contains("/")) url.substringBeforeLast("/") + "/" else url
        val initVideoName = "init-stream${selectedVideo.id}.m4s"
        val initAudioName = selectedAudio?.let { "init-stream${it.id}.m4s" }

        val allFiles = mutableListOf<Pair<String, String>>()
        allFiles.add(initVideoName to "$baseUrl$initVideoName")
        if (initAudioName != null) {
            allFiles.add(initAudioName to "$baseUrl$initAudioName")
        }

        for (chunkIdx in 1..totalChunks) {
            val numStr = String.format("%05d", chunkIdx)
            val videoChunk = "chunk-stream${selectedVideo.id}-$numStr.m4s"
            allFiles.add(videoChunk to "$baseUrl$videoChunk")
            if (selectedAudio != null) {
                val audioChunk = "chunk-stream${selectedAudio.id}-$numStr.m4s"
                allFiles.add(audioChunk to "$baseUrl$audioChunk")
            }
        }

        val totalItemsCount = allFiles.size
        val totalBitrate = selectedVideo.bandwidth + (selectedAudio?.bandwidth ?: 0L)
        val estimatedTotalBytes = if (totalBitrate > 0) (totalBitrate * totalChunks * 6L / 8L) else (totalItemsCount * 500_000L)

        val completedCount = AtomicInteger(0)
        val totalDownloadedBytes = AtomicLong(0L)

        val pendingFiles = mutableListOf<Pair<String, String>>()
        for (item in allFiles) {
            val dest = File(downloadDir, item.first)
            if (dest.exists() && dest.length() > 0) {
                completedCount.incrementAndGet()
                totalDownloadedBytes.addAndGet(dest.length())
            } else {
                pendingFiles.add(item)
            }
        }

        // Initial progress update if resuming
        if (completedCount.get() > 0) {
            val initialProg = ((completedCount.get().toDouble() / totalItemsCount.toDouble()) * 100).toInt()
            val initialCur = totalDownloadedBytes.get()
            onProgress(initialProg.coerceIn(0, 99), initialCur, Math.max(initialCur, estimatedTotalBytes), "استئناف التحميل")
        }

        val queue = ConcurrentLinkedQueue(pendingFiles)
        val lastDownloadTimes = mutableListOf<Long>()
        val lastDownloadBytes = mutableListOf<Long>()

        val numWorkers = 8
        coroutineScope {
            val workers = (0 until numWorkers).map {
                async(Dispatchers.IO) {
                    val buffer = ByteArray(64 * 1024)
                    while (isActive) {
                        val item = queue.poll() ?: break
                        val fileName = item.first
                        val fileUrl = item.second
                        val targetFile = File(downloadDir, fileName)
                        val tmpFile = File(downloadDir, "$fileName.tmp")

                        var success = false
                        var retry = 0
                        while (retry < 5 && !success && isActive) {
                            var conn: HttpURLConnection? = null
                            try {
                                conn = URL(fileUrl).openConnection() as HttpURLConnection
                                conn.connectTimeout = 15000
                                conn.readTimeout = 15000
                                conn.setRequestProperty("User-Agent", "okhttp/4.10.0")
                                conn.setRequestProperty("Connection", "keep-alive")
                                headers?.forEach { (k, v) -> conn.setRequestProperty(k, v) }
                                conn.connect()

                                val code = conn.responseCode
                                if (code == HttpURLConnection.HTTP_OK) {
                                    conn.inputStream.use { input ->
                                        tmpFile.outputStream().use { output ->
                                            var read: Int
                                            while (input.read(buffer).also { read = it } != -1) {
                                                if (!isActive) {
                                                    return@async false
                                                }
                                                output.write(buffer, 0, read)
                                                val curDownloaded = totalDownloadedBytes.addAndGet(read.toLong())

                                                synchronized(MultiThreadDownloader) {
                                                    val now = System.currentTimeMillis()
                                                    if (lastDownloadTimes.isEmpty() || now - lastDownloadTimes.last() >= 1000) {
                                                        lastDownloadTimes.add(now)
                                                        lastDownloadBytes.add(curDownloaded)
                                                        if (lastDownloadTimes.size > 5) {
                                                            lastDownloadTimes.removeAt(0)
                                                            lastDownloadBytes.removeAt(0)
                                                        }
                                                        val timeDiff = now - lastDownloadTimes.first()
                                                        var speedStr = "جار التحميل"
                                                        if (timeDiff > 0) {
                                                            val byteDiff = curDownloaded - lastDownloadBytes.first()
                                                            val bytesPerSec = (byteDiff * 1000) / timeDiff
                                                            speedStr = if (bytesPerSec > 1024 * 1024) {
                                                                String.format("%.1f MB/s", bytesPerSec / (1024f * 1024f))
                                                            } else {
                                                                String.format("%.1f KB/s", bytesPerSec / 1024f)
                                                            }
                                                        }
                                                        val progress = ((completedCount.get().toDouble() / totalItemsCount.toDouble()) * 100).toInt()
                                                        val displayTotal = Math.max(curDownloaded, estimatedTotalBytes)
                                                        onProgress(progress.coerceIn(0, 99), curDownloaded, displayTotal, speedStr)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (tmpFile.exists() && tmpFile.length() > 0) {
                                        if (targetFile.exists()) targetFile.delete()
                                        tmpFile.renameTo(targetFile)
                                        completedCount.incrementAndGet()
                                        success = true
                                    }
                                } else {
                                    retry++
                                    delay(1000L * retry)
                                }
                            } catch (e: Exception) {
                                tmpFile.delete()
                                retry++
                                delay(1000L * retry)
                            } finally {
                                conn?.disconnect()
                            }
                        }
                        if (!success) {
                            return@async false
                        }
                    }
                    true
                }
            }

            val results = workers.awaitAll()
            val allSuccess = results.all { it } && completedCount.get() >= totalItemsCount

            if (allSuccess && isActive) {
                val finalBytes = totalDownloadedBytes.get()
                onProgress(100, finalBytes, finalBytes, "مكتمل")
                withContext(Dispatchers.Main) { onComplete(true) }
            } else if (isActive) {
                withContext(Dispatchers.Main) { onComplete(false) }
            }
        }
    }

    private suspend fun downloadStandardFile(
        downloadId: String,
        url: String,
        outputFile: File,
        headers: Map<String, String>?,
        onProgress: (Int, Long, Long, String) -> Unit,
        onComplete: (Boolean) -> Unit
    ) {
        // Determine file size
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "HEAD"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.setRequestProperty("User-Agent", "okhttp/4.10.0")
        headers?.forEach { (k, v) -> connection.setRequestProperty(k, v) }
        val fileSize = connection.contentLengthLong
        connection.disconnect()

        if (fileSize <= 0) {
            withContext(Dispatchers.Main) { onComplete(false) }
            return
        }

        // Prepare file and random access bounds
        val randomAccessFile = RandomAccessFile(outputFile, "rw")
        if (randomAccessFile.length() != fileSize) {
            randomAccessFile.setLength(fileSize)
        }
        randomAccessFile.close()

        val numThreads = 8
        val chunkSize = fileSize / numThreads

        var totalDownloaded = 0L
        val lastDownloadTimes = mutableListOf<Long>()
        val lastDownloadBytes = mutableListOf<Long>()

        // Check for existing progress (for pause/resume)
        val progressFile = File(outputFile.parentFile, "${outputFile.name}.progress")
        val startOffsets = LongArray(numThreads)

        if (progressFile.exists()) {
            try {
                val lines = progressFile.readLines()
                if (lines.size == numThreads) {
                    for (i in 0 until numThreads) {
                        startOffsets[i] = lines[i].toLong()
                        val originalStartByte = i * chunkSize
                        totalDownloaded += (startOffsets[i] - originalStartByte)
                    }
                } else {
                    progressFile.delete()
                    for (i in 0 until numThreads) startOffsets[i] = i * chunkSize
                }
            } catch (e: Exception) {
                progressFile.delete()
                for (i in 0 until numThreads) startOffsets[i] = i * chunkSize
            }
        } else {
            for (i in 0 until numThreads) startOffsets[i] = i * chunkSize
        }

        coroutineScope {
            val downloadJobs = (0 until numThreads).map { i ->
                async(Dispatchers.IO) {
                    try {
                        val endByte = if (i == numThreads - 1) fileSize - 1 else ((i + 1) * chunkSize) - 1
                        val startByte = startOffsets[i]

                        if (startByte > endByte) return@async true

                        var retryCount = 0
                        var success = false
                        while (retryCount < 5 && !success && isActive) {
                            try {
                                val chunkConnection = URL(url).openConnection() as HttpURLConnection
                                chunkConnection.connectTimeout = 15000
                                chunkConnection.readTimeout = 15000
                                chunkConnection.setRequestProperty("User-Agent", "okhttp/4.10.0")
                                headers?.forEach { (k, v) -> chunkConnection.setRequestProperty(k, v) }
                                chunkConnection.setRequestProperty("Range", "bytes=$startByte-$endByte")
                                chunkConnection.connect()

                                val responseCode = chunkConnection.responseCode
                                if (responseCode != HttpURLConnection.HTTP_PARTIAL && responseCode != HttpURLConnection.HTTP_OK) {
                                    throw Exception("Invalid response code for range: $responseCode")
                                }

                                val input = chunkConnection.inputStream
                                val output = RandomAccessFile(outputFile, "rw")
                                output.seek(startByte)

                                val buffer = ByteArray(64 * 1024)
                                var bytesRead: Int
                                var currentOffset = startByte

                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    if (!isActive) {
                                        input.close()
                                        output.close()
                                        chunkConnection.disconnect()
                                        return@async false
                                    }
                                    output.write(buffer, 0, bytesRead)
                                    currentOffset += bytesRead
                                    startOffsets[i] = currentOffset

                                    synchronized(this@MultiThreadDownloader) {
                                        totalDownloaded += bytesRead

                                        if (totalDownloaded % (512 * 1024) < 65536) {
                                            try {
                                                progressFile.writeText(startOffsets.joinToString("\n"))
                                            } catch (e: Exception) { }
                                        }

                                        val now = System.currentTimeMillis()

                                        if (lastDownloadTimes.isEmpty() || now - lastDownloadTimes.last() >= 1000) {
                                            lastDownloadTimes.add(now)
                                            lastDownloadBytes.add(totalDownloaded)
                                            if (lastDownloadTimes.size > 5) {
                                                lastDownloadTimes.removeAt(0)
                                                lastDownloadBytes.removeAt(0)
                                            }

                                            val progress = ((totalDownloaded.toDouble() / fileSize) * 100).toInt()
                                            val timeDiff = now - lastDownloadTimes.first()
                                            var speedStr = "جار التحميل"
                                            if (timeDiff > 0) {
                                                val byteDiff = totalDownloaded - lastDownloadBytes.first()
                                                val bytesPerSec = (byteDiff * 1000) / timeDiff
                                                speedStr = if (bytesPerSec > 1024 * 1024) {
                                                    String.format("%.1f MB/s", bytesPerSec / (1024f * 1024f))
                                                } else {
                                                    String.format("%.1f KB/s", bytesPerSec / 1024f)
                                                }
                                            }
                                            onProgress(progress.coerceIn(0, 100), totalDownloaded, fileSize, speedStr)
                                        }
                                    }
                                }
                                input.close()
                                output.close()
                                chunkConnection.disconnect()
                                success = true
                            } catch (e: Exception) {
                                e.printStackTrace()
                                retryCount++
                                delay((2000L * retryCount).coerceAtMost(10000L))
                            }
                        }
                        success
                    } catch (e: Exception) {
                        e.printStackTrace()
                        false
                    }
                }
            }

            val results = downloadJobs.awaitAll()
            val allSuccess = results.all { it }

            if (allSuccess && isActive) {
                progressFile.delete()
                withContext(Dispatchers.Main) { onComplete(true) }
            } else if (isActive) {
                withContext(Dispatchers.Main) { onComplete(false) }
            }
        }
    }

    fun pauseDownload(downloadId: String) {
        activeDownloads[downloadId]?.cancel()
        activeDownloads.remove(downloadId)
    }
}
