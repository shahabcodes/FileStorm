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
import androidx.compose.runtime.LaunchedEffect
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
    /**
     * Changes whenever the *criteria* change — search text, date filter, sort.
     * The list then jumps back to the top, because a filtered list left at row
     * 12,000 looks empty and the scrollbar sits in the wrong place. Deliberately
     * not the entries themselves: a file being added or renamed must not throw
     * away where the user was.
     */
    scrollResetKey: Any? = null,
    onClick: (FsEntry) -> Unit,
    onLongClick: (FsEntry) -> Unit,
) {
    if (grouped) {
        GroupedByMonth(
            entries = entries,
            scrollResetKey = scrollResetKey,
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
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val staggeredState =
        androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    ResetScrollOnChange(scrollResetKey, listState, gridState, staggeredState)

    Box(Modifier.fillMaxSize()) {
    when (viewMode) {
        ViewMode.LIST, ViewMode.DETAILED -> LazyColumn(
            Modifier.fillMaxSize().pinchToZoom(onZoom),
            state = listState,
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
            state = gridState,
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
                state = gridState,
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
                state = staggeredState,
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

        val handle = when (viewMode) {
            ViewMode.LIST, ViewMode.DETAILED, ViewMode.TIMELINE -> ScrollHandle(
                itemCount = entries.size,
                firstVisible = listState.firstVisibleItemIndex,
                scrolling = listState.isScrollInProgress,
                jumpTo = { listState.scrollToItem(it) },
            )
            ViewMode.MOSAIC -> ScrollHandle(
                itemCount = entries.size,
                firstVisible = staggeredState.firstVisibleItemIndex,
                scrolling = staggeredState.isScrollInProgress,
                jumpTo = { staggeredState.scrollToItem(it) },
            )
            else -> ScrollHandle(
                itemCount = entries.size,
                firstVisible = gridState.firstVisibleItemIndex,
                scrolling = gridState.isScrollInProgress,
                jumpTo = { gridState.scrollToItem(it) },
            )
        }
        FastScroller(
            handle = handle,
            labelFor = { index -> scrubberLabel(entries.getOrNull(index)) },
        )
    }
}

/** What the scrubber bubble says: month when sorting by date, else initial. */
private fun scrubberLabel(entry: FsEntry?): String {
    if (entry == null) return ""
    return when (Prefs.sortField) {
        SortField.NAME -> entry.name.take(1).uppercase()
        SortField.SIZE -> Formatters.bytes(entry.size)
        SortField.TYPE -> entry.extension.uppercase().ifEmpty { "FILE" }
        SortField.DATE -> monthLabel(entry.lastModified)
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

/** One row of the grouped view: a month heading, a day heading, an item or a gap. */
private sealed interface GroupSlot {
    val month: String

    data class MonthRow(override val month: String) : GroupSlot
    data class DayRow(override val month: String, val label: String, val count: Int) : GroupSlot
    data class ItemRow(
        override val month: String,
        val entry: FsEntry,
        val first: Boolean,
        val last: Boolean,
    ) : GroupSlot
    data class GapRow(override val month: String) : GroupSlot
}

/**
 * Any layout, split under Month Year headings, with day sub-headings inside a
 * month whenever that month is date-sorted and spans more than one day. The
 * heading of whatever is on top stays pinned, months collapse, and a scrubber
 * appears for long folders.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupedByMonth(
    entries: List<FsEntry>,
    scrollResetKey: Any? = null,
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
    // Grouping, per-month sorting and slot building all scale with the folder,
    // so for tens of thousands of files they cannot run during composition —
    // that blocks the frame and reads as a freeze. Compute on a worker and let
    // the previous result stay on screen until the new one is ready.
    val ascending = Prefs.sortAscending
    val sortField = Prefs.sortField
    val groups by androidx.compose.runtime.produceState(
        initialValue = emptyList<Pair<String, List<FsEntry>>>(),
        entries, ascending, sortField, monthSorts,
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            entries
                .groupBy { monthLabel(it.lastModified) }
                .entries
                .sortedWith(compareBy { group -> group.value.maxOfOrNull { it.lastModified } ?: 0L })
                .let { if (ascending) it else it.reversed() }
                .map { (label, items) ->
                    val (field, asc) = monthSorts[label] ?: (sortField to ascending)
                    label to com.shahabcodes.filestorm.data.FileRepository.sortEntries(
                        items, field, asc,
                    )
                }
        }
    }
    val summaries = remember(groups) { groups.associate { it.first to summarise(it.second) } }

    val slots by androidx.compose.runtime.produceState(
        initialValue = emptyList<GroupSlot>(),
        groups, collapsedMonths, monthSorts,
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        buildList {
            groups.forEach { (label, items) ->
                add(GroupSlot.MonthRow(label))
                if (label !in collapsedMonths) {
                    val field = monthSorts[label]?.first ?: Prefs.sortField
                    val ascending = monthSorts[label]?.second ?: Prefs.sortAscending
                    val days = if (field == SortField.DATE) {
                        items.groupBy { dayLabel(it.lastModified) }
                            .entries
                            .sortedWith(compareBy { d -> d.value.maxOfOrNull { it.lastModified } ?: 0L })
                            .let { if (ascending) it else it.reversed() }
                    } else emptyList()

                    if (days.size > 1) {
                        days.forEach { (dayName, dayItems) ->
                            add(GroupSlot.DayRow(label, dayName, dayItems.size))
                            dayItems.forEachIndexed { index, entry ->
                                add(
                                    GroupSlot.ItemRow(
                                        label, entry,
                                        first = index == 0,
                                        last = index == dayItems.lastIndex,
                                    )
                                )
                            }
                        }
                    } else {
                        items.forEachIndexed { index, entry ->
                            add(
                                GroupSlot.ItemRow(
                                    label, entry,
                                    first = index == 0,
                                    last = index == items.lastIndex,
                                )
                            )
                        }
                    }
                }
                add(GroupSlot.GapRow(label))
            }
        }
        }
    }

    val tiled = viewMode == ViewMode.GRID || viewMode == ViewMode.GALLERY ||
        viewMode == ViewMode.MOSAIC
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    ResetScrollOnChange(scrollResetKey, listState, gridState, null)

    val firstVisible = if (tiled) gridState.firstVisibleItemIndex else listState.firstVisibleItemIndex
    val topSlot = slots.getOrNull(firstVisible)
    val pinnedLabel = topSlot?.month ?: groups.firstOrNull()?.first
    // While a month's own heading is on screen there is no need to repeat it.
    val showPinned = pinnedLabel != null && topSlot !is GroupSlot.MonthRow

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

        Box(Modifier.fillMaxSize()) {
            if (tiled) {
                val span = columns.coerceIn(
                    1,
                    if (viewMode == ViewMode.GALLERY || viewMode == ViewMode.MOSAIC) 4 else 6,
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(span),
                    state = gridState,
                    modifier = Modifier.fillMaxSize().pinchToZoom(onZoom),
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(tileGap(span)),
                    horizontalArrangement = Arrangement.spacedBy(tileGap(span)),
                ) {
                    itemsIndexed(
                        slots,
                        key = { index, slot ->
                            if (slot is GroupSlot.ItemRow) slot.entry.path else "slot$index"
                        },
                        span = { _, slot ->
                            if (slot is GroupSlot.ItemRow) {
                                androidx.compose.foundation.lazy.grid.GridItemSpan(1)
                            } else {
                                androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan)
                            }
                        },
                    ) { _, slot ->
                        when (slot) {
                            is GroupSlot.MonthRow -> MonthHeader(
                                label = slot.month,
                                summary = summaries[slot.month],
                                collapsed = slot.month in collapsedMonths,
                                pinned = false,
                                sort = monthSorts[slot.month],
                                onClick = { onToggleMonth(slot.month) },
                                onSortPicked = { f, a -> onMonthSort(slot.month, f, a) },
                            )
                            is GroupSlot.DayRow -> DayHeader(slot.label, slot.count)
                            is GroupSlot.GapRow -> Spacer(Modifier.height(6.dp))
                            is GroupSlot.ItemRow -> {
                                if (viewMode == ViewMode.GALLERY || viewMode == ViewMode.MOSAIC) {
                                    PhotoTile(
                                        entry = slot.entry,
                                        selectionMode = selectionMode,
                                        selected = slot.entry.path in selected,
                                        corner = tileCorner(span),
                                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                        onClick = { onClick(slot.entry) },
                                        onLongClick = { onLongClick(slot.entry) },
                                    )
                                } else {
                                    GridTile(
                                        entry = slot.entry,
                                        selectionMode = selectionMode,
                                        selected = slot.entry.path in selected,
                                        onClick = { onClick(slot.entry) },
                                        onLongClick = { onLongClick(slot.entry) },
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().pinchToZoom(onZoom),
                    state = listState,
                    contentPadding = contentPadding,
                ) {
                    itemsIndexed(
                        slots,
                        key = { index, slot ->
                            if (slot is GroupSlot.ItemRow) slot.entry.path else "slot$index"
                        },
                    ) { _, slot ->
                        when (slot) {
                            is GroupSlot.MonthRow -> MonthHeader(
                                label = slot.month,
                                summary = summaries[slot.month],
                                collapsed = slot.month in collapsedMonths,
                                pinned = false,
                                sort = monthSorts[slot.month],
                                onClick = { onToggleMonth(slot.month) },
                                onSortPicked = { f, a -> onMonthSort(slot.month, f, a) },
                            )
                            is GroupSlot.DayRow -> DayHeader(slot.label, slot.count)
                            is GroupSlot.GapRow -> Spacer(Modifier.height(14.dp))
                            is GroupSlot.ItemRow -> {
                                val shape = when {
                                    slot.first && slot.last -> RoundedCornerShape(16.dp)
                                    slot.first -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                                    slot.last -> RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                                    else -> RoundedCornerShape(0.dp)
                                }
                                Column(Modifier.clip(shape).background(fsColors.card)) {
                                    if (viewMode == ViewMode.DETAILED) {
                                        DetailedFileRow(
                                            entry = slot.entry,
                                            selectionMode = selectionMode,
                                            selected = slot.entry.path in selected,
                                            onClick = { onClick(slot.entry) },
                                            onLongClick = { onLongClick(slot.entry) },
                                        )
                                    } else {
                                        FileRow(
                                            entry = slot.entry,
                                            selectionMode = selectionMode,
                                            selected = slot.entry.path in selected,
                                            onClick = { onClick(slot.entry) },
                                            onLongClick = { onLongClick(slot.entry) },
                                        )
                                    }
                                    if (!slot.last) {
                                        RowSeparator(
                                            startIndent = if (viewMode == ViewMode.DETAILED) 76.dp else 60.dp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            FastScroller(
                handle = if (tiled) ScrollHandle(
                    itemCount = slots.size,
                    firstVisible = gridState.firstVisibleItemIndex,
                    scrolling = gridState.isScrollInProgress,
                    jumpTo = { gridState.scrollToItem(it) },
                ) else ScrollHandle(
                    itemCount = slots.size,
                    firstVisible = listState.firstVisibleItemIndex,
                    scrolling = listState.isScrollInProgress,
                    jumpTo = { listState.scrollToItem(it) },
                ),
                labelFor = { index -> slots.getOrNull(index)?.month ?: "" },
            )
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


private val dayFormat = java.text.SimpleDateFormat("EEE, d MMMM", java.util.Locale.getDefault())

/** "Today" / "Yesterday" / "Mon, 15 March" for the sub-headings inside a month. */
/**
 * Date labels are asked for once per file, so a 35k-file folder used to build
 * three Calendars and run a formatter 35k times on the composition thread —
 * which is what made big folders freeze. Both labels are now keyed by local day
 * and cached, so the formatter runs once per distinct day instead.
 */
private object DateLabels {
    private val dayCache = HashMap<Long, String>()
    private val monthCache = HashMap<Long, String>()
    private var todayIndex = Long.MIN_VALUE
    private var computedAt = 0L

    private fun dayIndexOf(millis: Long): Long {
        val offset = java.util.TimeZone.getDefault().getOffset(millis)
        return Math.floorDiv(millis + offset, 86_400_000L)
    }

    /** "Today" has to stop meaning yesterday once the clock rolls over. */
    private fun refreshToday() {
        val now = System.currentTimeMillis()
        if (now - computedAt < 60_000L && todayIndex != Long.MIN_VALUE) return
        val fresh = dayIndexOf(now)
        if (fresh != todayIndex) {
            dayCache.clear()
            todayIndex = fresh
        }
        computedAt = now
    }

    @Synchronized
    fun day(millis: Long): String {
        if (millis <= 0) return "Unknown date"
        refreshToday()
        val index = dayIndexOf(millis)
        return dayCache.getOrPut(index) {
            when (index) {
                todayIndex -> "Today"
                todayIndex - 1 -> "Yesterday"
                else -> dayFormat.format(java.util.Date(millis))
            }
        }
    }

    @Synchronized
    fun month(millis: Long): String {
        if (millis <= 0) return "Unknown date"
        // One entry per day is plenty; a month has at most 31 of them.
        return monthCache.getOrPut(dayIndexOf(millis)) {
            monthFormat.format(java.util.Date(millis))
        }
    }
}

private fun dayLabel(millis: Long): String = DateLabels.day(millis)

/** Small heading between days inside a month. */
@Composable
private fun DayHeader(label: String, count: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(fsColors.groupedBackground)
            .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = fsColors.secondaryLabel,
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(fsColors.separator),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "$count",
            style = MaterialTheme.typography.labelSmall,
            color = fsColors.secondaryLabel.copy(alpha = 0.8f),
        )
    }
}

private val monthFormat = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault())

private fun monthLabel(millis: Long): String = DateLabels.month(millis)

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

/**
 * Sends every list back to the top when the filter criteria change, skipping the
 * very first composition so that opening a folder does not fight state
 * restoration.
 */
@Composable
private fun ResetScrollOnChange(
    key: Any?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    staggeredState: androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState?,
) {
    var lastKey by remember { mutableStateOf(key) }
    LaunchedEffect(key) {
        if (key == lastKey) return@LaunchedEffect
        lastKey = key
        listState.scrollToItem(0)
        gridState.scrollToItem(0)
        staggeredState?.scrollToItem(0)
    }
}
