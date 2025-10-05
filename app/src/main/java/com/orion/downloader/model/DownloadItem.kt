package com.orion.downloader.model

import java.util.UUID

data class DownloadItem(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val filename: String,
    val outputPath: String,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBps: Double = 0.0,
    val numConnections: Int = 8,
    val createdAt: Long = System.currentTimeMillis()
) {
    val percentage: Float
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes.toFloat()) * 100f else 0f

    val speedMbps: Double
        get() = speedBps / (1024.0 * 1024.0)

    val downloadedMB: Double
        get() = downloadedBytes / (1024.0 * 1024.0)

    val totalMB: Double
        get() = totalBytes / (1024.0 * 1024.0)
}

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}
