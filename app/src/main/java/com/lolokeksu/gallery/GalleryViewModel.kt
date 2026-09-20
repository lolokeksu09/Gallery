package com.lolokeksu.gallery

import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

private val Context.galleryStore by preferencesDataStore("gallery")
data class GalleryState(
    val media: List<GalleryMedia> = emptyList(), val favorites: Set<String> = emptySet(),
    val columns: Int = 3, val sort: SortOrder = SortOrder.NEWEST,
    val loading: Boolean = true, val error: String? = null, val canRead: Boolean = false,
    val partial: Boolean = false
)

class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = MediaRepository(app)
    private val store = app.galleryStore
    private val favoritesKey = stringSetPreferencesKey("favorites")
    private val columnsKey = intPreferencesKey("columns")
    private val sortKey = stringPreferencesKey("sort")
    private val mutable = MutableStateFlow(GalleryState())
    val state = mutable.asStateFlow()
    private var loadJob: Job? = null
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
                    sort = SortOrder.entries.find { s -> s.name == prefs[sortKey] } ?: SortOrder.NEWEST) }
                if (reload) refresh()
            }
        }
    }
    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val readable = repository.photos() || repository.videos()
            mutable.update { it.copy(loading = true, error = null, canRead = readable, partial = repository.partial(), media = emptyList()) }
            try {
                val media = repository.read(includeVideos = true)
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
    fun columns(count: Int) = viewModelScope.launch { store.edit { it[columnsKey] = count.coerceIn(2, 5) } }
    fun sort(order: SortOrder) = viewModelScope.launch { store.edit { it[sortKey] = order.name } }
    override fun onCleared() { getApplication<Application>().contentResolver.unregisterContentObserver(observer) }
}
