package com.zankup.android.progress.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zankup.android.charts.ChartCursor
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.EffortRange
import com.zankup.android.progress.logic.ChartLabel
import com.zankup.android.progress.logic.EffortWeek
import com.zankup.android.progress.logic.MonthFigures
import com.zankup.android.progress.logic.fitnessScale
import com.zankup.android.progress.logic.loadRadius
import com.zankup.android.progress.logic.raceTime
import com.zankup.android.progress.logic.spreadDays
import com.zankup.android.progress.logic.timeTicks
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.roundToInt

private fun DrawScope.ring(center: Offset, r: Float, fill: Color, stroke: Color, width: Float) {
    drawCircle(fill, r, center)
    drawCircle(stroke, r, center, style = Stroke(width))
}

private fun DrawScope.halo(center: Offset, outer: Float, inner: Float, color: Color, alpha: Float) {
    drawCircle(color.copy(alpha = alpha), outer, center)
    drawCircle(color, inner, center)
}

/**
 * Volume over time as Strava's Progress draws it: a line over a solid fill, a ring on each point when
 * there are few, the axis on the left inside a frame and months (or days) along the bottom; optionally
 * the span before as a second line. With a [cursor], touch or a sideways slide picks a point (a rule
 * and a filled dot), read only while drawing. New values slide into place.
 */
@Composable
internal fun VolumeChart(
    values: List<Double>,
    ticks: List<Double>,
    tickLabel: (Double) -> String,
    labels: List<ChartLabel>,
    description: String,
    modifier: Modifier = Modifier,
    previous: List<Double>? = null,
    cursor: ChartCursor? = null,
    onPick: (Int) -> Unit = {},
    height: Int = 230
) {
    val colors = ZankupTheme.colors
    val measurer = rememberTextMeasurer()
    val haptics = LocalHapticFeedback.current
    val count = values.size
    val rings = count <= 60
    val morph = rememberMorph(values.map { it.toFloat() })
    val before = rememberMorph(previous.orEmpty().take(count).map { it.toFloat() })
    val pick by rememberUpdatedState(onPick)
    val style = TextStyle(color = colors.fg, fontSize = 12.sp)
    val max = (ticks.lastOrNull() ?: 1.0).takeIf { it > 0 } ?: 1.0
    val axis = 44.dp
    Spacer(
        modifier
            .fillMaxWidth()
            .height(height.dp)
            .semantics { contentDescription = description }
            .then(
                if (cursor != null) {
                    Modifier.pickInput(values) { px ->
                        val left = 4.dp.toPx()
                        val plot = size.width - axis.toPx() - left
                        val step = if (count > 1) plot / (count - 1) else 1f
                        val i = ((px - left) / step).roundToInt().coerceIn(0, (count - 1).coerceAtLeast(0))
                        if (count > 0 && i != cursor.index) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            cursor.index = i
                            pick(i)
                        }
                    }
                } else {
                    Modifier
                }
            ).drawWithCache {
                val axisW = axis.toPx()
                val left = 4.dp.toPx()
                val right = size.width - axisW
                val plot = (right - left).coerceAtLeast(0f)
                val top = 10.dp.toPx()
                val base = size.height - 26.dp.toPx()
                val step = if (count > 1) plot / (count - 1) else 0f
                fun x(i: Int) = left + i * step

                // The extreme values sit on the axis' own lines (top and bottom), not inset from them.
                fun y(v: Float) = base - (v.coerceAtMost(max.toFloat()) / max.toFloat()) * (base - top)
                val tickTexts = ticks.map { measurer.measure(tickLabel(it), style) }
                val labelTexts = labels.map { it to measurer.measure(it.text, style) }
                onDrawBehind {
                    ticks.forEachIndexed { k, v ->
                        val ty = y(v.toFloat())
                        drawLine(colors.line, Offset(left, ty), Offset(right, ty), 1f)
                        val t = tickTexts[k]
                        drawText(t, topLeft = Offset(size.width - t.size.width, ty - t.size.height / 2f))
                    }
                    labelTexts.forEach { (label, t) ->
                        val px = x(label.index)
                        drawLine(colors.line, Offset(px, top), Offset(px, base), 1f)
                        val lx = (
                            px - t.size.width / 2f
                            ).coerceIn(left, (right - t.size.width).coerceAtLeast(left))
                        drawText(t, topLeft = Offset(lx, base + 8.dp.toPx()))
                    }
                    if (count == 0) return@onDrawBehind
                    // The frame and the rule go under the line and its rings: nothing cuts them.
                    drawRect(
                        colors.line,
                        Offset(left, top),
                        Size(plot, base - top),
                        style = Stroke(1.5.dp.toPx())
                    )
                    val points = List(count) { Offset(x(it), y(morph.at(it))) }
                    drawPath(areaPath(points, base), colors.accent.copy(alpha = 0.3f))
                    if (previous != null && before.to.size > 1) {
                        val pts = List(before.to.size) { Offset(x(it), y(before.at(it))) }
                        drawPath(linePath(pts), colors.fatigue, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
                    }
                    drawPath(linePath(points), colors.accent, style = Stroke(3.dp.toPx(), join = StrokeJoin.Round))
                    val at = cursor?.index?.takeIf { it in 0 until count }
                    if (at != null) {
                        val p = points[at]
                        drawLine(colors.fg, Offset(p.x, top), Offset(p.x, base), 2.dp.toPx())
                    }
                    if (rings) points.forEach { ring(it, 4.5.dp.toPx(), colors.surface, colors.accent, 2.dp.toPx()) }
                    at?.let { index -> halo(points[index], 14.dp.toPx(), 7.dp.toPx(), colors.accent, 0.3f) }
                }
            }
    )
}

/**
 * Fitness as Strava draws it: the score as a filled line, a circle on each day with load (bigger,
 * harder) when [showLoads], and the picked day marked with its score in a badge. The [cursor] (-1 for
 * the last day) is read only while drawing.
 */
@Composable
internal fun FitnessChart(
    values: List<Double>,
    loads: List<Double>,
    labels: List<String>,
    cursor: ChartCursor,
    showLoads: Boolean,
    description: String,
    modifier: Modifier = Modifier,
    onPick: (Int) -> Unit = {},
    height: Int = 360
) {
    val colors = ZankupTheme.colors
    val measurer = rememberTextMeasurer()
    val haptics = LocalHapticFeedback.current
    val count = values.size
    val ticks = remember(values) { fitnessScale(values) }
    val max = ticks.last().toFloat().takeIf { it > 0 } ?: 1f
    val morph = rememberMorph(values.map { it.toFloat() })
    val pick by rememberUpdatedState(onPick)
    val muted = TextStyle(color = colors.muted, fontSize = 11.sp)
    val badgeStyle = TextStyle(color = colors.accentFg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    val formats = com.zankup.android.ui.LocalFormats.current
    Spacer(
        modifier
            .fillMaxWidth()
            .height(height.dp)
            .semantics { contentDescription = description }
            .pickInput(values) { px ->
                val axis = 34.dp.toPx()
                val plot = size.width - axis - 22.dp.toPx()
                val step = if (count > 1) plot / (count - 1) else 1f
                val i = ((px - axis) / step).roundToInt().coerceIn(0, (count - 1).coerceAtLeast(0))
                val now = if (cursor.index < 0) count - 1 else cursor.index
                if (count > 0 && i != now) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    cursor.index = i
                    pick(i)
                }
            }.drawWithCache {
                val axis = 34.dp.toPx()
                val plot = (size.width - axis - 22.dp.toPx()).coerceAtLeast(0f)
                val top = 40.dp.toPx()
                val base = size.height - 22.dp.toPx()
                val inner = base - top
                val step = if (count > 1) plot / (count - 1) else 0f
                fun x(i: Int) = axis + i * step
                fun y(v: Float) = base - v / max * inner
                val tickTexts = ticks.map {
                    measurer.measure(com.zankup.android.progress.logic.shortNumber(it, formats.locale), muted)
                }
                val dayTexts = spreadDays(count).map { it to measurer.measure(labels.getOrElse(it) { "" }, muted) }
                onDrawBehind {
                    ticks.forEachIndexed { k, v ->
                        val ty = y(v.toFloat()).roundToInt() - 0.5f
                        drawLine(
                            colors.line.copy(alpha = if (v == 0.0) 1f else 0.5f),
                            Offset(axis, ty),
                            Offset(axis + plot, ty),
                            1f
                        )
                        val t = tickTexts[k]
                        drawText(t, topLeft = Offset(axis - 8.dp.toPx() - t.size.width, ty - t.size.height / 2f))
                    }
                    dayTexts.forEach { (i, t) ->
                        val lx = (x(i) - t.size.width / 2f).coerceIn(axis - 16.dp.toPx(), size.width - t.size.width)
                        drawText(t, topLeft = Offset(lx, base + 6.dp.toPx()))
                    }
                    if (count == 0) return@onDrawBehind
                    val points = List(count) { Offset(x(it), y(morph.at(it))) }
                    drawPath(areaPath(points, base), colors.accent.copy(alpha = 0.22f))
                    drawPath(
                        linePath(points),
                        colors.accent,
                        style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    // The picked day's rule first, so its ring is not cut by it.
                    val at = (if (cursor.index < 0) count - 1 else cursor.index).coerceIn(0, count - 1)
                    val p = points[at]
                    drawLine(colors.accent, Offset(p.x, max(0f, p.y - 24.dp.toPx())), Offset(p.x, base), 1.5.dp.toPx())
                    if (showLoads) {
                        points.forEachIndexed { i, point ->
                            val r = loadRadius(loads.getOrElse(i) { 0.0 })
                            if (r > 0) ring(point, r.dp.toPx(), colors.bg, colors.accent, 1.5.dp.toPx())
                        }
                    }
                    halo(p, 15.dp.toPx(), 7.dp.toPx(), colors.accent, 0.25f)
                    val badge = measurer.measure(values[at].roundToInt().toString(), badgeStyle)
                    val w = 48.dp.toPx()
                    val h = badge.size.height + 8.dp.toPx()
                    val bx = (p.x - w / 2).coerceIn(0f, size.width - w)
                    val by = max(0f, p.y - 58.dp.toPx())
                    drawRoundRect(colors.accent, Offset(bx, by), Size(w, h), CornerRadius(6.dp.toPx()))
                    drawText(badge, topLeft = Offset(bx + (w - badge.size.width) / 2, by + 4.dp.toPx()))
                }
            }
    )
}

/**
 * The card's small line: where the range started (its value written before it when [first] is
 * given) and today, marked. With [inverted], lower is higher (a race time: faster is higher).
 */
@Composable
internal fun Sparkline(
    values: List<Double>,
    modifier: Modifier = Modifier,
    first: String? = null,
    inverted: Boolean = false,
    width: Int = 150,
    height: Int = 84,
    fill: Boolean = true,
    guide: Boolean = true,
    left: Int = 30
) {
    val colors = ZankupTheme.colors
    val measurer = rememberTextMeasurer()
    val style = TextStyle(color = colors.muted, fontSize = 12.sp)
    if (values.isEmpty()) return
    Spacer(
        modifier.size(width.dp, height.dp)
            .drawWithCache {
                val l = (if (first != null) left else 6).dp.toPx()
                val pad = (if (inverted) 12 else 14).dp.toPx()
                val lo = values.min()
                val hi = values.max()
                fun y(v: Double): Float {
                    val span = hi - lo
                    if (inverted) {
                        return if (span >
                            0
                        ) {
                            (pad + (v - lo) / span * (size.height - 2 * pad)).toFloat()
                        } else {
                            size.height / 2
                        }
                    }
                    return (pad + (1 - (v - lo) / (if (span > 0) span else 1.0)) * (size.height - 2 * pad)).toFloat()
                }
                val step = if (values.size > 1) (size.width - l - pad) / (values.size - 1) else 0f
                val points = values.mapIndexed { i, v -> Offset(l + i * step, y(v)) }
                val label = first?.let { measurer.measure(it, style) }
                onDrawBehind {
                    val a = points.first()
                    val b = points.last()
                    if (fill) drawPath(areaPath(points, size.height), colors.accent.copy(alpha = 0.15f))
                    if (guide) drawLine(colors.muted.copy(alpha = 0.6f), Offset(l, b.y), Offset(b.x, b.y), 1f)
                    drawPath(
                        linePath(points),
                        colors.accent,
                        style = Stroke((if (inverted) 3f else 2.5f).dp.toPx(), join = StrokeJoin.Round)
                    )
                    ring(a, 5.dp.toPx(), colors.surface, colors.accent, 2.5.dp.toPx())
                    halo(
                        b,
                        (if (inverted) 12f else 11f).dp.toPx(),
                        (if (inverted) 6f else 5.5f).dp.toPx(),
                        colors.accent,
                        0.25f
                    )
                    if (label != null) {
                        drawText(
                            label,
                            topLeft = Offset(
                                l - 8.dp.toPx() - label.size.width,
                                a.y - label.size.height / 2f
                            )
                        )
                    }
                }
            }
    )
}

/**
 * A prediction over a range: faster is higher, a ring where it changed, today filled, times on the
 * right and dates under it. Another race or range slides into place.
 */
@Composable
internal fun PredictionChart(
    points: List<com.zankup.android.progress.logic.Point>,
    from: LocalDate,
    to: LocalDate,
    ticks: List<Double>,
    labels: List<Pair<LocalDate, String>>,
    modifier: Modifier = Modifier,
    height: Int = 300
) {
    val colors = ZankupTheme.colors
    val measurer = rememberTextMeasurer()
    val morph = rememberMorph(points.map { it.value.toFloat() })
    val span = ChronoUnit.DAYS.between(from, to).toFloat().takeIf { it > 0 } ?: 1f
    val days = points.map { ChronoUnit.DAYS.between(from, it.day).toFloat() }
    val tickStyle = TextStyle(color = colors.fg, fontSize = 13.sp)
    val labelStyle = TextStyle(color = colors.fg, fontSize = 12.sp)
    Spacer(
        modifier.fillMaxWidth().height(height.dp).drawWithCache {
            val plot = (size.width - 84.dp.toPx()).coerceAtLeast(0f)
            val top = 10.dp.toPx()
            val base = size.height - 30.dp.toPx()
            val lo = ticks.firstOrNull()?.toFloat() ?: 0f
            val hi = ticks.lastOrNull()?.toFloat() ?: 1f
            fun x(d: Float) = 8.dp.toPx() + d / span * (plot - 16.dp.toPx())
            val tickTexts = ticks.map { measurer.measure(raceTime(it), tickStyle) }
            val labelTexts = labels.map { (d, text) ->
                ChronoUnit.DAYS.between(from, d).toFloat() to measurer.measure(text, labelStyle)
            }
            onDrawBehind {
                // Before the morph effect has taken the new values, draw the old line on its own scale:
                // otherwise that first frame maps it against the new ticks and it snaps to 100 %.
                val current = points.map { it.value.toFloat() }
                val settled = morph.to == current
                val t = if (settled) morph.progress.value.coerceIn(0f, 1f) else 0f
                val old = if (morph.from.isEmpty()) {
                    lo to hi
                } else {
                    val oldTicks = timeTicks(morph.from.min().toDouble(), morph.from.max().toDouble())
                    (oldTicks.firstOrNull()?.toFloat() ?: lo) to (oldTicks.lastOrNull()?.toFloat() ?: hi)
                }
                val low = old.first + (lo - old.first) * t
                val high = old.second + (hi - old.second) * t
                fun y(v: Float) = top + (v - low) / (if (high > low) high - low else 1f) * (base - top)
                ticks.forEachIndexed { k, v ->
                    val ty = y(v.toFloat())
                    drawLine(colors.line, Offset(0f, ty), Offset(plot, ty), 1f)
                    val text = tickTexts[k]
                    drawText(text, topLeft = Offset(size.width - text.size.width, ty - text.size.height / 2f))
                }
                labelTexts.forEach { (d, text) ->
                    drawText(
                        text,
                        topLeft = Offset(max(0f, x(d) - text.size.width / 2f), base + 8.dp.toPx())
                    )
                }
                if (points.isEmpty()) return@onDrawBehind
                val at = points.indices.map { Offset(x(days[it]), y(morph.at(it))) }
                drawPath(smoothPath(at), colors.accent, style = Stroke(3.dp.toPx(), join = StrokeJoin.Round))
                at.dropLast(1).forEach { ring(it, 5.dp.toPx(), colors.bg, colors.accent, 2.dp.toPx()) }
                halo(at.last(), 14.dp.toPx(), 7.dp.toPx(), colors.accent, 0.25f)
            }
        }
    )
}

/**
 * Relative effort's two weeks added up day by day over the range band, today marked, the days written
 * under: last week in grey, this week in its status colour.
 */
@Composable
internal fun WeekSoFar(
    thisWeek: List<Double>,
    lastWeek: List<Double>,
    range: EffortRange?,
    color: Color,
    letters: List<String>,
    today: Int,
    modifier: Modifier = Modifier
) {
    val colors = ZankupTheme.colors
    val measurer = rememberTextMeasurer()
    val now = com.zankup.android.domain.cumulative(thisWeek, today)
    val before = com.zankup.android.domain.cumulative(lastWeek)
    val muted = TextStyle(color = colors.muted, fontSize = 12.sp)
    val strong = TextStyle(color = colors.fg, fontSize = 12.sp)
    val most = listOfNotNull(1.0, range?.hi?.toDouble(), before.lastOrNull(), now.lastOrNull()).max() * 1.1
    Spacer(
        modifier.size((132 + 52).dp, 104.dp)
            .drawWithCache {
                val left = 52.dp.toPx()
                val width = size.width - left
                val plot = width - 8.dp.toPx()
                val top = 8.dp.toPx()
                val base = size.height - 24.dp.toPx()
                val most = listOfNotNull(1.0, range?.hi?.toDouble(), before.lastOrNull(), now.lastOrNull()).max() * 1.1
                fun x(i: Int) = left + 4.dp.toPx() + i / 6f * (plot - 8.dp.toPx())
                fun y(v: Double) = (base - v / most * (base - top)).toFloat()
                val rangeText = range?.let { measurer.measure("${it.lo}-${it.hi}", muted) }
                val letterTexts = letters.mapIndexed { i, l -> measurer.measure(l, if (i == today) strong else muted) }
                onDrawBehind {
                    drawLine(colors.line, Offset(left, top), Offset(left + plot, top), 1f)
                    drawLine(colors.line, Offset(left, base), Offset(left + plot, base), 1f)
                    if (range != null) {
                        val a = y(range.hi.toDouble())
                        val b = y(range.lo.toDouble())
                        drawRect(color.copy(alpha = 0.18f), Offset(left, a), Size(plot, b - a))
                        rangeText?.let {
                            drawText(
                                it,
                                topLeft = Offset(
                                    left - 6.dp.toPx() - it.size.width,
                                    (a + b) / 2 - it.size.height / 2
                                )
                            )
                        }
                    }
                    if (before.isNotEmpty()) {
                        drawPath(
                            linePath(before.mapIndexed { i, v -> Offset(x(i), y(v)) }),
                            colors.muted,
                            style = Stroke(2.5.dp.toPx(), join = StrokeJoin.Round)
                        )
                    }
                    if (now.isNotEmpty()) {
                        val pts = now.mapIndexed { i, v -> Offset(x(i), y(v)) }
                        drawPath(linePath(pts), color, style = Stroke(3.dp.toPx(), join = StrokeJoin.Round))
                        ring(pts.first(), 5.dp.toPx(), colors.surface, color, 2.5.dp.toPx())
                        halo(pts.last(), 12.dp.toPx(), 6.dp.toPx(), color, 0.25f)
                    }
                    letterTexts.forEachIndexed { i, t ->
                        drawText(t, topLeft = Offset(x(i) - t.size.width / 2f, base + 6.dp.toPx()))
                    }
                }
            }
    )
}

/** This month's time adding up day by day (in the accent, to today, ending in a dot) over last month's. */
@Composable
internal fun MonthChart(
    current: MonthFigures,
    previous: MonthFigures,
    modifier: Modifier = Modifier,
    height: Int = 90
) {
    val colors = ZankupTheme.colors
    Spacer(
        modifier.fillMaxWidth().height(height.dp).drawWithCache {
            val pad = 10.dp.toPx()
            val slots = (max(current.month.lengthOfMonth(), previous.month.lengthOfMonth()) - 1).toFloat()
            val most = listOf(previous.time, current.time, 1.0).max()
            fun x(i: Int) = pad + i / slots * (size.width - 2 * pad)
            fun y(v: Double) = (pad + (1 - v / most) * (size.height - 2 * pad)).toFloat()
            val prev = linePath(previous.cumulative.mapIndexed { i, v -> Offset(x(i), y(v)) })
            val now = current.cumulative.mapIndexed { i, v -> Offset(x(i), y(v)) }
            onDrawBehind {
                drawPath(prev, colors.muted, style = Stroke(2.5.dp.toPx()))
                if (now.isNotEmpty()) {
                    drawPath(linePath(now), colors.accent, style = Stroke(2.5.dp.toPx(), join = StrokeJoin.Round))
                    halo(now.last(), 18.dp.toPx(), 8.dp.toPx(), colors.accent, 0.25f)
                }
            }
        }
    )
}

/** Twelve weeks: their ranges as a band, their scores as rings, the picked one filled in its colour. */
@Composable
internal fun EffortWeeksChart(
    weeks: List<EffortWeek>,
    picked: Int,
    color: Color,
    labels: List<ChartLabel>,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Int = 260
) {
    val colors = ZankupTheme.colors
    val measurer = rememberTextMeasurer()
    val pick by rememberUpdatedState(onPick)
    val count = weeks.size
    val most = max(1, weeks.maxOfOrNull { max(it.score, it.range?.hi ?: 0) } ?: 0)
    val style = TextStyle(color = colors.fg, fontSize = 13.sp)
    Spacer(
        modifier
            .fillMaxWidth()
            .height(height.dp)
            .pickInput(weeks) { px ->
                val plot = size.width - 44.dp.toPx()
                val step = if (count > 1) plot / (count - 0.5f) else 1f
                pick((px / step - 0.5f).roundToInt().coerceIn(0, (count - 1).coerceAtLeast(0)))
            }.drawWithCache {
                val top = 34.dp.toPx()
                val base = size.height - 34.dp.toPx()
                val plot = (size.width - 44.dp.toPx()).coerceAtLeast(0f)
                val step = if (count > 1) plot / (count - 0.5f) else 0f
                fun x(i: Int) = step * (i + 0.5f)
                fun y(v: Int) = base - v.toFloat() / most * (base - top)
                val banded = weeks.mapIndexedNotNull { i, w -> w.range?.let { i to it } }
                val band = androidx.compose.ui.graphics.Path().apply {
                    banded.forEachIndexed { k, (i, r) -> if (k == 0) moveTo(x(i), y(r.hi)) else lineTo(x(i), y(r.hi)) }
                    banded.reversed().forEach { (i, r) -> lineTo(x(i), y(r.lo)) }
                    close()
                }
                val mostText = measurer.measure(most.toString(), style)
                val labelTexts = labels.map { it.index to measurer.measure(it.text, style) }
                onDrawBehind {
                    weeks.indices.forEach { drawLine(colors.line, Offset(x(it), top), Offset(x(it), base), 1f) }
                    if (banded.isNotEmpty()) drawPath(band, colors.muted.copy(alpha = 0.35f))
                    if (picked in weeks.indices) {
                        drawLine(color, Offset(x(picked), 6.dp.toPx()), Offset(x(picked), base), 1.5.dp.toPx())
                    }
                    weeks.forEachIndexed { i, w ->
                        if (i !=
                            picked
                        ) {
                            drawCircle(
                                colors.fg,
                                5.dp.toPx(),
                                Offset(x(i), y(w.score)),
                                style = Stroke(2.dp.toPx())
                            )
                        }
                    }
                    weeks.getOrNull(picked)?.let {
                        halo(Offset(x(picked), y(it.score)), 16.dp.toPx(), 8.dp.toPx(), color, 0.3f)
                    }
                    listOf(0.25f, 0.5f, 0.75f, 1f).forEach { f ->
                        val ty = base - f * (base - top)
                        drawLine(colors.muted, Offset(plot + 16.dp.toPx(), ty), Offset(plot + 28.dp.toPx(), ty), 1f)
                    }
                    drawText(mostText, topLeft = Offset(size.width - mostText.size.width, top - 22.dp.toPx()))
                    labelTexts.forEach { (i, t) ->
                        drawText(t, topLeft = Offset(x(i) - 6.dp.toPx(), base + 8.dp.toPx()))
                    }
                }
            }
    )
}
