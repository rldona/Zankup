package com.zankup.android.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.charts.Bar
import com.zankup.android.charts.BarChart
import com.zankup.android.designsystem.components.Skeleton
import com.zankup.android.designsystem.components.ZankupTopBar
import com.zankup.android.designsystem.theme.Numbers
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.formatDuration
import com.zankup.android.ui.LocalFormats

/**
 * A run's training analysis: its splits as bars (as wide as each split, taller the faster) with the
 * average dashed, and their list; a finger on the chart or a row picks a split, the first at open.
 */
@Composable
fun AnalysisScreen(id: Long, onBack: () -> Unit, model: ActivityModel = hiltViewModel(key = "activity-$id")) {
    // Opened during composition: the screen reads its queries right below.
    remember(id) {
        model.open(id)
        id
    }
    val detail by model.detail.state.collectAsStateWithLifecycle(initialValue = null)
    val a = detail?.data
    val formats = LocalFormats.current
    var picked by remember { mutableIntStateOf(0) }
    Scaffold(containerColor = ZankupTheme.colors.page, topBar = {
        ZankupTopBar(stringResource(R.string.training_analysis), onBack = onBack)
    }) { padding ->
        if (a == null) {
            Skeleton(Modifier.padding(padding).padding(16.dp).fillMaxWidth(), height = 240.dp)
            return@Scaffold
        }
        val average = (a.durationS.takeIf { (a.distanceM ?: 0.0) > 0 } ?: 0.0) / ((a.distanceM ?: 1.0) / 1000)
        LazyColumn(Modifier.padding(padding)) {
            item {
                Column(Modifier.padding(16.dp)) {
                    BarChart(
                        bars = a.splits.mapIndexed { i, s ->
                            Bar((s.timeS / (s.distanceM / 1000)).toFloat(), "${i + 1}", weight = s.distanceM.toFloat())
                        },
                        selected = picked,
                        onSelect = { picked = it },
                        format = { formatDuration(it.toDouble()) },
                        color = ZankupTheme.colors.sport.ride,
                        average = average.toFloat(),
                        inverted = true,
                        height = 260.dp
                    )
                    a.splits.getOrNull(picked)?.let { s ->
                        Text(
                            "${stringResource(
                                R.string.split
                            )} ${picked + 1} · ${formats.unit.pace(s.distanceM / s.timeS)}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
            itemsIndexed(a.splits, key = { i, _ -> i }) { i, s ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (i == picked) ZankupTheme.colors.accentSoft else ZankupTheme.colors.page)
                        .clickable { picked = i }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    val style = MaterialTheme.typography.bodyMedium.merge(Numbers.tabular)
                    Text("${i + 1}", style = style, modifier = Modifier.weight(0.5f))
                    Text(formats.distance(s.distanceM), style = style, modifier = Modifier.weight(1f))
                    Text(formats.duration(s.timeS), style = style, modifier = Modifier.weight(1f))
                    Text(formats.unit.pace(s.distanceM / s.timeS), style = style, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
