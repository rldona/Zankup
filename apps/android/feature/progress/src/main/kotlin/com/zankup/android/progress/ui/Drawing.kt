package com.zankup.android.progress.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt

/**
 * Values that slide from the old ones to the new when they change (a range, a sport): read [at] while
 * drawing, so the slide redraws the canvas and nothing else. A different count is resampled first, so
 * a longer line starts as the shorter one, stretched.
 */
@Stable
internal class Morph(initial: List<Float>) {
    var from by mutableStateOf(initial)
    var to by mutableStateOf(initial)
    val progress = Animatable(1f)

    fun at(i: Int): Float {
        val b = to.getOrElse(i) { 0f }
        val a = from.getOrNull(i) ?: return b
        return a + (b - a) * progress.value
    }

    fun shown(): List<Float> = to.indices.map(::at)
}

@Composable
internal fun rememberMorph(values: List<Float>): Morph {
    val morph = remember { Morph(values) }
    LaunchedEffect(values) {
        if (values != morph.to) {
            val now = morph.shown()
            morph.from = resample(now, values.size)
            morph.to = values
            morph.progress.snapTo(0f)
            morph.progress.animateTo(1f, tween(450))
        }
    }
    return morph
}

/** [values] spread over [count] points by their place along the line. */
internal fun resample(values: List<Float>, count: Int): List<Float> {
    if (values.isEmpty() || count == values.size) return values
    if (count <= 1) return List(count) { values.last() }
    return List(count) { i -> values[(i.toFloat() * (values.size - 1) / (count - 1)).roundToInt()] }
}

/**
 * Touch and sideways drag pick a point: [onPick] gets the finger's x. A vertical swipe is left to the
 * page's scroll; a tap that does not move picks where it landed.
 */
internal fun Modifier.pickInput(key: Any?, onPick: PointerInputScope.(Float) -> Unit): Modifier = pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
        if (drag == null) {
            val up = currentEvent.changes.firstOrNull { it.id == down.id }
            if (up != null && !up.pressed && !up.isConsumed) onPick(down.position.x)
            return@awaitEachGesture
        }
        onPick(drag.position.x)
        horizontalDrag(drag.id) { change ->
            onPick(change.position.x)
            change.consume()
        }
    }
}

/** A polyline through [points]. */
internal fun linePath(points: List<Offset>): Path = Path().apply {
    points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
}

/** The area under [points] down to [base]. */
internal fun areaPath(points: List<Offset>, base: Float): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points.first().x, base)
    points.forEach { lineTo(it.x, it.y) }
    lineTo(points.last().x, base)
    close()
}

/** A smooth line that leaves and arrives level at each point, so it never overshoots one. */
internal fun smoothPath(points: List<Offset>): Path = Path().apply {
    points.forEachIndexed { i, p ->
        if (i == 0) {
            moveTo(p.x, p.y)
        } else {
            val q = points[i - 1]
            val mid = (q.x + p.x) / 2
            cubicTo(mid, q.y, mid, p.y, p.x, p.y)
        }
    }
}
