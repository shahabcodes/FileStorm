package com.shahaabapps.filestorm.ui.browser

import com.shahaabapps.filestorm.ui.components.FsSpinner
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shahaabapps.filestorm.data.FileKind
import com.shahaabapps.filestorm.data.FileRepository
import com.shahaabapps.filestorm.data.FsEntry
import com.shahaabapps.filestorm.ui.components.FileIconView
import com.shahaabapps.filestorm.ui.components.GroupedCard
import com.shahaabapps.filestorm.ui.components.RowSeparator
import com.shahaabapps.filestorm.ui.theme.fsColors
import com.shahaabapps.filestorm.util.Formatters
import java.io.File

private val kindLabels = mapOf(
    FileKind.FOLDER to "Folder",
    FileKind.IMAGE to "Image",
    FileKind.VIDEO to "Video",
    FileKind.AUDIO to "Audio",
    FileKind.DOCUMENT to "Document",
    FileKind.ARCHIVE to "Archive",
    FileKind.APK to "Android package",
    FileKind.OTHER to "File",
)

/** Properties sheet: kind, contents, total size, location, modified. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoSheet(entry: FsEntry, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var stats by remember { mutableStateOf<FileRepository.FolderStats?>(null) }

    if (entry.isDirectory) {
        LaunchedEffect(entry.path) {
            stats = FileRepository.folderStats(entry.path) { progress -> stats = progress }
        }
    }

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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FileIconView(entry, size = 72.dp, cornerRadius = 18.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                entry.name,
                style = MaterialTheme.typography.titleLarge,
                color = fsColors.label,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (entry.isDirectory) {
                    val s = stats
                    when {
                        s == null -> "Calculating…"
                        else -> Formatters.bytes(s.bytes) + if (s.scanning) "…" else ""
                    }
                } else Formatters.bytes(entry.size),
                style = MaterialTheme.typography.bodyMedium,
                color = fsColors.secondaryLabel,
            )
            Spacer(Modifier.height(20.dp))

            GroupedCard {
                InfoRow("Kind", kindLabels[entry.kind] ?: "File")
                RowSeparator(startIndent = 16.dp)

                if (entry.isDirectory) {
                    val s = stats
                    InfoRow(
                        "Contents",
                        when {
                            s == null -> "Calculating…"
                            else -> buildString {
                                append("${s.files} file${if (s.files == 1) "" else "s"}")
                                append(" · ${s.folders} folder${if (s.folders == 1) "" else "s"}")
                                if (s.scanning) append("…")
                            }
                        },
                        trailing = { if (stats?.scanning != false) ScanSpinner() },
                    )
                    RowSeparator(startIndent = 16.dp)
                    InfoRow(
                        "Total size",
                        stats?.let { Formatters.bytes(it.bytes) + if (it.scanning) "…" else "" } ?: "Calculating…",
                    )
                    RowSeparator(startIndent = 16.dp)
                } else {
                    InfoRow("Size", Formatters.bytes(entry.size))
                    RowSeparator(startIndent = 16.dp)
                    if (entry.extension.isNotEmpty()) {
                        InfoRow("Extension", ".${entry.extension}")
                        RowSeparator(startIndent = 16.dp)
                    }
                }

                InfoRow(
                    "Where",
                    File(entry.path).parent
                        ?.replace(FileRepository.rootPath, "Internal storage")
                        ?: "Internal storage",
                )
                RowSeparator(startIndent = 16.dp)
                InfoRow("Modified", Formatters.fullDate(entry.lastModified))
            }
        }
    }
}

@Composable
private fun ScanSpinner() {
    FsSpinner(size = 14.dp, strokeWidth = 1.8.dp)
}

@Composable
private fun InfoRow(label: String, value: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = fsColors.secondaryLabel,
            modifier = Modifier.width(90.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = fsColors.label,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}
