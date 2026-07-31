package com.shahabcodes.filestorm.ui.browser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PlayCircleFilled
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.shahabcodes.filestorm.data.FileKind
import com.shahabcodes.filestorm.data.FsEntry
import com.shahabcodes.filestorm.data.Prefs
import com.shahabcodes.filestorm.data.SortField
import com.shahabcodes.filestorm.data.ViewMode
import com.shahabcodes.filestorm.ui.components.FileIconView
import com.shahabcodes.filestorm.ui.components.RowSeparator
import com.shahabcodes.filestorm.ui.components.SelectionCircle
import com.shahabcodes.filestorm.ui.components.pressScale
import com.shahabcodes.filestorm.ui.components.kindColor
import com.shahabcodes.filestorm.ui.components.kindIcon
import com.shahabcodes.filestorm.ui.theme.fsColors
import com.shahabcodes.filestorm.util.Formatters
import java.io.File

/** Renders entries in the user's chosen view mode with shared selection behavior. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListView(
    entries: List<FsEntry>,
    selectionMode: Boolean,
    selected: Set<String>,
    contentPadding: PaddingValues,
    viewMode: ViewMode,
    columns: Int = 3,
    grouped: Boolean = false,
    collapsedMonths: Set<String> = emptySet(),
    onToggleMonth: (String) -> Unit = {},
    monthSorts: Map<String, Pair<SortField, Boolean>> = emptyMap(),
    onMonthSort: (String, SortField, Boolean) -> Unit = { _, _, _ -> },
    onZoom: (Float) -> Unit = {},
    onClick: (FsEntry) -> Unit,
    onLongClick: (FsEntry) -> Unit,
) {
    if (grouped) {
        GroupedByMonth(
            entries = entries,
            selectionMode = selectionMode,
            selected = selected,
            contentPadding = contentPadding,
            viewMode = viewMode,
            columns = columns,
            collapsedMonths = collapsedMonths,
            onToggleMonth = onToggleMonth,
            monthSorts = monthSorts,
            onMonthSort = onMonthSort,
            onZoom = onZoom,
            onClick = onClick,
            onLongClick = onLongClick,
        )
        return
    }
    when (viewMode) {
        ViewMode.LIST, ViewMode.DETAILED -> LazyColumn(
            Modifier.fillMaxSize().pinchToZoom(onZoom),
            contentPadding = contentPadding,
        ) {
            itemsIndexed(entries, key = { _, e -> e.path }) { index, entry ->
                val shape = when {
                    entries.size == 1 -> RoundedCornerShape(16.dp)
                    index == 0 -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    index == entries.lastIndex -> RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                    else -> RoundedCornerShape(0.dp)
                }
                Column(Modifier.clip(shape).background(fsColors.card)) {
                    if (viewMode == ViewMode.DETAILED) {
                        DetailedFileRow(
                            entry = entry,
                            selectionMode = selectionMode,
                            selected = entry.path in selected,
                            onClick = { onClick(entry) },
                            onLongClick = { onLongClick(entry) },
                        )
                    } else {
                        FileRow(
                            entry = entry,
                            selectionMode = selectionMode,
                            selected = entry.path in selected,
                            onClick = { onClick(entry) },
                            onLongClick = { onLongClick(entry) },
                        )
                    }
                    if (index != entries.lastIndex) {
                        RowSeparator(startIndent = if (viewMode == ViewMode.DETAILED) 76.dp else 60.dp)
                    }
                }
            }
        }

        ViewMode.GRID -> LazyVerticalGrid(
            columns = GridCells.Fixed(columns.coerceIn(1, 6)),
            modifier = Modifier.fillMaxSize().pinchToZoom(onZoom),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(entries, key = { _, e -> e.path }) { _, entry ->
                GridTile(
                    entry = entry,
                    selectionMode = selectionMode,
                    selected = entry.path in selected,
                    onClick = { onClick(entry) },
                    onLongClick = { onLongClick(entry) },
                )
            }
        }

        ViewMode.TIMELINE -> {
            val groups = remember(entries, Prefs.sortAscending) {
                val ordered = if (Prefs.sortAscending) entries.sortedBy { it.lastModified }
                else entries.sortedByDescending { it.lastModified }
                ordered.groupBy { monthLabel(it.lastModified) }
            }
            LazyColumn(
                Modifier.fillMaxSize().pinchToZoom(onZoom),
                contentPadding = contentPadding,
            ) {
                groups.forEach { (label, groupEntries) ->
                    stickyHeader(key = "hdr_$label") {
                        TimelineHeader(label, groupEntries)
                    }
                    itemsIndexed(groupEntries, key = { _, e -> e.path }) { index, entry ->
                        val shape = when {
                            groupEntries.size == 1 -> RoundedCornerShape(16.dp)
                            index == 0 -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                            index == groupEntries.lastIndex ->
                                RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                            else -> RoundedCornerShape(0.dp)
                        }
                        Column(Modifier.clip(shape).background(fsColors.card)) {
                            FileRow(
                                entry = entry,
                                selectionMode = selectionMode,
                                selected = entry.path in selected,
                                onClick = { onClick(entry) },
                                onLongClick = { onLongClick(entry) },
                            )
                            if (index != groupEntries.lastIndex) RowSeparator()
                        }
                    }
                    item(key = "gap_$label") { Spacer(Modifier.height(18.dp)) }
                }
            }
        }

        ViewMode.GALLERY -> {
            val span = columns.coerceIn(1, 4)
            LazyVerticalGrid(
                columns = GridCells.Fixed(span),
                modifier = Modifier.fillMaxSize().pinchToZoom(onZoom),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(tileGap(span)),
                horizontalArrangement = Arrangement.spacedBy(tileGap(span)),
            ) {
                itemsIndexed(entries, key = { _, e -> e.path }) { _, entry ->
                    PhotoTile(
                        entry = entry,
                        selectionMode = selectionMode,
                        selected = entry.path in selected,
                        corner = tileCorner(span),
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        onClick = { onClick(entry) },
                        onLongClick = { onLongClick(entry) },
                    )
                }
            }
        }

        ViewMode.MOSAIC -> {
            val span = columns.coerceIn(1, 4)
            androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid(
                columns = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells.Fixed(span),
                modifier = Modifier.fillMaxSize().pinchToZoom(onZoom),
                contentPadding = contentPadding,
                verticalItemSpacing = tileGap(span),
                horizontalArrangement = Arrangement.spacedBy(tileGap(span)),
            ) {
                items(entries, key = { it.path }) { entry ->
                    MosaicTile(
                        entry = entry,
                        selectionMode = selectionMode,
                        selected = entry.path in selected,
                        corner = tileCorner(span),
                        onClick = { onClick(entry) },
                        onLongClick = { onLongClick(entry) },
                    )
                }
            }
        }
    }
}



/** A month's contents at a glance. */
private data class MonthSummary(
    val images: Int,
    val videos: Int,
    val folders: Int,
    val others: Int,
    val bytes: Long,
) {
    fun line(): String = buildString {
        val parts = mutableListOf<String>()
        if (images > 0) parts.add("$images photo" + if (images == 1) "" else "s")
        if (videos > 0) parts.add("$videos video" + if (videos == 1) "" else "s")
        if (folders > 0) parts.add("$folders folder" + if (folders == 1) "" else "s")
        if (others > 0) parts.add("$others other")
        append(parts.joinToString(" · "))
        if (bytes > 0) {
            if (parts.isNotEmpty()) append(" · ")
            append(Formatters.bytes(bytes))
        }
    }
}

private fun summarise(entries: List<FsEntry>): MonthSummary {
    var images = 0
    var videos = 0
    var folders = 0
    var others = 0
    var bytes = 0L
    entries.forEach { entry ->
        when {
            entry.isDirectory -> folders++
            entry.kind == FileKind.IMAGE -> images++
            entry.kind == FileKind.VIDEO -> videos++
            else -> others++
        }
        if (!entry.isDirectory) bytes += entry.size
    }
    return MonthSummary(images, videos, folders, others, bytes)
}

/**
 * Any layout, split under Month Year headings. The heading for whatever month is
 * at the top stays pinned above the content, months can be collapsed, and the
 * chosen sort still orders the items inside each month.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupedByMonth(
    entries: List<FsEntry>,
    selectionMode: Boolean,
    selected: Set<String>,
    contentPadding: PaddingValues,
    viewMode: ViewMode,
    columns: Int,
    collapsedMonths: Set<String>,
    onToggleMonth: (String) -> Unit,
    monthSorts: Map<String, Pair<SortField, Boolean>>,
    onMonthSort: (String, SortField, Boolean) -> Unit,
    onZoom: (Float) -> Unit,
    onClick: (FsEntry) -> Unit,
    onLongClick: (FsEntry) -> Unit,
) {
    // Months run newest-first (or oldest-first), but inside a month the user's
    // name/size/type sort still applies.
    val groups = remember(entries, Prefs.sortAscending, Prefs.sortField, monthSorts) {
        entries
            .groupBy { monthLabel(it.lastModified) }
            .entries
            .sortedWith(
                compareBy { group -> group.value.maxOfOrNull { it.lastModified } ?: 0L }
            )
            .let { if (Prefs.sortAscending) it else it.reversed() }
            .map { (label, items) ->
                // A month can carry its own sort; otherwise it follows the folder.
                val (field, ascending) = monthSorts[label]
                    ?: (Prefs.sortField to Prefs.sortAscending)
                label to com.shahabcodes.filestorm.data.FileRepository.sortEntries(
                    items, field, ascending,
                )
            }
    }
    val summaries = remember(groups) { groups.associate { it.first to summarise(it.second) } }

    val tiled = viewMode == ViewMode.GRID || viewMode == ViewMode.GALLERY ||
        viewMode == ViewMode.MOSAIC
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

    // Maps every emitted slot back to its month so the pinned heading can follow
    // the scroll position in both the list and the grid.
    val slotLabels = remember(groups, collapsedMonths) {
        buildList {
            groups.forEach { (label, items) ->
                add(label)
                if (label !in collapsedMonths) repeat(items.size) { add(label) }
                add(label)
            }
        }
    }
    val headerSlots = remember(groups, collapsedMonths) {
        buildMap {
            var index = 0
            groups.forEach { (label, items) ->
                put(label, index)
                index += 1 + (if (label in collapsedMonths) 0 else items.size) + 1
            }
        }
    }
    val firstVisible = if (tiled) gridState.firstVisibleItemIndex else listState.firstVisibleItemIndex
    val pinnedLabel = slotLabels.getOrNull(firstVisible) ?: groups.firstOrNull()?.first
    // While a month's own heading is still on screen there is no need to repeat
    // it above the list - that is what caused the doubled heading.
    val showPinned = pinnedLabel != null && firstVisible > (headerSlots[pinnedLabel] ?: 0)

    Column(Modifier.fillMaxSize()) {
        if (showPinned && pinnedLabel != null) {
            MonthHeader(
                label = pinnedLabel,
                summary = summaries[pinnedLabel],
                collapsed = pinnedLabel in collapsedMonths,
                pinned = true,
                sort = monthSorts[pinnedLabel],
                onClick = { onToggleMonth(pinnedLabel) },
                onSortPicked = { field, asc -> onMonthSort(pinnedLabel, field, asc) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        if (tiled) {
            val span = columns.coerceIn(1, if (viewMode == ViewMode.GALLERY) 4 else 6)
            LazyVerticalGrid(
                columns = GridCells.Fixed(span),
                state = gridState,
                modifier = Modifier.fillMaxSize().pinchToZoom(onZoom),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                groups.forEach { (label, groupEntries) ->
                    item(
                        key = "hdr_$label",
                        span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
                    ) {
                        MonthHeader(
                            label = label,
                            summary = summaries[label],
                            collapsed = label in collapsedMonths,
                            pinned = false,
                            sort = monthSorts[label],
                            onClick = { onToggleMonth(label) },
                            onSortPicked = { field, asc -> onMonthSort(label, field, asc) },
                        )
                    }
                    if (label !in collapsedMonths) {
                        itemsIndexed(groupEntries, key = { _, e -> e.path }) { _, entry ->
                            if (viewMode == ViewMode.GALLERY || viewMode == ViewMode.MOSAIC) {
                                PhotoTile(
                                    entry = entry,
                                    selectionMode = selectionMode,
                                    selected = entry.path in selected,
                                    corner = tileCorner(span),
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                    onClick = { onClick(entry) },
                                    onLongClick = { onLongClick(entry) },
                                )
                            } else {
                                GridTile(
                                    entry = entry,
                                    selectionMode = selectionMode,
                                    selected = entry.path in selected,
                                    onClick = { onClick(entry) },
                                    onLongClick = { onLongClick(entry) },
                                )
                            }
                        }
                    }
                    item(
                        key = "gap_$label",
                        span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
                    ) { Spacer(Modifier.height(8.dp)) }
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().pinchToZoom(onZoom),
                state = listState,
                contentPadding = contentPadding,
            ) {
                groups.forEach { (label, groupEntries) ->
                    item(key = "hdr_$label") {
                        MonthHeader(
                            label = label,
                            summary = summaries[label],
                            collapsed = label in collapsedMonths,
                            pinned = false,
                            sort = monthSorts[label],
                            onClick = { onToggleMonth(label) },
                            onSortPicked = { field, asc -> onMonthSort(label, field, asc) },
                        )
                    }
                    if (label !in collapsedMonths) {
                        itemsIndexed(groupEntries, key = { _, e -> e.path }) { index, entry ->
                            val shape = when {
                                groupEntries.size == 1 -> RoundedCornerShape(16.dp)
                                index == 0 -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                                index == groupEntries.lastIndex ->
                                    RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                                else -> RoundedCornerShape(0.dp)
                            }
                            Column(Modifier.clip(shape).background(fsColors.card)) {
                                if (viewMode == ViewMode.DETAILED) {
                                    DetailedFileRow(
                                        entry = entry,
                                        selectionMode = selectionMode,
                                        selected = entry.path in selected,
                                        onClick = { onClick(entry) },
                                        onLongClick = { onLongClick(entry) },
                                    )
                                } else {
                                    FileRow(
                                        entry = entry,
                                        selectionMode = selectionMode,
                                        selected = entry.path in selected,
                                        onClick = { onClick(entry) },
                                        onLongClick = { onLongClick(entry) },
                                    )
                                }
                                if (index != groupEntries.lastIndex) {
                                    RowSeparator(
                                        startIndent = if (viewMode == ViewMode.DETAILED) 76.dp else 60.dp
                                    )
                                }
                            }
                        }
                    }
                    item(key = "gap_$label") { Spacer(Modifier.height(14.dp)) }
                }
            }
        }
    }
}

/** Month heading: tap to collapse, long-press for a sort menu for that month. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MonthHeader(
    label: String,
    summary: MonthSummary?,
    collapsed: Boolean,
    pinned: Boolean,
    sort: Pair<SortField, Boolean>?,
    onClick: () -> Unit,
    onSortPicked: (SortField, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(fsColors.groupedBackground)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuOpen = true },
                )
                .padding(vertical = if (pinned) 10.dp else 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(width = 4.dp, height = 20.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(fsColors.accent),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    color = fsColors.label,
                    maxLines = 1,
                )
                summary?.let {
                    Text(
                        it.line() + if (sort != null) " · by ${sort.first.label.lowercase()}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = fsColors.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                if (collapsed) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                if (collapsed) "Expand" else "Collapse",
                tint = fsColors.accent,
                modifier = Modifier.size(22.dp),
            )
        }

        androidx.compose.material3.DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            modifier = Modifier.background(fsColors.cardSecondary),
        ) {
            Text(
                "Sort $label by",
                style = MaterialTheme.typography.labelSmall,
                color = fsColors.secondaryLabel,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            SortField.entries.forEach { field ->
                val active = (sort?.first ?: Prefs.sortField) == field
                val ascending = if (active) (sort?.second ?: Prefs.sortAscending) else field.defaultAscending
                androidx.compose.material3.DropdownMenuItem(
                    text = {
                        Text(
                            field.label,
                            color = if (active) fsColors.accent else fsColors.label,
                        )
                    },
                    trailingIcon = {
                        if (active) {
                            Icon(
                                if (ascending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                                null,
                                tint = fsColors.accent,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    },
                    onClick = {
                        // Re-picking the active field flips its direction.
                        val nextAscending = if (active) !ascending else field.defaultAscending
                        onSortPicked(field, nextAscending)
                        menuOpen = false
                    },
                )
            }
        }
    }
}

/**
 * Maps an accumulated pinch factor onto the folder's view: pinching out grows
 * the tiles (fewer columns) and eventually reaches the list; pinching in shrinks
 * them, the way the iOS photo grid behaves. Returns the reset accumulator.
 */
fun applyPinch(key: String, mode: ViewMode, accumulated: Float): Float {
    // Timeline is a deliberate grouping, not a zoom level - leave it alone.
    if (mode == ViewMode.TIMELINE) return 1f
    val grid = mode == ViewMode.GRID || mode == ViewMode.GALLERY
    val maxColumns = if (mode == ViewMode.GALLERY) 4 else 6
    when {
        accumulated > 1.35f -> {
            if (grid) {
                val cols = com.shahabcodes.filestorm.data.FolderViews.columnsFor(key, mode)
                if (cols <= 1) {
                    com.shahabcodes.filestorm.data.FolderViews.setView(key, ViewMode.GALLERY)
                } else {
                    com.shahabcodes.filestorm.data.FolderViews.setColumns(key, mode, cols - 1)
                }
            } else {
                com.shahabcodes.filestorm.data.FolderViews.setView(key, ViewMode.GRID)
            }
            return 1f
        }
        accumulated < 0.74f -> {
            if (grid) {
                val cols = com.shahabcodes.filestorm.data.FolderViews.columnsFor(key, mode)
                if (cols >= maxColumns) {
                    com.shahabcodes.filestorm.data.FolderViews.setView(key, ViewMode.LIST)
                } else {
                    com.shahabcodes.filestorm.data.FolderViews.setColumns(key, mode, cols + 1)
                }
            }
            return 1f
        }
    }
    return accumulated
}

/**
 * Two-finger pinch reporting the accumulated zoom factor. Single-finger events
 * are left untouched so normal scrolling still works.
 */
private fun Modifier.pinchToZoom(onZoom: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            if (event.changes.size >= 2) {
                val zoom = event.calculateZoom()
                if (zoom != 1f) {
                    onZoom(zoom)
                    event.changes.forEach { it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}


/** Tighter gaps and softer corners as tiles get smaller, like a photo wall. */
private fun tileGap(columns: Int): androidx.compose.ui.unit.Dp = when {
    columns <= 1 -> 10.dp
    columns == 2 -> 6.dp
    columns == 3 -> 4.dp
    else -> 3.dp
}

private fun tileCorner(columns: Int): androidx.compose.ui.unit.Dp = when {
    columns <= 1 -> 20.dp
    columns == 2 -> 16.dp
    columns == 3 -> 12.dp
    else -> 9.dp
}

private val monthFormat = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault())

private fun monthLabel(millis: Long): String =
    if (millis <= 0) "Unknown date" else monthFormat.format(java.util.Date(millis))

/** Sticky month heading with the group's item count and total size. */
@Composable
private fun TimelineHeader(label: String, entries: List<FsEntry>) {
    val files = entries.count { !it.isDirectory }
    val folders = entries.size - files
    val bytes = entries.sumOf { if (it.isDirectory) 0L else it.size }
    Column(
        Modifier
            .fillMaxWidth()
            .background(fsColors.groupedBackground)
            .padding(top = 6.dp, bottom = 8.dp, start = 4.dp, end = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 4.dp, height = 18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(fsColors.accent),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = fsColors.label,
                modifier = Modifier.weight(1f),
            )
            Text(
                buildString {
                    if (folders > 0) append("$folders folder${if (folders == 1) "" else "s"}")
                    if (folders > 0 && files > 0) append(" · ")
                    if (files > 0) append("$files file${if (files == 1) "" else "s"}")
                    if (bytes > 0) append(" · ${Formatters.bytes(bytes)}")
                },
                style = MaterialTheme.typography.labelSmall,
                color = fsColors.secondaryLabel,
            )
        }
    }
}

private val kindLabels = mapOf(
    FileKind.FOLDER to "Folder",
    FileKind.IMAGE to "Image",
    FileKind.VIDEO to "Video",
    FileKind.AUDIO to "Audio",
    FileKind.DOCUMENT to "Document",
    FileKind.ARCHIVE to "Archive",
    FileKind.APK to "App package",
    FileKind.OTHER to "File",
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailedFileRow(
    entry: FsEntry,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) fsColors.accent.copy(alpha = 0.08f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            SelectionCircle(selected = selected)
            Spacer(Modifier.width(12.dp))
        }
        FileIconView(entry, size = 48.dp, cornerRadius = 12.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = com.shahabcodes.filestorm.ui.components.entryNameWeight(entry),
                color = fsColors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    append(kindLabels[entry.kind] ?: "File")
                    if (!entry.isDirectory) append(" · ${Formatters.bytes(entry.size)}")
                    else if (entry.childCount >= 0) append(" · ${entry.childCount} items")
                },
                style = MaterialTheme.typography.bodySmall,
                color = fsColors.secondaryLabel,
                maxLines = 1,
            )
            Text(
                Formatters.fullDate(entry.lastModified),
                style = MaterialTheme.typography.bodySmall,
                color = fsColors.secondaryLabel,
                maxLines = 1,
            )
        }
        if (!selectionMode && entry.isDirectory) {
            Icon(
                Icons.Rounded.ChevronRight, null,
                tint = fsColors.secondaryLabel.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridTile(
    entry: FsEntry,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) fsColors.accent.copy(alpha = 0.12f) else fsColors.card)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(vertical = 12.dp, horizontal = 6.dp)
                .fillMaxWidth(),
        ) {
            FileIconView(entry, size = 54.dp, cornerRadius = 13.dp)
            Spacer(Modifier.height(7.dp))
            Text(
                entry.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = com.shahabcodes.filestorm.ui.components.entryNameWeight(entry),
                color = fsColors.label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.height(34.dp),
            )
            Text(
                if (entry.isDirectory) "${entry.childCount.coerceAtLeast(0)} items"
                else Formatters.bytes(entry.size),
                style = MaterialTheme.typography.labelSmall,
                color = fsColors.secondaryLabel,
                maxLines = 1,
            )
        }
        if (selectionMode) {
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                SelectionCircle(selected = selected, size = 22.dp)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryTile(
    entry: FsEntry,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val showThumb = !entry.isDirectory && (entry.kind == FileKind.IMAGE || entry.kind == FileKind.VIDEO)
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(fsColors.card)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .aspectRatio(1f),
    ) {
        if (showThumb) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(entry.path))
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (entry.kind == FileKind.VIDEO) {
                Icon(
                    Icons.Rounded.PlayCircleFilled, null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.Center).size(40.dp),
                )
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    kindColor(entry.kind, fsColors.isDark).copy(alpha = 0.95f),
                                    kindColor(entry.kind, fsColors.isDark).copy(alpha = 0.7f),
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        kindIcon(entry.kind), null,
                        tint = Color.White, modifier = Modifier.size(38.dp),
                    )
                }
            }
        }

        // Bottom name scrim
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = if (showThumb) 0.65f else 0.35f))
                    )
                )
                .padding(horizontal = 10.dp)
                .padding(top = 18.dp, bottom = 8.dp),
        ) {
            Column {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = com.shahabcodes.filestorm.ui.components.entryNameWeight(entry),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (entry.isDirectory) "${entry.childCount.coerceAtLeast(0)} items"
                    else "${Formatters.bytes(entry.size)} · ${Formatters.shortDate(entry.lastModified)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                )
            }
        }

        if (selected) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(fsColors.accent.copy(alpha = 0.25f))
            )
        }
        if (selectionMode) {
            Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                SelectionCircle(selected = selected, size = 24.dp)
            }
        }
    }
}
