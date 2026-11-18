package com.zankup.android.routes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.designsystem.components.EmptyState
import com.zankup.android.designsystem.components.ErrorState
import com.zankup.android.designsystem.components.ListRow
import com.zankup.android.designsystem.components.Skeleton
import com.zankup.android.designsystem.components.ZankupTopBar
import com.zankup.android.designsystem.components.color
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.network.ApiResult
import com.zankup.android.ui.LocalFormats
import com.zankup.android.ui.family
import com.zankup.android.ui.sportName
import com.zankup.android.ui.sportOf
import kotlinx.coroutines.launch

/** Routes done more than once: name, times done, distance and best time; a row opens the route. */
@Composable
fun RoutesScreen(onBack: () -> Unit, onOpen: (Long) -> Unit, model: RoutesModel = hiltViewModel()) {
    LaunchedEffect(model) { model.routes.refresh() }
    val state by model.routes.state.collectAsStateWithLifecycle(initialValue = null)
    val formats = LocalFormats.current
    val retry = rememberCoroutineScope()
    val routes = state?.data
    Scaffold(containerColor = ZankupTheme.colors.page, topBar = {
        ZankupTopBar(stringResource(R.string.routes_title), onBack = onBack)
    }) { padding ->
        when {
            routes == null && state?.failure != null ->
                ErrorState(
                    onRetry = { retry.launch { model.routes.refresh() } },
                    offline = state?.failure is ApiResult.Offline,
                    modifier = Modifier.padding(padding)
                )
            routes == null -> Skeletons(Modifier.padding(padding))
            routes.isEmpty() -> EmptyState(stringResource(R.string.routes_empty), Modifier.padding(padding))
            else -> LazyColumn(Modifier.padding(padding)) {
                items(mostDone(routes) { it.count }, key = { it.id }) { r ->
                    val sport = sportOf(r.sport)
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        r.route?.let {
                            com.zankup.android.ui.RouteThumbnail(
                                it,
                                sport.family.color,
                                Modifier.padding(start = 16.dp).size(56.dp, 40.dp)
                            )
                        }
                        ListRow(
                            modifier = Modifier.weight(1f),
                            title = r.name ?: stringResource(sportName(sport)),
                            subtitle = listOf(
                                pluralStringResource(R.plurals.routes_times, r.count, r.count),
                                formats.distance(r.best.distanceM),
                                stringResource(R.string.routes_best) + " " + formats.duration(r.best.movingS)
                            ).joinToString(" · "),
                            onClick = { onOpen(r.id.toLong()) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun Skeletons(modifier: Modifier = Modifier) {
    Column(
        modifier.padding(16.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
    ) {
        repeat(6) { Skeleton(Modifier.fillMaxWidth(), height = 56.dp) }
    }
}
