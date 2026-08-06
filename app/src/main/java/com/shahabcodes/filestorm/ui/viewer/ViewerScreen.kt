package com.shahabcodes.filestorm.ui.viewer

import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.shahabcodes.filestorm.data.FileKind
import com.shahabcodes.filestorm.data.FileRepository
import com.shahabcodes.filestorm.data.FsEntry
import com.shahabcodes.filestorm.data.TrashManager
import com.shahabcodes.filestorm.ui.browser.InfoSheet
import com.shahabcodes.filestorm.ui.browser.openFile
import com.shahabcodes.filestorm.ui.theme.fsColors
import com.shahabcodes.filestorm.util.Formatters
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Shows [items], starting on [startPath]. The list is passed in rather than read
 * from shared state, so the viewer can only ever display what the tap handed it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ViewerScreen(
    items: List<String>,
    startPath: String,
    onBack: () -> Unit,
) {
    var current by remember(items) { mutableStateOf(items) }
    val close = onBack
    if (current.isEmpty()) {
        LaunchedEffect(Unit) { close() }
        return
    }
    androidx.activity.compose.BackHandler { close() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Resolve by path: even if the list shifted between the tap and this frame,
    // the viewer still opens on the file that was actually tapped.
    val initialPage = remember(items, startPath) {
        val resolved = items.indexOf(startPath)
        com.shahabcodes.filestorm.data.Diagnostics.log(
            "VIEWER",
            "resolve start=$startPath -> page=$resolved of ${items.size}" +
                if (resolved < 0) "  MISMATCH" else "",
        )
        resolved.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = initialPage) { current.size }
    var chromeVisible by remember { mutableStateOf(true) }
    // In the floating window there is only room for the picture itself; the
    // system draws its own controls over it.
    val pip = VideoController.inPip
    if (pip) chromeVisible = false
    var infoTarget by remember { mutableStateOf<FsEntry?>(null) }
    var confirmDelete by remember { mutableStateOf<File?>(null) }

    val currentFile = remember(pagerState.currentPage, current) {
        val file = File(current.getOrElse(pagerState.currentPage) { current.last() })
        com.shahabcodes.filestorm.data.Diagnostics.log(
            "VIEWER",
            "page ${pagerState.currentPage} showing ${file.name}",
        )
        file
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { current.getOrElse(it) { "empty$it" } },
        ) { page ->
            val file = File(current[page])
            if (FsEntry.kindOf(file.name, false) == FileKind.VIDEO) {
                VideoPage(
                    file = file,
                    active = pagerState.currentPage == page,
                    chromeVisible = chromeVisible,
                    onToggleChrome = { chromeVisible = !chromeVisible },
                )
            } else {
                ImagePage(file = file, onToggleChrome = { chromeVisible = !chromeVisible })
            }
        }

        AnimatedVisibility(
            visible = chromeVisible && !pip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .statusBarsPadding()
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarIcon(Icons.Rounded.Close, "Close") { close() }
                Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                    Text(
                        currentFile.name,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${pagerState.currentPage + 1} of ${current.size} · " +
                            Formatters.bytes(currentFile.length()) + " · " +
                            Formatters.fileDate(currentFile.lastModified()),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
                // Only offered for video, and only once a player exists to float.
                if (VideoController.available) {
                    val activity = LocalContext.current as? com.shahabcodes.filestorm.MainActivity
                    BarIcon(Icons.Rounded.PictureInPictureAlt, "Float") {
                        activity?.enterPipIfPlaying()
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = chromeVisible && !pip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .navigationBarsPadding()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarIcon(Icons.Rounded.Share, "Share") { share(context, currentFile) }
                BarIcon(Icons.Rounded.Info, "Details") {
                    if (currentFile.exists()) infoTarget = FsEntry.from(currentFile)
                }
                BarIcon(Icons.Rounded.OpenInNew, "Open with") {
                    if (currentFile.exists()) openFile(context, FsEntry.from(currentFile))
                }
                BarIcon(Icons.Rounded.Delete, "Delete", Color(0xFFFF6B6B)) {
                    confirmDelete = currentFile
                }
            }
        }
    }

    infoTarget?.let { target ->
        InfoSheet(entry = target, onDismiss = { infoTarget = null })
    }

    confirmDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = fsColors.card,
            title = { Text("Move to Trash?", color = fsColors.label) },
            text = {
                Text(
                    "${file.name} (${Formatters.bytes(file.length())}) goes to the Trash, " +
                        "where you can restore it until you empty it.",
                    color = fsColors.secondaryLabel,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch {
                        if (file.exists()) TrashManager.moveToTrash(listOf(FsEntry.from(file)))
                        FileRepository.invalidate(file.parent)
                        current = current.filterNot { it == file.absolutePath }
                        if (current.isEmpty()) close()
                    }
                }) { Text("Move to Trash", color = fsColors.red) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text("Cancel", color = fsColors.secondaryLabel)
                }
            },
        )
    }
}

@Composable
private fun BarIcon(
    icon: ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    // A plain clickable always invokes the current lambda; pointerInput keyed on
    // a constant would freeze the very first one and act on the wrong file.
    Icon(
        icon, label,
        tint = tint,
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.CircleShape)
            .clickable(onClick = onClick)
            .padding(12.dp)
            .size(22.dp),
    )
}

private fun share(context: android.content.Context, file: File) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType(mime)
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                "Share ${file.name}",
            )
        )
    }
}

/**
 * Zoomable image page. Gestures are only consumed when two fingers are down, or
 * when a single finger pans an already-zoomed image — otherwise the horizontal
 * drag reaches the pager so swiping between items keeps working.
 */
@Composable
private fun ImagePage(file: File, onToggleChrome: () -> Unit) {
    val toggle by androidx.compose.runtime.rememberUpdatedState(onToggleChrome)
    var scale by remember(file) { mutableFloatStateOf(1f) }
    var offsetX by remember(file) { mutableFloatStateOf(0f) }
    var offsetY by remember(file) { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    var zoomJob by remember(file) { mutableStateOf<Job?>(null) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(file) {
                detectTapGestures(
                    onTap = { toggle() },
                    onDoubleTap = { tap ->
                        // Zoom towards the point that was tapped rather than the
                        // middle of the screen, and glide there instead of
                        // snapping, which is what makes it feel like Photos.
                        val target = if (scale > 1.05f) 1f else 2.75f
                        val toX: Float
                        val toY: Float
                        if (target <= 1f) {
                            toX = 0f
                            toY = 0f
                        } else {
                            val maxX = size.width * (target - 1) / 2f
                            val maxY = size.height * (target - 1) / 2f
                            toX = ((tap.x - size.width / 2f) * (1 - target)).coerceIn(-maxX, maxX)
                            toY = ((tap.y - size.height / 2f) * (1 - target)).coerceIn(-maxY, maxY)
                        }
                        val fromScale = scale
                        val fromX = offsetX
                        val fromY = offsetY
                        zoomJob?.cancel()
                        zoomJob = scope.launch {
                            animate(0f, 1f, animationSpec = tween(240, easing = FastOutSlowInEasing)) { t, _ ->
                                scale = fromScale + (target - fromScale) * t
                                offsetX = fromX + (toX - fromX) * t
                                offsetY = fromY + (toY - fromY) * t
                            }
                        }
                    },
                )
            }
            .pointerInput(file) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val multiTouch = event.changes.count { it.pressed } >= 2
                        if (multiTouch) {
                            zoomJob?.cancel()
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            if (scale > 1.02f) {
                                val maxX = size.width * (scale - 1) / 2f
                                val maxY = size.height * (scale - 1) / 2f
                                offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                                offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                            event.changes.forEach { it.consume() }
                        } else if (scale > 1.02f) {
                            val pan = event.calculatePan()
                            val maxX = size.width * (scale - 1) / 2f
                            val maxY = size.height * (scale - 1) / 2f
                            offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                            offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(file)
                .crossfade(true)
                .build(),
            contentDescription = file.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY,
                ),
        )
    }
}

/**
 * TextureView-backed player. TextureView, unlike VideoView's SurfaceView, can be
 * scaled and translated, so video supports the same pinch-zoom and pan as photos
 * while still swiping between items when not zoomed.
 */
@Composable
private fun VideoPage(
    file: File,
    active: Boolean,
    chromeVisible: Boolean,
    onToggleChrome: () -> Unit,
) {
    val toggle by androidx.compose.runtime.rememberUpdatedState(onToggleChrome)
    var player by remember(file) { mutableStateOf<android.media.MediaPlayer?>(null) }
    var playing by remember(file) { mutableStateOf(false) }
    var muted by remember(file) { mutableStateOf(false) }
    var duration by remember(file) { mutableIntStateOf(0) }
    var position by remember(file) { mutableIntStateOf(0) }
    var scrubbing by remember(file) { mutableStateOf(false) }
    var videoWidth by remember(file) { mutableIntStateOf(0) }
    var videoHeight by remember(file) { mutableIntStateOf(0) }

    var scale by remember(file) { mutableFloatStateOf(1f) }
    var offsetX by remember(file) { mutableFloatStateOf(0f) }
    var offsetY by remember(file) { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    var zoomJob by remember(file) { mutableStateOf<Job?>(null) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(file) {
                detectTapGestures(
                    onTap = { toggle() },
                    onDoubleTap = { tap ->
                        // Zoom towards the point that was tapped rather than the
                        // middle of the screen, and glide there instead of
                        // snapping, which is what makes it feel like Photos.
                        val target = if (scale > 1.05f) 1f else 2.75f
                        val toX: Float
                        val toY: Float
                        if (target <= 1f) {
                            toX = 0f
                            toY = 0f
                        } else {
                            val maxX = size.width * (target - 1) / 2f
                            val maxY = size.height * (target - 1) / 2f
                            toX = ((tap.x - size.width / 2f) * (1 - target)).coerceIn(-maxX, maxX)
                            toY = ((tap.y - size.height / 2f) * (1 - target)).coerceIn(-maxY, maxY)
                        }
                        val fromScale = scale
                        val fromX = offsetX
                        val fromY = offsetY
                        zoomJob?.cancel()
                        zoomJob = scope.launch {
                            animate(0f, 1f, animationSpec = tween(240, easing = FastOutSlowInEasing)) { t, _ ->
                                scale = fromScale + (target - fromScale) * t
                                offsetX = fromX + (toX - fromX) * t
                                offsetY = fromY + (toY - fromY) * t
                            }
                        }
                    },
                )
            }
            .pointerInput(file) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val multiTouch = event.changes.count { it.pressed } >= 2
                        if (multiTouch || scale > 1.02f) {
                            zoomJob?.cancel()
                            val zoom = if (multiTouch) event.calculateZoom() else 1f
                            val pan = event.calculatePan()
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            if (scale > 1.02f) {
                                val maxX = size.width * (scale - 1) / 2f
                                val maxY = size.height * (scale - 1) / 2f
                                offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                                offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val ratio = if (videoWidth > 0 && videoHeight > 0) {
            videoWidth.toFloat() / videoHeight.toFloat()
        } else 0f

        AndroidView(
            factory = { ctx ->
                android.view.TextureView(ctx).apply {
                    surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(
                            texture: android.graphics.SurfaceTexture,
                            width: Int,
                            height: Int,
                        ) {
                            runCatching {
                                val mp = android.media.MediaPlayer()
                                mp.setSurface(android.view.Surface(texture))
                                mp.setDataSource(file.absolutePath)
                                mp.setOnPreparedListener {
                                    duration = it.duration
                                    videoWidth = it.videoWidth
                                    videoHeight = it.videoHeight
                                    it.setVolume(if (muted) 0f else 1f, if (muted) 0f else 1f)
                                    VideoController.reportSize(it.videoWidth, it.videoHeight)
                                }
                                mp.setOnCompletionListener {
                                    playing = false
                                    VideoController.reportPlaying(false)
                                }
                                mp.prepareAsync()
                                player = mp
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(
                            texture: android.graphics.SurfaceTexture,
                            width: Int,
                            height: Int,
                        ) = Unit

                        override fun onSurfaceTextureDestroyed(
                            texture: android.graphics.SurfaceTexture,
                        ): Boolean {
                            runCatching { player?.release() }
                            player = null
                            playing = false
                            return true
                        }

                        override fun onSurfaceTextureUpdated(
                            texture: android.graphics.SurfaceTexture,
                        ) = Unit
                    }
                }
            },
            modifier = (if (ratio > 0f) Modifier.fillMaxWidth().aspectRatio(ratio) else Modifier.fillMaxSize())
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY,
                ),
        )

        LaunchedEffect(active) {
            if (!active) {
                runCatching { player?.pause() }
                playing = false
            }
        }
        // The activity needs a way to start and stop this from the floating
        // window, where none of this composable's own controls are visible.
        DisposableEffect(file, player) {
            val path = file.absolutePath
            if (player != null) {
                VideoController.bind(path) {
                    val p = player
                    if (p != null) {
                        if (playing) {
                            runCatching { p.pause() }
                            playing = false
                        } else {
                            runCatching { p.start() }
                            playing = true
                        }
                    }
                }
            }
            onDispose { VideoController.unbind(path) }
        }
        LaunchedEffect(playing) { VideoController.reportPlaying(playing) }
        DisposableEffect(file) {
            onDispose {
                runCatching { player?.release() }
                player = null
            }
        }
        LaunchedEffect(playing, active) {
            while (playing && active) {
                runCatching { player?.let { if (!scrubbing) position = it.currentPosition } }
                delay(200)
            }
        }
        LaunchedEffect(muted) {
            runCatching { player?.setVolume(if (muted) 0f else 1f, if (muted) 0f else 1f) }
        }

        if (!playing) {
            Box(
                Modifier
                    .size(74.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    .pointerInput(file) {
                        detectTapGestures {
                            runCatching { player?.start() }
                            playing = true
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.PlayArrow, "Play",
                    tint = Color.White, modifier = Modifier.size(40.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = chromeVisible && !VideoController.inPip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp)
                    .padding(top = 6.dp, bottom = 62.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        clock(position),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Slider(
                        value = if (duration > 0) position.toFloat() / duration else 0f,
                        onValueChange = { fraction ->
                            scrubbing = true
                            position = (fraction * duration).toInt()
                        },
                        onValueChangeFinished = {
                            runCatching { player?.seekTo(position) }
                            scrubbing = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Text(
                        clock(duration),
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BarIcon(Icons.Rounded.Replay10, "Back 10 seconds") {
                        runCatching {
                            player?.let {
                                val target = (it.currentPosition - 10_000).coerceAtLeast(0)
                                it.seekTo(target)
                                position = target
                            }
                        }
                    }
                    BarIcon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (playing) "Pause" else "Play",
                    ) {
                        val mp = player ?: return@BarIcon
                        runCatching { if (playing) mp.pause() else mp.start() }
                        playing = !playing
                    }
                    BarIcon(Icons.Rounded.Forward10, "Forward 10 seconds") {
                        runCatching {
                            player?.let {
                                val target = (it.currentPosition + 10_000).coerceAtMost(duration)
                                it.seekTo(target)
                                position = target
                            }
                        }
                    }
                    BarIcon(
                        if (muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                        if (muted) "Unmute" else "Mute",
                    ) { muted = !muted }
                    BarIcon(
                        if (scale > 1.02f) Icons.Rounded.ZoomOut else Icons.Rounded.ZoomIn,
                        if (scale > 1.02f) "Reset zoom" else "Zoom in",
                    ) {
                        if (scale > 1.02f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = 2f
                        }
                    }
                }
            }
        }
    }
}

private fun clock(ms: Int): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}
