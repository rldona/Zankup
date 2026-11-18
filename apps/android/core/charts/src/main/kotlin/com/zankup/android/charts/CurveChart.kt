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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.tightAxis
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
    height: Dp = 180.dp,
    /** How the axis writes a value (without its unit, which [unitLabel] puts at the bottom). */
    axisFormat: (Float) -> String = format,
    /** The axis' unit, written in place of its lowest label ("W"). */
    unitLabel: String? = null
) {
    val colors = ZankupTheme.colors
    val measurer = rememberTextMeasurer()
    val haptics = LocalHapticFeedback.current
    val labelStyle = TextStyle(color = colors.fg, fontSize = 13.sp)
    val valueStyle = TextStyle(color = Color(0xFF16181D), fontSize = 19.sp, fontWeight = FontWeight.Bold)
    val whereStyle = TextStyle(color = Color(0xFF16181D), fontSize = 14.sp)
    val durations = curves.flatMap { c -> c.points.map { it.first } }.distinct().sorted()
    var picked by remember(durations) { mutableIntStateOf(-1) }
    val axisWidth = 44.dp
    val bottomBand = 28.dp
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
                val right = size.width - 1f
                val bottom = size.height - bottomBand.toPx()
                val width = right - left
                val all = curves.flatMap { c -> c.points.map { it.second } }
                // The axis hugs the values, as Strava's: the long durations fill the chart too.
                val axis = tightAxis((all.minOrNull() ?: 0f).toDouble(), (all.maxOrNull() ?: 1f).toDouble())
                fun x(d: Float) = left + (ln(d.coerceAtLeast(1f)) - lo) / (hi - lo).coerceAtLeast(1e-6f) * width
                fun y(v: Float): Float {
                    val share = ((v - axis.min) / (axis.max - axis.min).coerceAtLeast(1e-9)).toFloat()
                    return bottom - share.coerceIn(0f, 1f) * bottom
                }
                val paths =
                    curves.map { curve ->
                        curve to
                            Path().apply {
                                curve.points.sortedBy { it.first }.forEachIndexed { i, (d, v) ->
                                    if (i == 0) moveTo(x(d), y(v)) else lineTo(x(d), y(v))
                                }
                            }
                    }
                val grid = Color(0xFF4A4A48)
                val ticks =
                    axis.ticks.filter { y(it.toFloat()) < bottom - 2f }
                        .map { it to measurer.measure(axisFormat(it.toFloat()), labelStyle) }
                val unitText = unitLabel?.let { measurer.measure(it, labelStyle) }
                val markLabels = marks.filter {
                    it in (durations.firstOrNull() ?: 0f)..(durations.lastOrNull() ?: 0f)
                }.map { it to measurer.measure(durationLabel(it), labelStyle) }
                onDrawBehind {
                    // The grid: a line at each round value and each duration mark, inside a frame.
                    ticks.forEach { (tick, label) ->
                        val ty = y(tick.toFloat())
                        drawLine(grid, Offset(left, ty), Offset(right, ty), 1.5f)
                        drawText(label, topLeft = Offset(left - label.size.width - 8f, ty - label.size.height / 2f))
                    }
                    unitText?.let {
                        drawText(it, topLeft = Offset(left - it.size.width - 8f, bottom - it.size.height / 2f))
                    }
                    markLabels.forEach { (d, label) ->
                        val mx = x(d)
                        drawLine(grid, Offset(mx, 0f), Offset(mx, bottom + 4.dp.toPx()), 1.5f)
                        val lx = (mx - label.size.width / 2f).coerceIn(left, size.width - label.size.width)
                        drawText(label, topLeft = Offset(lx, bottom + 8.dp.toPx()))
                    }
                    drawRect(grid, Offset(left, 0f), Size(width, bottom), style = Stroke(1.5f))
                    paths.forEach { (curve, path) ->
                        drawPath(
                            path,
                            curve.color,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                    // The pick: a bubble in the curve's colour above the chart with the value and the
                    // duration, a white line down from it and a dot on each curve.
                    val index = picked
                    if (index in durations.indices) {
                        val d = durations[index]
                        val cx = x(d)
                        val found = curves.mapNotNull { c ->
                            c.points.firstOrNull { it.first == d }?.let {
                                c to
                                    it.second
                            }
                        }
                        // The last curve is the one on top (this activity): its colour and its value first.
                        val tint = found.lastOrNull()?.first?.color ?: colors.accent
                        val value = measurer.measure(
                            found.reversed().joinToString(" / ") {
                                format(it.second)
                            },
                            valueStyle
                        )
                        val where = measurer.measure(durationLabel(d), whereStyle)
                        val padX = 12.dp.toPx()
                        val padY = 8.dp.toPx()
                        val w = maxOf(value.size.width, where.size.width) + padX * 2
                        val h = value.size.height + where.size.height + padY * 2
                        val bx = (cx - w / 2f).coerceIn(0f, size.width - w)
                        val by = -h - 6.dp.toPx()
                        drawLine(Color.White, Offset(cx, by + h), Offset(cx, bottom), 2.dp.toPx())
                        drawRoundRect(tint, Offset(bx, by), Size(w, h), CornerRadius(10.dp.toPx()))
                        drawText(value, topLeft = Offset(bx + padX, by + padY))
                        drawText(where, topLeft = Offset(bx + padX, by + padY + value.size.height))
                        found.forEach { (_, v) ->
                            drawCircle(Color.White.copy(alpha = 0.3f), 9.dp.toPx(), Offset(cx, y(v)))
                            drawCircle(Color.White, 5.dp.toPx(), Offset(cx, y(v)))
                        }
                    }
                }
            }
    )
}
