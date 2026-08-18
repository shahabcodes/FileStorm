package com.shahaabapps.filestorm.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.shahaabapps.filestorm.data.TrashManager
import com.shahaabapps.filestorm.ui.theme.fsColors
import com.shahaabapps.filestorm.util.Formatters

/**
 * Shown wherever the app is moving things to, out of, or clean out of the
 * Trash. Rendered once at the root so every screen that trashes something gets
 * the same live view rather than each inventing its own spinner.
 */
@Composable
fun TrashProgressDialog() {
    val progress = TrashManager.progress
    if (!progress.active) return

    val animated by animateFloatAsState(progress.fraction, tween(220), label = "trash")
    val track = fsColors.fill
    val accent = fsColors.accent
    val green = fsColors.green

    Dialog(onDismissRequest = {}) {
        Column(
            Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(fsColors.card)
                .padding(horizontal = 26.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (progress.total > 0) {
                    Canvas(Modifier.size(112.dp)) {
                        val stroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                        drawArc(
                            color = track, startAngle = -90f, sweepAngle = 360f,
                            useCenter = false, style = stroke,
                        )
                        drawArc(
                            brush = Brush.sweepGradient(listOf(accent, green, accent)),
                            startAngle = -90f, sweepAngle = 360f * animated,
                            useCenter = false, style = stroke,
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${(animated * 100).toInt()}%",
                            style = MaterialTheme.typography.headlineMedium,
                            color = fsColors.label,
                        )
                        Text(
                            "${progress.done} of ${progress.total}",
                            style = MaterialTheme.typography.labelSmall,
                            color = fsColors.secondaryLabel,
                        )
                    }
                } else {
                    // No count to work from, so fall back to the chosen loader.
                    FsSpinner(size = 74.dp, strokeWidth = 6.dp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                progress.title,
                style = MaterialTheme.typography.titleMedium,
                color = fsColors.label,
            )
            if (progress.currentName.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    progress.currentName,
                    style = MaterialTheme.typography.bodySmall,
                    color = fsColors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(210.dp),
                )
            }
            if (progress.bytesTotal > 0) {
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .width(210.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(fsColors.fill),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(animated.coerceAtLeast(0.01f))
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Brush.horizontalGradient(listOf(accent, green))),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        Formatters.bytes(progress.bytesDone),
                        style = MaterialTheme.typography.labelSmall,
                        color = fsColors.label,
                    )
                    Text(
                        "of ${Formatters.bytes(progress.bytesTotal)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = fsColors.secondaryLabel,
                    )
                }
            }
            if (progress.failed > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "${progress.failed} failed",
                    style = MaterialTheme.typography.labelSmall,
                    color = fsColors.red,
                )
            }
        }
    }
}
