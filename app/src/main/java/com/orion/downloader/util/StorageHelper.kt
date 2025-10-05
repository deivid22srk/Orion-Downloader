package com.orion.downloader.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object StorageHelper {
    
    data class DownloadFileInfo(
        val uri: Uri?,
        val outputStream: OutputStream?,
        val tempFile: File?,
        val usesMediaStore: Boolean
    )
    
    fun createDownloadFile(
        context: Context,
        filename: String,
        mimeType: String = "application/octet-stream"
    ): DownloadFileInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            createFileWithMediaStore(context, filename, mimeType)
        } else {
            createFileWithLegacyStorage(filename)
        }
    }
    
    private fun createFileWithMediaStore(
        context: Context,
        filename: String,
        mimeType: String
    ): DownloadFileInfo {
        val resolver = context.contentResolver
        
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        
        if (uri != null) {
            val tempFile = File(context.cacheDir, "temp_$filename")
            val outputStream = FileOutputStream(tempFile)
            
            return DownloadFileInfo(
                uri = uri,
                outputStream = outputStream,
                tempFile = tempFile,
                usesMediaStore = true
            )
        }
        
        return DownloadFileInfo(null, null, null, true)
    }
    
    @Suppress("DEPRECATION")
    private fun createFileWithLegacyStorage(filename: String): DownloadFileInfo {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        downloadsDir.mkdirs()
        
        val file = File(downloadsDir, filename)
        val outputStream = FileOutputStream(file)
        
        return DownloadFileInfo(
            uri = Uri.fromFile(file),
            outputStream = outputStream,
            tempFile = file,
            usesMediaStore = false
        )
    }
    
    fun finishDownload(context: Context, fileInfo: DownloadFileInfo) {
        if (fileInfo.usesMediaStore && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            fileInfo.outputStream?.close()
            
            if (fileInfo.uri != null && fileInfo.tempFile != null) {
                context.contentResolver.openOutputStream(fileInfo.uri)?.use { output ->
                    fileInfo.tempFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                
                fileInfo.tempFile.delete()
                
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                context.contentResolver.update(fileInfo.uri, contentValues, null, null)
            }
        } else {
            fileInfo.outputStream?.close()
        }
    }
    
    fun cancelDownload(context: Context, fileInfo: DownloadFileInfo) {
        fileInfo.outputStream?.close()
        
        if (fileInfo.usesMediaStore && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            fileInfo.tempFile?.delete()
            fileInfo.uri?.let { uri ->
                context.contentResolver.delete(uri, null, null)
            }
        } else {
            fileInfo.tempFile?.delete()
        }
    }
    
    fun getMimeType(filename: String): String {
        val extension = filename.substringAfterLast('.', "")
        return when (extension.lowercase()) {
            "apk" -> "application/vnd.android.package-archive"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "7z" -> "application/x-7z-compressed"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "txt" -> "text/plain"
            "html", "htm" -> "text/html"
            "json" -> "application/json"
            "xml" -> "application/xml"
            else -> "application/octet-stream"
        }
    }
}
