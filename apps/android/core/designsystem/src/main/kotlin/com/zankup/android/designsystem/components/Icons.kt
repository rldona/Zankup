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
    Coach(R.drawable.ic_wand_stars),
    Chat(R.drawable.ic_chat),
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
    Other(R.drawable.ic_exercise),
    Calendar(R.drawable.ic_calendar_month),
    SignOut(R.drawable.ic_logout),
    Lock(R.drawable.ic_lock),
    Play(R.drawable.ic_play_arrow, R.drawable.ic_play_arrow_fill),
    Pause(R.drawable.ic_pause, R.drawable.ic_pause_fill),
    Options(R.drawable.ic_tune),
    Edit(R.drawable.ic_edit_square),
    Share(R.drawable.ic_share),
    AddPhoto(R.drawable.ic_add_photo_alternate),
    Medal(R.drawable.ic_emoji_events, R.drawable.ic_emoji_events_fill),
    Delete(R.drawable.ic_delete),
    Goal(R.drawable.ic_flag),
    Bolt(R.drawable.ic_bolt),
    Route(R.drawable.ic_route),
    Expand(R.drawable.ic_expand_more),
    Collapse(R.drawable.ic_expand_less),
    Steps(R.drawable.ic_footprint),
    Hrv(R.drawable.ic_monitor_heart),
    Gauge(R.drawable.ic_speed),
    Info(R.drawable.ic_info),
    Reviews(R.drawable.ic_summarize),
    Layers(R.drawable.ic_layers),
    Timeline(R.drawable.ic_timeline),
    LineChart(R.drawable.ic_show_chart),
    ChevronLeft(R.drawable.ic_chevron_left),
    Upload(R.drawable.ic_upload),
    Award(R.drawable.ic_military_tech, R.drawable.ic_military_tech_fill),
    MoreHoriz(R.drawable.ic_more_horiz),
    HealthConnect(R.drawable.ic_ecg_heart),
    Timer(R.drawable.ic_timer),
    Devices(R.drawable.ic_devices),
    Link(R.drawable.ic_link)
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
