package com.shahabcodes.filestorm.ui.browser

import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.shahabcodes.filestorm.data.FileKind
import com.shahabcodes.filestorm.data.FsEntry
import com.shahabcodes.filestorm.ui.components.kindColor
import com.shahabcodes.filestorm.ui.components.kindIcon
import com.shahabcodes.filestorm.ui.theme.fsColors
import com.shahabcodes.filestorm.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Image shapes, decoded from headers only (no full bitmap) so the mosaic can lay
 * tiles out at their true proportions without paying for a decode per file.
 */
object ThumbRatios {
    private val cache = ConcurrentHashMap<String, Float>()

    fun cached(path: String): Float? = cache[path]

    suspend fun ratioOf(path: String, isVideo: Boolean): Float = withContext(Dispatchers.IO) {
        cache[path] ?: run {
            val value = if (isVideo) {
                16f / 9f
            } else {
                runCatching {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(path, options)
                    if (options.outWidth > 0 && options.outHeight > 0) {
                        options.outWidth.toFloat() / options.outHeight.toFloat()
                    } else 1f
                }.getOrDefault(1f)
            }
            // Extreme panoramas would wreck the rhythm of the grid.
            val clamped = value.coerceIn(0.62f, 1.7f)
            cache[path] = clamped
            clamped
        }
    }
}

/**
 * Photo-wall tile: the image fills the frame with no text on top. Videos get a
 * small glyph, non-media get a tinted card, and selection shrinks the tile
 * behind an accent ring the way the iOS photo grid does.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoTile(
    entry: FsEntry,
    selectionMode: Boolean,
    selected: Boolean,
    corner: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val isMedia = !entry.isDirectory &&
        (entry.kind == FileKind.IMAGE || entry.kind == FileKind.VIDEO)
    val scale by animateFloatAsState(
        targetValue = if (selected) 0.9f else 1f,
        animationSpec = spring(stiffness = 600f),
        label = "tileScale",
    )
    val radius by animateDpAsState(
        targetValue = if (selected) corner + 4.dp else corner,
        label = "tileCorner",
    )

    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .then(
                if (selected) Modifier.border(2.5.dp, fsColors.accent, RoundedCornerShape(corner))
                else Modifier
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .scale(scale)
                .clip(RoundedCornerShape(radius))
                .background(fsColors.fill),
        ) {
            if (isMedia) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(File(entry.path))
                        .crossfade(180)
                        .build(),
                    contentDescription = entry.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (entry.kind == FileKind.VIDEO) {
                    // A small corner glyph reads better than a giant play button.
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(3.dp),
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow, null,
                            tint = Color.White, modifier = Modifier.size(13.dp),
                        )
                    }
                }
            } else {
                // Folders and documents keep a legible label since there is no image.
                val tint = if (entry.isDirectory) {
                    com.shahabcodes.filestorm.data.FolderStyles.colorOf(entry.path)
                        ?.let { Color(it) } ?: fsColors.accent
                } else kindColor(entry.kind, fsColors.isDark)
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(tint.copy(alpha = 0.22f), tint.copy(alpha = 0.10f))
                            )
                        ),
                ) {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            if (entry.isDirectory) {
                                com.shahabcodes.filestorm.ui.components.FolderIcons
                                    .iconFor(com.shahabcodes.filestorm.data.FolderStyles.iconOf(entry.path))
                            } else kindIcon(entry.kind),
                            null,
                            tint = tint,
                            modifier = Modifier.size(30.dp),
                        )
                        Spacer(Modifier.padding(top = 6.dp))
                        Text(
                            entry.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = fsColors.label,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                        if (!entry.isDirectory) {
                            Text(
                                Formatters.bytes(entry.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = fsColors.secondaryLabel,
                            )
                        }
                    }
                }
            }

            // Selection tick, iOS style: filled circle bottom-right of the frame.
            if (selectionMode) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) fsColors.accent else Color.Black.copy(alpha = 0.35f)
                        )
                        .then(
                            if (selected) Modifier
                            else Modifier.border(1.5.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            Icons.Rounded.Check, null,
                            tint = Color.White, modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Mosaic cell that sizes itself to the picture's real proportions. */
@Composable
fun MosaicTile(
    entry: FsEntry,
    selectionMode: Boolean,
    selected: Boolean,
    corner: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val isVideo = entry.kind == FileKind.VIDEO
    var ratio by remember(entry.path) {
        mutableFloatStateOf(ThumbRatios.cached(entry.path) ?: 1f)
    }
    LaunchedEffect(entry.path) {
        if (!entry.isDirectory && (entry.kind == FileKind.IMAGE || isVideo)) {
            ratio = ThumbRatios.ratioOf(entry.path, isVideo)
        }
    }
    PhotoTile(
        entry = entry,
        selectionMode = selectionMode,
        selected = selected,
        corner = corner,
        modifier = Modifier.fillMaxWidth().aspectRatio(ratio),
        onClick = onClick,
        onLongClick = onLongClick,
    )
}
