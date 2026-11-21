package com.zankup.android.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.designsystem.brand.Wordmark
import com.zankup.android.designsystem.components.EmptyState
import com.zankup.android.designsystem.components.ErrorState
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.components.ZankupPullToRefresh
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.network.ApiResult
import kotlinx.coroutines.launch

/**
 * Home (OS-1130): the ZANKUP wordmark with the Coach and Notifications on the right, over this week,
 * the last day with activities, the metrics and the last seven days. The header stays put while they
 * scroll, in the tab bar's colour. [onOpen] opens an activity, which starts loading as the finger lands
 * on it.
 */
@Composable
fun HomeScreen(
    onOpen: (Long) -> Unit,
    onCoach: () -> Unit,
    onNotifications: () -> Unit,
    unreadNotifications: Int = 0,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    model: HomeViewModel = hiltViewModel()
) {
    val ui by model.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { model.refresh() }
    HomeContent(
        ui = ui,
        onOpen = onOpen,
        onPress = model::prefetch,
        refreshing = ui.refreshing,
        onRefresh = model::pullRefresh,
        onCoach = onCoach,
        onNotifications = onNotifications,
        unreadNotifications = unreadNotifications,
        modifier = modifier,
        listState = listState
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeContent(
    ui: HomeUi,
    onOpen: (Long) -> Unit,
    onPress: (Long) -> Unit,
    /** Whether a fresh copy of the day's answers is on its way, for the pull-to-refresh. */
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onCoach: () -> Unit,
    onNotifications: () -> Unit,
    unreadNotifications: Int = 0,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    val colors = ZankupTheme.colors
    val list = listState
    val scope = rememberCoroutineScope()
    val backToTop = stringResource(R.string.home_top)
    // As Activities: the bar, then the list under it — the pull's indicator needs to be in sight, and
    // inside a Scaffold the content starts at the screen's edge, behind the bar.
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                // A tap on the wordmark jumps back to the top of the day, as Android's own apps.
                Box(
                    Modifier
                        .semantics { contentDescription = backToTop }
                        .clickable(role = Role.Button) { scope.launch { list.animateScrollToItem(0) } }
                ) { Wordmark(height = 15.dp) }
            },
            actions = {
                IconButton(onClick = onCoach) {
                    ZankupIcon(ZIcon.Coach, contentDescription = stringResource(R.string.home_coach))
                }
                IconButton(onClick = onNotifications) {
                    Box {
                        ZankupIcon(
                            ZIcon.Notifications,
                            contentDescription = stringResource(R.string.home_notifications)
                        )
                        if (unreadNotifications > 0) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(colors.formNegative),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (unreadNotifications > 99) "99+" else unreadNotifications.toString(),
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 9.sp
                                )
                            }
                        }
                    }
                }
            },
            colors =
            TopAppBarDefaults.topAppBarColors(
                // The tab bar's colour, so the two frames of the screen match.
                containerColor = colors.surface,
                titleContentColor = colors.fg,
                actionIconContentColor = colors.fg
            )
        )
        val spacing = ZankupTheme.spacing
        val side = Modifier.padding(horizontal = spacing.gutter)
        // A pull from the top fetches everything again from the API (the kept copy is not asked for).
        ZankupPullToRefresh(
            refreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize(),
                // The gutter is each card's own (`side`): here it would add up, and a card with double
                // margin stops being read as full width and loses the bleed to the screen's edges.
                contentPadding = PaddingValues(bottom = spacing.l),
                verticalArrangement = Arrangement.spacedBy(spacing.s)
            ) {
                item("week") {
                    val week = ui.week
                    when {
                        week != null -> WeekSection(week, onOpen, onPress, side)
                        ui.weekFailure != null ->
                            ErrorState(onRefresh, side, offline = ui.weekFailure is ApiResult.Offline)
                        else -> WeekSkeleton(side)
                    }
                }
                val recent = ui.recent
                when {
                    !ui.feedLoaded -> item("last-day") { CardSkeleton(side) }
                    ui.empty ->
                        item("empty") {
                            EmptyState(
                                title = stringResource(R.string.home_empty),
                                body = stringResource(R.string.home_empty_body),
                                icon = ZIcon.Activities,
                                modifier = side
                            )
                        }
                    recent != null -> item("last-day") { LastDaySection(recent, ui.today, onOpen, onPress) }
                }
                ui.dayHealth?.let { day -> item("day-health") { DayHealthCard(day, side) } }
                item("metrics") { MetricsSection(ui.metrics, side) }
                item("last-7") { LastSevenSection(ui, side) }
            }
        }
    }
}
