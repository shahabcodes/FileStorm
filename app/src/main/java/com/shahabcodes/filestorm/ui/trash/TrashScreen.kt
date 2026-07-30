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
    var busyLabel by remember { mutableStateOf<String?>(null) }

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
        Spacer(Modifier.height(12.dp))

        if (items.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(13.dp))
                        .background(fsColors.accent)
                        .pressScale {
                            if (busyLabel == null) {
                                scope.launch {
                                    busyLabel = "Restoring all ${items.size} item(s)…"
                                    TrashManager.restore(items)
                                    selected = emptySet()
                                    busyLabel = null
                                }
                            }
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.RestoreFromTrash, null,
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Restore All",
                            color = androidx.compose.ui.graphics.Color.White,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(13.dp))
                        .background(fsColors.red.copy(alpha = 0.12f))
                        .pressScale { if (busyLabel == null) confirmEmpty = true }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.DeleteForever, null,
                            tint = fsColors.red, modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Empty Trash",
                            color = fsColors.red,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

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
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        (if (item.isDirectory) "Folder" else Formatters.bytes(item.size)) +
                                            " · deleted ${Formatters.fullDate(item.deletedAt)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = fsColors.secondaryLabel,
                                        maxLines = 1,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "Restores to",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = fsColors.secondaryLabel.copy(alpha = 0.75f),
                                    )
                                    Text(
                                        item.originalPath.replace(
                                            FileRepository.rootPath, "Internal storage"
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = fsColors.accent,
                                        maxLines = 3,
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
                            enabled = busyLabel == null,
                            onClick = {
                                scope.launch {
                                    busyLabel = "Restoring ${selectedItems.size} item(s)…"
                                    TrashManager.restore(selectedItems)
                                    selected = emptySet()
                                    busyLabel = null
                                }
                            },
                        )
                        ActionPill(
                            Icons.Rounded.DeleteForever, "Delete Forever",
                            enabled = busyLabel == null,
                            tint = fsColors.red,
                            onClick = { confirmForever = true },
                        )
                    }
                }
            }
        }
    }

    busyLabel?.let { label ->
        androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(fsColors.card)
                    .padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                androidx.compose.material3.CircularProgressIndicator(color = fsColors.accent)
                Spacer(Modifier.height(14.dp))
                Text(label, color = fsColors.label, style = MaterialTheme.typography.bodyMedium)
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
                        busyLabel = "Deleting ${selectedItems.size} item(s)…"
                        TrashManager.deleteForever(selectedItems)
                        selected = emptySet()
                        busyLabel = null
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
                        busyLabel = "Emptying trash…"
                        TrashManager.emptyTrash()
                        selected = emptySet()
                        busyLabel = null
                    }
                }) { Text("Empty Trash", color = fsColors.red) }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmpty = false }) { Text("Cancel", color = fsColors.secondaryLabel) }
            },
        )
    }
}
