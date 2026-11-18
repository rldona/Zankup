package com.zankup.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zankup.android.api.endpoints.DefaultApi
import com.zankup.android.designsystem.brand.Mark
import com.zankup.android.designsystem.brand.Wordmark
import com.zankup.android.designsystem.components.ZankupCard
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.network.ApiResult
import com.zankup.android.network.Instance
import com.zankup.android.network.MemoryTokenStore
import com.zankup.android.network.ZankupApi
import com.zankup.android.network.call

/** The reference instance, until sign-in (OS-1110) asks for the person's own. */
private const val DEFAULT_INSTANCE = "api.zankup.com"

/**
 * The foundation build's only screen: the brand, what this build is, and the API client checking the
 * instance (`GET /api/health`), as the web's "API connected". Sign-in replaces it in block 1.
 */
@Composable
fun StartScreen() {
    val colors = ZankupTheme.colors
    val host = DEFAULT_INSTANCE
    var status by remember { mutableStateOf<String?>(null) }
    val checking = stringResource(R.string.start_checking, host)
    val unreachable = stringResource(R.string.start_unreachable, host)
    val connected = stringResource(R.string.start_connected)
    LaunchedEffect(Unit) {
        val api = ZankupApi(Instance.parse(host)!!, MemoryTokenStore(), debug = BuildConfig.DEBUG)
        status =
            when (val result = call { api.service<DefaultApi>().getHealth() }) {
                is ApiResult.Ok -> connected.format(host, result.value.version)
                else -> unreachable
            }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.page)
            .safeDrawingPadding()
            .padding(ZankupTheme.spacing.l),
        verticalArrangement = Arrangement.spacedBy(ZankupTheme.spacing.l, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Mark(height = 56.dp, color = colors.accent)
        Wordmark(height = 24.dp)
        ZankupCard {
            Text(
                stringResource(R.string.start_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.fg
            )
            Text(
                stringResource(R.string.start_body),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
                modifier = Modifier.padding(top = ZankupTheme.spacing.xs)
            )
        }
        Text(
            status ?: checking,
            style = MaterialTheme.typography.bodySmall,
            color = colors.muted,
            textAlign = TextAlign.Center
        )
    }
}
