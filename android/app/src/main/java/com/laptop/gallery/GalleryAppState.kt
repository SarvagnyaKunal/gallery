package com.laptop.gallery

import android.content.Context

object GalleryAppState {
    @Volatile
    private var controller: ServerController? = null

    fun controller(context: Context): ServerController {
        return controller ?: synchronized(this) {
            controller ?: ServerController(context.applicationContext).also { controller = it }
        }
    }
}
