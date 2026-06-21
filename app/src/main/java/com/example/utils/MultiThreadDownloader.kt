package com.example.utils

import kotlinx.coroutines.*
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

object MultiThreadDownloader {

    private val activeDownloads = mutableMapOf<String, Job>()

    fun startDownload(
        downloadId: String,
        url: String,
        outputFile: File,
        scope: CoroutineScope,
        onProgress: (Int, Long, Long, String) -> Unit, // progress, downloadedBytes, totalBytes, speed string
        onComplete: (Boolean) -> Unit
    ) {
        if (activeDownloads.containsKey(downloadId)) return
        
        val job = scope.launch(Dispatchers.IO) {
            try {
                // Determine file size
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "HEAD"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                val fileSize = connection.contentLengthLong
                connection.disconnect()

                if (fileSize <= 0) {
                    withContext(Dispatchers.Main) { onComplete(false) }
                    return@launch
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
                                // The amount of bytes downloaded in this chunk is the current offset minus the original start byte of the chunk
                                val originalStartByte = i * chunkSize
                                totalDownloaded += (startOffsets[i] - originalStartByte)
                            }
                        } else {
                            // Invalid progress file
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

                val downloadJobs = (0 until numThreads).map { i ->
                    async(Dispatchers.IO) {
                        val endByte = if (i == numThreads - 1) fileSize - 1 else ((i + 1) * chunkSize) - 1
                        val startByte = startOffsets[i]
                        
                        if (startByte > endByte) return@async // Chunk already complete

                        var retryCount = 0
                        var success = false
                        while (retryCount < 3 && !success && isActive) {
                            try {
                                val chunkConnection = URL(url).openConnection() as HttpURLConnection
                                chunkConnection.connectTimeout = 15000
                                chunkConnection.readTimeout = 15000
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
                                          return@async
                                     }
                                     output.write(buffer, 0, bytesRead)
                                     currentOffset += bytesRead
                                     startOffsets[i] = currentOffset
                                     
                                     synchronized(this@MultiThreadDownloader) {
                                         totalDownloaded += bytesRead
                                         
                                         // Save progress occasionally
                                         if (totalDownloaded % (512 * 1024) < 65536) {
                                             try {
                                                 progressFile.writeText(startOffsets.joinToString("\n"))
                                             } catch (e: Exception) { /* ignore write errors */ }
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
                                delay(2000)
                            }
                        }
                        if (!success && isActive) throw Exception("Chunk $i failed after 3 retries")
                    }
                }

                downloadJobs.awaitAll()
                
                if (isActive) { // Complete
                    progressFile.delete() // clean up successful download
                    withContext(Dispatchers.Main) { onComplete(true) }
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

    fun pauseDownload(downloadId: String) {
         activeDownloads[downloadId]?.cancel()
         activeDownloads.remove(downloadId)
    }
}
