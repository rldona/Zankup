package com.zankup.android.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.zankup.android.designsystem.components.ZankupSheet
import com.zankup.android.designsystem.components.ZankupTopBar
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.progress.ui.BestsCard
import com.zankup.android.progress.ui.DataSourcesCard
import com.zankup.android.progress.ui.EffortCard
import com.zankup.android.progress.ui.FitnessCard
import com.zankup.android.progress.ui.GoalsCard
import com.zankup.android.progress.ui.MonthlyCard
import com.zankup.android.progress.ui.PredictionsCard
import com.zankup.android.progress.ui.SeriesAndLog
import com.zankup.android.progress.ui.SeriesMonth
import com.zankup.android.progress.ui.SkillsCard
import com.zankup.android.progress.ui.VolumeCard
import com.zankup.android.progress.ui.ZonesCard

/**
 * The Progress tab (OS-1140), as Strava's: Volume first, then Series beside the Training log, Fitness,
 * Relative effort, race predictions, goals, training zones, skills, best times, monthly activities and
 * data sources. Each card opens its page through its callback; Series opens its month in a sheet, where
 * a day opens its activity. [listState] lets the tab bar scroll it back to the top.
 */
@Composable
fun ProgressScreen(
    onVolume: (String?) -> Unit,
    onTrainingLog: () -> Unit,
    onFitness: () -> Unit,
    onEffort: () -> Unit,
    onPredictions: () -> Unit,
    onGoals: () -> Unit,
    onZones: () -> Unit,
    onSkills: () -> Unit,
    onBests: (String?) -> Unit,
    onMonthly: () -> Unit,
    onSources: () -> Unit,
    onOpenActivity: (Long) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    model: ProgressModel = hiltViewModel()
) {
    LaunchedEffect(Unit) { model.refresh() }
    var series by rememberSaveable { mutableStateOf(false) }
    val colors = ZankupTheme.colors
    Scaffold(
        modifier = modifier,
        containerColor = colors.page,
        // The tab bar already holds the navigation inset; keep the content's end gap like Home's.
        contentWindowInsets = WindowInsets(0),
        topBar = { ZankupTopBar(stringResource(R.string.progress_nav_progress)) }
    ) { padding ->
        val direction = LocalLayoutDirection.current
        val spacing = ZankupTheme.spacing
        val side = Modifier.padding(horizontal = spacing.gutter)
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(direction),
                end = padding.calculateEndPadding(direction),
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + spacing.l
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.s)
        ) {
            item("volume") { Padded(side) { VolumeCard(model, onVolume) } }
            item("series-log") {
                Padded(side) { SeriesAndLog(model, onSeries = { series = true }, onLog = onTrainingLog) }
            }
            item("fitness") { Padded(side) { FitnessCard(model, onFitness) } }
            item("effort") { Padded(side) { EffortCard(model, onEffort) } }
            item("predictions") { Padded(side) { PredictionsCard(model, onPredictions) } }
            item("goals") { Padded(side) { GoalsCard(model, onGoals) } }
            item("zones") { Padded(side) { ZonesCard(model, onZones) } }
            item("skills") { Padded(side) { SkillsCard(model, onSkills) } }
            item("bests") { Padded(side) { BestsCard(model) { onBests(null) } } }
            item("monthly") { Padded(side) { MonthlyCard(model, onMonthly) } }
            item("sources") { Padded(side) { DataSourcesCard(model, onSources) } }
        }
    }
    if (series) {
        ZankupSheet(onDismiss = { series = false }) {
            SeriesMonth(model.queries, model.today) { id ->
                series = false
                onOpenActivity(id)
            }
        }
    }
}

@Composable
private fun Padded(modifier: Modifier, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(modifier) { content() }
}
