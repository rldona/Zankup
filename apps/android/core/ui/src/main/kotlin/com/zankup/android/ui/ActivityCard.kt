package com.zankup.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zankup.android.api.model.ActivitySummary
import com.zankup.android.designsystem.components.ZankupCard
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.components.color
import com.zankup.android.designsystem.theme.Numbers
import com.zankup.android.designsystem.theme.ZankupTheme
import kotlin.math.cos
import kotlin.math.max

/**
 * An activity in a list (the feed, search, Home): its sport, name and date, two figures (distance and
 * pace or speed, or time and heart rate for sports without distance), the route's thumbnail and, when it
 * took a goal past its target, a capsule saying so. [onPress] runs as the finger lands, to prefetch.
 */
@Composable
fun ActivityCard(activity: ActivitySummary, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val formats = LocalFormats.current
    val sport = sportOf(activity.sport)
    val tint = sport.family.color
    val colors = ZankupTheme.colors
    ZankupCard(modifier = modifier, onClick = onOpen) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SportBadge(sport.family, size = 28.dp)
                    Column {
                        Text(
                            activity.name ?: stringResource(sportName(sport)),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            formats.activityDate(activity.startUtc, activity.timezone),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    if (sport.byDistance && (activity.distanceM ?: 0.0) > 0) {
                        Figure(stringResource(R.string.figure_distance), formats.distance(activity.distanceM))
                        Figure(
                            stringResource(if (sport.usesPace) R.string.figure_pace else R.string.figure_speed),
                            formats.speedFor(sport, activity.distanceM, activity.movingS ?: activity.durationS)
                        )
                    } else {
                        Figure(stringResource(R.string.figure_time), formats.duration(activity.durationS))
                        activity.avgHr?.let { Figure(stringResource(R.string.figure_hr), "$it bpm") }
                    }
                }
                if (!activity.goalsReached.isNullOrEmpty()) {
                    Text(
                        stringResource(R.string.goal_reached),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.accentFg,
                        modifier = Modifier.clip(
                            CircleShape
                        ).background(colors.accent).padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            activity.route?.let { RouteThumbnail(it, tint, Modifier.size(88.dp, 64.dp)) }
        }
    }
}

@Composable
private fun Figure(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = ZankupTheme.colors.muted)
        Text(value, style = MaterialTheme.typography.titleMedium.merge(Numbers.tabular))
    }
}

/** The sport's icon on a disc of its colour. */
@Composable
fun SportBadge(
    family: com.zankup.android.designsystem.components.SportFamily,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp
) {
    Box(modifier.size(size).clip(CircleShape).background(family.color), contentAlignment = Alignment.Center) {
        ZankupIcon(family.icon, contentDescription = null, tint = ZankupTheme.colors.page, size = size * 0.6f)
    }
}

/**
 * The route as a small line in the sport's colour, north up and fitted to the box (longitudes shrunk by
 * the latitude, so a route is not stretched). Nothing for fewer than two points.
 */
@Composable
fun RouteThumbnail(polyline: String, color: Color, modifier: Modifier = Modifier) {
    val points = remember(polyline) { decodePolyline(polyline) }
    if (points.size < 2) return
    Canvas(modifier) {
        val pad = 4.dp.toPx()
        val lats = points.map { it.first }
        val k = cos(Math.toRadians(lats.average()))
        val xs = points.map { it.second * k }
        val minX = xs.min()
        val minY = lats.min()
        val w = (xs.max() - minX).coerceAtLeast(1e-9)
        val h = (lats.max() - minY).coerceAtLeast(1e-9)
        val scale = minOf((size.width - pad * 2) / w, (size.height - pad * 2) / h)
        val ox = (size.width - w * scale) / 2
        val oy = (size.height - h * scale) / 2
        val path = Path()
        points.forEachIndexed { i, (lat, _) ->
            val x = (ox + (xs[i] - minX) * scale).toFloat()
            val y = (size.height - oy - (lat - minY) * scale).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path,
            color,
            style = Stroke(width = max(2.dp.toPx(), 1f), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawCircle(
            color,
            2.5.dp.toPx(),
            Offset(
                (ox + (xs[0] - minX) * scale).toFloat(),
                (
                    size.height - oy -
                        (lats[0] - minY) * scale
                    ).toFloat()
            )
        )
    }
}
