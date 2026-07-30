package com.shahabcodes.filestorm.ui.transfer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shahabcodes.filestorm.data.FsEntry
import com.shahabcodes.filestorm.transfer.ItemStatus
import com.shahabcodes.filestorm.transfer.JobState
import com.shahabcodes.filestorm.transfer.TransferManager
import com.shahabcodes.filestorm.transfer.TransferOp
import com.shahabcodes.filestorm.ui.components.FileIconView
import com.shahabcodes.filestorm.ui.components.GroupedCard
import com.shahabcodes.filestorm.ui.components.pressScale
import com.shahabcodes.filestorm.ui.theme.fsColors
import com.shahabcodes.filestorm.util.Formatters
import java.io.File

/**
 * Compact transfer dialog: live progress, speed, ETA and the file currently
 * moving. The expand button opens the full-screen transfer view.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferSheet(
    onDismiss: () -> Unit,
    onExpand: () -> Unit,
) {
    val job by TransferManager.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = fsColors.groupedBackground,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when (job.state) {
                            JobState.PREPARING -> "Preparing…"
                            JobState.RUNNING -> if (job.op == TransferOp.MOVE) "Moving Files" else "Copying Files"
                            JobState.DONE -> "Transfer Complete"
                            JobState.CANCELLED -> "Transfer Cancelled"
                            JobState.IDLE -> "Transfers"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = fsColors.label,
                    )
                    if (job.destination.isNotEmpty()) {
                        Text(
                            "to ${File(job.destination).name.ifEmpty { "Internal storage" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = fsColors.secondaryLabel,
                        )
                    }
                }
                Icon(
                    Icons.Rounded.OpenInFull, "Expand",
                    tint = fsColors.accent,
                    modifier = Modifier
                        .pressScale(onExpand)
                        .padding(10.dp)
                        .size(20.dp),
                )
                Icon(
                    Icons.Rounded.Close, "Close",
                    tint = fsColors.secondaryLabel,
                    modifier = Modifier
                        .pressScale(onDismiss)
                        .padding(10.dp)
                        .size(20.dp),
                )
            }
            Spacer(Modifier.height(14.dp))

            GroupedCard {
                Column(Modifier.padding(16.dp)) {
                    // Big % + bytes
                    Row(verticalAlignment = Alignment.Bottom) {
                        val animated by animateFloatAsState(job.progress, tween(300), label = "p")
                        Text(
                            "${(animated * 100).toInt()}%",
                            style = MaterialTheme.typography.headlineMedium,
                            color = fsColors.label,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${Formatters.bytes(job.bytesDone)} of ${Formatters.bytes(job.totalBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = fsColors.secondaryLabel,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { job.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (job.state == JobState.DONE && job.failedCount == 0) fsColors.green else fsColors.accent,
                        trackColor = fsColors.fill,
                        strokeCap = StrokeCap.Round,
                    )
                    Spacer(Modifier.height(14.dp))

                    Row(Modifier.fillMaxWidth()) {
                        MiniStat("Speed", if (job.isActive) Formatters.speed(job.speedBps) else "—", Modifier.weight(1f))
                        MiniStat("Time left", if (job.isActive) Formatters.eta(job.etaSeconds) else "—", Modifier.weight(1f))
                        MiniStat("Files", "${job.doneCount}/${job.items.size}", Modifier.weight(1f))
                        if (job.failedCount > 0) {
                            MiniStat("Failed", "${job.failedCount}", Modifier.weight(1f), valueColor = fsColors.red)
                        }
                    }
                }
            }

            // Current file / result line
            Spacer(Modifier.height(12.dp))
            val current = job.items.getOrNull(job.currentIndex)
            if (job.isActive && current != null) {
                GroupedCard {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FileIconView(
                            FsEntry(
                                path = current.sourcePath,
                                name = current.name,
                                isDirectory = current.isDirectory,
                                size = current.totalBytes,
                                lastModified = 0,
                                kind = FsEntry.kindOf(current.name, current.isDirectory),
                            ),
                            size = 34.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                current.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = fsColors.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${Formatters.bytes(current.bytesDone)} of ${Formatters.bytes(current.totalBytes.coerceAtLeast(0))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = fsColors.secondaryLabel,
                            )
                        }
                    }
                }
            } else if (job.state == JobState.DONE || job.state == JobState.CANCELLED) {
                GroupedCard {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (job.failedCount == 0 && job.state == JobState.DONE) Icons.Rounded.CheckCircle
                            else Icons.Rounded.Error,
                            null,
                            tint = if (job.failedCount == 0 && job.state == JobState.DONE) fsColors.green else fsColors.orange,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            when {
                                job.state == JobState.CANCELLED -> "Cancelled — ${job.doneCount} finished before stopping"
                                job.failedCount == 0 -> "All ${job.doneCount} item(s) transferred successfully"
                                else -> "${job.doneCount} succeeded · ${job.failedCount} failed — expand for details"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = fsColors.label,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Action button
            if (job.isActive) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(fsColors.red.copy(alpha = 0.12f))
                        .pressScale { TransferManager.cancel() }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Cancel Transfer", color = fsColors.red, style = MaterialTheme.typography.titleMedium)
                }
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(fsColors.accent)
                        .pressScale {
                            TransferManager.clearFinished()
                            onDismiss()
                        }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Done", color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun MiniStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = fsColors.label,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = fsColors.secondaryLabel)
    }
}
