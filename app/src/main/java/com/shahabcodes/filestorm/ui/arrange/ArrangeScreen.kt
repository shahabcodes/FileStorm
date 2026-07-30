package com.shahabcodes.filestorm.ui.arrange

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shahabcodes.filestorm.data.FileRepository
import com.shahabcodes.filestorm.data.arrange.ArrangePhase
import com.shahabcodes.filestorm.data.arrange.ArrangeRunner
import com.shahabcodes.filestorm.ui.components.GroupedCard
import com.shahabcodes.filestorm.ui.components.RowSeparator
import com.shahabcodes.filestorm.ui.components.pressScale
import com.shahabcodes.filestorm.ui.theme.fsColors
import com.shahabcodes.filestorm.util.Formatters
import java.io.File

private fun phaseNumber(phase: ArrangePhase): Int = when (phase) {
    ArrangePhase.SCANNING -> 1
    ArrangePhase.REVIEW_PLAN -> 2
    ArrangePhase.MOVING -> 3
    ArrangePhase.REVIEW_CLEANUP, ArrangePhase.CLEANING -> 4
    else -> 0
}

private fun phaseTitle(phase: ArrangePhase): String = when (phase) {
    ArrangePhase.IDLE -> "Auto Arrange"
    ArrangePhase.SCANNING -> "Scanning"
    ArrangePhase.REVIEW_PLAN -> "Review the plan"
    ArrangePhase.MOVING -> "Moving files"
    ArrangePhase.REVIEW_CLEANUP -> "Empty folders"
    ArrangePhase.CLEANING -> "Removing folders"
    ArrangePhase.DONE -> "All done"
    ArrangePhase.CANCELLED -> "Cancelled"
    ArrangePhase.FAILED -> "Could not continue"
}

@Composable
fun ArrangeScreen(path: String, onBack: () -> Unit) {
    val s by ArrangeRunner.state.collectAsState()

    LaunchedEffect(path) {
        if (s.phase == ArrangePhase.IDLE || s.root != path) {
            ArrangeRunner.reset()
            ArrangeRunner.scan(path)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(fsColors.groupedBackground)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
            if (s.isBusy) {
                Text(
                    "Cancel", color = fsColors.red, style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .pressScale { ArrangeRunner.cancel() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            } else if (s.phase == ArrangePhase.DONE || s.phase == ArrangePhase.CANCELLED) {
                Text(
                    "Close", color = fsColors.accent, style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .pressScale {
                            ArrangeRunner.reset()
                            onBack()
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }

        Text(
            phaseTitle(s.phase),
            style = MaterialTheme.typography.headlineLarge,
            color = fsColors.label,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            File(path).name.ifEmpty { "Internal storage" } + " · all subfolders",
            style = MaterialTheme.typography.bodySmall,
            color = fsColors.secondaryLabel,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )
        Spacer(Modifier.height(14.dp))

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, bottom = 40.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ── Live narration of the current phase ─────────────────────
            item {
                GroupedCard {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when {
                                s.isBusy -> CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.5.dp,
                                    color = fsColors.accent,
                                )
                                s.phase == ArrangePhase.DONE -> Icon(
                                    Icons.Rounded.CheckCircle, null, tint = fsColors.green,
                                )
                                s.phase == ArrangePhase.FAILED -> Icon(
                                    Icons.Rounded.Error, null, tint = fsColors.red,
                                )
                                else -> Icon(
                                    Icons.Rounded.CalendarMonth, null, tint = fsColors.accent,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                if (phaseNumber(s.phase) > 0) {
                                    Text(
                                        "Step ${phaseNumber(s.phase)} of 4",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = fsColors.secondaryLabel,
                                    )
                                }
                                Text(
                                    s.error ?: s.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = fsColors.label,
                                )
                            }
                        }
                        if (s.phase == ArrangePhase.SCANNING) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "${s.scannedFiles} media file(s) · ${s.scannedFolders} folder(s) seen",
                                style = MaterialTheme.typography.labelSmall,
                                color = fsColors.secondaryLabel,
                            )
                        }
                    }
                }
            }

            // ── Step 3 live progress ────────────────────────────────────
            if (s.phase == ArrangePhase.MOVING) {
                item {
                    GroupedCard {
                        Column(Modifier.padding(16.dp)) {
                            val animated by animateFloatAsState(s.progress, tween(300), label = "ap")
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    "${(animated * 100).toInt()}%",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = fsColors.label,
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    s.currentMonth,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = fsColors.accent,
                                    modifier = Modifier.padding(bottom = 6.dp),
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { animated },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = fsColors.accent,
                                trackColor = fsColors.fill,
                                strokeCap = StrokeCap.Round,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                s.currentFile,
                                style = MaterialTheme.typography.bodySmall,
                                color = fsColors.secondaryLabel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth()) {
                                Stat("Moved", "${s.movedFiles}", Modifier.weight(1f))
                                Stat("Files left", "${s.filesLeft}", Modifier.weight(1f))
                                Stat("Size left", Formatters.bytes(s.bytesLeft), Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth()) {
                                Stat("Speed", Formatters.speed(s.speedBps), Modifier.weight(1f))
                                Stat("Time left", Formatters.eta(s.etaSeconds), Modifier.weight(1f))
                                Stat("Skipped", "${s.skippedFiles}", Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // ── Step 2: the plan ────────────────────────────────────────
            if (s.months.isNotEmpty() && s.phase != ArrangePhase.DONE) {
                item {
                    Text(
                        "MONTH FOLDERS",
                        style = MaterialTheme.typography.labelMedium,
                        color = fsColors.secondaryLabel,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                item {
                    GroupedCard {
                        s.months.forEachIndexed { index, month ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        month.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = fsColors.label,
                                    )
                                    Text(
                                        "${month.files} file(s) · ${Formatters.bytes(month.bytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = fsColors.secondaryLabel,
                                    )
                                }
                                Text(
                                    if (month.folderExists) "existing" else "new",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (month.folderExists) fsColors.green else fsColors.accent,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            (if (month.folderExists) fsColors.green else fsColors.accent)
                                                .copy(alpha = 0.14f)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                            if (index != s.months.lastIndex) RowSeparator(startIndent = 14.dp)
                        }
                    }
                }
            }

            if (s.phase == ArrangePhase.REVIEW_PLAN && s.months.isNotEmpty()) {
                item {
                    ActionButton(
                        label = "Move ${s.totalFiles} File(s)",
                        background = fsColors.accent,
                        contentColor = Color.White,
                    ) { ArrangeRunner.startMoving() }
                }
                item {
                    Text(
                        "Existing month folders are reused; only missing ones are created. " +
                            "Files already in the right month folder are left untouched.",
                        style = MaterialTheme.typography.labelSmall,
                        color = fsColors.secondaryLabel,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }

            // ── Step 4: empty folder cleanup ────────────────────────────
            if (s.phase == ArrangePhase.REVIEW_CLEANUP && s.emptyFolders.isNotEmpty()) {
                item {
                    GroupedCard {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Rounded.FolderOff, null, tint = fsColors.orange)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "About to delete ${s.emptyFolders.size} empty folder(s)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = fsColors.label,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "These folders contain no files at all — they are left over " +
                                        "after the move. Deleting them is permanent, but nothing " +
                                        "inside them is lost because they are empty. Your photos " +
                                        "and videos are already safe in the month folders.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = fsColors.secondaryLabel,
                                )
                            }
                        }
                    }
                }
                item {
                    Text(
                        "FOLDERS TO REMOVE",
                        style = MaterialTheme.typography.labelMedium,
                        color = fsColors.secondaryLabel,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                item {
                    GroupedCard {
                        s.emptyFolders.take(60).forEachIndexed { index, folder ->
                            Text(
                                folder.replace(FileRepository.rootPath, "Internal storage"),
                                style = MaterialTheme.typography.bodySmall,
                                color = fsColors.label,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                            )
                            if (index != s.emptyFolders.take(60).lastIndex) RowSeparator(startIndent = 14.dp)
                        }
                        if (s.emptyFolders.size > 60) {
                            Text(
                                "…and ${s.emptyFolders.size - 60} more",
                                style = MaterialTheme.typography.labelSmall,
                                color = fsColors.secondaryLabel,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            )
                        }
                    }
                }
                item {
                    ActionButton(
                        label = "Delete ${s.emptyFolders.size} Empty Folder(s)",
                        background = fsColors.red.copy(alpha = 0.14f),
                        contentColor = fsColors.red,
                        icon = Icons.Rounded.CleaningServices,
                    ) { ArrangeRunner.cleanUp() }
                }
                item {
                    ActionButton(
                        label = "Keep Them",
                        background = fsColors.fill,
                        contentColor = fsColors.label,
                    ) { ArrangeRunner.skipCleanUp() }
                }
            }

            if (s.phase == ArrangePhase.REVIEW_CLEANUP && s.emptyFolders.isEmpty()) {
                item {
                    ActionButton(
                        label = "Finish",
                        background = fsColors.accent,
                        contentColor = Color.White,
                    ) { ArrangeRunner.skipCleanUp() }
                }
            }

            // ── Summary ─────────────────────────────────────────────────
            if (s.phase == ArrangePhase.DONE) {
                item {
                    GroupedCard {
                        Column(Modifier.padding(16.dp)) {
                            SummaryLine("Files moved", "${s.movedFiles}", fsColors.green)
                            SummaryLine("Already in place", "${s.skippedFiles}")
                            SummaryLine("Month folders used", "${s.months.size} (${s.newFolderCount} created)")
                            SummaryLine("Empty folders removed", "${s.deletedFolders}")
                            if (s.failedFiles > 0) SummaryLine("Failed", "${s.failedFiles}", fsColors.red)
                            val secs = ((s.finishedAt - s.startedAt) / 1000).coerceAtLeast(1)
                            SummaryLine("Took", Formatters.eta(secs))
                        }
                    }
                }
            }

            if (s.errors.isNotEmpty() && !s.isBusy) {
                item {
                    GroupedCard {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "Problems",
                                style = MaterialTheme.typography.titleMedium,
                                color = fsColors.label,
                            )
                            Spacer(Modifier.height(6.dp))
                            s.errors.take(10).forEach {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = fsColors.red)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    background: Color,
    contentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .pressScale(onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = contentColor, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(label, color = contentColor, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = fsColors.label, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = fsColors.secondaryLabel)
    }
}

@Composable
private fun SummaryLine(label: String, value: String, valueColor: Color = fsColors.label) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = fsColors.secondaryLabel,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}
