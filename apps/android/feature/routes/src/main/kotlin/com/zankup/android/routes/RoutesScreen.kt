package com.zankup.android.routes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.designsystem.components.EmptyState
import com.zankup.android.designsystem.components.ErrorState
import com.zankup.android.designsystem.components.Skeleton
import com.zankup.android.designsystem.components.ZankupTopBar
import com.zankup.android.designsystem.components.color
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.Sport
import com.zankup.android.map.RouteSnapshot
import com.zankup.android.network.ApiResult
import com.zankup.android.ui.LocalFormats
import com.zankup.android.ui.decodePolyline
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
            else -> LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(
                    start = ZankupTheme.spacing.gutter,
                    top = ZankupTheme.spacing.gutter,
                    end = ZankupTheme.spacing.gutter,
                    bottom = ZankupTheme.spacing.l
                ),
                verticalArrangement = Arrangement.spacedBy(ZankupTheme.spacing.sm)
            ) {
                items(mostDone(routes) { it.count }, key = { it.id }) { r ->
                    val sport = sportOf(r.sport)
                    RouteRow(
                        name = r.name ?: stringResource(sportName(sport)),
                        subtitle = listOf(
                            pluralStringResource(R.plurals.routes_times, r.count, r.count),
                            formats.distance(r.best.distanceM),
                            stringResource(R.string.routes_best) + " " + formats.duration(r.best.movingS)
                        ).joinToString(" · "),
                        sport = sport,
                        route = r.route,
                        onClick = { onOpen(r.id.toLong()) }
                    )
                }
            }
        }
    }
}

/** A route as a card: its name and figures, and its shape on the right. */
@Composable
private fun RouteRow(name: String, subtitle: String, sport: Sport, route: String?, onClick: () -> Unit) {
    val colors = ZankupTheme.colors
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.surface
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.fg)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            route?.let {
                val points = remember(it) { decodePolyline(it) }
                RouteSnapshot(
                    points,
                    sport.family.color,
                    Modifier.size(88.dp, 64.dp).clip(RoundedCornerShape(10.dp))
                )
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
