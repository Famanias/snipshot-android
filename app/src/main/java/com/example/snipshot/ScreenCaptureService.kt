package com.example.snipshot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.os.Build

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): ScreenCaptureService = this@ScreenCaptureService
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "ScreenCaptureChannel"
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceWithNotification()
        Log.d("ScreenCaptureService", "Service created")
    }

    fun setMediaProjection(mediaProjection: MediaProjection) {
        this.mediaProjection = mediaProjection
        Log.d("ScreenCaptureService", "MediaProjection set")
    }

    private fun startForegroundServiceWithNotification() {
        createNotificationChannel()

        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Screen Capture Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Capture")
            .setContentText("Capturing screen content")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(true)
        Log.d("ScreenCaptureService", "Service stopped")
    }

}