package com.zankup.android.shell

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import com.zankup.android.BuildConfig
import com.zankup.android.R
import com.zankup.android.api.endpoints.AiApi
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.navigation.ActivitiesRoute
import com.zankup.android.navigation.ActivityRoute
import com.zankup.android.navigation.AnalysisRoute
import com.zankup.android.navigation.AppIconRoute
import com.zankup.android.navigation.AssistantsRoute
import com.zankup.android.navigation.BestsRoute
import com.zankup.android.navigation.CalendarRoute
import com.zankup.android.navigation.CoachRoute
import com.zankup.android.navigation.EffortRoute
import com.zankup.android.navigation.EstimatesRoute
import com.zankup.android.navigation.FitnessRoute
import com.zankup.android.navigation.GearRoute
import com.zankup.android.navigation.HealthRoute
import com.zankup.android.navigation.HeatmapRoute
import com.zankup.android.navigation.HomeRoute
import com.zankup.android.navigation.ImportRoute
import com.zankup.android.navigation.Links
import com.zankup.android.navigation.MatchesRoute
import com.zankup.android.navigation.MonthlyRoute
import com.zankup.android.navigation.NotificationsRoute
import com.zankup.android.navigation.ObjectiveEditRoute
import com.zankup.android.navigation.ObjectivesRoute
import com.zankup.android.navigation.PredictionsRoute
import com.zankup.android.navigation.ProgressDetailRoute
import com.zankup.android.navigation.ProgressRoute
import com.zankup.android.navigation.ReviewRoute
import com.zankup.android.navigation.RouteRoute
import com.zankup.android.navigation.RoutesRoute
import com.zankup.android.navigation.SegmentRoute
import com.zankup.android.navigation.SegmentsRoute
import com.zankup.android.navigation.SettingsRoute
import com.zankup.android.navigation.SettingsSectionRoute
import com.zankup.android.navigation.SkillsRoute
import com.zankup.android.navigation.SourcesRoute
import com.zankup.android.navigation.StatsRoute
import com.zankup.android.navigation.SyncRoute
import com.zankup.android.navigation.TrainingLogRoute
import com.zankup.android.navigation.WeightRoute
import com.zankup.android.navigation.YearsRoute
import com.zankup.android.navigation.YouRoute
import com.zankup.android.navigation.ZonesRoute
import com.zankup.android.network.ApiResult
import com.zankup.android.network.ZankupApi
import com.zankup.android.network.call
import com.zankup.android.session.Session
import kotlin.reflect.KClass

/** A tab of the bar, in the React Native app's order. */
private enum class Tab(val route: Any, val routeClass: KClass<*>, val icon: ZIcon, val label: Int) {
    HOME(HomeRoute, HomeRoute::class, ZIcon.Home, R.string.nav_home),
    ACTIVITIES(ActivitiesRoute, ActivitiesRoute::class, ZIcon.Activities, R.string.nav_activities),
    PROGRESS(ProgressRoute, ProgressRoute::class, ZIcon.Progress, R.string.nav_progress),
    HEALTH(HealthRoute, HealthRoute::class, ZIcon.Health, R.string.nav_health),
    YOU(YouRoute, YouRoute::class, ZIcon.You, R.string.nav_you)
}

/** The signed-in app: the tab bar over the tabs, and every other screen stacked over them (OS-1112). */
@Composable
fun AppShell(api: ZankupApi, session: Session, link: Intent?, onLinkHandled: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val onTab = Tab.entries.any { tab -> destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true }
    val coach by produceState(false, api) {
        value = (call { api.service<AiApi>().getAiStatus() } as? ApiResult.Ok)?.value?.available == true
    }
    LaunchedEffect(link) {
        if (link != null) {
            nav.handleDeepLink(link)
            onLinkHandled()
        }
    }
    Scaffold(
        containerColor = ZankupTheme.colors.page,
        bottomBar = { if (onTab) TabBar(nav, destination) }
    ) { padding ->
        NavHost(
            nav,
            startDestination = HomeRoute,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding())
        ) {
            val back: () -> Unit = { nav.popBackStack() }
            val go: (Any) -> Unit = { nav.navigate(it) }

            composable<HomeRoute> { HomeTab(coach, go) }
            composable<ActivitiesRoute> { ActivitiesTab(go) }
            composable<ProgressRoute> { ProgressTab(go) }
            composable<HealthRoute> { HealthTab(go) }
            composable<YouRoute> { YouTab(go, session) }

            val scheme = BuildConfig.LINK_SCHEME
            composable<ActivityRoute>(deepLinks = listOf(navDeepLink<ActivityRoute>(basePath = "$scheme://activity"))) {
                Placeholder(stringResource(R.string.nav_activity), "OS-1134", back)
            }
            composable<AnalysisRoute> { Placeholder(stringResource(R.string.nav_analysis), "OS-1134", back) }
            composable<MatchesRoute> { Placeholder(stringResource(R.string.nav_matches), "OS-1134", back) }
            composable<CalendarRoute> { Placeholder(stringResource(R.string.nav_calendar), "OS-1141", back) }
            composable<SyncRoute> { Placeholder(stringResource(R.string.nav_sync), "OS-1163", back) }

            composable<ProgressDetailRoute> {
                Placeholder(stringResource(R.string.nav_progress_detail), "OS-1140", back)
            }
            composable<TrainingLogRoute> { Placeholder(stringResource(R.string.nav_training_log), "OS-1140", back) }
            composable<FitnessRoute> { Placeholder(stringResource(R.string.nav_fitness), "OS-1140", back) }
            composable<EffortRoute> { Placeholder(stringResource(R.string.nav_effort), "OS-1140", back) }
            composable<PredictionsRoute> { Placeholder(stringResource(R.string.nav_predictions), "OS-1140", back) }
            composable<ObjectivesRoute> { Placeholder(stringResource(R.string.nav_objectives), "OS-1140", back) }
            composable<ObjectiveEditRoute> { Placeholder(stringResource(R.string.nav_objective_new), "OS-1140", back) }
            composable<ZonesRoute> { Placeholder(stringResource(R.string.nav_zones), "OS-1140", back) }
            composable<SkillsRoute> { Placeholder(stringResource(R.string.nav_skills), "OS-1140", back) }
            composable<BestsRoute> { Placeholder(stringResource(R.string.nav_bests), "OS-1140", back) }
            composable<MonthlyRoute> { Placeholder(stringResource(R.string.nav_monthly), "OS-1140", back) }
            composable<SourcesRoute> { Placeholder(stringResource(R.string.nav_sources), "OS-1140", back) }

            composable<StatsRoute> { Placeholder(stringResource(R.string.nav_stats), "OS-1141", back) }
            composable<EstimatesRoute> { Placeholder(stringResource(R.string.nav_estimates), "OS-1141", back) }
            composable<YearsRoute> { Placeholder(stringResource(R.string.nav_years), "OS-1141", back) }
            composable<HeatmapRoute> { Placeholder(stringResource(R.string.nav_heatmap), "OS-1141", back) }
            composable<ReviewRoute> { Placeholder(stringResource(R.string.nav_review), "OS-1141", back) }
            composable<RoutesRoute> { Placeholder(stringResource(R.string.nav_routes), "OS-1142", back) }
            composable<RouteRoute> { Placeholder(stringResource(R.string.nav_route), "OS-1142", back) }
            composable<SegmentsRoute> { Placeholder(stringResource(R.string.nav_segments), "OS-1142", back) }
            composable<SegmentRoute> { Placeholder(stringResource(R.string.nav_segment), "OS-1142", back) }

            composable<WeightRoute> { Placeholder(stringResource(R.string.nav_weight), "OS-1150", back) }

            composable<SettingsRoute> { SettingsList(go, back) }
            composable<SettingsSectionRoute> {
                val section = it.toRoute<SettingsSectionRoute>().section
                Placeholder(stringResource(sectionTitle(section)), "OS-1161", back)
            }
            composable<AppIconRoute> { Placeholder(stringResource(R.string.nav_app_icon), "OS-1171", back) }
            composable<CoachRoute> { Placeholder(stringResource(R.string.nav_coach), "OS-1162", back) }
            composable<AssistantsRoute> { Placeholder(stringResource(R.string.nav_assistants), "OS-1162", back) }
            composable<GearRoute> { Placeholder(stringResource(R.string.nav_gear), "OS-1163", back) }
            composable<ImportRoute> { Placeholder(stringResource(R.string.nav_import), "OS-1163", back) }
            composable<NotificationsRoute> { Placeholder(stringResource(R.string.nav_notifications), "OS-1163", back) }
        }
    }
}

internal fun sectionTitle(section: String) = when (section) {
    "heart" -> R.string.section_heart
    "notifications" -> R.string.section_notifications
    "ai" -> R.string.section_ai
    "privacy" -> R.string.section_privacy
    "health" -> R.string.section_health
    "data" -> R.string.section_data
    "account" -> R.string.section_account
    else -> R.string.section_general
}

@Composable
private fun TabBar(nav: NavHostController, destination: androidx.navigation.NavDestination?) {
    val colors = ZankupTheme.colors
    NavigationBar(containerColor = colors.surface) {
        Tab.entries.forEach { tab ->
            val selected = destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    nav.navigate(tab.route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { ZankupIcon(tab.icon, contentDescription = null, filled = selected) },
                label = { Text(stringResource(tab.label)) },
                colors =
                NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.accent,
                    selectedTextColor = colors.accent,
                    indicatorColor = colors.accentSoft,
                    unselectedIconColor = colors.muted,
                    unselectedTextColor = colors.muted
                )
            )
        }
    }
}

/** A tab's or a screen's body until its block builds it: what it is, and which ticket brings it. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun Placeholder(
    title: String,
    ticket: String,
    onBack: (() -> Unit)?,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {}
) {
    Scaffold(
        containerColor = ZankupTheme.colors.page,
        topBar = { com.zankup.android.designsystem.components.ZankupTopBar(title, onBack = onBack, actions = actions) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            content()
            com.zankup.android.designsystem.components.EmptyState(
                title = stringResource(R.string.coming_soon),
                body = stringResource(R.string.coming_soon_body, ticket),
                icon = ZIcon.Progress
            )
        }
    }
}

/** Settings' sections, each opening its own screen. */
@Composable
private fun SettingsList(go: (Any) -> Unit, back: () -> Unit) {
    Placeholder(stringResource(R.string.nav_settings), "OS-1161", back) {
        Links.SECTIONS.forEach { section ->
            com.zankup.android.designsystem.components.ListRow(
                title = stringResource(sectionTitle(section)),
                onClick = { go(SettingsSectionRoute(section)) }
            )
        }
    }
}
