package com.zankup.android.activity

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.theme.Numbers
import com.zankup.android.designsystem.theme.ZankupTheme
import kotlin.math.roundToInt

/** Strava's zone scales (packages/core tokens), the same on every theme. */
internal val HR_SCALE = listOf(0xFFFCD0D1, 0xFFF3A4A3, 0xFFEE7675, 0xFFEB4748, 0xFFDE2625).map(::Color)
internal val PACE_SCALE = listOf(0xFFCCE0F2, 0xFF99C0EC, 0xFF66A0E1, 0xFF327FD9, 0xFF0160CF, 0xFF004DA7).map(::Color)
internal val POWER_SCALE =
    listOf(0xFFE8CFF9, 0xFFD29EF1, 0xFFBC6EED, 0xFFA63DE7, 0xFF910DE2, 0xFF730AB2, 0xFF550886).map(::Color)

/** Medals' gold, silver and bronze. */
internal val MEDAL_COLOURS = listOf(0xFFE8B10E, 0xFF8F9EA6, 0xFFC06D3D).map(::Color)

/** `count` colours of a scale, light to dark: all of them, or spread over it. */
internal fun scale(colours: List<Color>, count: Int): List<Color> = if (count == colours.size) {
    colours
} else {
    List(count) { i -> colours[(i * (colours.size - 1).toFloat() / (count - 1).coerceAtLeast(1)).roundToInt()] }
}

/** A small line in the accent: the first value ringed (and written before it with [first]), the last haloed. */
@Composable
internal fun Sparkline(values: List<Double>, first: Boolean, modifier: Modifier = Modifier) {
    val colors = ZankupTheme.colors
    val measurer = rememberTextMeasurer()
    val style = TextStyle(color = colors.muted, fontSize = 12.sp).merge(Numbers.tabular)
    if (values.size < 2) return
    Spacer(
        modifier.size(120.dp, 64.dp).drawWithCache {
            val left = (if (first) 30 else 6).dp.toPx()
            val pad = 14.dp.toPx()
            val lo = values.min()
            val hi = values.max()
            val span = (hi - lo).takeIf { it > 0 } ?: 1.0
            val step = (size.width - left - pad) / (values.size - 1)
            val points =
                values.mapIndexed { i, v ->
                    Offset(
                        left + i * step,
                        (
                            pad +
                                (1 - (v - lo) / span) * (size.height - 2 * pad)
                            ).toFloat()
                    )
                }
            val line = Path().apply {
                points.forEachIndexed { i, p ->
                    if (i ==
                        0
                    ) {
                        moveTo(p.x, p.y)
                    } else {
                        lineTo(p.x, p.y)
                    }
                }
            }
            val area =
                Path().apply {
                    addPath(line)
                    lineTo(points.last().x, size.height)
                    lineTo(points.first().x, size.height)
                    close()
                }
            val label = if (first) measurer.measure("${values.first().roundToInt()}", style) else null
            onDrawBehind {
                val a = points.first()
                val b = points.last()
                drawPath(area, colors.accent.copy(alpha = 0.15f))
                drawLine(colors.muted.copy(alpha = 0.6f), Offset(left, b.y), b, 1.dp.toPx())
                drawPath(line, colors.accent, style = Stroke(2.5.dp.toPx(), join = StrokeJoin.Round))
                drawCircle(colors.surface, 5.dp.toPx(), a)
                drawCircle(colors.accent, 5.dp.toPx(), a, style = Stroke(2.5.dp.toPx()))
                drawCircle(colors.accent.copy(alpha = 0.25f), 11.dp.toPx(), b)
                drawCircle(colors.accent, 5.5.dp.toPx(), b)
                label?.let {
                    drawText(it, topLeft = Offset(left - 8.dp.toPx() - it.size.width, a.y - it.size.height / 2f))
                }
            }
        }
    )
}

/** A goal's ring, filling clockwise from twelve o'clock, with its sport in the middle. */
@Composable
internal fun GoalRing(fraction: Float, icon: ZIcon, size: Dp = 48.dp) {
    val colors = ZankupTheme.colors
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = this.size.width * 0.09f
            val inset = stroke / 2 + 1.dp.toPx()
            val box = Size(this.size.width - 2 * inset, this.size.height - 2 * inset)
            val at = Offset(inset, inset)
            drawArc(colors.muted.copy(alpha = 0.35f), 0f, 360f, false, at, box, style = Stroke(stroke))
            if (fraction > 0f) {
                drawArc(
                    colors.accent,
                    -90f,
                    360f * fraction,
                    false,
                    at,
                    box,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }
        ZankupIcon(icon, contentDescription = null, tint = colors.fg, size = size * 0.4f)
    }
}

/** A medal with its place on it, as Strava's: the first is "PR". */
@Composable
internal fun Medal(rank: Int, size: Dp = 36.dp) {
    val colors = ZankupTheme.colors
    Box(Modifier.size(size), contentAlignment = Alignment.BottomCenter) {
        ZankupIcon(
            ZIcon.Medal,
            contentDescription = null,
            filled = true,
            tint = MEDAL_COLOURS.getOrElse(rank - 1) { colors.muted },
            size = size
        )
        Text(
            if (rank == 1) "PR" else "$rank",
            color = colors.bg,
            fontSize = (size.value * if (rank == 1) 0.2f else 0.26f).sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(bottom = size * 0.12f)
        )
    }
}

/**
 * The splits as Strava's workout analysis: a bar per split as wide as its distance and taller the faster,
 * the two fastest darker, the elevation over them in see-through grey and the average dashed across; the
 * pace on the left.
 */
@Composable
internal fun SplitsChart(
    speeds: List<Double>,
    widths: List<Double>,
    elevation: FloatArray?,
    axis: (Double) -> String,
    unitLabel: String,
    height: Dp = 180.dp
) {
    val colors = ZankupTheme.colors
    val measurer = rememberTextMeasurer()
    val style = TextStyle(color = colors.fg, fontSize = 12.sp).merge(Numbers.tabular)
    val usable = speeds.filter { it > 0 }
    if (usable.isEmpty()) return
    val lo = usable.min() * 0.9
    val hi = usable.max()
    val total = widths.sum()
    val time = speeds.indices.sumOf { if (speeds[it] > 0) widths[it] / speeds[it] else 0.0 }
    val mean = if (time > 0) total / time else 0.0
    val fast = speeds.withIndex().sortedByDescending { it.value }.take(2).map { it.index }.toSet()
    Spacer(
        Modifier.fillMaxWidth().height(height).drawWithCache {
            val left = 46.dp.toPx()
            val gap = 4.dp.toPx()
            val h = size.height
            val plot = size.width - left
            val spread = (hi - lo).takeIf { it > 0 } ?: 1.0
            val floor = 12.dp.toPx()
            fun barHeight(v: Double) = if (v > 0) (floor + (v - lo) / spread * (h - floor)).toFloat() else 4.dp.toPx()
            val share = total / speeds.size
            val weights = widths.map { maxOf(0.2, it / share) }
            val unitWidth = (plot - gap * (speeds.size - 1)) / weights.sum()
            val ticks = listOf(0.25, 0.5, 0.75, 1.0).map { f ->
                val v = lo + f * (hi - lo)
                measurer.measure(axis(v), style) to (h - barHeight(v))
            }
            val unit = measurer.measure(unitLabel, style)
            val alts = elevation?.filter { it.isFinite() }.orEmpty()
            val area =
                if (alts.size > 1 && elevation != null) {
                    val aLo = alts.min()
                    val span = (alts.max() - aLo).takeIf { it > 0f } ?: 1f
                    Path().apply {
                        elevation.forEachIndexed { k, v ->
                            val x = left + k.toFloat() / (elevation.size - 1) * plot
                            val y = h - 6.dp.toPx() - ((if (v.isFinite()) v else aLo) - aLo) / span * (h * 0.85f)
                            if (k == 0) moveTo(x, y) else lineTo(x, y)
                        }
                        lineTo(size.width, h)
                        lineTo(left, h)
                        close()
                    }
                } else {
                    null
                }
            onDrawBehind {
                var x = left
                speeds.forEachIndexed { i, v ->
                    val w = (weights[i] * unitWidth).toFloat()
                    val bh = barHeight(v)
                    val r = minOf(8.dp.toPx(), w / 2)
                    val colour = if (i in fast) PACE_SCALE[3] else PACE_SCALE[2]
                    drawRoundRect(colour, Offset(x, h - bh), Size(w, bh + r), CornerRadius(r, r))
                    x += w + gap
                }
                area?.let { drawPath(it, Color(0xFF3A3A3A).copy(alpha = 0.6f)) }
                if (mean > 0) {
                    val y = h - barHeight(mean)
                    drawLine(
                        colors.fg,
                        Offset(left, y),
                        Offset(size.width, y),
                        1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                    )
                }
                ticks.forEach { (label, y) -> drawText(label, topLeft = Offset(0f, y - label.size.height / 2f)) }
                drawText(unit, topLeft = Offset(0f, h - unit.size.height))
            }
        }
    )
}

/** Time in each zone: one row per zone with a thick bar as long as its time against the longest. */
@Composable
internal fun ZoneRows(seconds: List<Double>, ranges: List<String>, colours: List<Color>, value: (Double) -> String) {
    val colors = ZankupTheme.colors
    val most = seconds.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        seconds.forEachIndexed { i, s ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Z${i + 1}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.width(26.dp)
                )
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((s / most * 0.6).toFloat().coerceAtLeast(0.02f))
                            .widthIn(min = 8.dp)
                            .height(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colours.getOrElse(i) { colours.last() })
                    )
                    Text(
                        value(s),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium.merge(Numbers.tabular)
                    )
                }
                Text(
                    ranges.getOrElse(i) { "" },
                    color = colors.muted,
                    style = MaterialTheme.typography.bodySmall.merge(Numbers.tabular).copy(fontSize = 13.sp)
                )
            }
        }
    }
}
