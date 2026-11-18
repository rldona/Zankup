package com.zankup.android.charts

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.niceAxis
import kotlin.math.ln

/** One curve: the best value for each duration (seconds → watts), and its colour. */
@Immutable
data class Curve(val label: String, val points: List<Pair<Float, Float>>, val color: Color)

/**
 * The power curve: the best average for every duration, on a logarithmic time axis (1 s to hours), with
 * this activity's curve against the all-time one. A finger picks a duration and reads both.
 */
@Composable
fun CurveChart(
    curves: List<Curve>,
    format: (Float) -> String,
    durationLabel: (Float) -> String,
    modifier: Modifier = Modifier,
    marks: List<Float> = listOf(1f, 5f, 30f, 60f, 300f, 1200f, 3600f),
    height: Dp = 180.dp
) {
    val colors = ZankupTheme.colors
    val measurer = rememberTextMeasurer()
    val haptics = LocalHapticFeedback.current
    val labelStyle = TextStyle(color = colors.muted, fontSize = 11.sp)
    val readout = TextStyle(color = colors.bg, fontSize = 12.sp)
    val durations = curves.flatMap { c -> c.points.map { it.first } }.distinct().sorted()
    var picked by remember(durations) { mutableIntStateOf(-1) }
    val axisWidth = 40.dp
    val bottomBand = 20.dp
    val lo = ln(durations.firstOrNull()?.coerceAtLeast(1f) ?: 1f)
    val hi = ln(durations.lastOrNull()?.coerceAtLeast(2f) ?: 2f)

    Spacer(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(durations) {
                val left = axisWidth.toPx()
                fun at(px: Float): Int {
                    val share = ((px - left) / (size.width - left)).coerceIn(0f, 1f)
                    val target = lo + share * (hi - lo)
                    return durations.indices.minByOrNull { kotlin.math.abs(ln(durations[it]) - target) } ?: -1
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    picked = at(down.position.x)
                    val drag =
                        awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                            ?: return@awaitEachGesture
                    horizontalDrag(drag.id) { change ->
                        val now = at(change.position.x)
                        if (now != picked) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                        picked = now
                        change.consume()
                    }
                }
            }.drawWithCache {
                val left = axisWidth.toPx()
                val bottom = size.height - bottomBand.toPx()
                val width = size.width - left
                val top = curves.flatMap { c -> c.points.map { it.second } }.maxOrNull() ?: 1f
                val axis = niceAxis(0.0, top.toDouble().coerceAtLeast(1.0))
                fun x(d: Float) = left + (ln(d.coerceAtLeast(1f)) - lo) / (hi - lo).coerceAtLeast(1e-6f) * width
                fun y(v: Float) = bottom - (v / axis.max.toFloat()) * bottom
                val paths =
                    curves.map { curve ->
                        curve to
                            Path().apply {
                                curve.points.sortedBy { it.first }.forEachIndexed { i, (d, v) ->
                                    if (i == 0) moveTo(x(d), y(v)) else lineTo(x(d), y(v))
                                }
                            }
                    }
                val ticks = axis.ticks.map { it to measurer.measure(format(it.toFloat()), labelStyle) }
                val markLabels = marks.filter {
                    it in (durations.firstOrNull() ?: 0f)..(durations.lastOrNull() ?: 0f)
                }.map {
                    it to
                        measurer.measure(durationLabel(it), labelStyle)
                }
                onDrawBehind {
                    ticks.forEach { (tick, label) ->
                        val ty = y(tick.toFloat())
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
                    markLabels.forEach { (d, label) ->
                        drawText(
                            label,
                            topLeft = Offset(
                                (x(d) - label.size.width / 2f).coerceIn(
                                    left,
                                    size.width - label.size.width
                                ),
                                bottom + 4f
                            )
                        )
                    }
                    paths.forEach { (curve, path) -> drawPath(path, curve.color, style = Stroke(width = 2.dp.toPx())) }
                    val index = picked
                    if (index in durations.indices) {
                        val d = durations[index]
                        val cx = x(d)
                        drawLine(colors.fg.copy(alpha = 0.6f), Offset(cx, 0f), Offset(cx, bottom), 1.5f)
                        val parts =
                            curves.mapNotNull { c ->
                                c.points.firstOrNull { it.first == d }?.let { (_, v) ->
                                    drawCircle(c.color, 4.dp.toPx(), Offset(cx, y(v)))
                                    format(v)
                                }
                            }
                        val label = measurer.measure("${durationLabel(d)} · ${parts.joinToString(" / ")}", readout)
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
