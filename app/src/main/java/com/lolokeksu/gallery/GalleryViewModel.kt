package com.lolokeksu.gallery

import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

private val Context.galleryStore by preferencesDataStore("gallery")
data class GalleryState(
    val media: List<GalleryMedia> = emptyList(), val favorites: Set<String> = emptySet(),
    val columns: Int = 3, val sort: SortOrder = SortOrder.NEWEST, val theme: String = "amethyst",
    val loading: Boolean = true, val error: String? = null, val canRead: Boolean = false,
    val partial: Boolean = false
)

class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = MediaRepository(app)
    private val store = app.galleryStore
    private val favoritesKey = stringSetPreferencesKey("favorites")
    private val columnsKey = intPreferencesKey("columns")
    private val sortKey = stringPreferencesKey("sort")
    private val themeKey = stringPreferencesKey("theme")
    private val mutable = MutableStateFlow(GalleryState())
    val state = mutable.asStateFlow()
    private var loadJob: Job? = null
    private val videoThumbnails = LinkedHashMap<String, ImageBitmap>()
    private var cachedBytes = 0L
    private var observerJob: Job? = null
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            observerJob?.cancel()
            observerJob = viewModelScope.launch { delay(350); refresh() }
        }
    }
    init {
        app.contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        app.contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        viewModelScope.launch {
            store.data.catch { emit(emptyPreferences()) }.collect { prefs ->
                val reload = mutable.value.loading
                mutable.update { it.copy(favorites = prefs[favoritesKey] ?: emptySet(),
                    columns = (prefs[columnsKey] ?: 3).coerceIn(2, 5),
                    sort = SortOrder.entries.find { s -> s.name == prefs[sortKey] } ?: SortOrder.NEWEST,
                    theme = prefs[themeKey] ?: "amethyst") }
                if (reload) refresh()
            }
        }
    }
    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val readable = repository.photos() || repository.videos()
            // Previously loaded media stays on screen while reloading, so returning to the
            // application or a MediaStore change no longer blanks the grid.
            mutable.update { it.copy(loading = it.media.isEmpty(), error = null, canRead = readable, partial = repository.partial()) }
            try {
                val media = repository.read()
                mutable.update { it.copy(media = media, loading = false) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                mutable.update { it.copy(loading = false, error = "Не удалось прочитать файлы. Проверь доступ и повтори.") }
            }
        }
    }
    fun favorite(key: String) = viewModelScope.launch {
        store.edit { prefs ->
            val old = prefs[favoritesKey] ?: emptySet()
            prefs[favoritesKey] = if (key in old) old - key else old + key
        }
    }

    fun favorite(keys: Set<String>) = viewModelScope.launch {
        store.edit { prefs -> prefs[favoritesKey] = BatchPlan.nextFavorites(prefs[favoritesKey] ?: emptySet(), keys) }
    }
    /**
     * Video tiles only. Coil decodes a frame out of the original file for every video tile and
     * redoes it on every scroll back; MediaStore already holds a generated thumbnail. Photos stay
     * with Coil, which downsamples them cheaply. Null means the caller should fall back to Coil.
     */
    suspend fun videoThumbnail(media: GalleryMedia): ImageBitmap? {
        videoThumbnails[media.key]?.let { return it }
        val bitmap = repository.thumbnail(media.uri, THUMBNAIL_PIXELS)?.asImageBitmap() ?: return null
        // Bounded by bytes rather than by count, so the cache cannot quietly grow into tens of
        // megabytes on a library with many videos.
        videoThumbnails[media.key] = bitmap
        cachedBytes += bitmap.width.toLong() * bitmap.height * 4
        while (cachedBytes > THUMBNAIL_BUDGET && videoThumbnails.size > 1) {
            val oldest = videoThumbnails.keys.first()
            videoThumbnails.remove(oldest)?.let { cachedBytes -= it.width.toLong() * it.height * 4 }
        }
        return bitmap
    }

    fun columns(count: Int) = viewModelScope.launch { store.edit { it[columnsKey] = count.coerceIn(2, 5) } }
    fun sort(order: SortOrder) = viewModelScope.launch { store.edit { it[sortKey] = order.name } }
    fun theme(id: String) = viewModelScope.launch { store.edit { it[themeKey] = id } }
    override fun onCleared() {
        videoThumbnails.clear()
        cachedBytes = 0
        getApplication<Application>().contentResolver.unregisterContentObserver(observer)
        super.onCleared()
    }
}

private const val THUMBNAIL_PIXELS = 384
private const val THUMBNAIL_BUDGET = 24L * 1024 * 1024
