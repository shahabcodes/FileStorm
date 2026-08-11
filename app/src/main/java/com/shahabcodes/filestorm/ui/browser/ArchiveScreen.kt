package com.shahabcodes.filestorm.ui.browser

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shahabcodes.filestorm.data.FileRepository
import com.shahabcodes.filestorm.data.archive.ArchiveEntry
import com.shahabcodes.filestorm.data.archive.ArchiveManager
import com.shahabcodes.filestorm.data.archive.ArchiveReader
import com.shahabcodes.filestorm.ui.components.FsLoadingState
import com.shahabcodes.filestorm.ui.components.GroupedCard
import com.shahabcodes.filestorm.ui.components.RowSeparator
import com.shahabcodes.filestorm.ui.components.SelectionCircle
import com.shahabcodes.filestorm.ui.components.pressScale
import com.shahabcodes.filestorm.ui.theme.fsColors
import com.shahabcodes.filestorm.util.Formatters
import java.io.File
import kotlinx.coroutines.launch

/**
 * Looks inside an archive before anything is unpacked, so you can see what you
 * are about to get and take only part of it. Extracting everything to a folder
 * blind was the only option before.
 */
@Composable
fun ArchiveScreen(path: String, onBack: () -> Unit, onOpenFolder: (String) -> Unit) {
    val archive = remember(path) { File(path) }
    val scope = rememberCoroutineScope()

    var entries by remember(path) { mutableStateOf<List<ArchiveEntry>?>(null) }
    var failed by remember(path) { mutableStateOf(false) }
    var selected by remember(path) { mutableStateOf(setOf<String>()) }
    var picking by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<ArchiveManager.Progress?>(null) }
    var outcome by remember { mutableStateOf<ArchiveManager.Outcome?>(null) }

    LaunchedEffect(path) {
        val listed = ArchiveReader.list(archive)
        entries = listed.orEmpty()
        failed = listed == null
    }

    val files = entries.orEmpty().filter { !it.isDirectory }
    val totalBytes = files.sumOf { it.size.coerceAtLeast(0L) }
    val packedBytes = archive.length()

    Column(
        Modifier
            .fillMaxSize()
            .background(fsColors.groupedBackground)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.pressScale(onBack).padding(horizontal = 6.dp, vertical = 6.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null,
                    tint = fsColors.accent, modifier = Modifier.size(18.dp),
                )
                Text("Back", color = fsColors.accent, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.weight(1f))
            if (files.isNotEmpty()) {
                Text(
                    if (selected.size == files.size) "Deselect All" else "Select All",
                    color = fsColors.accent,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .pressScale {
                            selected = if (selected.size == files.size) emptySet()
                            else files.map { it.name }.toSet()
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }

        Text(
            archive.name,
            style = MaterialTheme.typography.headlineMedium,
            color = fsColors.label,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            when {
                failed -> "This file could not be read as an archive"
                entries == null -> "Reading…"
                else -> "${files.size} file(s) · ${Formatters.bytes(totalBytes)} unpacked · " +
                    "${Formatters.bytes(packedBytes)} on disk"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (failed) fsColors.red else fsColors.secondaryLabel,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(10.dp))

        Box(Modifier.weight(1f)) {
            when {
                entries == null -> FsLoadingState("Reading the archive")
                files.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (failed) {
                            "File Storm can open zip, jar, apk, tar, tar.gz and gz archives."
                        } else "This archive is empty.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = fsColors.secondaryLabel,
                        modifier = Modifier.padding(32.dp),
                    )
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, bottom = 140.dp,
                    ),
                ) {
                    item {
                        GroupedCard {
                            files.forEachIndexed { index, entry ->
                                val on = entry.name in selected
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .pressScale {
                                            selected = if (on) selected - entry.name
                                            else selected + entry.name
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SelectionCircle(selected = on)
                                    Spacer(Modifier.width(12.dp))
                                    Icon(
                                        Icons.Rounded.InsertDriveFile, null,
                                        tint = fsColors.kinds.archive,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            entry.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = fsColors.label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (entry.parentPath.isNotEmpty()) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Rounded.Folder, null,
                                                    tint = fsColors.secondaryLabel,
                                                    modifier = Modifier.size(11.dp),
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    entry.parentPath,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = fsColors.secondaryLabel,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (entry.size >= 0) Formatters.bytes(entry.size) else "—",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = fsColors.secondaryLabel,
                                    )
                                }
                                if (index != files.lastIndex) RowSeparator(startIndent = 62.dp)
                            }
                        }
                    }
                }
            }

            if (files.isNotEmpty()) {
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .background(fsColors.card)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        if (selected.isEmpty()) "Extract everything, or pick individual files"
                        else "${selected.size} of ${files.size} selected",
                        style = MaterialTheme.typography.labelMedium,
                        color = fsColors.secondaryLabel,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(fsColors.accent)
                            .pressScale { picking = true }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (selected.isEmpty()) "Extract All" else "Extract ${selected.size}",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
    }

    if (picking) {
        FolderPickerSheet(
            title = "Extract to",
            confirmLabel = "Extract Here",
            onDismiss = { picking = false },
            onConfirm = { destination ->
                picking = false
                val only = selected.ifEmpty { null }
                scope.launch {
                    progress = ArchiveManager.Progress(
                        currentName = "", doneFiles = 0,
                        totalFiles = only?.size ?: files.size,
                        bytesDone = 0, bytesTotal = totalBytes,
                        startedAt = System.currentTimeMillis(),
                    )
                    val result = ArchiveReader.extract(archive, File(destination), only) {
                        progress = it
                    }
                    progress = null
                    outcome = result
                    FileRepository.invalidate(destination)
                }
            },
        )
    }

    progress?.let { ArchiveWorkingDialog("Extracting", it) }

    outcome?.let { result ->
        com.shahabcodes.filestorm.ui.components.FsDialog(
            title = if (result.ok) "Extracted" else "Extraction failed",
            message = if (result.ok) {
                "${result.files} file(s) · ${Formatters.bytes(result.bytes)} written to " +
                    result.target.replace(FileRepository.rootPath, "Internal storage")
            } else {
                result.error ?: "Unknown error"
            },
            icon = if (result.ok) Icons.Rounded.CheckCircleOutline else Icons.Rounded.ErrorOutline,
            destructive = !result.ok,
            confirmText = if (result.ok) "Open Folder" else "OK",
            dismissText = if (result.ok) "Done" else null,
            onDismiss = { outcome = null },
            onConfirm = {
                val target = result.target
                outcome = null
                if (result.ok) onOpenFolder(target)
            },
        )
    }
}

/** Live progress while an archive is being written or read. */
@Composable
private fun ArchiveWorkingDialog(action: String, progress: ArchiveManager.Progress) {
    androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
        Column(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(fsColors.card)
                .padding(horizontal = 26.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            com.shahabcodes.filestorm.ui.components.FsSpinner()
            Spacer(Modifier.height(14.dp))
            Text(
                "$action… ${(progress.fraction * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                color = fsColors.label,
            )
            if (progress.currentName.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    progress.currentName,
                    style = MaterialTheme.typography.bodySmall,
                    color = fsColors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${progress.doneFiles} of ${progress.totalFiles} · " +
                    Formatters.bytes(progress.bytesDone),
                style = MaterialTheme.typography.labelSmall,
                color = fsColors.secondaryLabel,
            )
            Row(horizontalArrangement = Arrangement.Center) {
                Text(
                    "Cancel",
                    style = MaterialTheme.typography.labelMedium,
                    color = fsColors.red,
                    modifier = Modifier
                        .pressScale { ArchiveManager.cancel() }
                        .padding(top = 12.dp, start = 8.dp, end = 8.dp),
                )
            }
        }
    }
}
