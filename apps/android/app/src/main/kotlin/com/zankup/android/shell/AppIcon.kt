package com.zankup.android.shell

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zankup.android.R
import com.zankup.android.designsystem.components.ZankupTopBar
import com.zankup.android.designsystem.theme.ZankupTheme

/** A launcher icon: its alias, its background colours (stripes for pride) and the mark's colour. */
internal enum class AppIcon(val label: Int, val background: List<Long>, val mark: Long) {
    DEFAULT(R.string.app_icon_default, listOf(0xFF20C997), 0xFFFFFFFF),
    MINT(R.string.app_icon_mint, listOf(0xFF20C997), 0xFF06221A),
    BLACK(R.string.app_icon_black, listOf(0xFF000000), 0xFFFFFFFF),
    WHITE(R.string.app_icon_white, listOf(0xFFFFFFFF), 0xFF20C997),
    NIGHT(R.string.app_icon_night, listOf(0xFF06221A), 0xFF20C997),
    BLUE(R.string.app_icon_blue, listOf(0xFF1C7ED6), 0xFFFFFFFF),
    PURPLE(R.string.app_icon_purple, listOf(0xFF7048E8), 0xFFFFFFFF),
    RED(R.string.app_icon_red, listOf(0xFFE03131), 0xFFFFFFFF),
    ORANGE(R.string.app_icon_orange, listOf(0xFFF76707), 0xFFFFFFFF),
    FLAME(R.string.app_icon_flame, listOf(0xFFFC4C02), 0xFFFFFFFF),
    PRIDE(R.string.app_icon_pride, listOf(0xFFE03131, 0xFFF59F00, 0xFF2F9E44, 0xFF1C7ED6, 0xFF7048E8), 0xFFFFFFFF);

    /** The manifest's activity alias for it (`.IconDefault`, `.IconMint`…). */
    fun component(context: Context) = ComponentName(
        context.packageName,
        "com.zankup.android.Icon" + name.lowercase().replaceFirstChar { it.uppercase() }
    )

    companion object {
        /** The one enabled now: the default unless another was chosen. */
        fun current(context: Context): AppIcon {
            val pm = context.packageManager
            return entries.firstOrNull {
                pm.getComponentEnabledSetting(it.component(context)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } ?: DEFAULT
        }

        /** Enables [icon]'s alias and disables the rest; the launcher picks it up in a few seconds. */
        fun use(context: Context, icon: AppIcon) {
            val pm = context.packageManager
            // The new one first, so there is always a launcher entry.
            pm.setComponentEnabledSetting(
                icon.component(context),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            entries.filter { it != icon }.forEach {
                pm.setComponentEnabledSetting(
                    it.component(context),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }
}

/** Settings › App icon (OS-1171): the mark on each variant's colours, the one in use ringed. */
@Composable
internal fun AppIconScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var current by remember { mutableStateOf(AppIcon.current(context)) }
    val colors = ZankupTheme.colors
    Scaffold(
        containerColor = colors.page,
        topBar = { ZankupTopBar(stringResource(R.string.nav_app_icon), onBack = onBack) }
    ) { padding ->
        LazyVerticalGrid(
            GridCells.Fixed(3),
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.app_icon_heading),
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.fg
                    )
                    Text(
                        stringResource(R.string.app_icon_intro),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.fg
                    )
                }
            }
            items(AppIcon.entries) { icon ->
                val chosen = icon == current
                Column(
                    Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(role = Role.RadioButton) {
                            AppIcon.use(context, icon)
                            current = icon
                        }.semantics { selected = chosen }
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .border(3.dp, if (chosen) colors.accent else Color.Transparent, CircleShape)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(Color(icon.background.first()))
                            .drawBehind {
                                val band = size.height / icon.background.size
                                icon.background.forEachIndexed { i, c ->
                                    drawRect(Color(c), Offset(0f, band * i), Size(size.width, band))
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painterResource(com.zankup.android.designsystem.R.drawable.zankup_mark),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(Color(icon.mark)),
                            modifier = Modifier.fillMaxWidth(0.5f)
                        )
                    }
                    Text(
                        stringResource(icon.label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (chosen) colors.accent else colors.fg,
                        textAlign = TextAlign.Center
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.app_icon_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
            }
        }
    }
}
