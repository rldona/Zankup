package com.zankup.android.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.designsystem.components.ChipPicker
import com.zankup.android.designsystem.components.Skeleton
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.Sport
import com.zankup.android.ui.decodePolyline
import com.zankup.android.ui.sportName

private val HEATMAP_SPORTS = listOf(null, Sport.RUN, Sport.RIDE, Sport.WALK)

/**
 * The personal heatmap (OS-1141), from Stats: every route with GPS over the whole screen, a
 * see-through header (back on a round background, the title on the map) with the sport chips, and the
 * number of routes at the bottom. Pinch to zoom, drag to move.
 */
@Composable
fun HeatmapScreen(onBack: () -> Unit, model: HeatmapModel = hiltViewModel()) {
    val state by model.routes.collectAsStateWithLifecycle()
    val sportKey by model.sport.collectAsStateWithLifecycle()
    val colors = ZankupTheme.colors
    val routes = state.data?.routes
    val lines = remember(routes) { routes?.map(::decodePolyline)?.filter { it.size >= 2 } }
    val count = routes?.size ?: 0
    Box(Modifier.fillMaxSize().background(colors.page)) {
        when {
            routes == null && state.loading -> Skeleton(Modifier.fillMaxSize(), height = 2000.dp)
            !lines.isNullOrEmpty() ->
                com.zankup.android.map.LinesMap(
                    lines,
                    colors.sport.run,
                    Modifier.fillMaxSize(),
                    top = 164.dp,
                    bottom = 72.dp
                )
            else -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Muted(stringResource(R.string.stats_heatmap_note))
            }
        }
        Column(Modifier.statusBarsPadding().padding(horizontal = 12.dp)) {
            Row(
                Modifier.height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    onClick = onBack,
                    shape = CircleShape,
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.line),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        ZankupIcon(ZIcon.Back, contentDescription = stringResource(R.string.stats_back))
                    }
                }
                Text(
                    stringResource(R.string.stats_heatmap_title),
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        shadow = Shadow(colors.bg, blurRadius = 12f)
                    ),
                    color = colors.fg,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).semantics { heading() }
                )
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ChipPicker(
                    HEATMAP_SPORTS,
                    HEATMAP_SPORTS.firstOrNull { it?.key == sportKey },
                    { s -> if (s == null) stringResource(R.string.stats_all) else stringResource(sportName(s)) },
                    { model.setSport(it?.key) },
                    Modifier.padding(top = 12.dp),
                    accent = true
                )
            }
        }
        if (count > 0) {
            val text = pluralStringResource(R.plurals.stats_heatmap_count, count, count)
            val note = stringResource(R.string.stats_heatmap_note)
            Surface(
                shape = CircleShape,
                color = colors.surface,
                border = BorderStroke(1.dp, colors.line),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
                    .semantics { contentDescription = "$note $text" }
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.fg,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}
