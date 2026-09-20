package com.lolokeksu.gallery

import android.content.ClipData
import android.content.Intent
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewer(media: List<GalleryMedia>, initialKey: String, favorites: Set<String>, onClose: () -> Unit,
    onFavorite: (String) -> Unit, onDelete: (GalleryMedia) -> Unit) {
    val context = LocalContext.current
    val pager = rememberPagerState(initialPage = media.indexOfFirst { it.key == initialKey }.coerceAtLeast(0), pageCount = { media.size })
    var details by remember { mutableStateOf(false) }
    var zoomed by remember { mutableStateOf(false) }
    val current = media[pager.currentPage.coerceIn(media.indices)]
    LaunchedEffect(pager.currentPage) { zoomed = false }
    BackHandler(onBack = onClose)
    Scaffold(containerColor = Color.Black, topBar = {
        TopAppBar(title = { Text("${pager.currentPage + 1} / ${media.size}", style = MaterialTheme.typography.titleMedium) }, navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
        }, actions = { IconButton(onClick = { details = true }) { Icon(Icons.Default.Info, "Сведения") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black))
    }, bottomBar = {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton(onClick = { onFavorite(current.key) }) { Icon(if (current.key in favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Избранное", tint = MaterialTheme.colorScheme.primary) }
            IconButton(onClick = {
                try {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = current.mime
                        putExtra(Intent.EXTRA_STREAM, current.uri)
                        clipData = ClipData.newRawUri(current.name, current.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Поделиться"))
                } catch (_: Exception) { Toast.makeText(context, "Не удалось отправить файл", Toast.LENGTH_SHORT).show() }
            }) { Icon(Icons.Default.Share, "Поделиться") }
            IconButton(onClick = { onDelete(current) }) { Icon(Icons.Default.DeleteOutline, "Удалить") }
        }
    }) { padding ->
        HorizontalPager(state = pager, key = { media[it].key }, userScrollEnabled = !zoomed, modifier = Modifier.fillMaxSize().padding(padding)) { index ->
            val item = media[index]
            if (item.video && index == pager.currentPage) VideoPlayer(item)
            else ZoomableImage(item, onZoom = { if (index == pager.currentPage) zoomed = it })
        }
    }
    if (details) AlertDialog(onDismissRequest = { details = false }, title = { Text("Сведения") }, text = {
        Text("${current.name}\n\n${DateFormat.getDateTimeInstance().format(Date(current.date))}\n${current.width} × ${current.height}\n${Formatter.formatFileSize(context, current.size)}\n${current.mime}\n\n${current.path}${current.name}")
    }, confirmButton = { TextButton(onClick = { details = false }) { Text("Закрыть") } })
}

@Composable
private fun ZoomableImage(media: GalleryMedia, onZoom: (Boolean) -> Unit) {
    var scale by remember(media.key) { mutableFloatStateOf(1f) }
    var offset by remember(media.key) { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().background(Color.Black)
        .pointerInput(media.key) { detectTapGestures(onDoubleTap = {
            scale = if (scale > 1f) 1f else 2.5f; offset = Offset.Zero; onZoom(scale > 1f)
        }) }
        .pointerInput(media.key, scale > 1f) {
            // At base scale a one-finger swipe belongs to the pager; transform detector
            // waits for gesture slop, while zoom is available using two fingers.
            detectTransformGestures { _, pan, zoom, _ ->
                val next = (scale * zoom).coerceIn(1f, 5f)
                scale = next
                offset = if (next <= 1f) Offset.Zero else Offset(
                    (offset.x + pan.x).coerceIn(-size.width * (next - 1) / 2f, size.width * (next - 1) / 2f),
                    (offset.y + pan.y).coerceIn(-size.height * (next - 1) / 2f, size.height * (next - 1) / 2f))
                onZoom(next > 1f)
            }
        }, contentAlignment = Alignment.Center) {
        AsyncImage(model = media.uri, contentDescription = media.name, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y })
    }
}

@Composable
private fun VideoPlayer(media: GalleryMedia) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val player = remember(media.key) { ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(media.uri)); prepare() } }
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) player.pause() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); player.release() }
    }
    AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = true } }, update = { it.player = player }, modifier = Modifier.fillMaxSize())
}
