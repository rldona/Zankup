package com.zankup.android.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.api.model.Comparison
import com.zankup.android.api.model.ImportOut
import com.zankup.android.api.model.MetricComparison
import com.zankup.android.designsystem.components.ChipPicker
import com.zankup.android.designsystem.components.SectionHeader
import com.zankup.android.designsystem.components.ZankupCard
import com.zankup.android.designsystem.theme.Numbers
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.formatNumber
import com.zankup.android.tools.ui.Announced
import com.zankup.android.tools.ui.Badge
import com.zankup.android.tools.ui.Bar
import com.zankup.android.tools.ui.InfoButton
import com.zankup.android.tools.ui.ListSkeleton
import com.zankup.android.tools.ui.Muted
import com.zankup.android.tools.ui.Tone
import com.zankup.android.tools.ui.ToolsScaffold
import com.zankup.android.tools.ui.mediumDate
import com.zankup.android.tools.ui.warning
import com.zankup.android.ui.LocalFormats

/**
 * FIT, TCX and GPX files have no MIME type Android knows, and providers label them differently: any
 * file is offered (as the React Native app), and the server checks it.
 */
private val PICKED_TYPES = arrayOf("*/*")

/** The metrics compared with Strava, in the order the chips show them. */
private val COMPARED = listOf(
    MetricComparison.Metric.EFFORT,
    MetricComparison.Metric.GRADE_ADJUSTED_DISTANCE,
    MetricComparison.Metric.CALORIES
)

/** At least this many activities for a metric to be compared. */
private const val MIN_COMPARED = 3

/**
 * Import (OS-1163): what can be imported, "Choose files" (several at once: FIT, TCX, GPX or ZIP), the
 * upload's progress, the imports with their counts and problems and, after a Strava export, how
 * Zankup's metrics compare with Strava's.
 */
@Composable
fun ImportScreen(
    onBack: () -> Unit,
    onOpenActivity: (Long) -> Unit,
    modifier: Modifier = Modifier,
    model: ImportModel = hiltViewModel()
) {
    LaunchedEffect(Unit) { model.refresh() }
    val imports by model.imports.collectAsStateWithLifecycle()
    val comparison by model.comparison.collectAsStateWithLifecycle()
    val progress by model.progress.collectAsStateWithLifecycle()
    val failed by model.failed.collectAsStateWithLifecycle()
    val locale = LocalFormats.current.locale
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        model.upload(uris)
    }
    ToolsScaffold(stringResource(R.string.tools_import_title), onBack, modifier) {
        item("help") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.tools_import_help), color = ZankupTheme.colors.fg)
                Muted("• " + stringResource(R.string.tools_import_strava))
                Muted("• " + stringResource(R.string.tools_import_garmin))
                Muted("• " + stringResource(R.string.tools_import_files))
            }
        }
        item("choose") {
            Button(
                onClick = { pick.launch(PICKED_TYPES) },
                enabled = progress == null,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.tools_import_choose)) }
        }
        progress?.let { p ->
            item("progress") {
                ZankupCard {
                    Text(
                        stringResource(R.string.tools_import_uploading, p.name) +
                            " · ${megabytes(p.sent, locale)} / ${megabytes(p.size, locale)}",
                        color = ZankupTheme.colors.fg
                    )
                    Bar(
                        if (p.size > 0) p.sent.toFloat() / p.size else 0f,
                        p.name,
                        Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
        if (failed) {
            item("failed") { Announced(stringResource(R.string.tools_import_upload_error), color = warning) }
        }
        item("history") { SectionHeader(stringResource(R.string.tools_import_history)) }
        val list = imports.data
        when {
            imports.loading -> item("loading") { ListSkeleton(2) }
            list == null -> item("error") { Announced(stringResource(R.string.tools_error)) }
            list.isEmpty() -> item("none") { Muted(stringResource(R.string.tools_import_none)) }
            else -> list.forEach { i -> item("import-${i.id}") { ImportRow(i, locale) } }
        }
        comparison.data?.takeIf { c -> c.metrics.any { it.count >= MIN_COMPARED } }?.let { c ->
            item("compare") { CompareWithStrava(c, onOpenActivity) }
        }
    }
}

@Composable
private fun statusName(status: String) = when (status) {
    "uploading" -> stringResource(R.string.tools_import_status_uploading)
    "queued" -> stringResource(R.string.tools_import_status_queued)
    "running" -> stringResource(R.string.tools_import_status_running)
    "done" -> stringResource(R.string.tools_import_status_done)
    "error" -> stringResource(R.string.tools_import_status_error)
    else -> status
}

@Composable
private fun kindName(kind: String) = when (kind) {
    "strava" -> stringResource(R.string.tools_import_kind_strava)
    "archive" -> stringResource(R.string.tools_import_kind_archive)
    "file" -> stringResource(R.string.tools_import_kind_file)
    else -> kind
}

/** One import: its file, status, size and kind, its counts and, folded, the files that could not be read. */
@Composable
private fun ImportRow(i: ImportOut, locale: java.util.Locale) {
    var open by rememberSaveable(i.id) { mutableStateOf(false) }
    ZankupCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    i.filename,
                    Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val tone = when (i.status) {
                    "done" -> Tone.GOOD
                    "error" -> Tone.BAD
                    else -> Tone.NEUTRAL
                }
                Badge(statusName(i.status), tone)
            }
            val line = listOfNotNull(
                parseInstant(i.createdAt)?.let { mediumDate(it, locale) },
                megabytes(i.propertySize.toLong(), locale),
                i.kind?.let { kindName(it) }
            )
            Muted(line.joinToString(" · "))
            if (importActive(i.status) && i.total > 0) Bar(i.processed.toFloat() / i.total, i.filename)
            if (i.total > 0) {
                val counts = stringResource(
                    R.string.tools_import_counts,
                    i.processed,
                    i.total,
                    i.created,
                    i.merged,
                    i.skipped,
                    i.failed
                )
                val photos = if (i.photos > 0) {
                    " · " + pluralStringResource(R.plurals.tools_import_photos, i.photos, i.photos)
                } else {
                    ""
                }
                Muted(counts + photos)
            }
            i.error?.let { Text(it, color = warning) }
            if (i.errors.isNotEmpty()) {
                OutlinedButton(onClick = { open = !open }, modifier = Modifier.fillMaxWidth()) {
                    Text(pluralStringResource(R.plurals.tools_import_problems, i.failed, i.failed))
                }
                if (open) i.errors.forEach { e -> Muted("${e.file}: ${e.error}") }
            }
        }
    }
}

@Composable
private fun metricName(metric: MetricComparison.Metric) = stringResource(
    when (metric) {
        MetricComparison.Metric.EFFORT -> R.string.tools_compare_effort
        MetricComparison.Metric.GRADE_ADJUSTED_DISTANCE -> R.string.tools_compare_grade_adjusted_distance
        MetricComparison.Metric.CALORIES -> R.string.tools_compare_calories
    }
)

/** How close Zankup's metrics are to what Strava computed, once a Strava export is in (OS-323). */
@Composable
private fun CompareWithStrava(data: Comparison, onOpenActivity: (Long) -> Unit) {
    val colors = ZankupTheme.colors
    val locale = LocalFormats.current.locale
    var metric by rememberSaveable { mutableStateOf(MetricComparison.Metric.EFFORT) }
    val current = data.metrics.firstOrNull { it.metric == metric }
    fun number(v: Double?, digits: Int) = v?.let { formatNumber(it, locale, 0, digits) } ?: "–"
    ZankupCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.tools_compare_title),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )
                InfoButton(stringResource(R.string.tools_compare_help))
            }
            ChipPicker(COMPARED, metric, { metricName(it) }, { metric = it })
            if (current != null && current.count >= MIN_COMPARED) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat(stringResource(R.string.tools_compare_count), current.count.toString(), Modifier.weight(1f))
                    Stat("R²", number(current.r2, 3), Modifier.weight(1f))
                    Stat(stringResource(R.string.tools_compare_mae), number(current.mae, 1), Modifier.weight(1f))
                    Stat(stringResource(R.string.tools_compare_ratio), number(current.ratio, 3), Modifier.weight(1f))
                }
                Text(stringResource(R.string.tools_compare_largest), fontWeight = FontWeight.Bold)
                current.largest.forEach { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { onOpenActivity(p.activityId.toLong()) }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.tools_compare_activity, p.activityId), color = colors.accent)
                        Text(
                            "Zankup ${number(p.ours, 0)} · Strava ${number(p.strava, 0)}",
                            style = Numbers.tabular,
                            color = colors.fg
                        )
                    }
                }
            } else {
                Muted(stringResource(R.string.tools_compare_not_enough))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Muted(label)
        Text(value, style = Numbers.tabular, fontWeight = FontWeight.Bold, color = ZankupTheme.colors.fg)
    }
}
