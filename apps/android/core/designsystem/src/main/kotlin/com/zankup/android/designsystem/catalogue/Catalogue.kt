package com.zankup.android.designsystem.catalogue

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zankup.android.designsystem.R
import com.zankup.android.designsystem.brand.Mark
import com.zankup.android.designsystem.brand.Wordmark
import com.zankup.android.designsystem.components.ChipPicker
import com.zankup.android.designsystem.components.EmptyState
import com.zankup.android.designsystem.components.ErrorState
import com.zankup.android.designsystem.components.ListRow
import com.zankup.android.designsystem.components.OfflineNotice
import com.zankup.android.designsystem.components.SectionHeader
import com.zankup.android.designsystem.components.Skeleton
import com.zankup.android.designsystem.components.SportFamily
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupCard
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.components.color
import com.zankup.android.designsystem.theme.Numbers
import com.zankup.android.designsystem.theme.ZankupTheme

/**
 * Every shared component on one page, in the current theme: the debug build opens it from the launcher
 * ("Zankup catalogue") and the screenshot tests draw it in every theme. Not part of the release app.
 */
@Composable
fun Catalogue(modifier: Modifier = Modifier) {
    val colors = ZankupTheme.colors
    val spacing = ZankupTheme.spacing
    var period by remember { mutableStateOf("1M") }
    var on by remember { mutableStateOf(true) }
    Column(
        modifier.background(colors.page).padding(spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(spacing.m)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
            Mark(height = 28.dp, color = colors.accent)
            Wordmark(height = 18.dp)
        }
        Text("Display 48", style = MaterialTheme.typography.displayLarge.merge(Numbers.tabular), color = colors.fg)
        Text("Headline", style = MaterialTheme.typography.headlineMedium, color = colors.fg)
        Text("Body text in the theme's foreground", style = MaterialTheme.typography.bodyLarge, color = colors.fg)
        Text("Muted text for what matters less", style = MaterialTheme.typography.bodyMedium, color = colors.muted)

        SectionHeader("Chips")
        ChipPicker(listOf("7D", "1M", "3M", "6M", "1Y"), period, { it }, { period = it })

        SectionHeader("Card")
        ZankupCard(onClick = {}) {
            Text("Morning run", style = MaterialTheme.typography.titleMedium)
            Text(pluralStringResource(R.plurals.activities, 3, 3), color = colors.muted)
            Row(Modifier.padding(top = spacing.s), horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                SportFamily.entries.forEach { sport ->
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(sport.color),
                        contentAlignment = Alignment.Center
                    ) {
                        ZankupIcon(sport.icon, contentDescription = sport.name, tint = colors.page, size = 18.dp)
                    }
                }
            }
        }

        SectionHeader("Zones")
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            colors.zones.forEach {
                Box(Modifier.width(40.dp).padding(vertical = 2.dp).size(40.dp, 12.dp).background(it))
            }
        }

        SectionHeader("Rows")
        Column(Modifier.clip(MaterialTheme.shapes.medium)) {
            ListRow("Heart rate and zones", subtitle = "Max 186 · rest 48", icon = ZIcon.Health, onClick = {})
            ListRow("Units", value = "Metric", icon = ZIcon.Settings, onClick = {})
        }

        SectionHeader("Buttons")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
            Button(onClick = {}) { Text("Sync now") }
            OutlinedButton(onClick = {}) { Text("Cancel") }
            Switch(checked = on, onCheckedChange = { on = it })
        }

        SectionHeader("Loading")
        Skeleton(Modifier.fillMaxWidth(), height = 20.dp)
        Skeleton(Modifier.fillMaxWidth(0.6f))

        SectionHeader("Empty and error")
        EmptyState("No activities yet", body = "Sync your Garmin to see them here.", icon = ZIcon.Activities)
        ErrorState(onRetry = {}, offline = true)
        OfflineNotice()
        Text(stringResource(R.string.brand_name), color = colors.muted)
    }
}
