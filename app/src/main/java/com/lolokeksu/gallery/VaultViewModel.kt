package com.lolokeksu.gallery

import android.app.Application
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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
    /** Copies encrypted and verified: the screen must now ask Android to delete these originals. */
    val pendingDelete: List<GalleryMedia> = emptyList(),
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
    private val thumbnails = LinkedHashMap<String, ImageBitmap>()
    private var cachedBytes = 0L

    private var pendingItems: List<VaultItem> = emptyList()
    private var importJob: Job? = null

    /**
     * Whether the platform delete dialog has already been launched for the pending batch. The
     * effect that launches it re-runs on activity recreation, and a second dialog for the same
     * files could be cancelled while the first is confirmed, losing them from both places.
     */
    var deleteRequested = false
        private set

    fun markDeleteRequested() {
        deleteRequested = true
    }

    init {
        viewModelScope.launch {
            // A crash can leave decrypted copies behind; the guarantee is that they never
            // outlive the application, so startup clears them too.
            withContext(Dispatchers.IO) { repository.clearCache() }
            // The stored count survives a force stop, so the delay it earned survives too.
            val stored = repository.failures()
            mutable.update { it.copy(configured = repository.configured(), lockedOutFor = waitFor(stored)) }
            waitOut(waitFor(stored))
        }
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
                val wait = waitFor(repository.recordFailure())
                mutable.update {
                    it.copy(busy = null, error = "Неверный пароль", lockedOutFor = wait)
                }
                waitOut(wait)
                return@launch
            }
            repository.clearFailures()
            key = unlocked
            mutable.update { it.copy(unlocked = true, busy = null, error = null, lockedOutFor = 0) }
            reload()
        }
    }

    fun lock() {
        key = null
        thumbnails.clear()
        cachedBytes = 0
        // An import still running would keep working with the key it captured and then republish
        // pendingDelete, leaving encrypted copies behind while their originals are still in the
        // gallery. Cancelling makes it clean up after itself.
        importJob?.cancel()
        importJob = null
        deleteRequested = false
        val abandoned = pendingItems
        pendingItems = emptyList()
        // Dropping the key is what matters and must happen now; unlinking files is done off the
        // main thread because lock() is driven from the lifecycle observer.
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                abandoned.forEach { repository.forget(it.id) }
                repository.clearCache()
            }
        }
        mutable.update {
            it.copy(
                unlocked = false, items = emptyList(), error = null, message = null,
                busy = null, pendingDelete = emptyList()
            )
        }
    }

    private fun forgetThumbnail(id: String) {
        thumbnails.remove(id)?.let { cachedBytes -= it.width.toLong() * it.height * 4 }
    }

    private suspend fun reload() {
        val current = key ?: return
        val listing = repository.list(current)
        mutable.update {
            it.copy(
                items = listing.items,
                error = if (listing.unreadable > 0) {
                    "${listing.unreadable} записей в хранилище не читаются. Файлы на месте, но метаданные повреждены."
                } else {
                    it.error
                }
            )
        }
    }

    suspend fun thumbnail(id: String): ImageBitmap? {
        thumbnails[id]?.let { return it }
        val current = key ?: return null
        val raw = repository.thumbnail(current, id) ?: return null
        val bitmap = withContext(Dispatchers.Default) {
            BitmapFactory.decodeByteArray(raw, 0, raw.size)?.asImageBitmap()
        } ?: return null
        // Bounded by bytes, not by count: a 512px preview decodes to about a megabyte, so a
        // count-based cache of a hundred entries would hold a hundred megabytes.
        thumbnails[id] = bitmap
        cachedBytes += bitmap.width.toLong() * bitmap.height * 4
        while (cachedBytes > THUMBNAIL_BUDGET && thumbnails.size > 1) {
            val oldest = thumbnails.keys.first()
            thumbnails.remove(oldest)?.let { cachedBytes -= it.width.toLong() * it.height * 4 }
        }
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

    /**
     * Encrypts and verifies the copy. The original is only deleted after [confirmHidden].
     *
     * Nothing announces what happened: a message naming the file or the vault would tell anyone
     * watching the screen that a vault exists, which is the one thing it must not do. Only the
     * unlabelled progress spinner shows, because a long encryption would otherwise look frozen.
     */
    fun hide(media: List<GalleryMedia>) {
        val current = key ?: return
        if (media.isEmpty()) return
        // A second start would overwrite the pending list and orphan the copies already made.
        if (mutable.value.busy != null || mutable.value.pendingDelete.isNotEmpty()) return
        importJob = viewModelScope.launch {
            val imported = mutableListOf<VaultItem>()
            val originals = mutableListOf<GalleryMedia>()
            var failed = 0
            try {
                media.forEachIndexed { index, source ->
                    // A bare counter, never a file name: nothing on screen may name the vault.
                    mutable.update {
                        it.copy(
                            busy = if (media.size > 1) "${index + 1} / ${media.size}" else "",
                            error = null, message = null
                        )
                    }
                    try {
                        imported += repository.import(current, source)
                        originals += source
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // import() already removed its own partial copy; carry on with the rest.
                        failed++
                    }
                }
            } catch (e: CancellationException) {
                // Locking mid-batch must not leave copies behind with their originals still there.
                withContext(NonCancellable) {
                    withContext(Dispatchers.IO) { imported.forEach { repository.forget(it.id) } }
                }
                throw e
            }
            pendingItems = imported
            mutable.update {
                it.copy(
                    busy = null,
                    pendingDelete = originals,
                    error = when {
                        failed == 0 -> null
                        originals.isEmpty() -> "Не удалось выполнить действие"
                        else -> "Не удалось обработать $failed из ${media.size}"
                    }
                )
            }
        }
    }

    /** The system delete succeeded. Deliberately silent: see [hide]. */
    fun confirmHidden() {
        deleteRequested = false
        pendingItems = emptyList()
        viewModelScope.launch {
            reload()
            mutable.update { it.copy(pendingDelete = emptyList()) }
        }
    }

    /**
     * The system delete was refused, so the copy is dropped and nothing changes. Also silent: the
     * user just dismissed that dialog themselves, so they know the file stayed where it was.
     */
    fun cancelHidden() {
        // Every copy made for this batch goes, not just the last one, or the rest would be
        // orphaned while their originals are still in the gallery.
        val abandoned = pendingItems
        pendingItems = emptyList()
        deleteRequested = false
        if (abandoned.isNotEmpty()) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) { abandoned.forEach { repository.forget(it.id) } }
            }
        }
        mutable.update { it.copy(pendingDelete = emptyList()) }
    }

    fun restore(item: VaultItem) {
        val current = key ?: return
        viewModelScope.launch {
            mutable.update { it.copy(busy = "Восстанавливаю ${item.name}", error = null, message = null) }
            try {
                repository.restore(current, item)
                forgetThumbnail(item.id)
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
            withContext(Dispatchers.IO) { repository.forget(item.id) }
            forgetThumbnail(item.id)
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
            try {
                val changed = repository.changePassword(old.toCharArray(), new.toCharArray())
                mutable.update {
                    if (changed) it.copy(busy = null, message = "Пароль изменён")
                    else it.copy(busy = null, error = "Старый пароль неверен")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(busy = null, error = e.message ?: "Не удалось сменить пароль") }
            }
        }
    }

    /** Deriving the key already costs a moment; add a growing wait after repeated misses. */
    private fun waitFor(failures: Int): Long =
        if (failures >= FREE_ATTEMPTS) minOf(1000L shl minOf(failures - FREE_ATTEMPTS, 6), MAX_LOCKOUT) else 0L

    private suspend fun waitOut(wait: Long) {
        if (wait <= 0) return
        delay(wait)
        mutable.update { it.copy(lockedOutFor = 0) }
    }

    fun clearNotice() = mutable.update { it.copy(error = null, message = null) }

    override fun onCleared() {
        // viewModelScope is already cancelled here, so the wipe runs directly.
        key = null
        thumbnails.clear()
        pendingItems = emptyList()
        repository.clearCache()
        super.onCleared()
    }

    private companion object {
        const val MIN_PASSWORD = 6
        const val FREE_ATTEMPTS = 3
        const val MAX_LOCKOUT = 60_000L
        const val THUMBNAIL_BUDGET = 32L * 1024 * 1024
    }
}
