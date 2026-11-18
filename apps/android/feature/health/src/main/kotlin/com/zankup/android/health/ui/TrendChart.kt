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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.PathEffect
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
    val muted = TextStyle(color = colors.muted, fontSize = 10.sp, fontFeatureSettings = "tnum")
    val strong = TextStyle(color = colors.fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    val plain = TextStyle(color = colors.fg, fontSize = 12.sp)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(legendGap)) {
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(height + 16.dp)
                .semantics { contentDescription = description }
                .pointerInput(count, right) {
                    val left = 34.dp.toPx()
                    fun index(x: Float): Int {
                        val plot = size.width - left - (if (right) 34.dp.toPx() else 8.dp.toPx())
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
                    val axisW = 34.dp.toPx()
                    val top = 8.dp.toPx()
                    val chartH = height.toPx()
                    val inner = chartH - top
                    val plot = (size.width - axisW - (if (right) axisW else 8.dp.toPx())).coerceAtLeast(0f)
                    val slot = if (count > 0) plot / count else 0f
                    val bar = (slot * 0.7f).coerceIn(1f, 14.dp.toPx())
                    val floorY = top + inner
                    fun y(scale: Int, v: Double): Float {
                        val a = axes[scale]
                        val span = (a.max - a.min).takeIf { it != 0.0 } ?: 1.0
                        return (top + inner - (v - a.min) / span * inner).toFloat()
                    }
                    fun x(i: Int) = axisW + i * slot + slot / 2
                    val leftTicks = axes.firstOrNull()?.ticks.orEmpty()
                    val steps = (leftTicks.size - 1).coerceAtLeast(1)
                    val leftTexts = leftTicks.map { measurer.measure(shortNumber(it, locale), muted) }
                    fun tick(v: Double) = measurer.measure(shortNumber(v, locale), muted)
                    val rightTexts = axes.getOrNull(1)?.ticks.orEmpty().map(::tick)
                    val dayTexts = dayTicks(count).map { it to measurer.measure(days[it], muted) }
                    val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
                    onDrawBehind {
                        val t = grow.value
                        for (i in leftTicks.indices) {
                            val py = top + inner - i.toFloat() / steps * inner
                            drawLine(
                                colors.line,
                                Offset(axisW, py),
                                Offset(axisW + plot, py),
                                1f,
                                pathEffect = if (i > 0) dash else null
                            )
                            val text = leftTexts[i]
                            val tx = axisW - 6.dp.toPx() - text.size.width
                            drawText(text, topLeft = Offset(tx, py - text.size.height / 2))
                        }
                        rightTexts.forEachIndexed { i, text ->
                            val py = top + inner - i.toFloat() / steps * inner
                            val tx = size.width - axisW + 6.dp.toPx()
                            drawText(text, topLeft = Offset(tx, py - text.size.height / 2))
                        }
                        axes.forEachIndexed { k, a ->
                            if (a.min < 0 && a.max > 0) {
                                val zy = y(k, 0.0)
                                drawLine(colors.muted, Offset(axisW, zy), Offset(axisW + plot, zy), 1f)
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
                                                Size(bar, h)
                                            )
                                        }
                                    }
                                }
                                is LineLayer -> {
                                    val stroke = layer.width.toPx()
                                    var path: Path? = null
                                    var points = 0
                                    fun flush() {
                                        val p = path ?: return
                                        if (points > 1) {
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
                                        val py = floorY + (y(scale, v) - floorY) * t
                                        val p = path ?: Path().also { path = it }
                                        if (points == 0) p.moveTo(x(i), py) else p.lineTo(x(i), py)
                                        points++
                                        // A day alone between gaps: a dot.
                                        val alone = layer.values.getOrNull(i - 1) == null &&
                                            layer.values.getOrNull(i + 1) == null
                                        if (alone) drawCircle(layer.color, stroke / 2 + 1f, Offset(x(i), py))
                                    }
                                    flush()
                                }
                            }
                        }
                        dayTexts.forEach { (i, text) ->
                            val lx = (x(i) - text.size.width / 2f)
                                .coerceIn(axisW - 12.dp.toPx(), (size.width - text.size.width).coerceAtLeast(0f))
                            drawText(text, topLeft = Offset(lx, chartH + 2.dp.toPx()))
                        }
                        val at = picked.intValue.takeIf { it in 0 until count } ?: return@onDrawBehind
                        val px = x(at)
                        drawLine(colors.fg, Offset(px, top), Offset(px, chartH), 1f)
                        // The picked day's values, beside the rule.
                        val items = layers.flatMap { layer ->
                            when (layer) {
                                is LineLayer -> listOf(Triple(layer.label, layer.color, layer.values.getOrNull(at)))
                                is BarsLayer -> layer.series.map { Triple(it.label, it.color, it.values.getOrNull(at)) }
                            }
                        }.filter { it.third != null }.take(8)
                        val head = measurer.measure(days[at], strong)
                        val rows = items.map { (label, color, v) ->
                            color to measurer.measure("$label  ${format(v!!)}", plain)
                        }
                        val pad = 8.dp.toPx()
                        val dot = 7.dp.toPx()
                        val gap = 2.dp.toPx()
                        val widest = rows.maxOfOrNull { it.second.size.width + dot + pad } ?: 0f
                        val w = maxOf(head.size.width.toFloat(), widest) + pad * 2
                        val h = head.size.height + rows.sumOf { it.second.size.height } + gap * rows.size + pad * 2
                        val bx = if (px < size.width / 2) px + 8.dp.toPx() else px - 8.dp.toPx() - w
                        drawRoundRect(colors.surface2, Offset(bx, 0f), Size(w, h), CornerRadius(8.dp.toPx()))
                        drawRoundRect(
                            colors.line,
                            Offset(bx, 0f),
                            Size(w, h),
                            CornerRadius(8.dp.toPx()),
                            style = Stroke(1f)
                        )
                        var ty = pad
                        drawText(head, topLeft = Offset(bx + pad, ty))
                        ty += head.size.height + gap
                        rows.forEach { (color, text) ->
                            drawCircle(color, dot / 2, Offset(bx + pad + dot / 2, ty + text.size.height / 2f))
                            drawText(text, topLeft = Offset(bx + pad + dot + pad / 2, ty))
                            ty += text.size.height + gap
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    entries.forEach { (label, color) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.size(8.dp).clip(CircleShape).background(color))
                            Text(
                                label,
                                Modifier.padding(start = 6.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted
                            )
                        }
                    }
                }
            }
        }
    }
}
