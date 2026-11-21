package com.zankup.android.feed

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zankup.android.designsystem.components.ChipPicker
import com.zankup.android.designsystem.components.ZankupSheet
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.Units
import com.zankup.android.domain.distanceUnit

/**
 * The search's filters: period, distance and time ranges, minimum ascent, three switches and the
 * order. Nothing applies until "Show N", which counts what the filters as they are now would find.
 */
@Composable
internal fun FiltersSheet(
    sheet: SheetUi,
    units: Units,
    onChange: ((Filters) -> Filters) -> Unit,
    onApply: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val f = sheet.draft
    val spacing = ZankupTheme.spacing
    ZankupSheet(onDismiss = onDismiss) {
        // The sheet's height is fixed, not measured from the form: with a weighted, scrollable form
        // inside, its height and the form's height fed each other and the sheet kept moving.
        Column(Modifier.fillMaxHeight()) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.gutter),
                verticalArrangement = Arrangement.spacedBy(spacing.m)
            ) {
                Text(stringResource(R.string.feed_filters), style = MaterialTheme.typography.titleLarge)
                Section(R.string.feed_period) {
                    ChipPicker(
                        options = Period.entries,
                        selected = f.period,
                        label = { stringResource(it.label) },
                        onSelect = { p -> onChange { it.copy(period = p) } }
                    )
                }
                Section(stringResource(R.string.feed_distance, units.distanceUnit)) {
                    Range(
                        low = f.distanceMin,
                        high = f.distanceMax,
                        onLow = { v -> onChange { it.copy(distanceMin = v) } },
                        onHigh = { v -> onChange { it.copy(distanceMax = v) } }
                    )
                }
                Section(R.string.feed_duration) {
                    Range(
                        low = f.durationMin,
                        high = f.durationMax,
                        onLow = { v -> onChange { it.copy(durationMin = v) } },
                        onHigh = { v -> onChange { it.copy(durationMax = v) } }
                    )
                }
                Section(stringResource(R.string.feed_ascent, units.elevationUnit)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        NumberField(
                            f.ascentMin,
                            { v -> onChange { it.copy(ascentMin = v) } },
                            R.string.feed_min,
                            Modifier.weight(1f)
                        )
                        Spacer(Modifier.weight(1f))
                    }
                }
                Column {
                    SwitchRow(R.string.feed_with_record, f.withRecord) { v -> onChange { it.copy(withRecord = v) } }
                    SwitchRow(R.string.feed_with_description, f.withDescription) { v ->
                        onChange { it.copy(withDescription = v) }
                    }
                    SwitchRow(R.string.feed_with_gps, f.withGps) { v -> onChange { it.copy(withGps = v) } }
                }
                Section(R.string.feed_sort) {
                    ChipPicker(
                        options = SortBy.entries,
                        selected = f.sort,
                        label = { stringResource(it.label) },
                        onSelect = { s -> onChange { it.copy(sort = s) } }
                    )
                    ChipPicker(
                        options = Order.entries,
                        selected = f.order,
                        label = { stringResource(it.label) },
                        onSelect = { o -> onChange { it.copy(order = o) } }
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = spacing.gutter, vertical = spacing.m),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.feed_clear))
                }
                Button(onClick = onApply, modifier = Modifier.weight(2f)) {
                    Text(showLabel(sheet.count))
                }
            }
        }
    }
}

@Composable
private fun showLabel(count: Int?): String = when (count) {
    null -> stringResource(R.string.feed_show_results)
    0 -> stringResource(R.string.feed_show_none)
    else -> pluralStringResource(R.plurals.feed_show, count, count)
}

@Composable
private fun Section(@StringRes title: Int, content: @Composable ColumnScope.() -> Unit) =
    Section(stringResource(title), content)

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ZankupTheme.spacing.s)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = ZankupTheme.colors.fg)
        content()
    }
}

@Composable
private fun Range(low: String, high: String, onLow: (String) -> Unit, onHigh: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(ZankupTheme.spacing.sm)) {
        NumberField(low, onLow, R.string.feed_min, Modifier.weight(1f))
        NumberField(high, onHigh, R.string.feed_max, Modifier.weight(1f))
    }
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, @StringRes label: Int, modifier: Modifier) {
    val colors = ZankupTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        label = { Text(stringResource(label)) },
        singleLine = true,
        isError = value.isNotBlank() && parseNumber(value) == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.line,
            focusedLabelColor = colors.accent,
            unfocusedLabelColor = colors.muted,
            cursorColor = colors.accent,
            focusedTextColor = colors.fg,
            unfocusedTextColor = colors.fg
        )
    )
}

@Composable
private fun SwitchRow(@StringRes label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, onValueChange = onChange, role = Role.Switch),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@get:StringRes
private val Period.label: Int
    get() = when (this) {
        Period.ALL -> R.string.feed_period_all
        Period.YEAR -> R.string.feed_period_year
        Period.LAST_12 -> R.string.feed_period_last12
    }

@get:StringRes
private val SortBy.label: Int
    get() = when (this) {
        SortBy.DATE -> R.string.feed_sort_date
        SortBy.DISTANCE -> R.string.feed_sort_distance
        SortBy.DURATION -> R.string.feed_sort_duration
        SortBy.ELEVATION -> R.string.feed_sort_elevation
        SortBy.SPEED -> R.string.feed_sort_speed
    }

@get:StringRes
private val Order.label: Int
    get() = when (this) {
        Order.DESC -> R.string.feed_order_desc
        Order.ASC -> R.string.feed_order_asc
    }
