package com.zankup.android.healthconnect

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zankup.android.designsystem.theme.ZankupTheme

/** How Zankup uses what it reads from Health Connect, shown from Health Connect's own screens. */
class PrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZankupTheme {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(ZankupTheme.colors.page)
                        .safeDrawingPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                    Text(
                        stringResource(R.string.hc_privacy_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = ZankupTheme.colors.fg
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.hc_privacy_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = ZankupTheme.colors.fg
                    )
                }
            }
        }
    }
}
