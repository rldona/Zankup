package com.zankup.android.tools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.api.model.GearOut
import com.zankup.android.designsystem.components.ChipPicker
import com.zankup.android.designsystem.components.EmptyState
import com.zankup.android.designsystem.components.ErrorState
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupCard
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.components.ZankupSheet
import com.zankup.android.designsystem.theme.Numbers
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.Sport
import com.zankup.android.domain.distanceUnit
import com.zankup.android.network.ApiResult
import com.zankup.android.tools.ui.Announced
import com.zankup.android.tools.ui.Badge
import com.zankup.android.tools.ui.Bar
import com.zankup.android.tools.ui.ListSkeleton
import com.zankup.android.tools.ui.Muted
import com.zankup.android.tools.ui.Tone
import com.zankup.android.tools.ui.ToolsScaffold
import com.zankup.android.tools.ui.mediumDate
import com.zankup.android.tools.ui.warning
import com.zankup.android.ui.LocalFormats
import com.zankup.android.ui.sportName
import kotlinx.coroutines.launch

/** What the sheet edits: a new gear, or one of the list. */
private sealed interface Editing {
    data object New : Editing

    data class Existing(val gear: GearOut) : Editing
}

private val KINDS = listOf("shoe", "bike", "other")

@Composable
private fun kindName(kind: String) = stringResource(
    when (kind) {
        "bike" -> R.string.tools_gear_kind_bike
        "other" -> R.string.tools_gear_kind_other
        else -> R.string.tools_gear_kind_shoe
    }
)

/**
 * Gear (OS-1163): each shoe or bike with its badges, its mileage against its limit and "Edit"; "+" adds
 * one. The form (a sheet) saves, retires or uses again, and deletes after asking.
 */
@Composable
fun GearScreen(onBack: () -> Unit, modifier: Modifier = Modifier, model: GearModel = hiltViewModel()) {
    LaunchedEffect(Unit) { model.refresh() }
    val gear by model.gear.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Editing?>(null) }
    val list = gear.data
    ToolsScaffold(
        stringResource(R.string.tools_gear_title),
        onBack,
        modifier,
        actions = {
            androidx.compose.material3.IconButton(onClick = { editing = Editing.New }) {
                ZankupIcon(ZIcon.Add, contentDescription = stringResource(R.string.tools_gear_add))
            }
        }
    ) {
        item("help") { Muted(stringResource(R.string.tools_gear_help)) }
        when {
            gear.loading -> item("loading") { ListSkeleton() }
            list == null -> item("error") {
                ErrorState(onRetry = model::refresh, offline = gear.failure is ApiResult.Offline)
            }
            list.isEmpty() -> item("empty") { EmptyState(stringResource(R.string.tools_gear_empty), icon = ZIcon.Run) }
            else -> sortGear(list).forEach { g ->
                item(g.id) { GearCard(g) { editing = Editing.Existing(g) } }
            }
        }
    }
    editing?.let { e ->
        GearSheet(
            gear = (e as? Editing.Existing)?.gear,
            model = model,
            onDone = { editing = null }
        )
    }
}

@Composable
private fun GearCard(g: GearOut, onEdit: () -> Unit) {
    val colors = ZankupTheme.colors
    val formats = LocalFormats.current
    val border = if (g.habitual && !g.retired) BorderStroke(2.dp, colors.accent) else null
    Surface(
        Modifier.fillMaxWidth().alpha(if (g.retired) 0.6f else 1f),
        shape = MaterialTheme.shapes.medium,
        color = colors.surface,
        contentColor = colors.fg,
        border = border
    ) {
        Column(Modifier.padding(ZankupTheme.spacing.m), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    g.name,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (g.habitual && !g.retired) Badge(stringResource(R.string.tools_gear_habitual), Tone.GOOD)
                if (g.retired) Badge(stringResource(R.string.tools_gear_retired))
            }
            val limit = g.limitM?.takeIf { it > 0 }
            val tint = if (g.overLimit) warning else colors.fg
            Row {
                Text(formats.distance(g.distanceM), style = Numbers.tabular, fontWeight = FontWeight.Bold, color = tint)
                if (limit != null) Text(" / ${formats.distance(limit)}", style = Numbers.tabular, color = tint)
            }
            mileageShare(g.distanceM, limit)?.let { share ->
                Bar(
                    share,
                    stringResource(R.string.tools_gear_mileage),
                    color = if (g.overLimit) warning else colors.accent
                )
            }
            if (g.overLimit) Text(stringResource(R.string.tools_gear_over_limit), color = warning)
            val details = buildList {
                add(kindName(g.kind))
                if (!g.brand.isNullOrBlank() || !g.model.isNullOrBlank()) add(gearModel(g.brand, g.model))
                add(pluralStringResource(R.plurals.tools_gear_activities, g.activities, g.activities))
                parseInstant(g.lastUsed)?.let {
                    add(stringResource(R.string.tools_gear_last_used, mediumDate(it, formats.locale)))
                }
                if (g.sports.isNotEmpty()) {
                    val names = g.sports.map { stringResource(sportName(Sport.fromKey(it))) }
                    add(stringResource(R.string.tools_gear_default_for, names.joinToString(", ")))
                }
            }
            Muted(details.joinToString(" · "))
            OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.tools_edit))
            }
        }
    }
}

/** Adding or editing gear: its fields, Save, and for one that exists Retire / Use again and Delete. */
@Composable
private fun GearSheet(gear: GearOut?, model: GearModel, onDone: () -> Unit) {
    val units = LocalFormats.current.units
    val unit = units.distanceUnit
    var form by remember { mutableStateOf(gear?.let { GearForm.of(it, units) } ?: GearForm()) }
    var saving by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<ApiResult.Failure?>(null) }
    var deleteFailed by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val numbersOk = (form.initial.isBlank() || fieldToMetres(form.initial, units) != null) &&
        (form.limit.isBlank() || fieldToMetres(form.limit, units) != null)

    fun save(retired: Boolean? = null) {
        saving = true
        failure = null
        scope.launch {
            failure = model.save(gear?.id, form, units, retired)
            saving = false
            if (failure == null) onDone()
        }
    }

    ZankupSheet(onDismiss = onDone) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(gear?.name ?: stringResource(R.string.tools_gear_add), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                form.name,
                { form = form.copy(name = it.take(MAX_TEXT)) },
                label = { Text(stringResource(R.string.tools_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(stringResource(R.string.tools_gear_kind_label), style = MaterialTheme.typography.titleSmall)
            ChipPicker(KINDS, form.kind, { kindName(it) }, { form = form.copy(kind = it) })
            if (gear?.retired != true) {
                SwitchRow(stringResource(R.string.tools_gear_habitual), form.habitual) {
                    form = form.copy(habitual = it)
                }
                Muted(stringResource(R.string.tools_gear_habitual_help))
            }
            OutlinedTextField(
                form.brand,
                { form = form.copy(brand = it.take(MAX_TEXT)) },
                label = { Text(stringResource(R.string.tools_gear_brand)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                form.model,
                { form = form.copy(model = it.take(MAX_TEXT)) },
                label = { Text(stringResource(R.string.tools_gear_model)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                form.initial,
                { form = form.copy(initial = it) },
                label = { Text(stringResource(R.string.tools_gear_initial, unit)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                form.limit,
                { form = form.copy(limit = it) },
                label = { Text(stringResource(R.string.tools_gear_limit, unit)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Text(stringResource(R.string.tools_gear_default_sports), style = MaterialTheme.typography.titleSmall)
            ZankupCard {
                Sport.entries.filter { it != Sport.OTHER }.forEach { sport ->
                    SwitchRow(stringResource(sportName(sport)), sport.key in form.sports) { on ->
                        form = form.copy(sports = if (on) form.sports + sport.key else form.sports - sport.key)
                    }
                }
            }
            failure?.let {
                Announced((it as? ApiResult.Http)?.detail ?: stringResource(R.string.tools_error), color = warning)
            }
            if (deleteFailed) Announced(stringResource(R.string.tools_error), color = warning)
            Button(
                onClick = { save() },
                enabled = !saving && form.name.isNotBlank() && numbersOk,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.tools_save)) }
            if (gear != null) {
                OutlinedButton(
                    onClick = { save(retired = !gear.retired) },
                    enabled = !saving && form.name.isNotBlank() && numbersOk,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(if (gear.retired) R.string.tools_gear_unretire else R.string.tools_gear_retire))
                }
                OutlinedButton(onClick = { asking = true }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.tools_gear_delete), color = warning)
                }
            }
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.tools_cancel))
            }
        }
    }
    if (asking && gear != null) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(gear.name) },
            text = { Text(stringResource(R.string.tools_gear_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    asking = false
                    saving = true
                    scope.launch {
                        val ok = model.delete(gear.id)
                        saving = false
                        deleteFailed = !ok
                        if (ok) onDone()
                    }
                }) { Text(stringResource(R.string.tools_gear_delete), color = warning) }
            },
            dismissButton = {
                TextButton(onClick = { asking = false }) { Text(stringResource(R.string.tools_cancel)) }
            }
        )
    }
}

/** A label and a switch, the whole row toggling it. */
@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), color = ZankupTheme.colors.fg)
        Switch(checked = checked, onCheckedChange = null)
    }
}

private const val MAX_TEXT = 120
