package com.zankup.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.zankup.android.data.Reachability
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.push.Push
import com.zankup.android.session.Prompter
import com.zankup.android.session.Session
import com.zankup.android.session.SessionState
import com.zankup.android.shell.Root
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * The only activity. A FragmentActivity because Android's biometric prompt needs one. The splash stays
 * until the session is decided: the fingerprint is asked for over it, as the React Native app does.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var session: Session

    @Inject lateinit var reachability: Reachability

    @Inject lateinit var settings: com.zankup.android.data.UserSettings

    /** The link that opened the app (or reached it while open), handed to the navigation once signed in. */
    private var link by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { session.current.value == SessionState.Starting }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        link = intent.takeIf { it.opensSomething() }
        if (session.current.value == SessionState.Starting) {
            lifecycleScope.launch { session.unlock(Prompter(this@MainActivity)) }
        }
        setContent {
            ZankupTheme {
                Root(session, reachability, settings, link, onLinkHandled = { link = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.opensSomething()) link = intent
    }

    /** A link (`zankup-next://…`) or a notification's web path. */
    private fun Intent.opensSomething() = data != null || hasExtra(Push.EXTRA_URL)
}
