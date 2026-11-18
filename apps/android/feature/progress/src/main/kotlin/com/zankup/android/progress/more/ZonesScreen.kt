package com.zankup.android.progress.more

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zankup.android.api.endpoints.StatsApi
import com.zankup.android.api.model.ZonesOut
import com.zankup.android.data.Query
import com.zankup.android.data.Store
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupCard
import com.zankup.android.designsystem.components.ZankupChip
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.components.ZankupTopBar
import com.zankup.android.designsystem.theme.Numbers
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.Sport
import com.zankup.android.domain.Units
import com.zankup.android.network.call
import com.zankup.android.progress.R
import com.zankup.android.ui.LocalFormats
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The zones of a kind over a span, for every sport or one; each query kept under its own key. */
@HiltViewModel
class ZonesModel
@Inject
constructor(private val store: Store) : ViewModel() {
    private val queries = mutableMapOf<String, Query<ZonesOut>>()

    internal fun zones(kind: ZoneKind, span: DaySpan, sport: Sport?): Query<ZonesOut> {
        val key = "more/zones/${kind.key}/${span.from}/${span.to}/${sport?.key ?: "all"}"
        return queries.getOrPut(key) {
            val apiKind = StatsApi.KindGetZones.entries.first { it.value == kind.key }
            store.query(key, ZonesOut.serializer()) { api ->
                call {
                    api.service<StatsApi>().getZones(
                        apiKind,
                        span.from.toString(),
                        span.to.toString(),
                        sport?.let { listOf(it.key) }
                    )
                }
            }.also { q -> viewModelScope.launch { q.refresh() } }
        }
    }
}

private fun kindLabel(kind: ZoneKind) = when (kind) {
    ZoneKind.HR -> R.string.more_heart_rate
    ZoneKind.POWER -> R.string.more_power
    ZoneKind.PACE -> R.string.more_pace
}

private fun sinceLabel(r: ProgressRange) = when (r) {
    ProgressRange.D7 -> R.string.more_zone_since_7d
    ProgressRange.M1 -> R.string.more_zone_since_1m
    ProgressRange.M3 -> R.string.more_zone_since_3m
    ProgressRange.M6 -> R.string.more_zone_since_6m
    ProgressRange.YTD -> R.string.more_zone_since_ytd
    ProgressRange.Y1 -> R.string.more_zone_since_1y
}

private fun adviceLabel(band: Band, up: Boolean) = when (band) {
    Band.EASY -> if (up) R.string.more_zone_advice_easy_up else R.string.more_zone_advice_easy_down
    Band.STEADY -> if (up) R.string.more_zone_advice_steady_up else R.string.more_zone_advice_steady_down
    Band.HARD -> if (up) R.string.more_zone_advice_hard_up else R.string.more_zone_advice_hard_down
}

/**
 * Training zones, from Progress (zones.tsx): HR, pace or power over 7 days to a year, for every sport
 * or one; the zone with most time and how its share moved against the span before; the zone rows; a
 * sentence on what changed most; notices without FTP or threshold. The pencil opens the heart settings.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ZonesScreen(onBack: () -> Unit, onHeartSettings: () -> Unit, model: ZonesModel = hiltViewModel()) {
    val colors = ZankupTheme.colors
    val locale = LocalFormats.current.locale
    val today = remember { LocalDate.now() }
    var sportKey by rememberSaveable { mutableStateOf<String?>(null) }
    val sport = sportKey?.let { Sport.fromKey(it) }
    var chosen by rememberSaveable { mutableStateOf(ZoneKind.HR) }
    val kinds = zoneKinds(sport)
    val kind = if (chosen in kinds) chosen else ZoneKind.HR
    var range by rememberSaveable { mutableStateOf(ProgressRange.M1) }
    var picking by remember { mutableStateOf(false) }
    val span = rangeSpan(range, today)
    val wanted = Triple(kind, span, sport)
    // Each answer tagged with what it is for, so one of a former choice is never taken for this one.
    val tagged = remember(wanted) { model.zones(kind, span, sport).state.map { wanted to it } }
    val answer by tagged.collectAsStateWithLifecycle(initialValue = null)
    val state = answer?.takeIf { it.first == wanted }?.second
    // While a new range or sport loads, the zones shown stay when they are of the same kind.
    var shown by remember { mutableStateOf<ZonesOut?>(null) }
    LaunchedEffect(state?.data) { state?.data?.let { shown = it } }
    val data = state?.data ?: shown?.takeIf { it.kind == kind.key }
    val loaded = state?.data != null
    val seconds = data?.seconds ?: List(if (kind == ZoneKind.POWER) 7 else 5) { 0.0 }
    val previous = data?.previous.orEmpty()
    val top = topZone(seconds)
    val change = top?.let { shareChange(seconds, previous, it.index) }
    val note = glance(seconds, previous)
    val longDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)

    Scaffold(containerColor = colors.page, topBar = {
        ZankupTopBar(stringResource(R.string.more_zones), onBack = onBack) {
            IconButton(onClick = onHeartSettings) {
                ZankupIcon(ZIcon.Edit, stringResource(R.string.more_edit_zones))
            }
            InfoAction(stringResource(R.string.more_zones_info))
        }
    }) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconChip(sportIcon(sport), sportLabel(sport), selected = true) { picking = true }
                kinds.forEach { k ->
                    ZankupChip(stringResource(kindLabel(k)), selected = k == kind, onClick = { chosen = k })
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            top != null -> stringResource(R.string.more_zones_top_long, top.share, top.index + 1)
                            data != null || state?.failure != null -> stringResource(R.string.more_no_zones)
                            else -> " "
                        },
                        color = colors.fg,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${longDate.format(span.from)} – ${stringResource(R.string.more_today)}",
                        color = colors.muted,
                        fontSize = 13.sp
                    )
                }
                if (change != null && change != 0) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        ShareBadge(change)
                        Text(stringResource(R.string.more_vs_before), color = colors.muted, fontSize = 13.sp)
                    }
                }
            }
            ZoneRows(kind, seconds, data?.bounds.orEmpty())
            if (loaded && kind == ZoneKind.POWER && data?.bounds.isNullOrEmpty()) {
                Text(stringResource(R.string.more_no_ftp), color = colors.muted)
            }
            if (loaded && kind == ZoneKind.PACE && data?.bounds.isNullOrEmpty()) {
                Text(stringResource(R.string.more_no_threshold), color = colors.muted)
            }
            RangePills(range, { range = it }, Modifier.padding(top = 4.dp))
            if (note != null) {
                ZankupCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ZankupIcon(sportIcon(sport), null, tint = colors.fg, size = 22.dp)
                        Text(stringResource(R.string.more_zones_glance), color = colors.fg, fontSize = 14.sp)
                    }
                    Text(
                        stringResource(
                            if (note.up) R.string.more_zone_up else R.string.more_zone_down,
                            note.zone + 1,
                            note.percent,
                            stringResource(sinceLabel(range))
                        ) + " " + stringResource(adviceLabel(note.band, note.up)),
                        color = colors.fg,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
        }
    }
    if (picking) {
        SportSheet(sport, stringResource(R.string.more_all_sports_note), onPick = {
            sportKey = it?.key
            // Each sport opens on its own zones: a run on pace, a ride on power.
            chosen = defaultZoneKind(it)
            picking = false
        }, onDismiss = { picking = false })
    }
}

/** A change in points of share: ▲ 4 % or ▼ 3 %. */
@Composable
private fun ShareBadge(points: Int) {
    val colors = ZankupTheme.colors
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).background(colors.surface2).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(if (points >= 0) "▲" else "▼", color = colors.muted, fontSize = 11.sp)
        Text("${abs(points)} %", color = colors.fg, fontSize = 14.sp, style = Numbers.tabular)
    }
}

/** The zones in full, the highest on top: each one's time as a bar, the time and its range. */
@Composable
private fun ZoneRows(kind: ZoneKind, seconds: List<Double>, bounds: List<Double>) {
    val colors = ZankupTheme.colors
    val zoneColours = zoneScale(kind, seconds.size)
    val most = maxOf(1.0, seconds.maxOrNull() ?: 0.0)
    val unit = if (kind == ZoneKind.HR) stringResource(R.string.more_bpm) else "W"
    val imperial = LocalFormats.current.units == Units.IMPERIAL
    fun range(i: Int) = when {
        bounds.isEmpty() -> ""
        kind == ZoneKind.PACE -> paceRange(bounds, i, if (imperial) METRES_PER_MILE else 1000.0).replace(" ", "")
        else -> "${zoneRange(bounds, i)} $unit"
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        seconds.indices.reversed().forEach { i ->
            val share by animateFloatAsState(
                maxOf(2.0, seconds[i] / most * 62).toFloat() / 100f,
                tween(320),
                label = "zone"
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Z${i + 1}",
                    color = colors.fg,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.width(24.dp)
                )
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(share)
                            .widthIn(min = 8.dp)
                            .height(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(zoneColours.getOrElse(i) { Color.Gray })
                    )
                    Text(
                        changeText(seconds[i]),
                        color = colors.fg,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        style = Numbers.tabular
                    )
                }
                Text(range(i), color = colors.muted, fontSize = 13.sp, style = Numbers.tabular)
            }
        }
    }
}
