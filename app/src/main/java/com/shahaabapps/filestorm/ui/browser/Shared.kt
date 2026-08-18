package com.shahaabapps.filestorm.ui.browser

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
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import com.shahaabapps.filestorm.ui.components.FsDialog
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
import com.shahaabapps.filestorm.data.FileRepository
import com.shahaabapps.filestorm.data.FsEntry
import com.shahaabapps.filestorm.data.SortField
import com.shahaabapps.filestorm.ui.components.ActionPill
import com.shahaabapps.filestorm.ui.components.FileIconView
import com.shahaabapps.filestorm.ui.components.SelectionCircle
import com.shahaabapps.filestorm.ui.theme.fsColors
import com.shahaabapps.filestorm.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
                fontWeight = com.shahaabapps.filestorm.ui.components.entryNameWeight(entry),
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

/** Bottom action bar shown in selection mode: Share / Copy / Move / Delete. */
@Composable
fun SelectionActionBar(
    selectedCount: Int,
    onShare: (() -> Unit)? = null,
    shareEnabled: Boolean = true,
    onCompress: (() -> Unit)? = null,
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
            if (onShare != null) {
                ActionPill(
                    Icons.Rounded.Share, "Share",
                    enabled = selectedCount > 0 && shareEnabled,
                    onClick = onShare,
                )
            }
            if (onCompress != null) {
                ActionPill(
                    Icons.Rounded.Archive, "Zip",
                    enabled = selectedCount > 0, onClick = onCompress,
                )
            }
            ActionPill(Icons.Rounded.ContentCopy, "Copy", enabled = selectedCount > 0, onClick = onCopy)
            ActionPill(Icons.Rounded.DriveFileMove, "Move", enabled = selectedCount > 0, onClick = onMove)
            ActionPill(
                Icons.Rounded.Delete, "Delete",
                enabled = selectedCount > 0, tint = fsColors.red, onClick = onDelete,
            )
        }
    }
}

/** Modal sheet with a themed Material date-range picker used for date-to-date selection. */
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
    FsDialog(
        title = title,
        icon = Icons.Rounded.DriveFileRenameOutline,
        confirmText = confirmLabel,
        confirmEnabled = text.trim().isNotEmpty() && !text.contains('/'),
        onConfirm = { onConfirm(text.trim()) },
        onDismiss = onDismiss,
        content = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = fsColors.accent,
                    unfocusedBorderColor = fsColors.separator,
                    focusedTextColor = fsColors.label,
                    unfocusedTextColor = fsColors.label,
                    cursorColor = fsColors.accent,
                ),
                shape = RoundedCornerShape(12.dp),
            )
            if (text.contains('/')) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "A name cannot contain a slash.",
                    style = MaterialTheme.typography.labelSmall,
                    color = fsColors.red,
                )
            }
        },
    )
}

/**
 * Spells out exactly what is about to be deleted: how many files and folders,
 * their combined size, the names involved, and where they end up.
 */
@Composable
fun ConfirmDeleteDialog(
    entries: List<FsEntry>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var files by remember(entries) { mutableStateOf(entries.count { !it.isDirectory }) }
    var folders by remember(entries) { mutableStateOf(entries.count { it.isDirectory }) }
    var bytes by remember(entries) { mutableStateOf(entries.filter { !it.isDirectory }.sumOf { it.size }) }
    var counting by remember(entries) { mutableStateOf(entries.any { it.isDirectory }) }

    // Folders need a walk to say how much is really going away.
    LaunchedEffect(entries) {
        if (entries.none { it.isDirectory }) return@LaunchedEffect
        val stats = withContext(Dispatchers.IO) {
            var f = entries.count { !it.isDirectory }
            var d = 0
            var b = entries.filter { !it.isDirectory }.sumOf { it.size }
            entries.filter { it.isDirectory }.forEach { entry ->
                val queue = ArrayDeque<File>()
                queue.add(entry.toFile())
                d++
                while (queue.isNotEmpty()) {
                    val dir = queue.removeFirst()
                    val children = dir.listFiles() ?: continue
                    for (child in children) {
                        if (child.isDirectory) {
                            d++
                            queue.add(child)
                        } else {
                            f++
                            b += child.length()
                        }
                    }
                }
            }
            Triple(f, d, b)
        }
        files = stats.first
        folders = stats.second
        bytes = stats.third
        counting = false
    }

    FsDialog(
        title = "Move ${entries.size} item${if (entries.size == 1) "" else "s"} to Trash?",
        message = buildString {
            append("This removes ")
            if (files > 0) append("$files file${if (files == 1) "" else "s"}")
            if (files > 0 && folders > 0) append(" and ")
            if (folders > 0) append("$folders folder${if (folders == 1) "" else "s"}")
            append(", totalling ${Formatters.bytes(bytes)}")
            if (counting) append(" so far")
            append(".")
        },
        icon = Icons.Rounded.DeleteOutline,
        destructive = true,
        confirmText = "Move to Trash",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        content = {
            if (counting) {
                Text(
                    "Still measuring folder contents…",
                    color = fsColors.secondaryLabel,
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.height(10.dp))
            }

            // The names are the part worth checking before agreeing, so they
            // get their own panel rather than being buried in the sentence.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(fsColors.fill.copy(alpha = 0.5f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                entries.take(6).forEach { entry ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (entry.isDirectory) Icons.Rounded.Folder else Icons.Rounded.InsertDriveFile,
                            null,
                            tint = fsColors.secondaryLabel,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            entry.name,
                            color = fsColors.label,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (entries.size > 6) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "…and ${entries.size - 6} more",
                        color = fsColors.secondaryLabel,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Rounded.RestoreFromTrash, null,
                    tint = fsColors.green, modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Everything goes to the Trash first, so you can restore it from " +
                        "there until you empty the Trash.",
                    color = fsColors.secondaryLabel,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
    )
}

/**
 * Opens a folder for browsing, sending an encrypted one to its vault screen.
 *
 * Browsing a vault as an ordinary folder shows the container's own shard
 * directories — two-character names like "2b" holding opaque blobs. That is
 * both confusing and useless, since the real names live encrypted in the index.
 *
 * Deliberately not folded into [openFolderGated]: the folder picker uses that
 * to walk into a folder as a *destination*, where jumping to the vault screen
 * would be wrong.
 */
fun openFolderForBrowsing(
    context: android.content.Context,
    path: String,
    name: String,
    onOpen: () -> Unit,
) {
    if (com.shahaabapps.filestorm.data.vault.VaultFolder.isVault(java.io.File(path))) {
        onOpenVault?.invoke(path)
        return
    }
    openFolderGated(context, path, name, onOpen)
}

/** Opens a folder, first passing biometric auth when the folder is locked. */
fun openFolderGated(
    context: android.content.Context,
    path: String,
    name: String,
    onOpen: () -> Unit,
) {
    if (com.shahaabapps.filestorm.data.FolderLocks.requiresAuth(path)) {
        val activity = context as? androidx.fragment.app.FragmentActivity ?: return
        com.shahaabapps.filestorm.ui.Biometrics.prompt(
            activity,
            title = "Unlock \"$name\"",
            subtitle = "This folder is protected",
            onSuccess = {
                com.shahaabapps.filestorm.data.FolderLocks.markUnlocked(path)
                onOpen()
            },
        )
    } else {
        onOpen()
    }
}

/**
 * Shares one or many files through the system chooser. Folders cannot be shared
 * and are skipped; very large selections are capped because the intent payload
 * has a hard size limit.
 */
fun shareFiles(context: android.content.Context, entries: List<FsEntry>) {
    val files = entries.filter { !it.isDirectory && it.toFile().isFile }
    if (files.isEmpty()) {
        android.widget.Toast
            .makeText(context, "Folders cannot be shared", android.widget.Toast.LENGTH_SHORT)
            .show()
        return
    }
    val capped = files.take(MAX_SHARE_ITEMS)
    runCatching {
        val authority = context.packageName + ".provider"
        val uris = ArrayList<android.net.Uri>(
            capped.map { FileProvider.getUriForFile(context, authority, it.toFile()) }
        )
        val types = capped
            .map { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.extension) ?: "*/*" }
            .distinct()
        val type = when {
            types.size == 1 -> types.first()
            types.map { it.substringBefore('/') }.distinct().size == 1 ->
                types.first().substringBefore('/') + "/*"
            else -> "*/*"
        }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND)
                .setType(type)
                .putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE)
                .setType(type)
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(
            Intent.createChooser(
                intent,
                if (uris.size == 1) "Share ${capped.first().name}" else "Share ${uris.size} files",
            )
        )
        if (files.size > capped.size) {
            android.widget.Toast.makeText(
                context,
                "Sharing the first ${capped.size} of ${files.size} files",
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }.onFailure {
        android.widget.Toast
            .makeText(context, "Could not share these files", android.widget.Toast.LENGTH_SHORT)
            .show()
    }
}

private const val MAX_SHARE_ITEMS = 100

/**
 * Opens a file the way the app knows best. Audio is handled in-app so playback
 * survives leaving the folder; everything the app has no viewer for is handed
 * to whichever app the user has for it.
 */
/** Set by the navigation host so a tapped archive can open its own screen. */
var onOpenArchive: ((String) -> Unit)? = null

/** Set by the navigation host so a folder can open the vault screen. */
var onOpenVault: ((String) -> Unit)? = null

fun openFile(context: android.content.Context, entry: FsEntry) {
    // An apk opens as an archive, not as an install.
    //
    // Handing it to the system installer needs REQUEST_INSTALL_PACKAGES, which
    // this app deliberately does not ask for: it is a sensitive permission
    // needing its own Play declaration, and installing apps is not what a file
    // manager is for. Without it the system refuses the request outright rather
    // than offering to allow it, so a tap that tried to install would simply
    // fail. Listing what is inside is the useful thing left.
    val opener = onOpenArchive
    if (opener != null && com.shahaabapps.filestorm.data.archive.ArchiveReader
            .isSupported(entry.toFile())
    ) {
        opener(entry.path)
        return
    }
    if (entry.kind == com.shahaabapps.filestorm.data.FileKind.AUDIO) {
        com.shahaabapps.filestorm.data.audio.AudioPlayer.playFolderOf(entry.path)
        return
    }
    com.shahaabapps.filestorm.data.Diagnostics.log(
        "EXTERNAL",
        "openFile " + com.shahaabapps.filestorm.data.Diagnostics.describe(entry),
    )
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
