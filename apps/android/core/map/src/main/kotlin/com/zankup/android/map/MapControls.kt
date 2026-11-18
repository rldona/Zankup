package com.zankup.android.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zankup.android.charts.ChartCursor
import com.zankup.android.designsystem.components.ChipPicker
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.components.ZankupSheet
import com.zankup.android.designsystem.theme.ZankupTheme

/**
 * What the route can be coloured by: its name, its colour stops (null for the plain line) and the
 * legend's two ends ("4:10 /km" and "6:30 /km", fast first for a pace).
 */
@Immutable
class MapMetric(
    val key: String,
    val label: String,
    val stops: List<Pair<Double, androidx.compose.ui.graphics.Color>>?,
    val low: String = "",
    val high: String = ""
)

/** How much faster than real time the replay runs. */
val REPLAY_SPEEDS = listOf(10, 30, 100)

/**
 * What drives the map, on the map itself: play and pause at the bottom left, and an options button at
 * the top right with the metric the route is coloured by (and its legend) and the replay speed. Nothing
 * else sits on the map, so the route stays in view. The replay moves the shared [cursor], so the charts
 * follow it too.
 */
@Composable
fun BoxScope.MapControls(
    route: Route,
    cursor: ChartCursor,
    metrics: List<MapMetric>,
    chosen: MapMetric,
    /** How much of the map's top a see-through header covers: the options button sits under it. */
    top: Dp = 0.dp,
    onChoose: (MapMetric) -> Unit
) {
    var playing by remember { mutableStateOf(false) }
    var speed by rememberSaveable { mutableStateOf(REPLAY_SPEEDS[1]) }
    var options by remember { mutableStateOf(false) }
    val clock = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(playing, speed) {
        if (!playing || route.t.isEmpty()) return@LaunchedEffect
        val end = route.t.last()
        clock.floatValue = route.t.getOrElse(cursor.index) { 0f }.let { if (it >= end) 0f else it }
        var last = withFrameNanos { it }
        while (playing) {
            val now = withFrameNanos { it }
            clock.floatValue += (now - last) / 1e9f * speed
            last = now
            if (clock.floatValue >= end) {
                cursor.index = route.t.lastIndex
                playing = false
                break
            }
            cursor.index = route.indexAtTime(clock.floatValue)
        }
    }

    val buttons =
        IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = ZankupTheme.colors.surface,
            contentColor = ZankupTheme.colors.fg
        )
    FilledTonalIconButton(
        onClick = { playing = !playing },
        colors = buttons,
        modifier = Modifier.align(
            Alignment.BottomStart
        ).padding(12.dp).border(1.dp, ZankupTheme.colors.line, CircleShape)
    ) {
        ZankupIcon(
            if (playing) ZIcon.Pause else ZIcon.Play,
            contentDescription = stringResource(if (playing) R.string.map_pause else R.string.map_play),
            filled = true
        )
    }
    FilledTonalIconButton(
        onClick = { options = true },
        colors = buttons,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = top)
            .padding(12.dp)
            .border(1.dp, ZankupTheme.colors.line, CircleShape)
    ) {
        ZankupIcon(ZIcon.Options, contentDescription = stringResource(R.string.map_options))
    }

    if (options) {
        ZankupSheet(onDismiss = { options = false }) {
            Column(
                Modifier.padding(
                    horizontal = ZankupTheme.spacing.gutter
                ).navigationBarsPadding().padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(ZankupTheme.spacing.sm)
            ) {
                Text(stringResource(R.string.map_colour_by), style = MaterialTheme.typography.titleMedium)
                ChipPicker(metrics, chosen, { it.label }, onChoose)
                if (chosen.stops != null) Legend(chosen)
                Text(stringResource(R.string.map_replay_speed), style = MaterialTheme.typography.titleMedium)
                ChipPicker(REPLAY_SPEEDS, speed, { "$it×" }, { speed = it })
            }
        }
    }
}

/** The ramp as a bar with its two ends written under it. */
@Composable
private fun Legend(metric: MapMetric) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Brush.horizontalGradient(RAMP))
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(metric.low, style = MaterialTheme.typography.labelMedium, color = ZankupTheme.colors.muted)
            Text(metric.high, style = MaterialTheme.typography.labelMedium, color = ZankupTheme.colors.muted)
        }
    }
}
