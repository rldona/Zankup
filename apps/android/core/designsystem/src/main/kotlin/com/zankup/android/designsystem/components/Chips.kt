package com.zankup.android.designsystem.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.zankup.android.designsystem.theme.ZankupTheme

/** One chip, one size everywhere: the pressed one in the foreground colour, as the React Native app. */
@Composable
fun ZankupChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ZankupTheme.colors
    Surface(
        modifier =
        modifier
            .heightIn(min = 36.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab),
        shape = CircleShape,
        color = if (selected) colors.fg else colors.surface2,
        contentColor = if (selected) colors.bg else colors.fg
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** A row of chips where one is chosen: periods (7D, 1M, 3M…), sports, metrics. Scrolls when it is wide. */
@Composable
fun <T> ChipPicker(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(ZankupTheme.spacing.s)
    ) {
        options.forEach { option ->
            ZankupChip(label = label(option), selected = option == selected, onClick = { onSelect(option) })
        }
    }
}
