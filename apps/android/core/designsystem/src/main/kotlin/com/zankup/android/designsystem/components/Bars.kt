package com.zankup.android.designsystem.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.zankup.android.designsystem.R
import com.zankup.android.designsystem.theme.ZankupTheme
import kotlinx.coroutines.launch

/**
 * A stacked screen's bar: back, its title and its actions. The tab bar's colour, as Home's, so the two
 * frames of a screen match.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZankupTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    /** The bar's colour: the surface, or the page where the screen is all one dark colour. */
    containerColor: Color = ZankupTheme.colors.surface,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = ZankupTheme.colors
    TopAppBar(
        title = {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        modifier = modifier,
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    ZankupIcon(ZIcon.Back, contentDescription = stringResource(R.string.back))
                }
            }
        },
        actions = actions,
        colors =
        TopAppBarDefaults.topAppBarColors(
            // The tab bar's colour, fixed: the content never slides under a tinted bar.
            containerColor = containerColor,
            scrolledContainerColor = containerColor,
            titleContentColor = colors.fg,
            navigationIconContentColor = colors.fg,
            actionIconContentColor = colors.fg
        )
    )
}

/** The bottom sheet every screen uses, in the surface colour, closed by dragging, back or a tap outside. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZankupSheet(onDismiss: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }

    // Close only after the sheet has slid away: removing it on the tap made its exit vanish.
    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            state.hide()
            onDismiss()
        }
    }
    ModalBottomSheet(
        onDismissRequest = { close() },
        sheetState = state,
        containerColor = ZankupTheme.colors.surface,
        contentColor = ZankupTheme.colors.fg,
        content = content
    )
}
