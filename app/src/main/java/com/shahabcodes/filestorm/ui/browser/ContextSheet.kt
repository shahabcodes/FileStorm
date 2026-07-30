package com.shahabcodes.filestorm.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.shahabcodes.filestorm.data.Favorites
import com.shahabcodes.filestorm.data.FileRepository
import com.shahabcodes.filestorm.data.FolderLocks
import com.shahabcodes.filestorm.data.FsEntry
import com.shahabcodes.filestorm.ui.Biometrics
import com.shahabcodes.filestorm.ui.components.FileIconView
import com.shahabcodes.filestorm.ui.components.GroupedCard
import com.shahabcodes.filestorm.ui.components.RowSeparator
import com.shahabcodes.filestorm.ui.components.pressScale
import com.shahabcodes.filestorm.ui.theme.fsColors
import com.shahabcodes.filestorm.util.Formatters

/**
 * Long-press menu for a file or folder: open, properties, rename, style,
 * favourite, lock, copy, move, delete and multi-select.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryContextSheet(
    entry: FsEntry,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onOpenInNewTab: (() -> Unit)? = null,
    onProperties: () -> Unit,
    onRename: () -> Unit,
    onEditDate: (() -> Unit)? = null,
    onStyle: (() -> Unit)? = null,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val isFolder = entry.isDirectory

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
                .padding(bottom = 20.dp),
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth().padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FileIconView(entry, size = 46.dp, cornerRadius = 12.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = fsColors.label,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (isFolder) {
                            val c = entry.childCount
                            if (c >= 0) "$c item${if (c == 1) "" else "s"}" else "Folder"
                        } else Formatters.bytes(entry.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = fsColors.secondaryLabel,
                    )
                }
            }

            GroupedCard {
                ContextRow(Icons.Rounded.OpenInNew, "Open") { onDismiss(); onOpen() }
                if (isFolder && onOpenInNewTab != null) {
                    RowSeparator(startIndent = 54.dp)
                    ContextRow(Icons.Rounded.Tab, "Open in new tab") { onDismiss(); onOpenInNewTab() }
                }
                RowSeparator(startIndent = 54.dp)
                ContextRow(
                    Icons.Rounded.Info,
                    if (isFolder) "Properties (size & contents)" else "Properties",
                ) { onDismiss(); onProperties() }
            }
            Spacer(Modifier.height(14.dp))

            GroupedCard {
                ContextRow(Icons.Rounded.DriveFileRenameOutline, "Rename") { onDismiss(); onRename() }
                if (!isFolder && onEditDate != null) {
                    RowSeparator(startIndent = 54.dp)
                    ContextRow(Icons.Rounded.Schedule, "Edit date & metadata") { onDismiss(); onEditDate() }
                }
                if (isFolder && onStyle != null) {
                    RowSeparator(startIndent = 54.dp)
                    ContextRow(Icons.Rounded.Palette, "Colour & style") { onDismiss(); onStyle() }
                    RowSeparator(startIndent = 54.dp)
                    val fav = Favorites.isFavorite(entry.path)
                    ContextRow(
                        if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        if (fav) "Remove from favourites" else "Add to favourites",
                    ) {
                        Favorites.toggle(entry.path)
                        onDismiss()
                    }
                    RowSeparator(startIndent = 54.dp)
                    val locked = FolderLocks.isLocked(entry.path)
                    val activity = context as? FragmentActivity
                    val canLock = activity != null && Biometrics.available(activity)
                    ContextRow(
                        if (locked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                        if (locked) "Remove folder lock" else "Lock this folder",
                        tint = if (canLock) fsColors.orange else fsColors.secondaryLabel.copy(alpha = 0.5f),
                        enabled = canLock,
                    ) {
                        val act = activity ?: return@ContextRow
                        Biometrics.prompt(
                            act,
                            title = if (locked) "Unlock \"${entry.name}\"" else "Lock \"${entry.name}\"",
                            subtitle = "Confirm it's you",
                            onSuccess = { FolderLocks.setLocked(entry.path, !locked) },
                        )
                        onDismiss()
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            GroupedCard {
                ContextRow(Icons.Rounded.ContentCopy, "Copy to…") { onDismiss(); onCopy() }
                RowSeparator(startIndent = 54.dp)
                ContextRow(Icons.Rounded.DriveFileMove, "Move to…") { onDismiss(); onMove() }
                RowSeparator(startIndent = 54.dp)
                ContextRow(Icons.Rounded.CheckCircle, "Select multiple") { onDismiss(); onSelect() }
                RowSeparator(startIndent = 54.dp)
                ContextRow(Icons.Rounded.Delete, "Move to Trash", tint = fsColors.red) {
                    onDismiss(); onDelete()
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                entry.path.replace(FileRepository.rootPath, "Internal storage"),
                style = MaterialTheme.typography.labelSmall,
                color = fsColors.secondaryLabel.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun ContextRow(
    icon: ImageVector,
    label: String,
    tint: Color? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val color = tint ?: fsColors.accent
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.pressScale(onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon, null,
            tint = if (enabled) color else color.copy(alpha = 0.4f),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) fsColors.label else fsColors.secondaryLabel.copy(alpha = 0.5f),
        )
    }
}
