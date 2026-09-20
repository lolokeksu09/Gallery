package com.lolokeksu.gallery

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File

/** Builds the shape the shared viewer components expect from a decrypted file. */
private fun VaultItem.asMedia(file: File) = GalleryMedia(
    uri = Uri.fromFile(file), name = name, album = "Хранилище", albumKey = "vault",
    date = date, size = size, width = width, height = height, path = "", mime = mime,
    video = video, duration = duration
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(vm: VaultViewModel, onClose: () -> Unit) {
    val palette = LocalGalleryPalette.current
    val state by vm.state.collectAsState()
    var opened by remember { mutableStateOf<VaultItem?>(null) }
    var settings by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<VaultItem?>(null) }

    BackHandler { if (opened != null) opened = null else onClose() }

    Box(Modifier.fillMaxSize().background(palette.backdropBottom)) {
        when {
            !state.unlocked -> VaultGate(state, vm, onClose)
            opened != null -> VaultViewer(
                items = state.items,
                initial = opened!!,
                vm = vm,
                onClose = { opened = null },
                onRestore = { vm.restore(it); opened = null },
                onDelete = { confirmDelete = it }
            )
            else -> VaultGrid(
                state = state,
                vm = vm,
                onOpen = { opened = it },
                onClose = onClose,
                onSettings = { settings = true }
            )
        }
    }
    // Progress and notices are drawn by the caller, so they are also visible while hiding a
    // file from the gallery with this screen closed.

    confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Удалить навсегда?") },
            text = {
                Text(
                    "${item.name} будет стёрт из хранилища. Оригинала в галерее уже нет, " +
                        "восстановить файл будет неоткуда."
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.delete(item); confirmDelete = null; opened = null }) {
                    Text("Удалить")
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Отмена") } }
        )
    }

    if (settings) VaultPasswordDialog(vm) { settings = false }
}

@Composable
private fun VaultGate(state: VaultState, vm: VaultViewModel, onClose: () -> Unit) {
    val palette = LocalGalleryPalette.current
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val creating = !state.configured
    val blocked = state.lockedOutFor > 0

    fun submit() {
        if (blocked) return
        if (creating) vm.create(password, repeat) else vm.unlock(password)
    }

    Column(
        Modifier.fillMaxSize().background(palette.backdrop).systemBarsPadding().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text(
            if (creating) "Создать хранилище" else "Личное хранилище",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (creating) {
                "Файлы шифруются ключом из этого пароля. Забытый пароль восстановить нельзя: " +
                    "ни я, ни вы не сможем открыть хранилище без него."
            } else {
                "Введите пароль, чтобы открыть."
            },
            style = MaterialTheme.typography.bodySmall,
            color = palette.muted
        )
        Spacer(Modifier.height(22.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Пароль") },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { visible = !visible }) {
                    Text(if (visible) "Скрыть" else "Показать", style = MaterialTheme.typography.labelSmall)
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = if (creating) ImeAction.Next else ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth()
        )
        if (creating) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = repeat,
                onValueChange = { repeat = it },
                label = { Text("Ещё раз") },
                singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = { submit() }, enabled = !blocked, modifier = Modifier.fillMaxWidth()) {
            Text(
                when {
                    blocked -> "Подождите ${state.lockedOutFor / 1000} с"
                    creating -> "Создать"
                    else -> "Открыть"
                }
            )
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onClose) { Text("Назад") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultGrid(
    state: VaultState, vm: VaultViewModel,
    onOpen: (VaultItem) -> Unit, onClose: () -> Unit, onSettings: () -> Unit
) {
    val palette = LocalGalleryPalette.current
    Scaffold(
        containerColor = palette.backdropBottom,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Хранилище", fontWeight = FontWeight.SemiBold)
                        Text(
                            "${state.items.size} файлов · зашифровано",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Сменить пароль") }
                    IconButton(onClick = vm::lock) { Icon(Icons.Default.Lock, "Закрыть хранилище") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.chrome)
            )
        }
    ) { padding ->
        if (state.items.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).background(palette.backdrop).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Lock, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(18.dp))
                Text("Хранилище пусто", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Откройте фото или видео в галерее и нажмите замок на нижней панели. " +
                        "Пока хранилище открыто, эта кнопка видна.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(8.dp),
                modifier = Modifier.fillMaxSize().padding(padding).background(palette.backdrop)
            ) {
                items(state.items, key = { it.id }) { item ->
                    VaultThumbnail(
                        item, vm,
                        Modifier.animateItem().aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                            .clickable { onOpen(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VaultThumbnail(item: VaultItem, vm: VaultViewModel, modifier: Modifier) {
    val palette = LocalGalleryPalette.current
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, item.id) {
        value = vm.thumbnail(item.id)
    }
    Box(modifier.background(palette.card)) {
        bitmap?.let {
            Image(it, item.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                if (item.video) Icons.Default.PlayArrow else Icons.Default.Lock, null,
                Modifier.size(20.dp), tint = palette.muted.copy(alpha = .5f)
            )
        }
        if (item.video) {
            Text(
                formatDuration(item.duration),
                modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp)
                    .clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = .65f))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall, color = Color.White
            )
        }
    }
}

@Composable
private fun VaultViewer(
    items: List<VaultItem>, initial: VaultItem, vm: VaultViewModel,
    onClose: () -> Unit, onRestore: (VaultItem) -> Unit, onDelete: (VaultItem) -> Unit
) {
    val palette = LocalGalleryPalette.current
    if (items.isEmpty()) return
    val pager = rememberPagerState(
        initialPage = items.indexOfFirst { it.id == initial.id }.coerceAtLeast(0),
        pageCount = { items.size }
    )
    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    val current = items[pager.currentPage.coerceIn(items.indices)]
    LaunchedEffect(pager.currentPage) { zoomed = false; chrome = true }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pager, key = { items[it].id }, userScrollEnabled = !zoomed,
            modifier = Modifier.fillMaxSize()
        ) { index ->
            val item = items[index]
            // Each page is decrypted into the private cache only when it is reached.
            val file by produceState<File?>(null, item.id) { value = vm.open(item) }
            val decrypted = file
            if (decrypted == null) {
                Box(
                    Modifier.fillMaxSize().pointerInput(item.id) {
                        detectTapGestures { chrome = !chrome }
                    },
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            } else {
                val media = remember(item.id, decrypted) { item.asMedia(decrypted) }
                if (item.video && index == pager.currentPage) {
                    VideoPlayer(media, chrome) { chrome = it }
                } else {
                    ZoomableImage(
                        media,
                        onZoom = { if (index == pager.currentPage) zoomed = it },
                        onTap = { chrome = !chrome }
                    )
                }
            }
        }
        AnimatedVisibility(
            chrome,
            enter = fadeIn(tween(200)) + slideInVertically(tween(240)) { -it / 3 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { -it / 3 },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .75f), Color.Transparent)))
                    .statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        current.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "${pager.currentPage + 1} / ${items.size}",
                        style = MaterialTheme.typography.labelSmall, color = palette.muted
                    )
                }
            }
        }
        AnimatedVisibility(
            chrome,
            enter = fadeIn(tween(200)) + slideInVertically(tween(240)) { it / 3 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 3 },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .75f))))
                    .navigationBarsPadding().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = { onRestore(current) }) { Text("Вернуть в галерею") }
                TextButton(onClick = { onDelete(current) }) { Text("Удалить", color = palette.danger) }
            }
        }
    }
}

@Composable
private fun VaultPasswordDialog(vm: VaultViewModel, onDismiss: () -> Unit) {
    val palette = LocalGalleryPalette.current
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Сменить пароль") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Файлы не перешифровываются: меняется только защита ключа.",
                    style = MaterialTheme.typography.bodySmall, color = palette.muted
                )
                OutlinedTextField(old, { old = it }, label = { Text("Текущий пароль") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(new, { new = it }, label = { Text("Новый пароль") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(repeat, { repeat = it }, label = { Text("Ещё раз") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation())
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.changePassword(old, new, repeat); onDismiss() }) { Text("Сменить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
