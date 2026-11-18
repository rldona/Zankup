package com.zankup.android.routes

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.charts.ChartCursor
import com.zankup.android.charts.ChartSection
import com.zankup.android.designsystem.components.ErrorState
import com.zankup.android.designsystem.components.SectionHeader
import com.zankup.android.designsystem.components.ZankupTopBar
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.map.RouteMap
import com.zankup.android.network.ApiResult
import com.zankup.android.ui.LocalFormats
import kotlinx.coroutines.launch

/** One segment: its efforts, its line on the map (260 dp), the history, and "Delete segment" with a confirm. */
@Composable
fun SegmentScreen(
    id: Long,
    onBack: () -> Unit,
    onOpenActivity: (Long) -> Unit,
    model: SegmentModel = hiltViewModel(key = "segment-$id")
) {
    remember(id) {
        model.open(id)
        id
    }
    LaunchedEffect(id) { model.segment.refresh() }
    val state by model.segment.state.collectAsStateWithLifecycle(initialValue = null)
    val formats = LocalFormats.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val s = state?.data
    Scaffold(containerColor = ZankupTheme.colors.page, topBar = {
        ZankupTopBar(s?.name.orEmpty(), onBack = onBack)
    }) { padding ->
        when {
            s == null && state?.failure != null ->
                ErrorState(
                    onRetry = { scope.launch { model.segment.refresh() } },
                    offline = state?.failure is ApiResult.Offline,
                    modifier = Modifier.padding(padding)
                )
            s == null -> Skeletons(Modifier.padding(padding))
            else -> {
                val track = remember(s.track) { trackOf(s.track, precision = 6) }
                val cursor = remember { ChartCursor() }
                val section = remember { ChartSection() }
                val best = s.bestS ?: fastest(s.history.map { it.elapsedS }) ?: 0.0
                LazyColumn(Modifier.padding(padding)) {
                    item {
                        Text(
                            listOfNotNull(
                                formats.distance(s.distanceM),
                                s.ascentM?.let { "+" + formats.elevation(it) },
                                pluralStringResource(R.plurals.segments_efforts, s.efforts, s.efforts)
                            ).joinToString(" · "),
                            Modifier.padding(16.dp),
                            color = ZankupTheme.colors.muted,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (track != null) {
                        item("map") { RouteMap(track, cursor, section, Modifier.fillMaxWidth().height(260.dp)) }
                    }
                    item {
                        SectionHeader(stringResource(R.string.segments_history), Modifier.padding(horizontal = 16.dp))
                    }
                    items(newestFirst(s.history), key = { it.activityId }) { e ->
                        EffortRow(
                            Effort(
                                e.activityId.toLong(),
                                e.day,
                                e.elapsedS,
                                e.avgHr?.let { "$it bpm" } ?: e.avgPower?.let { "$it W" }
                            ),
                            best
                        ) { onOpenActivity(e.activityId.toLong()) }
                    }
                    item {
                        TextButton(onClick = { confirm = true }, Modifier.padding(horizontal = 8.dp)) {
                            Text(stringResource(R.string.segments_delete), color = ZankupTheme.colors.formNegative)
                        }
                        if (failed) {
                            Text(
                                stringResource(R.string.routes_delete_failed),
                                Modifier.padding(horizontal = 16.dp),
                                color = ZankupTheme.colors.formNegative
                            )
                        }
                    }
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(s?.name.orEmpty()) },
            text = { Text(stringResource(R.string.segments_delete)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    scope.launch { if (model.delete()) onBack() else failed = true }
                }) { Text(stringResource(R.string.routes_delete), color = ZankupTheme.colors.formNegative) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.routes_cancel)) }
            }
        )
    }
}
