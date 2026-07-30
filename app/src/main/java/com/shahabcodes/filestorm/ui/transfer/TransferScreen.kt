package com.shahabcodes.filestorm.ui.transfer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shahabcodes.filestorm.data.FsEntry
import com.shahabcodes.filestorm.transfer.ItemStatus
import com.shahabcodes.filestorm.transfer.JobState
import com.shahabcodes.filestorm.transfer.TransferItem
import com.shahabcodes.filestorm.transfer.TransferManager
import com.shahabcodes.filestorm.transfer.TransferOp
import com.shahabcodes.filestorm.ui.components.FileIconView
import com.shahabcodes.filestorm.ui.components.GroupedCard
import com.shahabcodes.filestorm.ui.components.RowSeparator
import com.shahabcodes.filestorm.ui.components.pressScale
import com.shahabcodes.filestorm.ui.theme.fsColors
import com.shahabcodes.filestorm.util.Formatters
import java.io.File

@Composable
fun TransferScreen(onBack: () -> Unit) {
    val job by TransferManager.state.collectAsState()

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
                    Icons.AutoMirrored.Rounded.ArrowBackIos, null,
                    tint = fsColors.accent, modifier = Modifier.size(18.dp),
                )
                Text("Back", color = fsColors.accent, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.weight(1f))
            if (job.isActive) {
                Text(
                    "Cancel",
                    color = fsColors.red,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .pressScale { TransferManager.cancel() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            } else if (job.state == JobState.DONE || job.state == JobState.CANCELLED) {
                Text(
                    "Clear",
                    color = fsColors.accent,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .pressScale {
                            TransferManager.clearFinished()
                            onBack()
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }

        Text(
            when (job.state) {
                JobState.IDLE -> "Transfers"
                JobState.PREPARING -> "Preparing…"
                JobState.RUNNING -> if (job.op == TransferOp.MOVE) "Moving Files" else "Copying Files"
                JobState.DONE -> "Transfer Complete"
                JobState.CANCELLED -> "Transfer Cancelled"
            },
            style = MaterialTheme.typography.headlineLarge,
            color = fsColors.label,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(16.dp))

        if (job.state == JobState.IDLE) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Rounded.HourglassEmpty, null,
                    tint = fsColors.secondaryLabel.copy(alpha = 0.4f),
                    modifier = Modifier.size(52.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "No active transfers",
                    color = fsColors.secondaryLabel,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return@Column
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, bottom = 40.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Overall progress card ───────────────────────────────────
            item {
                GroupedCard {
                    Column(
                        Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ProgressRing(
                            fraction = job.progress,
                            active = job.isActive,
                            done = job.state == JobState.DONE && job.failedCount == 0,
                        )
                        Spacer(Modifier.height(16.dp))

                        Text(
                            "${Formatters.bytes(job.bytesDone)} of ${Formatters.bytes(job.totalBytes)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = fsColors.label,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "→ ${File(job.destination).name.ifEmpty { "Internal storage" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = fsColors.secondaryLabel,
                        )
                        Spacer(Modifier.height(16.dp))

                        Row(Modifier.fillMaxWidth()) {
                            StatCell("Speed", if (job.isActive) Formatters.speed(job.speedBps) else "—", Modifier.weight(1f))
                            StatCell(
                                "Time left",
                                if (job.isActive) Formatters.eta(job.etaSeconds) else "—",
                                Modifier.weight(1f),
                            )
                            StatCell(
                                "Files",
                                "${job.doneCount}/${job.items.size}",
                                Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // ── Result summary when finished ────────────────────────────
            if (!job.isActive && (job.doneCount > 0 || job.failedCount > 0)) {
                item {
                    GroupedCard {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (job.failedCount == 0) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                                null,
                                tint = if (job.failedCount == 0) fsColors.green else fsColors.orange,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    if (job.failedCount == 0)
                                        "All ${job.doneCount} item(s) ${if (job.op == TransferOp.MOVE) "moved" else "copied"} successfully"
                                    else
                                        "${job.doneCount} succeeded · ${job.failedCount} failed",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = fsColors.label,
                                )
                                val secs = ((job.finishedAt - job.startedAt) / 1000).coerceAtLeast(1)
                                Text(
                                    "Took ${Formatters.eta(secs)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = fsColors.secondaryLabel,
                                )
                            }
                        }
                    }
                }
            }

            // ── Per-file list ───────────────────────────────────────────
            item {
                Text(
                    "Items",
                    style = MaterialTheme.typography.titleLarge,
                    color = fsColors.label,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            item {
                GroupedCard {
                    job.items.forEachIndexed { index, item ->
                        TransferItemRow(item)
                        if (index != job.items.lastIndex) RowSeparator(startIndent = 64.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = fsColors.label, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = fsColors.secondaryLabel)
    }
}

@Composable
private fun ProgressRing(fraction: Float, active: Boolean, done: Boolean) {
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(400),
        label = "progress",
    )
    val track = fsColors.fill
    val accent = fsColors.accent
    val green = fsColors.green
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(150.dp)) {
            val stroke = Stroke(width = 13.dp.toPx(), cap = StrokeCap.Round)
            drawArc(color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke)
            drawArc(
                brush = Brush.sweepGradient(
                    if (done) listOf(green, green) else listOf(accent, green, accent)
                ),
                startAngle = -90f,
                sweepAngle = 360f * animated.coerceIn(0f, 1f),
                useCenter = false,
                style = stroke,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${(animated * 100).toInt()}%",
                style = MaterialTheme.typography.headlineMedium,
                color = fsColors.label,
            )
            if (active) {
                Text("in progress", style = MaterialTheme.typography.labelSmall, color = fsColors.secondaryLabel)
            }
        }
    }
}

@Composable
private fun TransferItemRow(item: TransferItem) {
    val entry = FsEntry(
        path = item.sourcePath,
        name = item.name,
        isDirectory = item.isDirectory,
        size = item.totalBytes,
        lastModified = 0,
        kind = com.shahabcodes.filestorm.data.FsEntry.kindOf(item.name, item.isDirectory),
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FileIconView(entry, size = 38.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = fsColors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when (item.status) {
                        ItemStatus.PENDING -> "Waiting…"
                        ItemStatus.IN_PROGRESS ->
                            "${Formatters.bytes(item.bytesDone)} of ${Formatters.bytes(item.totalBytes.coerceAtLeast(0))}"
                        ItemStatus.DONE -> Formatters.bytes(item.totalBytes.coerceAtLeast(0))
                        ItemStatus.FAILED -> item.error ?: "Failed"
                        ItemStatus.SKIPPED -> "Skipped"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when (item.status) {
                        ItemStatus.FAILED -> fsColors.red
                        ItemStatus.DONE -> fsColors.green
                        else -> fsColors.secondaryLabel
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            when (item.status) {
                ItemStatus.PENDING -> Icon(
                    Icons.Rounded.HourglassEmpty, null,
                    tint = fsColors.secondaryLabel.copy(alpha = 0.5f), modifier = Modifier.size(20.dp),
                )
                ItemStatus.IN_PROGRESS -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp, color = fsColors.accent,
                )
                ItemStatus.DONE -> Icon(
                    Icons.Rounded.CheckCircle, null, tint = fsColors.green, modifier = Modifier.size(22.dp),
                )
                ItemStatus.FAILED -> Icon(
                    Icons.Rounded.Error, null, tint = fsColors.red, modifier = Modifier.size(22.dp),
                )
                ItemStatus.SKIPPED -> Icon(
                    Icons.Rounded.RemoveCircleOutline, null,
                    tint = fsColors.secondaryLabel, modifier = Modifier.size(20.dp),
                )
            }
        }
        if (item.status == ItemStatus.IN_PROGRESS && item.totalBytes > 0) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (item.bytesDone.toFloat() / item.totalBytes).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = fsColors.accent,
                trackColor = fsColors.fill,
                strokeCap = StrokeCap.Round,
            )
        }
    }
}
