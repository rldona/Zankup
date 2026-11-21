package com.zankup.android.health.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.api.model.HealthSourceStatus
import com.zankup.android.designsystem.components.ErrorState
import com.zankup.android.designsystem.components.Skeleton
import com.zankup.android.designsystem.components.ZankupCard
import com.zankup.android.designsystem.components.ZankupChip
import com.zankup.android.designsystem.components.ZankupTopBar
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.health.R
import com.zankup.android.healthconnect.HealthConnect
import com.zankup.android.healthconnect.ReadResult
import com.zankup.android.network.ApiResult
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

private val METRICS =
    listOf(
        "sleep" to R.string.hs_metric_sleep,
        "hrv" to R.string.hs_metric_hrv,
        "resting_hr" to R.string.hs_metric_resting_hr,
        "spo2" to R.string.hs_metric_spo2,
        "respiration" to R.string.hs_metric_respiration,
        "steps" to R.string.hs_metric_steps
    )

private fun sourceName(source: HealthSourceStatus.Source) = when (source) {
    HealthSourceStatus.Source.GARMIN -> "Garmin"
    HealthSourceStatus.Source.GOOGLE_HEALTH -> "Google Health"
    HealthSourceStatus.Source.HEALTH_CONNECT -> "Health Connect"
}

private val WHEN = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

private fun whenText(instant: Instant) = WHEN.format(instant.atZone(ZoneId.systemDefault()))

/**
 * Settings › Health (OS-1151), as the React Native app has it: bringing the last year, Health Connect on
 * this phone (asking for its permissions and reading), each source with what it sent, disconnecting
 * Google Health and deleting what a source sent, and the source to prefer per metric. Connecting Google
 * Health needs the web, since its sign-in comes back to the browser.
 */
@Composable
fun HealthSettingsScreen(
    onBack: () -> Unit,
    /** Cards that belong to other modules: Google Health's switch for the instance. */
    extra: @Composable () -> Unit = {},
    model: HealthSettingsModel = hiltViewModel()
) {
    val state by model.settings.state.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { model.refresh() }
    Scaffold(
        containerColor = ZankupTheme.colors.page,
        topBar = { ZankupTopBar(stringResource(R.string.hs_title), onBack = onBack) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                // The settings' measures: the side gutter, 8 dp on top and between cards, 24 dp at the end.
                .padding(
                    start = ZankupTheme.spacing.gutter,
                    top = ZankupTheme.spacing.s,
                    end = ZankupTheme.spacing.gutter,
                    bottom = ZankupTheme.spacing.l
                ),
            verticalArrangement = Arrangement.spacedBy(ZankupTheme.spacing.s)
        ) {
            History(model)
            HealthConnectCard(model)
            extra()
            val settings = state?.data
            when {
                settings != null -> {
                    Sources(settings.sources, model)
                    val usable = settings.sources.filter { it.connected || it.nights + it.days > 0 }
                    if (usable.size > 1) Priority(usable, settings.priority, model)
                }
                state?.failure != null ->
                    ErrorState(
                        onRetry = { scope.launch { model.refresh() } },
                        offline = state?.failure is ApiResult.Offline
                    )
                else -> Skeleton(Modifier.fillMaxWidth(), height = 160.dp)
            }
        }
    }
}

@Composable
private fun Card(title: String, content: @Composable ColumnScope.() -> Unit) {
    ZankupCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = ZankupTheme.colors.fg)
            content()
        }
    }
}

@Composable
private fun Note(text: String, live: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = ZankupTheme.colors.muted,
        modifier = if (live) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier
    )
}

@Composable
private fun counts(result: ReadResult) = Pair(
    pluralStringResource(R.plurals.hs_nights, result.nights, result.nights),
    pluralStringResource(R.plurals.hs_days, result.days, result.days)
)

/** What a read or a request came back with, to show under its card. */
private sealed interface Outcome {
    data class Sent(val result: ReadResult) : Outcome

    data class History(val queued: List<String>, val read: ReadResult?) : Outcome

    data object Denied : Outcome

    data object Failed : Outcome
}

@Composable
private fun History(model: HealthSettingsModel) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<Outcome?>(null) }
    val last by model.healthConnect.lastRead.collectAsStateWithLifecycle()
    Card(stringResource(R.string.hs_history_title)) {
        Note(stringResource(R.string.hs_history_note))
        OutlinedButton(enabled = !busy, onClick = {
            busy = true
            outcome = null
            scope.launch {
                val queued = model.askHistory()
                val read = if (last != null) (model.readHistory() as? ApiResult.Ok)?.value else null
                outcome = if (queued == null) Outcome.Failed else Outcome.History(queued, read)
                model.refresh()
                busy = false
            }
        }) {
            Text(stringResource(if (busy) R.string.hs_history_working else R.string.hs_history_bring))
        }
        when (val o = outcome) {
            is Outcome.History -> {
                val lines = buildList {
                    if (o.queued.isNotEmpty()) {
                        val names = o.queued.joinToString(", ") { q ->
                            HealthSourceStatus.Source.entries.firstOrNull { it.value == q }?.let(::sourceName) ?: q
                        }
                        add(stringResource(R.string.hs_history_queued, names))
                    }
                    if (o.read != null) {
                        val (nights, days) = counts(o.read)
                        add(stringResource(R.string.hs_history_hc, nights, days))
                    }
                }
                Note(lines.joinToString(" ").ifEmpty { stringResource(R.string.hs_history_none) }, live = true)
            }
            Outcome.Failed -> Note(stringResource(R.string.hs_error), live = true)
            else -> Unit
        }
    }
}

@Composable
private fun HealthConnectCard(model: HealthSettingsModel) {
    val scope = rememberCoroutineScope()
    val available = remember { model.healthConnect.available() }
    val last by model.healthConnect.lastRead.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<Outcome?>(null) }
    fun read() {
        busy = true
        scope.launch {
            outcome = when (val r = model.read()) {
                is ApiResult.Ok -> Outcome.Sent(r.value)
                is ApiResult.Failure -> Outcome.Failed
                null -> Outcome.Denied
            }
            busy = false
        }
    }
    val ask =
        rememberLauncherForActivityResult(model.healthConnect.permissionContract()) { granted ->
            if (granted.any { it in HealthConnect.READ }) read() else outcome = Outcome.Denied
        }
    Card(stringResource(R.string.hs_hc_title)) {
        Note(stringResource(if (available) R.string.hs_hc_note else R.string.hs_hc_unavailable))
        if (available) {
            Button(enabled = !busy, onClick = {
                outcome = null
                ask.launch(HealthConnect.READ + HealthConnect.HISTORY)
            }) { Text(stringResource(R.string.hs_hc_connect)) }
            if (last != null) {
                OutlinedButton(enabled = !busy, onClick = { read() }) { Text(stringResource(R.string.hs_hc_read)) }
            }
        }
        last?.let { Note(stringResource(R.string.hs_hc_last, whenText(it))) }
        when (val o = outcome) {
            is Outcome.Sent -> {
                val (nights, days) = counts(o.result)
                Note(stringResource(R.string.hs_hc_done, nights, days), live = true)
            }
            Outcome.Denied -> Note(stringResource(R.string.hs_hc_denied), live = true)
            Outcome.Failed -> Note(stringResource(R.string.hs_error), live = true)
            else -> Unit
        }
    }
}

@Composable
private fun sourceNote(s: HealthSourceStatus): String {
    val sent = s.nights + s.days > 0
    val parts = mutableListOf<String>()
    parts +=
        if (sent || s.connected) {
            val date = s.lastSyncAt?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
            val synced = date?.let { stringResource(R.string.hs_last_sync, whenText(it)) }
                ?: stringResource(R.string.hs_never)
            val count = stringResource(
                R.string.hs_counts,
                pluralStringResource(R.plurals.hs_nights, s.nights, s.nights),
                pluralStringResource(R.plurals.hs_days, s.days, s.days)
            )
            "$synced · $count"
        } else {
            when {
                s.source == HealthSourceStatus.Source.HEALTH_CONNECT -> stringResource(R.string.hs_hc_source_note)
                !s.available && s.source == HealthSourceStatus.Source.GARMIN -> stringResource(R.string.hs_garmin_off)
                !s.available -> stringResource(R.string.hs_google_off)
                s.source == HealthSourceStatus.Source.GARMIN -> stringResource(R.string.hs_never)
                else -> stringResource(R.string.hs_google_on_web)
            }
        }
    if (s.source == HealthSourceStatus.Source.GOOGLE_HEALTH && !s.available && (s.connected || sent)) {
        parts += stringResource(R.string.hs_google_off)
    }
    if (s.needsReconnect == true) parts += stringResource(R.string.hs_google_reconnect)
    if (!s.missing.isNullOrEmpty()) parts += stringResource(R.string.hs_google_missing)
    return parts.joinToString(" ")
}

@Composable
private fun Sources(sources: List<HealthSourceStatus>, model: HealthSettingsModel) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var wipe by remember { mutableStateOf<HealthSourceStatus.Source?>(null) }
    Card(stringResource(R.string.hs_sources)) {
        sources.forEach { s ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(sourceName(s.source), style = MaterialTheme.typography.bodyLarge, color = ZankupTheme.colors.fg)
                Note(sourceNote(s))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (s.source == HealthSourceStatus.Source.GOOGLE_HEALTH && s.connected) {
                        OutlinedButton(enabled = !busy, onClick = {
                            busy = true
                            scope.launch {
                                model.disconnectGoogle()
                                busy = false
                            }
                        }) { Text(stringResource(R.string.hs_google_disconnect)) }
                    }
                    if (s.nights + s.days > 0) {
                        OutlinedButton(enabled = !busy, onClick = { wipe = s.source }) {
                            Text(stringResource(R.string.hs_delete))
                        }
                    }
                }
            }
        }
    }
    wipe?.let { source ->
        AlertDialog(
            onDismissRequest = { wipe = null },
            title = { Text(sourceName(source)) },
            text = { Text(stringResource(R.string.hs_delete_confirm, sourceName(source))) },
            confirmButton = {
                TextButton(onClick = {
                    wipe = null
                    busy = true
                    scope.launch {
                        model.wipe(source.value)
                        busy = false
                    }
                }) { Text(stringResource(R.string.hs_delete), color = ZankupTheme.colors.formNegative) }
            },
            dismissButton = { TextButton(onClick = { wipe = null }) { Text(stringResource(R.string.hs_cancel)) } },
            containerColor = ZankupTheme.colors.surface
        )
    }
}

@Composable
private fun Priority(
    usable: List<HealthSourceStatus>,
    priority: Map<String, List<String>>,
    model: HealthSettingsModel
) {
    val scope = rememberCoroutineScope()
    Card(stringResource(R.string.hs_priority)) {
        Note(stringResource(R.string.hs_priority_note))
        METRICS.forEach { (metric, label) ->
            val chosen = priority[metric]?.firstOrNull()
            Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = ZankupTheme.colors.muted)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ZankupChip(stringResource(R.string.hs_automatic), chosen == null, onClick = {
                    scope.launch { model.prefer(metric, null) }
                })
                usable.forEach { s ->
                    ZankupChip(sourceName(s.source), chosen == s.source.value, onClick = {
                        scope.launch { model.prefer(metric, s.source.value) }
                    })
                }
            }
        }
    }
}
