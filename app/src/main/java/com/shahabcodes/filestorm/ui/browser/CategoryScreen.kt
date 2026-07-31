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
import kotlinx.coroutines.launch

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
    onOpenViewer: (List<String>, Int) -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Every piece of state is keyed on the category. Without the key the same
    // composition slot is reused when a different category opens, so the old
    // category's files stayed on screen and tapping one opened the wrong file.
    var files by remember(kind) { mutableStateOf<List<FsEntry>>(emptyList()) }
    var loading by remember(kind) { mutableStateOf(true) }
    var query by remember(kind) { mutableStateOf("") }
    var selectionMode by remember(kind) { mutableStateOf(false) }
    var selected by remember(kind) { mutableStateOf(setOf<String>()) }
    var showDateSheet by remember(kind) { mutableStateOf(false) }
    var showPicker by remember(kind) { mutableStateOf<TransferOp?>(null) }
    var confirmDelete by remember(kind) { mutableStateOf(false) }
    var infoTarget by remember(kind) { mutableStateOf<FsEntry?>(null) }
    var reloadKey by remember(kind) { mutableStateOf(0) }
    val viewKey = "category:" + kind.name
    val viewMode = com.shahabcodes.filestorm.data.FolderViews.viewFor(viewKey)
    var pinchAccumulator by remember(kind) { mutableStateOf(1f) }
    var collapsedMonths by remember(kind) { mutableStateOf(setOf<String>()) }
    var monthSorts by remember(kind) {
        mutableStateOf(mapOf<String, Pair<com.shahabcodes.filestorm.data.SortField, Boolean>>())
    }
    var compressTargets by remember(kind) { mutableStateOf<List<FsEntry>>(emptyList()) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var viewMenuOpen by remember { mutableStateOf(false) }

    var sortTick by remember { mutableStateOf(0) }

    LaunchedEffect(kind, reloadKey) {
        // Only the very first pass shows the loader; later passes reuse the cache
        // so returning to a category is instant instead of re-walking storage.
        loading = files.isEmpty()
        files = FileRepository.sortEntries(
            FileRepository.filesByKind(kind), Prefs.sortField, Prefs.sortAscending
        )
        loading = false
    }
    LaunchedEffect(sortTick) {
        if (sortTick > 0) files = FileRepository.sortEntries(files, Prefs.sortField, Prefs.sortAscending)
    }

    // Hard invariant: a category lists nothing but its own kind, whatever the
    // scan or any cache hands back.
    val visible = remember(files, query, kind) {
        val ofKind = files.filter { !it.isDirectory && it.kind == kind }
        if (query.isBlank()) ofKind
        else ofKind.filter { it.name.contains(query.trim(), ignoreCase = true) }
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
                    ViewModeMenu(
                        expanded = viewMenuOpen,
                        current = viewMode,
                        grouped = com.shahabcodes.filestorm.data.FolderViews.groupedFor(viewKey),
                        onDismiss = { viewMenuOpen = false },
                        onSelect = { com.shahabcodes.filestorm.data.FolderViews.setView(viewKey, it) },
                        onGroupedChange = {
                            com.shahabcodes.filestorm.data.FolderViews.setGrouped(viewKey, it)
                        },
                    )
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
                loading -> com.shahabcodes.filestorm.ui.components.FsLoadingState(
                    title = "Scanning storage",
                    detail = "Looking through every folder for " +
                        (kindTitles[kind] ?: "files").lowercase(),
                )
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
                    viewMode = viewMode,
                    columns = com.shahabcodes.filestorm.data.FolderViews.columnsFor(viewKey, viewMode),
                    grouped = com.shahabcodes.filestorm.data.FolderViews.groupedFor(viewKey),
                    collapsedMonths = collapsedMonths,
                    onToggleMonth = { month ->
                        collapsedMonths = if (month in collapsedMonths) collapsedMonths - month
                        else collapsedMonths + month
                    },
                    monthSorts = monthSorts,
                    onMonthSort = { month, field, ascending ->
                        monthSorts = monthSorts + (month to (field to ascending))
                    },
                    onZoom = { zoom -> pinchAccumulator = applyPinch(viewKey, viewMode, pinchAccumulator * zoom) },
                    onClick = { entry ->
                        if (selectionMode) {
                            selected = if (entry.path in selected) selected - entry.path
                            else selected + entry.path
                        } else {
                            val media = visible.filter {
                                (it.kind == FileKind.IMAGE || it.kind == FileKind.VIDEO) &&
                                    it.toFile().isFile
                            }
                            val index = media.indexOfFirst { it.path == entry.path }
                            if (index >= 0) onOpenViewer(media.map { it.path }, index)
                            else openFile(context, entry)
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
                        onShare = { shareFiles(context, selectedEntries) },
                        shareEnabled = selectedEntries.any { !it.isDirectory },
                        onCopy = { showPicker = TransferOp.COPY },
                        onMove = { showPicker = TransferOp.MOVE },
                        onDelete = { confirmDelete = true },
                    )
                }
            }
        }
    }

    if (compressTargets.isNotEmpty()) {
        CompressSheet(
            entries = compressTargets,
            destinationFolder = compressTargets.first().toFile().parent ?: FileRepository.rootPath,
            onDismiss = { compressTargets = emptyList() },
            onFinished = {
                exitSelection()
                reloadKey++
            },
        )
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
            entries = selectedEntries,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                val toDelete = selectedEntries
                val deletedPaths = toDelete.map { it.path }.toSet()
                exitSelection()
                scope.launch { com.shahabcodes.filestorm.data.TrashManager.moveToTrash(toDelete) }
                files = files.filterNot { it.path in deletedPaths }
            },
        )
    }

    // Refresh only when a transfer really finishes while this screen is open.
    val transfer by TransferManager.state.collectAsState()
    var lastTransferState by remember { mutableStateOf(transfer.state) }
    LaunchedEffect(transfer.state) {
        if (transfer.state != lastTransferState) {
            val finished = !transfer.isActive
            lastTransferState = transfer.state
            if (finished) {
                FileRepository.invalidateKinds()
                reloadKey++
            }
        }
    }
}
