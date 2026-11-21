package com.zankup.android.routes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.charts.ChartCursor
import com.zankup.android.charts.ChartSection
import com.zankup.android.designsystem.components.ErrorState
import com.zankup.android.designsystem.components.ZankupTopBar
import com.zankup.android.designsystem.components.color
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.map.RouteMap
import com.zankup.android.network.ApiResult
import com.zankup.android.ui.LocalFormats
import com.zankup.android.ui.family
import com.zankup.android.ui.sportName
import com.zankup.android.ui.sportOf
import kotlinx.coroutines.launch

/** One route: times done, its line on the map and every time, the fastest marked; a row opens the activity. */
@Composable
fun RouteScreen(
    id: Long,
    onBack: () -> Unit,
    onOpenActivity: (Long) -> Unit,
    model: RouteModel = hiltViewModel(key = "route-$id")
) {
    remember(id) {
        model.open(id)
        id
    }
    LaunchedEffect(id) { model.route.refresh() }
    val state by model.route.state.collectAsStateWithLifecycle(initialValue = null)
    val formats = LocalFormats.current
    val retry = rememberCoroutineScope()
    val r = state?.data
    val sport = sportOf(r?.sport)
    Scaffold(containerColor = ZankupTheme.colors.page, topBar = {
        ZankupTopBar(r?.name ?: stringResource(sportName(sport)), onBack = onBack)
    }) { padding ->
        when {
            r == null && state?.failure != null ->
                ErrorState(
                    onRetry = { retry.launch { model.route.refresh() } },
                    offline = state?.failure is ApiResult.Offline,
                    modifier = Modifier.padding(padding)
                )
            r == null -> Skeletons(Modifier.padding(padding))
            else -> {
                val track = remember(r.route) { trackOf(r.route) }
                val cursor = remember { ChartCursor() }
                val section = remember { ChartSection() }
                val best = fastest(r.activities.map { it.movingS }) ?: 0.0
                // The count and the map stay put; only the times below scroll.
                Column(Modifier.padding(padding).fillMaxSize()) {
                    Text(
                        pluralStringResource(R.plurals.routes_times, r.count, r.count),
                        Modifier.padding(16.dp),
                        color = ZankupTheme.colors.muted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (track != null) {
                        RouteMap(
                            track,
                            cursor,
                            section,
                            Modifier.fillMaxWidth().height(260.dp),
                            colour = sportOf(r.sport).family.color
                        )
                    }
                    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                        items(newestFirst(r.activities), key = { it.id }) { a ->
                            EffortRow(
                                Effort(
                                    a.id.toLong(),
                                    a.day,
                                    a.movingS,
                                    a.distanceM?.let { formats.speedFor(sport, it, a.movingS) }
                                ),
                                best
                            ) { onOpenActivity(a.id.toLong()) }
                        }
                    }
                }
            }
        }
    }
}
