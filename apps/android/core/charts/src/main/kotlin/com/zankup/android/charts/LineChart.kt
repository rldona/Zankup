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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
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
    height: Dp = 168.dp,
    /** Kept for callers; every chart is drawn as a solid area now. */
    @Suppress("UNUSED_PARAMETER") filled: Boolean = true,
    /** The series' average, dashed across the chart. */
    average: Float? = series.average,
    /** The axis' unit, written in place of its lowest label ("/km", "bpm", "m"). */
    unitLabel: String? = series.unit
) {
    val colors = ZankupTheme.colors
    val measurer = rememberTextMeasurer()
    val haptics = LocalHapticFeedback.current
    val labelStyle = TextStyle(color = colors.fg, fontSize = 13.sp)
    val valueStyle = TextStyle(color = Color(0xFF16181D), fontSize = 19.sp, fontWeight = FontWeight.Bold)
    val whereStyle = TextStyle(color = Color(0xFF16181D), fontSize = 14.sp)

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
    // Where the plot starts: just past the widest axis value, so the values and the chart sit centred
    // in the card. Worked out while drawing; the finger reads it.
    val plotLeft = remember { mutableFloatStateOf(0f) }
    val bottomBand = 28.dp
    val step = markStep(profile.total, unit)

    Spacer(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = "${series.label}: ${series.summary}" }
            .pointerInput(profile) {
                fun indexAt(px: Float): Int {
                    val left = plotLeft.floatValue
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
                    val showing = cursor.index >= 0
                    // Only a sideways move is the chart's: a vertical one is left to the page's scroll.
                    val drag =
                        awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                    if (drag == null) {
                        // A tap (not a scroll): it shows the bubble there, or hides the one already shown.
                        val tapped = currentEvent.changes.none { it.pressed }
                        if (tapped && !current) cursor.index = if (showing) -1 else start
                        return@awaitEachGesture
                    }
                    follow(drag.position.x)
                    horizontalDrag(drag.id) { change ->
                        follow(change.position.x)
                        change.consume()
                    }
                }
            }.drawWithCache {
                val values = if (morph.value >= 1f) shown else blend(previous, shown, morph.value)
                val axis = axisFor(values, series.fromZero, clip)
                val widest =
                    (axis?.ticks.orEmpty().map { series.format(it.toFloat()) } + listOfNotNull(unitLabel))
                        .maxOfOrNull { measurer.measure(it, labelStyle).size.width } ?: 0
                val left = widest + 10.dp.toPx()
                plotLeft.floatValue = left
                val right = size.width - 1f
                val top = 0f
                val bottom = size.height - bottomBand.toPx()
                val plot = Size(right - left, bottom - top)
                val count = plot.width.toInt().coerceAtLeast(1)
                val cols = columns(profile, values, count)
                val back = series.backdrop?.let { columns(profile, it, count) }
                val backRange = series.backdrop?.let { valueRange(it, false) }

                fun y(v: Float): Float {
                    axis ?: return bottom
                    // Outside the axis (a stop in a pace) the area stays on its edge.
                    val share = ((v - axis.min) / (axis.max - axis.min)).toFloat().coerceIn(0f, 1f)
                    return if (series.inverted) top + share * plot.height else bottom - share * plot.height
                }

                // The elevation behind, on its own scale, nearly to the top (as Strava draws it).
                val backPath =
                    if (back != null && backRange != null) {
                        val (lo, hi) = backRange
                        val span = (hi - lo).takeIf { it > 0f } ?: 1f
                        areaPath(back, left, bottom) { bottom - (it - lo) / span * plot.height * 0.95f }
                    } else {
                        null
                    }
                val area = areaPath(cols, left, bottom) { y(it) }
                val grid = Color(0xFF4A4A48)
                val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
                // The round values, but the lowest is the unit, on the baseline.
                val ticks = axis?.ticks.orEmpty().filter { y(it.toFloat()) < bottom - 2f }
                val tickLabels = ticks.map { measurer.measure(series.format(it.toFloat()), labelStyle) }
                val unitText = unitLabel?.let { measurer.measure(it, labelStyle) }
                val marks =
                    generateSequence(step) { it + step }
                        .takeWhile { it <= profile.total }
                        .map { it to measurer.measure(markLabel(it), labelStyle) }
                        .toList()

                onDrawBehind {
                    backPath?.let { drawPath(it, Color(0xFF3A3A37)) }
                    drawPath(area, color.copy(alpha = 0.8f))
                    // The grid: a line at each round value and each mark, inside a frame.
                    ticks.forEachIndexed { i, tick ->
                        val ty = y(tick.toFloat())
                        drawLine(grid, Offset(left, ty), Offset(right, ty), 1.5f)
                        val label = tickLabels[i]
                        drawText(
                            label,
                            topLeft = Offset(
                                left - label.size.width - 10.dp.toPx(),
                                ty - label.size.height / 2f
                            )
                        )
                    }
                    unitText?.let {
                        drawText(
                            it,
                            topLeft = Offset(left - it.size.width - 10.dp.toPx(), bottom - it.size.height / 2f)
                        )
                    }
                    var free = left
                    marks.forEach { (at, label) ->
                        val mx = left + at / profile.total * plot.width
                        drawLine(grid, Offset(mx, top), Offset(mx, bottom + 4.dp.toPx()), 1.5f)
                        val lx = (mx - label.size.width / 2f).coerceIn(left, size.width - label.size.width)
                        // A mark too close to the one before is left out rather than written over it.
                        if (lx >= free) {
                            drawText(label, topLeft = Offset(lx, bottom + 8.dp.toPx()))
                            free = lx + label.size.width + 8.dp.toPx()
                        }
                    }
                    drawRect(grid, Offset(left, top), plot, style = Stroke(1.5f))
                    average?.takeIf { it.isFinite() && axis != null }?.let { mean ->
                        val ay = y(mean)
                        drawLine(colors.fg, Offset(left, ay), Offset(right, ay), 1.5.dp.toPx(), pathEffect = dash)
                    }

                    // The section: a band, read here so picking it redraws only this canvas.
                    section.range?.let { range ->
                        val from = left + profile.x[range.first] / profile.total * plot.width
                        val to = left + profile.x[range.last] / profile.total * plot.width
                        drawRect(
                            colors.fg.copy(alpha = 0.16f),
                            Offset(from, top),
                            Size((to - from).coerceAtLeast(2f), plot.height)
                        )
                    }

                    // The cursor: a bubble in the series' colour above the chart with the value and where it
                    // is, a white line down from it, and a dot on the value.
                    val index = cursor.index
                    if (index in 0 until profile.size) {
                        val cx = left + profile.x[index] / profile.total * plot.width
                        val v = values.getOrElse(index) { Float.NaN }
                        val text = if (v.isFinite()) series.format(v) + (unitLabel?.let { " $it" } ?: "") else "—"
                        val value = measurer.measure(text, valueStyle)
                        val where = measurer.measure(xLabel(profile.x[index]), whereStyle)
                        val padX = 12.dp.toPx()
                        val padY = 8.dp.toPx()
                        val w = maxOf(value.size.width, where.size.width) + padX * 2
                        val h = value.size.height + where.size.height + padY * 2
                        val bx = (cx - w / 2f).coerceIn(0f, size.width - w)
                        val by = top - h - 6.dp.toPx()
                        drawLine(Color.White, Offset(cx, by + h), Offset(cx, bottom), 2.dp.toPx())
                        drawRoundRect(color, Offset(bx, by), Size(w, h), CornerRadius(10.dp.toPx()))
                        drawText(value, topLeft = Offset(bx + padX, by + padY))
                        drawText(where, topLeft = Offset(bx + padX, by + padY + value.size.height))
                        if (v.isFinite()) {
                            drawCircle(Color.White.copy(alpha = 0.3f), 9.dp.toPx(), Offset(cx, y(v)))
                            drawCircle(Color.White, 5.dp.toPx(), Offset(cx, y(v)))
                        }
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
