package com.shahabcodes.filestorm.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shahabcodes.filestorm.data.StorageInsights
import com.shahabcodes.filestorm.data.TrashManager
import com.shahabcodes.filestorm.data.dup.DuplicateFinder
import com.shahabcodes.filestorm.ui.components.FsSpinner
import com.shahabcodes.filestorm.ui.components.GroupedCard
import com.shahabcodes.filestorm.ui.components.RowSeparator
import com.shahabcodes.filestorm.ui.components.pressScale
import com.shahabcodes.filestorm.ui.theme.fsColors
import com.shahabcodes.filestorm.util.Formatters
import java.io.File

/** Shared shell so every insight card has the same header and empty state. */
@Composable
private fun InsightCard(
    title: String,
    subtitle: String?,
    hasData: Boolean,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    GroupedCard {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = fsColors.label,
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = fsColors.secondaryLabel,
                        )
                    }
                }
                if (StorageInsights.scanning) {
                    FsSpinner(size = 20.dp, strokeWidth = 2.5.dp)
                } else {
                    action?.invoke()
                }
            }
            Spacer(Modifier.height(12.dp))
            if (!hasData) {
                Text(
                    if (StorageInsights.scanning) {
                        "Scanning… ${Formatters.compactCount(StorageInsights.scannedFiles)} files"
                    } else {
                        "Nothing to show yet."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = fsColors.secondaryLabel,
                )
            } else {
                content()
            }
        }
    }
}

/** One row of the ranked lists: name, sub-line, proportional bar and value. */
@Composable
private fun RankRow(
    title: String,
    subtitle: String,
    value: String,
    fraction: Float,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().pressScale(onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = fsColors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = fsColors.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(fsColors.fill),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.horizontalGradient(listOf(fsColors.accent, fsColors.green))
                        ),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.labelMedium, color = fsColors.label)
    }
}

/**
 * What the app can actually give back: the trash, confirmed duplicates, empty
 * folders and zero-byte files, each with the action that reclaims it.
 */
@Composable
fun ReclaimCard(
    onOpenTrash: () -> Unit,
    onOpenDuplicates: () -> Unit,
    onCleanEmptyFolders: () -> Unit,
    onCleanZeroByte: () -> Unit,
) {
    val snapshot = StorageInsights.snapshot
    val dup by DuplicateFinder.state.collectAsState()
    val trashBytes = TrashManager.items.sumOf { it.size }
    val trashCount = TrashManager.items.size
    val dupBytes = dup.wastedBytes
    val emptyCount = snapshot?.emptyFolderCount ?: 0
    val zeroCount = snapshot?.zeroByteCount ?: 0
    val total = trashBytes + dupBytes

    InsightCard(
        title = "Reclaim Space",
        subtitle = if (total > 0) "${Formatters.bytes(total)} can be freed right now"
        else "Nothing obvious to reclaim",
        hasData = true,
    ) {
        Column {
            ReclaimRow(
                icon = Icons.Rounded.DeleteOutline,
                tint = fsColors.red,
                label = "Trash",
                detail = if (trashCount == 0) "Empty"
                else "$trashCount item(s) · ${Formatters.bytes(trashBytes)}",
                actionable = trashCount > 0,
                onClick = onOpenTrash,
            )
            RowSeparator(startIndent = 38.dp)
            ReclaimRow(
                icon = Icons.Rounded.ContentCopy,
                tint = fsColors.orange,
                label = "Duplicates",
                detail = if (dup.pairs.isEmpty()) "Run a sweep to find copies"
                else "${dup.extraCopies} extra copy(s) · ${Formatters.bytes(dupBytes)}",
                actionable = true,
                onClick = onOpenDuplicates,
            )
            RowSeparator(startIndent = 38.dp)
            ReclaimRow(
                icon = Icons.Rounded.FolderOff,
                tint = fsColors.accent,
                label = "Empty folders",
                detail = if (emptyCount == 0) "None found" else "$emptyCount folder(s)",
                actionable = emptyCount > 0,
                onClick = onCleanEmptyFolders,
            )
            RowSeparator(startIndent = 38.dp)
            ReclaimRow(
                icon = Icons.Rounded.InsertDriveFile,
                tint = fsColors.green,
                label = "Zero-byte files",
                detail = if (zeroCount == 0) "None found" else "$zeroCount file(s)",
                actionable = zeroCount > 0,
                onClick = onCleanZeroByte,
            )
        }
    }
}

@Composable
private fun ReclaimRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    label: String,
    detail: String,
    actionable: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (actionable) Modifier.pressScale(onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(tint.copy(alpha = if (actionable) 0.16f else 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon, null,
                tint = if (actionable) tint else tint.copy(alpha = 0.45f),
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = fsColors.label)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = fsColors.secondaryLabel)
        }
        if (actionable) {
            Icon(
                Icons.Rounded.ChevronRight, null,
                tint = fsColors.secondaryLabel.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** The largest files anywhere on the device; tapping one opens its folder. */
@Composable
fun BiggestFilesCard(onOpenFolder: (String) -> Unit) {
    val files = StorageInsights.snapshot?.biggestFiles.orEmpty()
    val biggest = files.firstOrNull()?.size?.coerceAtLeast(1L) ?: 1L
    InsightCard(
        title = "Biggest Files",
        subtitle = if (files.isEmpty()) null
        else "Top ${files.size} · ${Formatters.bytes(files.sumOf { it.size })} together",
        hasData = files.isNotEmpty(),
    ) {
        Column {
            files.forEach { file ->
                RankRow(
                    title = file.name,
                    subtitle = prettyPath(file.folder),
                    value = Formatters.bytes(file.size),
                    fraction = file.size.toFloat() / biggest,
                    onClick = { onOpenFolder(file.folder) },
                )
            }
        }
    }
}

/** Where the space actually sits: folder totals including everything beneath. */
@Composable
fun LargestFoldersCard(onOpenFolder: (String) -> Unit) {
    val folders = StorageInsights.snapshot?.largestFolders.orEmpty()
    val biggest = folders.firstOrNull()?.bytes?.coerceAtLeast(1L) ?: 1L
    InsightCard(
        title = "Largest Folders",
        subtitle = if (folders.isEmpty()) null else "Including everything nested inside",
        hasData = folders.isNotEmpty(),
    ) {
        Column {
            folders.forEach { folder ->
                RankRow(
                    title = folder.name.ifEmpty { "Internal storage" },
                    subtitle = "${Formatters.compactCount(folder.files)} files · " +
                        prettyPath(folder.path.substringBeforeLast(File.separatorChar, "")),
                    value = Formatters.bytes(folder.bytes),
                    fraction = folder.bytes.toFloat() / biggest,
                    onClick = { onOpenFolder(folder.path) },
                )
            }
        }
    }
}

/** How much was added each month, oldest to newest. */
@Composable
fun GrowthCard() {
    val months = StorageInsights.snapshot?.months.orEmpty().reversed().takeLast(8)
    val peak = months.maxOfOrNull { it.bytes }?.coerceAtLeast(1L) ?: 1L
    InsightCard(
        title = "Storage Growth",
        subtitle = if (months.isEmpty()) null
        else "${Formatters.bytes(months.sumOf { it.bytes })} across ${months.size} months",
        hasData = months.isNotEmpty(),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().height(120.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                months.forEach { month ->
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Text(
                            Formatters.bytes(month.bytes).substringBefore(" "),
                            style = MaterialTheme.typography.labelSmall,
                            color = fsColors.secondaryLabel,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(3.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height((78 * (month.bytes.toFloat() / peak)).coerceAtLeast(4f).dp)
                                .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(fsColors.accent, fsColors.accent.copy(alpha = 0.45f))
                                    )
                                ),
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                months.forEach { month ->
                    Text(
                        month.label.take(3),
                        style = MaterialTheme.typography.labelSmall,
                        color = fsColors.secondaryLabel,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Everything added or changed in the last week. */
@Composable
fun RecentFilesCard(onOpenFolder: (String) -> Unit) {
    val files = StorageInsights.snapshot?.recent.orEmpty().take(12)
    InsightCard(
        title = "Recent Files",
        subtitle = if (files.isEmpty()) "Nothing changed in the last week"
        else "${files.size} shown from the last 7 days",
        hasData = files.isNotEmpty(),
    ) {
        Column {
            files.forEachIndexed { index, file ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressScale { onOpenFolder(file.folder) }
                        .padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            file.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = fsColors.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            prettyPath(file.folder),
                            style = MaterialTheme.typography.labelSmall,
                            color = fsColors.secondaryLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            Formatters.bytes(file.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = fsColors.label,
                        )
                        Text(
                            Formatters.fileDate(file.modified),
                            style = MaterialTheme.typography.labelSmall,
                            color = fsColors.secondaryLabel,
                        )
                    }
                }
                if (index != files.lastIndex) RowSeparator(startIndent = 0.dp)
            }
        }
    }
}

private fun prettyPath(path: String): String =
    path.replace(com.shahabcodes.filestorm.data.FileRepository.rootPath, "Internal storage")
