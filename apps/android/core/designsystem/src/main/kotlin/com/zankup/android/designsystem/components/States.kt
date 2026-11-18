package com.zankup.android.designsystem.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zankup.android.designsystem.R
import com.zankup.android.designsystem.theme.ZankupTheme

/** A grey block that pulses where content will be, in the shape of what is loading. */
@Composable
fun Skeleton(modifier: Modifier = Modifier, height: Dp = 16.dp) {
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "alpha"
    )
    val loading = stringResource(R.string.loading)
    Box(
        modifier
            .height(height)
            .alpha(pulse)
            .clip(MaterialTheme.shapes.small)
            .background(ZankupTheme.colors.surface2)
            .semantics { contentDescription = loading }
    )
}

/** Nothing to show yet: an icon, a sentence and, when there is something to do about it, a button. */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    icon: ZIcon? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier.fillMaxWidth().padding(ZankupTheme.spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ZankupTheme.spacing.s)
    ) {
        if (icon != null) ZankupIcon(icon, contentDescription = null, tint = ZankupTheme.colors.muted, size = 40.dp)
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = ZankupTheme.colors.fg,
            textAlign = TextAlign.Center
        )
        if (body != null) {
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = ZankupTheme.colors.muted,
                textAlign = TextAlign.Center
            )
        }
        if (action != null && onAction != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = ZankupTheme.spacing.s)) { Text(action) }
        }
    }
}

/** Something failed: what happened, in words, and "Try again". Offline has its own icon and sentence. */
@Composable
fun ErrorState(onRetry: () -> Unit, modifier: Modifier = Modifier, offline: Boolean = false, message: String? = null) {
    EmptyState(
        title = stringResource(if (offline) R.string.error_offline_title else R.string.error_title),
        body = message ?: stringResource(if (offline) R.string.error_offline_body else R.string.error_body),
        icon = if (offline) ZIcon.Offline else ZIcon.Error,
        action = stringResource(R.string.retry),
        onAction = onRetry,
        modifier = modifier
    )
}
