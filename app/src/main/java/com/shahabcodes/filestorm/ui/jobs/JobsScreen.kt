package com.shahabcodes.filestorm.ui.jobs

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shahabcodes.filestorm.data.FileRepository
import com.shahabcodes.filestorm.data.jobs.JobRunner
import com.shahabcodes.filestorm.data.jobs.JobStore
import com.shahabcodes.filestorm.data.jobs.OrganizeJob
import com.shahabcodes.filestorm.transfer.JobService
import com.shahabcodes.filestorm.ui.browser.FolderPickerSheet
import com.shahabcodes.filestorm.ui.components.GroupedCard
import com.shahabcodes.filestorm.ui.components.RowSeparator
import com.shahabcodes.filestorm.ui.components.pressScale
import com.shahabcodes.filestorm.ui.theme.fsColors
import com.shahabcodes.filestorm.util.Formatters

private fun prettyPath(path: String): String =
    path.replace(FileRepository.rootPath, "Internal storage")

@Composable
fun JobsScreen(onBack: () -> Unit, onOpenProgress: () -> Unit, onOpenVerify: () -> Unit) {
    val context = LocalContext.current
    val jobs = JobStore.jobs.sortedByDescending { it.createdAt }
    val runState by JobRunner.state.collectAsState()

    var editorJob by remember { mutableStateOf<OrganizeJob?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<OrganizeJob?>(null) }
    var busyNotice by remember { mutableStateOf(false) }

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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .pressScale {
                        editorJob = null
                        showEditor = true
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Icon(Icons.Rounded.Add, null, tint = fsColors.accent, modifier = Modifier.size(20.dp))
                Text("New Job", color = fsColors.accent, style = MaterialTheme.typography.bodyLarge)
            }
        }

        Text(
            "Jobs",
            style = MaterialTheme.typography.headlineLarge,
            color = fsColors.label,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            "Organize files into month folders like April2026",
            style = MaterialTheme.typography.bodySmall,
            color = fsColors.secondaryLabel,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(10.dp))

        // Live banner for the active/last run
        if (runState.phase != com.shahabcodes.filestorm.data.jobs.JobPhase.IDLE) {
            GroupedCard(
                Modifier
                    .padding(horizontal = 16.dp)
                    .pressScale(onOpenProgress),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CalendarMonth, null, tint = fsColors.accent)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            runState.jobName,
                            style = MaterialTheme.typography.titleMedium,
                            color = fsColors.label,
                        )
                        Text(
                            if (runState.isActive)
                                "${(runState.progress * 100).toInt()}% · ${runState.monthsLeft} months left · tap for details"
                            else "Last run finished · tap for details",
                            style = MaterialTheme.typography.bodySmall,
                            color = fsColors.secondaryLabel,
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        if (jobs.isEmpty()) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Rounded.CalendarMonth, null,
                    tint = fsColors.secondaryLabel.copy(alpha = 0.4f),
                    modifier = Modifier.size(52.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "No jobs yet — create one to sort files\ninto month folders automatically",
                    color = fsColors.secondaryLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, bottom = 40.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(jobs, key = { it.id }) { job ->
                    GroupedCard {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        job.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = fsColors.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        if (job.move) "Moves files" else "Copies files",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (job.move) fsColors.orange else fsColors.green,
                                    )
                                }
                                Icon(
                                    Icons.Rounded.Delete, "Delete job",
                                    tint = fsColors.red.copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .pressScale { deleteTarget = job }
                                        .padding(8.dp)
                                        .size(20.dp),
                                )
                                Icon(
                                    Icons.Rounded.VerifiedUser, "Verify transfers",
                                    tint = fsColors.green,
                                    modifier = Modifier
                                        .pressScale {
                                            if (com.shahabcodes.filestorm.data.jobs.VerifyRunner.start(job)) {
                                                com.shahabcodes.filestorm.transfer.VerifyService.start(context)
                                                onOpenVerify()
                                            } else busyNotice = true
                                        }
                                        .padding(8.dp)
                                        .size(20.dp),
                                )
                                Box(
                                    Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(fsColors.accent)
                                        .pressScale {
                                            if (JobRunner.start(job)) {
                                                JobService.start(context)
                                                onOpenProgress()
                                            } else busyNotice = true
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Rounded.PlayArrow, "Run job",
                                        tint = Color.White, modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            job.sources.forEach { src ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Rounded.Folder, null,
                                        tint = fsColors.secondaryLabel, modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        prettyPath(src),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = fsColors.secondaryLabel,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("→", color = fsColors.accent, style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    prettyPath(job.destination) + "/MonthYear",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = fsColors.secondaryLabel,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (job.lastRunAt > 0) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Last run ${Formatters.fileDate(job.lastRunAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = fsColors.secondaryLabel.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        JobEditorSheet(
            existing = editorJob,
            onDismiss = { showEditor = false },
        )
    }

    deleteTarget?.let { job ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = fsColors.card,
            title = { Text("Delete job \"${job.name}\"?", color = fsColors.label) },
            text = { Text("The job definition is removed. Your files are not touched.", color = fsColors.secondaryLabel) },
            confirmButton = {
                TextButton(onClick = {
                    JobStore.delete(job.id)
                    deleteTarget = null
                }) { Text("Delete", color = fsColors.red) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel", color = fsColors.secondaryLabel) }
            },
        )
    }

    if (busyNotice) {
        AlertDialog(
            onDismissRequest = { busyNotice = false },
            containerColor = fsColors.card,
            title = { Text("Another task is running", color = fsColors.label) },
            text = { Text("Wait for the current job or verification to finish, or cancel it first.", color = fsColors.secondaryLabel) },
            confirmButton = {
                TextButton(onClick = { busyNotice = false }) { Text("OK", color = fsColors.accent) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JobEditorSheet(existing: OrganizeJob?, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf(existing?.name ?: "Monthly organize") }
    var sources by remember { mutableStateOf(existing?.sources ?: emptyList()) }
    var destination by remember { mutableStateOf(existing?.destination ?: "") }
    var move by remember { mutableStateOf(existing?.move ?: false) }
    var includeSub by remember { mutableStateOf(existing?.includeSubfolders ?: false) }
    var pickingSource by remember { mutableStateOf(false) }
    var pickingDest by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun validate(): String? {
        if (name.isBlank()) return "Give the job a name"
        if (sources.isEmpty()) return "Pick at least one source folder"
        if (destination.isEmpty()) return "Pick a destination folder"
        for (src in sources) {
            if (destination == src) return "Destination cannot be a source folder"
            if (destination.startsWith("$src/")) return "Destination cannot be inside a source folder"
        }
        return null
    }

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (existing == null) "New Job" else "Edit Job",
                    style = MaterialTheme.typography.titleLarge,
                    color = fsColors.label,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) { Text("Cancel", color = fsColors.secondaryLabel) }
                TextButton(onClick = {
                    val problem = validate()
                    if (problem != null) {
                        error = problem
                    } else {
                        JobStore.save(
                            OrganizeJob(
                                id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                                name = name.trim(),
                                sources = sources.distinct(),
                                destination = destination,
                                move = move,
                                includeSubfolders = includeSub,
                                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                                lastRunAt = existing?.lastRunAt ?: 0L,
                            )
                        )
                        onDismiss()
                    }
                }) { Text("Save", color = fsColors.accent) }
            }

            error?.let {
                Text(it, color = fsColors.red, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Job name", color = fsColors.secondaryLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = fsColors.accent,
                    unfocusedBorderColor = fsColors.separator,
                    focusedTextColor = fsColors.label,
                    unfocusedTextColor = fsColors.label,
                    cursorColor = fsColors.accent,
                ),
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.height(16.dp))

            Text(
                "SOURCE FOLDERS (max 2)",
                style = MaterialTheme.typography.labelMedium,
                color = fsColors.secondaryLabel,
            )
            Spacer(Modifier.height(8.dp))
            GroupedCard {
                sources.forEachIndexed { i, src ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Folder, null, tint = fsColors.accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            prettyPath(src),
                            style = MaterialTheme.typography.bodyMedium,
                            color = fsColors.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Rounded.Delete, "Remove source",
                            tint = fsColors.red.copy(alpha = 0.8f),
                            modifier = Modifier
                                .pressScale { sources = sources.filterIndexed { j, _ -> j != i } }
                                .padding(4.dp)
                                .size(18.dp),
                        )
                    }
                    RowSeparator(startIndent = 16.dp)
                }
                if (sources.size < 2) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .pressScale { pickingSource = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = fsColors.accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Add source folder",
                            style = MaterialTheme.typography.bodyMedium,
                            color = fsColors.accent,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            Text(
                "DESTINATION",
                style = MaterialTheme.typography.labelMedium,
                color = fsColors.secondaryLabel,
            )
            Spacer(Modifier.height(8.dp))
            GroupedCard {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressScale { pickingDest = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Folder, null, tint = fsColors.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (destination.isEmpty()) "Choose destination folder…"
                        else prettyPath(destination) + "/MonthYear",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (destination.isEmpty()) fsColors.accent else fsColors.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            GroupedCard {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Move files", style = MaterialTheme.typography.bodyLarge, color = fsColors.label)
                        Text(
                            if (move) "Files are moved out of the source folders"
                            else "Files stay in place; copies are organized",
                            style = MaterialTheme.typography.bodySmall,
                            color = fsColors.secondaryLabel,
                        )
                    }
                    Switch(
                        checked = move,
                        onCheckedChange = { move = it },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = fsColors.orange,
                            checkedThumbColor = Color.White,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = fsColors.fill,
                            uncheckedBorderColor = Color.Transparent,
                        ),
                    )
                }
                RowSeparator(startIndent = 16.dp)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Include subfolders", style = MaterialTheme.typography.bodyLarge, color = fsColors.label)
                        Text(
                            "Also organize files inside nested folders",
                            style = MaterialTheme.typography.bodySmall,
                            color = fsColors.secondaryLabel,
                        )
                    }
                    Switch(
                        checked = includeSub,
                        onCheckedChange = { includeSub = it },
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
        }
    }

    if (pickingSource) {
        FolderPickerSheet(
            title = "Choose source folder",
            confirmLabel = "Use This Folder",
            onDismiss = { pickingSource = false },
            onConfirm = { path ->
                pickingSource = false
                if (path !in sources) sources = sources + path
                error = null
            },
        )
    }

    if (pickingDest) {
        FolderPickerSheet(
            title = "Choose destination",
            confirmLabel = "Use This Folder",
            onDismiss = { pickingDest = false },
            onConfirm = { path ->
                pickingDest = false
                destination = path
                error = null
            },
        )
    }
}
