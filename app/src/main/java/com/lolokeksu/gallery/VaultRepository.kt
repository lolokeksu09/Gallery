package com.lolokeksu.gallery

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.crypto.SecretKey

private val Context.vaultStore by preferencesDataStore("vault")

data class VaultItem(
    val id: String, val name: String, val mime: String, val date: Long, val size: Long,
    val width: Int, val height: Int, val video: Boolean, val duration: Long
)

/** Raised when the vault refuses to do something, with a message meant for the user. */
class VaultException(message: String) : Exception(message)

/**
 * Encrypted storage inside the application's private directory.
 *
 * Importing never destroys anything by itself: the encrypted copy is decrypted again and its
 * SHA-256 compared with the source before the caller is told it may ask Android to delete the
 * original. A failed verification removes the partial copy and reports the failure.
 */
class VaultRepository(private val context: Context) {
    private val saltKey = byteArrayPreferencesKey("salt")
    private val wrappedKey = byteArrayPreferencesKey("wrapped_key")
    private val failuresKey = intPreferencesKey("failures")
    private val dir = File(context.filesDir, "vault")
    private val cache = File(context.cacheDir, "vault-open")

    /** Ids this process decrypted completely. A cache file from an earlier run is never reused. */
    private val materialized = mutableSetOf<String>()

    private fun media(id: String) = File(dir, "$id.bin")
    private fun meta(id: String) = File(dir, "$id.meta")
    private fun thumb(id: String) = File(dir, "$id.thumb")

    suspend fun configured(): Boolean = read(saltKey) != null

    private suspend fun read(key: androidx.datastore.preferences.core.Preferences.Key<ByteArray>) =
        context.vaultStore.data.catch { emit(emptyPreferences()) }.first()[key]

    /** Creates the vault. Returns the data key that encrypts the files. */
    suspend fun create(password: CharArray): SecretKey = withContext(Dispatchers.Default) {
        if (configured()) throw VaultException("Хранилище уже создано")
        val salt = VaultCrypto.randomBytes(VaultCrypto.SALT_BYTES)
        val dataKey = VaultCrypto.newDataKey()
        val wrapped = VaultCrypto.seal(VaultCrypto.deriveKey(password, salt), dataKey.encoded)
        context.vaultStore.edit { it[saltKey] = salt; it[wrappedKey] = wrapped; it[failuresKey] = 0 }
        dir.mkdirs()
        dataKey
    }

    /** Returns the data key, or null when the password is wrong. */
    suspend fun unlock(password: CharArray): SecretKey? = withContext(Dispatchers.Default) {
        val salt = read(saltKey) ?: return@withContext null
        val wrapped = read(wrappedKey) ?: return@withContext null
        try {
            VaultCrypto.keyOf(VaultCrypto.open(VaultCrypto.deriveKey(password, salt), wrapped))
        } catch (_: Exception) {
            null
        }
    }

    /** Rewraps the same data key, so stored files are untouched. */
    suspend fun changePassword(old: CharArray, new: CharArray): Boolean = withContext(Dispatchers.Default) {
        val dataKey = unlock(old) ?: return@withContext false
        val salt = VaultCrypto.randomBytes(VaultCrypto.SALT_BYTES)
        val wrapped = VaultCrypto.seal(VaultCrypto.deriveKey(new, salt), dataKey.encoded)
        context.vaultStore.edit { it[saltKey] = salt; it[wrappedKey] = wrapped }
        true
    }

    class VaultListing(val items: List<VaultItem>, val unreadable: Int)

    suspend fun list(key: SecretKey): VaultListing = withContext(Dispatchers.IO) {
        var unreadable = 0
        val items = (dir.listFiles { file -> file.name.endsWith(".meta") } ?: emptyArray())
            .mapNotNull { file ->
                try {
                    decodeMeta(file.name.removeSuffix(".meta"), VaultCrypto.open(key, file.readBytes()))
                } catch (_: Exception) {
                    unreadable++
                    null
                }
            }
            .sortedByDescending { it.date }
        VaultListing(items, unreadable)
    }

    suspend fun thumbnail(key: SecretKey, id: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = thumb(id)
        if (!file.exists()) return@withContext null
        try {
            VaultCrypto.open(key, file.readBytes())
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Encrypts [source] into the vault and verifies the copy. The original is NOT touched here;
     * the caller asks Android to delete it once this returns.
     */
    suspend fun import(key: SecretKey, source: GalleryMedia): VaultItem = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val id = UUID.randomUUID().toString()
        val target = media(id)
        try {
            val written = context.contentResolver.openInputStream(source.uri)?.use { input ->
                target.outputStream().use { output -> VaultCrypto.encryptStream(key, input, output) }
            } ?: throw VaultException("Не удалось прочитать ${source.name}")

            // A source that ends early hashes consistently with the short copy it produced, so the
            // digest alone proves nothing. MediaStore's size is the only independent witness.
            if (source.size > 0 && written.bytes != source.size) {
                throw VaultException(
                    "Прочитано ${written.bytes} из ${source.size} байт ${source.name}, оригинал не тронут"
                )
            }
            if (written.bytes == 0L) {
                throw VaultException("Файл ${source.name} пуст или недоступен, оригинал не тронут")
            }

            val stored = target.inputStream().use { VaultCrypto.decryptStream(key, it, null) }
            if (stored.bytes != written.bytes || !stored.sha256.contentEquals(written.sha256)) {
                throw VaultException("Проверка копии ${source.name} не сошлась, оригинал не тронут")
            }

            val item = VaultItem(
                id = id, name = source.name, mime = source.mime, date = source.date,
                size = source.size, width = source.width, height = source.height,
                video = source.video, duration = source.duration
            )
            meta(id).writeBytes(VaultCrypto.seal(key, encodeMeta(item).toByteArray()))
            preview(source)?.let { thumb(id).writeBytes(VaultCrypto.seal(key, it)) }
            item
        } catch (e: Exception) {
            listOf(media(id), meta(id), thumb(id)).forEach { it.delete() }
            throw if (e is VaultException) e else VaultException("Не удалось скрыть ${source.name}: ${e.message}")
        }
    }

    /** Writes the file back into MediaStore and removes it from the vault once that succeeded. */
    suspend fun restore(key: SecretKey, item: VaultItem): Uri = withContext(Dispatchers.IO) {
        val collection = if (item.video) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.name)
            put(MediaStore.MediaColumns.MIME_TYPE, item.mime)
            put(MediaStore.MediaColumns.DATE_TAKEN, item.date)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                if (item.video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(collection, values)
            ?: throw VaultException("Не удалось создать файл ${item.name}")
        try {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                media(item.id).inputStream().use { VaultCrypto.decryptStream(key, it, output) }
            } ?: throw VaultException("Не удалось записать ${item.name}")
        } catch (e: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw if (e is VaultException) e else VaultException("Не удалось восстановить ${item.name}: ${e.message}")
        }
        context.contentResolver.update(
            uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null
        )
        forget(item.id)
        uri
    }

    /** Decrypts into the private cache so Coil and Media3 can read an ordinary file. */
    suspend fun materialize(key: SecretKey, item: VaultItem): File = withContext(Dispatchers.IO) {
        cache.mkdirs()
        val target = File(cache, item.id)
        if (synchronized(materialized) { item.id in materialized } && target.exists()) return@withContext target
        // Decrypt through a part file so a kill mid-write can never leave a truncated file
        // that later looks complete.
        val part = File(cache, "${item.id}.part")
        try {
            part.outputStream().use { output ->
                media(item.id).inputStream().use { VaultCrypto.decryptStream(key, it, output) }
            }
            target.delete()
            if (!part.renameTo(target)) throw VaultException("Не удалось подготовить ${item.name}")
        } catch (e: Exception) {
            part.delete()
            target.delete()
            throw if (e is VaultException) e else VaultException("Не удалось открыть ${item.name}: ${e.message}")
        }
        synchronized(materialized) { materialized += item.id }
        target
    }

    fun forget(id: String) {
        synchronized(materialized) { materialized -= id }
        listOf(media(id), meta(id), thumb(id), File(cache, id), File(cache, "$id.part")).forEach { it.delete() }
    }

    /** Removes every decrypted copy. Called when the vault locks and once at startup. */
    fun clearCache() {
        synchronized(materialized) { materialized.clear() }
        cache.listFiles()?.forEach { it.delete() }
    }

    private fun encodeMeta(item: VaultItem) = JSONObject().apply {
        put("name", item.name); put("mime", item.mime); put("date", item.date)
        put("size", item.size); put("width", item.width); put("height", item.height)
        put("video", item.video); put("duration", item.duration)
    }.toString()

    private fun decodeMeta(id: String, raw: ByteArray): VaultItem {
        val json = JSONObject(String(raw))
        return VaultItem(
            id = id,
            name = json.optString("name", "Без имени"),
            mime = json.optString("mime", "*/*"),
            date = json.optLong("date"),
            size = json.optLong("size"),
            width = json.optInt("width"),
            height = json.optInt("height"),
            video = json.optBoolean("video"),
            duration = json.optLong("duration")
        )
    }

    /** A small JPEG so the vault grid never has to decrypt whole photos or videos. */
    private fun preview(source: GalleryMedia): ByteArray? = try {
        val bitmap = if (source.video) videoFrame(source.uri) else downsampled(source.uri)
        bitmap?.let {
            ByteArrayOutputStream().use { out ->
                it.compress(Bitmap.CompressFormat.JPEG, 80, out)
                it.recycle()
                out.toByteArray()
            }
        }
    } catch (_: Exception) {
        null
    }

    private fun downsampled(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > PREVIEW_PIXELS) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    private fun videoFrame(uri: Uri): Bitmap? = MediaMetadataRetriever().use { retriever ->
        retriever.setDataSource(context, uri)
        retriever.getScaledFrameAtTime(
            0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, PREVIEW_PIXELS, PREVIEW_PIXELS
        )
    }

    private companion object {
        const val PREVIEW_PIXELS = 512
    }
}
