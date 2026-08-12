package com.shahabcodes.filestorm.ui.browser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import kotlinx.coroutines.delay
import androidx.compose.material.icons.rounded.Share
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material.icons.rounded.Close
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.shahabcodes.filestorm.data.Prefs
import com.shahabcodes.filestorm.util.Formatters
import java.io.File

/** Photos and videos only — everything else has nothing to show full screen. */
fun reelItems(entries: List<FsEntry>): List<FsEntry> =
    entries.filter { !it.isDirectory && (it.kind == FileKind.IMAGE || it.kind == FileKind.VIDEO) }

/**
 * The active clip's playback, shared with the chrome around it.
 *
 * The picture lives inside a layer that pinch-zoom scales, and the scrub bar
 * must not scale with it — so the bar is drawn outside that layer and reads the
 * player through here instead.
 */
class ReelPlayback {
    var position by mutableIntStateOf(0)
    var duration by mutableIntStateOf(0)
    var playing by mutableStateOf(false)
    var seek: ((Int) -> Unit)? = null
}

/**
 * Whether the reel has been expanded to fill the screen.
 *
 * The reel used to swallow the toolbar the moment you picked it, which is
 * wrong — you can no longer search, sort or get out without knowing that back
 * works. It now sits in the layout like any other view until you ask for the
 * whole screen, and the screens that draw the toolbar read this to know.
 */
object ReelFullscreen {
    var on by mutableStateOf(false)
}

/**
 * A full-screen vertical feed: one photo or video per page, swipe up for the
 * next. Videos start on their own when they reach the front and stop the moment
 * they leave, so only ever one is decoding.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReelView(
    entries: List<FsEntry>,
    contentPadding: PaddingValues,
    onLongClick: (FsEntry) -> Unit,
    onExit: (() -> Unit)? = null,
) {
    val items = remember(entries) { reelItems(entries) }
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text(
                "Nothing to play here.\nReel shows photos and videos.",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    // Mute is a property of the session, not of one clip: unmuting once should
    // hold as you keep swiping, the way it does in a real feed.
    var muted by remember { mutableStateOf(Prefs.reelStartMuted) }
    val pagerState = rememberPagerState { items.size }
    // Pinching has to win over the pager, otherwise a two-finger gesture just
    // flicks to the next item. While anything is zoomed in, dragging pans the
    // picture instead of turning the page.
    var zoomedPage by remember { mutableStateOf(-1) }
    val playback = remember { ReelPlayback() }
    val context = LocalContext.current

    // A short flick should move exactly one item and settle immediately, which
    // is what a feed feels like. The stock behaviour needs a longer, more
    // deliberate swipe and can fly past several items at once.
    val fling = PagerDefaults.flingBehavior(
        state = pagerState,
        pagerSnapDistance = PagerSnapDistance.atMost(1),
        snapPositionalThreshold = 0.15f,
        snapAnimationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
    )

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { items[it].path },
            userScrollEnabled = zoomedPage < 0,
            flingBehavior = fling,
        ) { page ->
            val entry = items[page]
            val active = pagerState.currentPage == page

            var scale by remember(entry.path) { mutableFloatStateOf(1f) }
            var offsetX by remember(entry.path) { mutableFloatStateOf(0f) }
            var offsetY by remember(entry.path) { mutableFloatStateOf(0f) }

            // Leaving a page zoomed in would strand the pager, so reset on exit.
            LaunchedEffect(active) {
                if (!active) {
                    scale = 1f
                    offsetX = 0f
                    offsetY = 0f
                }
            }
            LaunchedEffect(scale, active) {
                if (active) zoomedPage = if (scale > 1.01f) page else -1
            }

            var pageSize by remember(entry.path) { mutableStateOf(IntSize.Zero) }
            val doubleTapZoom: (Offset) -> Unit = { tap ->
                if (scale > 1.01f) {
                    scale = 1f
                    offsetX = 0f
                    offsetY = 0f
                } else {
                    scale = 2.5f
                    val maxX = pageSize.width * (scale - 1) / 2f
                    val maxY = pageSize.height * (scale - 1) / 2f
                    offsetX = ((tap.x - pageSize.width / 2f) * (1 - scale)).coerceIn(-maxX, maxX)
                    offsetY = ((tap.y - pageSize.height / 2f) * (1 - scale)).coerceIn(-maxY, maxY)
                }
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { pageSize = it }
                    .pointerInput(entry.path) {
                        detectTapGestures(
                            onLongPress = { onLongClick(entry) },
                            onDoubleTap = { doubleTapZoom(it) },
                        )
                    }
                    .pointerInput(entry.path) {
                        // Hand-rolled rather than detectTransformGestures,
                        // which consumes every drag it sees — including the
                        // one-finger flick that is supposed to turn the page.
                        // Gestures are only taken here when two fingers are
                        // down, or when the picture is already zoomed in and
                        // dragging should pan it.
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val pinching = event.changes.size > 1
                                if (pinching || scale > 1.01f) {
                                    if (pinching) {
                                        scale = (scale * event.calculateZoom())
                                            .coerceIn(1f, 6f)
                                    }
                                    if (scale > 1.01f) {
                                        val pan = event.calculatePan()
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
                val zoom = Modifier.graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY,
                )
                if (entry.kind == FileKind.VIDEO) {
                    Box(zoom) {
                        ReelVideo(
                            file = File(entry.path),
                            active = active,
                            muted = muted,
                            playback = if (active) playback else null,
                            // A video's own tap handler sits on top of the
                            // page's, so it has to forward the gestures it is
                            // not interested in or zoom would never fire here.
                            onDoubleTap = { doubleTapZoom(it) },
                            onLongPress = { onLongClick(entry) },
                        )
                    }
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(entry.path))
                            .crossfade(true)
                            .build(),
                        contentDescription = entry.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().then(zoom),
                    )
                }

                // Caption, sitting above the browser's own bottom chrome.
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.35f))
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp)
                        .padding(
                            top = 10.dp,
                            // Clear of the scrub bar, which sits underneath.
                            bottom = 18.dp + contentPadding.calculateBottomPadding(),
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            entry.name,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            Formatters.bytes(entry.size) + " · " +
                                Formatters.fileDate(entry.lastModified) +
                                " · ${page + 1} of ${items.size}",
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f))
                            .pointerInput(entry.path) {
                                detectTapGestures { shareFile(context, File(entry.path)) }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Share, "Share",
                            tint = Color.White, modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        // Scrub bar for the clip in front. Outside the pager on purpose: it
        // must not scale with a pinch, and it must not scroll away with a page.
        if (playback.duration > 0) {
            ReelScrubber(
                playback = playback,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = contentPadding.calculateBottomPadding()),
            )
        }

        // Close only earns its place once the toolbar is gone; otherwise the
        // header's own Back is right there.
        if (onExit != null && ReelFullscreen.on) {
            ReelButton(
                icon = Icons.Rounded.Close,
                label = "Leave reel",
                onClick = onExit,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(top = 8.dp, start = 14.dp),
            )
        }

        Row(
            Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 14.dp),
        ) {
            if (items.any { it.kind == FileKind.VIDEO }) {
                ReelButton(
                    icon = if (muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                    label = if (muted) "Unmute" else "Mute",
                    onClick = { muted = !muted },
                )
                Spacer(Modifier.width(10.dp))
            }
            ReelButton(
                icon = if (ReelFullscreen.on) Icons.Rounded.FullscreenExit
                else Icons.Rounded.Fullscreen,
                label = if (ReelFullscreen.on) "Exit full screen" else "Full screen",
                onClick = { ReelFullscreen.on = !ReelFullscreen.on },
            )
        }
    }

    // Leaving the reel must not strand the rest of the app with no toolbar.
    DisposableEffect(Unit) {
        onDispose { ReelFullscreen.on = false }
    }
}

@Composable
private fun ReelButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .pointerInput(label) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(21.dp))
    }
}

/**
 * One clip in the feed. The player is tied to [active], so the pages either
 * side of the current one hold no decoder at all.
 */
@Composable
private fun ReelVideo(
    file: File,
    active: Boolean,
    muted: Boolean,
    playback: ReelPlayback?,
    onDoubleTap: (Offset) -> Unit,
    onLongPress: () -> Unit,
) {
    var player by remember(file) { mutableStateOf<android.media.MediaPlayer?>(null) }
    var surface by remember(file) { mutableStateOf<android.view.Surface?>(null) }
    var playing by remember(file) { mutableStateOf(false) }
    var width by remember(file) { mutableIntStateOf(0) }
    var height by remember(file) { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                android.view.TextureView(ctx).apply {
                    surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(
                            texture: android.graphics.SurfaceTexture,
                            w: Int,
                            h: Int,
                        ) {
                            surface = android.view.Surface(texture)
                        }

                        override fun onSurfaceTextureSizeChanged(
                            texture: android.graphics.SurfaceTexture,
                            w: Int,
                            h: Int,
                        ) = Unit

                        override fun onSurfaceTextureDestroyed(
                            texture: android.graphics.SurfaceTexture,
                        ): Boolean {
                            surface = null
                            return true
                        }

                        override fun onSurfaceTextureUpdated(
                            texture: android.graphics.SurfaceTexture,
                        ) = Unit
                    }
                }
            },
            modifier = if (width > 0 && height > 0) {
                Modifier.fillMaxWidth().aspectRatio(width.toFloat() / height)
            } else {
                Modifier.fillMaxSize()
            },
        )

        DisposableEffect(file, active, surface) {
            val target = surface
            if (active && target != null) {
                runCatching {
                    val mp = android.media.MediaPlayer()
                    mp.setSurface(target)
                    mp.setDataSource(file.absolutePath)
                    mp.isLooping = Prefs.reelLoop
                    mp.setOnPreparedListener {
                        width = it.videoWidth
                        height = it.videoHeight
                        it.setVolume(if (muted) 0f else 1f, if (muted) 0f else 1f)
                        playback?.duration = it.duration
                        playback?.seek = { ms -> runCatching { it.seekTo(ms) } }
                        if (Prefs.reelAutoplay) {
                            runCatching { it.start() }
                            playing = true
                        }
                    }
                    mp.setOnCompletionListener { playing = false }
                    mp.setOnErrorListener { _, _, _ -> playing = false; true }
                    mp.prepareAsync()
                    player = mp
                }
            }
            onDispose {
                runCatching { player?.release() }
                player = null
                playing = false
                playback?.seek = null
                playback?.duration = 0
                playback?.position = 0
            }
        }

        // Poll only while something is actually moving.
        LaunchedEffect(playing, active) {
            while (playing && active) {
                runCatching { player?.let { playback?.position = it.currentPosition } }
                delay(120)
            }
        }
        LaunchedEffect(playing) { playback?.playing = playing }

        LaunchedEffect(muted) {
            runCatching { player?.setVolume(if (muted) 0f else 1f, if (muted) 0f else 1f) }
        }

        // Tap anywhere to hold the clip, tap again to carry on.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(file) {
                    detectTapGestures(
                        onDoubleTap = onDoubleTap,
                        onLongPress = { onLongPress() },
                        onTap = {
                            val p = player ?: return@detectTapGestures
                            if (playing) {
                                runCatching { p.pause() }
                                playing = false
                            } else {
                                runCatching { p.start() }
                                playing = true
                            }
                        },
                    )
                },
        )

        if (!playing) {
            Box(
                Modifier
                    .size(66.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.PlayArrow, "Play",
                    tint = Color.White, modifier = Modifier.size(36.dp),
                )
            }
        }
    }
}

/** A slim Instagram-style progress line you can drag to move through a clip. */
@Composable
private fun ReelScrubber(playback: ReelPlayback, modifier: Modifier = Modifier) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var barWidth by remember { mutableIntStateOf(1) }

    val fraction = if (dragging) dragFraction
    else if (playback.duration > 0) playback.position.toFloat() / playback.duration
    else 0f

    Column(modifier.fillMaxWidth()) {
        if (dragging) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text(
                    clockOf((fraction * playback.duration).toInt()),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    clockOf(playback.duration),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                // The touch target is far taller than the line, so the bar is
                // grabbable without being visually heavy.
                .height(26.dp)
                .onSizeChanged { barWidth = it.width.coerceAtLeast(1) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            dragFraction = (offset.x / barWidth).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            playback.seek?.invoke((dragFraction * playback.duration).toInt())
                            dragging = false
                        },
                        onDragCancel = { dragging = false },
                        onHorizontalDrag = { change, delta ->
                            change.consume()
                            dragFraction = (dragFraction + delta / barWidth).coerceIn(0f, 1f)
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .height(if (dragging) 5.dp else 2.5.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.28f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(if (dragging) 5.dp else 2.5.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
        }
    }
}

private fun clockOf(ms: Int): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

/** Hands the file to whatever the user picks; the app never sends it anywhere. */
private fun shareFile(context: android.content.Context, file: File) {
    runCatching {
        // The manifest declares ${applicationId}.provider — getting this wrong
        // throws, and the throw would be swallowed into a button that does
        // nothing at all.
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            context.packageName + ".provider",
            file,
        )
        val type = android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            setDataAndType(uri, type)
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Share"))
    }
}
