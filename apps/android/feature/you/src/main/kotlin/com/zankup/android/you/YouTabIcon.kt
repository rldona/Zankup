package com.zankup.android.you

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
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
 * The You tab's icon: the person's photo in a circle, as the React Native app's tab bar shows it; the
 * person icon until there is one.
 */
@Composable
fun YouTabIcon(selected: Boolean, avatar: String?) {
    if (avatar == null) {
        ZankupIcon(ZIcon.You, contentDescription = null, filled = selected)
        return
    }
    AsyncImage(
        model = avatar,
        contentDescription = null,
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(ZankupTheme.colors.surface2)
    )
}
