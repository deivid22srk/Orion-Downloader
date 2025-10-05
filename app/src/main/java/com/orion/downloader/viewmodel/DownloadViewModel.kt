package com.orion.downloader.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.orion.downloader.model.DownloadItem
import com.orion.downloader.model.DownloadStatus
import com.orion.downloader.service.DownloadService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    private val _numConnections = MutableStateFlow(8)
    val numConnections: StateFlow<Int> = _numConnections.asStateFlow()

    private val _downloadPath = MutableStateFlow(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
    )
    val downloadPath: StateFlow<String> = _downloadPath.asStateFlow()

    private var downloadService: DownloadService? = null
    private var isBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as DownloadService.DownloadBinder
            downloadService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            downloadService = null
            isBound = false
        }
    }

    fun bindService() {
        val intent = Intent(getApplication(), DownloadService::class.java)
        getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun unbindService() {
        if (isBound) {
            getApplication<Application>().unbindService(serviceConnection)
            isBound = false
        }
    }

    fun addDownload(url: String, filename: String) {
        viewModelScope.launch {
            val outputPath = File(_downloadPath.value, filename).absolutePath
            val item = DownloadItem(
                url = url,
                filename = filename,
                outputPath = outputPath,
                numConnections = _numConnections.value
            )
            
            _downloads.update { it + item }
        }
    }

    fun startDownload(itemId: String) {
        viewModelScope.launch {
            val item = _downloads.value.find { it.id == itemId } ?: return@launch
            
            if (item.status == DownloadStatus.DOWNLOADING) return@launch

            updateDownloadStatus(itemId, DownloadStatus.DOWNLOADING)
            
            val serviceIntent = Intent(getApplication(), DownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getApplication<Application>().startForegroundService(serviceIntent)
            } else {
                getApplication<Application>().startService(serviceIntent)
            }
            
            downloadService?.startDownload(
                downloadId = itemId,
                url = item.url,
                filename = item.filename,
                numConnections = item.numConnections,
                onProgress = { downloadedBytes, totalBytes, speedBps ->
                    updateDownloadProgress(itemId, downloadedBytes, totalBytes, speedBps)
                },
                onComplete = { success ->
                    if (success) {
                        updateDownloadStatus(itemId, DownloadStatus.COMPLETED)
                    } else {
                        val currentStatus = _downloads.value.find { it.id == itemId }?.status
                        if (currentStatus != DownloadStatus.CANCELLED) {
                            updateDownloadStatus(itemId, DownloadStatus.FAILED)
                        }
                    }
                }
            )
        }
    }

    fun pauseDownload(itemId: String) {
        viewModelScope.launch {
            downloadService?.pauseDownload(itemId)
            updateDownloadStatus(itemId, DownloadStatus.PAUSED)
        }
    }

    fun resumeDownload(itemId: String) {
        viewModelScope.launch {
            downloadService?.resumeDownload(itemId)
            updateDownloadStatus(itemId, DownloadStatus.DOWNLOADING)
        }
    }

    fun cancelDownload(itemId: String) {
        viewModelScope.launch {
            downloadService?.cancelDownload(itemId)
            updateDownloadStatus(itemId, DownloadStatus.CANCELLED)
        }
    }

    fun deleteDownload(itemId: String) {
        viewModelScope.launch {
            cancelDownload(itemId)
            _downloads.update { it.filter { item -> item.id != itemId } }
        }
    }

    fun setNumConnections(num: Int) {
        _numConnections.value = num.coerceIn(1, 16)
    }

    fun setDownloadPath(path: String) {
        _downloadPath.value = path
    }

    private fun updateDownloadProgress(
        itemId: String,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBps: Double
    ) {
        _downloads.update { downloads ->
            downloads.map { item ->
                if (item.id == itemId) {
                    item.copy(
                        downloadedBytes = downloadedBytes,
                        totalBytes = totalBytes,
                        speedBps = speedBps
                    )
                } else {
                    item
                }
            }
        }
    }

    private fun updateDownloadStatus(itemId: String, status: DownloadStatus) {
        _downloads.update { downloads ->
            downloads.map { item ->
                if (item.id == itemId) {
                    item.copy(status = status)
                } else {
                    item
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        unbindService()
    }
}
