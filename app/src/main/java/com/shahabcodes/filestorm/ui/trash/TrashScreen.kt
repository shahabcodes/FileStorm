package com.shahabcodes.filestorm.ui.trash

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shahabcodes.filestorm.data.FileRepository
import com.shahabcodes.filestorm.data.FsEntry
import com.shahabcodes.filestorm.data.TrashItem
import com.shahabcodes.filestorm.data.TrashManager
import com.shahabcodes.filestorm.ui.components.ActionPill
import com.shahabcodes.filestorm.ui.components.FileIconView
import com.shahabcodes.filestorm.ui.components.GroupedCard
import com.shahabcodes.filestorm.ui.components.RowSeparator
import com.shahabcodes.filestorm.ui.components.SelectionCircle
import com.shahabcodes.filestorm.ui.components.pressScale
import com.shahabcodes.filestorm.ui.theme.fsColors
import com.shahabcodes.filestorm.util.Formatters
import kotlinx.coroutines.launch

@Composable
fun TrashScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val items = TrashManager.items
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmForever by remember { mutableStateOf(false) }
    var confirmEmpty by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { TrashManager.refresh() }

    val selectedItems = items.filter { it.trashName in selected }

    Column(
        Modifier
            .fillMaxSize()
            .background(fsColors.groupedBackground)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.pressScale(onBack).padding(horizontal = 6.dp, vertical = 6.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBackIos, null,
                    tint = fsColors.accent, modifier = Modifier.size(18.dp),
                )
                Text("Back", color = fsColors.accent, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.weight(1f))
            if (items.isNotEmpty()) {
                Text(
                    if (selected.size == items.size) "Deselect All" else "Select All",
                    color = fsColors.accent,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .pressScale {
                            selected = if (selected.size == items.size) emptySet()
                            else items.map { it.trashName }.toSet()
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
                Text(
                    "Empty",
                    color = fsColors.red,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .pressScale { confirmEmpty = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }

        Text(
            "Trash",
            style = MaterialTheme.typography.headlineLarge,
            color = fsColors.label,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            if (items.isEmpty()) "Nothing in the trash"
            else "${items.size} item${if (items.size == 1) "" else "s"} · ${Formatters.bytes(items.sumOf { it.size })} · tap to select",
            style = MaterialTheme.typography.bodySmall,
            color = fsColors.secondaryLabel,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(10.dp))

        Box(Modifier.weight(1f)) {
            if (items.isEmpty()) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Rounded.DeleteOutline, null,
                        tint = fsColors.secondaryLabel.copy(alpha = 0.4f),
                        modifier = Modifier.size(52.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Deleted items appear here and can be restored",
                        color = fsColors.secondaryLabel,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, bottom = 120.dp
                    ),
                ) {
                    itemsIndexed(items, key = { _, item -> item.trashName }) { index, item ->
                        val shape = when {
                            items.size == 1 -> RoundedCornerShape(16.dp)
                            index == 0 -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                            index == items.lastIndex -> RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                            else -> RoundedCornerShape(0.dp)
                        }
                        Column(Modifier.clip(shape).background(fsColors.card)) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .pressScale {
                                        selected = if (item.trashName in selected) selected - item.trashName
                                        else selected + item.trashName
                                    }
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SelectionCircle(selected = item.trashName in selected)
                                Spacer(Modifier.width(12.dp))
                                FileIconView(
                                    FsEntry(
                                        path = item.trashFile().absolutePath,
                                        name = item.name,
                                        isDirectory = item.isDirectory,
                                        size = item.size,
                                        lastModified = item.deletedAt,
                                        kind = FsEntry.kindOf(item.name, item.isDirectory),
                                    )
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = fsColors.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "Deleted ${Formatters.fileDate(item.deletedAt)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = fsColors.secondaryLabel,
                                        maxLines = 1,
                                    )
                                    Text(
                                        "From " + (java.io.File(item.originalPath).parent
                                            ?.replace(FileRepository.rootPath, "Internal storage")
                                            ?: "Internal storage"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = fsColors.secondaryLabel,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (index != items.lastIndex) RowSeparator()
                        }
                    }
                }
            }

            if (selected.isNotEmpty()) {
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .background(fsColors.card)
                        .navigationBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ActionPill(
                            Icons.Rounded.RestoreFromTrash, "Restore",
                            onClick = {
                                scope.launch {
                                    TrashManager.restore(selectedItems)
                                    selected = emptySet()
                                }
                            },
                        )
                        ActionPill(
                            Icons.Rounded.DeleteForever, "Delete Forever",
                            tint = fsColors.red,
                            onClick = { confirmForever = true },
                        )
                    }
                }
            }
        }
    }

    if (confirmForever) {
        AlertDialog(
            onDismissRequest = { confirmForever = false },
            containerColor = fsColors.card,
            title = { Text("Delete ${selected.size} item(s) forever?", color = fsColors.label) },
            text = { Text("This permanently erases them. It cannot be undone.", color = fsColors.secondaryLabel) },
            confirmButton = {
                TextButton(onClick = {
                    confirmForever = false
                    scope.launch {
                        TrashManager.deleteForever(selectedItems)
                        selected = emptySet()
                    }
                }) { Text("Delete Forever", color = fsColors.red) }
            },
            dismissButton = {
                TextButton(onClick = { confirmForever = false }) { Text("Cancel", color = fsColors.secondaryLabel) }
            },
        )
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            containerColor = fsColors.card,
            title = { Text("Empty the trash?", color = fsColors.label) },
            text = {
                Text(
                    "All ${items.size} item(s) will be permanently erased. This cannot be undone.",
                    color = fsColors.secondaryLabel,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmpty = false
                    scope.launch {
                        TrashManager.emptyTrash()
                        selected = emptySet()
                    }
                }) { Text("Empty Trash", color = fsColors.red) }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmpty = false }) { Text("Cancel", color = fsColors.secondaryLabel) }
            },
        )
    }
}
