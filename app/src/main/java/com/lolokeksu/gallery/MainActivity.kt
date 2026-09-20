package com.lolokeksu.gallery

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.*
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val SETTINGS_TAB = 3
private const val VAULT_TAPS = 5
private const val TAP_WINDOW_MS = 1500L

/** Minimum and maximum number of grid columns reachable with the pinch gesture. */
const val MIN_COLUMNS = 2
const val MAX_COLUMNS = 5

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: GalleryViewModel = viewModel()
            val theme by vm.state.collectAsStateWithLifecycle()
            val palette = paletteFor(theme.theme)
            CompositionLocalProvider(LocalGalleryPalette provides palette) {
                MaterialTheme(colorScheme = palette.colorScheme) { GalleryApp(vm) }
            }
        }
    }
}

@Composable private fun photosIcon(): Painter = painterResource(R.drawable.ic_photos)
@Composable private fun albumsIcon(): Painter = painterResource(R.drawable.ic_albums)
@Composable private fun sortIcon(): Painter = painterResource(R.drawable.ic_sort)
@Composable private fun gridIcon(): Painter = painterResource(R.drawable.ic_grid)
@Composable private fun paletteIcon(): Painter = painterResource(R.drawable.ic_palette)

/** Tab, album and sort filtering in one place so every screen derives the same list. */
private fun mediaFor(state: GalleryState, tab: Int, album: String?): List<GalleryMedia> {
    val filtered = state.media.filter {
        (tab != 2 || it.key in state.favorites) && (album == null || it.albumKey == album)
    }
    return when (state.sort) {
        SortOrder.NEWEST -> filtered.sortedByDescending { it.date }
        SortOrder.OLDEST -> filtered.sortedBy { it.date }
        SortOrder.NAME -> filtered.sortedBy { it.name.lowercase() }
    }
}

@Composable
fun GalleryApp(vm: GalleryViewModel = viewModel(), vaultVm: VaultViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val vault by vaultVm.state.collectAsStateWithLifecycle()
    val palette = LocalGalleryPalette.current
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var vaultOpen by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var album by rememberSaveable { mutableStateOf<String?>(null) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var selectionList by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val selection = remember(selectionList) { selectionList.toSet() }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refresh() }
    fun access() {
        request.launch(buildList {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
            if (Build.VERSION.SDK_INT >= 34) add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        }.toTypedArray())
    }
    LaunchedEffect(Unit) {
        // Records only whether onboarding was shown, never the actual permission state.
        val onboarding = context.getSharedPreferences("onboarding", android.content.Context.MODE_PRIVATE)
        if (!onboarding.getBoolean("combined_media_prompt_v1", false)) {
            onboarding.edit().putBoolean("combined_media_prompt_v1", true).apply()
            val granted = android.content.pm.PackageManager.PERMISSION_GRANTED
            val fullAccess = context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == granted &&
                context.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == granted
            val partialAccess = Build.VERSION.SDK_INT >= 34 &&
                context.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == granted
            if (!fullAccess && !partialAccess) access()
        }
    }
    val deleteRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        vm.refresh()
        // A cancelled deletion must leave the viewer open on the same file.
        if (result.resultCode == Activity.RESULT_OK) {
            selected = null
            selectionList = emptyList()
        }
    }
    // The vault locks itself whenever the application leaves the foreground, which also wipes
    // every decrypted copy from the cache.
    val hideRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            vaultVm.confirmHidden()
            selected = null
            selectionList = emptyList()
        } else {
            vaultVm.cancelHidden()
        }
        vm.refresh()
    }
    LaunchedEffect(vault.pendingDelete) {
        val pending = vault.pendingDelete
        if (pending.isEmpty()) return@LaunchedEffect
        try {
            // Deliberately a real delete, not the trash: a trashed original stays listed in the
            // system trash, which would defeat the point of hiding it. One dialog covers the batch.
            val intent = MediaStore.createDeleteRequest(context.contentResolver, pending.map { it.uri })
            hideRequest.launch(IntentSenderRequest.Builder(intent.intentSender).build())
        } catch (_: Exception) {
            vaultVm.cancelHidden()
        }
    }
    // The recents snapshot is taken as the application leaves the foreground, so an unlocked
    // vault would otherwise sit in the task switcher, visible after the screen lock. FLAG_SECURE
    // blanks that snapshot and blocks screenshots, screen recording and casting in one move.
    // It is held only while the vault is involved: half this library is screenshots, so blocking
    // them everywhere would break ordinary use.
    val secure = vaultOpen || vault.unlocked
    DisposableEffect(secure) {
        val window = context.activity()?.window
        if (secure) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> vm.refresh()
                // A rotation also stops the activity; locking there would drop the key and
                // throw the user back to the password gate mid-view.
                Lifecycle.Event.ON_STOP -> if (context.activity()?.isChangingConfigurations != true) {
                    vaultVm.lock()
                    vaultOpen = false
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // Favorites only change the list on the favorites tab, so toggling a heart elsewhere
    // does not re-filter and re-sort the whole library.
    val visible = remember(state.media, state.sort, tab, album, if (tab == 2) state.favorites else null) {
        mediaFor(state, tab, album)
    }
    val openKey = selected?.takeIf { key -> visible.any { it.key == key } }
    // The request is held while the viewer plays its exit animation. `session` increments on
    // every open so a reopened viewer never inherits the previous pager position.
    var viewer by remember { mutableStateOf<ViewerRequest?>(null) }
    val held = viewer
    if (openKey == null) {
        if (held != null && held.open) viewer = held.copy(open = false)
    } else if (held == null || !held.open || held.key != openKey) {
        viewer = ViewerRequest(visible, openKey, (held?.session ?: 0) + 1, open = true)
    } else if (held.media !== visible) {
        // Refreshes and deletions elsewhere in the library reach the open viewer.
        viewer = held.copy(media = visible)
    }

    val chosen = remember(visible, selection) { visible.filter { it.key in selection } }
    fun trashChosen() {
        if (chosen.isEmpty()) return
        try {
            val intent = MediaStore.createTrashRequest(context.contentResolver, chosen.map { it.uri }, true)
            deleteRequest.launch(IntentSenderRequest.Builder(intent.intentSender).build())
        } catch (_: Exception) { Toast.makeText(context, "Не удалось запросить удаление", Toast.LENGTH_SHORT).show() }
    }
    fun shareChosen() {
        if (chosen.isEmpty()) return
        try {
            val uris = ArrayList(chosen.map { it.uri })
            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = chosen.first().mime
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = BatchPlan.shareType(chosen.map { it.mime })
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                }
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(intent, "Поделиться"))
        } catch (_: Exception) { Toast.makeText(context, "Не удалось отправить файлы", Toast.LENGTH_SHORT).show() }
    }

    Box(Modifier.fillMaxSize().background(palette.backdropBottom)) {
        GalleryHome(state, vm, tab, album, visible, onTab = { tab = it; album = null; selectionList = emptyList() },
            onAlbum = { album = it; selectionList = emptyList() },
            onOpen = { selected = it }, onAccess = { access() }, onVault = { vaultOpen = true },
            selection = selection,
            onToggle = { media ->
                selectionList = if (media.key in selection) selectionList - media.key else selectionList + media.key
            },
            onClearSelection = { selectionList = emptyList() },
            onSelectionShare = { shareChosen() },
            onSelectionTrash = { trashChosen() },
            onSelectionFavorite = { vm.favorite(selection); selectionList = emptyList() },
            onSelectionHide = if (vault.unlocked) ({ vaultVm.hide(chosen) }) else null)
        AnimatedVisibility(
            visible = openKey != null,
            enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.94f, animationSpec = tween(220)),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.94f, animationSpec = tween(180))
        ) {
            viewer?.let { request ->
                key(request.session) {
                    MediaViewer(request.media, request.key, state.favorites, onClose = { selected = null }, onFavorite = vm::favorite,
                        onDelete = { media ->
                            try {
                                // The system trash keeps the file recoverable for 30 days, unlike
                                // createDeleteRequest which erases it outright.
                                val intent = MediaStore.createTrashRequest(context.contentResolver, listOf(media.uri), true)
                                deleteRequest.launch(IntentSenderRequest.Builder(intent.intentSender).build())
                            } catch (_: Exception) { Toast.makeText(context, "Не удалось запросить удаление", Toast.LENGTH_SHORT).show() }
                        },
                        onHide = if (vault.unlocked) ({ media -> vaultVm.hide(listOf(media)) }) else null)
                }
            }
        }
        AnimatedVisibility(
            visible = vaultOpen,
            enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.94f, animationSpec = tween(220)),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.94f, animationSpec = tween(180))
        ) {
            VaultScreen(vaultVm) { vaultOpen = false }
        }
        // Hiding a file starts from the gallery viewer with the vault closed, so its progress and
        // its failures have to be shown here rather than inside the vault screen.
        vault.busy?.let { busy ->
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .72f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    // Hiding a file uses an empty label on purpose, so nothing on screen names
                    // the vault while the encryption runs.
                    if (busy.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text(busy, color = Color.White)
                    }
                }
            }
        }
    }
    val notice = vault.error ?: vault.message
    if (notice != null && vault.busy == null && vault.pendingDelete.isEmpty()) {
        AlertDialog(
            onDismissRequest = vaultVm::clearNotice,
            text = { Text(notice) },
            confirmButton = { TextButton(onClick = vaultVm::clearNotice) { Text("Понятно") } }
        )
    }
}

/** What the viewer overlay renders, kept alive across its exit animation. */
private data class ViewerRequest(
    val media: List<GalleryMedia>, val key: String, val session: Int, val open: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryHome(
    state: GalleryState, vm: GalleryViewModel, tab: Int, album: String?, visible: List<GalleryMedia>,
    onTab: (Int) -> Unit, onAlbum: (String?) -> Unit, onOpen: (String) -> Unit, onAccess: () -> Unit,
    onVault: () -> Unit,
    selection: Set<String>, onToggle: (GalleryMedia) -> Unit, onClearSelection: () -> Unit,
    onSelectionShare: () -> Unit, onSelectionTrash: () -> Unit, onSelectionFavorite: () -> Unit,
    onSelectionHide: (() -> Unit)?
) {
    val palette = LocalGalleryPalette.current
    var sortMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // The vault has no visible entry point: tapping the already open Settings tab five times
    // in a row is the only way in.
    var taps by remember { mutableIntStateOf(0) }
    var lastTap by remember { mutableLongStateOf(0L) }
    val selecting = selection.isNotEmpty()
    // While files are selected, back leaves the selection rather than the album.
    BackHandler(selecting) { onClearSelection() }
    BackHandler(album != null && !selecting) { onAlbum(null) }
    Scaffold(
        containerColor = palette.backdropBottom,
        topBar = {
            AnimatedContent(
                targetState = selecting,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(140)) using null },
                label = "topBar"
            ) { inSelection ->
                if (inSelection) {
                    TopAppBar(
                        title = { Text("${selection.size} выбрано", fontWeight = FontWeight.SemiBold) },
                        navigationIcon = {
                            IconButton(onClick = onClearSelection) { Icon(Icons.Default.Close, "Снять выбор") }
                        },
                        actions = {
                            IconButton(onClick = onSelectionShare) { Icon(Icons.Default.Share, "Поделиться") }
                            IconButton(onClick = onSelectionFavorite) { Icon(Icons.Default.Favorite, "Избранное") }
                            // Present only while the vault is unlocked, so the feature stays hidden.
                            onSelectionHide?.let { hide ->
                                IconButton(onClick = hide) { Icon(Icons.Default.Lock, "Скрыть") }
                            }
                            IconButton(onClick = onSelectionTrash) { Icon(Icons.Default.Delete, "Удалить") }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = palette.chrome,
                            titleContentColor = palette.accent,
                            actionIconContentColor = palette.accent,
                            navigationIconContentColor = palette.accent
                        )
                    )
                } else {
                    TopAppBar(title = { Column {
                        Text(if (album != null) visible.firstOrNull()?.album ?: "Альбом" else listOf("Фотографии", "Альбомы", "Избранное", "Настройки")[tab], fontWeight = FontWeight.SemiBold)
                        if (tab != 3) Text("${visible.size} файлов", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    } }, navigationIcon = {
                        if (album != null) IconButton(onClick = { onAlbum(null) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                    }, actions = {
                        if (tab != 3) {
                            IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, "Обновить") }
                            Box {
                                IconButton(onClick = { sortMenu = true }) { Icon(sortIcon(), "Сортировка") }
                                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                    SortOrder.entries.forEach { order -> DropdownMenuItem(text = { Text(order.label) }, onClick = { vm.sort(order); sortMenu = false }) }
                                }
                            }
                        }
                    }, colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.chrome))
                }
            }
        },
        bottomBar = { NavigationBar(containerColor = palette.chrome) {
            val labels = listOf("Фото", "Альбомы", "Избранное", "Настройки")
            labels.forEachIndexed { index, label ->
                NavigationBarItem(selected = tab == index, onClick = {
                    if (index == SETTINGS_TAB && tab == SETTINGS_TAB) {
                        val now = System.currentTimeMillis()
                        taps = if (now - lastTap < TAP_WINDOW_MS) taps + 1 else 1
                        lastTap = now
                        val left = VAULT_TAPS - taps
                        when {
                            left <= 0 -> { taps = 0; onVault() }
                            left <= 2 -> Toast.makeText(context, "Ещё $left", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        taps = 0
                        onTab(index)
                    }
                }, label = { Text(label) }, colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = palette.accent, selectedTextColor = palette.accent,
                    unselectedIconColor = palette.muted, unselectedTextColor = palette.muted,
                    indicatorColor = palette.accent.copy(alpha = .16f)
                ), icon = {
                    when (index) {
                        0 -> Icon(photosIcon(), label)
                        1 -> Icon(albumsIcon(), label)
                        2 -> Icon(Icons.Default.Favorite, label)
                        else -> Icon(Icons.Default.Settings, label)
                    }
                })
            }
        } }
    ) { padding ->
        AnimatedContent(
            targetState = tab to album,
            transitionSpec = {
                (fadeIn(tween(240, delayMillis = 60)) + scaleIn(initialScale = 0.97f, animationSpec = tween(240, delayMillis = 60)))
                    .togetherWith(fadeOut(tween(140)) + scaleOut(targetScale = 1.02f, animationSpec = tween(140))) using null
            },
            label = "tab"
        ) { (currentTab, currentAlbum) ->
            // The active tab reuses the list the caller already built; only the tab animating
            // out needs its own.
            val items = if (currentTab == tab && currentAlbum == album) visible
                else mediaFor(state, currentTab, currentAlbum)
            Column(Modifier.fillMaxSize().padding(padding)) {
                when {
                    currentTab == 3 -> SettingsPage(state, vm, onAccess)
                    state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    state.error != null -> EmptyPage("Не удалось загрузить", state.error!!, "Повторить", vm::refresh)
                    !state.canRead -> EmptyPage("Фото и видео", "Разреши доступ, чтобы увидеть фотографии и видео на телефоне.", "Разрешить доступ", onAccess)
                    else -> {
                        if (state.partial) TextButton(onClick = onAccess) { Text("Выбрать ещё фото и видео") }
                        if (items.isEmpty()) EmptyPage(
                            if (currentTab == 2) "Пока нет избранного" else "Здесь пока пусто",
                            if (currentTab == 2) "Нажми сердечко при просмотре фотографии." else "Доступные фотографии появятся здесь.",
                            "Обновить", vm::refresh)
                        else if (currentTab == 1 && currentAlbum == null) AlbumGrid(items, vm, onAlbum)
                        else PhotoGrid(
                            items, state, vm, selection, onToggle,
                            onColumns = vm::columns, onOpen = { onOpen(it.key) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumGrid(media: List<GalleryMedia>, vm: GalleryViewModel, onAlbum: (String?) -> Unit) {
    val palette = LocalGalleryPalette.current
    val albums = remember(media) { media.groupBy { it.albumKey }.values.toList() }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(100.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().background(palette.backdrop)
    ) {
        items(albums, key = { it.first().albumKey }) { items ->
            Column(Modifier.animateItem().clickable { onAlbum(items.first().albumKey) }) {
                Thumbnail(items.first(), false, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)), vm)
                Text(items.first().album, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                Text("${items.size} файлов", style = MaterialTheme.typography.labelSmall, color = palette.muted)
            }
        }
    }
}

/** Gap between thumbnails; denser grids get a slightly tighter gap so tiles stay readable. */
private fun gridGap(columns: Int) = when (columns) {
    2 -> 10
    3 -> 8
    4 -> 6
    else -> 5
}

/**
 * Two-finger pinch on the photo grid: spreading fingers enlarges photos (fewer columns),
 * pinching shrinks them (more columns). Events are read on the initial pass and consumed
 * only while two fingers actually scale, so one-finger scrolling still reaches the grid.
 */
private suspend fun PointerInputScope.detectGridPinch(onStep: (Int) -> Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var accumulated = 1f
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.none { it.pressed }) break
            if (event.changes.count { it.pressed } < 2) continue
            val zoom = event.calculateZoom()
            if (zoom == 1f) continue
            accumulated *= zoom
            event.changes.forEach { it.consume() }
            when {
                accumulated >= 1.3f -> { onStep(-1); accumulated = 1f }
                accumulated <= 0.77f -> { onStep(1); accumulated = 1f }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoGrid(
    media: List<GalleryMedia>, state: GalleryState, vm: GalleryViewModel,
    selection: Set<String>, onToggle: (GalleryMedia) -> Unit,
    onColumns: (Int) -> Unit, onOpen: (GalleryMedia) -> Unit
) {
    val palette = LocalGalleryPalette.current
    val groups = remember(media, state.sort) {
        if (state.sort == SortOrder.NAME) linkedMapOf("По названию" to media)
        else media.groupBy { Instant.ofEpochMilli(it.date).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale.forLanguageTag("ru"))) }
    }
    var hint by remember { mutableStateOf(false) }
    // The gesture drives a local count so a fast pinch is not thrown away while the stored
    // value makes its round trip through DataStore; stored changes flow back in.
    val columns = remember { mutableIntStateOf(state.columns) }
    LaunchedEffect(state.columns) { columns.intValue = state.columns }
    LaunchedEffect(columns.intValue, hint) { if (hint) { delay(900); hint = false } }
    val motion = spring<androidx.compose.ui.unit.Dp>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    val gap by animateDpAsState(gridGap(columns.intValue).dp, motion, label = "gap")
    val corner by animateDpAsState((gridGap(columns.intValue) + 4).dp, motion, label = "corner")
    val applyColumns by rememberUpdatedState(onColumns)
    Box(Modifier.fillMaxSize().background(palette.backdrop).pointerInput(Unit) {
        detectGridPinch { step ->
            val next = (columns.intValue + step).coerceIn(MIN_COLUMNS, MAX_COLUMNS)
            if (next != columns.intValue) { columns.intValue = next; applyColumns(next); hint = true }
        }
    }) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns.intValue),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
            contentPadding = PaddingValues(start = gap, end = gap, top = 4.dp, bottom = 16.dp)
        ) {
            groups.forEach { (date, items) ->
                item(key = "date:$date", span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.animateItem().padding(start = 4.dp, top = 16.dp, bottom = 10.dp)) {
                        Text(date, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("${items.size} файлов", style = MaterialTheme.typography.labelSmall, color = palette.muted)
                    }
                }
                items(items, key = { it.key }) { media ->
                    Thumbnail(
                        media, media.key in state.favorites,
                        Modifier.animateItem().aspectRatio(1f).clip(RoundedCornerShape(corner))
                            .combinedClickable(
                                // Outside selection a tap opens; inside it toggles. A long press
                                // always starts or extends the selection.
                                onClick = { if (selection.isEmpty()) onOpen(media) else onToggle(media) },
                                onLongClick = { onToggle(media) }
                            ),
                        vm, selected = media.key in selection)
                }
            }
        }
        AnimatedVisibility(hint, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)) {
            Text("${columns.intValue} в ряд",
                Modifier.clip(CircleShape).background(palette.chrome.copy(alpha = .92f)).padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun Thumbnail(
    media: GalleryMedia, favorite: Boolean, modifier: Modifier,
    vm: GalleryViewModel? = null, selected: Boolean = false
) {
    val palette = LocalGalleryPalette.current
    // Videos come from MediaStore's own thumbnail cache; decoding a frame out of the original file
    // on every scroll is what made video tiles slow. Photos stay with Coil, which downsamples them
    // cheaply, and Coil remains the fallback when MediaStore has no thumbnail to give.
    val preview by produceState<ImageBitmap?>(null, media.key, vm) {
        value = if (media.video && vm != null) vm.videoThumbnail(media) else null
    }
    val chosen by animateFloatAsState(if (selected) 1f else 0f, tween(180), label = "selected")
    Box(modifier.background(palette.card)) {
        val shrink = Modifier.fillMaxSize().graphicsLayer {
            val factor = 1f - 0.12f * chosen
            scaleX = factor
            scaleY = factor
        }
        preview?.let { bitmap ->
            Image(bitmap, media.name, shrink, contentScale = ContentScale.Crop)
        } ?: AsyncImage(model = media.uri, contentDescription = media.name, contentScale = ContentScale.Crop, modifier = shrink)
        if (media.video) Text(formatDuration(media.duration), modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = .65f)).padding(horizontal = 5.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
        if (favorite) Icon(Icons.Default.Favorite, "Избранное", Modifier.align(Alignment.TopEnd).padding(6.dp).size(16.dp), tint = Color.White)
        if (chosen > 0f) {
            Box(Modifier.fillMaxSize().background(palette.accent.copy(alpha = .28f * chosen)))
            Box(
                Modifier.align(Alignment.TopStart).padding(6.dp).size(20.dp).clip(CircleShape)
                    .background(palette.accent.copy(alpha = chosen)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = palette.onAccent)
            }
        }
    }
}

@Composable
private fun EmptyPage(title: String, subtitle: String, action: String, onAction: () -> Unit) {
    val palette = LocalGalleryPalette.current
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(photosIcon(), null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(20.dp)); Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp)); Text(subtitle, color = palette.muted)
        Spacer(Modifier.height(20.dp)); Button(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun SettingsPage(state: GalleryState, vm: GalleryViewModel, onAccess: () -> Unit) {
    val palette = LocalGalleryPalette.current
    val context = LocalContext.current
    val photos = state.media.count { !it.video }
    val videos = state.media.count { it.video }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LibraryOverview(photos, videos, state.favorites.size)
        SettingsCard("Сетка", gridIcon()) {
            Text("Размер плиток. В самой ленте это же меняется щипком двумя пальцами.",
                style = MaterialTheme.typography.bodySmall, color = palette.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                (MIN_COLUMNS..MAX_COLUMNS).forEach { count ->
                    GridOption(count, state.columns == count, Modifier.weight(1f)) { vm.columns(count) }
                }
            }
        }
        SettingsCard("Оформление", paletteIcon()) {
            Text("Цвет меняет фон ленты, панели и выделение целиком, а не только акцент.",
                style = MaterialTheme.typography.bodySmall, color = palette.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GalleryPalettes.forEach { option ->
                    ThemeOption(option, state.theme == option.id, Modifier.weight(1f)) { vm.theme(option.id) }
                }
            }
        }
        SettingsCard("Сортировка", sortIcon()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SortOrder.entries.forEach { order ->
                    ChoiceRow(order.label, state.sort == order) { vm.sort(order) }
                }
            }
        }
        SettingsCard("Доступ к медиа", rememberVectorPainter(Icons.Default.Lock)) {
            AccessStatus(state)
            ActionRow(photosIcon(), "Доступ к фото и видео",
                if (state.partial) "Выбрать больше файлов" else "Запросить у Android", onAccess)
            ActionRow(rememberVectorPainter(Icons.Default.Settings), "Системные разрешения", "Открыть настройки приложения") {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun LibraryOverview(photos: Int, videos: Int, favorites: Int) {
    val palette = LocalGalleryPalette.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
        .background(palette.accentWash)
        .border(1.dp, palette.border, RoundedCornerShape(26.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Icon(photosIcon(), null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
            }
            Column {
                Text("Галерея", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Всё хранится только на телефоне", style = MaterialTheme.typography.bodySmall, color = palette.muted)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Фото", photos, Modifier.weight(1f))
            Stat("Видео", videos, Modifier.weight(1f))
            Stat("Избранное", favorites, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(label: String, value: Int, modifier: Modifier) {
    val palette = LocalGalleryPalette.current
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Color(0x14FFFFFF)).padding(vertical = 12.dp, horizontal = 10.dp)) {
        AnimatedContent(value, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "stat") { shown ->
            Text("$shown", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SettingsCard(title: String, icon: Painter, content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalGalleryPalette.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(palette.card)
        .border(1.dp, palette.border, RoundedCornerShape(22.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = palette.muted, letterSpacing = 1.sp)
        }
        content()
    }
}

/** Grid density option drawn as a miniature preview of the resulting layout. */
@Composable
private fun GridOption(count: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val palette = LocalGalleryPalette.current
    val accent = MaterialTheme.colorScheme.primary
    val fill by animateColorAsState(if (selected) accent.copy(alpha = .14f) else Color(0x0FFFFFFF), tween(220), label = "fill")
    val edge by animateColorAsState(if (selected) accent else Color(0x1AFFFFFF), tween(220), label = "edge")
    val tile by animateColorAsState(if (selected) accent else Color(0x33FFFFFF), tween(220), label = "tile")
    Column(modifier
        .clip(RoundedCornerShape(16.dp))
        .background(fill)
        .border(1.dp, edge, RoundedCornerShape(16.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                    repeat(count) {
                        Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(2.dp)).background(tile))
                    }
                }
            }
        }
        Text("$count", style = MaterialTheme.typography.labelMedium, color = if (selected) accent else palette.muted)
    }
}

/** A swatch of the theme's own backdrop with its accent on top. */
@Composable
private fun ThemeOption(option: GalleryPalette, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val palette = LocalGalleryPalette.current
    val edge by animateColorAsState(if (selected) option.accent else palette.border, tween(220), label = "themeEdge")
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp))
                .background(option.backdrop).border(if (selected) 2.dp else 1.dp, edge, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(option.accent))
        }
        Text(
            option.label, style = MaterialTheme.typography.labelSmall, maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) option.accent else palette.muted
        )
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val fill by animateColorAsState(if (selected) accent.copy(alpha = .12f) else Color.Transparent, tween(220), label = "choice")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(fill)
        .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = if (selected) accent else MaterialTheme.colorScheme.onSurface)
        AnimatedVisibility(selected, enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.6f, animationSpec = tween(200)), exit = fadeOut(tween(120))) {
            Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = accent)
        }
    }
}

@Composable
private fun ActionRow(icon: Painter, title: String, subtitle: String, onClick: () -> Unit) {
    val palette = LocalGalleryPalette.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x14FFFFFF)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = palette.muted)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp), tint = palette.muted)
    }
}

@Composable
private fun AccessStatus(state: GalleryState) {
    val palette = LocalGalleryPalette.current
    val (text, target) = when {
        state.partial -> "Выбранные файлы" to palette.warning
        state.canRead -> "Полный доступ к фото и видео" to palette.accent
        else -> "Доступ не выдан" to palette.danger
    }
    val color by animateColorAsState(target, tween(260), label = "access")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.clip(CircleShape).background(color.copy(alpha = .12f)).padding(horizontal = 12.dp, vertical = 7.dp)) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        AnimatedContent(text, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "accessText") { shown ->
            Text(shown, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}
