package com.laptop.gallery

import android.content.Context

class FcmTokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("gallery_fcm", Context.MODE_PRIVATE)

    fun get(): String? = prefs.getString("token", null)

    fun set(token: String) {
        prefs.edit().putString("token", token).apply()
    }
}
