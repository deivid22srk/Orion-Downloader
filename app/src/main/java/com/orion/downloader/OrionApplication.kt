package com.orion.downloader

import android.app.Application
import com.orion.downloader.util.NotificationHelper

class OrionApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
    }
}
