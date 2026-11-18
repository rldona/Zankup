package com.zankup.android.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zankup.android.designsystem.theme.ZankupTheme
import kotlin.math.floor

/**
 * One series of an activity along its distance (or time), drawn as Garmin and Strava do: a filled area,
 * round values on the left, a grid and the distance underneath, the elevation in grey behind it.
 *
 * Dragging a finger sideways moves the [cursor] (shared with the other charts and the map) and clicks at
 * each km or mi; a vertical swipe still scrolls the page. With [selecting], the drag picks the [section]
 * instead. The cursor and the section are read only while drawing, so following the finger redraws this
 * canvas and nothing else, at the screen's rate.
 *
 * When [series]' values change (a filter), the line slides from the old values to the new ones.
 */
@Composable
fun LineChart(
    profile: Profile,
    series: Series,
    cursor: ChartCursor,
    section: ChartSection,
    modifier: Modifier = Modifier,
    color: Color = ZankupTheme.colors.accent,
    selecting: Boolean = false,
    /** The x axis' unit (1000 m, 1609.344 m or 60 s) and how a position along it is written. */
    unit: Float = 1000f,
    xLabel: (Float) -> String = { "%.0f".format(it / unit) },
    /** How a round mark under the axis is written ("2 km"): shorter than [xLabel], which the cursor shows. */
    markLabel: (Float) -> String = xLabel,
    /** Clip the axis to the 2nd–98th percentile, so a stop does not flatten a pace. */
    clip: Boolean = false,
    height: Dp = 168.dp
) {
    val colors = ZankupTheme.colors
    val measurer = rememberTextMeasurer()
    val haptics = LocalHapticFeedback.current
    val labelStyle = TextStyle(color = colors.muted, fontSize = 11.sp)
    val readoutStyle = TextStyle(color = colors.bg, fontSize = 12.sp)

    // The slide between old and new values.
    var shown by remember { mutableStateOf(series.values) }
    var previous by remember { mutableStateOf(series.values) }
    val morph = remember { Animatable(1f) }
    LaunchedEffect(series.values) {
        if (series.values !== shown) {
            previous = if (morph.value < 1f) blend(previous, shown, morph.value) else shown
            shown = series.values
            morph.snapTo(0f)
            morph.animateTo(1f, tween(450))
        }
    }

    val current by rememberUpdatedState(selecting)
    val axisWidth = 40.dp
    val bottomBand = 20.dp
    val step = markStep(profile.total, unit)

    Spacer(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = "${series.label}: ${series.summary}" }
            .pointerInput(profile) {
                val left = axisWidth.toPx()
                fun indexAt(px: Float): Int {
                    val share = ((px - left) / (size.width - left)).coerceIn(0f, 1f)
                    return profile.indexAt(share * profile.total)
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var mark = -1
                    val start = indexAt(down.position.x)
                    fun follow(px: Float) {
                        val index = indexAt(px)
                        if (current) {
                            section.range = minOf(start, index)..maxOf(start, index)
                        }
                        cursor.index = index
                        val now = floor(profile.x.getOrElse(index) { 0f } / step).toInt()
                        if (mark != -1 &&
                            now != mark
                        ) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                        }
                        mark = now
                    }
                    // Only a sideways move is the chart's: a vertical one is left to the page's scroll.
                    val drag =
                        awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                            ?: return@awaitEachGesture run { if (!current) cursor.index = start }
                    follow(drag.position.x)
                    horizontalDrag(drag.id) { change ->
                        follow(change.position.x)
                        change.consume()
                    }
                }
            }.drawWithCache {
                val left = axisWidth.toPx()
                val bottom = size.height - bottomBand.toPx()
                val plot = Size(size.width - left, bottom)
                val values = if (morph.value >= 1f) shown else blend(previous, shown, morph.value)
                val axis = axisFor(values, series.fromZero, clip)
                val count = plot.width.toInt().coerceAtLeast(1)
                val cols = columns(profile, values, count)
                val back = series.backdrop?.let { columns(profile, it, count) }
                val backRange = series.backdrop?.let { valueRange(it, false) }

                fun y(v: Float): Float {
                    axis ?: return bottom
                    // Outside the axis (a stop in a pace) the line stays on its edge.
                    val share = ((v - axis.min) / (axis.max - axis.min)).toFloat().coerceIn(0f, 1f)
                    return if (series.inverted) share * bottom else bottom - share * bottom
                }

                // The elevation behind, on its own scale, filling the lower part.
                val backPath =
                    if (back != null && backRange != null) {
                        val (lo, hi) = backRange
                        val span = (hi - lo).takeIf { it > 0f } ?: 1f
                        areaPath(back, left, bottom) { bottom - (it - lo) / span * bottom * 0.85f }
                    } else {
                        null
                    }
                val line = linePath(cols, left) { y(it) }
                val area = areaPath(cols, left, bottom) { y(it) }
                val fill = Brush.verticalGradient(
                    listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.04f)),
                    0f,
                    bottom
                )
                val ticks = axis?.ticks.orEmpty()
                val tickLabels = ticks.map { measurer.measure(series.format(it.toFloat()), labelStyle) }
                val marks =
                    generateSequence(0f) { it + step }
                        .takeWhile { it <= profile.total }
                        .map { it to measurer.measure(markLabel(it), labelStyle) }
                        .toList()

                onDrawBehind {
                    backPath?.let { drawPath(it, colors.fg.copy(alpha = 0.08f)) }
                    ticks.forEachIndexed { i, tick ->
                        val ty = y(tick.toFloat())
                        drawLine(colors.line, Offset(left, ty), Offset(size.width, ty), 1f)
                        val label = tickLabels[i]
                        // Inside the chart: the top label must not climb onto what sits above it.
                        val top = (ty - label.size.height / 2f).coerceIn(0f, bottom - label.size.height)
                        drawText(label, topLeft = Offset(left - label.size.width - 6f, top))
                    }
                    var free = left
                    marks.forEach { (at, label) ->
                        val mx = left + at / profile.total * plot.width
                        val lx = (mx - label.size.width / 2f).coerceIn(left, size.width - label.size.width)
                        // A mark too close to the one before is left out rather than written over it.
                        if (lx >= free) {
                            drawText(label, topLeft = Offset(lx, bottom + 4f))
                            free = lx + label.size.width + 8.dp.toPx()
                        }
                    }
                    drawPath(area, fill)
                    drawPath(
                        line,
                        color,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    // The section: a band, read here so picking it redraws only this canvas.
                    section.range?.let { range ->
                        val from = left + profile.x[range.first] / profile.total * plot.width
                        val to = left + profile.x[range.last] / profile.total * plot.width
                        drawRect(
                            colors.accent.copy(alpha = 0.16f),
                            Offset(from, 0f),
                            Size((to - from).coerceAtLeast(2f), bottom)
                        )
                    }

                    // The cursor: a line, a dot on the series and the value with where it is.
                    val index = cursor.index
                    if (index in 0 until profile.size) {
                        val cx = left + profile.x[index] / profile.total * plot.width
                        drawLine(
                            colors.fg.copy(alpha = 0.6f),
                            Offset(cx, 0f),
                            Offset(cx, bottom),
                            1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                        )
                        val v = values.getOrElse(index) { Float.NaN }
                        val text = if (v.isFinite()) {
                            "${series.format(
                                v
                            )} · ${xLabel(profile.x[index])}"
                        } else {
                            xLabel(profile.x[index])
                        }
                        if (v.isFinite()) {
                            drawCircle(colors.surface, 6.dp.toPx(), Offset(cx, y(v)))
                            drawCircle(color, 4.dp.toPx(), Offset(cx, y(v)))
                        }
                        val label = measurer.measure(text, readoutStyle)
                        val pad = 6.dp.toPx()
                        val w = label.size.width + pad * 2
                        val bx = (cx - w / 2f).coerceIn(left, size.width - w)
                        drawRoundRect(
                            colors.fg,
                            Offset(bx, 0f),
                            Size(w, label.size.height + pad),
                            CornerRadius(pad, pad)
                        )
                        drawText(label, topLeft = Offset(bx + pad, pad / 2f))
                    }
                }
            }
    )
}

/** [from] blended towards [to] by [t]; when their sizes differ there is nothing to slide, so [to]. */
internal fun blend(from: FloatArray, to: FloatArray, t: Float): FloatArray {
    if (from.size != to.size) return to
    return FloatArray(to.size) { i ->
        val a = from[i]
        val b = to[i]
        when {
            a.isFinite() && b.isFinite() -> a + (b - a) * t
            else -> b
        }
    }
}

private fun linePath(cols: FloatArray, left: Float, y: (Float) -> Float): Path {
    val path = Path()
    var open = false
    cols.forEachIndexed { c, v ->
        if (v.isFinite()) {
            if (open) path.lineTo(left + c, y(v)) else path.moveTo(left + c, y(v))
            open = true
        } else {
            open = false
        }
    }
    return path
}

private fun areaPath(cols: FloatArray, left: Float, bottom: Float, y: (Float) -> Float): Path {
    val path = Path()
    var start = -1
    fun close(end: Int) {
        path.lineTo(left + end, bottom)
        path.lineTo(left + start, bottom)
        path.close()
    }
    cols.forEachIndexed { c, v ->
        if (v.isFinite()) {
            if (start == -1) {
                start = c
                path.moveTo(left + c, bottom)
            }
            path.lineTo(left + c, y(v))
        } else if (start != -1) {
            close(c - 1)
            start = -1
        }
    }
    if (start != -1) close(cols.size - 1)
    return path
}
