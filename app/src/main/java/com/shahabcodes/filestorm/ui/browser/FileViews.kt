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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PlayCircleFilled
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.shahabcodes.filestorm.data.ViewMode
import com.shahabcodes.filestorm.ui.components.FileIconView
import com.shahabcodes.filestorm.ui.components.RowSeparator
import com.shahabcodes.filestorm.ui.components.SelectionCircle
import com.shahabcodes.filestorm.ui.components.kindColor
import com.shahabcodes.filestorm.ui.components.kindIcon
import com.shahabcodes.filestorm.ui.theme.fsColors
import com.shahabcodes.filestorm.util.Formatters
import java.io.File

/** Renders entries in the user's chosen view mode with shared selection behavior. */
@Composable
fun FileListView(
    entries: List<FsEntry>,
    selectionMode: Boolean,
    selected: Set<String>,
    contentPadding: PaddingValues,
    onClick: (FsEntry) -> Unit,
    onLongClick: (FsEntry) -> Unit,
) {
    when (Prefs.viewMode) {
        ViewMode.LIST, ViewMode.DETAILED -> LazyColumn(
            Modifier.fillMaxSize(),
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
                    if (Prefs.viewMode == ViewMode.DETAILED) {
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
                        RowSeparator(startIndent = if (Prefs.viewMode == ViewMode.DETAILED) 76.dp else 60.dp)
                    }
                }
            }
        }

        ViewMode.GRID -> LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
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

        ViewMode.GALLERY -> LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(entries, key = { _, e -> e.path }) { _, entry ->
                GalleryTile(
                    entry = entry,
                    selectionMode = selectionMode,
                    selected = entry.path in selected,
                    onClick = { onClick(entry) },
                    onLongClick = { onLongClick(entry) },
                )
            }
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
