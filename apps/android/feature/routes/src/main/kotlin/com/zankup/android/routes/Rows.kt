package com.zankup.android.routes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.theme.Numbers
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.ui.LocalFormats

/** A time done or an effort: its day, time and one more figure, then the gap to the fastest (a medal for it). */
@Composable
internal fun EffortRow(effort: Effort, fastest: Double, onClick: () -> Unit) {
    val formats = LocalFormats.current
    val colors = ZankupTheme.colors
    val figures = MaterialTheme.typography.bodyMedium.merge(Numbers.tabular)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(shortDay(effort.day, formats.locale), Modifier.weight(1.3f), color = colors.accent, style = figures)
        Text(
            formats.duration(effort.seconds),
            Modifier.weight(1f),
            color = colors.fg,
            style = figures,
            textAlign = TextAlign.End
        )
        Text(effort.extra ?: "—", Modifier.weight(1f), color = colors.muted, style = figures, textAlign = TextAlign.End)
        Row(Modifier.weight(0.9f), horizontalArrangement = Arrangement.End) {
            if (effort.seconds <= fastest) {
                ZankupIcon(ZIcon.Medal, stringResource(R.string.routes_fastest), tint = colors.accent, filled = true)
            } else {
                Text("+" + formats.duration(behind(effort.seconds, fastest)), color = colors.muted, style = figures)
            }
        }
    }
}
