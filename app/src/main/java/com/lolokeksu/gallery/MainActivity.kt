package com.lolokeksu.gallery

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.*
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.Brush
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

/** Minimum and maximum number of grid columns reachable with the pinch gesture. */
const val MIN_COLUMNS = 2
const val MAX_COLUMNS = 5

/**
 * Tinted backdrop for the media grids. Surfaces stay near black for AMOLED, but the gaps
 * between photos carry a faint colour instead of reading as a flat black sheet.
 */
private val GridBackground = Brush.verticalGradient(
    listOf(Color(0xFF17241E), Color(0xFF0D1411), Color(0xFF0A100D))
)
private val CardColor = Color(0xFF0C0E0D)
private val CardBorder = Color(0xFF1D211F)
private val Muted = Color(0xFF8C948F)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFB3E5CB), onPrimary = Color(0xFF123127),
                background = Color.Black, surface = Color.Black,
                surfaceVariant = Color(0xFF181B19), onSurface = Color(0xFFEAEFEC)
            )) { GalleryApp() }
        }
    }
}

@Composable private fun photosIcon(): Painter = painterResource(R.drawable.ic_photos)
@Composable private fun albumsIcon(): Painter = painterResource(R.drawable.ic_albums)
@Composable private fun sortIcon(): Painter = painterResource(R.drawable.ic_sort)
@Composable private fun gridIcon(): Painter = painterResource(R.drawable.ic_grid)

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
fun GalleryApp(vm: GalleryViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var album by rememberSaveable { mutableStateOf<String?>(null) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
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
        if (result.resultCode == Activity.RESULT_OK) selected = null
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.refresh() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val visible = remember(state.media, state.favorites, state.sort, tab, album) { mediaFor(state, tab, album) }
    val openKey = selected?.takeIf { key -> visible.any { it.key == key } }
    // The list and key are held while the viewer plays its exit animation.
    var viewer by remember { mutableStateOf<Pair<List<GalleryMedia>, String>?>(null) }
    if (openKey != null && viewer?.second != openKey) viewer = visible to openKey

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        GalleryHome(state, vm, tab, album, onTab = { tab = it; album = null }, onAlbum = { album = it },
            onOpen = { selected = it }, onAccess = { access() })
        AnimatedVisibility(
            visible = openKey != null,
            enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.94f, animationSpec = tween(220)),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.94f, animationSpec = tween(180))
        ) {
            viewer?.let { (items, key) ->
                MediaViewer(items, key, state.favorites, onClose = { selected = null }, onFavorite = vm::favorite,
                    onDelete = { media ->
                        try {
                            val intent = MediaStore.createDeleteRequest(context.contentResolver, listOf(media.uri))
                            deleteRequest.launch(IntentSenderRequest.Builder(intent.intentSender).build())
                        } catch (_: Exception) { Toast.makeText(context, "Не удалось запросить удаление", Toast.LENGTH_SHORT).show() }
                    })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryHome(
    state: GalleryState, vm: GalleryViewModel, tab: Int, album: String?,
    onTab: (Int) -> Unit, onAlbum: (String?) -> Unit, onOpen: (String) -> Unit, onAccess: () -> Unit
) {
    var sortMenu by remember { mutableStateOf(false) }
    val visible = remember(state.media, state.favorites, state.sort, tab, album) { mediaFor(state, tab, album) }
    BackHandler(album != null) { onAlbum(null) }
    Scaffold(
        containerColor = Color.Black,
        topBar = { TopAppBar(title = { Column {
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
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)) },
        bottomBar = { NavigationBar(containerColor = Color.Black) {
            val labels = listOf("Фото", "Альбомы", "Избранное", "Настройки")
            labels.forEachIndexed { index, label ->
                NavigationBarItem(selected = tab == index, onClick = { onTab(index) }, label = { Text(label) }, icon = {
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
            val items = remember(state.media, state.favorites, state.sort, currentTab, currentAlbum) {
                mediaFor(state, currentTab, currentAlbum)
            }
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
                        else if (currentTab == 1 && currentAlbum == null) AlbumGrid(items, onAlbum)
                        else PhotoGrid(items, state, onColumns = vm::columns, onOpen = { onOpen(it.key) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumGrid(media: List<GalleryMedia>, onAlbum: (String?) -> Unit) {
    val albums = remember(media) { media.groupBy { it.albumKey }.values.toList() }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(100.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().background(GridBackground)
    ) {
        items(albums, key = { it.first().albumKey }) { items ->
            Column(Modifier.animateItem().clickable { onAlbum(items.first().albumKey) }) {
                Thumbnail(items.first(), false, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)))
                Text(items.first().album, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                Text("${items.size} файлов", style = MaterialTheme.typography.labelSmall, color = Muted)
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

@Composable
private fun PhotoGrid(media: List<GalleryMedia>, state: GalleryState, onColumns: (Int) -> Unit, onOpen: (GalleryMedia) -> Unit) {
    val groups = remember(media, state.sort) {
        if (state.sort == SortOrder.NAME) linkedMapOf("По названию" to media)
        else media.groupBy { Instant.ofEpochMilli(it.date).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale.forLanguageTag("ru"))) }
    }
    var hint by remember { mutableStateOf(false) }
    LaunchedEffect(state.columns, hint) { if (hint) { delay(900); hint = false } }
    val motion = spring<androidx.compose.ui.unit.Dp>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    val gap by animateDpAsState(gridGap(state.columns).dp, motion, label = "gap")
    val corner by animateDpAsState((gridGap(state.columns) + 4).dp, motion, label = "corner")
    // The gesture detector is started once, so the current values are read through updated state.
    val columns by rememberUpdatedState(state.columns)
    val applyColumns by rememberUpdatedState(onColumns)
    Box(Modifier.fillMaxSize().background(GridBackground).pointerInput(Unit) {
        detectGridPinch { step ->
            val next = (columns + step).coerceIn(MIN_COLUMNS, MAX_COLUMNS)
            if (next != columns) { applyColumns(next); hint = true }
        }
    }) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(state.columns),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
            contentPadding = PaddingValues(start = gap, end = gap, top = 4.dp, bottom = 16.dp)
        ) {
            groups.forEach { (date, items) ->
                item(key = "date:$date", span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.animateItem().padding(start = 4.dp, top = 16.dp, bottom = 10.dp)) {
                        Text(date, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("${items.size} файлов", style = MaterialTheme.typography.labelSmall, color = Muted)
                    }
                }
                items(items, key = { it.key }) { media ->
                    Thumbnail(media, media.key in state.favorites,
                        Modifier.animateItem().aspectRatio(1f).clip(RoundedCornerShape(corner)).clickable { onOpen(media) })
                }
            }
        }
        AnimatedVisibility(hint, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)) {
            Text("${state.columns} в ряд",
                Modifier.clip(CircleShape).background(Color(0xE61A1E1C)).padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun Thumbnail(media: GalleryMedia, favorite: Boolean, modifier: Modifier) {
    Box(modifier.background(Color(0xFF141715))) {
        AsyncImage(model = media.uri, contentDescription = media.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (media.video) Text(formatDuration(media.duration), modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = .65f)).padding(horizontal = 5.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
        if (favorite) Icon(Icons.Default.Favorite, "Избранное", Modifier.align(Alignment.TopEnd).padding(6.dp).size(16.dp), tint = Color.White)
    }
}

@Composable
private fun EmptyPage(title: String, subtitle: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(photosIcon(), null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(20.dp)); Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp)); Text(subtitle, color = Muted)
        Spacer(Modifier.height(20.dp)); Button(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun SettingsPage(state: GalleryState, vm: GalleryViewModel, onAccess: () -> Unit) {
    val context = LocalContext.current
    val photos = state.media.count { !it.video }
    val videos = state.media.count { it.video }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LibraryOverview(photos, videos, state.favorites.size)
        SettingsCard("Сетка", gridIcon()) {
            Text("Размер плиток. В самой ленте это же меняется щипком двумя пальцами.",
                style = MaterialTheme.typography.bodySmall, color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                (MIN_COLUMNS..MAX_COLUMNS).forEach { count ->
                    GridOption(count, state.columns == count, Modifier.weight(1f)) { vm.columns(count) }
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
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
        .background(Brush.verticalGradient(listOf(Color(0xFF15211B), Color(0xFF0B0D0C))))
        .border(1.dp, CardBorder, RoundedCornerShape(26.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Icon(photosIcon(), null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
            }
            Column {
                Text("Галерея", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Всё хранится только на телефоне", style = MaterialTheme.typography.bodySmall, color = Muted)
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
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Color(0x14FFFFFF)).padding(vertical = 12.dp, horizontal = 10.dp)) {
        AnimatedContent(value, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "stat") { shown ->
            Text("$shown", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SettingsCard(title: String, icon: Painter, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(CardColor)
        .border(1.dp, CardBorder, RoundedCornerShape(22.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = Muted, letterSpacing = 1.sp)
        }
        content()
    }
}

/** Grid density option drawn as a miniature preview of the resulting layout. */
@Composable
private fun GridOption(count: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
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
        Text("$count", style = MaterialTheme.typography.labelMedium, color = if (selected) accent else Muted)
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
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x14FFFFFF)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Muted)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp), tint = Muted)
    }
}

@Composable
private fun AccessStatus(state: GalleryState) {
    val accent = MaterialTheme.colorScheme.primary
    val warn = Color(0xFFE5C57C)
    val (text, target) = when {
        state.partial -> "Выбранные файлы" to warn
        state.canRead -> "Полный доступ к фото и видео" to accent
        else -> "Доступ не выдан" to Color(0xFFE59A8C)
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
