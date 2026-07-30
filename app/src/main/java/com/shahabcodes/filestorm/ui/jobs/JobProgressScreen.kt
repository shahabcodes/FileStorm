package com.shahabcodes.filestorm.ui.jobs

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shahabcodes.filestorm.data.jobs.JobPhase
import com.shahabcodes.filestorm.data.jobs.JobRunner
import com.shahabcodes.filestorm.data.jobs.MonthState
import com.shahabcodes.filestorm.ui.components.GroupedCard
import com.shahabcodes.filestorm.ui.components.RowSeparator
import com.shahabcodes.filestorm.ui.components.pressScale
import com.shahabcodes.filestorm.ui.theme.fsColors
import com.shahabcodes.filestorm.util.Formatters
import java.io.File

@Composable
fun JobProgressScreen(onBack: () -> Unit) {
    val s by JobRunner.state.collectAsState()

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
            if (s.isActive) {
                Text(
                    "Cancel",
                    color = fsColors.red,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .pressScale { JobRunner.cancel() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            } else if (s.phase != JobPhase.IDLE) {
                Text(
                    "Clear",
                    color = fsColors.accent,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .pressScale {
                            JobRunner.clearFinished()
                            onBack()
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }

        Text(
            when (s.phase) {
                JobPhase.IDLE -> "Job Progress"
                JobPhase.SCANNING -> "Scanning…"
                JobPhase.RUNNING -> s.jobName
                JobPhase.DONE -> "Job Complete"
                JobPhase.CANCELLED -> "Job Cancelled"
                JobPhase.FAILED -> "Job Failed"
            },
            style = MaterialTheme.typography.headlineLarge,
            color = fsColors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(14.dp))

        if (s.phase == JobPhase.IDLE) {
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
                Text("No job has run yet", color = fsColors.secondaryLabel, style = MaterialTheme.typography.bodyMedium)
            }
            return@Column
        }

        if (s.phase == JobPhase.FAILED) {
            GroupedCard(Modifier.padding(horizontal = 16.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Error, null, tint = fsColors.red)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        s.error ?: "Unknown error",
                        style = MaterialTheme.typography.bodyMedium,
                        color = fsColors.label,
                    )
                }
            }
            return@Column
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, bottom = 40.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                GroupedCard {
                    Column(
                        Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val animated by animateFloatAsState(s.progress, tween(400), label = "jp")
                        val track = fsColors.fill
                        val accent = fsColors.accent
                        val green = fsColors.green
                        Box(contentAlignment = Alignment.Center) {
                            Canvas(Modifier.size(140.dp)) {
                                val stroke = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                                drawArc(color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke)
                                drawArc(
                                    brush = Brush.sweepGradient(
                                        if (s.phase == JobPhase.DONE && s.failedFiles == 0) listOf(green, green)
                                        else listOf(accent, green, accent)
                                    ),
                                    startAngle = -90f,
                                    sweepAngle = 360f * animated.coerceIn(0f, 1f),
                                    useCenter = false, style = stroke,
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "${(animated * 100).toInt()}%",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = fsColors.label,
                                )
                                if (s.isActive && s.currentMonthIndex >= 0) {
                                    Text(
                                        s.months.getOrNull(s.currentMonthIndex)?.label ?: "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = fsColors.secondaryLabel,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "→ ${File(s.destination).name.ifEmpty { "Destination" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = fsColors.secondaryLabel,
                        )
                        if (s.isActive && s.currentFileName.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                s.currentFileName,
                                style = MaterialTheme.typography.bodySmall,
                                color = fsColors.secondaryLabel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.height(16.dp))

                        Row(Modifier.fillMaxWidth()) {
                            Stat("Months left", "${s.monthsLeft}", Modifier.weight(1f))
                            Stat("Files left", "${s.filesLeft}", Modifier.weight(1f))
                            Stat("Size left", Formatters.bytes(s.bytesLeft), Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth()) {
                            Stat("Speed", if (s.isActive) Formatters.speed(s.speedBps) else "—", Modifier.weight(1f))
                            Stat("Time left", if (s.isActive) Formatters.eta(s.etaSeconds) else "—", Modifier.weight(1f))
                            Stat(
                                "Done",
                                "${s.doneFiles + s.skippedFiles}/${s.totalFiles}",
                                Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            if (!s.isActive) {
                item {
                    GroupedCard {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (s.failedFiles == 0 && s.phase == JobPhase.DONE) Icons.Rounded.CheckCircle
                                else Icons.Rounded.Error,
                                null,
                                tint = if (s.failedFiles == 0 && s.phase == JobPhase.DONE) fsColors.green else fsColors.orange,
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    "${s.doneFiles} organized · ${s.skippedFiles} already in place · ${s.failedFiles} failed",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = fsColors.label,
                                )
                                val secs = ((s.finishedAt - s.startedAt) / 1000).coerceAtLeast(1)
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

            item {
                Text(
                    "Months",
                    style = MaterialTheme.typography.titleLarge,
                    color = fsColors.label,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            item {
                GroupedCard {
                    s.months.forEachIndexed { index, month ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(fsColors.accent.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.CalendarMonth, null,
                                    tint = fsColors.accent, modifier = Modifier.size(19.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    month.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = fsColors.label,
                                )
                                Text(
                                    buildString {
                                        append("${month.fileCount} files · ${Formatters.bytes(month.totalBytes)}")
                                        if (month.skippedFiles > 0) append(" · ${month.skippedFiles} skipped")
                                        if (month.failedFiles > 0) append(" · ${month.failedFiles} failed")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (month.failedFiles > 0) fsColors.orange else fsColors.secondaryLabel,
                                )
                            }
                            when (month.state) {
                                MonthState.PENDING -> Icon(
                                    Icons.Rounded.HourglassEmpty, null,
                                    tint = fsColors.secondaryLabel.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp),
                                )
                                MonthState.RUNNING -> CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.5.dp,
                                    color = fsColors.accent,
                                )
                                MonthState.DONE -> Icon(
                                    if (month.failedFiles > 0) Icons.Rounded.Error else Icons.Rounded.CheckCircle,
                                    null,
                                    tint = if (month.failedFiles > 0) fsColors.orange else fsColors.green,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        if (index != s.months.lastIndex) RowSeparator(startIndent = 62.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = fsColors.label, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = fsColors.secondaryLabel)
    }
}
