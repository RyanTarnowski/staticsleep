package com.example.staticsleep

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat

class MusicService : Service() {

    private val noiseGenerator = StaticNoiseGenerator()
    private val channelID = "MusicServiceChannel"
    private val notificationID = 1

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()

        val notification: Notification = NotificationCompat.Builder(this, channelID)
            .setContentTitle("Static Player")
            .setContentText("Playing background static...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // Safely start foreground depending on the Android API version
        startForeground(notificationID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)

        // Start playback if it's not already playing
        noiseGenerator.startStaticNoise()

        // System will recreate the service if it gets killed
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        noiseGenerator.stopStaticNoise()
    }

    override fun onBind(intent: Intent?): IBinder? {
        // Return null if you are not binding components to this service
        return null
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            channelID,
            "Background Music Service Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(serviceChannel)
    }
}
