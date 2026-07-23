package com.laptop.gallery

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val LAPTOP_ACCESS_CHANNEL_ID = "laptop_access"
        private const val LAPTOP_ACCESS_NOTIFICATION_ID = 43
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FcmTokenStore(applicationContext).set(token)
        Log.d("FCM", "New registration token: $token")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d("FCM", "Wake push received from: ${message.from}")

        showLaptopAccessNotification()
        try {
            GalleryForegroundService.start(applicationContext)
        } catch (e: Throwable) {
            Log.e("FCM", "Error restarting server from FCM wake", e)
        }
    }

    private fun showLaptopAccessNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    LAPTOP_ACCESS_CHANNEL_ID,
                    "Laptop access",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, LAPTOP_ACCESS_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Laptop Gallery")
            .setContentText("Laptop requested gallery access. Server restarted.")
            .setContentIntent(openAppIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(LAPTOP_ACCESS_NOTIFICATION_ID, notification)
    }
}
