package com.orion.downloader.core

class DownloadEngine {
    
    init {
        try {
            System.loadLibrary("orion_downloader")
        } catch (e: UnsatisfiedLinkError) {
            e.printStackTrace()
        }
    }

    external fun nativeGetVersion(): String
}
