package com.zankup.android.designsystem.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.zankup.android.designsystem.R
import com.zankup.android.designsystem.theme.ZankupTheme

/**
 * The small (i) every screen uses: a tap opens its explanation in a centred dialog, closed by the
 * button, back or a tap outside.
 */
@Composable
fun ZankupInfoButton(text: String, tint: Color = ZankupTheme.colors.fg, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val title = stringResource(R.string.info_title)
    IconButton(onClick = { open = true }, modifier = modifier) {
        ZankupIcon(ZIcon.Info, contentDescription = title, tint = tint)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.close)) } },
            containerColor = ZankupTheme.colors.surface
        )
    }
}
