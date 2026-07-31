package com.shahabcodes.filestorm.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesomeMosaic
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.RemoveDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoveToInbox
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shahabcodes.filestorm.data.BrowserTabs
import com.shahabcodes.filestorm.data.Favorites
import com.shahabcodes.filestorm.data.FileRepository
import com.shahabcodes.filestorm.data.FsEntry
import com.shahabcodes.filestorm.data.Prefs
import com.shahabcodes.filestorm.data.SortField
import com.shahabcodes.filestorm.data.ViewMode
import com.shahabcodes.filestorm.transfer.TransferManager
import com.shahabcodes.filestorm.transfer.TransferOp
import com.shahabcodes.filestorm.transfer.TransferService
import com.shahabcodes.filestorm.ui.components.IosSearchField
import com.shahabcodes.filestorm.ui.components.pressScale
import com.shahabcodes.filestorm.ui.theme.fsColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

class BrowserViewModel(val path: String) : ViewModel() {
    val entries = MutableStateFlow<List<FsEntry>>(emptyList())
    val loading = MutableStateFlow(true)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            loading.value = true
            entries.value = FileRepository.list(path)
            loading.value = false
        }
    }

    fun resort() {
        entries.value = FileRepository.sortEntries(entries.value, Prefs.sortField, Prefs.sortAscending)
    }
}

/** Dropdown listing sort fields; tapping the active field flips direction. */
@Composable
fun SortMenu(expanded: Boolean, onDismiss: () -> Unit, onChanged: () -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(fsColors.cardSecondary),
    ) {
        SortField.entries.forEach { field ->
            val active = Prefs.sortField == field
            DropdownMenuItem(
                text = { Text(field.label, color = if (active) fsColors.accent else fsColors.label) },
                trailingIcon = {
                    if (active) {
                        Icon(
                            if (Prefs.sortAscending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                            null, tint = fsColors.accent, modifier = Modifier.size(16.dp),
                        )
                    }
                },
                onClick = {
                    if (active) Prefs.updateSort(field, !Prefs.sortAscending)
                    else Prefs.updateSort(field, field.defaultAscending)
                    onChanged()
                },
            )
        }
    }
}

/** Dropdown for switching between list/detailed/grid/gallery. */
@Composable
fun ViewModeMenu(
    expanded: Boolean,
    current: ViewMode,
    onDismiss: () -> Unit,
    onSelect: (ViewMode) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(fsColors.cardSecondary),
    ) {
        ViewMode.entries.forEach { mode ->
            val active = current == mode
            DropdownMenuItem(
                text = { Text(mode.label, color = if (active) fsColors.accent else fsColors.label) },
                trailingIcon = {
                    if (active) {
                        Icon(
                            Icons.Rounded.CheckCircle, null,
                            tint = fsColors.accent, modifier = Modifier.size(16.dp),
                        )
                    }
                },
                onClick = {
                    onSelect(mode)
                    onDismiss()
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowserScreen(
    onExit: () -> Unit,
    onOpenTransfer: () -> Unit,
    onArrange: (String, com.shahabcodes.filestorm.data.arrange.ArrangeMode) -> Unit,
    onOpenViewer: (List<String>, Int) -> Unit,
) {
    val tabs = BrowserTabs.tabs
    if (tabs.isEmpty()) {
        // Should not happen (home always opens a path first), but never crash.
        LaunchedEffect(Unit) { BrowserTabs.open(FileRepository.rootPath) }
        return
    }
    val path = BrowserTabs.active.current

    val vm: BrowserViewModel = viewModel(key = path) { BrowserViewModel(path) }
    val entries by vm.entries.collectAsState()
    val loading by vm.loading.collectAsState()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val viewMode = com.shahabcodes.filestorm.data.FolderViews.viewFor(path)
    var query by remember(path) { mutableStateOf("") }
    var selectionMode by remember(path) { mutableStateOf(false) }
    var selected by remember(path) { mutableStateOf(setOf<String>()) }
    var menuOpen by remember { mutableStateOf(false) }
    var pinchAccumulator by remember { mutableStateOf(1f) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var viewMenuOpen by remember { mutableStateOf(false) }
    var showDateSheet by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf<TransferOp?>(null) }
    var showNewFolder by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FsEntry?>(null) }
    var infoTarget by remember { mutableStateOf<FsEntry?>(null) }
    var styleTarget by remember { mutableStateOf<FsEntry?>(null) }
    var contextTarget by remember { mutableStateOf<FsEntry?>(null) }
    var metaTarget by remember { mutableStateOf<FsEntry?>(null) }
    var batchMetaOpen by remember { mutableStateOf(false) }
    var folderMetaRoot by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Prefs.showHidden) { vm.refresh() }

    fun goBack() {
        if (!BrowserTabs.pop()) onExit()
    }
    BackHandler { goBack() }

    val visibleEntries = remember(entries, query) {
        if (query.isBlank()) entries
        else entries.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
    val selectedEntries = remember(entries, selected) { entries.filter { it.path in selected } }

    fun exitSelection() {
        selectionMode = false
        selected = emptySet()
    }

    fun openEntryFolder(entry: FsEntry) {
        openFolderGated(context, entry.path, entry.name) { BrowserTabs.push(entry.path) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(fsColors.groupedBackground)
            .statusBarsPadding(),
    ) {
        // ── Tab bar ─────────────────────────────────────────────────────
        var renameTabIndex by remember { mutableStateOf(-1) }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                val isActive = index == BrowserTabs.activeIndex
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isActive) androidx.compose.ui.graphics.Brush.horizontalGradient(
                                listOf(fsColors.accent, fsColors.accent.copy(alpha = 0.82f))
                            ) else androidx.compose.ui.graphics.SolidColor(fsColors.card)
                        )
                        .combinedClickable(
                            onClick = { BrowserTabs.select(index) },
                            onLongClick = { renameTabIndex = index },
                        )
                        .padding(start = 12.dp, end = if (isActive && tabs.size > 1) 4.dp else 12.dp)
                        .padding(vertical = 5.dp),
                ) {
                    Column {
                        Text(
                            BrowserTabs.labelOf(index),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isActive) androidx.compose.ui.graphics.Color.White else fsColors.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 130.dp),
                        )
                        Text(
                            File(tab.current).name.ifEmpty { "Storage" },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isActive)
                                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f)
                            else fsColors.secondaryLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 130.dp),
                        )
                    }
                    if (isActive && tabs.size > 1) {
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            Icons.Rounded.Close, "Close tab",
                            tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                            modifier = Modifier
                                .pressScale { BrowserTabs.close(index) }
                                .padding(5.dp)
                                .size(14.dp),
                        )
                    }
                }
            }
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(fsColors.accent.copy(alpha = 0.14f))
                    .pressScale { BrowserTabs.newTab(FileRepository.rootPath) }
                    .padding(8.dp),
            ) {
                Icon(Icons.Rounded.Add, "New tab", tint = fsColors.accent, modifier = Modifier.size(16.dp))
            }
        }
        if (renameTabIndex >= 0) {
            val idx = renameTabIndex
            NameDialog(
                title = "Rename Tab",
                initial = BrowserTabs.labelOf(idx),
                confirmLabel = "Rename",
                onDismiss = { renameTabIndex = -1 },
                onConfirm = { name ->
                    renameTabIndex = -1
                    BrowserTabs.rename(idx, name)
                },
            )
        }

        // ── Navigation bar ──────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                Text(
                    "Cancel",
                    color = fsColors.accent,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .pressScale { exitSelection() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                Text(
                    "${selected.size} selected",
                    style = MaterialTheme.typography.titleMedium,
                    color = fsColors.label,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                val allSelected = selected.size == visibleEntries.size && visibleEntries.isNotEmpty()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (allSelected) fsColors.accent else fsColors.accent.copy(alpha = 0.14f))
                        .pressScale {
                            selected = if (allSelected) emptySet()
                            else visibleEntries.map { it.path }.toSet()
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Icon(
                        if (allSelected) Icons.Rounded.RemoveDone else Icons.Rounded.DoneAll,
                        null,
                        tint = if (allSelected) Color.White else fsColors.accent,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (allSelected) "Deselect" else "All",
                        color = if (allSelected) Color.White else fsColors.accent,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .pressScale { goBack() }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
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
                        modifier = Modifier
                            .pressScale { viewMenuOpen = true }
                            .padding(8.dp)
                            .size(21.dp),
                    )
                    ViewModeMenu(
                        expanded = viewMenuOpen,
                        current = viewMode,
                        onDismiss = { viewMenuOpen = false },
                        onSelect = { com.shahabcodes.filestorm.data.FolderViews.setView(path, it) },
                    )
                }
                Box {
                    Icon(
                        Icons.Rounded.SwapVert, "Sort",
                        tint = fsColors.accent,
                        modifier = Modifier
                            .pressScale { sortMenuOpen = true }
                            .padding(8.dp)
                            .size(22.dp),
                    )
                    SortMenu(
                        expanded = sortMenuOpen,
                        onDismiss = { sortMenuOpen = false },
                        onChanged = { vm.resort() },
                    )
                }
                Box {
                    Icon(
                        Icons.Rounded.MoreHoriz, "More",
                        tint = fsColors.accent,
                        modifier = Modifier
                            .pressScale { menuOpen = true }
                            .padding(8.dp)
                            .size(24.dp),
                    )
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        modifier = Modifier.background(fsColors.cardSecondary),
                    ) {
                        DropdownMenuItem(
                            text = { Text("Select", color = fsColors.label) },
                            leadingIcon = { Icon(Icons.Rounded.CheckCircle, null, tint = fsColors.accent) },
                            onClick = {
                                selectionMode = true
                                menuOpen = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Select all", color = fsColors.label) },
                            leadingIcon = { Icon(Icons.Rounded.DoneAll, null, tint = fsColors.accent) },
                            onClick = {
                                menuOpen = false
                                selectionMode = true
                                selected = visibleEntries.map { it.path }.toSet()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Auto arrange into months…", color = fsColors.label) },
                            leadingIcon = { Icon(Icons.Rounded.AutoAwesomeMosaic, null, tint = fsColors.accent) },
                            onClick = {
                                menuOpen = false
                                onArrange(path, com.shahabcodes.filestorm.data.arrange.ArrangeMode.MONTHLY)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Gather all files into this folder…", color = fsColors.label) },
                            leadingIcon = { Icon(Icons.Rounded.MoveToInbox, null, tint = fsColors.accent) },
                            onClick = {
                                menuOpen = false
                                onArrange(path, com.shahabcodes.filestorm.data.arrange.ArrangeMode.FLATTEN)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Fix dates in this folder…", color = fsColors.label) },
                            leadingIcon = { Icon(Icons.Rounded.Schedule, null, tint = fsColors.accent) },
                            onClick = {
                                menuOpen = false
                                folderMetaRoot = path
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Select by date…", color = fsColors.label) },
                            leadingIcon = { Icon(Icons.Rounded.DateRange, null, tint = fsColors.accent) },
                            onClick = {
                                menuOpen = false
                                showDateSheet = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("New folder", color = fsColors.label) },
                            leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, null, tint = fsColors.accent) },
                            onClick = {
                                menuOpen = false
                                showNewFolder = true
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (Prefs.showHidden) "Hide hidden files" else "Show hidden files",
                                    color = fsColors.label,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (Prefs.showHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    null, tint = fsColors.accent,
                                )
                            },
                            onClick = {
                                menuOpen = false
                                Prefs.updateShowHidden(!Prefs.showHidden)
                            },
                        )
                    }
                }
            }
        }

        // ── Title + search ──────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Text(
                if (path == FileRepository.rootPath) "Internal Storage" else File(path).name,
                style = MaterialTheme.typography.headlineLarge,
                color = fsColors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (com.shahabcodes.filestorm.data.FolderLocks.isLocked(path)) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Rounded.Lock, "Locked folder",
                    tint = fsColors.orange, modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        IosSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Search this folder",
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(12.dp))

        // ── File list ───────────────────────────────────────────────────
        Box(Modifier.weight(1f)) {
            when {
                loading -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(color = fsColors.accent)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Loading folder…",
                        color = fsColors.secondaryLabel,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                visibleEntries.isEmpty() -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Rounded.FolderOff, null,
                        tint = fsColors.secondaryLabel.copy(alpha = 0.4f),
                        modifier = Modifier.size(52.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (query.isBlank()) "This folder is empty" else "No results for \"$query\"",
                        color = fsColors.secondaryLabel,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                else -> FileListView(
                    entries = visibleEntries,
                    selectionMode = selectionMode,
                    selected = selected,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                    viewMode = viewMode,
                    columns = com.shahabcodes.filestorm.data.FolderViews.columnsFor(path, viewMode),
                    onZoom = { zoom -> pinchAccumulator = applyPinch(path, viewMode, pinchAccumulator * zoom) },
                    onClick = { entry ->
                        if (selectionMode) {
                            selected = if (entry.path in selected) selected - entry.path
                            else selected + entry.path
                        } else if (entry.isDirectory) {
                            openEntryFolder(entry)
                        } else {
                            val media = visibleEntries.filter {
                                !it.isDirectory &&
                                    (it.kind == com.shahabcodes.filestorm.data.FileKind.IMAGE ||
                                        it.kind == com.shahabcodes.filestorm.data.FileKind.VIDEO)
                            }
                            val index = media.indexOfFirst { it.path == entry.path }
                            if (index >= 0) onOpenViewer(media.map { it.path }, index)
                            else openFile(context, entry)
                        }
                    },
                    onLongClick = { entry ->
                        if (!selectionMode) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            contextTarget = entry
                        }
                    },
                )
            }

            // ── Selection action bar ────────────────────────────────────
            androidx.compose.animation.AnimatedVisibility(
                visible = selectionMode,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Column {
                    if (selected.isNotEmpty()) {
                        val single = if (selected.size == 1) {
                            entries.firstOrNull { it.path == selected.first() }
                        } else null
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        ) {
                            if (single != null) {
                                SelectionPill(Icons.Rounded.Info, "Info") { infoTarget = single }
                                SelectionPill(Icons.Rounded.DriveFileRenameOutline, "Rename") { renameTarget = single }
                                if (single.isDirectory) {
                                    SelectionPill(Icons.Rounded.Palette, "Style") { styleTarget = single }
                                    val fav = Favorites.isFavorite(single.path)
                                    SelectionPill(
                                        if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        if (fav) "Unfavorite" else "Favorite",
                                    ) { Favorites.toggle(single.path) }
                                } else {
                                    SelectionPill(Icons.Rounded.Schedule, "Edit date") { metaTarget = single }
                                }
                            } else if (selectedEntries.any { !it.isDirectory }) {
                                SelectionPill(Icons.Rounded.Schedule, "Fix dates") { batchMetaOpen = true }
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

    // ── Sheets & dialogs ────────────────────────────────────────────────
    if (showDateSheet) {
        DateRangeSheet(
            onDismiss = { showDateSheet = false },
            onConfirm = { start, end ->
                showDateSheet = false
                selectionMode = true
                selected = visibleEntries
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
            excludedPaths = selected,
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

    if (showNewFolder) {
        NameDialog(
            title = "New Folder",
            initial = "",
            confirmLabel = "Create",
            onDismiss = { showNewFolder = false },
            onConfirm = { name ->
                showNewFolder = false
                FileRepository.createFolder(path, name)
                vm.refresh()
            },
        )
    }

    contextTarget?.let { target ->
        EntryContextSheet(
            entry = target,
            onDismiss = { contextTarget = null },
            onOpen = {
                if (target.isDirectory) openEntryFolder(target) else openFile(context, target)
            },
            onOpenInNewTab = if (target.isDirectory) {
                {
                    openFolderGated(context, target.path, target.name) {
                        BrowserTabs.newTab(target.path)
                    }
                }
            } else null,
            onArrange = if (target.isDirectory) ({
                onArrange(target.path, com.shahabcodes.filestorm.data.arrange.ArrangeMode.MONTHLY)
            }) else null,
            onFlatten = if (target.isDirectory) ({
                onArrange(target.path, com.shahabcodes.filestorm.data.arrange.ArrangeMode.FLATTEN)
            }) else null,
            onProperties = { infoTarget = target },
            onRename = { renameTarget = target },
            onEditDate = {
                if (target.isDirectory) folderMetaRoot = target.path else metaTarget = target
            },
            onStyle = if (target.isDirectory) ({ styleTarget = target }) else null,
            onCopy = {
                selected = setOf(target.path)
                showPicker = TransferOp.COPY
            },
            onMove = {
                selected = setOf(target.path)
                showPicker = TransferOp.MOVE
            },
            onDelete = {
                selected = setOf(target.path)
                confirmDelete = true
            },
            onSelect = {
                selectionMode = true
                selected = setOf(target.path)
            },
        )
    }

    infoTarget?.let { target ->
        InfoSheet(entry = target, onDismiss = { infoTarget = null })
    }

    metaTarget?.let { target ->
        com.shahabcodes.filestorm.ui.meta.MetadataSheet(
            entry = target,
            onDismiss = {
                metaTarget = null
                vm.refresh()
            },
        )
    }

    if (batchMetaOpen) {
        com.shahabcodes.filestorm.ui.meta.BatchDateSheet(
            entries = selectedEntries,
            onDismiss = {
                batchMetaOpen = false
                exitSelection()
                vm.refresh()
            },
        )
    }

    folderMetaRoot?.let { root ->
        com.shahabcodes.filestorm.ui.meta.BatchDateSheet(
            folderRoot = root,
            onDismiss = {
                folderMetaRoot = null
                vm.refresh()
            },
        )
    }

    styleTarget?.let { target ->
        FolderStyleSheet(entry = target, onDismiss = { styleTarget = null })
    }

    renameTarget?.let { target ->
        NameDialog(
            title = "Rename",
            initial = target.name,
            confirmLabel = "Rename",
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                renameTarget = null
                FileRepository.rename(target, name)
                exitSelection()
                vm.refresh()
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
                exitSelection()
                vm.viewModelScope.launch {
                    com.shahabcodes.filestorm.data.TrashManager.moveToTrash(toDelete)
                    vm.refresh()
                }
            },
        )
    }

    // Refresh listing when a transfer touching this folder finishes.
    val transfer by TransferManager.state.collectAsState()
    LaunchedEffect(transfer.state) {
        if (!transfer.isActive) vm.refresh()
    }
}

@Composable
private fun SelectionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(fsColors.card)
            .pressScale(onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Icon(icon, null, tint = fsColors.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = fsColors.accent, style = MaterialTheme.typography.labelLarge)
    }
}
