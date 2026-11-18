package com.zankup.android.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.zankup.android.designsystem.catalogue.Catalogue
import com.zankup.android.designsystem.components.ChipPicker
import com.zankup.android.designsystem.theme.ThemeChoice
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.designsystem.theme.label

/** Debug builds only: the shared components in any theme, picked at the top (OS-1104). */
class CatalogueActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            var choice by rememberSaveable { mutableStateOf(ThemeChoice.SYSTEM) }
            ZankupTheme(choice = choice) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(ZankupTheme.colors.page)
                        .safeDrawingPadding()
                        .verticalScroll(rememberScrollState())
                ) {
                    ChipPicker(ThemeChoice.entries, choice, { stringResource(it.label) }, { choice = it })
                    Catalogue()
                }
            }
        }
    }
}
