package com.shahaabapps.filestorm.ui.browser

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Finds what a finger is touching inside a lazy layout.
 *
 * Results are reported as item *keys* rather than positions. Every file row is
 * keyed by its path already, so a sticky month header or a spacer — keyed by
 * something else entirely — simply fails to be a path and drops out. That keeps
 * the timeline, with its headers interleaved between files, working on the same
 * code as a plain list, where every item is a file.
 *
 * Only on-screen items are considered, which is the whole intent: a sweep
 * selects what it passes over.
 */
interface ItemHitTest {
    /** Index of the item under [point] in the layout's own numbering, or -1. */
    fun indexAt(point: Offset): Int

    /** Keys of every item whose index falls within [first]..[last]. */
    fun keysBetween(first: Int, last: Int): List<Any>
}

fun LazyListState.hitTest(): ItemHitTest = object : ItemHitTest {
    override fun indexAt(point: Offset) = layoutInfo.visibleItemsInfo.firstOrNull {
        point.y >= it.offset && point.y < it.offset + it.size
    }?.index ?: -1

    override fun keysBetween(first: Int, last: Int) =
        layoutInfo.visibleItemsInfo.filter { it.index in first..last }.map { it.key }
}

fun LazyGridState.hitTest(): ItemHitTest = object : ItemHitTest {
    override fun indexAt(point: Offset) = layoutInfo.visibleItemsInfo.firstOrNull {
        point.x >= it.offset.x && point.x < it.offset.x + it.size.width &&
            point.y >= it.offset.y && point.y < it.offset.y + it.size.height
    }?.index ?: -1

    override fun keysBetween(first: Int, last: Int) =
        layoutInfo.visibleItemsInfo.filter { it.index in first..last }.map { it.key }
}

fun LazyStaggeredGridState.hitTest(): ItemHitTest = object : ItemHitTest {
    override fun indexAt(point: Offset) = layoutInfo.visibleItemsInfo.firstOrNull {
        point.x >= it.offset.x && point.x < it.offset.x + it.size.width &&
            point.y >= it.offset.y && point.y < it.offset.y + it.size.height
    }?.index ?: -1

    override fun keysBetween(first: Int, last: Int) =
        layoutInfo.visibleItemsInfo.filter { it.index in first..last }.map { it.key }
}

/**
 * Drag sideways across files to select them, the way a photo library does.
 *
 * The hard part is coexisting with the list's own scrolling. Events are read on
 * the [PointerEventPass.Initial] pass, which arrives before the scroll
 * container sees them, but nothing is consumed until the drag has proven itself
 * horizontal — so an ordinary vertical flick still scrolls, untouched. Once the
 * gesture is claimed every move is consumed and the list holds still while the
 * finger sweeps across it.
 *
 * A second finger hands the gesture back, so pinch-to-zoom still wins.
 *
 * @param onStart fires once, at the moment the sweep is claimed.
 * @param onSweep fires with the keys covered so far, repeatedly, as it grows.
 */
fun Modifier.swipeToSelect(
    enabled: Boolean,
    hitTest: ItemHitTest,
    onStart: () -> Unit,
    onSweep: (List<Any>) -> Unit,
): Modifier = if (!enabled) this else pointerInput(hitTest) {
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var claimed = false
        var anchor = -1
        var totalX = 0f
        var totalY = 0f
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            // Pinch beats select; give the gesture up rather than fight it.
            if (event.changes.size > 1) break
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break

            if (!claimed) {
                val moved = change.positionChange()
                totalX += moved.x
                totalY += moved.y
                when {
                    // Decisively sideways: this is a sweep.
                    abs(totalX) > slop && abs(totalX) > abs(totalY) * 1.5f -> {
                        anchor = hitTest.indexAt(down.position)
                        if (anchor < 0) break
                        claimed = true
                        onStart()
                        onSweep(hitTest.keysBetween(anchor, anchor))
                    }
                    // Decisively vertical: it was a scroll all along.
                    abs(totalY) > slop -> break
                }
            }

            if (claimed) {
                change.consume()
                val current = hitTest.indexAt(change.position)
                if (current >= 0) {
                    onSweep(hitTest.keysBetween(min(anchor, current), max(anchor, current)))
                }
            }
        }
    }
}
