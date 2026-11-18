package com.zankup.android.health.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zankup.android.designsystem.components.Skeleton
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupCard
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.health.R

/** A muted line. */
@Composable
internal fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodyMedium, color = ZankupTheme.colors.muted)
}

/** Something failed: a muted line that TalkBack reads out, what was shown stays. */
@Composable
internal fun ErrorLine(modifier: Modifier = Modifier) {
    Muted(
        stringResource(R.string.health_error),
        modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
}

/** A card's title, with its (i) when it has a [note]. */
@Composable
internal fun CardTitle(title: String, note: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = ZankupTheme.colors.fg
        )
        if (note != null) InfoButton(note)
    }
}

/** The (i): its explanation in a dialog. */
@Composable
internal fun InfoButton(text: String) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.health_more_info)
    IconButton(onClick = { open = true }, modifier = Modifier.size(32.dp)) {
        ZankupIcon(ZIcon.Info, contentDescription = label, tint = ZankupTheme.colors.muted, size = 20.dp)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.health_close)) } }
        )
    }
}

/** The tab's shape while the first answer loads: the night, the day's tiles and a chart. */
@Composable
internal fun HealthSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Skeleton(Modifier.fillMaxWidth(0.4f), height = 22.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Skeleton(Modifier.weight(1f), height = 96.dp)
            Skeleton(Modifier.weight(1f), height = 96.dp)
        }
        Skeleton(Modifier.fillMaxWidth(), height = 180.dp)
        Skeleton(Modifier.fillMaxWidth(0.5f), height = 22.dp)
        ZankupCard {
            Skeleton(Modifier.fillMaxWidth(0.3f), height = 14.dp)
            Spacer(Modifier.height(8.dp))
            Skeleton(Modifier.fillMaxWidth(0.2f), height = 28.dp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Skeleton(Modifier.weight(1f), height = 72.dp)
            Skeleton(Modifier.weight(1f), height = 72.dp)
        }
        Skeleton(Modifier.fillMaxWidth(), height = 160.dp)
    }
}
