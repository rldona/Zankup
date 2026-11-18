package com.zankup.android.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.api.model.Review
import com.zankup.android.api.model.ReviewActivity
import com.zankup.android.designsystem.components.ChipPicker
import com.zankup.android.designsystem.components.EmptyState
import com.zankup.android.designsystem.components.Skeleton
import com.zankup.android.designsystem.components.ZankupCard
import com.zankup.android.designsystem.components.ZankupTopBar
import com.zankup.android.designsystem.theme.Numbers
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.formatDuration
import com.zankup.android.ui.Formats
import com.zankup.android.ui.LocalFormats
import com.zankup.android.ui.sportName
import com.zankup.android.ui.sportOf
import kotlin.math.roundToInt

/**
 * A month or a year in review (OS-1141): totals against the previous period with their bars,
 * consistency and sports, the longest, hardest and hilliest activity, records and gear. [period] is
 * "month" or "year"; the arrows move between periods.
 */
@Composable
fun ReviewScreen(
    period: String,
    onBack: () -> Unit,
    onOpenActivity: (Long) -> Unit,
    model: ReviewModel = hiltViewModel(key = "review-$period")
) {
    LaunchedEffect(period) { model.open(period) }
    val shown by model.shown.collectAsStateWithLifecycle()
    val state by model.review.collectAsStateWithLifecycle()
    val f = LocalFormats.current
    val (p, value) = shown ?: return
    val heading = periodTitle(p, value, f.locale)
    Scaffold(containerColor = ZankupTheme.colors.page, topBar = {
        Column {
            ZankupTopBar(
                stringResource(
                    if (p == ReviewPeriod.YEAR) R.string.stats_review_year_title else R.string.stats_review_month_title,
                    heading
                ),
                onBack = onBack
            )
            Column(Modifier.padding(horizontal = ZankupTheme.spacing.gutter).padding(bottom = 8.dp)) {
                ChipPicker(
                    ReviewPeriod.entries,
                    p,
                    {
                        stringResource(
                            if (it == ReviewPeriod.MONTH) R.string.stats_review_month else R.string.stats_review_year
                        )
                    },
                    model::switchTo,
                    accent = true
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { model.shift(-1) }, modifier = Modifier.weight(1f)) {
                        Text("← ${periodTitle(p, shiftPeriod(p, value, -1), f.locale)}", maxLines = 1)
                    }
                    OutlinedButton(onClick = { model.shift(1) }, modifier = Modifier.weight(1f)) {
                        Text("${periodTitle(p, shiftPeriod(p, value, 1), f.locale)} →", maxLines = 1)
                    }
                }
            }
        }
    }) { padding ->
        val data = state.data
        when {
            data == null && state.loading ->
                Skeleton(Modifier.padding(padding).padding(16.dp).fillMaxWidth(), height = 240.dp)
            data == null -> Muted(stringResource(R.string.stats_error), Modifier.padding(padding).padding(16.dp))
            (data.totals.count ?: 0) == 0 ->
                EmptyState(stringResource(R.string.stats_review_empty), Modifier.padding(padding))
            else -> LazyColumn(
                Modifier.padding(padding),
                contentPadding = PaddingValues(ZankupTheme.spacing.gutter),
                verticalArrangement = Arrangement.spacedBy(ZankupTheme.spacing.sm)
            ) { reviewCards(data, f, onOpenActivity) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.reviewCards(data: Review, f: Formats, onOpenActivity: (Long) -> Unit) {
    item(key = "totals") {
        StatCard(stringResource(R.string.stats_review_totals)) {
            val t = data.totals
            val b = data.previous
            val rows = listOf(
                Triple(
                    stringResource(R.string.stats_review_activities),
                    (t.count ?: 0).toString(),
                    change(
                        (t.count ?: 0).toDouble(),
                        (b.count ?: 0).toDouble()
                    )
                ),
                Triple(
                    stringResource(R.string.stats_distance),
                    f.distance(t.distanceM),
                    change(t.distanceM ?: 0.0, b.distanceM ?: 0.0)
                ),
                Triple(
                    stringResource(R.string.stats_time),
                    f.duration(t.movingS ?: 0.0),
                    change(t.movingS ?: 0.0, b.movingS ?: 0.0)
                ),
                Triple(
                    stringResource(R.string.stats_ascent),
                    f.elevation(t.ascentM ?: 0.0),
                    change(t.ascentM ?: 0.0, b.ascentM ?: 0.0)
                )
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rows.forEach { (label, value, delta) ->
                    Stat(
                        label,
                        value,
                        Modifier.widthIn(min = 120.dp),
                        below = delta?.let { stringResource(R.string.stats_review_vs_previous, it) }
                    )
                }
            }
            val max = data.buckets.maxOfOrNull { it.distanceM }?.coerceAtLeast(1.0) ?: 1.0
            val barsLabel = stringResource(R.string.stats_review_bars_label)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .height(72.dp)
                    .semantics { contentDescription = barsLabel },
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                data.buckets.forEach { bucket ->
                    val share = maxOf(if (bucket.count > 0) 0.04 else 0.0, bucket.distanceM / max).toFloat()
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                        if (share > 0f) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(share)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(ZankupTheme.colors.accent)
                            )
                        }
                    }
                }
            }
        }
    }
    item(key = "consistency") {
        StatCard(stringResource(R.string.stats_review_consistency)) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Stat(stringResource(R.string.stats_review_active_days), data.activeDays.toString())
                Stat(stringResource(R.string.stats_review_streak), data.longestStreak.toString())
            }
            data.bySport.entries.sortedByDescending { it.value.movingS ?: 0.0 }.forEach { (sport, s) ->
                Column(Modifier.padding(top = 6.dp)) {
                    Text(
                        stringResource(sportName(sportOf(sport))),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    val count = s.count ?: 0
                    Muted(
                        listOfNotNull(
                            pluralStringResource(R.plurals.stats_review_activities_count, count, count),
                            s.distanceM?.takeIf { it > 0 }?.let { f.distance(it) },
                            f.duration(s.movingS ?: 0.0)
                        ).joinToString(" · ")
                    )
                }
            }
        }
    }
    listOf(
        "longest" to data.longest,
        "hardest" to data.hardest,
        "hilliest" to data.hilliest
    ).forEach { (key, a) ->
        if (a != null) item(key = key) { StandOut(key, a, f) { onOpenActivity(a.id.toLong()) } }
    }
    val records = data.records.filter { effortShown(it.distanceKey, f.imperial) }
    if (records.isNotEmpty()) {
        item(key = "records") {
            StatCard(stringResource(R.string.stats_review_records)) {
                records.forEach { r ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { onOpenActivity(r.activityId.toLong()) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            effortName(r.distanceKey),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = ZankupTheme.colors.accent
                        )
                        r.previousS?.let {
                            Text(
                                " " + stringResource(R.string.stats_review_beat, formatDuration(it)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = ZankupTheme.colors.muted
                            )
                        }
                        Box(Modifier.weight(1f))
                        Text(
                            formatDuration(r.timeS),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                .merge(Numbers.tabular)
                        )
                    }
                }
            }
        }
    }
    if (data.gear.isNotEmpty()) {
        item(key = "gear") {
            StatCard(stringResource(R.string.stats_review_gear)) {
                data.gear.forEach { g ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                g.name,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Muted(pluralStringResource(R.plurals.stats_review_activities_count, g.count, g.count))
                        }
                        Text(
                            f.distance(g.distanceM),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                .merge(Numbers.tabular)
                        )
                    }
                }
            }
        }
    }
}

/** The longest, hardest or hilliest activity: its name and day, and its figures. Opens it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StandOut(key: String, a: ReviewActivity, f: Formats, onOpen: () -> Unit) {
    val title = when (key) {
        "longest" -> R.string.stats_review_longest
        "hardest" -> R.string.stats_review_hardest
        else -> R.string.stats_review_hilliest
    }
    ZankupCard(onClick = onOpen) {
        Text(stringResource(title), style = HeadingStyle, color = ZankupTheme.colors.fg)
        Row(Modifier.padding(vertical = 4.dp)) {
            Text(
                a.name ?: stringResource(sportName(sportOf(a.sport))),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = ZankupTheme.colors.accent,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(
                " · ${dayLabel(a.day, f)}",
                style = MaterialTheme.typography.bodyMedium,
                color = ZankupTheme.colors.muted
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            a.distanceM?.takeIf { it > 0 }?.let { Stat(stringResource(R.string.stats_distance), f.distance(it)) }
            Stat(stringResource(R.string.stats_time), f.duration(a.movingS))
            a.ascentM?.takeIf { it > 0 }?.let { Stat(stringResource(R.string.stats_ascent), f.elevation(it)) }
            a.load?.takeIf {
                it > 0
            }?.let { Stat(stringResource(R.string.stats_relative_effort), it.roundToInt().toString()) }
        }
    }
}
