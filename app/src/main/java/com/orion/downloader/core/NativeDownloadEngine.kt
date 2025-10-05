package com.orion.downloader.core

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NativeDownloadEngine {
    
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
        fun onProgress(
            downloadedBytes: Long,
            totalBytes: Long,
            speedBps: Double,
            activeConnections: Int
        )
    }
    
    private var engineId: Long = 0L
    
    init {
        try {
            System.loadLibrary("orion_downloader")
            engineId = nativeCreate()
            Log.i("NativeDownloadEngine", "C++ Engine created (HTTP-only): $engineId")
        } catch (e: Exception) {
            Log.w("NativeDownloadEngine", "Failed to load native library, will use Kotlin engine", e)
            engineId = 0L
        }
    }
    
    fun isNativeAvailable(): Boolean = engineId != 0L
    
    suspend fun getContentLength(url: String): Long = withContext(Dispatchers.IO) {
        if (engineId == 0L) return@withContext -1L
        try {
            nativeGetContentLength(engineId, url)
        } catch (e: Exception) {
            Log.e("NativeDownloadEngine", "getContentLength error", e)
            -1L
        }
    }
    
    suspend fun supportsRangeRequests(url: String): Boolean = withContext(Dispatchers.IO) {
        if (engineId == 0L) return@withContext false
        try {
            nativeSupportsRangeRequests(engineId, url)
        } catch (e: Exception) {
            Log.e("NativeDownloadEngine", "supportsRangeRequests error", e)
            false
        }
    }
    
    suspend fun startDownload(
        url: String,
        outputPath: String,
        numConnections: Int = 8,
        progressCallback: ProgressCallback? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (engineId == 0L) return@withContext false
        try {
            nativeStartDownload(
                engineId,
                url,
                outputPath,
                numConnections,
                progressCallback ?: ProgressCallback { _, _, _, _ -> }
            )
        } catch (e: Exception) {
            Log.e("NativeDownloadEngine", "startDownload error", e)
            false
        }
    }
    
    fun pauseDownload() {
        if (engineId == 0L) return
        try {
            nativePauseDownload(engineId)
        } catch (e: Exception) {
            Log.e("NativeDownloadEngine", "pauseDownload error", e)
        }
    }
    
    fun resumeDownload() {
        if (engineId == 0L) return
        try {
            nativeResumeDownload(engineId)
        } catch (e: Exception) {
            Log.e("NativeDownloadEngine", "resumeDownload error", e)
        }
    }
    
    fun cancelDownload() {
        if (engineId == 0L) return
        try {
            nativeCancelDownload(engineId)
        } catch (e: Exception) {
            Log.e("NativeDownloadEngine", "cancelDownload error", e)
        }
    }
    
    fun isDownloading(): Boolean {
        if (engineId == 0L) return false
        return try {
            nativeIsDownloading(engineId)
        } catch (e: Exception) {
            Log.e("NativeDownloadEngine", "isDownloading error", e)
            false
        }
    }
    
    fun isPaused(): Boolean {
        if (engineId == 0L) return false
        return try {
            nativeIsPaused(engineId)
        } catch (e: Exception) {
            Log.e("NativeDownloadEngine", "isPaused error", e)
            false
        }
    }
    
    fun getProgress(): DownloadProgress? {
        if (engineId == 0L) return null
        return try {
            nativeGetProgress(engineId)
        } catch (e: Exception) {
            Log.e("NativeDownloadEngine", "getProgress error", e)
            null
        }
    }
    
    fun destroy() {
        if (engineId != 0L) {
            try {
                nativeDestroy(engineId)
            } catch (e: Exception) {
                Log.e("NativeDownloadEngine", "destroy error", e)
            }
            engineId = 0L
        }
    }
    
    protected fun finalize() {
        destroy()
    }
    
    private external fun nativeCreate(): Long
    private external fun nativeDestroy(engineId: Long)
    private external fun nativeGetContentLength(engineId: Long, url: String): Long
    private external fun nativeSupportsRangeRequests(engineId: Long, url: String): Boolean
    private external fun nativeStartDownload(
        engineId: Long,
        url: String,
        outputPath: String,
        numConnections: Int,
        callback: ProgressCallback
    ): Boolean
    private external fun nativePauseDownload(engineId: Long)
    private external fun nativeResumeDownload(engineId: Long)
    private external fun nativeCancelDownload(engineId: Long)
    private external fun nativeIsDownloading(engineId: Long): Boolean
    private external fun nativeIsPaused(engineId: Long): Boolean
    private external fun nativeGetProgress(engineId: Long): DownloadProgress?
}
