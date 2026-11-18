package com.zankup.android.routes

import androidx.compose.foundation.layout.padding
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.designsystem.components.EmptyState
import com.zankup.android.designsystem.components.ErrorState
import com.zankup.android.designsystem.components.ListRow
import com.zankup.android.designsystem.components.ZankupTopBar
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.network.ApiResult
import com.zankup.android.ui.LocalFormats
import kotlinx.coroutines.launch

/** Your segments: name, distance, efforts and best time; a row opens the segment. */
@Composable
fun SegmentsScreen(onBack: () -> Unit, onOpen: (Long) -> Unit, model: SegmentsModel = hiltViewModel()) {
    LaunchedEffect(model) { model.segments.refresh() }
    val state by model.segments.state.collectAsStateWithLifecycle(initialValue = null)
    val formats = LocalFormats.current
    val retry = rememberCoroutineScope()
    val segments = state?.data
    Scaffold(containerColor = ZankupTheme.colors.page, topBar = {
        ZankupTopBar(stringResource(R.string.segments_title), onBack = onBack)
    }) { padding ->
        when {
            segments == null && state?.failure != null ->
                ErrorState(
                    onRetry = { retry.launch { model.segments.refresh() } },
                    offline = state?.failure is ApiResult.Offline,
                    modifier = Modifier.padding(padding)
                )
            segments == null -> Skeletons(Modifier.padding(padding))
            segments.isEmpty() -> EmptyState(stringResource(R.string.segments_empty), Modifier.padding(padding))
            else -> LazyColumn(Modifier.padding(padding)) {
                items(segments, key = { it.id }) { s ->
                    ListRow(
                        title = s.name,
                        subtitle = listOfNotNull(
                            formats.distance(s.distanceM),
                            pluralStringResource(R.plurals.segments_efforts, s.efforts, s.efforts),
                            s.bestS?.let { stringResource(R.string.routes_best) + " " + formats.duration(it) }
                        ).joinToString(" · "),
                        onClick = { onOpen(s.id.toLong()) }
                    )
                }
            }
        }
    }
}
