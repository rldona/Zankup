package com.zankup.android.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.api.model.EstimateOut
import com.zankup.android.designsystem.components.EmptyState
import com.zankup.android.designsystem.components.Skeleton
import com.zankup.android.designsystem.components.ZankupCard
import com.zankup.android.designsystem.components.ZankupTopBar
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.formatDuration
import com.zankup.android.ui.LocalFormats

/**
 * Estimates (OS-1141), from You: VDOT, FTP, critical speed, threshold HR (and max HR), each with its
 * value, its history and the activities it comes from; then the race predictions with their method.
 */
@Composable
fun EstimatesScreen(onBack: () -> Unit, onOpenActivity: (Long) -> Unit, model: EstimatesModel = hiltViewModel()) {
    val state by model.estimates.collectAsStateWithLifecycle()
    Scaffold(containerColor = ZankupTheme.colors.page, topBar = {
        ZankupTopBar(stringResource(R.string.stats_estimates), onBack = onBack)
    }) { padding ->
        val list = state.data
        when {
            list == null && state.loading ->
                Skeleton(Modifier.padding(padding).padding(16.dp).fillMaxWidth(), height = 200.dp)
            list == null -> Muted(stringResource(R.string.stats_error), Modifier.padding(padding).padding(16.dp))
            list.isEmpty() ->
                EmptyState(stringResource(R.string.stats_estimates_empty), Modifier.padding(padding))
            else -> {
                val by = list.associateBy { it.kind }
                val main = ESTIMATE_ORDER.mapNotNull { by[it] }
                val races = RACES.mapNotNull { by[it] }
                LazyColumn(
                    Modifier.padding(padding),
                    contentPadding = PaddingValues(ZankupTheme.spacing.gutter),
                    verticalArrangement = Arrangement.spacedBy(ZankupTheme.spacing.sm)
                ) {
                    items(main, key = { it.kind }) { EstimateCard(it, onOpenActivity) }
                    if (races.isNotEmpty()) item(key = "races") { RacesCard(races) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EstimateCard(e: EstimateOut, onOpenActivity: (Long) -> Unit) {
    val f = LocalFormats.current
    val name = estimateName(e.kind)
    ZankupCard {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(name, style = HeadingStyle, color = ZankupTheme.colors.fg, modifier = Modifier.weight(1f))
            Text(
                estimateValue(e.kind, e.value, f),
                style = StatValueStyle.copy(fontSize = 22.sp),
                color = ZankupTheme.colors.fg
            )
        }
        if (e.history.size > 1) {
            LinesChart(
                lines = listOf(ChartLine(name, e.history.map { it.value.toFloat() }, ZankupTheme.colors.accent)),
                xLabels = emptyList(),
                format = { estimateValue(e.kind, it.toDouble(), f) },
                description = name,
                height = 48.dp
            )
        }
        FlowRow(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.Center) {
            Muted(methodText(e.method, f) + if (e.sources.isNotEmpty()) ": " else "")
            e.sources.forEachIndexed { i, s ->
                if (i > 0) Muted(", ")
                Link(shortDay(s.day, f), { onOpenActivity(s.id.toLong()) })
            }
        }
    }
}

@Composable
private fun RacesCard(races: List<EstimateOut>) {
    val f = LocalFormats.current
    StatCard(stringResource(R.string.stats_predictions)) {
        Muted(methodText(races.first().method, f))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            races.forEach { Stat(estimateName(it.kind), formatDuration(it.value)) }
        }
    }
}
