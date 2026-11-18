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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.zankup.android.designsystem.R
import com.zankup.android.designsystem.theme.ZankupTheme

/** A stacked screen's bar: back, its title and its actions, on the page colour. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZankupTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
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
            containerColor = colors.page,
            scrolledContainerColor = colors.surface,
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = ZankupTheme.colors.surface,
        contentColor = ZankupTheme.colors.fg,
        content = content
    )
}
