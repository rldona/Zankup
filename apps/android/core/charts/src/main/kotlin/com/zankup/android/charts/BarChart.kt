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
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
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
import com.zankup.android.domain.niceAxis

/** A bar: its value, the label under it and, if it has one, its own colour. */
@Immutable
data class Bar(
    val value: Float,
    val label: String = "",
    val color: Color? = null,
    /** Its share of the chart's width (a split as wide as its distance); equal when all are 1. */
    val weight: Float = 1f
)

/**
 * Bars (weekly volume, splits, months): the round axis on the left, the labels underneath, an optional
 * dashed [average]. Touching or dragging picks a bar, with a click each time it changes; when the bars'
 * values change they grow or shrink from their old height instead of starting from zero.
 */
@Composable
fun BarChart(
    bars: List<Bar>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    format: (Float) -> String,
    modifier: Modifier = Modifier,
    color: Color = ZankupTheme.colors.accent,
    average: Float? = null,
    /** Lower is better (a pace): taller is faster. */
    inverted: Boolean = false,
    height: Dp = 180.dp,
    description: String = ""
) {
    val colors = ZankupTheme.colors
    val measurer = rememberTextMeasurer()
    val haptics = LocalHapticFeedback.current
    val labelStyle = TextStyle(color = colors.muted, fontSize = 11.sp)
    val select by rememberUpdatedState(onSelect)

    var from by remember { mutableStateOf(bars.map { 0f }) }
    var to by remember { mutableStateOf(bars.map { it.value }) }
    val grow = remember { Animatable(0f) }
    LaunchedEffect(bars.map { it.value }) {
        val now = if (from.size == to.size) from.zip(to) { a, b -> a + (b - a) * grow.value } else to
        from = if (now.size == bars.size) now else bars.map { 0f }
        to = bars.map { it.value }
        grow.snapTo(0f)
        grow.animateTo(1f, tween(500))
    }

    val axisWidth = 40.dp
    val bottomBand = 20.dp
    val total = bars.sumOf { it.weight.toDouble() }.toFloat().coerceAtLeast(1e-6f)

    Spacer(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
            .pointerInput(bars.size, total) {
                val left = axisWidth.toPx()
                fun barAt(px: Float): Int {
                    var acc = 0f
                    val share = ((px - left) / (size.width - left)).coerceIn(0f, 1f) * total
                    bars.forEachIndexed { i, bar ->
                        acc += bar.weight
                        if (share <= acc) return i
                    }
                    return bars.lastIndex
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var last = barAt(down.position.x)
                    select(last)
                    val drag =
                        awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                            ?: return@awaitEachGesture
                    horizontalDrag(drag.id) { change ->
                        val index = barAt(change.position.x)
                        if (index != last) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            last = index
                            select(index)
                        }
                        change.consume()
                    }
                }
            }.drawWithCache {
                val left = axisWidth.toPx()
                val bottom = size.height - bottomBand.toPx()
                val width = size.width - left
                // Upright, the axis starts at zero; inverted (a pace), it hugs the values with some room, so
                // the slowest bar still shows and the fastest is the tallest.
                val shown = to.filter { it > 0f }
                val axis =
                    if (inverted && shown.isNotEmpty()) {
                        val lo = shown.min().coerceAtMost(average ?: Float.MAX_VALUE)
                        val hi = shown.max().coerceAtLeast(average ?: 0f)
                        val pad = ((hi - lo) * 0.3f).coerceAtLeast(lo * 0.05f)
                        niceAxis((lo - pad).toDouble().coerceAtLeast(0.0), (hi + pad).toDouble())
                    } else {
                        niceAxis(0.0, (to.maxOrNull() ?: 0f).coerceAtLeast(average ?: 0f).toDouble().coerceAtLeast(1.0))
                    }
                val span = (axis.max - axis.min).toFloat()
                fun share(v: Float): Float {
                    val s = (v - axis.min.toFloat()) / span
                    return (if (inverted) 1f - s else s).coerceIn(0f, 1f)
                }
                val tickLabels = axis.ticks.map { it to measurer.measure(format(it.toFloat()), labelStyle) }
                val barLabels = bars.map { measurer.measure(it.label, labelStyle) }
                val gap = 3.dp.toPx()
                val radius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                onDrawBehind {
                    tickLabels.forEach { (tick, label) ->
                        val ty = bottom - share(tick.toFloat()) * bottom
                        drawLine(colors.line, Offset(left, ty), Offset(size.width, ty), 1f)
                        drawText(
                            label,
                            topLeft = Offset(
                                left - label.size.width - 6f,
                                (ty - label.size.height / 2f).coerceIn(
                                    0f,
                                    bottom - label.size.height
                                )
                            )
                        )
                    }
                    var x = left
                    bars.forEachIndexed { i, bar ->
                        val w = bar.weight / total * width

                        // The height slides, not the value: a bar grows from nothing to its own height,
                        // also on an inverted (pace) axis, where a value of zero would sit at the top.
                        fun heightOf(v: Float) = if (v > 0f) share(v) * bottom else 0f
                        val target = heightOf(to.getOrElse(i) { 0f })
                        val start = if (from.size == to.size) heightOf(from.getOrElse(i) { 0f }) else 0f
                        val h = start + (target - start) * grow.value
                        val tint = bar.color ?: color
                        val picked = selected == null || selected == i
                        drawRoundRect(
                            tint.copy(alpha = if (picked) 1f else 0.35f),
                            Offset(x + gap / 2f, bottom - h),
                            Size((w - gap).coerceAtLeast(1f), h),
                            radius
                        )
                        val label = barLabels[i]
                        if (label.size.width < w * 1.6f) {
                            drawText(label, topLeft = Offset(x + (w - label.size.width) / 2f, bottom + 4f))
                        }
                        x += w
                    }
                    average?.let {
                        val ay = bottom - share(it) * bottom
                        drawLine(
                            colors.fg,
                            Offset(left, ay),
                            Offset(size.width, ay),
                            1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                        )
                    }
                }
            }
    )
}
