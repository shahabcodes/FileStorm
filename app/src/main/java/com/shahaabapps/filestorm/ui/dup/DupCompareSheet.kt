package com.shahaabapps.filestorm.ui.dup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayCircleFilled
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.shahaabapps.filestorm.data.FileKind
import com.shahaabapps.filestorm.data.FileRepository
import com.shahaabapps.filestorm.data.FsEntry
import com.shahaabapps.filestorm.data.dup.DupPair
import com.shahaabapps.filestorm.ui.browser.openFile
import com.shahaabapps.filestorm.ui.components.FileIconView
import com.shahaabapps.filestorm.ui.components.GroupedCard
import com.shahaabapps.filestorm.ui.components.RowSeparator
import com.shahaabapps.filestorm.ui.components.pressScale
import com.shahaabapps.filestorm.ui.theme.fsColors
import com.shahaabapps.filestorm.util.Formatters
import java.io.File

/**
 * Side-by-side comparison of one duplicate pair: preview, name, size, date and
 * full path for each copy, with a delete action per side.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DupCompareSheet(
    pair: DupPair,
    deepCompared: Boolean,
    onDismiss: () -> Unit,
    onDeleteSide: (Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val fileA = remember(pair.pathA) { File(pair.pathA) }
    val fileB = remember(pair.pathB) { File(pair.pathB) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = fsColors.groupedBackground,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                pair.name,
                style = MaterialTheme.typography.titleLarge,
                color = fsColors.label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${Formatters.bytes(pair.size)} each · ${Formatters.bytes(pair.size)} reclaimable",
                style = MaterialTheme.typography.bodySmall,
                color = fsColors.secondaryLabel,
            )
            Spacer(Modifier.height(12.dp))

            // Match confidence banner
            GroupedCard {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.CheckCircle, null,
                        tint = if (deepCompared) fsColors.green else fsColors.orange,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (deepCompared) "Contents verified identical, byte for byte"
                        else "Name, type and size match (contents not compared)",
                        style = MaterialTheme.typography.bodySmall,
                        color = fsColors.label,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CopyColumn(
                    heading = "Copy 1",
                    file = fileA,
                    fallbackSize = pair.size,
                    name = pair.name,
                    onOpen = { openFile(context, FsEntry.from(fileA)) },
                    onDelete = { onDeleteSide(1) },
                    modifier = Modifier.weight(1f),
                )
                CopyColumn(
                    heading = "Copy 2",
                    file = fileB,
                    fallbackSize = pair.size,
                    name = pair.name,
                    onOpen = { openFile(context, FsEntry.from(fileB)) },
                    onDelete = { onDeleteSide(2) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CopyColumn(
    heading: String,
    file: File,
    fallbackSize: Long,
    name: String,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val exists = file.exists()
    val kind = FsEntry.kindOf(name, false)
    Column(modifier) {
        Text(
            heading,
            style = MaterialTheme.typography.labelMedium,
            color = fsColors.secondaryLabel,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        GroupedCard {
            // Preview
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(fsColors.fill),
                contentAlignment = Alignment.Center,
            ) {
                if (exists && (kind == FileKind.IMAGE || kind == FileKind.VIDEO)) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(file)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (kind == FileKind.VIDEO) {
                        Icon(
                            Icons.Rounded.PlayCircleFilled, null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(40.dp),
                        )
                    }
                } else {
                    FileIconView(
                        FsEntry(
                            path = file.absolutePath,
                            name = name,
                            isDirectory = false,
                            size = fallbackSize,
                            lastModified = 0,
                            kind = kind,
                        ),
                        size = 56.dp,
                        cornerRadius = 14.dp,
                    )
                }
            }

            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                DetailLine("Name", name)
                DetailLine("Size", Formatters.bytes(if (exists) file.length() else fallbackSize))
                DetailLine(
                    "Modified",
                    if (exists) Formatters.fullDate(file.lastModified()) else "—",
                )
                DetailLine(
                    "Folder",
                    (file.parent ?: "").replace(FileRepository.rootPath, "Internal storage"),
                    maxLines = 4,
                )
                if (!exists) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "This copy no longer exists",
                        style = MaterialTheme.typography.labelSmall,
                        color = fsColors.red,
                    )
                }
            }
            RowSeparator(startIndent = 0.dp)
            Row(Modifier.fillMaxWidth()) {
                SmallAction("Open", Icons.Rounded.OpenInNew, fsColors.accent, exists, onOpen, Modifier.weight(1f))
                SmallAction("Delete", Icons.Rounded.Delete, fsColors.red, exists, onDelete, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ColumnScope.DetailLine(label: String, value: String, maxLines: Int = 2) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = fsColors.secondaryLabel,
    )
    Text(
        value,
        style = MaterialTheme.typography.bodySmall,
        color = fsColors.label,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(bottom = 7.dp),
    )
}

@Composable
private fun SmallAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .then(if (enabled) Modifier.pressScale(onClick) else Modifier)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon, null,
            tint = if (enabled) tint else fsColors.secondaryLabel.copy(alpha = 0.4f),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) tint else fsColors.secondaryLabel.copy(alpha = 0.4f),
            textAlign = TextAlign.Center,
        )
    }
}
