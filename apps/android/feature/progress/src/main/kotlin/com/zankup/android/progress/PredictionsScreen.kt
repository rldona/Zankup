package com.zankup.android.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.components.ZankupTopBar
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.progress.logic.PredictionRange
import com.zankup.android.progress.logic.Race
import com.zankup.android.progress.logic.predictionLabelDays
import com.zankup.android.progress.logic.racePace
import com.zankup.android.progress.logic.raceTime
import com.zankup.android.progress.logic.series
import com.zankup.android.progress.logic.timeTicks
import com.zankup.android.progress.logic.valueOn
import com.zankup.android.progress.ui.BigFigure
import com.zankup.android.progress.ui.CardSkeleton
import com.zankup.android.progress.ui.InfoButton
import com.zankup.android.progress.ui.Muted
import com.zankup.android.progress.ui.PredictionChart
import com.zankup.android.progress.ui.ProgressChip
import com.zankup.android.progress.ui.RaceBadge
import com.zankup.android.progress.ui.RaceChange
import com.zankup.android.progress.ui.SmallText
import com.zankup.android.progress.ui.date
import com.zankup.android.progress.ui.histories
import com.zankup.android.progress.ui.imperial
import com.zankup.android.progress.ui.longDate
import com.zankup.android.ui.LocalFormats

/**
 * Performance predictions, from Progress (as Strava's): today, the four races with their pace and how
 * they changed in 30 days; over a month, three or six, one race with its line. The range is chosen at
 * the bottom of the screen, always there.
 */
@Composable
fun PredictionsScreen(onBack: () -> Unit, modifier: Modifier = Modifier, model: PredictionsModel = hiltViewModel()) {
    LaunchedEffect(Unit) { model.refresh() }
    val colors = ZankupTheme.colors
    val estimates by model.estimates.collectAsStateWithLifecycle()
    var range by rememberSaveable { mutableStateOf(PredictionRange.TODAY) }
    var race by rememberSaveable { mutableStateOf(Race.R5K) }
    Scaffold(
        modifier = modifier,
        containerColor = colors.page,
        topBar = {
            ZankupTopBar(stringResource(R.string.progress_predictions), onBack = onBack) {
                InfoButton(stringResource(R.string.progress_predictions_info))
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val byKind = histories(estimates.data.orEmpty())
                when {
                    estimates.loading -> {
                        CardSkeleton(2)
                        CardSkeleton(3)
                    }
                    Race.entries.none { byKind[it.kind].orEmpty().isNotEmpty() } ->
                        Muted(stringResource(R.string.progress_no_predictions))
                    range == PredictionRange.TODAY -> Today(model, byKind) {
                        race = it
                        range = PredictionRange.M1
                    }
                    else -> OverTime(model, byKind, race, range) { race = it }
                }
            }
            RangeSwitch(range) { range = it }
        }
    }
}

private val Race.label
    get() = when (this) {
        Race.R5K -> R.string.progress_race_race_5k
        Race.R10K -> R.string.progress_race_race_10k
        Race.HALF -> R.string.progress_race_race_half_marathon
        Race.MARATHON -> R.string.progress_race_race_marathon
    }

@Composable
private fun Today(
    model: PredictionsModel,
    byKind: Map<String, List<com.zankup.android.progress.logic.Point>>,
    onRace: (Race) -> Unit
) {
    val colors = ZankupTheme.colors
    val imperial = imperial()
    val unit = if (imperial) "/mi" else "/km"
    val today = model.today
    Text(stringResource(R.string.progress_predictions_intro), fontSize = 14.sp, lineHeight = 22.sp, color = colors.fg)
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        maxItemsInEachRow = 2,
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        Race.entries.forEach { r ->
            val history = byKind[r.kind].orEmpty()
            val now = valueOn(history, today)
            val before = valueOn(history, today.minusDays(30))
            Column(
                Modifier.weight(1f).clickable(role = Role.Button) { onRace(r) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                RaceBadge(r, 60)
                if (now != null) {
                    BigFigure(raceTime(now), size = 24)
                    Muted("${racePace(now, r.metres, imperial)} $unit")
                    if (before != null) RaceChange(now - before)
                } else {
                    Muted("–")
                }
            }
        }
    }
}

@Composable
private fun OverTime(
    model: PredictionsModel,
    byKind: Map<String, List<com.zankup.android.progress.logic.Point>>,
    race: Race,
    range: PredictionRange,
    onRace: (Race) -> Unit
) {
    val colors = ZankupTheme.colors
    val imperial = imperial()
    val locale = LocalFormats.current.locale
    val today = model.today
    val from = today.minusDays(range.days)
    val history = byKind[race.kind].orEmpty()
    val points = series(history, from, today)
    val now = valueOn(history, today)
    val values = points.map { it.value }
    val ticks = if (values.isEmpty()) emptyList() else timeTicks(values.min(), values.max())
    val labels = predictionLabelDays(range, from, today).map {
        it to date(it, if (range == PredictionRange.M1) "d MMM" else "MMM").replace(".", "").uppercase(locale)
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Race.entries.forEach { r -> ProgressChip(stringResource(r.label), on = r == race, onClick = { onRace(r) }) }
    }
    Text(
        stringResource(R.string.progress_current_predictions),
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = colors.fg
    )
    if (now == null || points.isEmpty()) {
        Muted(stringResource(R.string.progress_no_predictions))
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        RaceBadge(race, 54)
        Column(Modifier.weight(1f)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigFigure(raceTime(now), size = 24)
                Muted(
                    "${racePace(now, race.metres, imperial)} ${if (imperial) "/mi" else "/km"}",
                    Modifier.align(Alignment.Bottom),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp)
                )
            }
            Muted(
                "${longDate(from)} - ${stringResource(R.string.progress_today)}",
                style = SmallText
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RaceChange(now - points.first().value)
            Muted(
                stringResource(
                    when (range) {
                        PredictionRange.M3 -> R.string.progress_pred_last_3m
                        PredictionRange.M6 -> R.string.progress_pred_last_6m
                        else -> R.string.progress_pred_last_1m
                    }
                ),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
    PredictionChart(points, from, today, ticks, labels)
}

/** Today · 1M · 3M · 6M, in a pill at the bottom; the chosen one ticked. */
@Composable
private fun RangeSwitch(range: PredictionRange, onPick: (PredictionRange) -> Unit) {
    val colors = ZankupTheme.colors
    Row(
        Modifier
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
            .fillMaxWidth()
            .clip(CircleShape)
            .border(1.5.dp, colors.line, CircleShape)
    ) {
        PredictionRange.entries.forEach { r ->
            val on = r == range
            Row(
                Modifier
                    .weight(1f)
                    .background(if (on) colors.surface2 else colors.page)
                    .clickable(role = Role.RadioButton) { onPick(r) }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (on) ZankupIcon(ZIcon.Check, contentDescription = null, tint = colors.fg, size = 18.dp)
                Text(
                    stringResource(
                        when (r) {
                            PredictionRange.TODAY -> R.string.progress_pred_range_today
                            PredictionRange.M1 -> R.string.progress_pred_range_1m
                            PredictionRange.M3 -> R.string.progress_pred_range_3m
                            PredictionRange.M6 -> R.string.progress_pred_range_6m
                        }
                    ),
                    color = colors.fg,
                    fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
