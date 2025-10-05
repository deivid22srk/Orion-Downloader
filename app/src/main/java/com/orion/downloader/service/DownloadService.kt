package com.orion.downloader.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.orion.downloader.core.HttpDownloadEngine
import com.orion.downloader.util.NotificationHelper
import kotlinx.coroutines.*

class DownloadService : Service() {
    
    private val TAG = "DownloadService"
    private val binder = DownloadBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val activeDownloads = mutableMapOf<String, HttpDownloadEngine>()
    private var activeDownloadCount = 0
    
    inner class DownloadBinder : Binder() {
        fun getService(): DownloadService = this@DownloadService
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "DownloadService created")
        NotificationHelper.createNotificationChannel(this)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "DownloadService started")
        
        val notification = NotificationHelper.createDownloadNotification(
            context = this,
            title = "Orion Downloader",
            message = "Ready to download",
            isIndeterminate = true
        )
        
        startForeground(NotificationHelper.NOTIFICATION_ID, notification)
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    fun startDownload(
        downloadId: String,
        url: String,
        filename: String,
        numConnections: Int,
        onProgress: (Long, Long, Double) -> Unit,
        onComplete: (Boolean) -> Unit
    ) {
        if (activeDownloads.containsKey(downloadId)) {
            Log.w(TAG, "Download already active: $downloadId")
            return
        }
        
        val engine = HttpDownloadEngine(this)
        activeDownloads[downloadId] = engine
        activeDownloadCount++
        
        serviceScope.launch {
            try {
                val success = engine.startDownload(
                    url = url,
                    filename = filename,
                    numConnections = numConnections,
                    progressCallback = object : HttpDownloadEngine.ProgressCallback {
                        override fun onProgress(progress: HttpDownloadEngine.DownloadProgress) {
                            onProgress(
                                progress.downloadedBytes,
                                progress.totalBytes,
                                progress.speedBps
                            )
                            
                            val notification = NotificationHelper.createDownloadNotification(
                                context = this@DownloadService,
                                title = "Downloading $filename",
                                message = "${String.format("%.1f", progress.percentage)}% - ${formatSpeed(progress.speedBps)}",
                                progress = progress.percentage.toInt(),
                                maxProgress = 100,
                                isIndeterminate = false
                            )
                            
                            NotificationHelper.updateNotification(
                                this@DownloadService,
                                NotificationHelper.NOTIFICATION_ID,
                                notification
                            )
                        }
                    }
                )
                
                activeDownloads.remove(downloadId)
                activeDownloadCount--
                
                if (success) {
                    val completeNotification = NotificationHelper.createDownloadCompleteNotification(
                        context = this@DownloadService,
                        title = "Download Complete",
                        filename = filename
                    )
                    
                    NotificationHelper.updateNotification(
                        this@DownloadService,
                        NotificationHelper.NOTIFICATION_ID,
                        completeNotification
                    )
                    
                    delay(3000)
                }
                
                onComplete(success)
                
                if (activeDownloadCount == 0) {
                    stopForegroundAndService()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Download error", e)
                activeDownloads.remove(downloadId)
                activeDownloadCount--
                onComplete(false)
                
                if (activeDownloadCount == 0) {
                    stopForegroundAndService()
                }
            }
        }
    }
    
    fun pauseDownload(downloadId: String) {
        activeDownloads[downloadId]?.pauseDownload()
    }
    
    fun resumeDownload(downloadId: String) {
        activeDownloads[downloadId]?.resumeDownload()
    }
    
    fun cancelDownload(downloadId: String) {
        activeDownloads[downloadId]?.cancelDownload()
        activeDownloads.remove(downloadId)
        activeDownloadCount--
        
        if (activeDownloadCount == 0) {
            stopForegroundAndService()
        }
    }
    
    private fun stopForegroundAndService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }
    
    private fun formatSpeed(bytesPerSecond: Double): String {
        return when {
            bytesPerSecond >= 1024 * 1024 -> String.format("%.2f MB/s", bytesPerSecond / (1024 * 1024))
            bytesPerSecond >= 1024 -> String.format("%.2f KB/s", bytesPerSecond / 1024)
            else -> String.format("%.0f B/s", bytesPerSecond)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "DownloadService destroyed")
        activeDownloads.values.forEach { it.cancelDownload() }
        activeDownloads.clear()
        serviceScope.cancel()
    }
}
