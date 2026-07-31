package com.shahabcodes.filestorm.ui.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.shahabcodes.filestorm.ui.theme.fsColors
import kotlinx.coroutines.launch

/** Everything the scrubber needs, whatever kind of lazy list is underneath. */
class ScrollHandle(
    val itemCount: Int,
    val firstVisible: Int,
    val scrolling: Boolean,
    val jumpTo: suspend (Int) -> Unit,
)

/**
 * Draggable scrollbar for long folders. It stays out of the way until the list
 * moves, then fades in; dragging it jumps straight through the folder while a
 * bubble names wherever you have landed.
 */
@Composable
fun BoxScope.FastScroller(
    handle: ScrollHandle,
    labelFor: (Int) -> String,
    minItems: Int = 40,
) {
    if (handle.itemCount < minItems) return

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var dragging by remember { mutableStateOf(false) }
    var trackHeightPx by remember { mutableFloatStateOf(0f) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val thumbHeight = 46.dp
    val thumbHeightPx = with(density) { thumbHeight.toPx() }
    val travel = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)

    val listFraction = if (handle.itemCount <= 1) 0f
    else handle.firstVisible.toFloat() / (handle.itemCount - 1).toFloat()
    val fraction = (if (dragging) dragFraction else listFraction).coerceIn(0f, 1f)
    val activeIndex = ((handle.itemCount - 1) * fraction).toInt().coerceIn(0, handle.itemCount - 1)

    // Idle for a moment after scrolling stops, then fade away again.
    var recentlyScrolled by remember { mutableStateOf(false) }
    LaunchedEffect(handle.scrolling, dragging) {
        if (handle.scrolling || dragging) {
            recentlyScrolled = true
        } else {
            kotlinx.coroutines.delay(1200)
            recentlyScrolled = false
        }
    }

    AnimatedVisibility(
        visible = recentlyScrolled || dragging,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.CenterEnd),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(38.dp)
                .onSizeChanged { trackHeightPx = it.height.toFloat() }
                .pointerInput(handle.itemCount, trackHeightPx) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            dragFraction = ((offset.y - thumbHeightPx / 2f) / travel).coerceIn(0f, 1f)
                            scope.launch {
                                handle.jumpTo(
                                    ((handle.itemCount - 1) * dragFraction).toInt()
                                        .coerceIn(0, handle.itemCount - 1)
                                )
                            }
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                        onVerticalDrag = { change, delta ->
                            change.consume()
                            dragFraction = (dragFraction + delta / travel).coerceIn(0f, 1f)
                            scope.launch {
                                handle.jumpTo(
                                    ((handle.itemCount - 1) * dragFraction).toInt()
                                        .coerceIn(0, handle.itemCount - 1)
                                )
                            }
                        },
                    )
                },
        ) {
            val thumbOffset = with(density) { (travel * fraction).toDp() }

            // Bubble naming the position, shown while dragging.
            AnimatedVisibility(
                visible = dragging,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = thumbOffset)
                    .padding(end = 30.dp),
            ) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp))
                        .background(fsColors.accent)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        labelFor(activeIndex),
                        style = MaterialTheme.typography.titleMedium,
                        color = androidx.compose.ui.graphics.Color.White,
                        maxLines = 1,
                    )
                }
            }

            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = thumbOffset)
                    .padding(end = 5.dp)
                    .width(if (dragging) 8.dp else 5.dp)
                    .height(thumbHeight)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (dragging) fsColors.accent
                        else fsColors.secondaryLabel.copy(alpha = 0.55f)
                    ),
            )
        }
    }
}
