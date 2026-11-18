package com.zankup.android.progress.more

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zankup.android.api.endpoints.HealthApi
import com.zankup.android.api.endpoints.ProfileApi
import com.zankup.android.api.endpoints.StatsApi
import com.zankup.android.api.model.CalendarDay
import com.zankup.android.api.model.HealthSettings
import com.zankup.android.api.model.HealthSourceStatus
import com.zankup.android.api.model.SourceStatus
import com.zankup.android.data.Store
import com.zankup.android.designsystem.components.ErrorState
import com.zankup.android.designsystem.components.Skeleton
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupCard
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.components.ZankupTopBar
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.Units
import com.zankup.android.network.ApiResult
import com.zankup.android.network.call
import com.zankup.android.progress.R
import com.zankup.android.ui.LocalFormats
import com.zankup.android.ui.sportName
import com.zankup.android.ui.sportOf
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.format.TextStyle as DateTextStyle
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer

/** The calendar of this month and the last. */
@HiltViewModel
class MonthlyModel
@Inject
constructor(store: Store) : ViewModel() {
    val today: LocalDate = LocalDate.now()
    private val span = monthPairRange(today)
    val calendar =
        store.query("more/calendar/${span.from}", ListSerializer(CalendarDay.serializer())) { api ->
            call { api.service<StatsApi>().getCalendar(span.from.toString(), span.to.toString()) }
        }

    init {
        retry()
    }

    fun retry() {
        viewModelScope.launch { calendar.refresh() }
    }
}

/**
 * Monthly activities (monthly.tsx): this month against the last for all sports, then for each sport
 * done in either, with its time, distance, elevation and count, and the two months' time adding up.
 */
@Composable
fun MonthlyScreen(onBack: () -> Unit, model: MonthlyModel = hiltViewModel()) {
    val colors = ZankupTheme.colors
    val state by model.calendar.state.collectAsStateWithLifecycle(initialValue = null)
    val calendar = state?.data
    Scaffold(containerColor = colors.bg, topBar = {
        ZankupTopBar(stringResource(R.string.more_monthly_title), onBack = onBack)
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Column(
                Modifier.fillMaxWidth().background(colors.surface).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                if (calendar == null && state?.failure != null) {
                    ErrorState(onRetry = model::retry, offline = state?.failure is ApiResult.Offline)
                } else if (calendar == null) {
                    Skeleton(Modifier.fillMaxWidth(), height = 260.dp)
                } else {
                    val all = months(calendar, model.today)
                    val n = all.current.totals.count
                    Text(
                        pluralStringResource(R.plurals.more_monthly_activities, n, n),
                        color = colors.fg,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Figures(all.current.totals, count = false)
                    MonthChart(all, 260.dp)
                }
            }
            calendar?.let { days ->
                monthSports(days).forEach { sport ->
                    val pair = months(days, model.today, sport)
                    Column(
                        Modifier.padding(top = 8.dp).fillMaxWidth().background(colors.surface).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Text(
                            stringResource(sportName(sportOf(sport))),
                            color = colors.fg,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Figures(pair.current.totals, count = true)
                        MonthChart(pair, 220.dp)
                    }
                }
            }
        }
    }
}

/** The month's figures; a sport with nothing this month shows only its count. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Figures(totals: MonthFigures, count: Boolean) {
    val colors = ZankupTheme.colors
    val formats = LocalFormats.current
    val imperial = formats.units == Units.IMPERIAL
    val figures =
        if (totals.count == 0) {
            listOf(stringResource(R.string.more_monthly_count) to "0")
        } else {
            buildList {
                add(stringResource(R.string.more_monthly_time) to hoursMinutes(totals.time))
                if (totals.distance > 0) {
                    add(
                        stringResource(R.string.more_monthly_distance) to
                            volumeValue("distance", totals.distance, imperial, formats.locale, 1)
                    )
                    add(
                        stringResource(R.string.more_monthly_ascent) to
                            volumeValue("ascent", totals.ascent, imperial, formats.locale)
                    )
                }
                if (count) add(stringResource(R.string.more_monthly_count) to totals.count.toString())
            }
        }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        figures.forEach { (label, value) ->
            Column {
                Text(label, color = colors.muted, fontSize = 13.sp)
                Text(value, color = colors.fg, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

/**
 * The two lines (src/progress/Monthly.tsx MonthChart, with its label): last month in grey and this month
 * in the accent up to today, ending in a dot, and a dashed line at last month's total with its name and time.
 */
@Composable
private fun MonthChart(pair: MonthPair, height: Dp) {
    val colors = ZankupTheme.colors
    val locale = LocalFormats.current.locale
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val (current, previous) = pair
    val label =
        "${previous.start.month.getDisplayName(
            DateTextStyle.FULL_STANDALONE,
            locale
        )} · ${hoursMinutes(previous.totals.time)}"
    Box(Modifier.fillMaxWidth().height(height)) {
        val pad = with(density) { 10.dp.toPx() }
        val top = with(density) { 28.dp.toPx() }
        Canvas(Modifier.fillMaxSize()) {
            val slots = (maxOf(current.days, previous.days) - 1).coerceAtLeast(1)
            val most = maxOf(previous.totals.time, current.totals.time, 1.0)
            fun x(i: Int) = pad + i.toFloat() / slots * (size.width - 2 * pad)
            fun y(v: Double) = top + (1 - v / most).toFloat() * (size.height - top - pad)
            fun line(values: List<Double>) = androidx.compose.ui.graphics.Path().apply {
                values.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) }
            }
            val layout = measurer.measure(
                label,
                TextStyle(color = colors.fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            )
            val ly = y(previous.totals.time)
            drawLine(
                colors.muted,
                Offset(pad + layout.size.width + 8.dp.toPx(), ly),
                Offset(size.width - pad, ly),
                1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
            )
            drawText(layout, topLeft = Offset(pad, ly - layout.size.height / 2f))
            val stroke = 3.dp.toPx()
            drawPath(line(previous.cumulative), colors.muted, style = Stroke(stroke))
            val last = current.cumulative.lastIndex
            if (last >= 0) {
                drawPath(line(current.cumulative), colors.accent, style = Stroke(stroke, join = StrokeJoin.Round))
                val dot = Offset(x(last), y(current.cumulative[last]))
                drawCircle(colors.accent.copy(alpha = 0.25f), 13.dp.toPx(), dot)
                drawCircle(colors.accent, 6.dp.toPx(), dot)
            }
        }
    }
}

/** Garmin's state and the health sources' (sources.tsx). */
@HiltViewModel
class SourcesModel
@Inject
constructor(store: Store) : ViewModel() {
    val sources =
        store.query("more/sources", ListSerializer(SourceStatus.serializer())) { api ->
            call { api.service<ProfileApi>().listSources() }
        }
    val health =
        store.query("more/wellness/settings", HealthSettings.serializer()) { api ->
            call { api.service<HealthApi>().getHealthSettings() }
        }

    init {
        viewModelScope.launch { sources.refresh() }
        viewModelScope.launch { health.refresh() }
    }
}

private class SourceItem(
    val icon: ZIcon,
    val label: String,
    val note: String,
    val connected: Boolean,
    val open: () -> Unit
)

/**
 * Apps and devices (sources.tsx): where Zankup gets activities and health data from, whether each is
 * connected, and the AI assistants, each opening where it is set up.
 */
@Composable
fun SourcesScreen(
    onBack: () -> Unit,
    onAccount: () -> Unit,
    onImport: () -> Unit,
    onAssistants: () -> Unit,
    onHealthSettings: () -> Unit,
    model: SourcesModel = hiltViewModel()
) {
    val colors = ZankupTheme.colors
    val sources by model.sources.state.collectAsStateWithLifecycle(initialValue = null)
    val health by model.health.state.collectAsStateWithLifecycle(initialValue = null)
    val garmin = sources?.data?.firstOrNull { it.kind == SourceStatus.Kind.GARMIN }
    fun on(source: HealthSourceStatus.Source) = health?.data?.sources.orEmpty().any {
        it.source == source &&
            it.connected
    }
    val groups =
        listOf(
            stringResource(R.string.more_sources_devices) to
                listOf(
                    SourceItem(
                        ZIcon.Sync,
                        "Garmin Connect",
                        stringResource(R.string.more_sources_garmin_note),
                        garmin?.configured == true && garmin.lastSyncAt != null,
                        onAccount
                    ),
                    SourceItem(
                        ZIcon.Upload,
                        stringResource(R.string.more_sources_import),
                        stringResource(R.string.more_sources_import_note),
                        false,
                        onImport
                    )
                ),
            stringResource(R.string.more_sources_tools) to
                listOf(
                    SourceItem(
                        ZIcon.Assistant,
                        stringResource(R.string.more_assistants),
                        stringResource(R.string.more_sources_ai_note),
                        false,
                        onAssistants
                    )
                ),
            stringResource(R.string.more_sources_services) to
                listOf(
                    SourceItem(
                        ZIcon.Health,
                        "Google Health",
                        stringResource(R.string.more_sources_google_note),
                        on(HealthSourceStatus.Source.GOOGLE_HEALTH),
                        onHealthSettings
                    ),
                    SourceItem(
                        ZIcon.HealthConnect,
                        "Health Connect",
                        stringResource(R.string.more_sources_hc_note),
                        on(HealthSourceStatus.Source.HEALTH_CONNECT),
                        onHealthSettings
                    )
                )
        )
    val connected = stringResource(R.string.more_sources_connected)
    Scaffold(containerColor = colors.page, topBar = {
        ZankupTopBar(stringResource(R.string.more_sources_title), onBack = onBack)
    }) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            groups.forEach { (title, items) ->
                ZankupCard {
                    Text(title, color = colors.fg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    items.forEachIndexed { i, item ->
                        SourceLine(item, connected)
                        if (i < items.lastIndex) HorizontalDivider(color = colors.line)
                    }
                }
            }
        }
    }
}

/** A source's row (ui.tsx MenuRow): icon, name and note, and a tick in the accent when connected. */
@Composable
private fun SourceLine(item: SourceItem, connected: String) {
    val colors = ZankupTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = item.open)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ZankupIcon(item.icon, null, tint = colors.fg)
        Column(Modifier.weight(1f)) {
            Text(item.label, color = colors.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(item.note, color = colors.muted, fontSize = 13.sp)
        }
        if (item.connected) {
            ZankupIcon(ZIcon.Check, connected, tint = colors.accent, size = 24.dp)
        } else {
            ZankupIcon(ZIcon.Chevron, null, tint = colors.muted, size = 20.dp)
        }
    }
}
