package com.laptop.gallery

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Photo or video metadata sent to clients. */
data class Media(val id: Long, val date: Long, val w: Int, val h: Int, val isVideo: Boolean = false)

/** Stream container for full-resolution media. */
data class MediaStream(val stream: InputStream, val size: Long, val mimeType: String)

/**
 * Reads images and videos across all storage locations (Camera, Screenshots, Downloads,
 * WhatsApp, SD Card, etc.) using MediaStore.Files. Strictly sorted chronologically by
 * Date / Month / Year (newest first).
 */
class GalleryRepo(private val context: Context) {

    private val filesCollection = MediaStore.Files.getContentUri("external")
    private val imageCollection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    private val videoCollection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

    private val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.DATE_TAKEN,
        MediaStore.Files.FileColumns.DATE_MODIFIED,
        MediaStore.Files.FileColumns.DATE_ADDED,
        MediaStore.Files.FileColumns.WIDTH,
        MediaStore.Files.FileColumns.HEIGHT,
        MediaStore.Files.FileColumns.MEDIA_TYPE
    )

    private val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
    private val selectionArgs = arrayOf(
        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
    )

    // Compute effective timestamp in ms so sorting places latest photos (e.g. 2026) first
    private val sortOrderSql = "(CASE WHEN ${MediaStore.Files.FileColumns.DATE_TAKEN} IS NOT NULL AND ${MediaStore.Files.FileColumns.DATE_TAKEN} > 0 THEN ${MediaStore.Files.FileColumns.DATE_TAKEN} WHEN ${MediaStore.Files.FileColumns.DATE_MODIFIED} IS NOT NULL AND ${MediaStore.Files.FileColumns.DATE_MODIFIED} > 0 THEN ${MediaStore.Files.FileColumns.DATE_MODIFIED} * 1000 ELSE ${MediaStore.Files.FileColumns.DATE_ADDED} * 1000 END) DESC, ${MediaStore.Files.FileColumns._ID} DESC"

    /** Total number of images + videos across all indexed storage folders. */
    fun count(): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val args = Bundle().apply {
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                }
                context.contentResolver.query(filesCollection, arrayOf(MediaStore.Files.FileColumns._ID), args, null)
            } else {
                context.contentResolver.query(filesCollection, arrayOf(MediaStore.Files.FileColumns._ID), selection, selectionArgs, null)
            }?.use { it.count } ?: 0
        } catch (e: Throwable) {
            0
        }
    }

    /** One page of media (images + videos), sorted chronologically by date/month/year (newest first). */
    fun page(offset: Int, limit: Int): List<Media> {
        val out = ArrayList<Media>(limit)

        try {
            val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val args = Bundle().apply {
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                    putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrderSql)
                    putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                    putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
                }
                context.contentResolver.query(filesCollection, projection, args, null)
            } else {
                context.contentResolver.query(
                    filesCollection,
                    projection,
                    selection,
                    selectionArgs,
                    "$sortOrderSql LIMIT $limit OFFSET $offset"
                )
            }

            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val takenCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_TAKEN)
                val modifiedCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val addedCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                val wCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)
                val hCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)
                val typeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)

                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val taken = c.getLong(takenCol)
                    val modified = c.getLong(modifiedCol)
                    val added = c.getLong(addedCol)
                    val date = resolveTimestamp(taken, modified, added)
                    val w = c.getInt(wCol)
                    val h = c.getInt(hCol)
                    val mediaType = c.getInt(typeCol)
                    val isVideo = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO

                    out.add(Media(id, date, w, h, isVideo))
                }
            }
        } catch (e: Throwable) {
            // Surface empty list gracefully on query error
        }

        return out
    }

    /**
     * Resolves the actual capture or file creation timestamp in milliseconds.
     * Safely normalizes values given in seconds (e.g. DATE_MODIFIED/DATE_ADDED) vs milliseconds (DATE_TAKEN).
     * Prefers DATE_TAKEN (EXIF), then DATE_MODIFIED (original file timestamp), then DATE_ADDED.
     */
    private fun resolveTimestamp(taken: Long, modified: Long, added: Long): Long {
        fun toMs(v: Long): Long {
            if (v <= 0L) return 0L
            return if (v < 10_000_000_000L) v * 1000L else v
        }
        val t = toMs(taken)
        val m = toMs(modified)
        val a = toMs(added)

        return when {
            t > 0L -> t
            m > 0L -> m
            a > 0L -> a
            else -> System.currentTimeMillis()
        }
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
