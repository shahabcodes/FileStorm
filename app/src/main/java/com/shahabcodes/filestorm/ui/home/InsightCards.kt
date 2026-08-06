package com.shahabcodes.filestorm.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shahabcodes.filestorm.data.ChartStyle
import com.shahabcodes.filestorm.data.FileKind
import com.shahabcodes.filestorm.data.FileRepository
import com.shahabcodes.filestorm.data.FsEntry
import com.shahabcodes.filestorm.data.Prefs
import com.shahabcodes.filestorm.data.StorageInsights
import com.shahabcodes.filestorm.data.TrashManager
import com.shahabcodes.filestorm.data.dup.DuplicateFinder
import com.shahabcodes.filestorm.ui.browser.openFile
import com.shahabcodes.filestorm.ui.components.FsSpinner
import com.shahabcodes.filestorm.ui.components.GroupedCard
import com.shahabcodes.filestorm.ui.components.RowSeparator
import com.shahabcodes.filestorm.ui.components.pressScale
import com.shahabcodes.filestorm.ui.theme.fsColors
import com.shahabcodes.filestorm.util.Formatters
import java.io.File

/** Cards stay glanceable at three rows; everything else lives behind View all. */
const val CARD_PREVIEW = 3

/** One entry in a ranked card, independent of how it ends up being drawn. */
data class Slice(
    val title: String,
    val subtitle: String,
    val bytes: Long,
    val onClick: () -> Unit,
)

/** Shared shell so every insight card has the same header and empty state. */
@Composable
fun InsightCard(
    title: String,
    subtitle: String?,
    hasData: Boolean,
    content: @Composable () -> Unit,
) {
    GroupedCard {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = fsColors.label)
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = fsColors.secondaryLabel,
                        )
                    }
                }
                if (StorageInsights.scanning) FsSpinner(size = 20.dp, strokeWidth = 2.5.dp)
            }
            Spacer(Modifier.height(12.dp))
            if (!hasData) {
                Text(
                    if (StorageInsights.scanning) {
                        "Scanning… ${Formatters.compactCount(StorageInsights.scannedFiles)} files"
                    } else {
                        "Nothing to show yet."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = fsColors.secondaryLabel,
                )
            } else {
                content()
            }
        }
    }
}

/**
 * Drives the grow-in animation. Charts read far better when the eye can follow
 * the bars or arcs settling, and it also disguises the frame or two a treemap
 * takes to lay out.
 */
@Composable
private fun rememberReveal(key: Any?): Float {
    var shown by androidx.compose.runtime.remember(key) {
        androidx.compose.runtime.mutableStateOf(false)
    }
    androidx.compose.runtime.LaunchedEffect(key) { shown = true }
    val value by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 620,
            easing = androidx.compose.animation.core.FastOutSlowInEasing,
        ),
        label = "reveal",
    )
    return value
}

/** Draws a ranked list in whichever style Settings asks for. */
@Composable
fun RankedChart(slices: List<Slice>) {
    when (Prefs.chartStyle) {
        ChartStyle.BARS -> BarsChart(slices)
        ChartStyle.DONUT -> DonutChart(slices)
        ChartStyle.TREEMAP -> TreemapChart(slices)
        ChartStyle.LIST -> PlainList(slices)
    }
}

@Composable
fun sliceColor(index: Int): Color {
    val kinds = fsColors.kinds
    val wheel = listOf(
        fsColors.accent, kinds.image, kinds.video, fsColors.orange,
        kinds.archive, kinds.apk, kinds.audio, fsColors.green,
    )
    return wheel[index % wheel.size]
}

@Composable
private fun BarsChart(slices: List<Slice>) {
    val biggest = slices.maxOfOrNull { it.bytes }?.coerceAtLeast(1L) ?: 1L
    val total = slices.sumOf { it.bytes }.coerceAtLeast(1L)
    val reveal = rememberReveal(slices.size to biggest)
    Column {
        slices.forEachIndexed { index, slice ->
            // Each row keeps the colour it has in the donut and treemap, so the
            // same item is recognisable whichever style is selected.
            val color = sliceColor(index)
            Row(
                Modifier.fillMaxWidth().pressScale(slice.onClick).padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(RoundedCornerShape(50))
                                .background(color)
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            slice.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = fsColors.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            Formatters.bytes(slice.bytes),
                            style = MaterialTheme.typography.labelMedium,
                            color = fsColors.label,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(fsColors.fill),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(
                                    ((slice.bytes.toFloat() / biggest) * reveal)
                                        .coerceIn(0.02f, 1f)
                                )
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(color.copy(alpha = 0.75f), color)
                                    )
                                ),
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${(100f * slice.bytes / total).coerceAtLeast(0.1f).let {
                            if (it < 1f) "<1" else it.toInt().toString()
                        }}% · ${slice.subtitle}",
                        style = MaterialTheme.typography.labelSmall,
                        color = fsColors.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlainList(slices: List<Slice>) {
    Column {
        slices.forEachIndexed { index, slice ->
            Row(
                Modifier.fillMaxWidth().pressScale(slice.onClick).padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        slice.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = fsColors.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        slice.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = fsColors.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    Formatters.bytes(slice.bytes),
                    style = MaterialTheme.typography.labelMedium,
                    color = fsColors.label,
                )
            }
            if (index != slices.lastIndex) RowSeparator(startIndent = 0.dp)
        }
    }
}

@Composable
private fun DonutChart(slices: List<Slice>) {
    // Beyond a handful the arcs get too thin to read, so the tail is pooled.
    val shown = slices.take(7)
    val tail = slices.drop(7)
    val tailBytes = tail.sumOf { it.bytes }
    val total = (shown.sumOf { it.bytes } + tailBytes).coerceAtLeast(1L)
    val colors = shown.indices.map { sliceColor(it) }
    val tailColor = fsColors.secondaryLabel.copy(alpha = 0.35f)
    val reveal = rememberReveal(slices.size to total)
    val trackColor = fsColors.fill

    Column {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(168.dp)) {
                    val width = 30.dp.toPx()
                    val stroke = Stroke(width = width, cap = StrokeCap.Round)
                    drawArc(
                        color = trackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = width),
                    )
                    // A small gap between segments reads as separate pieces
                    // rather than one continuous smear of colour.
                    val gap = 2.2f
                    var start = -90f
                    shown.forEachIndexed { index, slice ->
                        val full = 360f * (slice.bytes.toFloat() / total)
                        val sweep = ((full - gap) * reveal).coerceAtLeast(0f)
                        if (sweep > 0f) {
                            drawArc(
                                color = colors[index],
                                startAngle = start + gap / 2f,
                                sweepAngle = sweep,
                                useCenter = false,
                                style = stroke,
                            )
                        }
                        start += full
                    }
                    if (tailBytes > 0) {
                        val full = 360f * (tailBytes.toFloat() / total)
                        val sweep = ((full - gap) * reveal).coerceAtLeast(0f)
                        if (sweep > 0f) {
                            drawArc(
                                color = tailColor,
                                startAngle = start + gap / 2f,
                                sweepAngle = sweep,
                                useCenter = false,
                                style = stroke,
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        Formatters.bytes(total),
                        style = MaterialTheme.typography.headlineMedium,
                        color = fsColors.label,
                    )
                    Text(
                        "${slices.size} entries",
                        style = MaterialTheme.typography.labelSmall,
                        color = fsColors.secondaryLabel,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        shown.forEachIndexed { index, slice ->
            Row(
                Modifier.fillMaxWidth().pressScale(slice.onClick).padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(colors[index])
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        slice.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = fsColors.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        slice.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = fsColors.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        Formatters.bytes(slice.bytes),
                        style = MaterialTheme.typography.labelMedium,
                        color = fsColors.label,
                    )
                    Text(
                        "${(100f * slice.bytes / total).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = fsColors.secondaryLabel,
                    )
                }
            }
        }
        if (tail.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(tailColor))
                Spacer(Modifier.width(10.dp))
                Text(
                    "${tail.size} more",
                    style = MaterialTheme.typography.bodyMedium,
                    color = fsColors.secondaryLabel,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    Formatters.bytes(tailBytes),
                    style = MaterialTheme.typography.labelMedium,
                    color = fsColors.secondaryLabel,
                )
            }
        }
    }
}

/** A tile in the treemap, in dp within the card's width. */
private data class Tile(val index: Int, val x: Float, val y: Float, val w: Float, val h: Float)

/**
 * Squarified treemap: fill rows along the shorter side, extending each row only
 * while doing so keeps the tiles closer to square. Slice-and-dice is simpler but
 * degenerates into unreadable slivers once the values are lopsided, which is
 * exactly the case here — one huge folder and a long tail of small ones.
 */
private fun squarify(values: List<Long>, width: Float, height: Float): List<Tile> {
    val total = values.sum().toFloat()
    if (total <= 0f || width <= 0f || height <= 0f) return emptyList()
    val scale = width * height / total
    val areas = values.map { it * scale }
    val out = ArrayList<Tile>(values.size)

    var x = 0f
    var y = 0f
    var w = width
    var h = height
    var i = 0
    while (i < areas.size && w > 1f && h > 1f) {
        val vertical = w >= h
        val side = if (vertical) h else w
        var rowArea = 0f
        var best = Float.MAX_VALUE
        var j = i
        while (j < areas.size) {
            val candidate = rowArea + areas[j]
            if (candidate <= 0f) { j++; continue }
            val thickness = candidate / side
            var worst = 0f
            for (k in i..j) {
                val length = if (thickness > 0f) areas[k] / thickness else 0f
                if (length <= 0f) continue
                val ratio = maxOf(thickness / length, length / thickness)
                if (ratio > worst) worst = ratio
            }
            if (j > i && worst > best) break
            best = worst
            rowArea = candidate
            j++
        }
        if (rowArea <= 0f) break
        val thickness = rowArea / side
        var offset = 0f
        for (k in i until j) {
            val length = areas[k] / thickness
            out.add(
                if (vertical) Tile(k, x, y + offset, thickness, length)
                else Tile(k, x + offset, y, length, thickness)
            )
            offset += length
        }
        if (vertical) {
            x += thickness
            w -= thickness
        } else {
            y += thickness
            h -= thickness
        }
        i = j
    }
    return out
}

@Composable
private fun TreemapChart(slices: List<Slice>) {
    val shown = slices.take(14)
    val reveal = rememberReveal(shown.size)
    BoxWithConstraints(Modifier.fillMaxWidth().height(230.dp)) {
        val tiles = squarify(shown.map { it.bytes }, maxWidth.value, maxHeight.value)
        tiles.forEach { tile ->
            val slice = shown[tile.index]
            val color = sliceColor(tile.index)
            Box(
                Modifier
                    .offset(x = tile.x.dp, y = tile.y.dp)
                    .size(
                        width = (tile.w - 3f).coerceAtLeast(1f).dp,
                        height = (tile.h - 3f).coerceAtLeast(1f).dp,
                    )
                    .graphicsLayer {
                        // Settle into place rather than snapping in fully formed.
                        scaleX = 0.82f + 0.18f * reveal
                        scaleY = 0.82f + 0.18f * reveal
                        alpha = reveal
                    }
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.68f))
                        )
                    )
                    .pressScale(slice.onClick)
                    .padding(7.dp),
            ) {
                // Only label tiles with room for it; the rest stay clean blocks.
                if (tile.w > 66f && tile.h > 38f) {
                    Column {
                        Text(
                            slice.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(1.dp))
                        Text(
                            Formatters.bytes(slice.bytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReclaimCard(
    onOpenTrash: () -> Unit,
    onOpenDuplicates: () -> Unit,
    onCleanEmptyFolders: () -> Unit,
    onCleanZeroByte: () -> Unit,
) {
    val snapshot = StorageInsights.snapshot
    val dup by DuplicateFinder.state.collectAsState()
    val trashBytes = TrashManager.items.sumOf { it.size }
    val trashCount = TrashManager.items.size
    val dupBytes = dup.wastedBytes
    val emptyCount = snapshot?.emptyFolderCount ?: 0
    val zeroCount = snapshot?.zeroByteCount ?: 0
    val total = trashBytes + dupBytes

    InsightCard(
        title = "Reclaim Space",
        subtitle = if (total > 0) "${Formatters.bytes(total)} can be freed right now"
        else "Nothing obvious to reclaim",
        hasData = true,
    ) {
        Column {
            ReclaimRow(
                icon = Icons.Rounded.DeleteOutline,
                tint = fsColors.red,
                label = "Trash",
                detail = if (trashCount == 0) "Empty"
                else "$trashCount item(s) · ${Formatters.bytes(trashBytes)}",
                actionable = trashCount > 0,
                onClick = onOpenTrash,
            )
            RowSeparator(startIndent = 38.dp)
            ReclaimRow(
                icon = Icons.Rounded.ContentCopy,
                tint = fsColors.orange,
                label = "Duplicates",
                detail = if (dup.pairs.isEmpty()) "Run a sweep to find copies"
                else "${dup.extraCopies} extra copy(s) · ${Formatters.bytes(dupBytes)}",
                actionable = true,
                onClick = onOpenDuplicates,
            )
            RowSeparator(startIndent = 38.dp)
            ReclaimRow(
                icon = Icons.Rounded.FolderOff,
                tint = fsColors.accent,
                label = "Empty folders",
                detail = if (emptyCount == 0) "None found" else "$emptyCount folder(s)",
                actionable = emptyCount > 0,
                onClick = onCleanEmptyFolders,
            )
            RowSeparator(startIndent = 38.dp)
            ReclaimRow(
                icon = Icons.Rounded.InsertDriveFile,
                tint = fsColors.green,
                label = "Zero-byte files",
                detail = if (zeroCount == 0) "None found" else "$zeroCount file(s)",
                actionable = zeroCount > 0,
                onClick = onCleanZeroByte,
            )
        }
    }
}

@Composable
private fun ReclaimRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    label: String,
    detail: String,
    actionable: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (actionable) Modifier.pressScale(onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(tint.copy(alpha = if (actionable) 0.16f else 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon, null,
                tint = if (actionable) tint else tint.copy(alpha = 0.45f),
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = fsColors.label)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = fsColors.secondaryLabel)
        }
        if (actionable) {
            Icon(
                Icons.Rounded.ChevronRight, null,
                tint = fsColors.secondaryLabel.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** The largest files anywhere on the device; tapping one opens its folder. */
@Composable
fun BiggestFilesCard(
    onOpenViewer: (List<String>, Int) -> Unit,
    onViewMore: () -> Unit,
) {
    val context = LocalContext.current
    val snapshot = StorageInsights.snapshot
    val files = snapshot?.biggestFiles.orEmpty()
    InsightCard(
        title = "Biggest Files",
        subtitle = if (files.isEmpty()) null
        else "${Formatters.bytes(files.sumOf { it.size })} across ${files.size}" +
            hiddenNote(snapshot?.includedHidden),
        hasData = files.isNotEmpty(),
    ) {
        Column {
            RankedChart(
                files.take(CARD_PREVIEW).map { file ->
                    Slice(
                        title = file.name,
                        subtitle = prettyPath(file.folder),
                        bytes = file.size,
                        onClick = { openInsightFile(context, files, file, onOpenViewer) },
                    )
                }
            )
            if (files.size > CARD_PREVIEW) ViewMoreRow("View all ${files.size} files", onViewMore)
        }
    }
}

/** Where the space actually sits: folder totals including everything beneath. */
@Composable
fun LargestFoldersCard(onOpenFolder: (String) -> Unit, onViewMore: () -> Unit) {
    val snapshot = StorageInsights.snapshot
    val folders = snapshot?.largestFolders.orEmpty()
    InsightCard(
        title = "Largest Folders",
        subtitle = if (folders.isEmpty()) null
        else "Including everything nested inside" + hiddenNote(snapshot?.includedHidden),
        hasData = folders.isNotEmpty(),
    ) {
        Column {
            RankedChart(folders.take(CARD_PREVIEW).map { folderSlice(it, onOpenFolder) })
            if (folders.size > CARD_PREVIEW) {
                ViewMoreRow("View all ${folders.size} folders", onViewMore)
            }
        }
    }
}

/** Shared so the card and the detail screen describe a folder identically. */
fun folderSlice(
    folder: StorageInsights.FolderEntry,
    onOpenFolder: (String) -> Unit,
): Slice = Slice(
    title = folder.name.ifEmpty { "Internal storage" },
    subtitle = "${Formatters.compactCount(folder.files)} files · " +
        prettyPath(folder.path.substringBeforeLast(File.separatorChar, "")),
    bytes = folder.bytes,
    onClick = { onOpenFolder(folder.path) },
)

/** How much was added each month, oldest to newest. */
@Composable
fun GrowthCard(onViewMore: () -> Unit) {
    val all = StorageInsights.snapshot?.months.orEmpty()
    val months = all.reversed().takeLast(8)
    val peak = months.maxOfOrNull { it.bytes }?.coerceAtLeast(1L) ?: 1L
    val reveal = rememberReveal(months.size to peak)
    InsightCard(
        title = "Monthly Footprint",
        subtitle = if (months.isEmpty()) null
        else "From file dates · deleted files aren't counted",
        hasData = months.isNotEmpty(),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().height(126.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                months.forEach { month ->
                    // The heaviest month carries the accent; the rest sit back
                    // so the shape of the year reads at a glance.
                    val isPeak = month.bytes == peak
                    val color = if (isPeak) fsColors.accent else fsColors.accent.copy(alpha = 0.34f)
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Text(
                            Formatters.bytes(month.bytes).substringBefore(" "),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isPeak) fsColors.label else fsColors.secondaryLabel,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(
                                    (84f * (month.bytes.toFloat() / peak) * reveal)
                                        .coerceAtLeast(4f).dp
                                )
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(color, color.copy(alpha = 0.35f))
                                    )
                                ),
                        )
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                months.forEach { month ->
                    Text(
                        month.label.take(3),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (month.bytes == peak) fsColors.label else fsColors.secondaryLabel,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (all.size > months.size) ViewMoreRow("View all ${all.size} months", onViewMore)
        }
    }
}

@Composable
fun RecentFilesCard(onOpenViewer: (List<String>, Int) -> Unit, onViewMore: () -> Unit) {
    val context = LocalContext.current
    val all = StorageInsights.snapshot?.recent.orEmpty()
    val files = all.take(CARD_PREVIEW)
    InsightCard(
        title = "Recent Files",
        subtitle = if (files.isEmpty()) "Nothing changed in the last week"
        else "The newest changes from the last 7 days",
        hasData = files.isNotEmpty(),
    ) {
        Column {
            files.forEachIndexed { index, file ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressScale { openInsightFile(context, files, file, onOpenViewer) }
                        .padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    com.shahabcodes.filestorm.ui.components.FileIconView(
                        FsEntry.from(File(file.path)),
                        size = 38.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            file.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = fsColors.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            prettyPath(file.folder),
                            style = MaterialTheme.typography.labelSmall,
                            color = fsColors.secondaryLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            Formatters.bytes(file.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = fsColors.label,
                        )
                        Text(
                            Formatters.fileDate(file.modified),
                            style = MaterialTheme.typography.labelSmall,
                            color = fsColors.secondaryLabel,
                        )
                    }
                }
                if (index != files.lastIndex) RowSeparator(startIndent = 50.dp)
            }
            if (all.size > CARD_PREVIEW) ViewMoreRow("View all ${all.size} files", onViewMore)
        }
    }
}

@Composable
private fun ViewMoreRow(label: String, onClick: () -> Unit) {
    Column {
        Spacer(Modifier.height(4.dp))
        RowSeparator(startIndent = 0.dp)
        Row(
            Modifier.fillMaxWidth().pressScale(onClick).padding(top = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = fsColors.accent,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Rounded.ChevronRight, null,
                tint = fsColors.accent, modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Opens a file listed by a dashboard card: photos and video in the app's own
 * viewer with the card's other media alongside to swipe through, anything else
 * handed to whichever app handles it. Shared so Biggest Files and Recent Files
 * behave identically — landing in the folder instead of the file was the odd
 * one out.
 */
fun openInsightFile(
    context: android.content.Context,
    all: List<StorageInsights.FileEntry>,
    file: StorageInsights.FileEntry,
    onOpenViewer: (List<String>, Int) -> Unit,
) {
    val media = all.filter {
        val kind = FsEntry.kindOf(it.name, false)
        kind == FileKind.IMAGE || kind == FileKind.VIDEO
    }
    val at = media.indexOfFirst { it.path == file.path }
    if (at >= 0) onOpenViewer(media.map { it.path }, at)
    else openFile(context, FsEntry.from(File(file.path)))
}

fun hiddenNote(included: Boolean?): String =
    if (included == true) " · hidden included" else ""

fun prettyPath(path: String): String =
    path.replace(FileRepository.rootPath, "Internal storage")
