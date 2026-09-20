package com.lolokeksu.gallery

import android.Manifest
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
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryApp(vm: GalleryViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var album by rememberSaveable { mutableStateOf<String?>(null) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var sortMenu by remember { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refresh() }
    fun access(video: Boolean) {
        request.launch(buildList {
            add(if (video) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_MEDIA_IMAGES)
            if (Build.VERSION.SDK_INT >= 34) add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        }.toTypedArray())
    }
    val deleteRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { vm.refresh(); selected = null }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.refresh() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val visible = remember(state.media, state.favorites, state.sort, tab, album) {
        val filtered = state.media.filter { (tab != 2 || it.key in state.favorites) && (album == null || it.albumKey == album) }
        when (state.sort) {
            SortOrder.NEWEST -> filtered.sortedByDescending { it.date }
            SortOrder.OLDEST -> filtered.sortedBy { it.date }
            SortOrder.NAME -> filtered.sortedBy { it.name.lowercase() }
        }
    }
    if (selected != null && visible.any { it.key == selected }) {
        MediaViewer(visible, selected!!, state.favorites, onClose = { selected = null }, onFavorite = vm::favorite,
            onDelete = { media ->
                try {
                    val intent = MediaStore.createDeleteRequest(context.contentResolver, listOf(media.uri))
                    deleteRequest.launch(IntentSenderRequest.Builder(intent.intentSender).build())
                } catch (_: Exception) { Toast.makeText(context, "Не удалось запросить удаление", Toast.LENGTH_SHORT).show() }
            })
        return
    }
    BackHandler(album != null) { album = null }
    Scaffold(
        containerColor = Color.Black,
        topBar = { TopAppBar(title = { Column {
            Text(if (album != null) visible.firstOrNull()?.album ?: "Альбом" else listOf("Фотографии", "Альбомы", "Избранное", "Настройки")[tab], fontWeight = FontWeight.SemiBold)
            if (tab != 3) Text("${visible.size} файлов · только на устройстве", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        } }, navigationIcon = {
            if (album != null) IconButton(onClick = { album = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
        }, actions = {
            if (tab != 3) {
                IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, "Обновить") }
                Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.Default.Sort, "Сортировка") }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        SortOrder.entries.forEach { order -> DropdownMenuItem(text = { Text(order.label) }, onClick = { vm.sort(order); sortMenu = false }) }
                    }
                }
            }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)) },
        bottomBar = { NavigationBar(containerColor = Color.Black) {
            val labels = listOf("Фото", "Альбомы", "Избранное", "Настройки")
            val icons = listOf(Icons.Default.PhotoLibrary, Icons.Default.Folder, Icons.Default.Favorite, Icons.Default.Settings)
            labels.forEachIndexed { index, label -> NavigationBarItem(selected = tab == index, onClick = { tab = index; album = null }, icon = { Icon(icons[index], label) }, label = { Text(label) }) }
        } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                tab == 3 -> SettingsPage(state, vm, onPhotos = { access(false) }, onVideos = { vm.videos(true); access(true) })
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.error != null -> EmptyPage("Не удалось загрузить", state.error!!, "Повторить", vm::refresh)
                !state.canRead -> EmptyPage("Твои фотографии. Только здесь.", "Разреши чтение фотографий. Видео подключаются отдельно. Приложение не имеет доступа к интернету.", "Открыть фотографии", { access(false) })
                else -> {
                    if (state.partial) TextButton(onClick = { access(false) }) { Text("Ограниченный доступ · выбрать ещё фотографии") }
                    if (visible.isEmpty()) EmptyPage(if (tab == 2) "Пока нет избранного" else "Здесь пока пусто", if (tab == 2) "Нажми сердечко при просмотре фотографии." else "Доступные фотографии появятся здесь.", "Обновить", vm::refresh)
                    else if (tab == 1 && album == null) {
                        val albums = remember(visible) { visible.groupBy { it.albumKey }.values.toList() }
                        LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(albums, key = { it.first().albumKey }) { items ->
                                Column(Modifier.clickable { album = items.first().albumKey }) {
                                    Thumbnail(items.first(), false, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)))
                                    Text(items.first().album, maxLines = 1, modifier = Modifier.padding(top = 8.dp))
                                    Text("${items.size} файлов", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                                }
                            }
                        }
                    } else PhotoGrid(visible, state, onOpen = { selected = it.key })
                }
            }
        }
    }
}

@Composable
private fun PhotoGrid(media: List<GalleryMedia>, state: GalleryState, onOpen: (GalleryMedia) -> Unit) {
    val groups = remember(media, state.sort) {
        if (state.sort == SortOrder.NAME) linkedMapOf("По названию" to media)
        else media.groupBy { Instant.ofEpochMilli(it.date).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale.forLanguageTag("ru"))) }
    }
    LazyVerticalGrid(columns = GridCells.Fixed(state.columns), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
        groups.forEach { (date, items) ->
            item(key = "date:$date", span = { GridItemSpan(maxLineSpan) }) { Text(date, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(14.dp)) }
            items(items, key = { it.key }) { media -> Thumbnail(media, media.key in state.favorites, Modifier.aspectRatio(1f).clickable { onOpen(media) }) }
        }
    }
}

@Composable
private fun Thumbnail(media: GalleryMedia, favorite: Boolean, modifier: Modifier) {
    Box(modifier.background(Color(0xFF141715))) {
        AsyncImage(model = media.uri, contentDescription = media.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (media.video) Text(formatDuration(media.duration), modifier = Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha = .65f)).padding(4.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
        if (favorite) Icon(Icons.Default.Favorite, "Избранное", Modifier.align(Alignment.TopEnd).padding(6.dp).size(16.dp), tint = Color.White)
    }
}

@Composable
private fun EmptyPage(title: String, subtitle: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.PhotoLibrary, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(20.dp)); Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp)); Text(subtitle, color = Color.Gray)
        Spacer(Modifier.height(20.dp)); Button(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun SettingsPage(state: GalleryState, vm: GalleryViewModel, onPhotos: () -> Unit, onVideos: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Личная. Офлайн. AMOLED.", style = MaterialTheme.typography.headlineSmall)
        Text("Нет аккаунтов, аналитики и сетевых разрешений. Избранное и настройки хранятся только в приложении.", color = Color.Gray)
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Показывать видео"); Text("Отдельное разрешение", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
            Switch(checked = state.videos, onCheckedChange = { if (it) onVideos() else vm.videos(false) })
        }
        if (state.videos) TextButton(onClick = onVideos) { Text("Настроить доступ к видео") }
        Text("Столбцов в сетке: ${state.columns}")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { (2..5).forEach { count -> FilterChip(selected = state.columns == count, onClick = { vm.columns(count) }, label = { Text("$count") }) } }
        HorizontalDivider()
        TextButton(onClick = onPhotos) { Text("Доступ к фотографиям") }
        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("Системные разрешения") }
        Text("Отключение видео скрывает их в Gallery. Чтобы отозвать разрешение, открой системные настройки.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Text("Gallery 0.1.0 · Android 13+", style = MaterialTheme.typography.labelMedium)
    }
}
