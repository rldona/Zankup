package com.zankup.android.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zankup.android.designsystem.R

/** The app's icons: Material Symbols Rounded (scripts/icons.txt), the filled one where it has it. */
enum class ZIcon(@param:DrawableRes val outlined: Int, @param:DrawableRes val filled: Int = outlined) {
    Home(R.drawable.ic_home, R.drawable.ic_home_fill),
    Activities(R.drawable.ic_cards_stack, R.drawable.ic_cards_stack_fill),
    Progress(R.drawable.ic_monitoring, R.drawable.ic_monitoring_fill),
    Health(R.drawable.ic_favorite, R.drawable.ic_favorite_fill),
    You(R.drawable.ic_person, R.drawable.ic_person_fill),
    Back(R.drawable.ic_arrow_back),
    Chevron(R.drawable.ic_chevron_right),
    Close(R.drawable.ic_close),
    Check(R.drawable.ic_check),
    Add(R.drawable.ic_add),
    Search(R.drawable.ic_search),
    Sync(R.drawable.ic_sync),
    Notifications(R.drawable.ic_notifications, R.drawable.ic_notifications_fill),
    Settings(R.drawable.ic_settings),
    Coach(R.drawable.ic_chat),
    Error(R.drawable.ic_error),
    Offline(R.drawable.ic_cloud_off),
    Refresh(R.drawable.ic_refresh),
    More(R.drawable.ic_more_vert),
    Show(R.drawable.ic_visibility),
    Hide(R.drawable.ic_visibility_off),
    Fingerprint(R.drawable.ic_fingerprint),
    Run(R.drawable.ic_directions_run),
    Ride(R.drawable.ic_directions_bike),
    Walk(R.drawable.ic_directions_walk),
    Strength(R.drawable.ic_fitness_center),
    Row(R.drawable.ic_rowing),
    Swim(R.drawable.ic_pool),
    Other(R.drawable.ic_exercise)
}

@Composable
fun ZankupIcon(
    icon: ZIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp
) {
    Icon(
        painter = painterResource(if (filled) icon.filled else icon.outlined),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint
    )
}
