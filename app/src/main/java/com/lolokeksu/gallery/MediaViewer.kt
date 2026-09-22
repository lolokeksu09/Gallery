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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Controls stay on screen this long before the video goes edge-to-edge full screen. */
private const val VIDEO_CHROME_TIMEOUT_MS = 5000
private const val ZOOM_ANIMATION_MS = 260
private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

/**
 * Longest side of the detail layer requested once a photograph is zoomed. A full
 * fifty-megapixel frame decodes to about two hundred megabytes, which is an out of memory
 * crash rather than a sharper picture, so the request is capped instead of asking for the
 * original.
 */
private const val MAX_DETAIL_PIXELS = 4096

/** Below this the screen-sized layer is already sharp enough to not pay for a second decode. */
private const val DETAIL_FROM_SCALE = 1.2f

internal fun Context.activity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@Composable
fun MediaViewer(media: List<GalleryMedia>, initialKey: String, favorites: Set<String>, onClose: () -> Unit,
    onFavorite: (String) -> Unit, onDelete: (GalleryMedia) -> Unit, onHide: ((GalleryMedia) -> Unit)? = null) {
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
        AnimatedVisibility(chrome,
            enter = fadeIn(tween(200)) + slideInVertically(tween(240)) { -it / 3 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { -it / 3 },
            modifier = Modifier.align(Alignment.TopCenter)) {
            Row(Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .75f), Color.Transparent)))
                .statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = Color.White) }
                Text("${pager.currentPage + 1} / ${media.size}", style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f))
                IconButton(onClick = { details = true }) { Icon(Icons.Default.Info, "Сведения", tint = Color.White) }
            }
        }
        AnimatedVisibility(chrome,
            enter = fadeIn(tween(200)) + slideInVertically(tween(240)) { it / 3 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 3 },
            modifier = Modifier.align(Alignment.BottomCenter)) {
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
                IconButton(onClick = { onDelete(current) }) { Icon(Icons.Default.Delete, "Удалить", tint = Color.White) }
                // Only present while the vault is unlocked, so the feature stays hidden otherwise.
                if (onHide != null) {
                    IconButton(onClick = { onHide(current) }) { Icon(Icons.Default.Lock, "Скрыть", tint = Color.White) }
                }
            }
        }
    }
    if (details) AlertDialog(onDismissRequest = { details = false }, title = { Text("Сведения") }, text = {
        Text("${current.name}\n\n${DateFormat.getDateTimeInstance().format(Date(current.date))}\n${current.width} × ${current.height}\n${Formatter.formatFileSize(context, current.size)}\n${current.mime}\n\n${current.path}${current.name}")
    }, confirmButton = { TextButton(onClick = { details = false }) { Text("Закрыть") } })
}

@Composable
internal fun ZoomableImage(media: GalleryMedia, onZoom: (Boolean) -> Unit, onTap: () -> Unit) {
    var scale by remember(media.key) { mutableFloatStateOf(1f) }
    var offset by remember(media.key) { mutableStateOf(Offset.Zero) }
    var bounds by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()
    val zoomJob = remember(media.key) { mutableStateOf<Job?>(null) }

    fun clamp(value: Offset, factor: Float) = if (factor <= 1f) Offset.Zero else Offset(
        value.x.coerceIn(-bounds.width * (factor - 1) / 2f, bounds.width * (factor - 1) / 2f),
        value.y.coerceIn(-bounds.height * (factor - 1) / 2f, bounds.height * (factor - 1) / 2f))

    /** Double tap eases into place instead of snapping to the new scale. */
    fun animateZoom(target: Float) {
        zoomJob.value?.cancel()
        onZoom(target > 1f)
        zoomJob.value = scope.launch {
            val fromScale = scale
            val fromOffset = offset
            animate(0f, 1f, animationSpec = tween(ZOOM_ANIMATION_MS, easing = FastOutSlowInEasing)) { progress, _ ->
                scale = fromScale + (target - fromScale) * progress
                offset = clamp(fromOffset * (1f - progress), scale)
            }
        }
    }

    val transform = rememberTransformableState { zoom, pan, _ ->
        zoomJob.value?.cancel()
        val next = (scale * zoom).coerceIn(1f, MAX_ZOOM)
        scale = next
        offset = clamp(offset + pan, next)
        onZoom(next > 1f)
    }
    val context = LocalContext.current
    // Both layers carry the same transform, or the sharp one would drift away from the one
    // underneath it during a pinch.
    val transformed = Modifier.fillMaxSize().graphicsLayer {
        scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
    }
    Box(Modifier.fillMaxSize().background(Color.Black)
        .onSizeChanged { bounds = it }
        .pointerInput(media.key) { detectTapGestures(
            onTap = { onTap() },
            onDoubleTap = { animateZoom(if (scale > 1f) 1f else DOUBLE_TAP_ZOOM) }) }
        .transformable(state = transform, canPan = { scale > 1f }), contentAlignment = Alignment.Center) {
        // The base layer is sized to the screen, which is all Coil was ever asked for: zooming to
        // five times used to magnify those same pixels, so the detail simply was not there.
        AsyncImage(model = media.uri, contentDescription = media.name, contentScale = ContentScale.Fit,
            modifier = transformed)
        if (scale > DETAIL_FROM_SCALE) {
            // Mounted only while zoomed and dropped on the way back, so the larger bitmap is not
            // held for every page of the pager. It fades in over the base layer, which keeps
            // showing in the meantime, so there is no blank frame while it decodes.
            val detail = remember(media.key) {
                ImageRequest.Builder(context)
                    .data(media.uri)
                    .size(Size(MAX_DETAIL_PIXELS, MAX_DETAIL_PIXELS))
                    .build()
            }
            AsyncImage(model = detail, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = transformed)
        }
    }
}

/**
 * Playback starts immediately; Media3 hides its own controls after [VIDEO_CHROME_TIMEOUT_MS],
 * and [onChrome] mirrors that so the application bars and system bars disappear at the same
 * moment. A tap brings everything back.
 */
@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun VideoPlayer(media: GalleryMedia, chrome: Boolean, onChrome: (Boolean) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val player = remember(media.key) {
        ExoPlayer.Builder(context).build().apply {
            // Audio focus is off by default, so a video used to play over whatever the user was
            // already listening to. USAGE_MEDIA is required: automatic focus only covers usages
            // that ask for permanent focus, and setAudioAttributes throws on the others.
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true
            )
            setMediaItem(MediaItem.fromUri(media.uri)); prepare(); playWhenReady = true
        }
    }
    // PlayerView does not keep the screen awake by itself, so a long video used to be cut off by
    // the display timeout. Tied to actual playback rather than to the screen being open, so a
    // paused video lets the phone sleep as usual.
    var playing by remember(media.key) { mutableStateOf(false) }
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) player.pause() }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        lifecycle.addObserver(observer)
        player.addListener(listener)
        onDispose {
            lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
        }
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
            playerView.keepScreenOn = playing
            // Keep Media3 controls and application chrome in the same state after an
            // external toggle (back gesture, page change).
            if (chrome) playerView.showController() else playerView.hideController()
        },
        modifier = Modifier.fillMaxSize()
    )
}
