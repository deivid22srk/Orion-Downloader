package com.orion.downloader.viewmodel

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orion.downloader.core.DownloadEngine
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

    private val downloadEngines = mutableMapOf<String, DownloadEngine>()

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

            val engine = DownloadEngine()
            downloadEngines[itemId] = engine

            engine.setProgressCallback { downloadedBytes, totalBytes, speedBps, _ ->
                updateDownloadProgress(itemId, downloadedBytes, totalBytes, speedBps)
            }

            updateDownloadStatus(itemId, DownloadStatus.DOWNLOADING)

            val success = engine.startDownload(item.url, item.outputPath, item.numConnections)
            
            if (!success) {
                updateDownloadStatus(itemId, DownloadStatus.FAILED)
                downloadEngines.remove(itemId)?.destroy()
            } else {
                checkDownloadCompletion(itemId)
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
            downloadEngines.remove(itemId)?.destroy()
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

    private fun checkDownloadCompletion(itemId: String) {
        viewModelScope.launch {
            kotlinx.coroutines.delay(1000)
            
            val engine = downloadEngines[itemId]
            if (engine != null && !engine.isDownloading()) {
                val item = _downloads.value.find { it.id == itemId }
                if (item != null && item.downloadedBytes >= item.totalBytes && item.totalBytes > 0) {
                    updateDownloadStatus(itemId, DownloadStatus.COMPLETED)
                }
                downloadEngines.remove(itemId)?.destroy()
            } else if (engine != null && engine.isDownloading()) {
                checkDownloadCompletion(itemId)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        downloadEngines.values.forEach { it.destroy() }
        downloadEngines.clear()
    }
}
