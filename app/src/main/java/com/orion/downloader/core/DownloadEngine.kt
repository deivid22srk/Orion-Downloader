package com.orion.downloader.core

class DownloadEngine {
    
    init {
        System.loadLibrary("orion_downloader")
        nativeInit()
    }

    fun interface ProgressCallback {
        fun onProgress(downloadedBytes: Long, totalBytes: Long, speedBps: Double, activeConnections: Int)
    }

    fun setProgressCallback(callback: ProgressCallback?) {
        nativeSetProgressCallback(callback)
    }

    fun startDownload(url: String, outputPath: String, numConnections: Int = 8): Boolean {
        return nativeStartDownload(url, outputPath, numConnections)
    }

    fun pauseDownload() {
        nativePauseDownload()
    }

    fun resumeDownload() {
        nativeResumeDownload()
    }

    fun cancelDownload() {
        nativeCancelDownload()
    }

    fun isDownloading(): Boolean {
        return nativeIsDownloading()
    }

    fun isPaused(): Boolean {
        return nativeIsPaused()
    }

    fun getProgress(): DownloadProgress? {
        val result = nativeGetProgress() ?: return null
        return DownloadProgress(
            downloadedBytes = result[0],
            totalBytes = result[1],
            speedBps = result[2].toDouble()
        )
    }

    fun destroy() {
        nativeDestroy()
    }

    private external fun nativeInit()
    private external fun nativeDestroy()
    private external fun nativeSetProgressCallback(callback: ProgressCallback?)
    private external fun nativeStartDownload(url: String, outputPath: String, numConnections: Int): Boolean
    private external fun nativePauseDownload()
    private external fun nativeResumeDownload()
    private external fun nativeCancelDownload()
    private external fun nativeIsDownloading(): Boolean
    private external fun nativeIsPaused(): Boolean
    private external fun nativeGetProgress(): LongArray?
}

data class DownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBps: Double
) {
    val percentage: Float
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes.toFloat()) * 100f else 0f
}
