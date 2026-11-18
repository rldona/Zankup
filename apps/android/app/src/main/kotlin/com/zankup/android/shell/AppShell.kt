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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.zankup.android.data.Reachability
import com.zankup.android.designsystem.components.OfflineNotice
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
import com.zankup.android.push.Push
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
fun AppShell(
    api: ZankupApi,
    session: Session,
    reachability: Reachability,
    settings: com.zankup.android.data.UserSettings,
    link: Intent?,
    onLinkHandled: () -> Unit
) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val onTab = Tab.entries.any { tab -> destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true }
    val coach by produceState(false, api) {
        val status = call { api.service<AiApi>().getAiStatus() }
        reachability.note(status)
        value = (status as? ApiResult.Ok)?.value?.available == true
    }
    val unreachable by reachability.unreachable.collectAsStateWithLifecycle()
    // Figures in the person's units, as soon as their settings are known (metric until then).
    val units by settings.units.collectAsStateWithLifecycle(initialValue = com.zankup.android.domain.Units.METRIC)
    LaunchedEffect(api) { settings.query.refresh() }
    val formats = remember(units) { com.zankup.android.ui.Formats(units, java.util.Locale.getDefault()) }
    LaunchedEffect(link) {
        if (link != null) {
            // A notification carries the web path it is about; a link, the app's own address.
            val path = link.getStringExtra(Push.EXTRA_URL)
            if (path != null) nav.navigate(Links.routeForWebPath(path)) else nav.handleDeepLink(link)
            onLinkHandled()
        }
    }
    AskForNotifications()
    androidx.compose.runtime.CompositionLocalProvider(com.zankup.android.ui.LocalFormats provides formats) {
        Scaffold(
            containerColor = ZankupTheme.colors.page,
            bottomBar = {
                Column {
                    if (unreachable) OfflineNotice()
                    if (onTab) TabBar(nav, destination)
                }
            }
        ) { padding ->
            NavHost(
                nav,
                startDestination = HomeRoute,
                modifier = Modifier.padding(bottom = padding.calculateBottomPadding())
            ) {
                val back: () -> Unit = { nav.popBackStack() }
                val go: (Any) -> Unit = { nav.navigate(it) }

                composable<HomeRoute> {
                    com.zankup.android.home.HomeScreen(
                        coach = coach,
                        onOpen = { id -> go(ActivityRoute(id)) },
                        onCoach = { go(CoachRoute) },
                        onNotifications = { go(NotificationsRoute) }
                    )
                }
                composable<ActivitiesRoute> {
                    com.zankup.android.feed.FeedScreen(
                        onOpen = { id -> go(ActivityRoute(id)) },
                        onSync = { go(SyncRoute) },
                        onCalendar = { go(CalendarRoute) }
                    )
                }
                composable<ProgressRoute> {
                    com.zankup.android.progress.ProgressScreen(
                        onVolume = { sport -> go(ProgressDetailRoute(sport)) },
                        onTrainingLog = { go(TrainingLogRoute) },
                        onFitness = { go(FitnessRoute) },
                        onEffort = { go(EffortRoute) },
                        onPredictions = { go(PredictionsRoute) },
                        onGoals = { go(ObjectivesRoute) },
                        onZones = { go(ZonesRoute) },
                        onSkills = { go(SkillsRoute) },
                        onBests = { key -> go(BestsRoute(key ?: "10k")) },
                        onMonthly = { go(MonthlyRoute) },
                        onSources = { go(SourcesRoute) },
                        onOpenActivity = { id -> go(ActivityRoute(id)) }
                    )
                }
                composable<HealthRoute> { HealthTab(go) }
                composable<YouRoute> { YouTab(go, session) }

                val scheme = BuildConfig.LINK_SCHEME
                composable<ActivityRoute>(
                    deepLinks = listOf(navDeepLink<ActivityRoute>(basePath = "$scheme://activity"))
                ) {
                    com.zankup.android.activity.ActivityScreen(
                        it.toRoute<ActivityRoute>().id,
                        com.zankup.android.activity.ActivityLinks(
                            back = back,
                            open = { id -> go(ActivityRoute(id)) },
                            analysis = { id -> go(AnalysisRoute(id)) },
                            matches = { id -> go(MatchesRoute(id)) },
                            fitness = { go(FitnessRoute) },
                            effort = { go(EffortRoute) },
                            goals = { go(ObjectivesRoute) },
                            bests = { key -> go(BestsRoute(key ?: "10k")) }
                        ),
                        aiAvailable = coach
                    )
                }
                composable<AnalysisRoute> {
                    com.zankup.android.activity.AnalysisScreen(it.toRoute<AnalysisRoute>().id, back)
                }
                composable<MatchesRoute> {
                    com.zankup.android.activity.MatchesScreen(
                        it.toRoute<MatchesRoute>().id,
                        back,
                        onOpen = { id -> go(ActivityRoute(id)) }
                    )
                }
                composable<CalendarRoute> {
                    com.zankup.android.stats.CalendarScreen(back, onOpenActivity = { id -> go(ActivityRoute(id)) })
                }
                composable<SyncRoute> { Placeholder(stringResource(R.string.nav_sync), "OS-1163", back) }

                composable<ProgressDetailRoute> {
                    com.zankup.android.progress.ProgressDetailScreen(
                        it.toRoute<ProgressDetailRoute>().sport,
                        back,
                        onOpenActivity = { id -> go(ActivityRoute(id)) }
                    )
                }
                composable<TrainingLogRoute> {
                    com.zankup.android.progress.TrainingLogScreen(
                        back,
                        onOpenActivity = { id -> go(ActivityRoute(id)) },
                        onSearch = { goTab(nav, ActivitiesRoute) }
                    )
                }
                composable<FitnessRoute> {
                    com.zankup.android.progress.FitnessScreen(back, onOpenActivity = { id -> go(ActivityRoute(id)) })
                }
                composable<EffortRoute> {
                    com.zankup.android.progress.EffortScreen(back, onOpenActivity = { id -> go(ActivityRoute(id)) })
                }
                composable<PredictionsRoute> { com.zankup.android.progress.PredictionsScreen(back) }
                composable<ObjectivesRoute> {
                    com.zankup.android.progress.more.ObjectivesScreen(
                        back,
                        onNew = { go(ObjectiveEditRoute()) },
                        onEdit = { id -> go(ObjectiveEditRoute(id)) }
                    )
                }
                composable<ObjectiveEditRoute> {
                    com.zankup.android.progress.more.ObjectiveEditScreen(
                        it.toRoute<ObjectiveEditRoute>().id,
                        back,
                        onSaved = back
                    )
                }
                composable<ZonesRoute> {
                    com.zankup.android.progress.more.ZonesScreen(back, onHeartSettings = {
                        go(SettingsSectionRoute("heart"))
                    })
                }
                composable<SkillsRoute> { com.zankup.android.progress.more.SkillsScreen(back) }
                composable<BestsRoute> {
                    com.zankup.android.progress.more.BestsScreen(
                        it.toRoute<BestsRoute>().key,
                        back,
                        onOpenActivity = { id -> go(ActivityRoute(id)) }
                    )
                }
                composable<MonthlyRoute> { com.zankup.android.progress.more.MonthlyScreen(back) }
                composable<SourcesRoute> {
                    com.zankup.android.progress.more.SourcesScreen(
                        back,
                        onAccount = { go(SettingsSectionRoute("account")) },
                        onImport = { go(ImportRoute) },
                        onAssistants = { go(AssistantsRoute) },
                        onHealthSettings = { go(SettingsSectionRoute("health")) }
                    )
                }

                val openActivity: (Long) -> Unit = { id -> go(ActivityRoute(id)) }
                composable<StatsRoute> {
                    com.zankup.android.stats.StatsScreen(
                        back,
                        onReview = { period -> go(ReviewRoute(period)) },
                        onHeatmap = { go(HeatmapRoute) },
                        onBests = { key -> go(BestsRoute(key ?: "10k")) },
                        onRoutes = { go(RoutesRoute) },
                        onSegments = { go(SegmentsRoute) },
                        onYears = { go(YearsRoute) },
                        onOpenActivity = openActivity
                    )
                }
                composable<EstimatesRoute> { com.zankup.android.stats.EstimatesScreen(back, openActivity) }
                composable<YearsRoute> { com.zankup.android.stats.YearsScreen(back) }
                composable<HeatmapRoute> { com.zankup.android.stats.HeatmapScreen(back) }
                composable<ReviewRoute> {
                    com.zankup.android.stats.ReviewScreen(it.toRoute<ReviewRoute>().period, back, openActivity)
                }
                composable<RoutesRoute> {
                    com.zankup.android.routes.RoutesScreen(back, onOpen = { id -> go(RouteRoute(id)) })
                }
                composable<RouteRoute> {
                    com.zankup.android.routes.RouteScreen(it.toRoute<RouteRoute>().id, back, openActivity)
                }
                composable<SegmentsRoute> {
                    com.zankup.android.routes.SegmentsScreen(back, onOpen = { id -> go(SegmentRoute(id)) })
                }
                composable<SegmentRoute> {
                    com.zankup.android.routes.SegmentScreen(it.toRoute<SegmentRoute>().id, back, openActivity)
                }

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
                composable<NotificationsRoute> {
                    Placeholder(stringResource(R.string.nav_notifications), "OS-1163", back)
                }
            }
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

/** Opens a tab as the bar does: one copy of each, its state kept. */
private fun goTab(nav: NavHostController, route: Any) {
    nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun TabBar(nav: NavHostController, destination: androidx.navigation.NavDestination?) {
    val colors = ZankupTheme.colors
    NavigationBar(containerColor = colors.surface) {
        Tab.entries.forEach { tab ->
            val selected = destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true
            NavigationBarItem(
                selected = selected,
                onClick = { goTab(nav, tab.route) },
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
