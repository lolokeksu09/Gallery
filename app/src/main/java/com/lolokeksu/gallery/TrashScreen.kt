package com.lolokeksu.gallery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Android's own trash, not a second one kept by this application. Deleting from the gallery uses
 * createTrashRequest, so a file stays recoverable for thirty days; this lists those files and
 * hands both actions back to the platform, which asks for confirmation itself.
 *
 * Android may withhold trashed items that belong to another application. The empty state says so
 * instead of claiming the trash is empty, because the two look identical from here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    items: List<GalleryMedia>,
    loading: Boolean,
    vm: GalleryViewModel,
    onClose: () -> Unit,
    onRestore: (List<GalleryMedia>) -> Unit,
    onDeleteForever: (List<GalleryMedia>) -> Unit
) {
    val palette = LocalGalleryPalette.current
    var selection by remember { mutableStateOf(emptySet<String>()) }
    var confirmPurge by remember { mutableStateOf(false) }
    val chosen = remember(items, selection) { items.filter { it.key in selection } }

    // Dropping a selection is a step inside the screen, so it stays an ordinary back press.
    BackHandler(selection.isNotEmpty()) { selection = emptySet() }
    val back = predictiveBackProgress(selection.isEmpty(), onBack = onClose)

    Box(
        Modifier.fillMaxSize().background(palette.backdrop).predictiveBack(back)
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Scaffold(
            containerColor = palette.backdrop,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                if (selection.isEmpty()) "Корзина" else "${selection.size} выбрано",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (selection.isEmpty()) fileCount(items.size) + " · Android удалит через 30 дней"
                                else "Действие подтвердит Android",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.accent
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { if (selection.isEmpty()) onClose() else selection = emptySet() }) {
                            Icon(
                                if (selection.isEmpty()) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Close,
                                "Назад"
                            )
                        }
                    },
                    actions = {
                        if (items.isNotEmpty()) {
                            TextButton(onClick = {
                                selection = if (selection.size == items.size) emptySet()
                                else items.mapTo(HashSet()) { it.key }
                            }) {
                                Text(if (selection.size == items.size) "Снять" else "Все")
                            }
                        }
                        if (selection.isNotEmpty()) {
                            IconButton(onClick = { onRestore(chosen); selection = emptySet() }) {
                                Icon(Icons.Default.Refresh, "Восстановить")
                            }
                            IconButton(onClick = { confirmPurge = true }) {
                                Icon(Icons.Default.Delete, "Удалить навсегда")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.chrome)
                )
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).background(palette.backdrop)) {
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    items.isEmpty() -> EmptyTrash(palette)
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(104.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(items, key = { it.key }) { media ->
                            Thumbnail(
                                media, favorite = false,
                                modifier = Modifier.animateItem().aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        selection = if (media.key in selection) selection - media.key
                                        else selection + media.key
                                    },
                                vm = vm, selected = media.key in selection
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmPurge) {
        AlertDialog(
            onDismissRequest = { confirmPurge = false },
            title = { Text("Удалить навсегда?") },
            text = {
                Text(
                    (if (chosen.size == 1) "Файл будет стёрт" else fileCount(chosen.size) + " будут стёрты") +
                        " без возможности восстановления. Android спросит подтверждение ещё раз."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteForever(chosen)
                    selection = emptySet()
                    confirmPurge = false
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmPurge = false }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun EmptyTrash(palette: GalleryPalette) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Delete, null, Modifier.size(52.dp), tint = palette.accent)
        Spacer(Modifier.height(18.dp))
        Text("Здесь пусто", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        Text(
            "Либо в корзине ничего нет, либо Android не показывает этому приложению файлы, " +
                "удалённые другими. Отличить одно от другого отсюда нельзя — посмотрите корзину " +
                "в приложении «Файлы» или в системной галерее.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.muted
        )
    }
}
