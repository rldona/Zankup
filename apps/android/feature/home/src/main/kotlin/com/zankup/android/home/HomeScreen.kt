package com.zankup.android.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.designsystem.brand.Wordmark
import com.zankup.android.designsystem.components.EmptyState
import com.zankup.android.designsystem.components.ErrorState
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.network.ApiResult

/**
 * Home (OS-1130): the ZANKUP wordmark with the Coach (when [coach]) and Notifications on the right, over
 * this week, the last day with activities, the metrics and the last seven days. The header stays put
 * while they scroll. [onOpen] opens an activity, which starts loading as the finger lands on it.
 */
@Composable
fun HomeScreen(
    coach: Boolean,
    onOpen: (Long) -> Unit,
    onCoach: () -> Unit,
    onNotifications: () -> Unit,
    modifier: Modifier = Modifier,
    model: HomeViewModel = hiltViewModel()
) {
    val ui by model.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { model.refresh() }
    HomeContent(
        ui = ui,
        coach = coach,
        onOpen = onOpen,
        onPress = model::prefetch,
        onRetry = model::refresh,
        onCoach = onCoach,
        onNotifications = onNotifications,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeContent(
    ui: HomeUi,
    coach: Boolean,
    onOpen: (Long) -> Unit,
    onPress: (Long) -> Unit,
    onRetry: () -> Unit,
    onCoach: () -> Unit,
    onNotifications: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZankupTheme.colors
    Scaffold(
        modifier = modifier,
        containerColor = colors.page,
        topBar = {
            TopAppBar(
                title = { Wordmark(height = 15.dp) },
                actions = {
                    if (coach) {
                        IconButton(onClick = onCoach) {
                            ZankupIcon(ZIcon.Coach, contentDescription = stringResource(R.string.home_coach))
                        }
                    }
                    IconButton(onClick = onNotifications) {
                        ZankupIcon(
                            ZIcon.Notifications,
                            contentDescription = stringResource(R.string.home_notifications)
                        )
                    }
                },
                colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.page,
                    titleContentColor = colors.fg,
                    actionIconContentColor = colors.fg
                )
            )
        }
    ) { padding ->
        val direction = LocalLayoutDirection.current
        val spacing = ZankupTheme.spacing
        val side = Modifier.padding(horizontal = spacing.gutter)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding =
            PaddingValues(
                start = padding.calculateStartPadding(direction),
                end = padding.calculateEndPadding(direction),
                top = padding.calculateTopPadding() + spacing.xs,
                bottom = padding.calculateBottomPadding() + spacing.l
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            item("week") {
                val week = ui.week
                when {
                    week != null -> WeekSection(week, onOpen, onPress, side)
                    ui.weekFailure != null ->
                        ErrorState(onRetry, side, offline = ui.weekFailure is ApiResult.Offline)
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
