package com.zankup.android.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zankup.android.designsystem.R
import com.zankup.android.designsystem.theme.ZankupTheme

/**
 * The pill above the tab bar while the instance cannot be reached: the screens keep showing what was
 * kept. It never takes a touch; it goes away when a request gets through.
 */
@Composable
fun OfflineNotice(modifier: Modifier = Modifier) {
    val colors = ZankupTheme.colors
    Box(modifier.fillMaxWidth().padding(bottom = ZankupTheme.spacing.s), contentAlignment = Alignment.Center) {
        Surface(
            shape = CircleShape,
            color = colors.fg,
            contentColor = colors.bg,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ZankupIcon(ZIcon.Offline, contentDescription = null, size = 18.dp)
                Text(stringResource(R.string.offline_notice), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
