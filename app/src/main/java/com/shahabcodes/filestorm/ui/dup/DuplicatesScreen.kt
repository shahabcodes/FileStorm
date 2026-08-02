package com.shahabcodes.filestorm.ui.dup

import com.shahabcodes.filestorm.ui.components.FsSpinner
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.shahabcodes.filestorm.data.FileRepository
import com.shahabcodes.filestorm.data.FsEntry
import com.shahabcodes.filestorm.data.dup.DupPair
import com.shahabcodes.filestorm.data.dup.DupPhase
import com.shahabcodes.filestorm.data.dup.DuplicateFinder
import com.shahabcodes.filestorm.ui.browser.FolderPickerSheet
import com.shahabcodes.filestorm.ui.components.FileIconView
import com.shahabcodes.filestorm.ui.components.GroupedCard
import com.shahabcodes.filestorm.ui.components.RowSeparator
import com.shahabcodes.filestorm.ui.components.SelectionCircle
import com.shahabcodes.filestorm.ui.components.pressScale
import com.shahabcodes.filestorm.ui.theme.fsColors
import com.shahabcodes.filestorm.util.Formatters
import java.io.File

private fun pretty(path: String): String = path.replace(FileRepository.rootPath, "Internal storage")

@Composable
fun DuplicatesScreen(onBack: () -> Unit) {
    val s by DuplicateFinder.state.collectAsState()

    var folder1 by remember { mutableStateOf("") }
    var folder2 by remember { mutableStateOf("") }
    var deep by remember { mutableStateOf(false) }
    var hidden by remember { mutableStateOf(true) }
    var picking by remember { mutableStateOf(0) } // 0 none, 1 folder1, 2 folder2
    var wholeStorage by remember { mutableStateOf(false) }
    var selected by remember(s.pairs) { mutableStateOf(s.pairs.map { it.id }.toSet()) }
    var confirmSide by remember { mutableStateOf(0) }
    var comparePair by remember { mutableStateOf<com.shahabcodes.filestorm.data.dup.DupPair?>(null) }
    var sort by remember { mutableStateOf(DupSort.SIZE) }
    var sortAscending by remember { mutableStateOf(false) }

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
            if (s.isActive) {
                Text(
                    "Cancel", color = fsColors.red, style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .pressScale { DuplicateFinder.cancel() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            } else if (s.phase != DupPhase.IDLE && !s.cleaning) {
                Text(
                    "New Search", color = fsColors.accent, style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .pressScale { DuplicateFinder.clearFinished() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }

        Text(
            "Duplicate Finder",
            style = MaterialTheme.typography.headlineLarge,
            color = fsColors.label,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            if (wholeStorage) "Sweep the whole phone for repeated files"
            else "Match files across two folders and reclaim space",
            style = MaterialTheme.typography.bodySmall,
            color = fsColors.secondaryLabel,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(10.dp))

        when (s.phase) {
            DupPhase.IDLE -> SetupView(
                folder1 = folder1, folder2 = folder2, deep = deep, hidden = hidden,
                wholeStorage = wholeStorage,
                onScopeChange = { wholeStorage = it },
                onPick1 = { picking = 1 }, onPick2 = { picking = 2 },
                onDeepChange = { deep = it },
                onHiddenChange = { hidden = it },
                onStart = {
                    if (wholeStorage) DuplicateFinder.startWholeStorage(deep, hidden)
                    else DuplicateFinder.start(folder1, folder2, deep, hidden)
                },
            )

            DupPhase.SCANNING -> ScanningView(s)

            DupPhase.COMPARING -> Centered {
                val animated by animateFloatAsState(s.progress, tween(300), label = "dp")
                val track = fsColors.fill
                val accent = fsColors.accent
                val green = fsColors.green
                Box(contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(120.dp)) {
                        val stroke = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round)
                        drawArc(color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke)
                        drawArc(
                            brush = Brush.sweepGradient(listOf(accent, green, accent)),
                            startAngle = -90f, sweepAngle = 360f * animated,
                            useCenter = false, style = stroke,
                        )
                    }
                    Text(
                        "${(animated * 100).toInt()}%",
                        style = MaterialTheme.typography.titleLarge,
                        color = fsColors.label,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Comparing contents · ${s.comparedCount}/${s.candidateCount} files",
                    color = fsColors.label, style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "${Formatters.speed(s.speedBps)} · ${Formatters.eta(s.etaSeconds)} left · ${s.currentFileName}",
                    color = fsColors.secondaryLabel, style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }

            DupPhase.FAILED -> Column(Modifier.padding(horizontal = 16.dp)) {
                GroupedCard {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Error, null, tint = fsColors.red)
                        Spacer(Modifier.width(12.dp))
                        Text(s.error ?: "Unknown error", color = fsColors.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            DupPhase.DONE, DupPhase.CANCELLED -> ResultsView(
                sort = sort,
                sortAscending = sortAscending,
                onSortChange = { field ->
                    if (field == sort) sortAscending = !sortAscending
                    else {
                        sort = field
                        sortAscending = field == DupSort.NAME || field == DupSort.FOLDER
                    }
                },
                selected = selected,
                onToggle = { pid ->
                    selected = if (pid in selected) selected - pid else selected + pid
                },
                onToggleAll = {
                    selected = if (selected.size == s.pairs.size) emptySet()
                    else s.pairs.map { it.id }.toSet()
                },
                onDelete = { side -> confirmSide = side },
                onCompare = { pair -> comparePair = pair },
            )
        }
    }

    comparePair?.let { pair ->
        DupCompareSheet(
            pair = pair,
            deepCompared = s.deepCompare,
            onDismiss = { comparePair = null },
            onDeleteSide = { side ->
                comparePair = null
                DuplicateFinder.deleteSide(side, setOf(pair.id))
            },
        )
    }

    if (picking != 0) {
        FolderPickerSheet(
            title = "Choose folder ${picking}",
            confirmLabel = "Use This Folder",
            onDismiss = { picking = 0 },
            onConfirm = { path ->
                if (picking == 1) folder1 = path else folder2 = path
                picking = 0
            },
        )
    }

    if (confirmSide != 0) {
        val side = confirmSide
        val count = selected.size
        val bytes = DuplicateFinder.state.value.pairs.filter { it.id in selected }.sumOf { it.size }
        val folderName = File(if (side == 1) s.folder1 else s.folder2).name
        AlertDialog(
            onDismissRequest = { confirmSide = 0 },
            containerColor = fsColors.card,
            title = {
                Text(
                    if (s.wholeStorage) "Delete $count extra copy(s)?"
                    else "Delete from \"$folderName\"?",
                    color = fsColors.label,
                )
            },
            text = {
                Text(
                    if (s.wholeStorage) {
                        "$count file(s) (${Formatters.bytes(bytes)}) will be moved to the Trash. " +
                            "One copy of every file is always kept, and nothing leaves the " +
                            "Trash until you empty it."
                    } else {
                        "$count duplicate file(s) (${Formatters.bytes(bytes)}) will be moved from " +
                            "\"$folderName\" to the Trash. The copies in the other folder stay untouched."
                    },
                    color = fsColors.secondaryLabel,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmSide = 0
                    DuplicateFinder.deleteSide(side, selected)
                }) { Text("Move to Trash", color = fsColors.red) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSide = 0 }) { Text("Cancel", color = fsColors.secondaryLabel) }
            },
        )
    }

    // The live trash progress dialog is rendered app-wide, so nothing extra is
    // needed here — it appears the moment the move starts.
}

@Composable
private fun Centered(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

@Composable
private fun SetupView(
    folder1: String,
    folder2: String,
    deep: Boolean,
    hidden: Boolean,
    wholeStorage: Boolean,
    onScopeChange: (Boolean) -> Unit,
    onPick1: () -> Unit,
    onPick2: () -> Unit,
    onDeepChange: (Boolean) -> Unit,
    onHiddenChange: (Boolean) -> Unit,
    onStart: () -> Unit,
) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(fsColors.fill)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            ScopeTab("Two Folders", !wholeStorage, Modifier.weight(1f)) { onScopeChange(false) }
            ScopeTab("Entire Storage", wholeStorage, Modifier.weight(1f)) { onScopeChange(true) }
        }
        Spacer(Modifier.height(14.dp))
        if (wholeStorage) {
            GroupedCard {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Storage, null, tint = fsColors.accent, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            "Whole-storage sweep",
                            style = MaterialTheme.typography.bodyLarge,
                            color = fsColors.label,
                        )
                        Text(
                            "Every folder on internal storage is searched, however deeply " +
                                "nested. Each set of identical files keeps its oldest copy " +
                                "and the rest are listed as extras.",
                            style = MaterialTheme.typography.bodySmall,
                            color = fsColors.secondaryLabel,
                        )
                    }
                }
            }
        } else {
            GroupedCard {
                FolderRow("Folder 1", folder1, onPick1)
                RowSeparator(startIndent = 16.dp)
                FolderRow("Folder 2", folder2, onPick2)
            }
        }
        Spacer(Modifier.height(14.dp))
        GroupedCard {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Deep compare", style = MaterialTheme.typography.bodyLarge, color = fsColors.label)
                    Text(
                        "Also compare file contents byte-for-byte. Slower, but 100% certain.",
                        style = MaterialTheme.typography.bodySmall,
                        color = fsColors.secondaryLabel,
                    )
                }
                Switch(
                    checked = deep,
                    onCheckedChange = onDeepChange,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = fsColors.accent,
                        checkedThumbColor = Color.White,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = fsColors.fill,
                        uncheckedBorderColor = Color.Transparent,
                    ),
                )
            }
            RowSeparator(startIndent = 14.dp)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Include hidden files", style = MaterialTheme.typography.bodyLarge, color = fsColors.label)
                    Text(
                        "Search hidden files and folders too (names starting with a dot).",
                        style = MaterialTheme.typography.bodySmall,
                        color = fsColors.secondaryLabel,
                    )
                }
                Switch(
                    checked = hidden,
                    onCheckedChange = onHiddenChange,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = fsColors.accent,
                        checkedThumbColor = Color.White,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = fsColors.fill,
                        uncheckedBorderColor = Color.Transparent,
                    ),
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        val ready = wholeStorage || (folder1.isNotEmpty() && folder2.isNotEmpty())
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (ready) fsColors.accent else fsColors.fill)
                .pressScale { if (ready) onStart() }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Find Duplicates",
                color = if (ready) Color.White else fsColors.secondaryLabel,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Files match when their name, type and size are identical. Nested folders are always " +
                "searched; File Storm's own trash is never touched." +
                if (wholeStorage) {
                    " App-private Android/data and Android/obb are skipped because Android " +
                        "blocks them, but Android/media — where WhatsApp and similar apps store " +
                        "files — is included."
                } else "",
            style = MaterialTheme.typography.labelSmall,
            color = fsColors.secondaryLabel,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun ScopeTab(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) fsColors.card else Color.Transparent)
            .pressScale(onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) fsColors.label else fsColors.secondaryLabel,
        )
    }
}

@Composable
private fun FolderRow(label: String, path: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Folder, null, tint = fsColors.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = fsColors.secondaryLabel)
            Text(
                if (path.isEmpty()) "Choose folder…" else pretty(path),
                style = MaterialTheme.typography.bodyMedium,
                color = if (path.isEmpty()) fsColors.accent else fsColors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ResultsView(
    sort: DupSort,
    sortAscending: Boolean,
    onSortChange: (DupSort) -> Unit,
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
    onToggleAll: () -> Unit,
    onDelete: (Int) -> Unit,
    onCompare: (com.shahabcodes.filestorm.data.dup.DupPair) -> Unit,
) {
    val s by DuplicateFinder.state.collectAsState()
    val shown = remember(s.pairs, sort, sortAscending) {
        val comparator: Comparator<DupPair> = when (sort) {
            DupSort.SIZE -> compareBy<DupPair> { it.size }
            DupSort.DATE -> compareBy<DupPair> { it.modified }
            DupSort.NAME -> compareBy<DupPair, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
            DupSort.FOLDER -> compareBy<DupPair, String>(
                String.CASE_INSENSITIVE_ORDER
            ) { it.folderName }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        }
        s.pairs.sortedWith(if (sortAscending) comparator else comparator.reversed())
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, bottom = 170.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    GroupedCard {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.ContentCopy, null,
                                    tint = if (s.pairs.isEmpty()) fsColors.green else fsColors.orange,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        if (s.pairs.isEmpty()) "No duplicates found"
                                        else if (s.wholeStorage) "${s.extraCopies} extra copy(s) found"
                                        else "${s.pairs.size} duplicate pair(s) found",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = fsColors.label,
                                    )
                                    if (s.pairs.isNotEmpty()) {
                                        Text(
                                            "Up to ${Formatters.bytes(s.wastedBytes)} reclaimable" +
                                                (if (s.deepCompare) " · contents verified" else "") +
                                                (if (s.includeHidden) " · hidden included" else ""),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = fsColors.secondaryLabel,
                                        )
                                    }
                                }
                            }
                            if (s.truncatedBy > 0) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Showing the first ${s.pairs.size}. ${s.truncatedBy} more " +
                                        "extra copy(s) were found — clear these and run the " +
                                        "sweep again to see the rest.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = fsColors.orange,
                                )
                            }
                            if (s.cleanedCount >= 0) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "${s.cleanedCount} file(s) (${Formatters.bytes(s.cleanedBytes)}) moved to Trash",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = fsColors.green,
                                )
                            }
                        }
                    }
                }

                if (s.pairs.isNotEmpty()) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            DupSort.entries.forEach { option ->
                                SortChip(
                                    label = option.label,
                                    selected = sort == option,
                                    ascending = sortAscending,
                                ) { onSortChange(option) }
                            }
                        }
                    }
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Duplicates",
                                style = MaterialTheme.typography.titleLarge,
                                color = fsColors.label,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (selected.size == s.pairs.size) "Deselect All" else "Select All",
                                color = fsColors.accent,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.pressScale(onToggleAll).padding(6.dp),
                            )
                        }
                    }
                    itemsIndexed(shown, key = { _, pair -> pair.id }) { index, pair ->
                        GroupedCard {
                            run {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .pressScale { onCompare(pair) }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(Modifier.pressScale { onToggle(pair.id) }.padding(2.dp)) {
                                        SelectionCircle(selected = pair.id in selected)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    FileIconView(
                                        FsEntry(
                                            path = pair.pathA,
                                            name = pair.name,
                                            isDirectory = false,
                                            size = pair.size,
                                            lastModified = 0,
                                            kind = FsEntry.kindOf(pair.name, false),
                                        ),
                                        size = 38.dp,
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            pair.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = fsColors.label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            (if (s.wholeStorage) "Keep: " else "1: ") +
                                                pretty(File(pair.pathA).parent ?: ""),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = fsColors.secondaryLabel,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            (if (s.wholeStorage) "Extra: " else "2: ") +
                                                pretty(File(pair.pathB).parent ?: ""),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = fsColors.secondaryLabel,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            Formatters.bytes(pair.size),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = fsColors.label,
                                        )
                                        if (pair.modified > 0) {
                                            Text(
                                                Formatters.fileDate(pair.modified),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = fsColors.secondaryLabel,
                                            )
                                        }
                                        Text(
                                            "Compare",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = fsColors.accent,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (s.pairs.isNotEmpty()) {
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
                        if (s.wholeStorage) {
                            "${selected.size} selected · one copy of each is always kept"
                        } else {
                            "Delete ${selected.size} selected duplicate(s) from:"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = fsColors.secondaryLabel,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (s.wholeStorage) {
                        // Only the extras can go. Offering "delete the keepers"
                        // here could wipe every copy in a group of three or more.
                        SideButton(
                            label = "Delete Extra Copies",
                            enabled = selected.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { onDelete(2) }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SideButton(
                                label = File(s.folder1).name.ifEmpty { "Folder 1" },
                                enabled = selected.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                            ) { onDelete(1) }
                            SideButton(
                                label = File(s.folder2).name.ifEmpty { "Folder 2" },
                                enabled = selected.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                            ) { onDelete(2) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SideButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(13.dp))
            .background(if (enabled) fsColors.red.copy(alpha = 0.12f) else fsColors.fill)
            .pressScale { if (enabled) onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) fsColors.red else fsColors.secondaryLabel,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}


/** How the duplicate list is ordered once a scan finishes. */
enum class DupSort(val label: String) {
    SIZE("Size"),
    DATE("Date"),
    NAME("Name"),
    FOLDER("Folder"),
}

@Composable
private fun SortChip(
    label: String,
    selected: Boolean,
    ascending: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) fsColors.accent else fsColors.fill)
            .pressScale(onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color.White else fsColors.secondaryLabel,
        )
        if (selected) {
            Spacer(Modifier.width(4.dp))
            Icon(
                if (ascending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                null,
                tint = Color.White,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

/**
 * Live picture of a whole-storage sweep. Pass 1 cannot know its total so it
 * shows a moving indicator with running counts; pass 2 knows exactly how many
 * files pass 1 saw, so it shows a real percentage.
 */
@Composable
private fun ScanningView(s: com.shahabcodes.filestorm.data.dup.DupState) {
    val determinate = s.pass == 2 && s.passTotal > 0
    val animated by animateFloatAsState(s.scanProgress, tween(300), label = "scan")
    val track = fsColors.fill
    val accent = fsColors.accent
    val green = fsColors.green

    // Elapsed time has to tick on its own; nothing else updates once a scan
    // reaches a slow folder, and a frozen screen looks like a hang.
    var tick by remember { mutableStateOf(0L) }
    LaunchedEffect(s.phase) {
        while (true) {
            tick = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }
    val elapsed = remember(tick, s.startedAt) { s.scanElapsedSeconds }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(20.dp))
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(150.dp)) {
                val stroke = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                drawArc(
                    color = track, startAngle = -90f, sweepAngle = 360f,
                    useCenter = false, style = stroke,
                )
                if (determinate) {
                    drawArc(
                        brush = Brush.sweepGradient(listOf(accent, green, accent)),
                        startAngle = -90f, sweepAngle = 360f * animated,
                        useCenter = false, style = stroke,
                    )
                }
            }
            if (!determinate) {
                com.shahabcodes.filestorm.ui.components.FsSpinner(size = 126.dp, strokeWidth = 12.dp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (determinate) {
                    Text(
                        "${(animated * 100).toInt()}%",
                        style = MaterialTheme.typography.headlineMedium,
                        color = fsColors.label,
                    )
                } else {
                    Text(
                        Formatters.compactCount(s.scannedFiles),
                        style = MaterialTheme.typography.headlineMedium,
                        color = fsColors.label,
                    )
                }
                Text(
                    if (s.wholeStorage) "Step ${s.pass.coerceAtLeast(1)} of 2" else "Scanning",
                    style = MaterialTheme.typography.labelSmall,
                    color = fsColors.secondaryLabel,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            when {
                !s.wholeStorage -> "Scanning both folders"
                s.pass == 1 -> "Indexing every file"
                else -> "Collecting the repeats"
            },
            style = MaterialTheme.typography.titleMedium,
            color = fsColors.label,
        )
        Text(
            when {
                !s.wholeStorage -> "Looking for files that appear in both"
                s.pass == 1 -> "Fingerprinting names and sizes across all storage"
                else -> "Re-reading only the files that look repeated"
            },
            style = MaterialTheme.typography.bodySmall,
            color = fsColors.secondaryLabel,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))

        GroupedCard {
            StatRow(
                Icons.Rounded.Description,
                "Files scanned",
                if (determinate) "${Formatters.compactCount(s.scannedFiles)} of " +
                    Formatters.compactCount(s.passTotal)
                else Formatters.compactCount(s.scannedFiles),
                fsColors.accent,
            )
            RowSeparator(startIndent = 52.dp)
            StatRow(
                Icons.Rounded.Storage,
                "Data seen",
                Formatters.bytes(s.scannedBytes),
                fsColors.orange,
            )
            RowSeparator(startIndent = 52.dp)
            StatRow(
                Icons.Rounded.ContentCopy,
                if (s.pass == 2) "Copies collected" else "Repeats spotted",
                Formatters.compactCount(s.groupsFound),
                fsColors.green,
            )
            RowSeparator(startIndent = 52.dp)
            StatRow(
                Icons.Rounded.Schedule,
                "Elapsed",
                Formatters.eta(elapsed),
                fsColors.secondaryLabel,
            )
        }
        Spacer(Modifier.height(12.dp))
        if (s.currentFolder.isNotEmpty()) {
            Text(
                pretty(s.currentFolder),
                style = MaterialTheme.typography.labelSmall,
                color = fsColors.secondaryLabel,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun StatRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    tint: Color,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = fsColors.secondaryLabel,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, color = fsColors.label)
    }
}
