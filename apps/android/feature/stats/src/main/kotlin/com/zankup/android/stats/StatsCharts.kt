package com.zankup.android.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.niceAxis

/** One line of a chart: a value per x (null where there is none). */
@Immutable
class ChartLine(
    val label: String,
    val values: List<Float?>,
    val color: Color,
    val width: Dp = 2.dp,
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

private val AXIS_WIDTH = 40.dp
private val BOTTOM_BAND = 20.dp
private const val X_MARKS = 4

/** The index under a finger at [x] across [count] points spread over the plot. */
private fun indexAt(x: Float, left: Float, width: Float, count: Int): Int {
    if (count <= 1) return 0
    val share = ((x - left) / (width - left)).coerceIn(0f, 1f)
    return (share * (count - 1) + 0.5f).toInt()
}

/**
 * Lines over x labels (days, durations), with round values on the left. A finger sideways reads every
 * line at that x; a vertical swipe still scrolls. The finger's position is read only while drawing.
 * A null [height] fills what the parent gives.
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
    val labelStyle = TextStyle(color = colors.muted, fontSize = 11.sp)
    val readout = TextStyle(color = colors.bg, fontSize = 12.sp)
    val scrub = remember { mutableIntStateOf(-1) }
    val count = maxOf(xLabels.size, lines.maxOfOrNull { it.values.size } ?: 0)
    val values = lines.flatMap { it.values.filterNotNull() }
    val lo = domain?.start ?: (values.minOrNull() ?: 0f)
    val hi = domain?.endInclusive ?: (values.maxOrNull() ?: 1f)
    val scale = niceAxis(lo.toDouble(), if (hi > lo) hi.toDouble() else lo + 1.0)
    val sized = if (height != null) modifier.fillMaxWidth().height(height) else modifier.fillMaxSize()
    Canvas(
        sized
            .semantics { contentDescription = description }
            .pointerInput(count) {
                val left = if (axis) AXIS_WIDTH.toPx() else 0f
                detectTapGestures(onPress = {
                    scrub.intValue = indexAt(it.x, left, size.width.toFloat(), count)
                    tryAwaitRelease()
                    scrub.intValue = -1
                })
            }.pointerInput(count) {
                val left = if (axis) AXIS_WIDTH.toPx() else 0f
                detectHorizontalDragGestures(
                    onDragStart = { scrub.intValue = indexAt(it.x, left, size.width.toFloat(), count) },
                    onDragEnd = { scrub.intValue = -1 },
                    onDragCancel = { scrub.intValue = -1 }
                ) { change, _ -> scrub.intValue = indexAt(change.position.x, left, size.width.toFloat(), count) }
            }
    ) {
        val left = if (axis) AXIS_WIDTH.toPx() else 0f
        val bottom = size.height - if (xLabels.isEmpty()) 0f else BOTTOM_BAND.toPx()
        val width = size.width - left
        val min = scale.min.toFloat()
        val span = (scale.max - scale.min).toFloat().takeIf { it > 0f } ?: 1f
        fun x(i: Int) = left + if (count <= 1) width / 2 else i * width / (count - 1)
        fun y(v: Float) = bottom - ((v - min) / span).coerceIn(0f, 1f) * bottom
        if (axis) drawTicks(measurer, labelStyle, scale.ticks.map { it.toFloat() }, format, left, ::y, colors.line)
        bars?.let { drawSignedBars(it, count, left, width, bottom) }
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
            drawLine(colors.muted, Offset(x(at), 0f), Offset(x(at), bottom), strokeWidth = 1.dp.toPx())
            val text = buildList {
                xLabels.getOrNull(at)?.let { add(it) }
                lines.forEach { l -> l.values.getOrNull(at)?.let { add("${l.label} ${format(it)}") } }
                bars?.values?.getOrNull(at)?.let { add(format(it)) }
            }.joinToString("  ·  ")
            drawReadout(measurer, readout, text, x(at), colors.fg)
        }
    }
}

/**
 * Stacked bars (volume by sport): one stack per x label. A finger reads a stack's total and label.
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
    val labelStyle = TextStyle(color = colors.muted, fontSize = 11.sp)
    val readout = TextStyle(color = colors.bg, fontSize = 12.sp)
    val scrub = remember { mutableIntStateOf(-1) }
    val top = stacks.maxOfOrNull { it.total }?.takeIf { it > 0f } ?: 1f
    val scale = niceAxis(0.0, top.toDouble())
    val count = stacks.size
    fun at(x: Float, left: Float, width: Float) = (((x - left) / (width - left)).coerceIn(0f, 0.9999f) * count).toInt()
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
            .pointerInput(count) {
                detectTapGestures(onPress = {
                    scrub.intValue = at(it.x, AXIS_WIDTH.toPx(), size.width.toFloat())
                    tryAwaitRelease()
                    scrub.intValue = -1
                })
            }.pointerInput(count) {
                detectHorizontalDragGestures(
                    onDragStart = { scrub.intValue = at(it.x, AXIS_WIDTH.toPx(), size.width.toFloat()) },
                    onDragEnd = { scrub.intValue = -1 },
                    onDragCancel = { scrub.intValue = -1 }
                ) { change, _ -> scrub.intValue = at(change.position.x, AXIS_WIDTH.toPx(), size.width.toFloat()) }
            }
    ) {
        if (count == 0) return@Canvas
        val left = AXIS_WIDTH.toPx()
        val bottom = size.height - BOTTOM_BAND.toPx()
        val slot = (size.width - left) / count
        val gap = (slot * 0.2f).coerceAtMost(4.dp.toPx())
        val max = scale.max.toFloat()
        fun y(v: Float) = bottom - (v / max).coerceIn(0f, 1f) * bottom
        drawTicks(measurer, labelStyle, scale.ticks.map { it.toFloat() }, format, left, ::y, colors.line)
        val picked = scrub.intValue
        stacks.forEachIndexed { i, stack ->
            var base = 0f
            val x = left + i * slot + gap / 2
            stack.parts.forEach { (v, c) ->
                if (v <= 0f) return@forEach
                val y0 = y(base)
                val y1 = y(base + v)
                drawRect(
                    c.copy(alpha = if (picked < 0 || picked == i) 1f else 0.4f),
                    Offset(x, y1),
                    Size(slot - gap, y0 - y1)
                )
                base += v
            }
        }
        drawXMarks(measurer, labelStyle, xLabels, { left + (it + 0.5f) * slot }, bottom)
        if (picked in 0 until count) {
            val text = listOfNotNull(xLabels.getOrNull(picked), format(stacks[picked].total)).joinToString("  ·  ")
            drawReadout(measurer, readout, text, left + (picked + 0.5f) * slot, colors.fg)
        }
    }
}

/** The colours of a chart's series, under it. */
@Composable
internal fun Legend(items: List<Pair<String, Color>>, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { (label, colour) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(12.dp, 7.dp).clip(RoundedCornerShape(4.dp)).background(colour))
                Text(label, style = MaterialTheme.typography.labelSmall, color = ZankupTheme.colors.muted)
            }
        }
    }
}

private fun DrawScope.drawTicks(
    measurer: TextMeasurer,
    style: TextStyle,
    ticks: List<Float>,
    format: (Float) -> String,
    left: Float,
    y: (Float) -> Float,
    line: Color
) {
    ticks.forEach { tick ->
        val ty = y(tick)
        drawLine(line, Offset(left, ty), Offset(size.width, ty), strokeWidth = 1f)
        val label = measurer.measure(format(tick), style)
        val top = (ty - label.size.height / 2f).coerceIn(0f, size.height - label.size.height)
        drawText(label, topLeft = Offset((left - label.size.width - 6.dp.toPx()).coerceAtLeast(0f), top))
    }
}

private fun DrawScope.drawXMarks(
    measurer: TextMeasurer,
    style: TextStyle,
    labels: List<String>,
    x: (Int) -> Float,
    bottom: Float
) {
    if (labels.isEmpty()) return
    val step = maxOf(1, (labels.size + X_MARKS - 1) / X_MARKS)
    var free = 0f
    for (i in labels.indices step step) {
        val label = measurer.measure(labels[i], style)
        val lx = (x(i) - label.size.width / 2f).coerceIn(0f, size.width - label.size.width)
        if (lx < free) continue
        drawText(label, topLeft = Offset(lx, bottom + 4.dp.toPx()))
        free = lx + label.size.width + 8.dp.toPx()
    }
}

private fun DrawScope.drawSignedBars(bars: SignedBars, count: Int, left: Float, width: Float, bottom: Float) {
    val top = bars.values.maxOfOrNull { kotlin.math.abs(it) }?.takeIf { it > 0f } ?: return
    val zero = bottom / 2
    val slot = width / maxOf(count, 1)
    bars.values.forEachIndexed { i, v ->
        val h = v / top * zero
        val x = left + i * slot
        drawRect(
            if (v >= 0) bars.positive else bars.negative,
            Offset(x, if (h >= 0) zero - h else zero),
            Size(maxOf(slot * 0.8f, 1f), kotlin.math.abs(h))
        )
    }
}

private fun DrawScope.drawReadout(measurer: TextMeasurer, style: TextStyle, text: String, x: Float, fill: Color) {
    if (text.isEmpty()) return
    val label = measurer.measure(text, style)
    val pad = 6.dp.toPx()
    val w = label.size.width + pad * 2
    val h = label.size.height + pad
    val lx = (x - w / 2).coerceIn(0f, (size.width - w).coerceAtLeast(0f))
    drawRoundRect(fill, Offset(lx, 0f), Size(w, h), CornerRadius(6.dp.toPx()))
    drawText(label, topLeft = Offset(lx + pad, pad / 2))
}
