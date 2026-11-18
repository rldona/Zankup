package com.zankup.android.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.api.model.SyncStatus
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupCard
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.tools.ui.Announced
import com.zankup.android.tools.ui.Badge
import com.zankup.android.tools.ui.Bar
import com.zankup.android.tools.ui.Heading
import com.zankup.android.tools.ui.ListSkeleton
import com.zankup.android.tools.ui.Muted
import com.zankup.android.tools.ui.Tone
import com.zankup.android.tools.ui.ToolsScaffold
import com.zankup.android.tools.ui.mediumDateTime
import com.zankup.android.ui.LocalFormats

/**
 * Sync (OS-1163): Garmin syncs on its own; "Sync now"; what the current run is doing (with its windows
 * as a bar); loading the whole history; and the recent runs, folded.
 */
@Composable
fun SyncScreen(onBack: () -> Unit, modifier: Modifier = Modifier, model: SyncModel = hiltViewModel()) {
    LaunchedEffect(Unit) { model.refresh() }
    val runs by model.runs.collectAsStateWithLifecycle()
    val starting by model.starting.collectAsStateWithLifecycle()
    val failure by model.failure.collectAsStateWithLifecycle()
    var showRuns by rememberSaveable { mutableStateOf(false) }
    val list = runs.data.orEmpty()
    val latest = list.firstOrNull()
    val busy = starting || runActive(latest)
    ToolsScaffold(stringResource(R.string.tools_sync_title), onBack, modifier) {
        item("auto") { Muted(stringResource(R.string.tools_sync_auto)) }
        item("now") {
            Button(onClick = { model.start(false) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.tools_sync_now))
            }
        }
        failure?.let { f ->
            item("failure") {
                val text = when (f) {
                    StartFailure.NotConfigured -> stringResource(R.string.tools_sync_not_configured)
                    is StartFailure.Other -> f.detail ?: stringResource(R.string.tools_error)
                }
                Announced(text)
            }
        }
        if (runs.loading) item("loading") { ListSkeleton(1) }
        if (latest != null) item("latest") { ZankupCard { RunProgress(latest) } }
        item("history") { Heading(stringResource(R.string.tools_sync_history), Modifier.padding(top = 8.dp)) }
        item("history-help") { Muted(stringResource(R.string.tools_sync_history_help)) }
        item("history-start") {
            OutlinedButton(onClick = { model.start(true) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.tools_sync_history_start))
            }
        }
        if (list.isNotEmpty()) {
            item("recent") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = showRuns, role = Role.Button, onValueChange = { showRuns = it })
                        .padding(top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Heading(stringResource(R.string.tools_sync_recent), Modifier.weight(1f))
                    Muted(list.size.toString())
                    ZankupIcon(
                        if (showRuns) ZIcon.ArrowUp else ZIcon.ArrowDown,
                        contentDescription = null,
                        tint = ZankupTheme.colors.muted
                    )
                }
            }
            if (showRuns) {
                item("runs") {
                    ZankupCard {
                        list.forEach { run -> RunRow(run) }
                    }
                }
            }
        }
    }
}

@Composable
private fun successText(count: Int) = if (count == 0) {
    stringResource(R.string.tools_sync_success_none)
} else {
    pluralStringResource(R.plurals.tools_sync_success, count, count)
}

/** What a run is doing or how it ended, and its numbers. */
@Composable
private fun RunProgress(run: SyncStatus) {
    val p = run.progress
    Column(
        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            when (run.status) {
                SyncStatus.Status.QUEUED -> stringResource(R.string.tools_sync_queued)
                SyncStatus.Status.RUNNING -> stringResource(R.string.tools_sync_running)
                SyncStatus.Status.SUCCESS -> successText(run.newCount)
                SyncStatus.Status.ERROR -> stringResource(R.string.tools_sync_error, run.error.orEmpty())
            },
            color = ZankupTheme.colors.fg,
            fontSize = 15.sp
        )
        runWindows(run)?.let { (window, windows) ->
            Bar(window.toFloat() / windows, stringResource(R.string.tools_sync_title))
            Muted(stringResource(R.string.tools_sync_window, window, windows))
        }
        p.listed?.let { listed ->
            Muted(stringResource(R.string.tools_sync_counts, listed, p.new ?: 0, p.downloaded ?: 0))
        }
        if (run.pendingImports > 0) {
            Muted(pluralStringResource(R.plurals.tools_sync_pending, run.pendingImports, run.pendingImports))
        }
        p.noFit?.takeIf { it > 0 }?.let { Muted(pluralStringResource(R.plurals.tools_sync_no_fit, it, it)) }
    }
}

@Composable
private fun triggerName(trigger: SyncStatus.Trigger) = stringResource(
    when (trigger) {
        SyncStatus.Trigger.CRON -> R.string.tools_sync_trigger_cron
        SyncStatus.Trigger.BUTTON -> R.string.tools_sync_trigger_button
        SyncStatus.Trigger.INITIAL -> R.string.tools_sync_trigger_initial
        SyncStatus.Trigger.IMPORT -> R.string.tools_sync_trigger_import
    }
)

@Composable
private fun statusName(status: SyncStatus.Status) = stringResource(
    when (status) {
        SyncStatus.Status.QUEUED -> R.string.tools_sync_status_queued
        SyncStatus.Status.RUNNING -> R.string.tools_sync_status_running
        SyncStatus.Status.SUCCESS -> R.string.tools_sync_status_success
        SyncStatus.Status.ERROR -> R.string.tools_sync_status_error
    }
)

/** One past run: what started it, when, what it found or its error, and its status. */
@Composable
private fun RunRow(run: SyncStatus) {
    val locale = LocalFormats.current.locale
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(triggerName(run.trigger), fontWeight = FontWeight.SemiBold, color = ZankupTheme.colors.fg)
            val parts = listOfNotNull(
                parseInstant(run.startedAt)?.let { mediumDateTime(it, locale) },
                if (run.status == SyncStatus.Status.SUCCESS) successText(run.newCount) else null,
                run.error
            )
            Muted(parts.joinToString(" · "))
        }
        val tone = when (run.status) {
            SyncStatus.Status.SUCCESS -> Tone.GOOD
            SyncStatus.Status.ERROR -> Tone.BAD
            else -> Tone.NEUTRAL
        }
        Badge(statusName(run.status), tone)
    }
}
