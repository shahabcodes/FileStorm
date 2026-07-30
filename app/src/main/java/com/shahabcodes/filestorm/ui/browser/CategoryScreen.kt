package com.shahabcodes.filestorm.ui.browser

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import com.shahabcodes.filestorm.data.FileKind
import com.shahabcodes.filestorm.data.FileRepository
import com.shahabcodes.filestorm.data.FsEntry
import com.shahabcodes.filestorm.data.Prefs
import com.shahabcodes.filestorm.transfer.TransferManager
import com.shahabcodes.filestorm.transfer.TransferOp
import com.shahabcodes.filestorm.transfer.TransferService
import com.shahabcodes.filestorm.ui.components.IosSearchField
import com.shahabcodes.filestorm.ui.components.RowSeparator
import com.shahabcodes.filestorm.ui.components.pressScale
import com.shahabcodes.filestorm.ui.theme.fsColors

private val kindTitles = mapOf(
    FileKind.IMAGE to "Images",
    FileKind.VIDEO to "Videos",
    FileKind.AUDIO to "Audio",
    FileKind.DOCUMENT to "Documents",
    FileKind.ARCHIVE to "Archives",
    FileKind.APK to "APKs",
)

@Composable
fun CategoryScreen(
    kind: FileKind,
    onBack: () -> Unit,
    onOpenTransfer: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    var files by remember { mutableStateOf<List<FsEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var showDateSheet by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf<TransferOp?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var infoTarget by remember { mutableStateOf<FsEntry?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var viewMenuOpen by remember { mutableStateOf(false) }

    var sortTick by remember { mutableStateOf(0) }

    LaunchedEffect(kind, reloadKey) {
        loading = true
        files = FileRepository.sortEntries(
            FileRepository.filesByKind(kind), Prefs.sortField, Prefs.sortAscending
        )
        loading = false
    }
    LaunchedEffect(sortTick) {
        if (sortTick > 0) files = FileRepository.sortEntries(files, Prefs.sortField, Prefs.sortAscending)
    }

    val visible = remember(files, query) {
        if (query.isBlank()) files
        else files.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
    val selectedEntries = remember(files, selected) { files.filter { it.path in selected } }

    fun exitSelection() {
        selectionMode = false
        selected = emptySet()
    }

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
            if (selectionMode) {
                Text(
                    "Cancel", color = fsColors.accent, style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.pressScale { exitSelection() }.padding(horizontal = 10.dp, vertical = 6.dp),
                )
                Text(
                    "${selected.size} selected",
                    style = MaterialTheme.typography.titleMedium,
                    color = fsColors.label,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (selected.size == visible.size) "Deselect All" else "Select All",
                    color = fsColors.accent, style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .pressScale {
                            selected = if (selected.size == visible.size) emptySet()
                            else visible.map { it.path }.toSet()
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            } else {
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
                Box {
                    Icon(
                        Icons.Rounded.GridView, "View",
                        tint = fsColors.accent,
                        modifier = Modifier.pressScale { viewMenuOpen = true }.padding(8.dp).size(20.dp),
                    )
                    ViewModeMenu(expanded = viewMenuOpen, onDismiss = { viewMenuOpen = false })
                }
                Box {
                    Icon(
                        Icons.Rounded.SwapVert, "Sort",
                        tint = fsColors.accent,
                        modifier = Modifier.pressScale { sortMenuOpen = true }.padding(8.dp).size(21.dp),
                    )
                    SortMenu(
                        expanded = sortMenuOpen,
                        onDismiss = { sortMenuOpen = false },
                        onChanged = { sortTick++ },
                    )
                }
                Icon(
                    Icons.Rounded.DateRange, "Select by date",
                    tint = fsColors.accent,
                    modifier = Modifier.pressScale { showDateSheet = true }.padding(8.dp).size(20.dp),
                )
                Text(
                    "Select", color = fsColors.accent, style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.pressScale { selectionMode = true }.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }

        Text(
            kindTitles[kind] ?: "Files",
            style = MaterialTheme.typography.headlineLarge,
            color = fsColors.label,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(10.dp))
        IosSearchField(
            value = query, onValueChange = { query = it },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(12.dp))

        Box(Modifier.weight(1f)) {
            when {
                loading -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(color = fsColors.accent)
                    Spacer(Modifier.height(12.dp))
                    Text("Scanning storage…", color = fsColors.secondaryLabel, style = MaterialTheme.typography.bodyMedium)
                }
                visible.isEmpty() -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Rounded.SearchOff, null,
                        tint = fsColors.secondaryLabel.copy(alpha = 0.4f), modifier = Modifier.size(52.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Nothing found", color = fsColors.secondaryLabel, style = MaterialTheme.typography.bodyMedium)
                }
                else -> FileListView(
                    entries = visible,
                    selectionMode = selectionMode,
                    selected = selected,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, bottom = 120.dp
                    ),
                    onClick = { entry ->
                        if (selectionMode) {
                            selected = if (entry.path in selected) selected - entry.path
                            else selected + entry.path
                        } else {
                            openFile(context, entry)
                        }
                    },
                    onLongClick = { entry ->
                        if (!selectionMode) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectionMode = true
                            selected = setOf(entry.path)
                        }
                    },
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = selectionMode,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Column {
                    if (selected.size == 1) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(fsColors.card)
                                    .pressScale {
                                        infoTarget = files.firstOrNull { it.path == selected.first() }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.Info, null,
                                    tint = fsColors.accent, modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Info", color = fsColors.accent, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    SelectionActionBar(
                        selectedCount = selected.size,
                        onCopy = { showPicker = TransferOp.COPY },
                        onMove = { showPicker = TransferOp.MOVE },
                        onDelete = { confirmDelete = true },
                    )
                }
            }
        }
    }

    infoTarget?.let { target ->
        InfoSheet(entry = target, onDismiss = { infoTarget = null })
    }

    if (showDateSheet) {
        DateRangeSheet(
            onDismiss = { showDateSheet = false },
            onConfirm = { start, end ->
                showDateSheet = false
                selectionMode = true
                selected = visible
                    .filter { it.lastModified in start..end }
                    .map { it.path }
                    .toSet()
            },
        )
    }

    showPicker?.let { op ->
        FolderPickerSheet(
            title = if (op == TransferOp.MOVE) "Move ${selected.size} item(s) to" else "Copy ${selected.size} item(s) to",
            confirmLabel = if (op == TransferOp.MOVE) "Move Here" else "Copy Here",
            onDismiss = { showPicker = null },
            onConfirm = { dest ->
                showPicker = null
                TransferManager.start(selectedEntries, dest, op)
                TransferService.start(context)
                exitSelection()
                onOpenTransfer()
            },
        )
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            count = selected.size,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                val toDelete = selectedEntries
                val deletedPaths = toDelete.map { it.path }.toSet()
                exitSelection()
                FileRepository.deleteAsync(toDelete)
                files = files.filterNot { it.path in deletedPaths }
            },
        )
    }

    val transfer by TransferManager.state.collectAsState()
    LaunchedEffect(transfer.state) {
        if (!transfer.isActive && !loading) reloadKey++
    }
}
