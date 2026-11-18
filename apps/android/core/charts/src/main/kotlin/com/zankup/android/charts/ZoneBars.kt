package com.zankup.android.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zankup.android.designsystem.theme.Numbers
import com.zankup.android.designsystem.theme.ZankupTheme

/** A zone: its name ("Z2"), its range ("120–140"), the time in it and its colour. */
@Immutable
data class Zone(val name: String, val range: String, val seconds: Float, val time: String, val color: Color)

/**
 * Time in zones as thick bars, each as long as its share of the longest, with the time beside it (not
 * stacked). The bars slide to their new length when the zones change.
 */
@Composable
fun ZoneBars(zones: List<Zone>, modifier: Modifier = Modifier) {
    val most = zones.maxOfOrNull { it.seconds }?.takeIf { it > 0f } ?: 1f
    val colors = ZankupTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        zones.forEach { zone ->
            val share by animateFloatAsState(zone.seconds / most, tween(450), label = "zone")
            Row(
                Modifier.fillMaxWidth().semantics { contentDescription = "${zone.name} ${zone.range}: ${zone.time}" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(Modifier.width(64.dp)) {
                    Text(zone.name, style = MaterialTheme.typography.labelLarge, color = colors.fg)
                    Text(
                        zone.range,
                        style = MaterialTheme.typography.labelSmall.merge(Numbers.tabular),
                        color = colors.muted
                    )
                }
                Box(Modifier.weight(1f).height(22.dp)) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(share.coerceIn(0.01f, 1f))
                            .clip(RoundedCornerShape(6.dp))
                            .background(zone.color)
                    )
                }
                Text(
                    zone.time,
                    style = MaterialTheme.typography.labelLarge.merge(Numbers.tabular),
                    color = colors.fg,
                    modifier = Modifier.width(56.dp)
                )
            }
        }
    }
}
