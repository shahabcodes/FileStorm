package com.shahabcodes.filestorm.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shahabcodes.filestorm.data.LoaderStyle
import com.shahabcodes.filestorm.data.Prefs
import com.shahabcodes.filestorm.ui.theme.fsColors

/**
 * The app's loading mark. Renders whichever style is chosen in Settings, so
 * every wait in the app picks up the user's preference from one place.
 */
@Composable
fun FsSpinner(size: Dp = 46.dp, strokeWidth: Dp = 4.dp) {
    FsSpinner(style = Prefs.loaderStyle, size = size, strokeWidth = strokeWidth)
}

/** Explicit-style variant, used by the settings picker to preview each option. */
@Composable
fun FsSpinner(style: LoaderStyle, size: Dp = 46.dp, strokeWidth: Dp = 4.dp) {
    when (style) {
        LoaderStyle.ARC -> ArcLoader(size, strokeWidth)
        LoaderStyle.DOTS -> DotsLoader(size)
        LoaderStyle.PULSE -> PulseLoader(size)
        LoaderStyle.BARS -> BarsLoader(size)
        LoaderStyle.ORBIT -> OrbitLoader(size, strokeWidth)
    }
}

@Composable
private fun ArcLoader(size: Dp, strokeWidth: Dp) {
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

/** Three dots taking turns to hop, with a soft shadow under each. */
@Composable
private fun DotsLoader(size: Dp) {
    val transition = rememberInfiniteTransition(label = "dots")
    val accent = fsColors.accent
    val green = fsColors.green
    val dot = size / 5
    Row(
        horizontalArrangement = Arrangement.spacedBy(size / 9),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.size(size),
    ) {
        repeat(3) { index ->
            val hop by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(1080, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "hop$index",
            )
            // Each dot is a third of a cycle behind the one before it.
            val phase = (hop + index / 3f) % 1f
            val lift = kotlin.math.sin(phase * Math.PI).toFloat().coerceAtLeast(0f)
            Box(Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) {
                Canvas(
                    Modifier
                        .size(dot)
                        .offset(y = -(size / 2.6f) * lift),
                ) {
                    drawCircle(
                        color = androidx.compose.ui.graphics.lerp(accent, green, index / 2f)
                            .copy(alpha = 0.55f + 0.45f * lift),
                    )
                }
            }
        }
    }
}

/** Rings expanding outward from the centre and fading as they grow. */
@Composable
private fun PulseLoader(size: Dp) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val accent = fsColors.accent
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        repeat(3) { index ->
            val grow by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(1500, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "ring$index",
            )
            val phase = (grow + index / 3f) % 1f
            Canvas(Modifier.size(size)) {
                drawCircle(
                    color = accent.copy(alpha = (1f - phase) * 0.6f),
                    radius = this.size.minDimension / 2f * phase,
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
        }
        Canvas(Modifier.size(size / 5)) { drawCircle(color = accent) }
    }
}

/** Equaliser bars, the shortest of the styles for tight spaces. */
@Composable
private fun BarsLoader(size: Dp) {
    val transition = rememberInfiniteTransition(label = "bars")
    val accent = fsColors.accent
    val green = fsColors.green
    Row(
        horizontalArrangement = Arrangement.spacedBy(size / 12),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.size(size),
    ) {
        repeat(4) { index ->
            val wave by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(900, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "bar$index",
            )
            val phase = (wave + index / 4f) % 1f
            val height = 0.25f + 0.75f * kotlin.math.sin(phase * Math.PI).toFloat().coerceAtLeast(0f)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(height)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                androidx.compose.ui.graphics.lerp(accent, green, index / 3f),
                                accent.copy(alpha = 0.6f),
                            )
                        )
                    ),
            )
        }
    }
}

/** A single dot running around a faint circular track. */
@Composable
private fun OrbitLoader(size: Dp, strokeWidth: Dp) {
    val transition = rememberInfiniteTransition(label = "orbit")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "spin",
    )
    val track = fsColors.fill
    val accent = fsColors.accent
    val green = fsColors.green
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            drawCircle(
                color = track,
                radius = this.size.minDimension / 2f - strokeWidth.toPx(),
                style = Stroke(width = strokeWidth.toPx()),
            )
        }
        Canvas(Modifier.size(size).rotate(angle)) {
            val radius = this.size.minDimension / 2f - strokeWidth.toPx()
            // Trailing dots fade out behind the leader for a sense of motion.
            repeat(4) { index ->
                val back = index * 0.30f
                drawCircle(
                    color = androidx.compose.ui.graphics.lerp(accent, green, index / 3f)
                        .copy(alpha = 1f - index * 0.24f),
                    radius = strokeWidth.toPx() * (1.55f - index * 0.22f),
                    center = androidx.compose.ui.geometry.Offset(
                        center.x + radius * kotlin.math.cos(-Math.PI / 2 - back).toFloat(),
                        center.y + radius * kotlin.math.sin(-Math.PI / 2 - back).toFloat(),
                    ),
                )
            }
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
