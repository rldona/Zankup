package com.zankup.android.activity

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.charts.Bar
import com.zankup.android.charts.BarChart
import com.zankup.android.designsystem.components.ListRow
import com.zankup.android.designsystem.components.SectionHeader
import com.zankup.android.designsystem.components.Skeleton
import com.zankup.android.designsystem.components.ZankupTopBar
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.ui.LocalFormats
import com.zankup.android.ui.sportOf

/**
 * Every effort on the same route: their pace or speed in order (the opened one in the accent) and the
 * list by year; a row opens its activity.
 */
@Composable
fun MatchesScreen(
    id: Long,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    model: ActivityModel = hiltViewModel(key = "activity-$id")
) {
    // Opened during composition: the screen reads its queries right below.
    remember(id) {
        model.open(id)
        id
    }
    val detail by model.detail.state.collectAsStateWithLifecycle(initialValue = null)
    val context by model.activityContext.state.collectAsStateWithLifecycle(initialValue = null)
    val formats = LocalFormats.current
    val sport = sportOf(detail?.data?.sport)
    val matches = context?.data?.matches.orEmpty().sortedBy { it.day }
    Scaffold(containerColor = ZankupTheme.colors.page, topBar = {
        ZankupTopBar(stringResource(R.string.matches), onBack = onBack)
    }) { padding ->
        if (matches.isEmpty()) {
            Skeleton(Modifier.padding(padding).padding(16.dp).fillMaxWidth(), height = 200.dp)
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding)) {
            item {
                BarChart(
                    bars =
                    matches.map {
                        val speed = it.speedMps ?: 0.0
                        Bar(
                            if (sport.usesPace &&
                                speed > 0
                            ) {
                                (1000 / speed).toFloat()
                            } else {
                                (speed * 3.6).toFloat()
                            },
                            it.day.take(4),
                            if (it.`this`) ZankupTheme.colors.accent else ZankupTheme.colors.muted
                        )
                    },
                    selected = null,
                    onSelect = {},
                    format = {
                        if (sport.usesPace) {
                            com.zankup.android.domain.formatDuration(
                                it.toDouble()
                            )
                        } else {
                            "%.0f".format(it)
                        }
                    },
                    inverted = sport.usesPace,
                    modifier = Modifier.padding(16.dp)
                )
            }
            matches.groupBy { it.day.take(4) }.toSortedMap(compareByDescending { it }).forEach { (year, list) ->
                item { SectionHeader(year, Modifier.padding(horizontal = 16.dp)) }
                items(list.sortedByDescending { it.day }, key = { it.activityId }) { match ->
                    ListRow(
                        title = match.day,
                        subtitle = listOfNotNull(
                            match.speedMps?.let {
                                formats.unit.speedFor(it, sport)
                            },
                            formats.duration(match.movingS),
                            match.relativeEffort?.let {
                                "%.0f".format(it)
                            }
                        ).joinToString(" · "),
                        onClick = if (match.`this`) null else ({ onOpen(match.activityId.toLong()) })
                    )
                }
            }
        }
    }
}
