package com.shahabcodes.filestorm.ui.browser

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
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
fun ViewModeMenu(expanded: Boolean, onDismiss: () -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(fsColors.cardSecondary),
    ) {
        ViewMode.entries.forEach { mode ->
            val active = Prefs.viewMode == mode
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
                    Prefs.updateViewMode(mode)
                    onDismiss()
                },
            )
        }
    }
}

@Composable
fun BrowserScreen(
    path: String,
    onOpenFolder: (String) -> Unit,
    onBack: () -> Unit,
    onOpenTransfer: () -> Unit,
) {
    val vm: BrowserViewModel = viewModel(key = path) { BrowserViewModel(path) }
    val entries by vm.entries.collectAsState()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    var query by remember { mutableStateOf("") }
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var menuOpen by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var viewMenuOpen by remember { mutableStateOf(false) }
    var showDateSheet by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf<TransferOp?>(null) }
    var showNewFolder by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FsEntry?>(null) }
    var infoTarget by remember { mutableStateOf<FsEntry?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Prefs.showHidden) { vm.refresh() }

    val visibleEntries = remember(entries, query) {
        if (query.isBlank()) entries
        else entries.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
    val selectedEntries = remember(entries, selected) { entries.filter { it.path in selected } }

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
        // ── Navigation bar ──────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
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
                Text(
                    if (selected.size == visibleEntries.size) "Deselect All" else "Select All",
                    color = fsColors.accent,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .pressScale {
                            selected = if (selected.size == visibleEntries.size) emptySet()
                            else visibleEntries.map { it.path }.toSet()
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .pressScale(onBack)
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
                    ViewModeMenu(expanded = viewMenuOpen, onDismiss = { viewMenuOpen = false })
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
        Text(
            if (path == FileRepository.rootPath) "Internal Storage" else File(path).name,
            style = MaterialTheme.typography.headlineLarge,
            color = fsColors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
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
            if (visibleEntries.isEmpty()) {
                Column(
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
            } else {
                FileListView(
                    entries = visibleEntries,
                    selectionMode = selectionMode,
                    selected = selected,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                    onClick = { entry ->
                        if (selectionMode) {
                            selected = if (entry.path in selected) selected - entry.path
                            else selected + entry.path
                        } else if (entry.isDirectory) {
                            onOpenFolder(entry.path)
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

            // ── Selection action bar ────────────────────────────────────
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
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(fsColors.card)
                                    .pressScale {
                                        infoTarget = entries.firstOrNull { it.path == selected.first() }
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(fsColors.card)
                                    .pressScale {
                                        renameTarget = entries.firstOrNull { it.path == selected.first() }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.DriveFileRenameOutline, null,
                                    tint = fsColors.accent, modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Rename", color = fsColors.accent, style = MaterialTheme.typography.labelLarge)
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

    infoTarget?.let { target ->
        InfoSheet(entry = target, onDismiss = { infoTarget = null })
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
            count = selected.size,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                val toDelete = selectedEntries
                exitSelection()
                vm.viewModelScope.launch {
                    FileRepository.delete(toDelete)
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
