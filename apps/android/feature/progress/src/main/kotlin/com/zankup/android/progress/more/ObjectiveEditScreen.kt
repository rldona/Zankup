package com.zankup.android.progress.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.api.model.GoalCreate
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.components.ZankupTopBar
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.Sport
import com.zankup.android.domain.Units
import com.zankup.android.progress.R
import com.zankup.android.ui.LocalFormats
import kotlinx.coroutines.launch

private val PERIODS = listOf("week", "month", "year")
private val METRICS = listOf("count", "time", "distance", "ascent")

/** What the form needs of a goal to know which kinds are taken. */
internal data class GoalSlot(val id: Int, val period: String, val metric: String, val sport: String?)

/** The kinds already set for a sport and period (one goal of each), greyed out in the form. */
internal fun takenKinds(goals: List<GoalSlot>, editing: Int?, period: String, sport: String?): Set<String> =
    goals.filter { it.id != editing && it.period == period && it.sport == sport }.map { it.metric }.toSet()

/**
 * A new goal, or one being edited (objective-new.tsx): the sport (all, or one, in a sheet), weekly,
 * monthly or yearly, what to measure (kinds already set for that sport and period greyed out; strength
 * has no distance), and the target in its units. Saving returns; a failure says so.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ObjectiveEditScreen(
    id: Long?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    model: ObjectiveEditModel = hiltViewModel()
) {
    val colors = ZankupTheme.colors
    val imperial = LocalFormats.current.units == Units.IMPERIAL
    val scope = rememberCoroutineScope()
    val goals by model.goals.state.collectAsStateWithLifecycle(initialValue = null)
    val list = goals?.data.orEmpty()
    val editing = id?.let { wanted -> list.firstOrNull { it.id.toLong() == wanted } }

    var sport by rememberSaveable { mutableStateOf<String?>(null) }
    var period by rememberSaveable { mutableStateOf("week") }
    var metric by rememberSaveable { mutableStateOf<String?>(null) }
    var a by rememberSaveable { mutableStateOf("") }
    var b by rememberSaveable { mutableStateOf("") }
    var filled by rememberSaveable { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    // The goal being edited fills the form once it is known.
    LaunchedEffect(editing) {
        if (editing != null && !filled) {
            filled = true
            sport = editing.sport
            period = editing.period
            metric = editing.metric
            val start = toInput(editing.metric, editing.target, imperial)
            a = start.a
            b = start.b
        }
    }

    val taken = takenKinds(list.map { GoalSlot(it.id, it.period, it.metric, it.sport) }, editing?.id, period, sport)
    val noDistance = sport == Sport.STRENGTH.key
    val chosen = metric
    val target = chosen?.let { fromInput(it, a, b, imperial) } ?: 0.0
    val typed = a.isNotEmpty() || b.isNotEmpty()
    val valid = chosen != null && chosen !in taken && target > 0 && !(noDistance && chosen == "distance")

    Scaffold(containerColor = colors.page, topBar = {
        ZankupTopBar(
            stringResource(if (id != null) R.string.more_edit_goal else R.string.more_new_goal),
            onBack = onBack
        )
    }) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Heading(stringResource(R.string.more_choose_sports))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.5.dp, colors.line, RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) { picking = true }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ZankupIcon(sportIconOf(sport), null, tint = colors.fg)
                Text(goalSport(sport), color = colors.fg, fontSize = 15.sp, modifier = Modifier.weight(1f))
                ZankupIcon(ZIcon.ArrowDown, null, tint = colors.fg)
            }

            Heading(stringResource(R.string.more_which_period), top = 8)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PERIODS.forEach { p ->
                    Pill(p == period, stringResource(periodName(p)), Modifier.weight(1f)) { period = p }
                }
            }

            Heading(stringResource(R.string.more_which_kind), top = 8)
            if (taken.isNotEmpty()) Text(stringResource(R.string.more_kind_taken), color = colors.fg, fontSize = 14.sp)
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                maxItemsInEachRow = 2
            ) {
                METRICS.forEach { m ->
                    val label =
                        stringResource(
                            when (m) {
                                "count" -> R.string.more_metric_short_count
                                "time" -> R.string.more_metric_time
                                "distance" -> R.string.more_metric_distance
                                else -> R.string.more_metric_ascent
                            }
                        )
                    Pill(
                        m == metric,
                        label,
                        Modifier.weight(1f),
                        disabled =
                        m in taken || (noDistance && m == "distance")
                    ) {
                        metric = m
                        a = ""
                        b = ""
                    }
                }
            }

            if (chosen != null && chosen !in taken) {
                Heading(stringResource(R.string.more_what_target), top = 8)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.5.dp, colors.line, RoundedCornerShape(12.dp))
                        .background(colors.bg)
                ) {
                    if (chosen == "time") {
                        Field(a, { a = it }, "h", Modifier.weight(1f))
                        Box(Modifier.width(1.5.dp).fillMaxHeight().background(colors.line))
                        Field(b, { b = it }, "min", Modifier.weight(1f))
                    } else {
                        val unit =
                            when (chosen) {
                                "distance" -> if (imperial) "mi" else "km"
                                "ascent" -> if (imperial) "ft" else "m"
                                else -> ""
                            }
                        Field(a, { a = it }, unit, Modifier.weight(1f))
                    }
                }
                if (typed && !valid) {
                    Text(stringResource(R.string.more_target_invalid), color = colors.formNegative, fontSize = 13.sp)
                }
            }
            if (failed) Text(stringResource(R.string.more_goal_save_error), color = colors.formNegative)

            Button(
                onClick = {
                    val m = chosen ?: return@Button
                    busy = true
                    failed = false
                    scope.launch {
                        val body =
                            GoalCreate(
                                period = GoalCreate.Period.entries.first { it.value == period },
                                metric = GoalCreate.Metric.entries.first { it.value == m },
                                target = target,
                                sport = sport
                            )
                        val ok = model.save(editing?.id, body)
                        busy = false
                        if (ok) onSaved() else failed = true
                    }
                },
                enabled = valid && !busy && (id == null || editing != null),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.accentFg),
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp).height(52.dp)
            ) {
                Text(stringResource(R.string.more_save_goal), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (picking) {
        // The goal's own sport list (all, or one), without a search: the note says what "all" counts.
        SportSheet(
            sport = sport?.let { Sport.fromKey(it) },
            allNote = stringResource(R.string.more_goal_all_sports_note),
            onPick = {
                sport = it?.key
                picking = false
            },
            onDismiss = { picking = false },
            search = false
        )
    }
}

@Composable
private fun Heading(text: String, top: Int = 0) {
    Text(
        text,
        color = ZankupTheme.colors.fg,
        fontSize = 17.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(top = top.dp)
    )
}

/** A choice of the form: outlined, the chosen one in the accent, a taken one shaded and dimmed. */
@Composable
private fun Pill(on: Boolean, label: String, modifier: Modifier, disabled: Boolean = false, onClick: () -> Unit) {
    val colors = ZankupTheme.colors
    val border =
        when {
            on -> colors.accent
            disabled -> Color.Transparent
            else -> colors.line
        }
    Box(
        modifier
            .clip(CircleShape)
            .background(if (disabled) colors.surface2 else Color.Transparent)
            .border(1.5.dp, border, CircleShape)
            .selectable(on, enabled = !disabled, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color =
            when {
                on -> colors.accent
                disabled -> colors.muted
                else -> colors.fg
            },
            fontSize = 15.sp
        )
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, unit: String, modifier: Modifier) {
    val colors = ZankupTheme.colors
    Row(modifier.fillMaxHeight().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            if (value.isEmpty()) Text("0", color = colors.fg, fontSize = 16.sp)
            BasicTextField(
                value,
                { text -> onChange(text.filter { it.isDigit() || it == '.' || it == ',' }) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = TextStyle(color = colors.fg, fontSize = 16.sp),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.width(IntrinsicWidth)
            )
        }
        if (unit.isNotEmpty()) Text(" $unit", color = colors.fg, fontSize = 16.sp)
    }
}

private val IntrinsicWidth = 72.dp
