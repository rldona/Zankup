package com.zankup.android.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.designsystem.components.Skeleton
import com.zankup.android.designsystem.components.ZankupTopBar
import com.zankup.android.designsystem.theme.Numbers
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.formatNumber
import com.zankup.android.ui.LocalFormats
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Year by year (OS-1141): each year's distance cumulative through it, one line per year, the chart
 * filling the height the years' list leaves. Not scrolled. A tap on a year makes it stand out (this
 * year at first).
 */
@Composable
fun YearsScreen(onBack: () -> Unit, model: YearsModel = hiltViewModel()) {
    val state by model.lines.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableIntStateOf(LocalDate.now().year) }
    val f = LocalFormats.current
    val colors = ZankupTheme.colors
    // The days of a leap year, written short: the same for every year's line.
    val days = remember(f.locale) {
        val format = DateTimeFormatter.ofPattern("d MMM", f.locale)
        (0 until 366).map { format.format(LocalDate.of(2024, 1, 1).plusDays(it.toLong())).replace(".", "") }
    }
    Scaffold(containerColor = colors.page, topBar = {
        ZankupTopBar(stringResource(R.string.stats_progress), onBack = onBack) {
            InfoButton(stringResource(R.string.stats_progress_note))
        }
    }) { padding ->
        val lines = state.data
        if (lines.isNullOrEmpty()) {
            if (state.loading) Skeleton(Modifier.padding(padding).padding(16.dp).fillMaxWidth(), height = 300.dp)
            return@Scaffold
        }
        val scale = f.scale
        val top = (lines.maxOf { lineTotal(it.cumulativeM, scale) }.coerceAtLeast(1.0) * 1.05).toFloat()
        val palette = yearColours(
            lines.map { it.year },
            selected,
            colors.accent,
            colors.sport.let { listOf(it.run, it.strength, it.row, it.ride, it.swim, it.walk) },
            colors.muted
        )
        Surface(
            Modifier.padding(padding).padding(16.dp).navigationBarsPadding().fillMaxSize(),
            shape = MaterialTheme.shapes.medium,
            color = colors.surface
        ) {
            Column(Modifier.padding(16.dp)) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LinesChart(
                        lines = selectedLast(lines, { it.year }, selected).map { l ->
                            ChartLine(
                                l.year.toString(),
                                l.cumulativeM.map { (it / scale).toFloat() },
                                palette[l.year] ?: colors.muted,
                                width = if (l.year == selected) 3.dp else 1.6.dp,
                                alpha = if (l.year == selected) 1f else 0.5f
                            )
                        },
                        xLabels = days,
                        format = { "${it.roundToInt()} ${f.unit.distanceUnit}" },
                        description = lines.joinToString(", ") {
                            "${it.year}: ${lineTotal(it.cumulativeM, scale).roundToInt()} ${f.unit.distanceUnit}"
                        },
                        height = null,
                        domain = 0f..top
                    )
                }
                lines.forEach { l ->
                    val on = l.year == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(on, role = Role.RadioButton) { selected = l.year }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .alpha(if (on) 1f else 0.5f)
                                .clip(CircleShape)
                                .background(palette[l.year] ?: colors.muted)
                        )
                        Text(
                            l.year.toString(),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${formatNumber(lineTotal(l.cumulativeM, scale).roundToInt().toDouble(), f.locale)} " +
                                f.unit.distanceUnit,
                            style = MaterialTheme.typography.bodyLarge.merge(Numbers.tabular)
                        )
                    }
                }
            }
        }
    }
}
