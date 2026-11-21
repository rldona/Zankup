package com.zankup.android.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.api.model.EventOut
import com.zankup.android.designsystem.components.EmptyState
import com.zankup.android.designsystem.components.ErrorState
import com.zankup.android.designsystem.components.RowDivider
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.components.color
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.network.ApiResult
import com.zankup.android.tools.ui.ListSkeleton
import com.zankup.android.tools.ui.Muted
import com.zankup.android.tools.ui.ToolsScaffold
import com.zankup.android.tools.ui.mediumDate
import com.zankup.android.tools.ui.warning
import com.zankup.android.ui.LocalFormats
import com.zankup.android.ui.family
import com.zankup.android.ui.sportName
import com.zankup.android.ui.sportOf

/**
 * Notifications (OS-1163): what the engine noticed lately — a new record, an estimate going up, a risky
 * jump in load — worded as the notification. One about an activity opens it.
 */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenActivity: (Long) -> Unit,
    modifier: Modifier = Modifier,
    model: EventsModel = hiltViewModel()
) {
    LaunchedEffect(Unit) { model.refresh() }
    val ui by model.notices.collectAsStateWithLifecycle()
    val units = LocalFormats.current.units
    val notices = ui.data
    val shown = notices.orEmpty().filter { n -> n.event?.let { eventShown(it.kind, it.payload, units) } ?: true }
    LaunchedEffect(notices) { if (notices != null) model.markSeen() }
    ToolsScaffold(
        stringResource(R.string.tools_notifications_title),
        onBack,
        modifier,
        actions = { NotificationsMenu(model::deleteAll) },
        edgeToEdge = true
    ) {
        when {
            ui.loading -> item("loading") { ListSkeleton() }
            notices == null -> item("error") {
                ErrorState(onRetry = model::refresh, offline = ui.failure is ApiResult.Offline)
            }
            notices.isEmpty() -> item("empty") {
                EmptyState(stringResource(R.string.tools_notifications_empty), icon = ZIcon.Notifications)
            }
            else -> item("events") {
                val colors = ZankupTheme.colors
                Surface(
                    Modifier.fillMaxWidth(),
                    color = colors.surface,
                    contentColor = colors.fg
                ) {
                    Column {
                        shown.forEachIndexed { i, n ->
                            NoticeRow(n, onOpenActivity, model::markRead)
                            if (i < shown.lastIndex) RowDivider(inset = 14.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationsMenu(onDeleteAll: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val colors = ZankupTheme.colors
    Box {
        IconButton(onClick = { open = true }) {
            ZankupIcon(ZIcon.More, contentDescription = stringResource(R.string.tools_notifications_menu))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tools_notifications_delete_all), color = colors.formNegative) },
                onClick = {
                    open = false
                    onDeleteAll()
                },
                leadingIcon = {
                    ZankupIcon(ZIcon.Delete, contentDescription = null, tint = colors.formNegative)
                }
            )
        }
    }
}

@Composable
private fun NoticeRow(notice: Notice, onOpenActivity: (Long) -> Unit, onRead: (Notice) -> Unit) {
    val target = notice.activity?.id?.toLong() ?: notice.event?.let { eventActivity(it.payload) }
    val open: () -> Unit = {
        onRead(notice)
        if (target != null) onOpenActivity(target)
    }
    val event = notice.event
    val activity = notice.activity
    when {
        event != null -> EventRow(event, notice.read, open)
        activity != null -> ActivityRow(activity, notice.read, open)
    }
}

@Composable
private fun EventRow(e: EventOut, read: Boolean, onClick: () -> Unit) {
    val colors = ZankupTheme.colors
    val formats = LocalFormats.current
    val icon = when (e.kind) {
        "record" -> ZIcon.Trophy
        "estimate_up" -> ZIcon.TrendingUp
        "ramp" -> ZIcon.Warning
        else -> ZIcon.Notifications
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (read) Color.Transparent else colors.surface2)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ZankupIcon(icon, contentDescription = null, tint = if (e.kind == "ramp") warning else colors.accent)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(eventText(eventWording(e.kind, e.payload, formats.unit)), color = colors.fg, fontSize = 15.sp)
            parseInstant(e.createdAt)?.let { Muted(mediumDate(it, formats.locale)) }
        }
    }
}

@Composable
private fun ActivityRow(a: com.zankup.android.api.model.ActivitySummary, read: Boolean, onClick: () -> Unit) {
    val colors = ZankupTheme.colors
    val formats = LocalFormats.current
    val sport = sportOf(a.sport)
    val title = a.name ?: stringResource(sportName(sport))
    val figures = listOfNotNull(
        a.distanceM?.takeIf { it > 0 }?.let { formats.distance(it) },
        formats.duration(a.movingS ?: a.durationS)
    ).joinToString(" · ")
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (read) Color.Transparent else colors.surface2)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ZankupIcon(sport.family.icon, contentDescription = null, tint = sport.family.color)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = colors.fg, fontSize = 15.sp)
            Muted(figures)
            parseInstant(a.startUtc)?.let { Muted(mediumDate(it, formats.locale)) }
        }
    }
}

/** The event in words, as its notification says it. */
@Composable
private fun eventText(w: EventWording): String = when (w) {
    is EventWording.Record -> stringResource(R.string.tools_event_record, effortName(w.distanceKey), w.time)
    is EventWording.EstimateUp -> stringResource(R.string.tools_event_estimate_up, estimateName(w.kind), w.value)
    is EventWording.Ramp -> stringResource(R.string.tools_event_ramp, w.ramp)
    is EventWording.Unknown -> w.kind
}

@Composable
private fun effortName(key: String): String = when (key) {
    "400m" -> stringResource(R.string.tools_effort_400m)
    "half_mile" -> stringResource(R.string.tools_effort_half_mile)
    "1k" -> stringResource(R.string.tools_effort_1k)
    "1mile" -> stringResource(R.string.tools_effort_1mile)
    "2mile" -> stringResource(R.string.tools_effort_2mile)
    "5k" -> stringResource(R.string.tools_effort_5k)
    "10k" -> stringResource(R.string.tools_effort_10k)
    "15k" -> stringResource(R.string.tools_effort_15k)
    "10mile" -> stringResource(R.string.tools_effort_10mile)
    "half_marathon" -> stringResource(R.string.tools_effort_half_marathon)
    "marathon" -> stringResource(R.string.tools_effort_marathon)
    else -> key
}

@Composable
private fun estimateName(kind: String): String = when (kind) {
    "ftp" -> stringResource(R.string.tools_estimate_ftp)
    "critical_speed" -> stringResource(R.string.tools_estimate_critical_speed)
    "lthr" -> stringResource(R.string.tools_estimate_lthr)
    "hr_max" -> stringResource(R.string.tools_estimate_hr_max)
    "vdot" -> stringResource(R.string.tools_estimate_vdot)
    "race_5k" -> stringResource(R.string.tools_estimate_race_5k)
    "race_10k" -> stringResource(R.string.tools_estimate_race_10k)
    "race_half_marathon" -> stringResource(R.string.tools_estimate_race_half_marathon)
    "race_marathon" -> stringResource(R.string.tools_estimate_race_marathon)
    else -> kind
}
