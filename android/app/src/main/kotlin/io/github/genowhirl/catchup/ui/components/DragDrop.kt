package io.github.genowhirl.catchup.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Long-press-and-drag between parts of one screen. Sources say what they carry, targets say
 * where they are (in root coordinates); the screen draws the dragged item at [pointer].
 */
class DragDropState<T> internal constructor(private val onDrop: () -> ((T, Any?) -> Unit)) {
    var item by mutableStateOf<T?>(null)
        private set
    var pointer by mutableStateOf(Offset.Zero)
        private set
    internal val targets = mutableStateMapOf<Any, Rect>()

    /** The target under the finger, if any. */
    val hovered: Any?
        get() = if (item == null) null else targets.entries.firstOrNull { it.value.contains(pointer) }?.key

    internal fun start(value: T, at: Offset) {
        item = value
        pointer = at
    }

    internal fun moveTo(at: Offset) {
        pointer = at
    }

    internal fun drop() {
        val value = item ?: return
        val target = hovered
        item = null
        onDrop()(value, target)
    }

    internal fun cancel() {
        item = null
    }
}

@Composable
fun <T> rememberDragDropState(onDrop: (item: T, target: Any?) -> Unit): DragDropState<T> {
    val latest by rememberUpdatedState(onDrop)
    return remember { DragDropState<T> { latest } }
}

/** Marks a place things can be dropped on. */
fun Modifier.dropTarget(state: DragDropState<*>, key: Any): Modifier = composed {
    DisposableEffect(state, key) { onDispose { state.targets.remove(key) } }
    onGloballyPositioned { state.targets[key] = it.boundsInRoot() }
}

/** Makes this draggable after a long press, carrying `item`. */
fun <T> Modifier.dragSource(state: DragDropState<T>, item: T): Modifier = composed {
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(item)
    var origin by remember { mutableStateOf(Offset.Zero) }
    onGloballyPositioned { origin = it.positionInRoot() }
        .pointerInput(state) {
            detectDragGesturesAfterLongPress(
                onDragStart = { at ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    state.start(current, origin + at)
                },
                // Positions, not deltas: the source may scroll while the finger stays put.
                onDrag = { change, _ ->
                    change.consume()
                    state.moveTo(origin + change.position)
                },
                onDragEnd = { state.drop() },
                onDragCancel = { state.cancel() },
            )
        }
}
