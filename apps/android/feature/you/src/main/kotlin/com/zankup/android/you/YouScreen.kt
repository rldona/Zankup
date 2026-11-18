package com.zankup.android.you

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.zankup.android.api.model.Me
import com.zankup.android.api.model.ProfileSummary
import com.zankup.android.api.model.SportTotals
import com.zankup.android.designsystem.components.ErrorState
import com.zankup.android.designsystem.components.ListRow
import com.zankup.android.designsystem.components.Skeleton
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupCard
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.components.ZankupTopBar
import com.zankup.android.designsystem.theme.Numbers
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.Units
import com.zankup.android.domain.formatNumber
import com.zankup.android.network.ApiResult
import com.zankup.android.ui.Formats
import com.zankup.android.ui.LocalFormats
import com.zankup.android.ui.sportName
import com.zankup.android.ui.sportOf
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The You tab (OS-1160), as the React Native app's: who you are (editable with the pencil), the way to
 * the rest of the app (Stats, Estimates, the Coach when the server has a model, Gear, Import, Account,
 * Settings), then the summary: the physical data, this year and all time by sport, and the records.
 * [listState] lets the tab bar scroll it back to the top.
 */
@Composable
fun YouScreen(
    onStats: () -> Unit,
    onEstimates: () -> Unit,
    onCoach: () -> Unit,
    onGear: () -> Unit,
    onImport: () -> Unit,
    onAccount: () -> Unit,
    onSettings: () -> Unit,
    onOpenActivity: (Long) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    model: YouViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) { model.refresh() }
    val colors = ZankupTheme.colors
    val me by model.me.collectAsStateWithLifecycle()
    val summary by model.summary.collectAsStateWithLifecycle()
    val ai by model.ai.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    val hidden by model.physicalHidden.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        containerColor = colors.page,
        topBar = { ZankupTopBar(stringResource(R.string.you_title)) }
    ) { padding ->
        val direction = LocalLayoutDirection.current
        val spacing = ZankupTheme.spacing
        val side = Modifier.padding(horizontal = spacing.gutter)
        val person = me.data
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(direction),
                end = padding.calculateEndPadding(direction),
                top = padding.calculateTopPadding() + spacing.xs,
                bottom = padding.calculateBottomPadding() + spacing.l
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            if (person == null) {
                if (me.failure != null) {
                    item("error") {
                        ErrorState(
                            onRetry = model::refresh,
                            offline = me.failure is ApiResult.Offline,
                            modifier = side
                        )
                    }
                } else {
                    item("skeleton") { Box(side) { YouSkeleton() } }
                }
                return@LazyColumn
            }
            item("header") { Header(person, onEdit = { editing = true }, modifier = side) }
            item("menu") {
                Menu(
                    coach = ai.data?.available == true,
                    onStats = onStats,
                    onEstimates = onEstimates,
                    onCoach = onCoach,
                    onGear = onGear,
                    onImport = onImport,
                    onAccount = onAccount,
                    onSettings = onSettings,
                    modifier = side
                )
            }
            val data = summary.data
            when {
                data != null -> {
                    val hrMax = settings.data?.let { it.hrMax ?: it.hrMaxEstimate }
                    item("physical") {
                        Physical(person, hrMax, hidden, model::togglePhysical, side)
                    }
                    summaryItems(data, onOpenActivity, side)
                }
                summary.failure != null -> item("summary-error") { ErrorLine(side) }
                else -> item("summary-skeleton") { Box(side) { SummarySkeleton() } }
            }
        }
    }
    val person = me.data
    if (editing && person != null) {
        EditProfileSheet(person, model, onDone = { editing = false })
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.summaryItems(
    data: ProfileSummary,
    onOpenActivity: (Long) -> Unit,
    side: Modifier
) {
    if (data.thisYear.isNotEmpty()) {
        item("year") {
            ZankupCard(side) {
                CardTitle(stringResource(R.string.you_by_year))
                SportRows(data.thisYear)
            }
        }
    }
    item("all-time") {
        ZankupCard(side) {
            CardTitle(stringResource(R.string.you_all_time))
            data.since?.let { Muted(stringResource(R.string.you_since, day(it))) }
            SportRows(data.allTime)
        }
    }
    item("records") { Records(data, onOpenActivity, side) }
}

// --- the header ------------------------------------------------------------------------------------

@Composable
private fun Header(me: Me, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ZankupTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val avatar = me.avatarUrl
        if (avatar != null) {
            AsyncImage(
                model = avatar,
                contentDescription = null,
                modifier = Modifier.size(64.dp).clip(CircleShape).background(colors.surface2)
            )
        } else {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(colors.surface2),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    initials(me.displayName, me.email),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.fg
                )
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(
                me.displayName?.takeIf { it.isNotBlank() } ?: me.email,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }
            )
            // Only where you are: the watch belongs to each activity, Garmin to Account.
            me.location?.takeIf { it.isNotBlank() }?.let { Muted(it) }
        }
        IconButton(onClick = onEdit, modifier = Modifier.align(Alignment.Top)) {
            ZankupIcon(ZIcon.Edit, contentDescription = stringResource(R.string.you_edit), tint = colors.fg)
        }
    }
}

// --- the menu --------------------------------------------------------------------------------------

@Composable
private fun Menu(
    coach: Boolean,
    onStats: () -> Unit,
    onEstimates: () -> Unit,
    onCoach: () -> Unit,
    onGear: () -> Unit,
    onImport: () -> Unit,
    onAccount: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    ZankupCard(modifier) {
        ListRow(stringResource(R.string.you_stats), icon = ZIcon.BarChart, onClick = onStats)
        ListRow(stringResource(R.string.you_estimates), icon = ZIcon.Gauge, onClick = onEstimates)
        if (coach) ListRow(stringResource(R.string.you_coach), icon = ZIcon.Coach, onClick = onCoach)
        ListRow(stringResource(R.string.you_gear), icon = ZIcon.Steps, onClick = onGear)
        ListRow(stringResource(R.string.you_import), icon = ZIcon.Upload, onClick = onImport)
        ListRow(stringResource(R.string.you_account), icon = ZIcon.Account, onClick = onAccount)
        ListRow(stringResource(R.string.you_settings), icon = ZIcon.Settings, onClick = onSettings)
    }
}

// --- the summary -----------------------------------------------------------------------------------

@Composable
private fun Physical(me: Me, hrMax: Int?, hidden: Boolean, onToggle: () -> Unit, modifier: Modifier) {
    val colors = ZankupTheme.colors
    val f = LocalFormats.current
    val today = remember { LocalDate.now() }
    val values = buildList {
        me.birthDate?.let { ageOn(it, today) }?.let {
            add(stringResource(R.string.you_age) to pluralStringResource(R.plurals.you_years, it, it))
        }
        me.sex?.let { add(stringResource(R.string.you_sex) to sexName(it.value)) }
        me.weightKg?.let { kg ->
            val unit = stringResource(if (f.units == Units.IMPERIAL) R.string.you_unit_lb else R.string.you_unit_kg)
            add(stringResource(R.string.you_weight) to "${formatNumber(weightIn(kg, f.units), f.locale, 0, 1)} $unit")
        }
        me.heightCm?.let { cm ->
            val unit = stringResource(if (f.units == Units.IMPERIAL) R.string.you_unit_in else R.string.you_unit_cm)
            add(stringResource(R.string.you_height) to "${heightIn(cm, f.units)} $unit")
        }
        hrMax?.let { add(stringResource(R.string.you_hr_max) to "$it bpm") }
    }
    if (values.isEmpty()) return
    ZankupCard(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.you_physical),
                Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
                color = colors.fg
            )
            IconButton(onClick = onToggle, modifier = Modifier.size(36.dp)) {
                ZankupIcon(
                    if (hidden) ZIcon.Hide else ZIcon.Show,
                    contentDescription = stringResource(
                        if (hidden) R.string.you_show_physical else R.string.you_hide_physical
                    ),
                    tint = colors.muted,
                    size = 22.dp
                )
            }
        }
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            values.forEach { (label, value) ->
                Column(Modifier.widthIn(min = 72.dp)) {
                    Text(
                        if (hidden) "•••" else value,
                        style = MaterialTheme.typography.titleLarge.merge(Numbers.tabular),
                        color = colors.fg
                    )
                    Text(label, style = MaterialTheme.typography.labelMedium, color = colors.muted)
                }
            }
        }
    }
}

@Composable
private fun SportRows(rows: List<SportTotals>) {
    val colors = ZankupTheme.colors
    val f = LocalFormats.current
    rows.forEach { s ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(sportName(sportOf(s.sport))),
                    color = colors.fg,
                    fontWeight = FontWeight.SemiBold
                )
                Muted(pluralStringResource(R.plurals.you_activities, s.count, s.count))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(f.distance(s.distanceM), color = colors.fg, style = Numbers.tabular)
                Muted(f.hoursMinutes(s.movingS))
            }
        }
    }
}

@Composable
private fun Records(data: ProfileSummary, onOpenActivity: (Long) -> Unit, modifier: Modifier) {
    val colors = ZankupTheme.colors
    val f = LocalFormats.current
    ZankupCard(modifier) {
        CardTitle(stringResource(R.string.you_records))
        if (data.records.isEmpty()) Muted(stringResource(R.string.you_no_records))
        data.records.filter { effortShown(it.distanceKey, f.units) }.forEach { r ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onOpenActivity(r.activityId.toLong()) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(effortName(r.distanceKey), color = colors.accent, fontWeight = FontWeight.SemiBold)
                    val pace = if (r.timeS > 0) f.unit.pace(r.distanceM / r.timeS) else "—"
                    Muted("$pace · ${day(r.date)}")
                }
                Text(
                    f.duration(r.timeS),
                    color = colors.fg,
                    fontWeight = FontWeight.Bold,
                    style = Numbers.tabular
                )
            }
        }
    }
}

// --- bits ------------------------------------------------------------------------------------------

@Composable
internal fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodyMedium, color = ZankupTheme.colors.muted)
}

@Composable
private fun CardTitle(text: String) {
    Text(
        text,
        Modifier.padding(bottom = 6.dp).semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        color = ZankupTheme.colors.fg
    )
}

@Composable
private fun ErrorLine(modifier: Modifier = Modifier) {
    Muted(stringResource(R.string.you_error), modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
internal fun sexName(value: String): String = stringResource(
    when (value) {
        "female" -> R.string.you_sex_female
        "male" -> R.string.you_sex_male
        "other" -> R.string.you_sex_other
        else -> R.string.you_sex_none
    }
)

@Composable
private fun effortName(key: String): String = when (key) {
    "400m" -> stringResource(R.string.you_effort_400m)
    "half_mile" -> stringResource(R.string.you_effort_half_mile)
    "1k" -> stringResource(R.string.you_effort_1k)
    "1mile" -> stringResource(R.string.you_effort_1mile)
    "2mile" -> stringResource(R.string.you_effort_2mile)
    "5k" -> stringResource(R.string.you_effort_5k)
    "10k" -> stringResource(R.string.you_effort_10k)
    "15k" -> stringResource(R.string.you_effort_15k)
    "10mile" -> stringResource(R.string.you_effort_10mile)
    "half_marathon" -> stringResource(R.string.you_effort_half_marathon)
    "marathon" -> stringResource(R.string.you_effort_marathon)
    else -> key
}

/** "12 Nov 2025": a day (or the start of an instant) of the API in the phone's language. */
@Composable
private fun day(iso: String): String {
    val f: Formats = LocalFormats.current
    val date = isoDate(iso.take(10)) ?: return iso
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(f.locale).format(date)
}

@Composable
private fun YouSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Skeleton(Modifier.size(64.dp), height = 64.dp)
            Column(Modifier.padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Skeleton(Modifier.fillMaxWidth(0.5f), height = 22.dp)
                Skeleton(Modifier.fillMaxWidth(0.3f), height = 14.dp)
            }
        }
        Skeleton(Modifier.fillMaxWidth(), height = 340.dp)
        SummarySkeleton()
    }
}

@Composable
private fun SummarySkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Skeleton(Modifier.fillMaxWidth(), height = 96.dp)
        Skeleton(Modifier.fillMaxWidth(), height = 140.dp)
        Skeleton(Modifier.fillMaxWidth(), height = 140.dp)
    }
}
