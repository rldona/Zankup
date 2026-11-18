package com.zankup.android.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
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
class SignedBars(val values: List<Float>, val positive: Color, val negative: Color)

/** A stack of one bar: each part's value and colour, bottom first. */
@Immutable
class Stack(val parts: List<Pair<Float, Color>>) {
    val total get() = parts.sumOf { it.first.toDouble() }.toFloat()
}

private val AXIS_WIDTH = 34.dp
private val RIGHT_GAP = 8.dp
private val TOP_GAP = 8.dp
private val BOTTOM_BAND = 20.dp
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
 * Lines over x labels (days, durations), each value at the centre of its slot, as the React Native
 * app's Chart. With [bars] (form) the bars take the left axis and the lines a right one, four steps
 * each so both share the grid; alone, the lines get the tightest round axis. A finger sideways reads
 * every line at that x; a vertical swipe still scrolls. A null [height] fills what the parent gives.
 */
@Composable
internal fun LinesChart(
    lines: List<ChartLine>,
    xLabels: List<String>,
    format: (Float) -> String,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp? = 170.dp,
    domain: ClosedFloatingPointRange<Float>? = null,
    bars: SignedBars? = null,
    axis: Boolean = true
) {
    val colors = ZankupTheme.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = colors.muted, fontSize = 10.sp)
    val readout = TextStyle(color = colors.fg, fontSize = 12.sp)
    val scrub = remember { mutableIntStateOf(-1) }
    val count = maxOf(xLabels.size, lines.maxOfOrNull { it.values.size } ?: 0, bars?.values?.size ?: 0)
    val (lo, hi) = domain?.let { it.start to it.endInclusive } ?: ownDomain(lines.flatMap { it.values.filterNotNull() })
    val top = if (hi > lo) hi.toDouble() else lo + 1.0
    val scale = if (bars != null) niceAxis(lo.toDouble(), top) else tightAxis(lo.toDouble(), top)
    val barScale = bars?.let { b ->
        val low = minOf(0f, b.values.minOrNull() ?: 0f).toDouble()
        val high = maxOf(0f, b.values.maxOrNull() ?: 0f).toDouble()
        niceAxis(low, if (high > low) high else low + 1.0)
    }
    val sized = if (height != null) modifier.fillMaxWidth().height(height) else modifier.fillMaxSize()
    Canvas(
        sized
            .semantics { contentDescription = description }
            .pointerInput(count, axis, bars != null) {
                val left = if (axis) AXIS_WIDTH.toPx() else 0f
                val plot = size.width - left - (if (bars != null) AXIS_WIDTH else RIGHT_GAP).toPx()
                detectTapGestures(onPress = {
                    scrub.intValue = indexAt(it.x, left, plot, count)
                    tryAwaitRelease()
                    scrub.intValue = -1
                })
            }.pointerInput(count, axis, bars != null) {
                val left = if (axis) AXIS_WIDTH.toPx() else 0f
                val plot = size.width - left - (if (bars != null) AXIS_WIDTH else RIGHT_GAP).toPx()
                detectHorizontalDragGestures(
                    onDragStart = { scrub.intValue = indexAt(it.x, left, plot, count) },
                    onDragEnd = { scrub.intValue = -1 },
                    onDragCancel = { scrub.intValue = -1 }
                ) { change, _ -> scrub.intValue = indexAt(change.position.x, left, plot, count) }
            }
    ) {
        val left = if (axis) AXIS_WIDTH.toPx() else 0f
        val right = bars != null && axis
        val plot = size.width - left - (if (right) AXIS_WIDTH else RIGHT_GAP).toPx()
        val topY = TOP_GAP.toPx()
        val bottom = size.height - if (xLabels.isEmpty()) 0f else BOTTOM_BAND.toPx()
        val slot = if (count > 0) plot / count else plot
        fun x(i: Int) = left + (i + 0.5f) * slot
        fun yOf(min: Double, max: Double): (Float) -> Float {
            val span = (max - min).toFloat().takeIf { it > 0f } ?: 1f
            return { v -> bottom - ((v - min.toFloat()) / span).coerceIn(0f, 1f) * (bottom - topY) }
        }
        val y = yOf(scale.min, scale.max)
        val leftScale = barScale ?: scale
        val leftY = yOf(leftScale.min, leftScale.max)
        drawGrid(leftScale.ticks.map { it.toFloat() }, leftY, left, plot, colors.line)
        if (axis) {
            drawAxisLabels(
                measurer,
                labelStyle,
                leftScale.ticks.map {
                    it.toFloat()
                },
                format,
                leftY,
                left,
                start = true
            )
            if (right) drawAxisLabels(measurer, labelStyle, scale.ticks.map { it.toFloat() }, format, y, left + plot)
        }
        if (bars != null && barScale != null) {
            if (barScale.min < 0 && barScale.max > 0) {
                drawLine(colors.muted, Offset(left, leftY(0f)), Offset(left + plot, leftY(0f)), 1.dp.toPx())
            }
            drawSignedBars(bars, slot, left, leftY, MAX_BAR.toPx())
        }
        lines.forEach { line ->
            val path = Path()
            var started = false
            line.values.forEachIndexed { i, v ->
                if (v == null) {
                    started = false
                } else if (!started) {
                    path.moveTo(x(i), y(v))
                    started = true
                } else {
                    path.lineTo(x(i), y(v))
                }
            }
            drawPath(
                path,
                line.color.copy(alpha = line.alpha),
                style = Stroke(line.width.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
        drawXMarks(measurer, labelStyle, xLabels, ::x, bottom)
        val at = scrub.intValue
        if (at in 0 until count) {
            drawLine(colors.fg, Offset(x(at), topY), Offset(x(at), bottom), strokeWidth = 1.dp.toPx())
            val text = buildList {
                xLabels.getOrNull(at)?.let { add(it) }
                lines.forEach { l -> l.values.getOrNull(at)?.let { add("${l.label} ${format(it)}") } }
                bars?.values?.getOrNull(at)?.let { add(format(it)) }
            }.joinToString("  ·  ")
            drawReadout(measurer, readout, text, x(at), colors.surface2, colors.line)
        }
    }
}

/**
 * Stacked bars (volume by sport): one stack per x label, at most 14 dp wide in the middle of its slot,
 * on the tightest round axis. A finger reads a stack's total and label.
 */
@Composable
internal fun StackedBars(
    stacks: List<Stack>,
    xLabels: List<String>,
    format: (Float) -> String,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 170.dp
) {
    val colors = ZankupTheme.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = colors.muted, fontSize = 10.sp)
    val readout = TextStyle(color = colors.fg, fontSize = 12.sp)
    val scrub = remember { mutableIntStateOf(-1) }
    val top = stacks.maxOfOrNull { it.total }?.takeIf { it > 0f } ?: 1f
    val scale = tightAxis(0.0, top.toDouble())
    val count = stacks.size
    fun plotOf(width: Float, density: Float) = width - (AXIS_WIDTH.value + RIGHT_GAP.value) * density
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
            .pointerInput(count) {
                val plot = plotOf(size.width.toFloat(), density)
                detectTapGestures(onPress = {
                    scrub.intValue = indexAt(it.x, AXIS_WIDTH.toPx(), plot, count)
                    tryAwaitRelease()
                    scrub.intValue = -1
                })
            }.pointerInput(count) {
                val plot = plotOf(size.width.toFloat(), density)
                detectHorizontalDragGestures(
                    onDragStart = { scrub.intValue = indexAt(it.x, AXIS_WIDTH.toPx(), plot, count) },
                    onDragEnd = { scrub.intValue = -1 },
                    onDragCancel = { scrub.intValue = -1 }
                ) { change, _ -> scrub.intValue = indexAt(change.position.x, AXIS_WIDTH.toPx(), plot, count) }
            }
    ) {
        if (count == 0) return@Canvas
        val left = AXIS_WIDTH.toPx()
        val plot = plotOf(size.width, density)
        val topY = TOP_GAP.toPx()
        val bottom = size.height - BOTTOM_BAND.toPx()
        val slot = plot / count
        val bar = (slot * 0.7f).coerceIn(1f, MAX_BAR.toPx())
        val max = scale.max.toFloat()
        val y: (Float) -> Float = { v -> bottom - (v / max).coerceIn(0f, 1f) * (bottom - topY) }
        val ticks = scale.ticks.map { it.toFloat() }
        drawGrid(ticks, y, left, plot, colors.line)
        drawAxisLabels(measurer, labelStyle, ticks, format, y, left, start = true)
        val picked = scrub.intValue
        // As the RN app's: every bar a touch lighter while one is read.
        val alpha = if (picked < 0) 1f else 0.9f
        stacks.forEachIndexed { i, stack ->
            var base = 0f
            val x = left + i * slot + (slot - bar) / 2
            stack.parts.forEach { (v, c) ->
                if (v <= 0f) return@forEach
                val y0 = y(base)
                val y1 = y(base + v)
                drawRect(c.copy(alpha = alpha), Offset(x, y1), Size(bar, maxOf(1f, y0 - y1)))
                base += v
            }
        }
        drawXMarks(measurer, labelStyle, xLabels, { left + (it + 0.5f) * slot }, bottom)
        if (picked in 0 until count) {
            val px = left + (picked + 0.5f) * slot
            drawLine(colors.fg, Offset(px, topY), Offset(px, bottom), strokeWidth = 1.dp.toPx())
            val text = listOfNotNull(xLabels.getOrNull(picked), format(stacks[picked].total)).joinToString("  ·  ")
            drawReadout(measurer, readout, text, px, colors.surface2, colors.line)
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

/** The grid: a solid line at the foot and dashed ones above, in the line colour. */
private fun DrawScope.drawGrid(ticks: List<Float>, y: (Float) -> Float, left: Float, plot: Float, line: Color) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
    ticks.forEachIndexed { i, tick ->
        val ty = y(tick)
        drawLine(
            line,
            Offset(left, ty),
            Offset(left + plot, ty),
            strokeWidth = 1f,
            pathEffect = if (i >
                0
            ) {
                dash
            } else {
                null
            }
        )
    }
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
        val lx = if (start) (x - label.size.width - 6.dp.toPx()).coerceAtLeast(0f) else x + 6.dp.toPx()
        drawText(label, topLeft = Offset(lx, top))
    }
}

/** About five labels under the chart, spread evenly, always the first and the last. */
private fun DrawScope.drawXMarks(
    measurer: TextMeasurer,
    style: TextStyle,
    labels: List<String>,
    x: (Int) -> Float,
    bottom: Float
) {
    if (labels.isEmpty()) return
    val marks = if (labels.size <= X_MARKS) {
        labels.indices.toList()
    } else {
        val every = (labels.size - 1) / (X_MARKS - 1f)
        (0 until X_MARKS).map { (it * every).roundToInt() }
    }
    marks.forEach { i ->
        val label = measurer.measure(labels[i], style)
        val lx = (x(i) - label.size.width / 2f).coerceIn(0f, (size.width - label.size.width).coerceAtLeast(0f))
        drawText(label, topLeft = Offset(lx, bottom + 4.dp.toPx()))
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

/** The reading at the finger: the card colour with a hairline, as the RN app's tooltip. */
private fun DrawScope.drawReadout(
    measurer: TextMeasurer,
    style: TextStyle,
    text: String,
    x: Float,
    fill: Color,
    border: Color
) {
    if (text.isEmpty()) return
    val label = measurer.measure(text, style, constraints = Constraints(maxWidth = (size.width * 0.9f).toInt()))
    val padX = 8.dp.toPx()
    val padY = 6.dp.toPx()
    val w = label.size.width + padX * 2
    val h = label.size.height + padY * 2
    val lx = (x - w / 2).coerceIn(0f, (size.width - w).coerceAtLeast(0f))
    val corner = CornerRadius(8.dp.toPx())
    drawRoundRect(fill, Offset(lx, 0f), Size(w, h), corner)
    drawRoundRect(border, Offset(lx, 0f), Size(w, h), corner, style = Stroke(1f))
    drawText(label, topLeft = Offset(lx + padX, padY))
}
