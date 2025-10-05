package com.orion.downloader.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.min

class HttpDownloadEngine {
    
    private var isDownloading = false
    private var isPaused = false
    private var shouldCancel = false
    
    data class DownloadProgress(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedBps: Double,
        val activeConnections: Int
    ) {
        val percentage: Float
            get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes.toFloat()) * 100f else 0f
    }
    
    interface ProgressCallback {
        fun onProgress(progress: DownloadProgress)
    }
    
    suspend fun startDownload(
        url: String,
        outputPath: String,
        numConnections: Int = 8,
        progressCallback: ProgressCallback? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (isDownloading) return@withContext false
        
        try {
            isDownloading = true
            shouldCancel = false
            isPaused = false
            
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connect()
            
            val contentLength = connection.contentLengthLong
            val supportsRanges = connection.getHeaderField("Accept-Ranges") == "bytes"
            connection.disconnect()
            
            if (contentLength <= 0) {
                isDownloading = false
                return@withContext false
            }
            
            val actualConnections = if (supportsRanges) min(numConnections, 16) else 1
            val chunkSize = contentLength / actualConnections
            
            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()
            
            RandomAccessFile(outputFile, "rw").use { raf ->
                raf.setLength(contentLength)
            }
            
            val chunks = mutableListOf<ChunkDownloader>()
            val startTime = System.currentTimeMillis()
            var totalDownloaded = 0L
            
            for (i in 0 until actualConnections) {
                val start = i * chunkSize
                val end = if (i == actualConnections - 1) contentLength - 1 else start + chunkSize - 1
                
                val chunk = ChunkDownloader(
                    url = url,
                    outputFile = outputFile,
                    start = start,
                    end = end,
                    onProgress = { downloaded ->
                        if (!isPaused && !shouldCancel) {
                            totalDownloaded += downloaded
                            val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                            val speed = if (elapsed > 0) totalDownloaded / elapsed else 0.0
                            
                            progressCallback?.onProgress(
                                DownloadProgress(
                                    downloadedBytes = totalDownloaded,
                                    totalBytes = contentLength,
                                    speedBps = speed,
                                    activeConnections = actualConnections
                                )
                            )
                        }
                    },
                    checkPaused = { isPaused },
                    checkCancel = { shouldCancel }
                )
                
                chunks.add(chunk)
            }
            
            chunks.map { chunk ->
                kotlinx.coroutines.async { chunk.download() }
            }.forEach { it.await() }
            
            isDownloading = false
            return@withContext !shouldCancel
            
        } catch (e: Exception) {
            e.printStackTrace()
            isDownloading = false
            return@withContext false
        }
    }
    
    fun pauseDownload() {
        isPaused = true
    }
    
    fun resumeDownload() {
        isPaused = false
    }
    
    fun cancelDownload() {
        shouldCancel = true
        isPaused = false
    }
    
    fun isDownloading() = isDownloading
    fun isPaused() = isPaused
    
    private class ChunkDownloader(
        private val url: String,
        private val outputFile: File,
        private val start: Long,
        private val end: Long,
        private val onProgress: (Long) -> Unit,
        private val checkPaused: () -> Boolean,
        private val checkCancel: () -> Boolean
    ) {
        suspend fun download() = withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Range", "bytes=$start-$end")
                connection.connectTimeout = 10000
                connection.readTimeout = 30000
                connection.connect()
                
                val inputStream = connection.inputStream
                val raf = RandomAccessFile(outputFile, "rw")
                raf.seek(start)
                
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0L
                
                while (!checkCancel()) {
                    while (checkPaused() && !checkCancel()) {
                        Thread.sleep(100)
                    }
                    
                    bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break
                    
                    raf.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    onProgress(bytesRead.toLong())
                }
                
                raf.close()
                inputStream.close()
                connection.disconnect()
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
