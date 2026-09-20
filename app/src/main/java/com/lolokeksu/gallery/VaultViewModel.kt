package com.lolokeksu.gallery

import android.app.Application
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.crypto.SecretKey

data class VaultState(
    val configured: Boolean = false,
    val unlocked: Boolean = false,
    val items: List<VaultItem> = emptyList(),
    val busy: String? = null,
    val error: String? = null,
    val message: String? = null,
    /** Set once a copy is encrypted and verified: the screen must now ask Android to delete the original. */
    val pendingDelete: GalleryMedia? = null,
    /** Milliseconds the user must wait before the next attempt after repeated failures. */
    val lockedOutFor: Long = 0
)

/**
 * Holds the vault's unlocked data key for as long as the vault is open. The key lives only in
 * memory: locking drops it and wipes every decrypted copy, and the screen locks itself whenever
 * the application goes to the background.
 */
class VaultViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = VaultRepository(app)
    private val mutable = MutableStateFlow(VaultState())
    val state = mutable.asStateFlow()

    private var key: SecretKey? = null
    private var failures = 0
    private val thumbnails = LinkedHashMap<String, ImageBitmap>()

    private var pendingItem: VaultItem? = null

    init {
        viewModelScope.launch { mutable.update { it.copy(configured = repository.configured()) } }
    }

    fun create(password: String, repeat: String) {
        if (password.length < MIN_PASSWORD) {
            mutable.update { it.copy(error = "Пароль короче $MIN_PASSWORD символов") }
            return
        }
        if (password != repeat) {
            mutable.update { it.copy(error = "Пароли не совпадают") }
            return
        }
        viewModelScope.launch {
            mutable.update { it.copy(busy = "Создаю хранилище", error = null) }
            try {
                key = repository.create(password.toCharArray())
                mutable.update { it.copy(configured = true, unlocked = true, busy = null, items = emptyList()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(busy = null, error = e.message ?: "Не удалось создать хранилище") }
            }
        }
    }

    fun unlock(password: String) {
        viewModelScope.launch {
            mutable.update { it.copy(busy = "Проверяю пароль", error = null) }
            val unlocked = repository.unlock(password.toCharArray())
            if (unlocked == null) {
                failures++
                // Deriving the key already costs a moment; add a growing wait after repeated misses.
                val wait = if (failures >= FREE_ATTEMPTS) {
                    minOf(1000L shl minOf(failures - FREE_ATTEMPTS, 6), MAX_LOCKOUT)
                } else {
                    0L
                }
                mutable.update {
                    it.copy(busy = null, error = "Неверный пароль", lockedOutFor = wait)
                }
                if (wait > 0) {
                    delay(wait)
                    mutable.update { it.copy(lockedOutFor = 0) }
                }
                return@launch
            }
            failures = 0
            key = unlocked
            mutable.update { it.copy(unlocked = true, busy = null, error = null, lockedOutFor = 0) }
            reload()
        }
    }

    fun lock() {
        key = null
        thumbnails.clear()
        repository.clearCache()
        pendingItem = null
        mutable.update {
            it.copy(
                unlocked = false, items = emptyList(), error = null, message = null,
                busy = null, pendingDelete = null
            )
        }
    }

    private suspend fun reload() {
        val current = key ?: return
        mutable.update { it.copy(items = repository.list(current)) }
    }

    suspend fun thumbnail(id: String): ImageBitmap? {
        thumbnails[id]?.let { return it }
        val current = key ?: return null
        val raw = repository.thumbnail(current, id) ?: return null
        val bitmap = withContext(Dispatchers.Default) {
            BitmapFactory.decodeByteArray(raw, 0, raw.size)?.asImageBitmap()
        } ?: return null
        if (thumbnails.size >= THUMBNAIL_CACHE) {
            thumbnails.remove(thumbnails.keys.first())
        }
        thumbnails[id] = bitmap
        return bitmap
    }

    suspend fun open(item: VaultItem): File? {
        val current = key ?: return null
        return try {
            repository.materialize(current, item)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            mutable.update { it.copy(error = e.message) }
            null
        }
    }

    /** Encrypts and verifies the copy. The original is only deleted after [confirmHidden]. */
    fun hide(media: GalleryMedia) {
        val current = key ?: return
        viewModelScope.launch {
            mutable.update { it.copy(busy = "Шифрую ${media.name}", error = null, message = null) }
            try {
                val item = repository.import(current, media)
                pendingItem = item
                mutable.update { it.copy(busy = null, pendingDelete = media) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                pendingItem = null
                mutable.update {
                    it.copy(busy = null, pendingDelete = null, error = e.message ?: "Не удалось скрыть файл")
                }
            }
        }
    }

    /** The system delete succeeded, so the vault copy is now the only one. */
    fun confirmHidden() {
        val hidden = pendingItem
        pendingItem = null
        viewModelScope.launch {
            reload()
            mutable.update {
                it.copy(pendingDelete = null, message = hidden?.let { item -> "${item.name} перенесён в хранилище" })
            }
        }
    }

    /** The system delete was refused, so the copy is dropped and nothing changes. */
    fun cancelHidden() {
        val item = pendingItem
        val media = mutable.value.pendingDelete
        pendingItem = null
        if (item != null) repository.forget(item.id)
        mutable.update {
            it.copy(
                pendingDelete = null,
                message = media?.let { source -> "Оригинал не удалён, поэтому ${source.name} не скрыт" }
            )
        }
    }

    fun restore(item: VaultItem) {
        val current = key ?: return
        viewModelScope.launch {
            mutable.update { it.copy(busy = "Восстанавливаю ${item.name}", error = null, message = null) }
            try {
                repository.restore(current, item)
                thumbnails.remove(item.id)
                reload()
                mutable.update { it.copy(busy = null, message = "${item.name} возвращён в галерею") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(busy = null, error = e.message ?: "Не удалось восстановить файл") }
            }
        }
    }

    fun delete(item: VaultItem) {
        viewModelScope.launch {
            repository.forget(item.id)
            thumbnails.remove(item.id)
            reload()
            mutable.update { it.copy(message = "${item.name} удалён без возможности возврата") }
        }
    }

    fun changePassword(old: String, new: String, repeat: String) {
        if (new.length < MIN_PASSWORD) {
            mutable.update { it.copy(error = "Пароль короче $MIN_PASSWORD символов") }
            return
        }
        if (new != repeat) {
            mutable.update { it.copy(error = "Новые пароли не совпадают") }
            return
        }
        viewModelScope.launch {
            mutable.update { it.copy(busy = "Меняю пароль", error = null) }
            val changed = repository.changePassword(old.toCharArray(), new.toCharArray())
            mutable.update {
                if (changed) it.copy(busy = null, message = "Пароль изменён")
                else it.copy(busy = null, error = "Старый пароль неверен")
            }
        }
    }

    fun clearNotice() = mutable.update { it.copy(error = null, message = null) }

    override fun onCleared() {
        lock()
        super.onCleared()
    }

    private companion object {
        const val MIN_PASSWORD = 6
        const val FREE_ATTEMPTS = 3
        const val MAX_LOCKOUT = 60_000L
        const val THUMBNAIL_CACHE = 120
    }
}
