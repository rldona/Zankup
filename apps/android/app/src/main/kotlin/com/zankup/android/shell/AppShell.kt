package com.zankup.android.shell

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
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
@OptIn(ExperimentalComposeUiApi::class)
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
            // Test tags as resource ids, for the benchmarks' UI Automator (OS-1180).
            modifier = Modifier.semantics { testTagsAsResourceId = true },
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
                composable<HealthRoute> {
                    com.zankup.android.health.HealthScreen(
                        onWeight = { go(WeightRoute) },
                        onSettings = { go(SettingsSectionRoute("health")) },
                        onOpenActivity = { id -> go(ActivityRoute(id)) },
                        onConnect = { go(SettingsSectionRoute("health")) }
                    )
                }
                composable<YouRoute> {
                    com.zankup.android.you.YouScreen(
                        onStats = { go(StatsRoute) },
                        onEstimates = { go(EstimatesRoute) },
                        onCoach = { go(CoachRoute) },
                        onGear = { go(GearRoute) },
                        onImport = { go(ImportRoute) },
                        onAccount = { go(SettingsSectionRoute("account")) },
                        onSettings = { go(SettingsRoute) },
                        onOpenActivity = { id -> go(ActivityRoute(id)) }
                    )
                }

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
                composable<SyncRoute> { com.zankup.android.tools.SyncScreen(back) }

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

                composable<WeightRoute> { com.zankup.android.health.WeightScreen(back) }

                composable<SettingsRoute> {
                    com.zankup.android.settings.basic.SettingsScreen(
                        back,
                        onSection = { section -> go(SettingsSectionRoute(section)) },
                        onAppIcon = { go(AppIconRoute) }
                    )
                }
                composable<SettingsSectionRoute> {
                    val section = it.toRoute<SettingsSectionRoute>().section
                    if (section == "health") {
                        com.zankup.android.health.settings.HealthSettingsScreen(back, extra = {
                            com.zankup.android.settings.more.GoogleHealthSwitch()
                        })
                    } else {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val push = remember(api) {
                            com.zankup.android.settings.basic.PushControl(
                                available = Push.available(context),
                                enabled = { Push.enabled(context) },
                                setEnabled = { on -> Push.setEnabled(context, api, on) }
                            )
                        }
                        com.zankup.android.settings.basic.SettingsSectionScreen(
                            section,
                            back,
                            push = push,
                            onImport = { go(ImportRoute) },
                            onGear = { go(GearRoute) },
                            ai = {
                                com.zankup.android.settings.basic.SettingsBody {
                                    com.zankup.android.settings.more.AiSection(onAssistants = { go(AssistantsRoute) })
                                }
                            },
                            privacy = {
                                com.zankup.android.settings.basic.SettingsBody {
                                    com.zankup.android.settings.more.PrivacySection()
                                }
                            },
                            account = {
                                com.zankup.android.settings.basic.SettingsBody {
                                    // Signing out changes the session, and the root shows sign-in.
                                    com.zankup.android.settings.more.AccountSection(onSignedOut = {})
                                }
                            }
                        )
                    }
                }
                composable<AppIconRoute> { AppIconScreen(back) }
                composable<CoachRoute> {
                    com.zankup.android.coach.CoachScreen(
                        back,
                        onSettings = { go(SettingsSectionRoute("ai")) },
                        onOpenActivity = { id -> go(ActivityRoute(id)) }
                    )
                }
                composable<AssistantsRoute> {
                    com.zankup.android.coach.AssistantsScreen(back, onTokens = { go(SettingsSectionRoute("ai")) })
                }
                composable<GearRoute> { com.zankup.android.tools.GearScreen(back) }
                composable<ImportRoute> {
                    com.zankup.android.tools.ImportScreen(back, onOpenActivity = { id -> go(ActivityRoute(id)) })
                }
                composable<NotificationsRoute> {
                    com.zankup.android.tools.NotificationsScreen(back, onOpenActivity = { id -> go(ActivityRoute(id)) })
                }
            }
        }
    }
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
                modifier = Modifier.testTag("tab_" + tab.name.lowercase()),
                selected = selected,
                onClick = { goTab(nav, tab.route) },
                icon = {
                    if (tab == Tab.YOU) {
                        com.zankup.android.you.YouTabIcon(selected)
                    } else {
                        ZankupIcon(tab.icon, contentDescription = null, filled = selected)
                    }
                },
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
