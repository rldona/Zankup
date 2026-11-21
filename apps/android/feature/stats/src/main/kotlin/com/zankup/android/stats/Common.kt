package com.zankup.android.stats

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupCard
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.components.ZankupInfoButton
import com.zankup.android.designsystem.components.color
import com.zankup.android.designsystem.theme.Numbers
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.Units
import com.zankup.android.domain.formatDuration
import com.zankup.android.ui.Formats
import com.zankup.android.ui.LocalFormats
import com.zankup.android.ui.family
import com.zankup.android.ui.sportOf
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** A card with its title, an optional info button and something on the right (a switch). */
@Composable
internal fun StatCard(
    title: String,
    modifier: Modifier = Modifier,
    info: String? = null,
    right: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    ZankupCard(modifier, clip = false) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = ZankupTheme.spacing.m),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ZankupTheme.spacing.xs)
            ) {
                Text(
                    title,
                    style = HeadingStyle,
                    color = ZankupTheme.colors.fg,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (info != null) InfoButton(info)
            }
            right?.invoke(this)
        }
        content()
    }
}

/** A card's title, as the RN app's Heading: 17 sp, bold. */
internal val HeadingStyle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold)

/** A figure's value, as the RN app's statValue: 17 sp, bold, tabular. */
internal val StatValueStyle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold).merge(Numbers.tabular)

/** The (i): the shared centred explanation dialog. */
@Composable
internal fun InfoButton(text: String) {
    ZankupInfoButton(text)
}

/** An arrow of a stepper: left or right, dimmed and disabled at the end of the history. */
@Composable
internal fun Arrow(forward: Boolean, enabled: Boolean, @StringRes label: Int, onClick: () -> Unit) {
    val description = stringResource(label)
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.alpha(if (enabled) 1f else 0.3f).semantics { contentDescription = description }
    ) {
        ZankupIcon(
            if (forward) ZIcon.Chevron else ZIcon.ChevronLeft,
            contentDescription = null,
            tint = ZankupTheme.colors.fg,
            size = 28.dp
        )
    }
}

/** The year or month shown, with an arrow on each side. */
@Composable
internal fun Stepper(
    label: String,
    canBack: Boolean,
    canForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Arrow(false, canBack, R.string.stats_previous, onBack)
        Text(
            label,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = ZankupTheme.colors.fg,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        Arrow(true, canForward, R.string.stats_next, onForward)
    }
}

/** A figure: its value over its name in small capitals, as the RN app's Stat. */
@Composable
internal fun Stat(label: String, value: String, modifier: Modifier = Modifier, below: String? = null) {
    val locale = LocalFormats.current.locale
    Column(modifier.widthIn(min = 72.dp).padding(vertical = 4.dp)) {
        Text(value, style = StatValueStyle, color = ZankupTheme.colors.fg)
        Text(
            label.uppercase(locale),
            modifier = Modifier.padding(top = 2.dp),
            fontSize = 11.sp,
            color = ZankupTheme.colors.muted
        )
        if (below != null) Text(below, fontSize = 11.sp, color = ZankupTheme.colors.muted)
    }
}

/** A muted sentence (empty sections, notes, the engine's progress). */
@Composable
internal fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 13.sp, color = ZankupTheme.colors.muted, modifier = modifier)
}

/** Text in the accent that opens something. */
@Composable
internal fun Link(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold).merge(Numbers.tabular),
        color = ZankupTheme.colors.accent,
        modifier = modifier.clickable(role = Role.Button, onClick = onClick).padding(vertical = 2.dp)
    )
}

// --- names and values ---------------------------------------------------------------------------

/** km or mi in metres. */
internal val Formats.scale get() = if (units == Units.METRIC) 1000.0 else METRES_PER_MILE
internal val Formats.imperial get() = units != Units.METRIC

@Composable
internal fun sportColour(key: String?): Color = sportOf(key).family.color

/** "12 nov". */
internal fun dayLabel(day: String, f: Formats): String = runCatching {
    DateTimeFormatter.ofPattern("d MMM", f.locale).format(LocalDate.parse(day.take(10))).replace(".", "")
}.getOrDefault(day)

/** "12 nov 2025". */
internal fun shortDay(day: String, f: Formats): String = runCatching {
    DateTimeFormatter.ofPattern("d MMM yyyy", f.locale).format(LocalDate.parse(day.take(10))).replace(".", "")
}.getOrDefault(day)

@StringRes
internal fun effortRes(key: String): Int? = when (key) {
    "400m" -> R.string.stats_effort_400m
    "half_mile" -> R.string.stats_effort_half_mile
    "1k" -> R.string.stats_effort_1k
    "1mile" -> R.string.stats_effort_1mile
    "2mile" -> R.string.stats_effort_2mile
    "5k" -> R.string.stats_effort_5k
    "10k" -> R.string.stats_effort_10k
    "15k" -> R.string.stats_effort_15k
    "10mile" -> R.string.stats_effort_10mile
    "half_marathon" -> R.string.stats_effort_half_marathon
    "marathon" -> R.string.stats_effort_marathon
    else -> null
}

@Composable
internal fun effortName(key: String): String = effortRes(key)?.let { stringResource(it) } ?: key

@Composable
internal fun estimateName(kind: String): String = when (kind) {
    "ftp" -> stringResource(R.string.stats_estimate_ftp)
    "critical_speed" -> stringResource(R.string.stats_estimate_critical_speed)
    "lthr" -> stringResource(R.string.stats_estimate_lthr)
    "hr_max" -> stringResource(R.string.stats_estimate_hr_max)
    "vdot" -> stringResource(R.string.stats_estimate_vdot)
    "race_5k" -> stringResource(R.string.stats_estimate_race_5k)
    "race_10k" -> stringResource(R.string.stats_estimate_race_10k)
    "race_half_marathon" -> stringResource(R.string.stats_estimate_race_half_marathon)
    "race_marathon" -> stringResource(R.string.stats_estimate_race_marathon)
    else -> kind
}

/** "riegel:10" → "Riegel's formula from your best 10 km". */
@Composable
internal fun methodText(method: String, f: Formats): String {
    val (key, km) = methodParts(method)
    val number = km.toDoubleOrNull()?.let { com.zankup.android.domain.formatNumber(it, f.locale, 0, 1) } ?: km
    return when (key) {
        "ftp_20min" -> stringResource(R.string.stats_method_ftp_20min)
        "ftp_critical_power" -> stringResource(R.string.stats_method_ftp_critical_power)
        "lthr_60min" -> stringResource(R.string.stats_method_lthr_60min)
        "lthr_20min" -> stringResource(R.string.stats_method_lthr_20min)
        "critical_speed" -> stringResource(R.string.stats_method_critical_speed)
        "vdot" -> stringResource(R.string.stats_method_vdot, number)
        "riegel" -> stringResource(R.string.stats_method_riegel, number)
        "hr_max_year" -> stringResource(R.string.stats_method_hr_max_year)
        else -> method
    }
}

internal fun estimateValue(kind: String, value: Double, f: Formats): String = when {
    kind == "ftp" -> "${Math.round(value)} W"
    kind == "critical_speed" -> f.unit.pace(value)
    kind == "lthr" || kind == "hr_max" -> "${Math.round(value)} bpm"
    kind.startsWith("race_") -> formatDuration(value)
    else -> com.zankup.android.domain.formatNumber(value, f.locale, 1, 1)
}
