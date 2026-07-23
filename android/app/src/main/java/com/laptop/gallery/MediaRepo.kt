package com.laptop.gallery

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import java.io.ByteArrayOutputStream

/** Photo metadata sent to clients. */
data class Photo(val id: Long, val date: Long, val w: Int, val h: Int)

/**
 * Reads images from MediaStore, newest first. Paginated so we never load the
 * whole gallery at once. Thumbnails and full images are streamed on demand.
 */
class MediaRepo(private val context: Context) {

    private val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    private val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.WIDTH,
        MediaStore.Images.Media.HEIGHT
    )

    /** Total number of images — for the client's scroll sizing. */
    fun count(): Int {
        context.contentResolver.query(collection, arrayOf(MediaStore.Images.Media._ID),
            null, null, null)?.use { return it.count }
        return 0
    }

    /** One page of photos, sorted by capture date (newest first). */
    fun page(offset: Int, limit: Int): List<Photo> {
        val out = ArrayList<Photo>(limit)
        // DATE_TAKEN is ms; fall back to DATE_ADDED (seconds) when it's null/0.
        val sort = "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media._ID} DESC"

        // API 30+ validates sortOrder and rejects a "LIMIT" suffix, so use the
        // query Bundle there. API 29 ignores the Bundle limit args, so use the
        // classic SQL suffix instead.
        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val args = android.os.Bundle().apply {
                putStringArray(android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS,
                    arrayOf(MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media._ID))
                putInt(android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION,
                    android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, limit)
                putInt(android.content.ContentResolver.QUERY_ARG_OFFSET, offset)
            }
            context.contentResolver.query(collection, projection, args, null)
        } else {
            context.contentResolver.query(collection, projection, null, null,
                "$sort LIMIT $limit OFFSET $offset")
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
                out.add(Photo(c.getLong(idCol), date, c.getInt(wCol), c.getInt(hCol)))
            }
        }
        return out
    }

    /** JPEG thumbnail bytes for one image (256px), or null if unavailable. */
    fun thumbnail(id: Long): ByteArray? {
        val uri = ContentUris.withAppendedId(collection, id)
        return try {
            val bmp: Bitmap = context.contentResolver.loadThumbnail(uri, Size(256, 256), null)
            ByteArrayOutputStream().use { bos ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 80, bos)
                bmp.recycle()
                bos.toByteArray()
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Full-resolution original bytes for one image, or null if unavailable. */
    fun original(id: Long): ByteArray? {
        val uri = ContentUris.withAppendedId(collection, id)
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }
}
