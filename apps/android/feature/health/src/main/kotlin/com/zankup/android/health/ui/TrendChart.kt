package com.zankup.android.health.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
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
import com.zankup.android.domain.Axis
import com.zankup.android.domain.formatNumber
import com.zankup.android.domain.niceAxis
import com.zankup.android.domain.tightAxis
import com.zankup.android.ui.LocalFormats
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** What a trend chart draws: lines, or bars (stacked, hanging from zero below it). */
internal sealed interface ChartLayer

internal data class LineLayer(
    val label: String,
    val color: Color,
    val values: List<Double?>,
    val domain: Pair<Double, Double>? = null,
    val width: Dp = 3.dp,
    val hideLegend: Boolean = false
) : ChartLayer

internal data class BarSeries(val label: String, val color: Color, val values: List<Double?>)

internal data class BarsLayer(val series: List<BarSeries>, val domain: Pair<Double, Double>? = null) : ChartLayer

/** The range a layer is drawn on: its own unless given, and always including zero for bars. */
internal fun domainOf(layer: ChartLayer): Pair<Double, Double> {
    when (layer) {
        is LineLayer -> {
            layer.domain?.let { return it }
            val values = layer.values.filterNotNull()
            if (values.isEmpty()) return 0.0 to 1.0
            val lo = values.min()
            val hi = values.max()
            val pad = ((hi - lo) * 0.1).takeIf { it != 0.0 } ?: 1.0
            return lo - pad to hi + pad
        }
        is BarsLayer -> {
            layer.domain?.let { return it }
            val length = layer.series.maxOfOrNull { it.values.size } ?: 0
            var lo = 0.0
            var hi = 0.0
            for (i in 0 until length) {
                lo = minOf(lo, layer.series.sumOf { s -> s.values.getOrNull(i)?.takeIf { it < 0 } ?: 0.0 })
                hi = maxOf(hi, layer.series.sumOf { s -> s.values.getOrNull(i)?.takeIf { it > 0 } ?: 0.0 })
            }
            return if (hi == lo) lo to lo + 1 else lo to hi
        }
    }
}

/** The scale each layer uses: layers given the same domain share one; the first is the left axis. */
internal fun chartScales(layers: List<ChartLayer>): Pair<List<Int>, List<Axis>> {
    val keys = ArrayList<String>()
    val of = layers.mapIndexed { index, layer ->
        val domain = (layer as? LineLayer)?.domain ?: (layer as? BarsLayer)?.domain
        val key = domain?.let { "${it.first}:${it.second}" } ?: "own:$index"
        if (key !in keys) keys += key
        keys.indexOf(key)
    }
    // Two scales: four steps each, so both axes' labels sit on the same grid lines.
    val axes = keys.indices.map { k ->
        val (lo, hi) = domainOf(layers[of.indexOf(k)])
        if (keys.size > 1) niceAxis(lo, hi, 4) else tightAxis(lo, hi)
    }
    return of to axes
}

/** The days written under the chart: about five, spread evenly, always the first and the last. */
internal fun dayTicks(count: Int, wanted: Int = 5): List<Int> {
    if (count <= 0) return emptyList()
    if (count <= wanted) return List(count) { it }
    val every = (count - 1).toDouble() / (wanted - 1)
    return List(wanted) { (it * every).roundToInt() }
}

/** A tick, short: 1,5 · 120 · 12k. */
internal fun shortNumber(v: Double, locale: java.util.Locale): String {
    val a = abs(v)
    return when {
        a >= 10_000 -> "${formatNumber(v / 1000, locale, 0, 1)}k"
        a >= 100 || v == floor(v) -> formatNumber(v, locale, 0, 0)
        else -> formatNumber(v, locale, 0, 1)
    }
}

/**
 * A trend over days, as the React Native app's chart: a grid with round values on the left (and a right
 * axis for a second scale), a few days along the bottom and a legend. Touching or sliding sideways picks
 * a day and shows its values; a vertical swipe still scrolls the page. The pick and the growth on new
 * values are read only while drawing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TrendChart(
    layers: List<ChartLayer>,
    days: List<String>,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    legend: Boolean = true,
    legendGap: Dp = 18.dp,
    format: (Double) -> String = { formatNumber(it, java.util.Locale.ROOT, 0, 1) }
) {
    val colors = ZankupTheme.colors
    val locale = LocalFormats.current.locale
    val measurer = rememberTextMeasurer()
    val count = days.size
    val picked = remember(count) { mutableIntStateOf(-1) }
    val grow = remember { Animatable(0f) }
    LaunchedEffect(layers) {
        grow.snapTo(0f)
        grow.animateTo(1f, tween(320))
    }
    val (of, axes) = remember(layers) { chartScales(layers) }
    val right = axes.size > 1
    val muted = TextStyle(color = colors.fg, fontSize = 12.sp, fontFeatureSettings = "tnum")
    val ink = Color(0xFF16181D)
    val valueStyle =
        TextStyle(color = ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
    val whereStyle = TextStyle(color = ink, fontSize = 13.sp)
    // Where the plot starts and ends: just past the widest values on each side. Worked out while drawing.
    val plotLeft = remember { mutableFloatStateOf(0f) }
    val plotRight = remember { mutableFloatStateOf(0f) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(legendGap)) {
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(height + 64.dp)
                .semantics { contentDescription = description }
                .pointerInput(count, right) {
                    fun index(x: Float): Int {
                        val left = plotLeft.floatValue
                        val plot = plotRight.floatValue - left
                        val slot = if (count > 0) plot / count else 0f
                        return if (slot > 0) floor((x - left) / slot).toInt() else -1
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                        if (drag == null) {
                            val up = currentEvent.changes.firstOrNull { it.id == down.id }
                            if (up != null && !up.pressed && !up.isConsumed) {
                                val i = index(down.position.x)
                                picked.intValue = if (i == picked.intValue || i !in 0 until count) -1 else i
                            }
                            return@awaitEachGesture
                        }
                        picked.intValue = index(drag.position.x).coerceIn(0, count - 1)
                        horizontalDrag(drag.id) { change ->
                            picked.intValue = index(change.position.x).coerceIn(0, count - 1)
                            change.consume()
                        }
                    }
                }.drawWithCache {
                    // Room on top for the bubble, which rises over the title while a day is picked.
                    val top = 36.dp.toPx()
                    val chartH = height.toPx() + 28.dp.toPx()
                    val inner = chartH - top
                    val gapLabel = 10.dp.toPx()
                    val leftTicks = axes.firstOrNull()?.ticks.orEmpty()
                    val steps = (leftTicks.size - 1).coerceAtLeast(1)
                    val leftTexts = leftTicks.map { measurer.measure(shortNumber(it, locale), muted) }
                    val rightTexts = axes.getOrNull(1)?.ticks.orEmpty().map {
                        measurer.measure(shortNumber(it, locale), muted)
                    }
                    val axisW = (leftTexts.maxOfOrNull { it.size.width } ?: 0) + gapLabel
                    val rightW = if (right) (rightTexts.maxOfOrNull { it.size.width } ?: 0) + gapLabel else 1f
                    val plotEnd = size.width - rightW
                    plotLeft.floatValue = axisW
                    plotRight.floatValue = plotEnd
                    val plot = (plotEnd - axisW).coerceAtLeast(0f)
                    val slot = if (count > 0) plot / count else 0f
                    val bar = (slot * 0.7f).coerceIn(1f, 14.dp.toPx())
                    val floorY = top + inner
                    fun y(scale: Int, v: Double): Float {
                        val a = axes[scale]
                        val span = (a.max - a.min).takeIf { it != 0.0 } ?: 1.0
                        return (top + inner - (v - a.min) / span * inner).toFloat()
                    }
                    fun x(i: Int) = axisW + i * slot + slot / 2
                    val ticksAt = dayTicks(count)
                    val dayTexts = ticksAt.map { it to measurer.measure(days[it], muted) }
                    val grid = Color(0xFF4A4A48)
                    onDrawBehind {
                        val t = grow.value
                        // Bars dim a little while a day is picked, as the React Native app's.
                        val barAlpha = if (picked.intValue in 0 until count) 0.9f else 1f
                        // The grid: a line at each round value and at each marked day, inside a frame.
                        for (i in leftTicks.indices) {
                            val py = top + inner - i.toFloat() / steps * inner
                            drawLine(grid, Offset(axisW, py), Offset(plotEnd, py), 1.5f)
                            val text = leftTexts[i]
                            drawText(
                                text,
                                topLeft = Offset(
                                    axisW - gapLabel - text.size.width,
                                    py - text.size.height / 2
                                )
                            )
                        }
                        rightTexts.forEachIndexed { i, text ->
                            val py = top + inner - i.toFloat() / steps * inner
                            drawText(text, topLeft = Offset(plotEnd + gapLabel, py - text.size.height / 2))
                        }
                        ticksAt.forEach { i ->
                            drawLine(grid, Offset(x(i), top), Offset(x(i), floorY + 4.dp.toPx()), 1.5f)
                        }
                        axes.forEachIndexed { k, a ->
                            if (a.min < 0 && a.max > 0) {
                                val zy = y(k, 0.0)
                                drawLine(colors.muted, Offset(axisW, zy), Offset(plotEnd, zy), 1.5f)
                            }
                        }
                        layers.forEachIndexed { index, layer ->
                            val scale = of[index]
                            when (layer) {
                                is BarsLayer -> {
                                    val pos = DoubleArray(count)
                                    val neg = DoubleArray(count)
                                    layer.series.forEach { s ->
                                        for (i in 0 until count) {
                                            val v = (s.values.getOrNull(i) ?: continue) * t
                                            if (v == 0.0) continue
                                            val under = if (v > 0) pos[i] else neg[i]
                                            val a = y(scale, under)
                                            val b = y(scale, under + v)
                                            if (v > 0) pos[i] += v else neg[i] += v
                                            val h = abs(a - b).coerceAtLeast(1f)
                                            drawRect(
                                                s.color,
                                                Offset(axisW + i * slot + (slot - bar) / 2, minOf(a, b)),
                                                Size(bar, h),
                                                alpha = barAlpha
                                            )
                                        }
                                    }
                                }
                                is LineLayer -> {
                                    val stroke = layer.width.toPx()
                                    var path: Path? = null
                                    var points = 0
                                    var lx = 0f
                                    var ly = 0f
                                    fun flush() {
                                        val p = path ?: return
                                        if (points > 1) {
                                            p.lineTo(lx, ly)
                                            drawPath(
                                                p,
                                                layer.color,
                                                style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                            )
                                        }
                                        path = null
                                        points = 0
                                    }
                                    for (i in 0 until count) {
                                        val v = layer.values.getOrNull(i)
                                        if (v == null) {
                                            flush()
                                            continue
                                        }
                                        val px = x(i)
                                        val py = floorY + (y(scale, v) - floorY) * t
                                        val p = path ?: Path().also { path = it }
                                        // Smooth: through the midpoints, each day the control of its curve.
                                        if (points ==
                                            0
                                        ) {
                                            p.moveTo(px, py)
                                        } else {
                                            p.quadraticTo(lx, ly, (lx + px) / 2, (ly + py) / 2)
                                        }
                                        lx = px
                                        ly = py
                                        points++
                                        // A day alone between gaps: a dot.
                                        val alone = layer.values.getOrNull(i - 1) == null &&
                                            layer.values.getOrNull(i + 1) == null
                                        if (alone) drawCircle(layer.color, stroke / 2 + 1f, Offset(px, py))
                                    }
                                    flush()
                                }
                            }
                        }
                        drawRect(grid, Offset(axisW, top), Size(plot, inner), style = Stroke(1.5f))
                        dayTexts.forEach { (i, text) ->
                            val lx = (
                                x(
                                    i
                                ) - text.size.width / 2f
                                ).coerceIn(0f, (size.width - text.size.width).coerceAtLeast(0f))
                            drawText(text, topLeft = Offset(lx, floorY + 8.dp.toPx()))
                        }
                        val at = picked.intValue.takeIf { it in 0 until count } ?: return@onDrawBehind
                        val px = x(at)
                        // The picked day: a bubble above the chart with its values and the day, a white line
                        // down from it and a dot on each line.
                        val items = layers.flatMap { layer ->
                            when (layer) {
                                is LineLayer -> listOf(Triple(layer.label, layer.color, layer.values.getOrNull(at)))
                                is BarsLayer -> layer.series.map { Triple(it.label, it.color, it.values.getOrNull(at)) }
                            }
                        }.filter { it.third != null }.take(6)
                        val single = items.size == 1
                        val tint = if (single) items.first().second else colors.fg
                        val padX = 12.dp.toPx()
                        val padY = 8.dp.toPx()
                        val dot = 8.dp.toPx()
                        val rows = items.map { (name, color, v) ->
                            val text = if (single) format(v!!) else "$name  ${format(v!!)}"
                            color to measurer.measure(text, valueStyle.copy(fontSize = if (single) 17.sp else 13.sp))
                        }
                        val where = measurer.measure(days[at], whereStyle)
                        val inset = if (single) 0f else dot + 6.dp.toPx()
                        val w =
                            maxOf(where.size.width.toFloat(), rows.maxOfOrNull { it.second.size.width + inset } ?: 0f) +
                                padX * 2
                        val h = rows.sumOf { it.second.size.height } + where.size.height + padY * 2
                        val bx = (px - w / 2f).coerceIn(0f, (size.width - w).coerceAtLeast(0f))
                        val by = maxOf(top - h - 6.dp.toPx(), -40.dp.toPx())
                        drawLine(Color.White, Offset(px, by + h), Offset(px, floorY), 2.dp.toPx())
                        drawRoundRect(tint, Offset(bx, by), Size(w, h), CornerRadius(10.dp.toPx()))
                        var ty = by + padY
                        rows.forEach { (color, text) ->
                            if (!single) {
                                drawCircle(
                                    color,
                                    dot / 2,
                                    Offset(
                                        bx + padX + dot / 2,
                                        ty + text.size.height / 2f
                                    )
                                )
                            }
                            drawText(text, topLeft = Offset(bx + padX + inset, ty))
                            ty += text.size.height
                        }
                        drawText(where, topLeft = Offset(bx + padX, ty))
                        layers.forEachIndexed { index, layer ->
                            if (layer is LineLayer) {
                                layer.values.getOrNull(at)?.let { v ->
                                    val py = y(of[index], v)
                                    drawCircle(Color.White.copy(alpha = 0.3f), 9.dp.toPx(), Offset(px, py))
                                    drawCircle(Color.White, 5.dp.toPx(), Offset(px, py))
                                }
                            }
                        }
                    }
                }
        )
        if (legend) {
            val entries = layers.flatMap { layer ->
                when (layer) {
                    is LineLayer -> if (layer.hideLegend) emptyList() else listOf(layer.label to layer.color)
                    is BarsLayer -> layer.series.map { it.label to it.color }
                }
            }
            if (entries.isNotEmpty()) {
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    entries.forEach { (label, color) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.size(8.dp).clip(CircleShape).background(color))
                            Text(
                                label,
                                Modifier.padding(start = 4.dp),
                                fontSize = 12.sp,
                                color = colors.muted
                            )
                        }
                    }
                }
            }
        }
    }
}
