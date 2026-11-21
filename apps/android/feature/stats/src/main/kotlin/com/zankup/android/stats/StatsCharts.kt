package com.zankup.android.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.niceAxis
import com.zankup.android.domain.tightAxis
import kotlin.math.roundToInt

/** One line of a chart: a value per x (null where there is none). */
@Immutable
class ChartLine(
    val label: String,
    val values: List<Float?>,
    val color: Color,
    val width: Dp = 3.dp,
    val alpha: Float = 1f
)

/** Bars around zero under the lines, green above and red below (form). */
@Immutable
class SignedBars(val values: List<Float>, val positive: Color, val negative: Color, val label: String = "")

/** A stack of one bar: each part's value and colour, bottom first. */
@Immutable
class Stack(val parts: List<Pair<Float, Color>>) {
    val total get() = parts.sumOf { it.first.toDouble() }.toFloat()
}

/** Every chart of the stats is this tall, as the activity's charts. */
internal val CHART_HEIGHT = 168.dp

// As the activity's charts (core/charts LineChart): the same grid, labels, band and bubble.
private val GRID = Color(0xFF4A4A48)
private val BUBBLE_TEXT = Color(0xFF16181D)
private const val GRID_WIDTH = 1.5f
private val LABEL_GAP = 10.dp
private val RIGHT_GAP = 8.dp
private val BOTTOM_BAND = 28.dp
private val MAX_BAR = 14.dp
private const val X_MARKS = 5

/** The slot under a finger at [x] across [count] slots of the plot. */
private fun indexAt(x: Float, left: Float, plot: Float, count: Int): Int {
    if (count <= 1) return 0
    return ((x - left) / (plot / count)).toInt().coerceIn(0, count - 1)
}

/** A line's range without a given one: its values with a tenth of room each side, as the RN app's. */
private fun ownDomain(values: List<Float>): Pair<Float, Float> {
    if (values.isEmpty()) return 0f to 1f
    val lo = values.min()
    val hi = values.max()
    val pad = ((hi - lo) * 0.1f).takeIf { it > 0f } ?: 1f
    return lo - pad to hi + pad
}

/**
 * The activity charts' touch: a sideways drag reads the slot under the finger (a tick at each one), a
 * tap shows the bubble or hides the one shown, and a vertical swipe is left to the page.
 */
private fun Modifier.scrubbing(
    count: Int,
    picked: MutableIntState,
    left: MutableFloatState,
    plot: MutableFloatState,
    haptics: HapticFeedback
): Modifier = pointerInput(count) {
    fun at(px: Float) = indexAt(px, left.floatValue, plot.floatValue, count)
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val before = picked.intValue
        val start = at(down.position.x)
        val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
        if (drag == null) {
            val tapped = currentEvent.changes.none { it.pressed }
            if (tapped) picked.intValue = if (before >= 0) -1 else start
            return@awaitEachGesture
        }
        picked.intValue = at(drag.position.x)
        horizontalDrag(drag.id) { change ->
            val now = at(change.position.x)
            if (now != picked.intValue) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            picked.intValue = now
            change.consume()
        }
    }
}

/**
 * Lines over x labels (days, durations), each value at the centre of its slot, drawn as the activity's
 * charts: a grid inside a frame, round values on the left, the dates underneath and a bubble in the
 * line's colour at the finger. With [bars] (form) the bars take the left axis and the lines a right one,
 * four steps each so both share the grid; alone, the lines get the tightest round axis. A null [height]
 * fills what the parent gives.
 */
@Composable
internal fun LinesChart(
    lines: List<ChartLine>,
    xLabels: List<String>,
    format: (Float) -> String,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp? = CHART_HEIGHT,
    domain: ClosedFloatingPointRange<Float>? = null,
    bars: SignedBars? = null,
    axis: Boolean = true
) {
    val colors = ZankupTheme.colors
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val labelStyle = TextStyle(color = colors.fg, fontSize = 13.sp)
    val count = maxOf(xLabels.size, lines.maxOfOrNull { it.values.size } ?: 0, bars?.values?.size ?: 0)
    val picked = remember(count) { mutableIntStateOf(-1) }
    val plotLeft = remember { mutableFloatStateOf(0f) }
    val plotWidth = remember { mutableFloatStateOf(1f) }
    val (lo, hi) = domain?.let { it.start to it.endInclusive } ?: ownDomain(lines.flatMap { it.values.filterNotNull() })
    val top = if (hi > lo) hi.toDouble() else lo + 1.0
    val scale = if (bars != null) niceAxis(lo.toDouble(), top) else tightAxis(lo.toDouble(), top)
    val barScale = bars?.let { b ->
        val low = minOf(0f, b.values.minOrNull() ?: 0f).toDouble()
        val high = maxOf(0f, b.values.maxOrNull() ?: 0f).toDouble()
        niceAxis(low, if (high > low) high else low + 1.0)
    }
    val leftScale = barScale ?: scale
    val right = bars != null && axis
    val leftTicks = leftScale.ticks.map { it.toFloat() }
    val rightTicks = scale.ticks.map { it.toFloat() }
    // Room for the widest label, so "6000 km" sits outside the plot instead of over it.
    val leftAxisWidth = with(density) {
        if (!axis) 0f else leftTicks.maxOf { measurer.measure(format(it), labelStyle).size.width } + LABEL_GAP.toPx()
    }
    val rightAxisWidth = with(density) {
        if (!right) 0f else rightTicks.maxOf { measurer.measure(format(it), labelStyle).size.width } + LABEL_GAP.toPx()
    }
    val sized = if (height != null) modifier.fillMaxWidth().height(height) else modifier.fillMaxSize()
    Canvas(
        sized
            .semantics { contentDescription = description }
            .scrubbing(count, picked, plotLeft, plotWidth, haptics)
    ) {
        val left = leftAxisWidth
        val plot = size.width - left - (if (right) rightAxisWidth else RIGHT_GAP.toPx())
        plotLeft.floatValue = left
        plotWidth.floatValue = plot
        val topY = 0f
        val bottom = size.height - if (xLabels.isEmpty()) 0f else BOTTOM_BAND.toPx()
        val slot = if (count > 0) plot / count else plot
        fun x(i: Int) = left + (i + 0.5f) * slot
        fun yOf(min: Double, max: Double): (Float) -> Float {
            val span = (max - min).toFloat().takeIf { it > 0f } ?: 1f
            return { v -> bottom - ((v - min.toFloat()) / span).coerceIn(0f, 1f) * (bottom - topY) }
        }
        val y = yOf(scale.min, scale.max)
        val leftY = yOf(leftScale.min, leftScale.max)
        drawGrid(leftTicks, leftY, left, plot)
        if (axis) {
            drawAxisLabels(measurer, labelStyle, leftTicks, format, leftY, left, start = true)
            if (right) drawAxisLabels(measurer, labelStyle, rightTicks, format, y, left + plot)
        }
        drawXMarks(measurer, labelStyle, xLabels, ::x, topY, bottom)
        // The form's bars, green above zero and red below.
        if (bars != null && barScale != null) {
            if (barScale.min < 0 && barScale.max > 0) {
                drawLine(colors.muted, Offset(left, leftY(0f)), Offset(left + plot, leftY(0f)), 1.dp.toPx())
            }
            drawSignedBars(bars, slot, left, leftY, MAX_BAR.toPx())
        }
        lines.forEach { line ->
            val stroke = line.width.toPx()
            var path: Path? = null
            var points = 0
            var lastX = 0f
            var lastY = 0f
            fun flush() {
                val p = path ?: return
                if (points > 1) {
                    p.lineTo(lastX, lastY)
                    drawPath(
                        p,
                        line.color.copy(alpha = line.alpha),
                        style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                } else if (points == 1) {
                    drawCircle(line.color.copy(alpha = line.alpha), stroke / 2 + 1f, Offset(lastX, lastY))
                }
                path = null
                points = 0
            }
            line.values.forEachIndexed { i, v ->
                if (v == null) {
                    flush()
                    return@forEachIndexed
                }
                val px = x(i)
                val py = y(v)
                val p = path ?: Path().also { path = it }
                // As Health's charts: smooth through the midpoints, each value a control point.
                if (points == 0) p.moveTo(px, py) else p.quadraticTo(lastX, lastY, (lastX + px) / 2, (lastY + py) / 2)
                lastX = px
                lastY = py
                points++
            }
            flush()
        }
        drawFrame(left, topY, plot, bottom)
        val at = picked.intValue
        if (at in 0 until count) {
            val shown = lines.mapNotNull { l -> l.values.getOrNull(at)?.let { l to it } }
            val text = buildList {
                shown.forEach { (l, v) -> add(if (shown.size > 1) "${l.label} ${format(v)}" else format(v)) }
                bars?.values?.getOrNull(at)?.let { add(listOf(bars.label, format(it)).filter(String::isNotEmpty).joinToString(" ")) }
            }.joinToString("  ·  ")
            val tint = shown.firstOrNull()?.first?.color ?: colors.accent
            val many = shown.size + (if (bars?.values?.getOrNull(at) != null) 1 else 0) > 1
            drawBubble(measurer, text, xLabels.getOrNull(at), x(at), topY, bottom, tint, compact = many)
            shown.forEach { (_, v) -> drawDot(Offset(x(at), y(v))) }
        }
    }
}

/**
 * Stacked bars (volume by sport): one stack per x label, at most 14 dp wide in the middle of its slot,
 * on the tightest round axis, framed and read as the activity's charts.
 */
@Composable
internal fun StackedBars(
    stacks: List<Stack>,
    xLabels: List<String>,
    format: (Float) -> String,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = CHART_HEIGHT
) {
    val colors = ZankupTheme.colors
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val labelStyle = TextStyle(color = colors.fg, fontSize = 13.sp)
    val count = stacks.size
    val picked = remember(count) { mutableIntStateOf(-1) }
    val plotLeft = remember { mutableFloatStateOf(0f) }
    val plotWidth = remember { mutableFloatStateOf(1f) }
    val top = stacks.maxOfOrNull { it.total }?.takeIf { it > 0f } ?: 1f
    val scale = tightAxis(0.0, top.toDouble())
    val ticks = scale.ticks.map { it.toFloat() }
    val axisWidth = with(density) {
        (ticks.maxOfOrNull { measurer.measure(format(it), labelStyle).size.width } ?: 0) + LABEL_GAP.toPx()
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
            .scrubbing(count, picked, plotLeft, plotWidth, haptics)
    ) {
        if (count == 0) return@Canvas
        val left = axisWidth
        val plot = size.width - left - RIGHT_GAP.toPx()
        plotLeft.floatValue = left
        plotWidth.floatValue = plot
        val topY = 0f
        val bottom = size.height - BOTTOM_BAND.toPx()
        val slot = plot / count
        val bar = (slot * 0.7f).coerceIn(1f, MAX_BAR.toPx())
        val max = scale.max.toFloat()
        val y: (Float) -> Float = { v -> bottom - (v / max).coerceIn(0f, 1f) * (bottom - topY) }
        fun x(i: Int) = left + (i + 0.5f) * slot
        drawGrid(ticks, y, left, plot)
        drawAxisLabels(measurer, labelStyle, ticks, format, y, left, start = true)
        drawXMarks(measurer, labelStyle, xLabels, ::x, topY, bottom)
        val at = picked.intValue
        // As the RN app's: every bar a touch lighter while one is read.
        val alpha = if (at < 0) 1f else 0.9f
        stacks.forEachIndexed { i, stack ->
            var base = 0f
            val bx = left + i * slot + (slot - bar) / 2
            stack.parts.forEach { (v, c) ->
                if (v <= 0f) return@forEach
                val y0 = y(base)
                val y1 = y(base + v)
                drawRect(c.copy(alpha = alpha), Offset(bx, y1), Size(bar, maxOf(1f, y0 - y1)))
                base += v
            }
        }
        drawFrame(left, topY, plot, bottom)
        if (at in 0 until count) {
            val tint = stacks[at].parts.lastOrNull { it.first > 0f }?.second ?: colors.accent
            drawBubble(measurer, format(stacks[at].total), xLabels.getOrNull(at), x(at), topY, bottom, tint)
        }
    }
}

/**
 * The colours of a chart's series, centred under it: a dot and the name, as the RN app's charts.
 * [bars] draws a short bar instead (the summary's year-before and now bars).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Legend(items: List<Pair<String, Color>>, modifier: Modifier = Modifier, bars: Boolean = false) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (bars) 16.dp else 12.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { (label, colour) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (bars) 6.dp else 4.dp)
            ) {
                val mark = if (bars) Modifier.size(12.dp, 7.dp) else Modifier.size(8.dp)
                Box(mark.clip(RoundedCornerShape(4.dp)).background(colour))
                Text(label, fontSize = 12.sp, color = ZankupTheme.colors.muted)
            }
        }
    }
}

/** The grid: a line at each round value. */
private fun DrawScope.drawGrid(ticks: List<Float>, y: (Float) -> Float, left: Float, plot: Float) {
    ticks.forEach { tick ->
        val ty = y(tick)
        drawLine(GRID, Offset(left, ty), Offset(left + plot, ty), GRID_WIDTH)
    }
}

/** The plot's frame. */
private fun DrawScope.drawFrame(left: Float, top: Float, plot: Float, bottom: Float) {
    drawRect(GRID, Offset(left, top), Size(plot, (bottom - top).coerceAtLeast(1f)), style = Stroke(GRID_WIDTH))
}

/** An axis' labels: right-aligned before [x] ([start]) or left-aligned after it. */
private fun DrawScope.drawAxisLabels(
    measurer: TextMeasurer,
    style: TextStyle,
    ticks: List<Float>,
    format: (Float) -> String,
    y: (Float) -> Float,
    x: Float,
    start: Boolean = false
) {
    ticks.forEach { tick ->
        val label = measurer.measure(format(tick), style)
        val top = (y(tick) - label.size.height / 2f).coerceIn(0f, size.height - label.size.height)
        val lx = if (start) (x - label.size.width - LABEL_GAP.toPx()).coerceAtLeast(0f) else x + LABEL_GAP.toPx()
        drawText(label, topLeft = Offset(lx, top))
    }
}

/** About five marks under the chart, spread evenly, always the first and the last, each with its line. */
private fun DrawScope.drawXMarks(
    measurer: TextMeasurer,
    style: TextStyle,
    labels: List<String>,
    x: (Int) -> Float,
    top: Float,
    bottom: Float
) {
    if (labels.isEmpty()) return
    val marks = if (labels.size <= X_MARKS) {
        labels.indices.toList()
    } else {
        val every = (labels.size - 1) / (X_MARKS - 1f)
        (0 until X_MARKS).map { (it * every).roundToInt() }
    }
    var free = 0f
    marks.forEach { i ->
        val mx = x(i)
        drawLine(GRID, Offset(mx, top), Offset(mx, bottom + 4.dp.toPx()), GRID_WIDTH)
        val label = measurer.measure(labels[i], style)
        val lx = (mx - label.size.width / 2f).coerceIn(0f, (size.width - label.size.width).coerceAtLeast(0f))
        // A mark too close to the one before is left out rather than written over it.
        if (lx >= free) {
            drawText(label, topLeft = Offset(lx, bottom + 8.dp.toPx()))
            free = lx + label.size.width + 8.dp.toPx()
        }
    }
}

/** Form's bars from zero: green above, red below, at most [maxBar] wide in each slot. */
private fun DrawScope.drawSignedBars(bars: SignedBars, slot: Float, left: Float, y: (Float) -> Float, maxBar: Float) {
    val bar = (slot * 0.7f).coerceIn(1f, maxBar)
    val zero = y(0f)
    bars.values.forEachIndexed { i, v ->
        if (v == 0f) return@forEachIndexed
        val top = y(v)
        drawRect(
            if (v >= 0) bars.positive else bars.negative,
            Offset(left + i * slot + (slot - bar) / 2, minOf(top, zero)),
            Size(bar, maxOf(1f, kotlin.math.abs(zero - top)))
        )
    }
}

/**
 * The reading at the finger, as the activity's charts: a bubble in the series' colour above the chart
 * with the value in bold and where it is below, and a white line down from it.
 */
private fun DrawScope.drawBubble(
    measurer: TextMeasurer,
    text: String,
    where: String?,
    x: Float,
    top: Float,
    bottom: Float,
    tint: Color,
    /** Several values (form's three): a smaller figure, so the bubble stays the size of one. */
    compact: Boolean = false
) {
    if (text.isEmpty()) return
    val limit = Constraints(maxWidth = (size.width - 24.dp.toPx()).toInt().coerceAtLeast(1))
    val value = measurer.measure(
        text,
        TextStyle(color = BUBBLE_TEXT, fontSize = if (compact) 16.sp else 19.sp, fontWeight = FontWeight.Bold),
        constraints = limit
    )
    val place = where?.let { measurer.measure(it, TextStyle(color = BUBBLE_TEXT, fontSize = 14.sp), constraints = limit) }
    val padX = 12.dp.toPx()
    val padY = 8.dp.toPx()
    val w = maxOf(value.size.width, place?.size?.width ?: 0) + padX * 2
    val h = value.size.height + (place?.size?.height ?: 0) + padY * 2
    val bx = (x - w / 2f).coerceIn(0f, (size.width - w).coerceAtLeast(0f))
    val by = top - h - 6.dp.toPx()
    drawLine(Color.White, Offset(x, by + h), Offset(x, bottom), 2.dp.toPx())
    drawRoundRect(tint, Offset(bx, by), Size(w, h), CornerRadius(10.dp.toPx()))
    drawText(value, topLeft = Offset(bx + padX, by + padY))
    place?.let { drawText(it, topLeft = Offset(bx + padX, by + padY + value.size.height)) }
}

/** A value under the finger: a white dot with a halo. */
private fun DrawScope.drawDot(at: Offset) {
    drawCircle(Color.White.copy(alpha = 0.3f), 9.dp.toPx(), at)
    drawCircle(Color.White, 5.dp.toPx(), at)
}
