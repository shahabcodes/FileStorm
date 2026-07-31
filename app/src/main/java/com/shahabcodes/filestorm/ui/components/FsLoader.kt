package com.shahabcodes.filestorm.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shahabcodes.filestorm.ui.theme.fsColors

/**
 * The app's loading mark: a sweeping accent arc over a soft track, with a
 * breathing inner dot. Used wherever the app has to wait on the filesystem.
 */
@Composable
fun FsSpinner(size: Dp = 46.dp, strokeWidth: Dp = 4.dp) {
    val transition = rememberInfiniteTransition(label = "loader")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1150, easing = LinearEasing)),
        label = "sweep",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(760, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    val track = fsColors.fill
    val accent = fsColors.accent
    val green = fsColors.green

    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke,
            )
        }
        Canvas(Modifier.size(size).rotate(angle)) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            drawArc(
                brush = Brush.sweepGradient(
                    0f to accent.copy(alpha = 0f),
                    0.55f to accent,
                    0.85f to green,
                    1f to accent.copy(alpha = 0f),
                ),
                startAngle = 0f,
                sweepAngle = 290f,
                useCenter = false,
                style = stroke,
            )
        }
        Canvas(Modifier.size(size / 5).scale(pulse)) {
            drawCircle(color = accent.copy(alpha = 0.85f))
        }
    }
}

/** Full-screen loading state with a title and an optional live detail line. */
@Composable
fun FsLoadingState(
    title: String,
    detail: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        FsSpinner()
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = fsColors.label,
            textAlign = TextAlign.Center,
        )
        if (detail != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = fsColors.secondaryLabel,
                textAlign = TextAlign.Center,
            )
        }
    }
}
