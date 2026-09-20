package com.lolokeksu.gallery

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GalleryMedia(
    val uri: Uri, val name: String, val album: String, val albumKey: String,
    val date: Long, val size: Long, val width: Int, val height: Int,
    val path: String, val mime: String, val video: Boolean, val duration: Long
) { val key: String get() = uri.toString() }

enum class SortOrder(val label: String) { NEWEST("Сначала новые"), OLDEST("Сначала старые"), NAME("По названию") }

fun formatDuration(ms: Long): String = "%d:%02d".format(ms / 60000, ms / 1000 % 60)

class MediaRepository(private val context: Context) {
    fun allowed(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    fun partial() = Build.VERSION.SDK_INT >= 34 && allowed(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    fun photos() = allowed(Manifest.permission.READ_MEDIA_IMAGES) || partial()
    fun videos() = allowed(Manifest.permission.READ_MEDIA_VIDEO) || partial()

    suspend fun read(includeVideos: Boolean): List<GalleryMedia> = withContext(Dispatchers.IO) {
        val result = mutableListOf<GalleryMedia>()
        if (photos()) result += query(false)
        if (includeVideos && videos()) result += query(true)
        result.sortedByDescending { it.date }
    }

    private fun query(video: Boolean): List<GalleryMedia> {
        val collection = if (video) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = mutableListOf("_id", "_display_name", "bucket_display_name", "bucket_id",
            "datetaken", "date_added", "_size", "width", "height", "relative_path", "mime_type", "volume_name")
        if (video) projection += "duration"
        val result = mutableListOf<GalleryMedia>()
        context.contentResolver.query(collection, projection.toTypedArray(),
            "is_pending = 0 AND is_trashed = 0", null, "date_added DESC")?.use { c ->
            fun s(name: String) = c.getString(c.getColumnIndexOrThrow(name)) ?: ""
            fun n(name: String) = c.getLong(c.getColumnIndexOrThrow(name))
            while (c.moveToNext()) {
                val volume = s("volume_name").ifBlank { MediaStore.VOLUME_EXTERNAL_PRIMARY }
                val base = if (video) MediaStore.Video.Media.getContentUri(volume) else MediaStore.Images.Media.getContentUri(volume)
                result += GalleryMedia(ContentUris.withAppendedId(base, n("_id")), s("_display_name"),
                    s("bucket_display_name").ifBlank { "Без альбома" }, "$volume:${s("bucket_id")}",
                    n("datetaken").takeIf { it > 0 } ?: n("date_added") * 1000,
                    n("_size"), n("width").toInt(), n("height").toInt(), s("relative_path"),
                    s("mime_type").ifBlank { if (video) "video/*" else "image/*" }, video,
                    if (video) n("duration") else 0)
            }
        }
        return result
    }
}
