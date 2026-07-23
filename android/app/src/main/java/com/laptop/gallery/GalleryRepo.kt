package com.laptop.gallery

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Photo or video metadata sent to clients. */
data class Media(val id: Long, val date: Long, val w: Int, val h: Int, val isVideo: Boolean = false)

/** Stream container for full-resolution media. */
data class MediaStream(val stream: InputStream, val size: Long, val mimeType: String)

/**
 * Reads images and videos from MediaStore, newest first. Paginated so we never
 * load the whole gallery at once. Thumbnails and full media are streamed on demand.
 * Videos are served as-is (original container); thumbnails are extracted JPEG frames.
 */
class GalleryRepo(private val context: Context) {

    private val imageCollection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    private val videoCollection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

    private val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.WIDTH,
        MediaStore.Images.Media.HEIGHT
    )

    /** Total number of images + videos — for the client's scroll sizing. */
    fun count(): Int {
        var c = 0
        context.contentResolver.query(imageCollection, arrayOf(MediaStore.Images.Media._ID),
            null, null, null)?.use { c += it.count }
        context.contentResolver.query(videoCollection, arrayOf(MediaStore.Video.Media._ID),
            null, null, null)?.use { c += it.count }
        return c
    }

    /** One page of media (images + videos), sorted by capture date (newest first). */
    fun page(offset: Int, limit: Int): List<Media> {
        val all = mutableListOf<Media>()

        // Fetch images
        queryCollection(imageCollection, isVideo = false)?.let { all.addAll(it) }
        // Fetch videos
        queryCollection(videoCollection, isVideo = true)?.let { all.addAll(it) }

        // Sort all by date descending, then take the page.
        all.sortByDescending { it.date }
        return all.drop(offset).take(limit)
    }

    private fun queryCollection(collection: android.net.Uri, isVideo: Boolean): List<Media>? {
        val out = ArrayList<Media>(300)
        val sort = "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media._ID} DESC"

        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val args = android.os.Bundle().apply {
                putStringArray(android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS,
                    arrayOf(MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media._ID))
                putInt(android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION,
                    android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, 300)
                putInt(android.content.ContentResolver.QUERY_ARG_OFFSET, 0)
            }
            context.contentResolver.query(collection, projection, args, null)
        } else {
            context.contentResolver.query(collection, projection, null, null,
                "$sort LIMIT 300 OFFSET 0")
        }

        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val takenCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val wCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val hCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            while (c.moveToNext()) {
                val taken = c.getLong(takenCol)
                val date = if (taken > 0) taken else c.getLong(addedCol) * 1000L
                out.add(Media(c.getLong(idCol), date, c.getInt(wCol), c.getInt(hCol), isVideo))
            }
        }
        return out
    }

    /** JPEG thumbnail bytes for one image/video (256px), or null if unavailable. */
    fun thumbnail(id: Long, isVideo: Boolean): ByteArray? {
        val collection = if (isVideo) videoCollection else imageCollection
        val uri = ContentUris.withAppendedId(collection, id)
        return try {
            val bmp: Bitmap = context.contentResolver.loadThumbnail(uri, Size(256, 256), null)
            ByteArrayOutputStream().use { bos ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 80, bos)
                bmp.recycle()
                bos.toByteArray()
            }
        } catch (e: Throwable) {
            null
        }
    }

    /** Full-resolution original stream for one image/video, or null if unavailable. */
    fun openOriginal(id: Long, isVideo: Boolean): MediaStream? {
        val collection = if (isVideo) videoCollection else imageCollection
        val uri = ContentUris.withAppendedId(collection, id)
        val mimeType = context.contentResolver.getType(uri) ?: if (isVideo) "video/mp4" else "image/jpeg"
        return try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                val size = pfd.statSize
                val stream = java.io.FileInputStream(pfd.fileDescriptor)
                MediaStream(stream, size, mimeType)
            } else {
                val stream = context.contentResolver.openInputStream(uri) ?: return null
                MediaStream(stream, -1L, mimeType)
            }
        } catch (e: Throwable) {
            try {
                val stream = context.contentResolver.openInputStream(uri) ?: return null
                MediaStream(stream, -1L, mimeType)
            } catch (ex: Throwable) {
                null
            }
        }
    }
}
