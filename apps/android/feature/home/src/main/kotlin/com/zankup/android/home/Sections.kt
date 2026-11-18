package com.zankup.android.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zankup.android.api.model.ActivitySummary
import com.zankup.android.api.model.HealthDay
import com.zankup.android.designsystem.components.SectionHeader
import com.zankup.android.designsystem.components.Skeleton
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupCard
import com.zankup.android.designsystem.components.color
import com.zankup.android.designsystem.theme.Numbers
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.formatNumber
import com.zankup.android.ui.LocalFormats
import com.zankup.android.ui.RouteThumbnail
import com.zankup.android.ui.SportBadge
import com.zankup.android.ui.family
import com.zankup.android.ui.sportName
import com.zankup.android.ui.sportOf
import java.time.LocalDate
import kotlin.math.roundToInt

// --- the last day ---------------------------------------------------------------------------------

/** Today's or yesterday's activities, one card each, swiped sideways, with dots when there are several. */
@Composable
internal fun LastDaySection(recent: LastDay, today: LocalDate, onOpen: (Long) -> Unit, onPress: (Long) -> Unit) {
    val colors = ZankupTheme.colors
    val gutter = ZankupTheme.spacing.gutter
    val heading =
        if (dayHeading(recent.day, today) == DayHeading.TODAY) R.string.home_today else R.string.home_yesterday
    val activities = recent.activities
    val pager = rememberPagerState { activities.size }
    Column(verticalArrangement = Arrangement.spacedBy(ZankupTheme.spacing.s)) {
        SectionHeader(stringResource(heading), Modifier.padding(horizontal = gutter))
        HorizontalPager(
            pager,
            contentPadding = PaddingValues(horizontal = gutter),
            pageSpacing = ZankupTheme.spacing.s,
            key = { activities[it].id }
        ) { page ->
            val position = stringResource(R.string.home_activity_of, page + 1, activities.size)
            DayActivityCard(
                activities[page],
                onOpen,
                onPress,
                Modifier.semantics { if (activities.size > 1) stateDescription = position }
            )
        }
        if (activities.size > 1) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ZankupTheme.spacing.s, Alignment.CenterHorizontally)
            ) {
                activities.indices.forEach { i ->
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (i == pager.currentPage) colors.fg else colors.line)
                    )
                }
            }
        }
    }
}

/** One activity of the day: its name, its sport, the main figure big and the rest under it. */
@Composable
private fun DayActivityCard(
    activity: ActivitySummary,
    onOpen: (Long) -> Unit,
    onPress: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val formats = LocalFormats.current
    val colors = ZankupTheme.colors
    val id = activity.id.toLong()
    val sport = sportOf(activity.sport)
    val moving = activity.movingS ?: activity.durationS
    val distance = activity.distanceM?.takeIf { it > 0 }
    val main = if (distance != null) formats.distance(distance) else formats.duration(moving)
    val power = activity.avgPower?.let { stringResource(R.string.home_avg_power, it) }
    val hr = activity.avgHr?.let { stringResource(R.string.home_avg_hr, it) }
    val details = listOfNotNull(if (distance != null) formats.duration(moving) else null, power ?: hr)
    ZankupCard(modifier.onPressDown(id) { onPress(id) }, onClick = { onOpen(id) }) {
        Text(
            activity.name ?: stringResource(sportName(sport)),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // As tall with a route as without, so every card of the slider is.
        Row(
            Modifier.fillMaxWidth().height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ZankupTheme.spacing.sm)
        ) {
            SportBadge(sport.family, size = 52.dp)
            Column(Modifier.weight(1f)) {
                Text(main, style = MaterialTheme.typography.headlineMedium.merge(Numbers.tabular), maxLines = 1)
                if (details.isNotEmpty()) {
                    Text(
                        details.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            activity.route?.let { RouteThumbnail(it, colors.muted, Modifier.size(80.dp, 56.dp)) }
        }
    }
}

/** The day's steps (with a small ring), resting heart rate and HRV. */
@Composable
internal fun DayHealthCard(day: HealthDay, modifier: Modifier = Modifier) {
    val colors = ZankupTheme.colors
    val locale = LocalFormats.current.locale
    val figures =
        listOfNotNull(
            day.steps?.let {
                Figure(
                    "steps",
                    ZIcon.Steps,
                    colors.sport.ride,
                    stringResource(R.string.home_steps),
                    formatNumber(it.value.roundToInt().toDouble(), locale),
                    extra = { Ring((it.value / STEPS_GOAL).toFloat().coerceIn(0f, 1f), 34.dp, 4.dp) }
                )
            },
            day.restingHr?.let {
                Figure(
                    "hr",
                    ZIcon.Health,
                    colors.zones.last(),
                    stringResource(R.string.home_heart_rate),
                    restingText(it.value)
                )
            },
            day.hrv?.let {
                Figure("hrv", ZIcon.Hrv, colors.hrv, stringResource(R.string.home_hrv), msText(it.value))
            }
        )
    if (figures.isNotEmpty()) FigureCard(figures, modifier)
}

/** The last day's shape while the feed loads. */
@Composable
internal fun CardSkeleton(modifier: Modifier = Modifier) {
    ZankupCard(modifier) {
        Skeleton(Modifier.fillMaxWidth(0.5f), height = 20.dp)
        Spacer(Modifier.height(ZankupTheme.spacing.sm))
        Skeleton(Modifier.fillMaxWidth(), height = 52.dp)
    }
}

// --- the metrics ----------------------------------------------------------------------------------

/** VO₂ max and FTP when the estimates have them, today's steps, and the training load when there is one. */
@Composable
internal fun MetricsSection(metrics: Metrics, modifier: Modifier = Modifier) {
    val colors = ZankupTheme.colors
    val locale = LocalFormats.current.locale
    val fiveBands = colors.zones.reversed()
    val tiles = mutableListOf<@Composable (Modifier) -> Unit>()
    metrics.vdot?.let { vdot ->
        tiles += { m ->
            val title = stringResource(R.string.home_vo2)
            val label = formatNumber(vdot.roundToInt().toDouble(), locale)
            Tile(ZIcon.Run, title, m) {
                Gauge(vdot, Vo2Scale, fiveBands, label, "$title: $label")
                Muted(stringResource(R.string.home_vo2_note))
            }
        }
    }
    metrics.ftp?.let { ftp ->
        tiles += { m ->
            val title = stringResource(R.string.home_ftp)
            val watts = stringResource(R.string.home_watts)
            val label = ftp.roundToInt().toString()
            Tile(ZIcon.Ride, title, m) {
                Gauge(metrics.ftpGaugeValue ?: 0.0, FtpScale, fiveBands, label, "$title: $label $watts", unit = watts)
                metrics.wattsPerKg?.let { Muted(stringResource(R.string.home_per_kg, formatNumber(it, locale, 0, 1))) }
            }
        }
    }
    tiles += { m ->
        val title = stringResource(R.string.home_steps_today)
        val steps = formatNumber(metrics.stepsToday.roundToInt().toDouble(), locale)
        Tile(ZIcon.Steps, title, m) {
            Ring(metrics.stepsShare, 110.dp, 10.dp) {
                Text(steps, style = MaterialTheme.typography.titleLarge.merge(Numbers.tabular))
            }
        }
    }
    metrics.form?.let { form ->
        tiles += { m ->
            val title = stringResource(R.string.home_load)
            val tone = formTone(form.tsb)
            val rounded = form.tsb.roundToInt()
            val label = if (rounded > 0) "+$rounded" else "$rounded"
            val words = stringResource(tone.label)
            Tile(ZIcon.Gauge, title, m) {
                // Freshness (fitness minus fatigue) on the form's bands, so the dot sits in the pill's colour.
                Gauge(form.tsb, FormGauge.scale, FormGauge.tones.map { it.color }, label, "$title: $label, $words")
                Pill(words, tone.color)
                Muted(stringResource(R.string.home_fitness_fatigue, form.ctl.roundToInt(), form.atl.roundToInt()))
            }
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ZankupTheme.spacing.sm)) {
        SectionHeader(stringResource(R.string.home_metrics))
        tiles.chunked(2).forEach { row ->
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(ZankupTheme.spacing.sm)
            ) {
                row.forEach { tile -> tile(Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

@Composable
private fun Muted(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = ZankupTheme.colors.muted,
        textAlign = TextAlign.Center
    )
}

// --- the last seven days --------------------------------------------------------------------------

/** The last seven days by sport, then the average steps, resting heart rate and HRV. Nothing when empty. */
@Composable
internal fun LastSevenSection(ui: HomeUi, modifier: Modifier = Modifier) {
    val colors = ZankupTheme.colors
    val formats = LocalFormats.current
    val sports =
        ui.sevenDays.map { s ->
            Figure(
                s.sport.key,
                s.sport.family.icon,
                s.sport.family.color,
                stringResource(R.string.home_sport_count, stringResource(sportName(s.sport)), s.count),
                if (s.distanceM > 0) formats.distance(s.distanceM) else formats.hoursMinutes(s.movingS)
            )
        }
    val health =
        listOfNotNull(
            ui.avgSteps?.let {
                Figure(
                    "steps",
                    ZIcon.Steps,
                    colors.sport.ride,
                    stringResource(R.string.home_steps),
                    stringResource(R.string.home_average, formatNumber(it.roundToInt().toDouble(), formats.locale))
                )
            },
            ui.avgRestingHr?.let {
                Figure(
                    "hr",
                    ZIcon.Health,
                    colors.zones.last(),
                    stringResource(R.string.home_heart_rate),
                    restingText(it)
                )
            },
            ui.avgHrv?.let {
                Figure(
                    "hrv",
                    ZIcon.Hrv,
                    colors.hrv,
                    stringResource(R.string.home_hrv),
                    stringResource(R.string.home_average, msText(it))
                )
            }
        )
    val figures = sports + health
    if (figures.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ZankupTheme.spacing.s)) {
        SectionHeader(stringResource(R.string.home_last7))
        FigureCard(figures)
    }
}
