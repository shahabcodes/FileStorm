package com.shahabcodes.filestorm.ui.browser

import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.shahabcodes.filestorm.data.FileRepository
import com.shahabcodes.filestorm.data.FsEntry
import com.shahabcodes.filestorm.data.SortField
import com.shahabcodes.filestorm.ui.components.ActionPill
import com.shahabcodes.filestorm.ui.components.FileIconView
import com.shahabcodes.filestorm.ui.components.SelectionCircle
import com.shahabcodes.filestorm.ui.theme.fsColors
import com.shahabcodes.filestorm.util.Formatters
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
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
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            SelectionCircle(selected = selected)
            Spacer(Modifier.width(12.dp))
        }
        FileIconView(entry)
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
            Spacer(Modifier.height(1.dp))
            Text(
                if (entry.isDirectory) {
                    val c = entry.childCount
                    if (c >= 0) "$c item${if (c == 1) "" else "s"} · ${Formatters.fileDate(entry.lastModified)}"
                    else Formatters.fileDate(entry.lastModified)
                } else {
                    "${Formatters.bytes(entry.size)} · ${Formatters.fileDate(entry.lastModified)}"
                },
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

/** Bottom action bar shown in selection mode: Copy / Move / Delete. */
@Composable
fun SelectionActionBar(
    selectedCount: Int,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(fsColors.card)
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActionPill(Icons.Rounded.ContentCopy, "Copy", enabled = selectedCount > 0, onClick = onCopy)
            ActionPill(Icons.Rounded.DriveFileMove, "Move", enabled = selectedCount > 0, onClick = onMove)
            ActionPill(
                Icons.Rounded.Delete, "Delete",
                enabled = selectedCount > 0, tint = fsColors.red, onClick = onDelete,
            )
        }
    }
}

/** Modal sheet with an iOS-styled Material date-range picker used for date-to-date selection. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeSheet(
    onDismiss: () -> Unit,
    onConfirm: (startMillis: Long, endMillis: Long) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pickerState = rememberDateRangePickerState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = fsColors.card,
    ) {
        Column(Modifier.padding(bottom = 12.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Select by date",
                    style = MaterialTheme.typography.titleLarge,
                    color = fsColors.label,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) { Text("Cancel", color = fsColors.secondaryLabel) }
                TextButton(
                    enabled = pickerState.selectedStartDateMillis != null,
                    onClick = {
                        val start = pickerState.selectedStartDateMillis ?: return@TextButton
                        // Single-day selection is allowed: end defaults to start.
                        val endRaw = pickerState.selectedEndDateMillis ?: start
                        // End of day inclusive.
                        onConfirm(start, endRaw + 24L * 60 * 60 * 1000 - 1)
                    },
                ) { Text("Select", color = fsColors.accent) }
            }
            DateRangePicker(
                state = pickerState,
                showModeToggle = false,
                modifier = Modifier.height(440.dp),
                colors = DatePickerDefaults.colors(
                    containerColor = fsColors.card,
                    selectedDayContainerColor = fsColors.accent,
                    dayInSelectionRangeContainerColor = fsColors.accent.copy(alpha = 0.15f),
                    dayInSelectionRangeContentColor = fsColors.label,
                    selectedDayContentColor = Color.White,
                ),
            )
        }
    }
}

/** Modal sheet to choose a destination folder by navigating the tree. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FolderPickerSheet(
    title: String,
    confirmLabel: String,
    excludedPaths: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pickerContext = androidx.compose.ui.platform.LocalContext.current
    var currentPath by rememberSaveable { mutableStateOf(FileRepository.rootPath) }
    var folders by remember { mutableStateOf<List<FsEntry>>(emptyList()) }
    var refreshTick by remember { mutableStateOf(0) }
    var showNewFolder by remember { mutableStateOf(false) }
    var renameFolder by remember { mutableStateOf<FsEntry?>(null) }

    LaunchedEffect(currentPath, refreshTick) {
        folders = FileRepository.list(currentPath, SortField.NAME, ascending = true)
            .filter { it.isDirectory }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = fsColors.groupedBackground,
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = fsColors.label)
                    Text(
                        currentPath.removePrefix(FileRepository.rootPath).ifEmpty { "/" }
                            .replace(Regex("^/"), "Internal storage/").trimEnd('/').ifEmpty { "Internal storage" },
                        style = MaterialTheme.typography.bodySmall,
                        color = fsColors.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    Icons.Rounded.CreateNewFolder, "New folder",
                    tint = fsColors.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .combinedClickable(onClick = { showNewFolder = true }, onLongClick = null)
                        .padding(8.dp)
                        .size(24.dp),
                )
                TextButton(onClick = onDismiss) { Text("Cancel", color = fsColors.secondaryLabel) }
            }
            Text(
                "Tip: long-press a folder to rename it",
                style = MaterialTheme.typography.labelSmall,
                color = fsColors.secondaryLabel.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(6.dp))

            LazyColumn(
                Modifier
                    .height(380.dp)
                    .padding(horizontal = 16.dp),
            ) {
                if (currentPath != FileRepository.rootPath) {
                    itemsIndexed(listOf("..")) { _, _ ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(fsColors.card)
                                .combinedClickable(onClick = {
                                    currentPath = File(currentPath).parent ?: FileRepository.rootPath
                                }, onLongClick = null)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.ArrowUpward, null, tint = fsColors.accent, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Up one level", color = fsColors.accent, style = MaterialTheme.typography.bodyLarge)
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
                itemsIndexed(folders, key = { _, f -> f.path }) { _, folder ->
                    val excluded = folder.path in excludedPaths
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(fsColors.card)
                            .combinedClickable(
                                enabled = !excluded,
                                onClick = {
                                    openFolderGated(pickerContext, folder.path, folder.name) {
                                        currentPath = folder.path
                                    }
                                },
                                onLongClick = { renameFolder = folder },
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Folder, null,
                            tint = if (excluded) fsColors.secondaryLabel.copy(alpha = 0.4f) else fsColors.accent,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            folder.name,
                            color = if (excluded) fsColors.secondaryLabel.copy(alpha = 0.5f) else fsColors.label,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Rounded.ChevronRight, null,
                            tint = fsColors.secondaryLabel.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(fsColors.accent)
                    .combinedClickable(onClick = { onConfirm(currentPath) }, onLongClick = null)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(confirmLabel, color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    if (showNewFolder) {
        NameDialog(
            title = "New Folder",
            initial = "",
            confirmLabel = "Create",
            onDismiss = { showNewFolder = false },
            onConfirm = { name ->
                showNewFolder = false
                FileRepository.createFolder(currentPath, name)
                refreshTick++
            },
        )
    }

    renameFolder?.let { folder ->
        NameDialog(
            title = "Rename Folder",
            initial = folder.name,
            confirmLabel = "Rename",
            onDismiss = { renameFolder = null },
            onConfirm = { name ->
                renameFolder = null
                FileRepository.rename(folder, name)
                refreshTick++
            },
        )
    }
}

@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = fsColors.card,
        title = { Text(title, color = fsColors.label, style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = fsColors.accent,
                    unfocusedBorderColor = fsColors.separator,
                    focusedTextColor = fsColors.label,
                    unfocusedTextColor = fsColors.label,
                    cursorColor = fsColors.accent,
                ),
                shape = RoundedCornerShape(10.dp),
            )
        },
        confirmButton = {
            TextButton(
                enabled = text.trim().isNotEmpty() && !text.contains('/'),
                onClick = { onConfirm(text.trim()) },
            ) { Text(confirmLabel, color = fsColors.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = fsColors.secondaryLabel) }
        },
    )
}

@Composable
fun ConfirmDeleteDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = fsColors.card,
        title = { Text("Move $count item${if (count == 1) "" else "s"} to Trash?", color = fsColors.label) },
        text = {
            Text(
                "Items go to File Storm's Trash, where you can restore them later or delete them forever.",
                color = fsColors.secondaryLabel,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Move to Trash", color = fsColors.red) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = fsColors.secondaryLabel) }
        },
    )
}

/** Opens a folder, first passing biometric auth when the folder is locked. */
fun openFolderGated(
    context: android.content.Context,
    path: String,
    name: String,
    onOpen: () -> Unit,
) {
    if (com.shahabcodes.filestorm.data.FolderLocks.requiresAuth(path)) {
        val activity = context as? androidx.fragment.app.FragmentActivity ?: return
        com.shahabcodes.filestorm.ui.Biometrics.prompt(
            activity,
            title = "Unlock \"$name\"",
            subtitle = "This folder is protected",
            onSuccess = {
                com.shahabcodes.filestorm.data.FolderLocks.markUnlocked(path)
                onOpen()
            },
        )
    } else {
        onOpen()
    }
}

fun openFile(context: android.content.Context, entry: FsEntry) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", entry.toFile())
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(entry.extension) ?: "*/*"
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }
}
