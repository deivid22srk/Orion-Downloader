package com.orion.downloader.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.min

class HttpDownloadEngine {
    
    @Volatile
    private var isDownloading = false
    @Volatile
    private var isPaused = false
    @Volatile
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
    
    fun interface ProgressCallback {
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
            
            val startTime = System.currentTimeMillis()
            @Volatile var totalDownloaded = 0L
            
            coroutineScope {
                val jobs = (0 until actualConnections).map { i ->
                    async {
                        val start = i * chunkSize
                        val end = if (i == actualConnections - 1) contentLength - 1 else start + chunkSize - 1
                        
                        downloadChunk(
                            url = url,
                            outputFile = outputFile,
                            start = start,
                            end = end,
                            onProgress = { downloaded ->
                                if (!isPaused && !shouldCancel) {
                                    synchronized(this@HttpDownloadEngine) {
                                        totalDownloaded += downloaded
                                    }
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
                            }
                        )
                    }
                }
                
                jobs.awaitAll()
            }
            
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
    
    private suspend fun downloadChunk(
        url: String,
        outputFile: File,
        start: Long,
        end: Long,
        onProgress: (Long) -> Unit
    ) = withContext(Dispatchers.IO) {
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
            
            while (!shouldCancel) {
                while (isPaused && !shouldCancel) {
                    delay(100)
                }
                
                bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) break
                
                raf.write(buffer, 0, bytesRead)
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
