package com.orion.downloader.core

import android.content.Context
import com.orion.downloader.util.StorageHelper
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

class HttpDownloadEngine(private val context: Context) {
    
    @Volatile
    private var isDownloading = false
    @Volatile
    private var isPaused = false
    @Volatile
    private var shouldCancel = false
    
    private var currentFileInfo: StorageHelper.DownloadFileInfo? = null
    
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
        filename: String,
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
            
            val mimeType = StorageHelper.getMimeType(filename)
            val fileInfo = StorageHelper.createDownloadFile(context, filename, mimeType)
            currentFileInfo = fileInfo
            
            if (fileInfo.tempFile == null) {
                isDownloading = false
                return@withContext false
            }
            
            RandomAccessFile(fileInfo.tempFile, "rw").use { raf ->
                raf.setLength(contentLength)
            }
            
            val startTime = System.currentTimeMillis()
            val totalDownloaded = java.util.concurrent.atomic.AtomicLong(0L)
            
            var downloadSuccess = false
            
            try {
                coroutineScope {
                    val jobs = (0 until actualConnections).map { i ->
                        async {
                            val start = i * chunkSize
                            val end = if (i == actualConnections - 1) contentLength - 1 else start + chunkSize - 1
                            
                            downloadChunk(
                                url = url,
                                outputFile = fileInfo.tempFile,
                                start = start,
                                end = end,
                                onProgress = { downloaded ->
                                    if (!isPaused && !shouldCancel) {
                                        val total = totalDownloaded.addAndGet(downloaded)
                                        val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                                        val speed = if (elapsed > 0) total / elapsed else 0.0
                                        
                                        progressCallback?.onProgress(
                                            DownloadProgress(
                                                downloadedBytes = total,
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
                
                downloadSuccess = !shouldCancel
                
            } catch (e: Exception) {
                e.printStackTrace()
                downloadSuccess = false
            }
            
            if (downloadSuccess) {
                StorageHelper.finishDownload(context, fileInfo)
            } else {
                StorageHelper.cancelDownload(context, fileInfo)
            }
            
            currentFileInfo = null
            isDownloading = false
            return@withContext downloadSuccess
            
        } catch (e: Exception) {
            e.printStackTrace()
            currentFileInfo?.let { StorageHelper.cancelDownload(context, it) }
            currentFileInfo = null
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
        currentFileInfo?.let { 
            try {
                StorageHelper.cancelDownload(context, it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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
        var connection: HttpURLConnection? = null
        var raf: RandomAccessFile? = null
        
        try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Range", "bytes=$start-$end")
            connection.connectTimeout = 10000
            connection.readTimeout = 30000
            connection.connect()
            
            val inputStream = connection.inputStream
            raf = RandomAccessFile(outputFile, "rw")
            raf.seek(start)
            
            val buffer = ByteArray(8192)
            var bytesRead: Int
            
            while (!shouldCancel) {
                while (isPaused && !shouldCancel) {
                    delay(100)
                }
                
                if (shouldCancel) break
                
                bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) break
                
                raf.write(buffer, 0, bytesRead)
                onProgress(bytesRead.toLong())
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                raf?.close()
                connection?.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
