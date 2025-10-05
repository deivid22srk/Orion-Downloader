package com.orion.downloader.viewmodel

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orion.downloader.core.HttpDownloadEngine
import com.orion.downloader.model.DownloadItem
import com.orion.downloader.model.DownloadStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class DownloadViewModel : ViewModel() {

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    private val _numConnections = MutableStateFlow(8)
    val numConnections: StateFlow<Int> = _numConnections.asStateFlow()

    private val _downloadPath = MutableStateFlow(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
    )
    val downloadPath: StateFlow<String> = _downloadPath.asStateFlow()

    private val downloadEngines = mutableMapOf<String, HttpDownloadEngine>()

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

            val engine = HttpDownloadEngine()
            downloadEngines[itemId] = engine

            updateDownloadStatus(itemId, DownloadStatus.DOWNLOADING)

            val success = engine.startDownload(
                url = item.url,
                outputPath = item.outputPath,
                numConnections = item.numConnections,
                progressCallback = object : HttpDownloadEngine.ProgressCallback {
                    override fun onProgress(progress: HttpDownloadEngine.DownloadProgress) {
                        updateDownloadProgress(
                            itemId,
                            progress.downloadedBytes,
                            progress.totalBytes,
                            progress.speedBps
                        )
                    }
                }
            )
            
            if (!success) {
                updateDownloadStatus(itemId, DownloadStatus.FAILED)
                downloadEngines.remove(itemId)
            } else {
                val finalItem = _downloads.value.find { it.id == itemId }
                if (finalItem != null && finalItem.downloadedBytes >= finalItem.totalBytes && finalItem.totalBytes > 0) {
                    updateDownloadStatus(itemId, DownloadStatus.COMPLETED)
                } else if (finalItem?.status == DownloadStatus.DOWNLOADING) {
                    updateDownloadStatus(itemId, DownloadStatus.FAILED)
                }
                downloadEngines.remove(itemId)
            }
        }
    }

    fun pauseDownload(itemId: String) {
        viewModelScope.launch {
            downloadEngines[itemId]?.pauseDownload()
            updateDownloadStatus(itemId, DownloadStatus.PAUSED)
        }
    }

    fun resumeDownload(itemId: String) {
        viewModelScope.launch {
            downloadEngines[itemId]?.resumeDownload()
            updateDownloadStatus(itemId, DownloadStatus.DOWNLOADING)
        }
    }

    fun cancelDownload(itemId: String) {
        viewModelScope.launch {
            downloadEngines[itemId]?.cancelDownload()
            updateDownloadStatus(itemId, DownloadStatus.CANCELLED)
            downloadEngines.remove(itemId)
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
        downloadEngines.values.forEach { it.cancelDownload() }
        downloadEngines.clear()
    }
}
