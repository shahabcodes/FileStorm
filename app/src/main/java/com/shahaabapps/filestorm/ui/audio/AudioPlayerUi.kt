package com.shahaabapps.filestorm.ui.audio

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shahaabapps.filestorm.data.audio.AudioPlayer
import com.shahaabapps.filestorm.data.audio.RepeatMode
import com.shahaabapps.filestorm.data.audio.Track
import com.shahaabapps.filestorm.ui.components.pressScale
import com.shahaabapps.filestorm.ui.theme.fsColors

private fun clock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val minutes = total / 60
    val seconds = total % 60
    val hours = minutes / 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes % 60, seconds)
    else "%d:%02d".format(minutes, seconds)
}

/** Cover art, decoded off the composition and cached per track. */
@Composable
private fun rememberArtwork(path: String?): androidx.compose.ui.graphics.ImageBitmap? {
    var art by remember(path) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    LaunchedEffect(path) {
        art = null
        if (path.isNullOrEmpty()) return@LaunchedEffect
        art = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            AudioPlayer.artworkFor(path)?.asImageBitmap()
        }
    }
    return art
}

@Composable
private fun ArtworkOrGlyph(path: String?, corner: androidx.compose.ui.unit.Dp) {
    val art = rememberArtwork(path)
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(corner))
            .background(
                // Files without embedded art still deserve something to look at.
                Brush.linearGradient(
                    listOf(fsColors.accent.copy(alpha = 0.7f), fsColors.kinds.audio)
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) {
            Image(
                bitmap = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Rounded.MusicNote, null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.fillMaxSize(0.34f),
            )
        }
    }
}

/**
 * A slim bar above the navigation bar whenever something is loaded, so playback
 * is always reachable without going back to the file that started it. Swiping
 * it sideways skips, tapping opens the full player.
 */
@Composable
fun BoxScope.MiniPlayer(onExpand: () -> Unit) {
    val state by AudioPlayer.state.collectAsState()
    val track = state.current ?: return
    val progress by animateFloatAsState(state.progress, tween(300), label = "mini")

    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(fsColors.cardSecondary)
            .navigationBarsPadding(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(fsColors.fill),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(fsColors.accent),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Only the artwork and titles open the full player. Putting that on
            // the whole bar meant every near-miss of a control expanded the
            // player instead of doing what was tapped.
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .pressScale(onExpand)
                    .pointerInput(Unit) {
                        var drag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { drag = 0f },
                            onDragEnd = {
                                if (drag < -60f) AudioPlayer.next()
                                else if (drag > 60f) AudioPlayer.previous()
                                drag = 0f
                            },
                        ) { _, amount -> drag += amount }
                    }
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp)) { ArtworkOrGlyph(track.path, 10.dp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        track.displayTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = fsColors.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        state.error ?: track.displayArtist,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.error != null) fsColors.red else fsColors.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // Every control gets a full 48dp target with real space between
            // them; they were 34-38dp and touching, which is why taps missed.
            RoundControl(
                icon = if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                size = 48.dp,
                filled = true,
            ) { AudioPlayer.togglePlay() }
            Spacer(Modifier.width(6.dp))
            RoundControl(Icons.Rounded.SkipNext, size = 48.dp) { AudioPlayer.next() }
            Spacer(Modifier.width(6.dp))
            RoundControl(Icons.Rounded.Close, size = 48.dp) { AudioPlayer.stop() }
        }
    }
}

/** The full player: art, scrubber, transport, and the queue. */
@Composable
fun AudioPlayerScreen(onCollapse: () -> Unit) {
    val state by AudioPlayer.state.collectAsState()
    val track = state.current
    if (track == null) {
        LaunchedEffect(Unit) { onCollapse() }
        return
    }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    val shown = if (scrubbing) scrubFraction else state.progress
    var showSpeed by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(fsColors.groupedBackground)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundControl(Icons.Rounded.KeyboardArrowDown, size = 38.dp, onClick = onCollapse)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Now Playing",
                    style = MaterialTheme.typography.labelSmall,
                    color = fsColors.secondaryLabel,
                )
                Text(
                    "${state.orderIndex + 1} of ${state.order.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = fsColors.secondaryLabel,
                )
            }
            RoundControl(Icons.Rounded.Close, size = 38.dp) {
                AudioPlayer.stop()
                onCollapse()
            }
        }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, bottom = 28.dp,
            ),
        ) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .padding(vertical = 12.dp),
                ) { ArtworkOrGlyph(track.path, 24.dp) }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    track.displayTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = fsColors.label,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    track.displayArtist + if (track.album.isNotBlank()) " · ${track.album}" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = fsColors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(18.dp))
            }

            item {
                Scrubber(
                    fraction = shown,
                    onScrubStart = { scrubbing = true },
                    onScrub = { scrubFraction = it },
                    onScrubEnd = {
                        scrubbing = false
                        AudioPlayer.seekTo((state.durationMs * scrubFraction).toLong())
                    },
                )
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text(
                        clock((state.durationMs * shown).toLong()),
                        style = MaterialTheme.typography.labelSmall,
                        color = fsColors.secondaryLabel,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        clock(state.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = fsColors.secondaryLabel,
                    )
                }
                Spacer(Modifier.height(14.dp))
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundControl(
                        Icons.Rounded.Shuffle,
                        size = 44.dp,
                        tint = if (state.shuffle) fsColors.accent else fsColors.secondaryLabel,
                    ) { AudioPlayer.toggleShuffle() }
                    RoundControl(
                        Icons.Rounded.SkipPrevious,
                        size = 54.dp,
                        tint = if (state.hasPrevious) fsColors.label
                        else fsColors.secondaryLabel.copy(alpha = 0.4f),
                    ) { AudioPlayer.previous() }
                    RoundControl(
                        icon = if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        size = 88.dp,
                        filled = true,
                    ) { AudioPlayer.togglePlay() }
                    RoundControl(
                        Icons.Rounded.SkipNext,
                        size = 54.dp,
                        tint = if (state.hasNext) fsColors.label
                        else fsColors.secondaryLabel.copy(alpha = 0.4f),
                    ) { AudioPlayer.next() }
                    RoundControl(
                        icon = if (state.repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne
                        else Icons.Rounded.Repeat,
                        size = 44.dp,
                        tint = if (state.repeat == RepeatMode.OFF) fsColors.secondaryLabel
                        else fsColors.accent,
                    ) { AudioPlayer.cycleRepeat() }
                }
                Spacer(Modifier.height(10.dp))
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(fsColors.fill)
                            .pressScale { showSpeed = !showSpeed }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Speed, null,
                            tint = fsColors.secondaryLabel, modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${state.speed}x",
                            style = MaterialTheme.typography.labelMedium,
                            color = fsColors.label,
                        )
                    }
                }
                if (showSpeed) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    ) {
                        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                            val on = state.speed == speed
                            Text(
                                "${speed}x",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (on) Color.White else fsColors.secondaryLabel,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (on) fsColors.accent else fsColors.fill)
                                    .pressScale { AudioPlayer.setSpeed(speed) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))
            }

            if (state.queue.size > 1) {
                item {
                    Text(
                        "Up Next",
                        style = MaterialTheme.typography.titleLarge,
                        color = fsColors.label,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                itemsIndexed(state.order, key = { _, q -> q }) { position, queueIndex ->
                    val item = state.queue.getOrNull(queueIndex) ?: return@itemsIndexed
                    QueueRow(
                        track = item,
                        position = position + 1,
                        current = position == state.orderIndex,
                        onClick = { AudioPlayer.playAt(queueIndex) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueRow(track: Track, position: Int, current: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (current) fsColors.accent.copy(alpha = 0.12f) else Color.Transparent)
            .pressScale(onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$position",
            style = MaterialTheme.typography.labelSmall,
            color = if (current) fsColors.accent else fsColors.secondaryLabel,
            modifier = Modifier.width(24.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                track.displayTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (current) fsColors.accent else fsColors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.displayArtist,
                style = MaterialTheme.typography.labelSmall,
                color = fsColors.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (track.durationMs > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                clock(track.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = fsColors.secondaryLabel,
            )
        }
    }
}

/** Draggable progress bar with a handle that grows while being dragged. */
@Composable
private fun Scrubber(
    fraction: Float,
    onScrubStart: () -> Unit,
    onScrub: (Float) -> Unit,
    onScrubEnd: () -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    val handle by animateFloatAsState(if (dragging) 1f else 0f, tween(160), label = "handle")
    val density = LocalDensity.current

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        onScrubStart()
                        onScrub((offset.x / size.width).coerceIn(0f, 1f))
                    },
                    onDragEnd = {
                        dragging = false
                        onScrubEnd()
                    },
                    onDragCancel = {
                        dragging = false
                        onScrubEnd()
                    },
                ) { change, _ ->
                    onScrub((change.position.x / size.width).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val track = fsColors.fill
        val accent = fsColors.accent
        val green = fsColors.green
        Canvas(Modifier.fillMaxWidth().height(28.dp)) {
            val y = size.height / 2f
            val thickness = (5f + 3f * handle) * density.density
            drawLine(
                color = track,
                start = androidx.compose.ui.geometry.Offset(0f, y),
                end = androidx.compose.ui.geometry.Offset(size.width, y),
                strokeWidth = thickness,
                cap = StrokeCap.Round,
            )
            val x = size.width * fraction.coerceIn(0f, 1f)
            if (x > 0f) {
                drawLine(
                    brush = Brush.horizontalGradient(listOf(accent, green)),
                    start = androidx.compose.ui.geometry.Offset(0f, y),
                    end = androidx.compose.ui.geometry.Offset(x, y),
                    strokeWidth = thickness,
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    color = accent,
                    radius = (6f + 3f * handle) * density.density,
                    center = androidx.compose.ui.geometry.Offset(x, y),
                )
            }
        }
        // Keeps widthPx referenced so the layout recomputes with the constraints.
        if (widthPx < 0f) Spacer(Modifier.size(0.dp))
    }
}

@Composable
private fun RoundControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    size: androidx.compose.ui.unit.Dp,
    tint: Color = fsColors.label,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (filled) Modifier.background(
                    Brush.linearGradient(listOf(fsColors.accent, fsColors.green))
                ) else Modifier
            )
            .pressScale(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            null,
            tint = if (filled) Color.White else tint,
            modifier = Modifier.size(size * if (filled) 0.5f else 0.62f),
        )
    }
}
