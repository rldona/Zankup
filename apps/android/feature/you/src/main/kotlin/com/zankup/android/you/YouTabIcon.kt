package com.zankup.android.you

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.zankup.android.api.endpoints.AuthApi
import com.zankup.android.api.model.Me
import com.zankup.android.data.Store
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.network.call
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** The profile as the tab bar needs it: its photo, from the profile kept for Home and You. */
@HiltViewModel
class AvatarModel
@Inject
constructor(store: Store) : ViewModel() {
    val me = store.query(YouViewModel.ME_KEY, Me.serializer()) { api -> call { api.service<AuthApi>().getMe() } }
}

/**
 * The You tab's icon: the person's photo in a circle (ringed in the accent when selected), as the React
 * Native app's tab bar shows it; the person icon until there is one.
 */
@Composable
fun YouTabIcon(selected: Boolean, model: AvatarModel = hiltViewModel()) {
    val state by model.me.state.collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(Unit) { if (state?.data == null) model.me.refresh() }
    val avatar = state?.data?.avatarUrl
    if (avatar == null) {
        ZankupIcon(ZIcon.You, contentDescription = null, filled = selected)
        return
    }
    val colors = ZankupTheme.colors
    AsyncImage(
        model = avatar,
        contentDescription = null,
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(colors.surface2)
            .then(if (selected) Modifier.border(2.dp, colors.accent, CircleShape) else Modifier)
    )
}
