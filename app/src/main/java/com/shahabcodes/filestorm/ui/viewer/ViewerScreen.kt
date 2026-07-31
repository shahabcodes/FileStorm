package com.shahabcodes.filestorm.ui.viewer

import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.shahabcodes.filestorm.data.FileKind
import com.shahabcodes.filestorm.data.FsEntry
import com.shahabcodes.filestorm.ui.browser.InfoSheet
import com.shahabcodes.filestorm.ui.browser.openFile
import com.shahabcodes.filestorm.util.Formatters
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.abs

/** Holds what the viewer should show; avoids passing long lists through navigation. */
object ViewerState {
    var paths: List<String> = emptyList()
        private set
    var startIndex: Int = 0
        private set

    fun open(items: List<String>, index: Int) {
        paths = items
        startIndex = index.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ViewerScreen(onBack: () -> Unit) {
    val items = ViewerState.paths
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = ViewerState.startIndex) { items.size }
    var chromeVisible by remember { mutableStateOf(true) }
    var infoTarget by remember { mutableStateOf<FsEntry?>(null) }

    val currentFile = remember(pagerState.currentPage, items) {
        File(items.getOrElse(pagerState.currentPage) { items.first() })
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val file = File(items[page])
            val kind = FsEntry.kindOf(file.name, false)
            if (kind == FileKind.VIDEO) {
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

        // Top bar
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Close, "Close",
                    tint = Color.White,
                    modifier = Modifier
                        .pointerInput(Unit) { detectTapGestures { onBack() } }
                        .padding(8.dp)
                        .size(22.dp),
                )
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        currentFile.name,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${pagerState.currentPage + 1} of ${items.size} · " +
                            Formatters.bytes(currentFile.length()),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Icon(
                    Icons.Rounded.Info, "Details",
                    tint = Color.White,
                    modifier = Modifier
                        .pointerInput(currentFile) {
                            detectTapGestures {
                                if (currentFile.exists()) infoTarget = FsEntry.from(currentFile)
                            }
                        }
                        .padding(8.dp)
                        .size(22.dp),
                )
                Icon(
                    Icons.Rounded.OpenInNew, "Open with",
                    tint = Color.White,
                    modifier = Modifier
                        .pointerInput(currentFile) {
                            detectTapGestures {
                                if (currentFile.exists()) openFile(context, FsEntry.from(currentFile))
                            }
                        }
                        .padding(8.dp)
                        .size(22.dp),
                )
            }
        }

        // Bottom filmstrip position hint
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .navigationBarsPadding()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    Formatters.fileDate(currentFile.lastModified()),
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }

    infoTarget?.let { target ->
        InfoSheet(entry = target, onDismiss = { infoTarget = null })
    }
}

/** Pinch-zoom, pan and double-tap-to-zoom image page. */
@Composable
private fun ImagePage(file: File, onToggleChrome: () -> Unit) {
    var scale by remember(file) { mutableFloatStateOf(1f) }
    var offsetX by remember(file) { mutableFloatStateOf(0f) }
    var offsetY by remember(file) { mutableFloatStateOf(0f) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(file) {
                detectTapGestures(
                    onTap = { onToggleChrome() },
                    onDoubleTap = {
                        if (scale > 1.05f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = 2.5f
                        }
                    },
                )
            }
            .pointerInput(file) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val next = (scale * zoom).coerceIn(1f, 6f)
                    scale = next
                    if (next > 1.02f) {
                        // Keep panning inside sensible bounds for the zoom level.
                        val maxX = size.width * (next - 1) / 2f
                        val maxY = size.height * (next - 1) / 2f
                        offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                        offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
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

/** VideoView-backed player with a compact custom control bar. */
@Composable
private fun VideoPage(
    file: File,
    active: Boolean,
    chromeVisible: Boolean,
    onToggleChrome: () -> Unit,
) {
    var view by remember(file) { mutableStateOf<VideoView?>(null) }
    var playing by remember(file) { mutableStateOf(false) }
    var duration by remember(file) { mutableIntStateOf(0) }
    var position by remember(file) { mutableIntStateOf(0) }
    var scrubbing by remember(file) { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(file) { detectTapGestures { onToggleChrome() } },
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoPath(file.absolutePath)
                    setOnPreparedListener { mp ->
                        duration = mp.duration
                        mp.setOnVideoSizeChangedListener { _, _, _ -> requestLayout() }
                    }
                    setOnCompletionListener { playing = false }
                    view = this
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Only the visible page keeps playing.
        LaunchedEffect(active) {
            if (!active) {
                view?.pause()
                playing = false
            }
        }
        DisposableEffect(file) {
            onDispose {
                view?.stopPlayback()
                view = null
            }
        }
        LaunchedEffect(playing, active) {
            while (playing && active) {
                view?.let { if (!scrubbing) position = it.currentPosition }
                delay(200)
            }
        }

        if (!playing) {
            Box(
                Modifier
                    .size(72.dp)
                    .background(Color.Black.copy(alpha = 0.45f), androidx.compose.foundation.shape.CircleShape)
                    .pointerInput(file) {
                        detectTapGestures {
                            view?.start()
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
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .padding(bottom = 30.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (playing) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier
                            .pointerInput(file) {
                                detectTapGestures {
                                    val v = view ?: return@detectTapGestures
                                    if (playing) v.pause() else v.start()
                                    playing = !playing
                                }
                            }
                            .padding(6.dp)
                            .size(26.dp),
                    )
                    Spacer(Modifier.width(8.dp))
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
                            view?.seekTo(position)
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
