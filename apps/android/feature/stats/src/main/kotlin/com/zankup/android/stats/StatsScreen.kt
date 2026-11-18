package com.zankup.android.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.designsystem.components.ChipPicker
import com.zankup.android.designsystem.components.ListRow
import com.zankup.android.designsystem.components.SectionHeader
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupCard
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.components.ZankupTopBar
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.ui.LocalFormats

/**
 * Stats (OS-1141), from You: the period (last 12 months, a year moved with arrows, a run of months or
 * all time) above everything for it, the engine's progress while it recomputes, the reviews and the
 * heatmap in the header, and the routes, segments and year by year under "More".
 */
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    onReview: (String) -> Unit,
    onHeatmap: () -> Unit,
    onBests: (String?) -> Unit,
    onRoutes: () -> Unit,
    onSegments: () -> Unit,
    onYears: () -> Unit,
    onOpenActivity: (Long) -> Unit,
    model: StatsModel = hiltViewModel()
) {
    LaunchedEffect(Unit) { model.open() }
    val period by model.period.collectAsStateWithLifecycle()
    val engine by model.engine.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = ZankupTheme.colors.page,
        topBar = {
            Column {
                ZankupTopBar(stringResource(R.string.stats_title), onBack = onBack) {
                    ReviewsMenu(onReview)
                    IconButton(onClick = onHeatmap) {
                        ZankupIcon(ZIcon.Layers, contentDescription = stringResource(R.string.stats_heatmap))
                    }
                }
                PeriodToolbar(period, model::setMode, model::setPeriod)
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(ZankupTheme.spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(ZankupTheme.spacing.sm)
        ) {
            engine?.takeIf { it.running }?.let { e ->
                item(key = "engine") { Muted(stringResource(R.string.stats_recomputing, e.done, e.total)) }
            }
            item(key = "summary") { SummarySection(model, period.range) }
            item(key = "fitness") { FitnessSection(model) }
            item(key = "volume") { VolumeSection(model) }
            item(key = "efficiency") { EfficiencySection(model) }
            item(key = "curves") { CurvesSection(model) }
            item(key = "records") { RecordsSection(model, onBests, onOpenActivity) }
            item(key = "links") { MoreLinks(onRoutes, onSegments, onYears) }
        }
    }
}

/** The header's Reviews menu: this month's and this year's. */
@Composable
private fun ReviewsMenu(onReview: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    // In a box, so the menu drops from the button.
    Box {
        IconButton(onClick = { open = true }) {
            ZankupIcon(ZIcon.Reviews, contentDescription = stringResource(R.string.stats_reviews))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.stats_review_month_link)) },
                onClick = {
                    open = false
                    onReview("month")
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.stats_review_year_link)) },
                onClick = {
                    open = false
                    onReview("year")
                }
            )
        }
    }
}

@Composable
private fun PeriodToolbar(period: StatsPeriod, onMode: (StatsMode) -> Unit, onPeriod: (StatsPeriod) -> Unit) {
    val locale = LocalFormats.current.locale
    Column(
        Modifier.padding(horizontal = ZankupTheme.spacing.gutter).padding(bottom = ZankupTheme.spacing.s),
        verticalArrangement = Arrangement.spacedBy(ZankupTheme.spacing.xs)
    ) {
        ChipPicker(
            StatsMode.entries,
            period.mode,
            {
                stringResource(
                    when (it) {
                        StatsMode.LAST12 -> R.string.stats_mode_last12
                        StatsMode.YEAR -> R.string.stats_mode_year
                        StatsMode.MONTHS -> R.string.stats_mode_month
                        StatsMode.ALL -> R.string.stats_mode_all
                    }
                )
            },
            onMode
        )
        when (period.mode) {
            StatsMode.YEAR -> Stepper(
                period.year.toString(),
                period.canYearBack,
                period.canYearForward,
                { onPeriod(period.yearBy(-1)) },
                { onPeriod(period.yearBy(1)) }
            )
            StatsMode.MONTHS -> Row(verticalAlignment = Alignment.CenterVertically) {
                Stepper(
                    monthLabel(period.fromMonth, locale),
                    period.canFromBack,
                    period.canFromForward,
                    { onPeriod(period.fromBy(-1)) },
                    { onPeriod(period.fromBy(1)) },
                    Modifier.weight(1f)
                )
                Text("–", color = ZankupTheme.colors.muted)
                Stepper(
                    monthLabel(period.toMonth, locale),
                    period.canToBack,
                    period.canToForward,
                    { onPeriod(period.toBy(-1)) },
                    { onPeriod(period.toBy(1)) },
                    Modifier.weight(1f)
                )
            }
            else -> Unit
        }
    }
}

@Composable
private fun MoreLinks(onRoutes: () -> Unit, onSegments: () -> Unit, onYears: () -> Unit) {
    Column {
        SectionHeader(stringResource(R.string.stats_links))
        ZankupCard {
            Column(Modifier.fillMaxWidth().padding(vertical = 0.dp)) {
                ListRow(stringResource(R.string.stats_routes), icon = ZIcon.Route, onClick = onRoutes)
                HorizontalDivider(color = ZankupTheme.colors.line)
                ListRow(stringResource(R.string.stats_segments), icon = ZIcon.Timeline, onClick = onSegments)
                HorizontalDivider(color = ZankupTheme.colors.line)
                ListRow(stringResource(R.string.stats_progress), icon = ZIcon.LineChart, onClick = onYears)
            }
        }
    }
}
