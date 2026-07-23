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
        Log.d("FCM", "New token: $token")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d("FCM", "From: ${message.from}")

        // Log data payload
        if (message.data.isNotEmpty()) {
            Log.d("FCM", "Data payload: ${message.data}")
            
            val action = message.data["action"]
            Log.d("FCM", "Action: $action")
            if (action == "wake") {
                showLaptopAccessNotification()
                GalleryForegroundService.start(applicationContext)
            }
        }

        // Log notification payload (optional)
        message.notification?.let {
            Log.d("FCM", "Notification Body: ${it.body}")
        }
    }

    private fun showLaptopAccessNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    LAPTOP_ACCESS_CHANNEL_ID,
                    "Laptop access",
                    NotificationManager.IMPORTANCE_DEFAULT
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
            .setContentTitle("Laptop access")
            .setContentText("Your laptop is requesting gallery access.")
            .setContentIntent(openAppIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(LAPTOP_ACCESS_NOTIFICATION_ID, notification)
    }
}
