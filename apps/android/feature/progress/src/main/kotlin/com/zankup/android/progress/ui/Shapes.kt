package com.zankup.android.progress.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.theme.Numbers
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.POWER_LEVELS
import com.zankup.android.domain.Skill
import com.zankup.android.domain.levelAt
import com.zankup.android.progress.logic.RADAR_DURATIONS
import com.zankup.android.progress.logic.Race
import com.zankup.android.progress.logic.radialShare
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A solid flame (two tips, the left one taller, as Strava's) in a 100-unit box, so the weeks can sit on
 * it: Material's filled flame has a hollow inside.
 */
private const val FLAME =
    "M50 99C24 99 9 82 9 61C9 41 22 30 30 9C35 23 42 31 47 35C50 25 55 19 58 10C74 27 91 42 91 63C91 84 75 99 50 99Z"

/** The flame with the weeks on it, low down where it is widest. */
@Composable
internal fun Flame(weeks: Int, size: Int) {
    val colors = ZankupTheme.colors
    val path = remember { PathParser().parsePathString(FLAME).toPath() }
    Box(Modifier.size(size.dp)) {
        Canvas(Modifier.size(size.dp)) {
            scale(this.size.width / 100f, pivot = Offset.Zero) { drawPath(path, colors.accent) }
        }
        Text(
            weeks.toString(),
            modifier = Modifier.align(Alignment.TopCenter).offset(y = (size * 0.40f).dp),
            color = colors.accentFg,
            style = TextStyle(
                fontSize = (size * if (weeks > 99) 0.26f else 0.32f).sp,
                fontWeight = FontWeight.ExtraBold
            ).merge(Numbers.tabular)
        )
    }
}

/** A goal's ring: how far along it is, with its sport in the middle, filling from twelve o'clock. */
@Composable
internal fun GoalRing(fraction: Float, icon: ZIcon, size: Int = 56) {
    val colors = ZankupTheme.colors
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size.dp)) {
            val stroke = this.size.width * 0.09f
            val inset = stroke / 2 + 1.dp.toPx()
            val box = Size(this.size.width - 2 * inset, this.size.height - 2 * inset)
            drawArc(
                colors.muted.copy(alpha = 0.35f),
                0f,
                360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = box,
                style = Stroke(stroke)
            )
            if (fraction > 0) {
                drawArc(
                    colors.accent,
                    -90f,
                    360f * fraction,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = box,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }
        ZankupIcon(icon, contentDescription = null, tint = colors.fg, size = (size * 0.4f).dp)
    }
}

/** A race's distance on a scalloped badge in the accent, as Strava's: 5 · 10 · 21,1 · 42,2 KM. */
@Composable
internal fun RaceBadge(race: Race, size: Int = 64) {
    val colors = ZankupTheme.colors
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size.dp)) {
            val r = this.size.width / 2
            val points = 24
            val path = Path().apply {
                for (i in 0 until points) {
                    val a = i.toFloat() / points * 2 * PI.toFloat() - PI.toFloat() / 2
                    val radius = if (i % 2 == 1) r * 0.82f else r * 0.94f
                    val p = Offset(r + radius * cos(a), r + radius * sin(a))
                    if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
                close()
            }
            drawPath(path, colors.accent, style = Stroke(2.5.dp.toPx(), join = StrokeJoin.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                race.badge,
                color = colors.accent,
                fontSize = (size * if (race.badge.length > 2) 0.26f else 0.32f).sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = (size * 0.34f).sp
            )
            Text(
                "KM",
                color = colors.accent,
                fontSize = (size * 0.14f).sp,
                fontWeight = FontWeight.Bold,
                lineHeight = (size * 0.16f).sp
            )
        }
    }
}

/** The skills' colours, as Strava's, the same on every theme. */
internal fun skillColor(skill: Skill): Color = when (skill) {
    Skill.SPRINT -> Color(0xFF0160CF)
    Skill.ATTACK -> Color(0xFF59A61C)
    Skill.CLIMB -> Color(0xFFFB7432)
}

/** The two radars' colours: all time in purple, the last eight weeks in pink over it. */
internal val RadarAll = Color(0xFF823BAE)
internal val RadarRecent = Color(0xFFD97ED1)

/** One shape of the radar: best power by duration, its colour and the weight it is read for. */
internal class RadarShape(val bests: Map<Int, Double>, val color: Color, val weight: Double)

/**
 * The card's small radar, as Strava's: rings for the levels (spaced as the level bar's segments), a
 * spoke per duration, the skills round it as a coloured ring, and the shapes (the first under).
 */
@Composable
internal fun SkillsRadar(size: Int, shapes: List<RadarShape>) {
    val colors = ZankupTheme.colors
    Canvas(Modifier.size(size.dp)) {
        val centre = this.size.width / 2
        val band = this.size.width * 0.06f
        val outer = centre - band / 2 - 1.dp.toPx()
        val inner = outer - band / 2 - 3.dp.toPx()
        val count = RADAR_DURATIONS.size
        val step = 2 * PI.toFloat() / count
        fun angle(i: Int) = -PI.toFloat() / 2 + i * step
        fun at(i: Int, r: Float) = Offset(centre + r * cos(angle(i)), centre + r * sin(angle(i)))
        val grid = colors.muted.copy(alpha = 0.45f)
        for (k in 1..POWER_LEVELS) {
            drawCircle(
                grid,
                inner * radialShare(k.toDouble()).toFloat(),
                Offset(centre, centre),
                style = Stroke(0.6.dp.toPx())
            )
        }
        for (i in 0 until count) drawLine(grid, Offset(centre, centre), at(i, inner), 0.6.dp.toPx())
        var from = 0
        val gap = 0.014f
        Skill.entries.forEach { skill ->
            val to = from + skill.durations.size - 1
            val a0 = angle(from) - step / 2 + gap
            val a1 = angle(to) + step / 2 - gap
            drawArc(
                skillColor(skill),
                Math.toDegrees(a0.toDouble()).toFloat(),
                Math.toDegrees((a1 - a0).toDouble()).toFloat(),
                useCenter = false,
                topLeft = Offset(centre - outer, centre - outer),
                size = Size(outer * 2, outer * 2),
                style = Stroke(band)
            )
            from = to + 1
        }
        shapes.forEach { shape ->
            val path = Path().apply {
                RADAR_DURATIONS.forEachIndexed { i, d ->
                    val level = levelAt(shape.bests[d] ?: 0.0, d, shape.weight)
                    val p = at(i, inner * radialShare(level).toFloat())
                    if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
                close()
            }
            drawPath(path, shape.color.copy(alpha = 0.9f))
        }
    }
}

/** Gold, silver and bronze in every theme; past the third, the theme's grey. */
private val MEDALS = listOf(Color(0xFFE8B10E), Color(0xFF8F9EA6), Color(0xFFC06D3D))

/** A medal with its place, as Strava's: the first is "PR". */
@Composable
internal fun Medal(rank: Int, size: Int = 44) {
    val colors = ZankupTheme.colors
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        ZankupIcon(
            ZIcon.Award,
            contentDescription = null,
            filled = true,
            tint = MEDALS.getOrElse(rank - 1) { colors.muted },
            size = size.dp
        )
        Text(
            if (rank == 1) "PR" else rank.toString(),
            modifier = Modifier.align(Alignment.TopCenter).offset(y = (size * 0.12f).dp),
            color = colors.bg,
            fontSize = (size * if (rank == 1) 0.2f else 0.26f).sp,
            fontWeight = FontWeight.Black
        )
    }
}
