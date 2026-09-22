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
    val media: List<GalleryMedia> = emptyList(),
    /** Favorites by stable key. */
    val favorites: Set<String> = emptySet(),
    /**
     * Favorites still stored against a content URI, from before the stable key existed. They are
     * folded into [favorites] as the library accounts for them and are never dropped otherwise.
     */
    val legacyFavorites: Set<String> = emptySet(),
    val columns: Int = 3, val sort: SortOrder = SortOrder.NEWEST, val theme: String = "amethyst",
    val loading: Boolean = true, val error: String? = null, val canRead: Boolean = false,
    val partial: Boolean = false,
    /** Android's trash, loaded only while that screen is open. */
    val trash: List<GalleryMedia> = emptyList(), val trashLoading: Boolean = false
)

/** The one place that answers it, because the answer now lives in two sets rather than one. */
fun GalleryState.isFavorite(media: GalleryMedia): Boolean =
    media.stableKey in favorites || media.key in legacyFavorites

class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = MediaRepository(app)
    private val store = app.galleryStore
    private val favoritesKey = stringSetPreferencesKey("favorites")
    private val legacyFavoritesKey = stringSetPreferencesKey("favorites_uri")
    private val migratedKey = booleanPreferencesKey("favorites_migrated_v2")
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
            // Before the first emission, so the hearts are never read against the old shape.
            migrateFavorites()
            store.data.catch { emit(emptyPreferences()) }.collect { prefs ->
                val reload = mutable.value.loading
                mutable.update { it.copy(favorites = prefs[favoritesKey] ?: emptySet(),
                    legacyFavorites = prefs[legacyFavoritesKey] ?: emptySet(),
                    columns = (prefs[columnsKey] ?: 3).coerceIn(2, 5),
                    sort = SortOrder.entries.find { s -> s.name == prefs[sortKey] } ?: SortOrder.NEWEST,
                    theme = prefs[themeKey] ?: "amethyst") }
                if (reload) refresh()
            }
        }
    }

    /**
     * One atomic edit: everything stored under the old shape moves to the legacy set and the
     * stable set starts empty. Nothing is discarded, so a failure to match later costs nothing.
     */
    private suspend fun migrateFavorites() {
        store.edit { prefs ->
            if (prefs[migratedKey] == true) return@edit
            val stored = prefs[favoritesKey] ?: emptySet()
            if (stored.isNotEmpty()) {
                prefs[legacyFavoritesKey] = (prefs[legacyFavoritesKey] ?: emptySet()) + stored
            }
            prefs[favoritesKey] = emptySet()
            prefs[migratedKey] = true
        }
    }

    /**
     * Folds legacy URIs into stable keys using what the library just returned.
     *
     * Runs only on a read that can be trusted. An empty library means the permission is gone, not
     * that the files are, and a partial grant hides most of them; converting against either would
     * be a slow way of losing the set. Unmatched entries are left alone by [FavoriteMigration].
     */
    private suspend fun convertLegacyFavorites(media: List<GalleryMedia>) {
        val current = mutable.value
        if (current.legacyFavorites.isEmpty()) return
        if (!current.canRead || current.partial || media.isEmpty()) return
        val library = media.map { it.key to it.stableKey }
        store.edit { prefs ->
            val (favorites, legacy) = FavoriteMigration.convert(
                prefs[legacyFavoritesKey] ?: emptySet(), prefs[favoritesKey] ?: emptySet(), library
            )
            prefs[favoritesKey] = favorites
            prefs[legacyFavoritesKey] = legacy
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
                convertLegacyFavorites(media)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                mutable.update { it.copy(loading = false, error = "Не удалось прочитать файлы. Проверь доступ и повтори.") }
            }
        }
    }
    fun favorite(media: GalleryMedia) = viewModelScope.launch {
        store.edit { prefs ->
            val favorites = prefs[favoritesKey] ?: emptySet()
            val legacy = prefs[legacyFavoritesKey] ?: emptySet()
            val stable = media.stableKey
            // The mark can currently come from either set, and turning it off has to clear both
            // or an unconverted file would stay favorited through the legacy entry.
            val on = stable in favorites || media.key in legacy
            prefs[favoritesKey] = if (on) favorites - stable else favorites + stable
            if (media.key in legacy) prefs[legacyFavoritesKey] = legacy - media.key
        }
    }

    /**
     * Favorites are never pruned against the library. With the permission revoked the library
     * reads empty and with a partial grant it reads partial, so removing keys whose file is
     * "missing" would delete favorites for files that are still there.
     */
    fun favorite(media: List<GalleryMedia>) = viewModelScope.launch {
        if (media.isEmpty()) return@launch
        store.edit { prefs ->
            val favorites = prefs[favoritesKey] ?: emptySet()
            val legacy = prefs[legacyFavoritesKey] ?: emptySet()
            val uris = media.map { it.key }.toSet()
            // Fold this selection's legacy marks in first, so "already all favorited" is judged
            // on one set instead of two and the group toggle keeps working.
            val folded = favorites + media.filter { it.key in legacy }.map { it.stableKey }
            prefs[favoritesKey] = BatchPlan.nextFavorites(folded, media.map { it.stableKey }.toSet())
            if (legacy.any { it in uris }) prefs[legacyFavoritesKey] = legacy - uris
        }
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

    fun loadTrash() = viewModelScope.launch {
        mutable.update { it.copy(trashLoading = true) }
        val items = try {
            repository.trashed()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        mutable.update { it.copy(trash = items, trashLoading = false) }
    }

    fun clearTrashList() = mutable.update { it.copy(trash = emptyList(), trashLoading = false) }

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
