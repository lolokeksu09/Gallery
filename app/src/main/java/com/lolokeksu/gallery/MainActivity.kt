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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

/** Tab, album and sort filtering in one place so every screen derives the same list. */
private fun mediaFor(state: GalleryState, tab: Int, album: String?): List<GalleryMedia> {
    val filtered = state.media.filter {
        (tab != 2 || state.isFavorite(it)) && (album == null || it.albumKey == album)
    }
    return when (state.sort) {
        SortOrder.NEWEST -> filtered.sortedByDescending { it.date }
        SortOrder.OLDEST -> filtered.sortedBy { it.date }
        SortOrder.NAME -> filtered.sortedBy { it.name.lowercase() }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun GalleryApp(vm: GalleryViewModel = viewModel(), vaultVm: VaultViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val vault by vaultVm.state.collectAsStateWithLifecycle()
    val palette = LocalGalleryPalette.current
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var vaultOpen by rememberSaveable { mutableStateOf(false) }
    var trashOpen by rememberSaveable { mutableStateOf(false) }
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
    // Both trash actions hand the work back to Android, which asks for its own confirmation.
    val trashRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        // Whatever the outcome, the listing is stale afterwards.
        vm.refresh()
        vm.loadTrash()
    }
    fun restoreFromTrash(items: List<GalleryMedia>) {
        if (items.isEmpty()) return
        try {
            val intent = MediaStore.createTrashRequest(context.contentResolver, items.map { it.uri }, false)
            trashRequest.launch(IntentSenderRequest.Builder(intent.intentSender).build())
        } catch (_: Exception) { Toast.makeText(context, "Не удалось восстановить", Toast.LENGTH_SHORT).show() }
    }
    fun purgeFromTrash(items: List<GalleryMedia>) {
        if (items.isEmpty()) return
        try {
            val intent = MediaStore.createDeleteRequest(context.contentResolver, items.map { it.uri })
            trashRequest.launch(IntentSenderRequest.Builder(intent.intentSender).build())
        } catch (_: Exception) { Toast.makeText(context, "Не удалось удалить", Toast.LENGTH_SHORT).show() }
    }
    LaunchedEffect(trashOpen) { if (trashOpen) vm.loadTrash() else vm.clearTrashList() }

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
        // This effect re-runs after an activity recreation, and the pending batch lives in the
        // view model, so without the flag a rotation would raise a second dialog for the same
        // files: cancelling one while confirming the other loses them from both places.
        if (pending.isEmpty() || vaultVm.deleteRequested) return@LaunchedEffect
        vaultVm.markDeleteRequested()
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
    val visible = remember(state.media, state.sort, tab, album,
        if (tab == 2) state.favorites to state.legacyFavorites else null) {
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

    // Which photograph the viewer is showing right now, which is not the same as the one it was
    // opened on. Kept out of openKey on purpose: the block above rebuilds ViewerRequest when
    // that changes, which would remount the pager mid-swipe. This only decides which tile
    // steps aside for the transform.
    var viewerPage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(openKey) { if (openKey == null) viewerPage = null }

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

    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransition provides this) {
            Box(Modifier.fillMaxSize().background(palette.backdrop)) {
                GalleryHome(state, vm, tab, album, visible, viewerPage,
                    onTab = { tab = it; album = null; selectionList = emptyList() },
                    onAlbum = { album = it; selectionList = emptyList() },
                    onOpen = { selected = it }, onAccess = { access() }, onVault = { vaultOpen = true },
                    onTrash = { trashOpen = true },
                    selection = selection,
                    onToggle = { media ->
                        selectionList = if (media.key in selection) selectionList - media.key else selectionList + media.key
                    },
                    onClearSelection = { selectionList = emptyList() },
                    onSelectionShare = { shareChosen() },
                    onSelectionTrash = { trashChosen() },
                    onSelectionFavorite = { vm.favorite(chosen); selectionList = emptyList() },
                    onSelectionHide = if (vault.unlocked) ({ vaultVm.hide(chosen) }) else null)
                AnimatedVisibility(
                    visible = openKey != null,
                    enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.94f, animationSpec = tween(220)),
                    exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.94f, animationSpec = tween(180))
                ) {
                    viewer?.let { request ->
                        key(request.session) {
                            MediaViewer(request.media, request.key, isFavorite = { state.isFavorite(it) },
                                animatedScope = this@AnimatedVisibility, onPage = { viewerPage = it },
                                onClose = { selected = null }, onFavorite = vm::favorite,
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
                    visible = trashOpen,
                    enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.94f, animationSpec = tween(220)),
                    exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.94f, animationSpec = tween(180))
                ) {
                    TrashScreen(
                        items = state.trash, loading = state.trashLoading, vm = vm,
                        onClose = { trashOpen = false },
                        onRestore = { restoreFromTrash(it) },
                        onDeleteForever = { purgeFromTrash(it) }
                    )
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
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .72f))
                            // A scrim that does not take the taps is not a scrim: without this the grid
                            // and the selection bar stay live while the files are being encrypted.
                            .pointerInput(Unit) { detectTapGestures { } },
                        contentAlignment = Alignment.Center
                    ) {
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
    viewerPage: String?,
    onTab: (Int) -> Unit, onAlbum: (String?) -> Unit, onOpen: (String) -> Unit, onAccess: () -> Unit,
    onVault: () -> Unit, onTrash: () -> Unit,
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
    // Material's own behaviour for a bar over black: transparent while the content starts below
    // it, taking the tonal container tone once anything scrolls underneath.
    val barScroll = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(barScroll.nestedScrollConnection),
        containerColor = palette.backdrop,
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
                            containerColor = palette.elevated,
                            titleContentColor = palette.accent,
                            actionIconContentColor = palette.accent,
                            navigationIconContentColor = palette.accent
                        )
                    )
                } else {
                    TopAppBar(title = {
                        // One line. The file count moved out: it belongs where it is acted on —
                        // an album tile, the trash — not over every screen in the application.
                        Text(
                            if (album != null) visible.firstOrNull()?.album ?: "Альбом"
                            else listOf("Фотографии", "Альбомы", "Избранное", "Настройки")[tab],
                            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }, navigationIcon = {
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
                    }, scrollBehavior = barScroll, colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = palette.backdrop,
                        scrolledContainerColor = palette.chrome
                    ))
                }
            }
        },
        bottomBar = {
            GalleryNavBar(tab) { index ->
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
            }
        }
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
                    currentTab == 3 -> SettingsPage(state, vm, onAccess, onTrash)
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
                            items, state, vm, selection, onToggle, viewerPage,
                            onColumns = vm::columns, onOpen = { onOpen(it.key) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Material's own navigation bar is 80dp before the gesture inset, and with a label, a full-width
 * tinted slab and a 64x32 indicator behind every icon it was the heaviest thing on the screen.
 * This one is 56dp, sits on the black page with no slab, and gives the indicator only to the tab
 * that is actually selected.
 */
@Composable
private fun GalleryNavBar(tab: Int, onSelect: (Int) -> Unit) {
    val palette = LocalGalleryPalette.current
    val labels = listOf("Фото", "Альбомы", "Избранное", "Настройки")
    Row(
        Modifier.fillMaxWidth().background(palette.backdrop)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(56.dp).selectableGroup(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        labels.forEachIndexed { index, label ->
            val selected = tab == index
            val pill by animateColorAsState(
                if (selected) palette.accent.copy(alpha = .18f) else Color.Transparent,
                tween(220), label = "navPill"
            )
            val tint by animateColorAsState(
                if (selected) palette.accent else palette.muted, tween(220), label = "navTint"
            )
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .selectable(selected = selected, role = Role.Tab) { onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier.clip(CircleShape).background(pill)
                        .padding(horizontal = 13.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val icon = when (index) {
                        0 -> photosIcon()
                        1 -> albumsIcon()
                        2 -> rememberVectorPainter(Icons.Default.Favorite)
                        else -> rememberVectorPainter(Icons.Default.Settings)
                    }
                    Icon(icon, label, Modifier.size(20.dp), tint = tint)
                }
                Text(
                    label, style = MaterialTheme.typography.labelSmall, color = tint,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
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
                Text(fileCount(items.size), style = MaterialTheme.typography.labelSmall, color = palette.muted)
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
    selection: Set<String>, onToggle: (GalleryMedia) -> Unit, viewerPage: String?,
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
    val haptics = LocalHapticFeedback.current
    val gridState = rememberLazyGridState()
    // Every key in the grid says which day it belongs to: headers are "date:", tiles are their
    // own media key. That is enough to name the position without a second list.
    val dateOfKey = remember(groups) {
        buildMap {
            groups.forEach { (date, items) ->
                put("date:$date", date)
                items.forEach { put(it.key, date) }
            }
        }
    }
    // LazyVerticalGrid has no sticky headers — foundation only has them for lists — so the date
    // of the current position rides above the grid while it moves and fades once it settles.
    val scrolling = gridState.isScrollInProgress
    var dateVisible by remember { mutableStateOf(false) }
    LaunchedEffect(scrolling) {
        if (scrolling) dateVisible = true else { delay(800); dateVisible = false }
    }
    val currentDate by remember(dateOfKey) {
        derivedStateOf { gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.key?.let(dateOfKey::get) }
    }
    Box(Modifier.fillMaxSize().background(palette.backdrop).pointerInput(Unit) {
        detectGridPinch { step ->
            val next = (columns.intValue + step).coerceIn(MIN_COLUMNS, MAX_COLUMNS)
            if (next != columns.intValue) {
                columns.intValue = next; applyColumns(next); hint = true
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(columns.intValue),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
            contentPadding = PaddingValues(start = gap, end = gap, top = 4.dp, bottom = 16.dp)
        ) {
            groups.forEach { (date, items) ->
                item(key = "date:$date", span = { GridItemSpan(maxLineSpan) }) {
                    // The date alone. A count under every date repeated the same two-storey
                    // pattern as the screen title and said nothing anyone acts on.
                    Text(
                        date, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.animateItem().padding(start = 4.dp, top = 18.dp, bottom = 10.dp)
                    )
                }
                items(items, key = { it.key }) { media ->
                    Thumbnail(
                        media, state.isFavorite(media),
                        Modifier.animateItem().aspectRatio(1f)
                            .sharedTile(media.key, hidden = media.key == viewerPage)
                            .clip(RoundedCornerShape(corner))
                            .combinedClickable(
                                // Outside selection a tap opens; inside it toggles. A long press
                                // always starts or extends the selection.
                                onClick = { if (selection.isEmpty()) onOpen(media) else onToggle(media) },
                                onLongClick = {
                                    // The press that starts a selection should be felt, not just seen.
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggle(media)
                                }
                            ),
                        vm, selected = media.key in selection)
                }
            }
        }
        // The density hint wins the spot while it is up: both are the same pill and stacking them
        // would put one on top of the other.
        AnimatedVisibility(dateVisible && !hint && currentDate != null,
            enter = fadeIn(tween(160)), exit = fadeOut(tween(260)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)) {
            Text(currentDate.orEmpty(),
                Modifier.clip(CircleShape).background(palette.elevated).padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        AnimatedVisibility(hint, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)) {
            Text("${columns.intValue} в ряд",
                Modifier.clip(CircleShape).background(palette.elevated).padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun Thumbnail(
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
private fun SettingsPage(
    state: GalleryState, vm: GalleryViewModel, onAccess: () -> Unit, onTrash: () -> Unit
) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        SettingsSection("Вид") {
            SettingsLabel("Размер плиток")
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                (MIN_COLUMNS..MAX_COLUMNS).forEach { count ->
                    GridOption(count, state.columns == count, Modifier.weight(1f)) { vm.columns(count) }
                }
            }
            SettingsDivider()
            SettingsLabel("Тема")
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GalleryPalettes.forEach { option ->
                    ThemeOption(option, state.theme == option.id, Modifier.weight(1f)) { vm.theme(option.id) }
                }
            }
        }
        SettingsSection("Сортировка") {
            SortOrder.entries.forEach { order ->
                ChoiceRow(order.label, state.sort == order) { vm.sort(order) }
            }
        }
        SettingsSection("Файлы") {
            ActionRow(
                rememberVectorPainter(Icons.Default.Delete), "Корзина",
                "Удалённое хранится 30 дней", onTrash
            )
        }
        SettingsSection("Доступ") {
            AccessStatus(state)
            ActionRow(
                photosIcon(), "Фото и видео",
                if (state.partial) "Выбрать больше файлов" else "Запросить у Android", onAccess
            )
            ActionRow(
                rememberVectorPainter(Icons.Default.Settings), "Разрешения приложения",
                "Открыть системные настройки"
            ) {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                )
            }
        }
    }
}

/**
 * A Material settings group: a quiet accent label, then the rows on the tonal container. No icon,
 * no uppercase, no paragraph explaining the control — a setting that needs a paragraph is the
 * wrong control.
 */
@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalGalleryPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title, style = MaterialTheme.typography.titleSmall, color = palette.accent,
            fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp)
        )
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(palette.card)
                .padding(vertical = 8.dp, horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsLabel(text: String) {
    Text(
        text, style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
        color = LocalGalleryPalette.current.border
    )
}

/** Grid density option drawn as a miniature preview of the resulting layout. */
@Composable
private fun GridOption(count: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val palette = LocalGalleryPalette.current
    val accent = MaterialTheme.colorScheme.primary
    val fill by animateColorAsState(if (selected) accent.copy(alpha = .16f) else palette.elevated, tween(220), label = "fill")
    val edge by animateColorAsState(if (selected) accent else Color.Transparent, tween(220), label = "edge")
    val tile by animateColorAsState(if (selected) accent else palette.muted.copy(alpha = .45f), tween(220), label = "tile")
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

/**
 * Every theme now sits on the same black page, so the swatch shows what actually differs: the
 * accent, on that theme's own container tone.
 */
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
                .background(option.elevated).border(if (selected) 2.dp else 1.dp, edge, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(20.dp).clip(CircleShape).background(option.accent))
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
    val fill by animateColorAsState(if (selected) accent.copy(alpha = .16f) else Color.Transparent, tween(220), label = "choice")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(fill)
        .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
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
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
        .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(palette.elevated), contentAlignment = Alignment.Center) {
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
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        AnimatedContent(text, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "accessText") { shown ->
            Text(shown, style = MaterialTheme.typography.bodyMedium, color = color)
        }
    }
}
