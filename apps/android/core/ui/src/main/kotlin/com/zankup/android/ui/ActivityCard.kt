package com.zankup.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zankup.android.api.model.ActivitySummary
import com.zankup.android.designsystem.components.ZankupCard
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.components.color
import com.zankup.android.designsystem.theme.Numbers
import com.zankup.android.designsystem.theme.ZankupTheme
import kotlin.math.cos
import kotlin.math.max

/**
 * An activity in a list (the feed, search, Home), as the React Native app draws it: the sport on its tile,
 * the name and date, then the figures (distance, time and pace or speed; or time and heart rate for
 * sports without distance) with the route's thumbnail on its own tile, and, when it took a goal past its
 * target, a capsule saying so.
 */
@Composable
fun ActivityCard(activity: ActivitySummary, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val formats = LocalFormats.current
    val sport = sportOf(activity.sport)
    val tint = sport.family.color
    val colors = ZankupTheme.colors
    val moving = activity.movingS ?: activity.durationS
    val distance = activity.distanceM?.takeIf { it > 0 }
    ZankupCard(modifier = modifier, onClick = onOpen) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SportTile(sport.family)
                Column(Modifier.weight(1f)) {
                    Text(
                        activity.name ?: stringResource(sportName(sport)),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        formats.activityDate(activity.startUtc, activity.timezone),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.muted
                    )
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (distance != null) {
                        Figure(stringResource(R.string.figure_distance), formats.distance(distance))
                        Figure(stringResource(R.string.figure_time), formats.duration(moving))
                        Figure(
                            stringResource(if (sport.usesPace) R.string.figure_pace else R.string.figure_speed),
                            formats.speedFor(sport, distance, moving)
                        )
                    } else {
                        Figure(stringResource(R.string.figure_time), formats.duration(moving))
                        activity.avgHr?.let { Figure(stringResource(R.string.figure_hr), "$it bpm") }
                    }
                }
                val muscles = remember(activity.exercises, activity.workoutType) {
                    com.zankup.android.ui.body.musclesFor(
                        com.zankup.android.ui.body.musclesFromExercises(activity.exercises),
                        activity.workoutType
                    )
                }
                val route = activity.route
                if (route != null) {
                    Box(Modifier.size(88.dp, 64.dp).clip(RoundedCornerShape(8.dp)).background(colors.surface2)) {
                        RouteThumbnail(route, tint, Modifier.fillMaxSize())
                    }
                } else if (muscles.isNotEmpty()) {
                    // A strength workout without a route shows its muscles, as the React Native app.
                    com.zankup.android.ui.body.BodyThumb(muscles)
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
    }
}

/** A figure with its label above, in capitals (the cards' and the week's numbers). */
@Composable
fun Figure(label: String, value: String) {
    // At least 72 dp each, as the React Native app's stats, so short figures do not huddle.
    Column(Modifier.widthIn(min = 72.dp).padding(vertical = 4.dp)) {
        Text(
            label.uppercase(LocalFormats.current.locale),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = ZankupTheme.colors.muted,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            ).merge(Numbers.tabular)
        )
    }
}

/** The sport's icon on a soft tile of its colour, as the cards' headers have it. */
@Composable
fun SportTile(
    family: com.zankup.android.designsystem.components.SportFamily,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(family.color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        ZankupIcon(family.icon, contentDescription = null, tint = family.color, size = size * 0.62f)
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
