package com.lolokeksu.gallery

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.text.format.Formatter
import android.view.View
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import java.text.DateFormat
import java.util.Date

/** Controls stay on screen this long before the video goes edge-to-edge full screen. */
private const val VIDEO_CHROME_TIMEOUT_MS = 5000

private fun Context.activity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@Composable
fun MediaViewer(media: List<GalleryMedia>, initialKey: String, favorites: Set<String>, onClose: () -> Unit,
    onFavorite: (String) -> Unit, onDelete: (GalleryMedia) -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val pager = rememberPagerState(initialPage = media.indexOfFirst { it.key == initialKey }.coerceAtLeast(0), pageCount = { media.size })
    var details by remember { mutableStateOf(false) }
    var zoomed by remember { mutableStateOf(false) }
    // Application chrome and the system bars: hidden together so video fills the whole screen.
    var chrome by remember { mutableStateOf(true) }
    val current = media[pager.currentPage.coerceIn(media.indices)]
    LaunchedEffect(pager.currentPage) { zoomed = false; chrome = true }
    val insets = remember(view) { context.activity()?.window?.let { WindowCompat.getInsetsController(it, view) } }
    LaunchedEffect(chrome, insets) {
        insets?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (chrome) insets?.show(WindowInsetsCompat.Type.systemBars()) else insets?.hide(WindowInsetsCompat.Type.systemBars())
    }
    DisposableEffect(insets) { onDispose { insets?.show(WindowInsetsCompat.Type.systemBars()) } }
    BackHandler { if (!chrome) chrome = true else onClose() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, key = { media[it].key }, userScrollEnabled = !zoomed, modifier = Modifier.fillMaxSize()) { index ->
            val item = media[index]
            if (item.video && index == pager.currentPage) VideoPlayer(item, chrome) { chrome = it }
            else ZoomableImage(item, onZoom = { if (index == pager.currentPage) zoomed = it }, onTap = { chrome = !chrome })
        }
        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .75f), Color.Transparent)))
                .statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = Color.White) }
                Text("${pager.currentPage + 1} / ${media.size}", style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f))
                IconButton(onClick = { details = true }) { Icon(Icons.Default.Info, "Сведения", tint = Color.White) }
            }
        }
        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Row(Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .75f))))
                .navigationBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
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
                }) { Icon(Icons.Default.Share, "Поделиться", tint = Color.White) }
                IconButton(onClick = { onDelete(current) }) { Icon(Icons.Default.DeleteOutline, "Удалить", tint = Color.White) }
            }
        }
    }
    if (details) AlertDialog(onDismissRequest = { details = false }, title = { Text("Сведения") }, text = {
        Text("${current.name}\n\n${DateFormat.getDateTimeInstance().format(Date(current.date))}\n${current.width} × ${current.height}\n${Formatter.formatFileSize(context, current.size)}\n${current.mime}\n\n${current.path}${current.name}")
    }, confirmButton = { TextButton(onClick = { details = false }) { Text("Закрыть") } })
}

@Composable
private fun ZoomableImage(media: GalleryMedia, onZoom: (Boolean) -> Unit, onTap: () -> Unit) {
    var scale by remember(media.key) { mutableFloatStateOf(1f) }
    var offset by remember(media.key) { mutableStateOf(Offset.Zero) }
    var bounds by remember { mutableStateOf(IntSize.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        val next = (scale * zoom).coerceIn(1f, 5f)
        scale = next
        offset = if (next <= 1f) Offset.Zero else Offset(
            (offset.x + pan.x).coerceIn(-bounds.width * (next - 1) / 2f, bounds.width * (next - 1) / 2f),
            (offset.y + pan.y).coerceIn(-bounds.height * (next - 1) / 2f, bounds.height * (next - 1) / 2f))
        onZoom(next > 1f)
    }
    Box(Modifier.fillMaxSize().background(Color.Black)
        .onSizeChanged { bounds = it }
        .pointerInput(media.key) { detectTapGestures(onTap = { onTap() }, onDoubleTap = {
            scale = if (scale > 1f) 1f else 2.5f; offset = Offset.Zero; onZoom(scale > 1f)
        }) }
        .transformable(state = transform, canPan = { scale > 1f }), contentAlignment = Alignment.Center) {
        AsyncImage(model = media.uri, contentDescription = media.name, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y })
    }
}

/**
 * Playback starts immediately; Media3 hides its own controls after [VIDEO_CHROME_TIMEOUT_MS],
 * and [onChrome] mirrors that so the application bars and system bars disappear at the same
 * moment. A tap brings everything back.
 */
@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun VideoPlayer(media: GalleryMedia, chrome: Boolean, onChrome: (Boolean) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val player = remember(media.key) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(media.uri)); prepare(); playWhenReady = true
        }
    }
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) player.pause() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); player.release() }
    }
    val latestChrome by rememberUpdatedState(onChrome)
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                controllerAutoShow = true
                controllerShowTimeoutMs = VIDEO_CHROME_TIMEOUT_MS
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                setBackgroundColor(android.graphics.Color.BLACK)
                setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                    latestChrome(visibility == View.VISIBLE)
                })
            }
        },
        update = { playerView ->
            playerView.player = player
            // Keep Media3 controls and application chrome in the same state after an
            // external toggle (back gesture, page change).
            if (chrome) playerView.showController() else playerView.hideController()
        },
        modifier = Modifier.fillMaxSize()
    )
}
