package com.lolokeksu.gallery

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.graphics.Bitmap
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GalleryMedia(
    val uri: Uri, val name: String, val album: String, val albumKey: String,
    val date: Long, val size: Long, val width: Int, val height: Int,
    val path: String, val mime: String, val video: Boolean, val duration: Long,
    val volume: String = ""
) {
    /**
     * Identity for this session: the row that MediaStore is serving right now.
     *
     * Computed once rather than on every read. Both of these were getters, and both build a
     * string, so every favorite check and every grid key allocated one — twice per tile per
     * frame while scrolling. Declared in the body, so neither joins equals, hashCode or copy.
     */
    val key: String = uri.toString()

    /**
     * Identity that outlives a MediaStore rescan. The row id in the content URI is reassigned when
     * the index is rebuilt, so anything stored against it silently points at nothing, or at a
     * different photograph. A volume, a relative path and a name are the file itself.
     */
    val stableKey: String = FavoriteMigration.stableKey(volume, path, name, key)
}

/**
 * Which media types the gallery shows at all.
 *
 * MediaStore indexes every image and video on the device, including things that are not photos:
 * icons, sprites and other assets that arrive with downloaded web content. Filtering happens in
 * the query rather than in the interface, so those files never enter the application.
 *
 * This is an allowlist, so an unlisted type disappears silently. Everything an Android camera,
 * a screenshot or a messenger produces is here; add a type rather than removing the filter if
 * something real turns out to be missing.
 */
object MediaTypes {
    val photos = listOf(
        "image/jpeg", "image/jpg", "image/png", "image/heic", "image/heif",
        "image/webp", "image/gif", "image/bmp", "image/x-ms-bmp",
        "image/dng", "image/x-adobe-dng"
    )

    val videos = listOf(
        "video/mp4", "video/3gpp", "video/3gpp2", "video/webm", "video/x-matroska",
        "video/quicktime", "video/mpeg", "video/mp2t", "video/x-msvideo"
    )

    fun of(video: Boolean) = if (video) videos else photos
}

enum class SortOrder(val label: String) { NEWEST("Сначала новые"), OLDEST("Сначала старые"), NAME("По названию") }

fun formatDuration(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    val hours = seconds / 3600
    return if (hours > 0) "%d:%02d:%02d".format(hours, seconds % 3600 / 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}

/**
 * Russian counts the noun after a number in three forms, and the interface said "файлов" for all
 * of them: "1 файлов", "3 файлов". Eleven to fourteen are the exception that the last digit alone
 * gets wrong, so the hundreds remainder decides first.
 */
fun fileCount(count: Int): String {
    val hundreds = count % 100
    val tens = count % 10
    val noun = when {
        hundreds in 11..14 -> "файлов"
        tens == 1 -> "файл"
        tens in 2..4 -> "файла"
        else -> "файлов"
    }
    return "$count $noun"
}

class MediaRepository(private val context: Context) {
    fun allowed(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    private fun fullPhotos() = allowed(Manifest.permission.READ_MEDIA_IMAGES)
    private fun fullVideos() = allowed(Manifest.permission.READ_MEDIA_VIDEO)

    /**
     * Android 14 also grants READ_MEDIA_VISUAL_USER_SELECTED when the user allows full access,
     * so limited access is only reported when full access is actually missing.
     */
    fun partial() = Build.VERSION.SDK_INT >= 34 &&
        allowed(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) && !(fullPhotos() && fullVideos())
    fun photos() = fullPhotos() || partial()
    fun videos() = fullVideos() || partial()

    /**
     * MediaStore keeps its own generated thumbnails. Reading one costs a fraction of decoding a
     * video frame out of the original file, which is what Coil's VideoFrameDecoder does on every
     * load. Returns null when MediaStore has nothing to offer, so the caller can fall back.
     */
    suspend fun thumbnail(uri: android.net.Uri, pixels: Int): Bitmap? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.loadThumbnail(uri, Size(pixels, pixels), null)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun read(): List<GalleryMedia> = withContext(Dispatchers.IO) {
        val result = mutableListOf<GalleryMedia>()
        if (photos()) result += query(false)
        if (videos()) result += query(true)
        result.sortedByDescending { it.date }
    }

    /**
     * What Android is holding in its trash. Deleting from the gallery uses createTrashRequest, so
     * a file stays recoverable for thirty days, and this is how it gets listed again.
     *
     * Android may hide trashed items belonging to another application, in which case this comes
     * back empty even right after a deletion. The screen says so rather than claiming the trash
     * is empty.
     */
    suspend fun trashed(): List<GalleryMedia> = withContext(Dispatchers.IO) {
        val result = mutableListOf<GalleryMedia>()
        if (photos()) result += query(false, trashed = true)
        if (videos()) result += query(true, trashed = true)
        result.sortedByDescending { it.date }
    }

    private fun query(video: Boolean, trashed: Boolean = false): List<GalleryMedia> {
        val collection = if (video) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = mutableListOf("_id", "_display_name", "bucket_display_name", "bucket_id",
            "datetaken", "date_added", "_size", "width", "height", "relative_path", "mime_type", "volume_name")
        if (video) projection += "duration"
        val result = mutableListOf<GalleryMedia>()
        // Bound arguments rather than an interpolated list: MediaStore rejects selections it
        // cannot parse, and the types never reach the SQL text.
        val types = MediaTypes.of(video)
        // is_trashed stays out of the selection: MediaStore filters trashed rows itself through
        // QUERY_ARG_MATCH_TRASHED, and a selection on that column does not decide it.
        val selection = "is_pending = 0 AND mime_type IN (" + types.joinToString(",") { "?" } + ")"
        val args = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, types.toTypedArray())
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "date_added DESC")
            putInt(
                MediaStore.QUERY_ARG_MATCH_TRASHED,
                if (trashed) MediaStore.MATCH_ONLY else MediaStore.MATCH_EXCLUDE
            )
        }
        context.contentResolver.query(collection, projection.toTypedArray(), args, null)?.use { c ->
            // Resolved once. getColumnIndexOrThrow walks the column names comparing strings, and
            // these helpers were calling it for every column of every row: twelve scans per file,
            // over the whole device.
            val index = projection.associateWith { c.getColumnIndexOrThrow(it) }
            fun s(name: String) = c.getString(index.getValue(name)) ?: ""
            fun n(name: String) = c.getLong(index.getValue(name))
            while (c.moveToNext()) {
                val volume = s("volume_name").ifBlank { MediaStore.VOLUME_EXTERNAL_PRIMARY }
                val base = if (video) MediaStore.Video.Media.getContentUri(volume) else MediaStore.Images.Media.getContentUri(volume)
                result += GalleryMedia(ContentUris.withAppendedId(base, n("_id")), s("_display_name"),
                    s("bucket_display_name").ifBlank { "Без альбома" }, "$volume:${s("bucket_id")}",
                    n("datetaken").takeIf { it > 0 } ?: n("date_added") * 1000,
                    n("_size"), n("width").toInt(), n("height").toInt(), s("relative_path"),
                    s("mime_type").ifBlank { if (video) "video/*" else "image/*" }, video,
                    if (video) n("duration") else 0, volume)
            }
        }
        return result
    }
}
