package com.officepilot.ai

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.officepilot.ai.util.C
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class OfficePilotApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(C.NOTIFICATION_CHANNEL, "Document Generation", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
    }
}
