package com.zankup.android.health.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zankup.android.api.model.Night
import com.zankup.android.api.model.NightDetail
import com.zankup.android.designsystem.theme.Numbers
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.formatHoursMinutes
import com.zankup.android.health.R
import com.zankup.android.health.Side
import com.zankup.android.health.SleepBand
import com.zankup.android.health.SleepGoal
import com.zankup.android.health.SleepLane
import com.zankup.android.health.nightClock
import com.zankup.android.health.sleepBlocks
import com.zankup.android.health.sleepDurationBand
import com.zankup.android.health.sleepScoreBand
import com.zankup.android.health.stageTotals
import java.time.format.DateTimeFormatter

/** A soft label in a colour: a band or a goal. */
@Composable
internal fun Pill(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.25f),
        contentColor = ZankupTheme.colors.fg
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** A figure in its own card: a label, the value big, whatever goes under it and whose it is in the corner. */
@Composable
internal fun Tile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    corner: (@Composable () -> Unit)? = null,
    cornerLabel: String = "",
    valueSize: Int = 26,
    upper: Boolean = false,
    content: @Composable () -> Unit = {}
) {
    val colors = ZankupTheme.colors
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = colors.surface, contentColor = colors.fg) {
        Box {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (upper) label.uppercase() else label,
                    Modifier.padding(end = 22.dp),
                    color = colors.muted,
                    fontSize = if (upper) 12.sp else 13.sp,
                    // The day's figures read as RN's small capitals, the night's as its bolder labels.
                    fontWeight = if (upper) FontWeight.Normal else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    value,
                    style = Numbers.tabular,
                    fontSize = valueSize.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    color = colors.fg
                )
                content()
            }
            if (corner != null) Corner(cornerLabel) { corner() }
        }
    }
}

@Composable
internal fun laneName(lane: SleepLane): String = androidx.compose.ui.res.stringResource(
    when (lane) {
        SleepLane.AWAKE -> R.string.health_stage_awake
        SleepLane.REM -> R.string.health_stage_rem
        SleepLane.LIGHT -> R.string.health_stage_light
        SleepLane.DEEP -> R.string.health_stage_deep
    }
)

@Composable
internal fun laneColor(lane: SleepLane): Color {
    val sleep = ZankupTheme.colors.sleep
    return when (lane) {
        SleepLane.AWAKE -> sleep.awake
        SleepLane.REM -> sleep.rem
        SleepLane.LIGHT -> sleep.light
        SleepLane.DEEP -> sleep.deep
    }
}

/**
 * The score and the hours asleep side by side, each with whose figure it is in its corner: Zankup's
 * mark on the score (worked out here), the source's on the hours.
 */
@Composable
internal fun SleepTiles(sleep: Night, sourceLabel: String, source: @Composable () -> Unit) {
    val colors = ZankupTheme.colors
    val asleep = sleep.asleepS ?: 0
    val zankup = androidx.compose.ui.res.stringResource(R.string.health_zankup_score)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Tile(
            androidx.compose.ui.res.stringResource(R.string.health_score),
            sleep.score?.toString() ?: "–",
            Modifier.weight(1f),
            corner = { ZankupMark() },
            cornerLabel = zankup
        ) {
            sleep.score?.let { score ->
                val band = sleepScoreBand(score)
                val tone = when (band) {
                    SleepBand.EXCELLENT, SleepBand.GOOD -> colors.sleep.light
                    SleepBand.FAIR -> colors.sleep.awake
                    SleepBand.POOR -> colors.zones.last()
                }
                Pill(androidx.compose.ui.res.stringResource(bandName(band)), tone)
            }
        }
        Tile(
            androidx.compose.ui.res.stringResource(R.string.health_duration),
            formatHoursMinutes(asleep.toDouble()),
            Modifier.weight(1f),
            corner = source,
            cornerLabel = sourceLabel
        ) {
            val goal = sleepDurationBand(asleep)
            val tone = when (goal) {
                SleepGoal.MET -> colors.sleep.light
                SleepGoal.ALMOST -> colors.sleep.awake
                SleepGoal.SHORT -> colors.zones.last()
            }
            Pill(androidx.compose.ui.res.stringResource(goalName(goal)), tone)
        }
    }
}

private fun bandName(band: SleepBand) = when (band) {
    SleepBand.EXCELLENT -> R.string.health_band_excellent
    SleepBand.GOOD -> R.string.health_band_good
    SleepBand.FAIR -> R.string.health_band_fair
    SleepBand.POOR -> R.string.health_band_poor
}

private fun goalName(goal: SleepGoal) = when (goal) {
    SleepGoal.MET -> R.string.health_goal_met
    SleepGoal.ALMOST -> R.string.health_goal_almost
    SleepGoal.SHORT -> R.string.health_goal_short
}

private const val TRACK = 30
private const val LABEL = 22
private const val ROW = LABEL + 6 + TRACK + 14

/**
 * The night's stages, one lane each (awake, REM, light, deep) with the time spent in it. A stretch is
 * a rounded block in its lane, squared off on the side a thin line joins it to the stage before or
 * after, so the night reads as one path going up and down; the clock runs underneath.
 */
@Composable
internal fun StageLanes(night: NightDetail, sleep: Night) {
    val colors = ZankupTheme.colors
    val measurer = rememberTextMeasurer()
    val blocks = remember(night.stages) { sleepBlocks(night.stages) }
    val spent = remember(night.stages) { stageTotals(night.stages) }
    val total = (blocks.maxOfOrNull { it.end } ?: 0).takeIf { it > 0 } ?: 1
    val lanes = SleepLane.entries
    val names = lanes.map { "${laneName(it)} · ${formatHoursMinutes(spent.getValue(it).toDouble())}" }
    val fills = lanes.map { laneColor(it) }
    val style = TextStyle(color = colors.fg, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    val label = androidx.compose.ui.res.stringResource(R.string.health_stages_label)
    val clock = DateTimeFormatter.ofPattern("H:mm")
    Column(Modifier.semantics { contentDescription = label }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Spacer(
            Modifier
                .fillMaxWidth()
                .height((lanes.size * ROW - 14).dp)
                .drawWithCache {
                    val track = TRACK.dp.toPx()
                    fun trackTop(lane: Int) = (lane * ROW + LABEL + 6).dp.toPx()
                    fun x(s: Int) = s.toFloat() / total * size.width
                    val texts = names.map { measurer.measure(it, style) }
                    val shapes = blocks.map { b ->
                        val w = maxOf(2f, x(b.end) - x(b.start))
                        val r = minOf(track / 2, w / 2)
                        fun c(square: Boolean) = if (square) CornerRadius.Zero else CornerRadius(r)
                        val rect = RoundRect(
                            left = x(b.start),
                            top = trackTop(b.lane),
                            right = x(b.start) + w,
                            bottom = trackTop(b.lane) + track,
                            topLeftCornerRadius = c(b.from == Side.ABOVE),
                            topRightCornerRadius = c(b.to == Side.ABOVE),
                            bottomRightCornerRadius = c(b.to == Side.BELOW),
                            bottomLeftCornerRadius = c(b.from == Side.BELOW)
                        )
                        fills.getOrElse(b.lane) { fills[2] } to Path().apply { addRoundRect(rect) }
                    }
                    onDrawBehind {
                        lanes.indices.forEach { i ->
                            drawRoundRect(
                                colors.surface2,
                                Offset(0f, trackTop(i)),
                                Size(size.width, track),
                                CornerRadius(track / 2)
                            )
                        }
                        // From the upper block's base to the lower block's top: never past either colour.
                        blocks.forEachIndexed { i, b ->
                            val prev = blocks.getOrNull(i - 1) ?: return@forEachIndexed
                            if (b.from == null) return@forEachIndexed
                            val top = minOf(trackTop(prev.lane), trackTop(b.lane)) + track
                            val bottom = maxOf(trackTop(prev.lane), trackTop(b.lane))
                            val lx = x(b.start)
                            drawLine(colors.muted.copy(alpha = 0.45f), Offset(lx, top), Offset(lx, bottom), 1.dp.toPx())
                        }
                        shapes.forEach { (color, path) -> drawPath(path, color) }
                        // The names over the lines, on the card's colour, so a line never runs through them.
                        texts.forEachIndexed { i, t ->
                            val ty = (i * ROW).dp.toPx()
                            val back = Size(t.size.width + 8.dp.toPx(), t.size.height.toFloat())
                            drawRect(colors.surface, Offset(0f, ty), back)
                            drawText(t, topLeft = Offset(0f, ty))
                        }
                    }
                }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0, total / 2, total).forEach { s ->
                Text(
                    nightClock(sleep.startUtc, sleep.utcOffsetS, s)?.format(clock) ?: "",
                    style = Numbers.tabular,
                    fontSize = 13.sp,
                    color = colors.muted
                )
            }
        }
    }
}
