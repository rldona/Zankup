package com.zankup.android.health

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.designsystem.components.SectionHeader
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupCard
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.components.ZankupSheet
import com.zankup.android.designsystem.components.ZankupTopBar
import com.zankup.android.designsystem.theme.Numbers
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.health.ui.ErrorLine
import com.zankup.android.health.ui.LineLayer
import com.zankup.android.health.ui.Muted
import com.zankup.android.health.ui.TrendChart
import com.zankup.android.health.ui.dayText
import com.zankup.android.health.ui.fullDay
import com.zankup.android.health.ui.signedWeight
import com.zankup.android.health.ui.weightText
import com.zankup.android.health.ui.weightValue
import com.zankup.android.ui.LocalFormats
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.launch

/** The weighing being written: a new one, or one opened from the list ([old] its day). */
private data class Editing(val day: LocalDate, val text: String, val old: LocalDate? = null)

/**
 * Weight, from Health (OS-1150): how it changed over a month to all time, as a line through the
 * weighings, and every weighing below, newest first. "+" adds one (a day has one: a second replaces
 * it), a weighing opens to be changed, and a long press deletes it. In the person's units.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WeightScreen(onBack: () -> Unit, modifier: Modifier = Modifier, model: WeightModel = hiltViewModel()) {
    LaunchedEffect(Unit) { model.refresh() }
    val colors = ZankupTheme.colors
    val formats = LocalFormats.current
    val today = model.today
    val weight by model.weight.collectAsStateWithLifecycle()
    val weighings = weight.data.orEmpty()
    var range by rememberSaveable { mutableStateOf(WeightRange.M3) }
    var editing by remember { mutableStateOf<Editing?>(null) }
    var deleting by remember { mutableStateOf<LocalDate?>(null) }
    var deleteFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val latest = weighings.lastOrNull()
    val fresh = { Editing(today, latest?.let { weightValue(it.weightKg, formats.units, formats.locale) }.orEmpty()) }

    Scaffold(
        modifier = modifier,
        containerColor = colors.page,
        topBar = {
            ZankupTopBar(stringResource(R.string.health_weight_title), onBack = onBack) {
                IconButton(onClick = { editing = fresh() }) {
                    ZankupIcon(ZIcon.Add, contentDescription = stringResource(R.string.health_weight_add))
                }
            }
        }
    ) { padding ->
        val direction = LocalLayoutDirection.current
        val spacing = ZankupTheme.spacing
        val side = Modifier.padding(horizontal = spacing.gutter)
        val from = weightRangeStart(range, today, weighings)
        val values = remember(weighings, from, today, formats.units) {
            dailyWeights(weighings, from, today).map { v -> v?.let { toUnit(it, formats.units) } }
        }
        val shown = values.filterNotNull()
        val moved = weightChange(weighings, from)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(direction),
                end = padding.calculateEndPadding(direction),
                top = padding.calculateTopPadding() + spacing.xs,
                bottom = padding.calculateBottomPadding() + spacing.l
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            if (latest != null) {
                item("latest") {
                    Row(side.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                weightText(latest.weightKg),
                                style = Numbers.tabular,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = colors.fg
                            )
                            val on = fullDay(LocalDate.parse(latest.day))
                            Muted(stringResource(R.string.health_weight_latest_on, on))
                        }
                        if (moved != null) Muted(stringResource(R.string.health_weight_change, signedWeight(moved)))
                    }
                }
            } else if (weight.data != null) {
                item("empty") {
                    ZankupCard(side) {
                        Text(stringResource(R.string.health_weight_empty), color = colors.fg)
                        Button(onClick = { editing = Editing(today, "") }, Modifier.padding(top = spacing.sm)) {
                            Text(stringResource(R.string.health_weight_add))
                        }
                    }
                }
            }
            if (weight.failure != null && weight.data == null) item("error") { ErrorLine(side) }
            if (deleteFailed) item("delete-error") { ErrorLine(side) }
            if (shown.isNotEmpty()) {
                item("chart") {
                    val title = stringResource(R.string.health_weight_title)
                    val labels = remember(from, today, formats.locale) {
                        generateSequence(from) { it.plusDays(1) }.takeWhile { it <= today }
                            .map { dayText(it, "dMMM", formats.locale) }.toList()
                    }
                    ZankupCard(side) {
                        TrendChart(
                            listOf(
                                LineLayer(
                                    title,
                                    colors.accent,
                                    values,
                                    domain = shown.min() - 1 to shown.max() + 1,
                                    hideLegend = true
                                )
                            ),
                            labels,
                            "$title ${weightText(fromUnit(shown.last(), formats.units))}",
                            height = 200.dp,
                            legend = false,
                            format = { com.zankup.android.domain.formatNumber(it, formats.locale, 0, 1) }
                        )
                    }
                }
            }
            if (weighings.isNotEmpty()) {
                item("ranges") { Box(side) { RangePicker(range) { range = it } } }
                item("history-title") { SectionHeader(stringResource(R.string.health_weight_history), side) }
                item("history") {
                    val rows = remember(weighings) { weightHistory(weighings) }
                    ZankupCard(side) {
                        rows.forEachIndexed { i, w ->
                            val edit = stringResource(R.string.health_weight_edit)
                            val delete = stringResource(R.string.health_delete)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClickLabel = edit,
                                        onLongClickLabel = delete,
                                        onLongClick = { deleting = w.day },
                                        onClick = {
                                            editing = Editing(
                                                w.day,
                                                weightValue(w.weightKg, formats.units, formats.locale),
                                                w.day
                                            )
                                        }
                                    ).padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(fullDay(w.day), Modifier.weight(1f), color = colors.fg)
                                w.stepKg?.takeIf { it != 0.0 }?.let {
                                    Muted(signedWeight(it), Modifier.padding(end = 12.dp))
                                }
                                Text(weightText(w.weightKg), fontWeight = FontWeight.Bold, color = colors.fg)
                            }
                            if (i < rows.lastIndex) HorizontalDivider(color = colors.line)
                        }
                    }
                }
                item("note") { Muted(stringResource(R.string.health_weight_profile_note), side) }
            }
        }
    }

    editing?.let { e ->
        EditSheet(
            editing = e,
            today = today,
            onChange = { editing = it },
            onDismiss = { editing = null },
            onSave = { day, kg -> model.save(day, kg, e.old).also { if (it) editing = null } }
        )
    }
    deleting?.let { day ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.health_weight_delete_ask, fullDay(day))) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch { deleteFailed = !model.delete(day) }
                }) { Text(stringResource(R.string.health_delete), color = colors.formNegative) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.health_cancel)) }
            }
        )
    }
}

@Composable
private fun rangeName(range: WeightRange) = stringResource(
    when (range) {
        WeightRange.M1 -> R.string.health_weight_range_1m
        WeightRange.M3 -> R.string.health_weight_range_3m
        WeightRange.M6 -> R.string.health_weight_range_6m
        WeightRange.Y1 -> R.string.health_weight_range_1y
        WeightRange.ALL -> R.string.health_weight_range_all
    }
)

/** 1M · 3M · 6M · 1Y · All, the one picked filled. */
@Composable
private fun RangePicker(range: WeightRange, onPick: (WeightRange) -> Unit) {
    val colors = ZankupTheme.colors
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        WeightRange.entries.forEach { r ->
            val on = r == range
            Surface(
                Modifier
                    .weight(1f)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onPick(r) }),
                shape = RoundedCornerShape(999.dp),
                color = if (on) colors.accent else colors.page
            ) {
                Text(
                    rangeName(r),
                    Modifier.padding(vertical = 9.dp).fillMaxWidth(),
                    color = if (on) colors.accentFg else colors.accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

/** Adding or changing a weighing: the weight in the person's unit and the day (never a future one). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSheet(
    editing: Editing,
    today: LocalDate,
    onChange: (Editing) -> Unit,
    onDismiss: () -> Unit,
    onSave: suspend (LocalDate, Double) -> Boolean
) {
    val units = LocalFormats.current.units
    val unit = weightUnit(units)
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val kg = parseWeight(editing.text, units)
    val valid = kg != null && editing.day <= today
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    ZankupSheet(onDismiss = onDismiss) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                stringResource(if (editing.old != null) R.string.health_weight_edit else R.string.health_weight_add),
                style = MaterialTheme.typography.titleLarge
            )
            OutlinedTextField(
                editing.text,
                { onChange(editing.copy(text = it)) },
                label = { Text(stringResource(R.string.health_weight_field, unit)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )
            OutlinedTextField(
                fullDay(editing.day),
                {},
                label = { Text(stringResource(R.string.health_weight_date)) },
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { picking = true }) {
                        ZankupIcon(ZIcon.Calendar, contentDescription = stringResource(R.string.health_weight_date))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (!valid && editing.text.isNotBlank()) {
                val f = LocalFormats.current
                Muted(
                    stringResource(
                        R.string.health_weight_invalid,
                        "${weightValue(MIN_KG, units, f.locale)} $unit",
                        "${weightValue(MAX_KG, units, f.locale)} $unit"
                    )
                )
            }
            if (failed) ErrorLine()
            Button(
                onClick = {
                    val value = kg ?: return@Button
                    saving = true
                    failed = false
                    scope.launch {
                        failed = !onSave(editing.day, value)
                        saving = false
                    }
                },
                enabled = valid && !saving,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.health_weight_save)) }
        }
    }
    if (picking) {
        val todayMs = today.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = editing.day.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMs

                override fun isSelectableYear(year: Int) = year <= today.year
            }
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val day = java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        onChange(editing.copy(day = day))
                    }
                    picking = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) { Text(stringResource(R.string.health_cancel)) }
            }
        ) { DatePicker(state) }
    }
}
