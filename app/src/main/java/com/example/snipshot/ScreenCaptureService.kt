package com.example.snipshot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

class ScreenCaptureService : Service() {

    override fun onCreate() {
        super.onCreate()
        startForeground()
    }

    private fun startForeground() {
        val channelId = "ScreenCaptureChannel"

        // Create notification channel (required for Android 8.0+)
        val channel = NotificationChannel(
            channelId,
            "Screen Capture",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Screen capture in progress"
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)

        // Build notification
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Screen Capture")
            .setContentText("Recording screen...")
            .setSmallIcon(R.drawable.ic_notification)
            .build()

        // Start foreground service with appropriate type
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(1, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}