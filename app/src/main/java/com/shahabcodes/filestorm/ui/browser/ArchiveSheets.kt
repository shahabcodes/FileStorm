package com.shahabcodes.filestorm.ui.browser

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.shahabcodes.filestorm.data.FileRepository
import com.shahabcodes.filestorm.data.FsEntry
import com.shahabcodes.filestorm.data.archive.ArchiveManager
import com.shahabcodes.filestorm.ui.components.GroupedCard
import com.shahabcodes.filestorm.ui.components.RowSeparator
import com.shahabcodes.filestorm.ui.components.pressScale
import com.shahabcodes.filestorm.ui.theme.fsColors
import com.shahabcodes.filestorm.util.Formatters
import kotlinx.coroutines.launch
import java.io.File

/** Names the archive, then packs the selection with live progress. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressSheet(
    entries: List<FsEntry>,
    destinationFolder: String,
    onDismiss: () -> Unit,
    onFinished: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val suggested = remember(entries, destinationFolder) {
        if (entries.size == 1) entries.first().name.substringBeforeLast('.', entries.first().name)
        else File(destinationFolder).name.ifEmpty { "Archive" }
    }
    var name by remember { mutableStateOf(suggested) }
    var target by remember { mutableStateOf(destinationFolder) }
    var pickingFolder by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<ArchiveManager.Progress?>(null) }
    var outcome by remember { mutableStateOf<ArchiveManager.Outcome?>(null) }

    val fileCount = entries.count { !it.isDirectory }
    val folderCount = entries.size - fileCount
    val approxBytes = entries.filter { !it.isDirectory }.sumOf { it.size }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = fsColors.groupedBackground,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Archive, null, tint = fsColors.accent)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "Compress to zip",
                        style = MaterialTheme.typography.titleLarge,
                        color = fsColors.label,
                    )
                    Text(
                        buildString {
                            if (fileCount > 0) append("$fileCount file" + if (fileCount == 1) "" else "s")
                            if (fileCount > 0 && folderCount > 0) append(" · ")
                            if (folderCount > 0) {
                                append("$folderCount folder" + if (folderCount == 1) "" else "s")
                            }
                            if (approxBytes > 0) append(" · ${Formatters.bytes(approxBytes)}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = fsColors.secondaryLabel,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Archive name", color = fsColors.secondaryLabel) },
                suffix = { Text(".zip", color = fsColors.secondaryLabel) },
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
            Spacer(Modifier.height(14.dp))

            GroupedCard {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressScale { pickingFolder = true }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Folder, null, tint = fsColors.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Save into",
                            style = MaterialTheme.typography.labelSmall,
                            color = fsColors.secondaryLabel,
                        )
                        Text(
                            target.replace(FileRepository.rootPath, "Internal storage"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = fsColors.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))

            val ready = name.isNotBlank() && !name.contains('/')
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (ready) fsColors.accent else fsColors.fill)
                    .pressScale {
                        if (!ready) return@pressScale
                        scope.launch {
                            val archive = ArchiveManager.uniqueArchive(File(target), name.trim())
                            progress = ArchiveManager.Progress(
                                "", 0, entries.size, 0, 0, System.currentTimeMillis(),
                            )
                            outcome = ArchiveManager.zip(entries, archive) { progress = it }
                            progress = null
                        }
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Create Archive",
                    color = if (ready) Color.White else fsColors.secondaryLabel,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }

    if (pickingFolder) {
        FolderPickerSheet(
            title = "Save archive into",
            confirmLabel = "Save Here",
            onDismiss = { pickingFolder = false },
            onConfirm = {
                target = it
                pickingFolder = false
            },
        )
    }

    progress?.let { ArchiveProgressDialog("Compressing", it) }

    outcome?.let { result ->
        ResultDialog(
            success = result.ok,
            title = if (result.ok) "Archive created" else "Could not compress",
            message = if (result.ok) {
                "${result.files} item(s) packed into ${File(result.target).name} " +
                    "(${Formatters.bytes(result.bytes)})."
            } else result.error ?: "Unknown error",
            onDismiss = {
                outcome = null
                onFinished()
                onDismiss()
            },
        )
    }
}

/** Chooses where to unpack, then extracts with live progress. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtractSheet(
    archive: FsEntry,
    onDismiss: () -> Unit,
    onFinished: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val archiveFile = remember(archive.path) { archive.toFile() }
    val parent = remember(archive.path) { archiveFile.parent ?: FileRepository.rootPath }
    val suggestedFolder = remember(archive.name) { archive.name.substringBeforeLast('.', archive.name) }

    var intoNewFolder by remember { mutableStateOf(true) }
    var target by remember { mutableStateOf(parent) }
    var pickingFolder by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<ArchiveManager.Progress?>(null) }
    var outcome by remember { mutableStateOf<ArchiveManager.Outcome?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = fsColors.groupedBackground,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Unarchive, null, tint = fsColors.accent)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "Extract archive",
                        style = MaterialTheme.typography.titleLarge,
                        color = fsColors.label,
                    )
                    Text(
                        archive.name + " · " + Formatters.bytes(archive.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = fsColors.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            GroupedCard {
                OptionRow(
                    label = "Into a new folder",
                    detail = "$suggestedFolder/",
                    selected = intoNewFolder,
                ) { intoNewFolder = true }
                RowSeparator(startIndent = 16.dp)
                OptionRow(
                    label = "Straight into this folder",
                    detail = target.replace(FileRepository.rootPath, "Internal storage"),
                    selected = !intoNewFolder,
                ) { intoNewFolder = false }
            }
            Spacer(Modifier.height(12.dp))

            GroupedCard {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressScale { pickingFolder = true }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Folder, null, tint = fsColors.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Change destination…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = fsColors.accent,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(fsColors.accent)
                    .pressScale {
                        scope.launch {
                            val destination = if (intoNewFolder) {
                                File(target, suggestedFolder)
                            } else File(target)
                            progress = ArchiveManager.Progress(
                                "", 0, 0, 0, 0, System.currentTimeMillis(),
                            )
                            outcome = ArchiveManager.unzip(archiveFile, destination) { progress = it }
                            progress = null
                        }
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Extract", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    if (pickingFolder) {
        FolderPickerSheet(
            title = "Extract into",
            confirmLabel = "Extract Here",
            onDismiss = { pickingFolder = false },
            onConfirm = {
                target = it
                pickingFolder = false
            },
        )
    }

    progress?.let { ArchiveProgressDialog("Extracting", it) }

    outcome?.let { result ->
        ResultDialog(
            success = result.ok,
            title = if (result.ok) "Extracted" else "Could not extract",
            message = if (result.ok) {
                "${result.files} file(s) extracted into " +
                    File(result.target).name + " (${Formatters.bytes(result.bytes)})."
            } else result.error ?: "Unknown error",
            onDismiss = {
                outcome = null
                onFinished()
                onDismiss()
            },
        )
    }
}

@Composable
private fun OptionRow(
    label: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = fsColors.label)
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = fsColors.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Icon(
                Icons.Rounded.CheckCircle, null,
                tint = fsColors.accent, modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ArchiveProgressDialog(action: String, progress: ArchiveManager.Progress) {
    Dialog(onDismissRequest = {}) {
        Column(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(fsColors.card)
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    action,
                    style = MaterialTheme.typography.titleMedium,
                    color = fsColors.label,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${(progress.fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = fsColors.accent,
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = fsColors.accent,
                trackColor = fsColors.fill,
                strokeCap = StrokeCap.Round,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                progress.currentName,
                style = MaterialTheme.typography.bodySmall,
                color = fsColors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${Formatters.bytes(progress.bytesDone)} of ${Formatters.bytes(progress.bytesTotal)}" +
                    if (progress.etaSeconds >= 0) " · ${Formatters.eta(progress.etaSeconds)} left" else "",
                style = MaterialTheme.typography.labelSmall,
                color = fsColors.secondaryLabel,
            )
        }
    }
}

@Composable
private fun ResultDialog(
    success: Boolean,
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = fsColors.card,
        icon = {
            Icon(
                if (success) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                null,
                tint = if (success) fsColors.green else fsColors.red,
            )
        },
        title = { Text(title, color = fsColors.label) },
        text = { Text(message, color = fsColors.secondaryLabel) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = fsColors.accent) }
        },
    )
}
