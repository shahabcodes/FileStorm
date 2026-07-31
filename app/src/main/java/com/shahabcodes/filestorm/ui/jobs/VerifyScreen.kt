package com.shahabcodes.filestorm.ui.jobs

import com.shahabcodes.filestorm.ui.components.FsSpinner
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.VerifiedUser
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shahabcodes.filestorm.data.jobs.VerifyPhase
import com.shahabcodes.filestorm.data.jobs.VerifyRunner
import com.shahabcodes.filestorm.ui.components.GroupedCard
import com.shahabcodes.filestorm.ui.components.RowSeparator
import com.shahabcodes.filestorm.ui.components.pressScale
import com.shahabcodes.filestorm.ui.theme.fsColors
import com.shahabcodes.filestorm.util.Formatters

@Composable
fun VerifyScreen(onBack: () -> Unit) {
    val s by VerifyRunner.state.collectAsState()

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
                        .pressScale { VerifyRunner.cancel() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            } else if (s.phase != VerifyPhase.IDLE && !s.cleaning) {
                Text(
                    "Clear",
                    color = fsColors.accent,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .pressScale {
                            VerifyRunner.clearFinished()
                            onBack()
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }

        Text(
            when (s.phase) {
                VerifyPhase.IDLE -> "Verification"
                VerifyPhase.SCANNING -> "Scanning…"
                VerifyPhase.VERIFYING -> "Verifying Files"
                VerifyPhase.DONE -> if (s.issues.isEmpty()) "All Verified" else "Verification Report"
                VerifyPhase.CANCELLED -> "Verification Cancelled"
                VerifyPhase.FAILED -> "Verification Failed"
            },
            style = MaterialTheme.typography.headlineLarge,
            color = fsColors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            s.jobName,
            style = MaterialTheme.typography.bodySmall,
            color = fsColors.secondaryLabel,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )
        Spacer(Modifier.height(12.dp))

        if (s.phase == VerifyPhase.IDLE) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Rounded.VerifiedUser, null,
                    tint = fsColors.secondaryLabel.copy(alpha = 0.4f),
                    modifier = Modifier.size(52.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text("Run Verify from a job to check its transfers", color = fsColors.secondaryLabel, style = MaterialTheme.typography.bodyMedium)
            }
            return@Column
        }

        if (s.phase == VerifyPhase.FAILED) {
            GroupedCard(Modifier.padding(horizontal = 16.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Error, null, tint = fsColors.red)
                    Spacer(Modifier.width(12.dp))
                    Text(s.error ?: "Unknown error", style = MaterialTheme.typography.bodyMedium, color = fsColors.label)
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
                        val animated by animateFloatAsState(s.progress, tween(400), label = "vp")
                        val track = fsColors.fill
                        val accent = fsColors.accent
                        val green = fsColors.green
                        Box(contentAlignment = Alignment.Center) {
                            Canvas(Modifier.size(130.dp)) {
                                val stroke = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round)
                                drawArc(color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke)
                                drawArc(
                                    brush = Brush.sweepGradient(
                                        if (s.allVerified) listOf(green, green) else listOf(accent, green, accent)
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
                                if (s.isActive) {
                                    Text("verifying", style = MaterialTheme.typography.labelSmall, color = fsColors.secondaryLabel)
                                }
                            }
                        }
                        if (s.isActive && s.currentFileName.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
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
                            VStat("Files left", "${s.filesLeft}", Modifier.weight(1f))
                            VStat("Size left", Formatters.bytes(s.bytesLeft), Modifier.weight(1f))
                            VStat("Speed", if (s.isActive) Formatters.speed(s.speedBps) else "—", Modifier.weight(1f))
                            VStat("Time left", if (s.isActive) Formatters.eta(s.etaSeconds) else "—", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth()) {
                            VStat("Verified", "${s.verifiedFiles}", Modifier.weight(1f), fsColors.green)
                            VStat("Issues", "${s.issues.size}", Modifier.weight(1f), if (s.issues.isEmpty()) fsColors.label else fsColors.red)
                            VStat("Checked", "${s.checkedFiles}/${s.totalFiles}", Modifier.weight(1f))
                        }
                    }
                }
            }

            // ── Verdict + cleanup ───────────────────────────────────────
            if (s.phase == VerifyPhase.DONE) {
                item {
                    GroupedCard {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (s.issues.isEmpty()) Icons.Rounded.VerifiedUser else Icons.Rounded.Error,
                                    null,
                                    tint = if (s.issues.isEmpty()) fsColors.green else fsColors.orange,
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    when {
                                        s.totalFiles == 0 ->
                                            "Source folders are empty — everything was moved. Nothing to clean up."
                                        s.issues.isEmpty() ->
                                            "Every source file has a byte-for-byte identical copy in the destination. It is safe to delete them from the source folders."
                                        else ->
                                            "${s.verifiedFiles} files are safely copied, but ${s.issues.size} are NOT verified. Only verified files can be cleaned up — the rest stay untouched."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = fsColors.label,
                                )
                            }

                            if (s.cleanedCount >= 0) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "${s.cleanedCount} verified files moved to Trash — recoverable until you empty it.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = fsColors.green,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else if (s.verifiedPaths.isNotEmpty()) {
                                Spacer(Modifier.height(14.dp))
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (s.cleaning) fsColors.fill else fsColors.green)
                                        .pressScale { if (!s.cleaning) VerifyRunner.cleanUp() }
                                        .padding(vertical = 13.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (s.cleaning) {
                                        FsSpinner(size = 20.dp, strokeWidth = 2.5.dp)
                                    } else {
                                        Text(
                                            "Move ${s.verifiedPaths.size} Verified Files to Trash",
                                            color = Color.White,
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Cleanup uses the Trash, so it stays recoverable.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = fsColors.secondaryLabel,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }

            // ── Issues ──────────────────────────────────────────────────
            if (!s.isActive && s.issues.isNotEmpty()) {
                item {
                    Text(
                        "Needs attention",
                        style = MaterialTheme.typography.titleLarge,
                        color = fsColors.label,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                item {
                    GroupedCard {
                        val shown = s.issues.take(100)
                        shown.forEachIndexed { index, issue ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.Error, null,
                                    tint = fsColors.red, modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        issue.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = fsColors.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        issue.reason,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = fsColors.red.copy(alpha = 0.85f),
                                        maxLines = 2,
                                    )
                                }
                            }
                            if (index != shown.lastIndex) RowSeparator(startIndent = 42.dp)
                        }
                        if (s.issues.size > shown.size) {
                            Text(
                                "…and ${s.issues.size - shown.size} more",
                                style = MaterialTheme.typography.bodySmall,
                                color = fsColors.secondaryLabel,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor ?: fsColors.label,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = fsColors.secondaryLabel)
    }
}
