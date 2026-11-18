package com.zankup.android.shell

import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.R
import com.zankup.android.data.Reachability
import com.zankup.android.designsystem.brand.Wordmark
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.session.Prompter
import com.zankup.android.session.Session
import com.zankup.android.session.SessionState
import com.zankup.android.signin.SignInScreen
import kotlinx.coroutines.launch

/** The gate: sign-in, the lock, or the app, as the session says. */
@Composable
fun Root(session: Session, reachability: Reachability, link: Intent?, onLinkHandled: () -> Unit) {
    val state by session.current.collectAsStateWithLifecycle()
    when (val current = state) {
        SessionState.Starting -> Box(Modifier.fillMaxSize().background(ZankupTheme.colors.page))
        SessionState.SignedOut -> SignInScreen()
        SessionState.Locked -> LockScreen(session)
        is SessionState.SignedIn -> AppShell(current.api, session, reachability, link, onLinkHandled)
    }
}

/** The token is on the phone but the fingerprint was not given: the wordmark and "Unlock". */
@Composable
private fun LockScreen(session: Session) {
    val activity = LocalActivity.current as FragmentActivity
    val prompter = remember(activity) { Prompter(activity) }
    val scope = rememberCoroutineScope()
    Column(
        Modifier
            .fillMaxSize()
            .background(ZankupTheme.colors.page)
            .safeDrawingPadding()
            .padding(ZankupTheme.spacing.l),
        verticalArrangement = Arrangement.spacedBy(ZankupTheme.spacing.l, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Wordmark(height = 24.dp)
        ZankupIcon(ZIcon.Fingerprint, contentDescription = null, tint = ZankupTheme.colors.accent, size = 48.dp)
        Text(
            stringResource(R.string.locked),
            style = MaterialTheme.typography.bodyLarge,
            color = ZankupTheme.colors.muted,
            textAlign = TextAlign.Center
        )
        Button(onClick = { scope.launch { session.unlock(prompter) } }) { Text(stringResource(R.string.unlock)) }
    }
}
