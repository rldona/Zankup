package com.zankup.android.shell

import android.animation.ValueAnimator
import android.content.Intent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.IntOffset
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
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
import com.zankup.android.Perf
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
import com.zankup.android.tools.NotificationsBadgeModel
import kotlin.reflect.KClass
import kotlinx.coroutines.launch

/** A tab of the bar, in the React Native app's order. */
private enum class Tab(val route: Any, val routeClass: KClass<*>, val icon: ZIcon, val label: Int) {
    HOME(HomeRoute, HomeRoute::class, ZIcon.Home, R.string.nav_home),
    ACTIVITIES(ActivitiesRoute, ActivitiesRoute::class, ZIcon.Activities, R.string.nav_activities),
    PROGRESS(ProgressRoute, ProgressRoute::class, ZIcon.Progress, R.string.nav_progress),
    HEALTH(HealthRoute, HealthRoute::class, ZIcon.Health, R.string.nav_health),
    YOU(YouRoute, YouRoute::class, ZIcon.You, R.string.nav_you)
}

/** Whether the destination is one of the five tabs, and not a screen stacked over them. */
private fun NavDestination.isTab(): Boolean = hierarchy.any { node -> Tab.entries.any { node.hasRoute(it.routeClass) } }

/** No movement at all: one tab taking another's place (as Strava's do), or the system's animations off. */
private val StillEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
    { EnterTransition.None }
private val StillExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
    { ExitTransition.None }

/** How long a screen takes to come in from the right (or to leave to it): Strava's push, measured 400 ms. */
private const val SLIDE_MS = 400

/** A screen underneath gives way only a quarter: the depth that reads as a stack. */
private const val PARALLAX = 4

/** How faint the screen underneath gets in the dark themes, where the page behind it reads as a dim. */
private const val DIM_DARK = 0.8f

/**
 * Strava's push, as measured frame by frame on the S26: off the mark at once and a long settle (a
 * bezier fitted to its own movement, within 0.5 % at every frame), not a linear-out.
 */
private val Motion = CubicBezierEasing(0.2f, 0.6f, 0.05f, 1f)
private val slide = tween<IntOffset>(SLIDE_MS, easing = Motion)
private val dim = tween<Float>(SLIDE_MS, easing = Motion)

/** The screen pushed on top comes in from the right over the one that was there, whole and undimmed. */
private val PushEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
    { slideInHorizontally(slide) { it } }

/** The screen that stays underneath steps aside a quarter and darkens, instead of sliding away whole. */
private fun underneathExit(dimTo: Float): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
    { slideOutHorizontally(slide) { -it / PARALLAX } + fadeOut(dim, targetAlpha = dimTo) }

/** Coming back, the two play the same movement backwards. */
private val PopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
    { slideInHorizontally(slide) { -it / PARALLAX } }

/** The stacked screen leaving on the way back slides away whole, darkening as the one underneath comes. */
private fun leavingExit(dimTo: Float): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
    { slideOutHorizontally(slide) { it } + fadeOut(dim, targetAlpha = dimTo) }

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
    val scope = rememberCoroutineScope()
    // One avatar for every tab's bar: switching tabs no longer remounts it and flashes the fallback icon.
    val avatarModel: com.zankup.android.you.AvatarModel = hiltViewModel()
    val youMe by avatarModel.me.state.collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(Unit) { avatarModel.me.refreshIfStale() }
    val youAvatar = youMe?.data?.avatarUrl
    val notificationsBadge: NotificationsBadgeModel = hiltViewModel()
    val unreadNotifications by notificationsBadge.unread.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { notificationsBadge.refresh() }
    // Each tab's lists outlive the tab's composition: a detail's back keeps its place, a tab switch starts at the top.
    val homeList = rememberLazyListState()
    val feedList = rememberLazyListState()
    val resultsList = rememberLazyListState()
    val progressList = rememberLazyListState()
    val healthList = rememberLazyListState()
    val youList = rememberLazyListState()
    val selectTab: (Tab) -> Unit = { target ->
        when (target) {
            Tab.HOME -> scope.launch { homeList.scrollToItem(0) }
            Tab.ACTIVITIES -> scope.launch {
                feedList.scrollToItem(0)
                resultsList.scrollToItem(0)
            }
            Tab.PROGRESS -> scope.launch { progressList.scrollToItem(0) }
            Tab.HEALTH -> scope.launch { healthList.scrollToItem(0) }
            Tab.YOU -> scope.launch { youList.scrollToItem(0) }
        }
        goTab(nav, target.route)
    }
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val onTab = destination?.isTab() == true
    // Debug only: the frame reports name the screen drawing when one misses (Perf is empty in release).
    LaunchedEffect(destination?.route) {
        Perf.screen = destination?.route?.substringAfterLast('.') ?: "?"
    }
    val coach by produceState(false, api) {
        val status = call { api.service<AiApi>().getAiStatus() }
        reachability.note(status)
        value = (status as? ApiResult.Ok)?.value?.available == true
    }
    val unreachable by reachability.unreachable.collectAsStateWithLifecycle()
    // Figures in the person's units, as soon as their settings are known (metric until then).
    val units by settings.units.collectAsStateWithLifecycle(initialValue = com.zankup.android.domain.Units.METRIC)
    LaunchedEffect(api) { settings.query.refreshIfStale() }
    val formats = remember(units) { com.zankup.android.ui.Formats(units, java.util.Locale.getDefault()) }
    // With the system's animations off (or reduced), screens cut instead of travelling: the transitions
    // are skipped rather than played at once.
    val still = !ValueAnimator.areAnimatorsEnabled()
    // In the dark themes the screen underneath dims as it gives way; in the light ones it only slips away
    // (fading it there would wash it towards the page instead of darkening it).
    val palette = ZankupTheme.colors
    val dimTo = remember(palette.isDark) { if (palette.isDark) DIM_DARK else 1f }
    val exitUnder = remember(dimTo) { underneathExit(dimTo) }
    val exitLeaving = remember(dimTo) { leavingExit(dimTo) }
    // A tab swaps in place when another tab takes its place, as Strava's do; but when a detail is stacked
    // over it the tab stays underneath until the detail has come in, and travels back with it when it pops.
    val tabEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = remember(still) {
        { if (still || initialState.destination.isTab()) StillEnter.invoke(this) else PopEnter.invoke(this) }
    }
    val tabExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
        remember(still, exitUnder) {
            { if (still || targetState.destination.isTab()) StillExit.invoke(this) else exitUnder.invoke(this) }
        }
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
            containerColor = palette.page,
            // Each tab carries the bar itself (it is part of what slides with the tab), so the Scaffold
            // leaves the window to the NavHost and only holds the offline notice of the stacked screens.
            contentWindowInsets = WindowInsets(0),
            bottomBar = { if (unreachable && !onTab) OfflineNotice() }
        ) { padding ->
            NavHost(
                nav,
                startDestination = HomeRoute,
                modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()),
                // As Strava: a screen comes in from the right over the one that was there, which steps
                // aside a quarter and dims; the tab underneath stays put and travels back on the pop.
                enterTransition = if (still) StillEnter else PushEnter,
                exitTransition = if (still) StillExit else exitUnder,
                popEnterTransition = if (still) StillEnter else PopEnter,
                popExitTransition = if (still) StillExit else exitLeaving
            ) {
                val back: () -> Unit = { nav.popBackStack() }
                val go: (Any) -> Unit = { nav.navigate(it) }

                composable<HomeRoute>(
                    enterTransition = tabEnter,
                    exitTransition = tabExit,
                    popEnterTransition = tabEnter,
                    popExitTransition = tabExit
                ) {
                    LaunchedEffect(Unit) { notificationsBadge.refresh() }
                    TabScreen(Tab.HOME, nav, unreachable, selectTab, youAvatar) { fill ->
                        com.zankup.android.home.HomeScreen(
                            onOpen = { id -> go(ActivityRoute(id)) },
                            onCoach = { go(CoachRoute) },
                            onNotifications = { go(NotificationsRoute) },
                            unreadNotifications = unreadNotifications,
                            modifier = fill,
                            listState = homeList
                        )
                    }
                }
                composable<ActivitiesRoute>(
                    enterTransition = tabEnter,
                    exitTransition = tabExit,
                    popEnterTransition = tabEnter,
                    popExitTransition = tabExit
                ) {
                    TabScreen(Tab.ACTIVITIES, nav, unreachable, selectTab, youAvatar) { fill ->
                        com.zankup.android.feed.FeedScreen(
                            onOpen = { id -> go(ActivityRoute(id)) },
                            onBack = back,
                            onCalendar = { go(CalendarRoute) },
                            modifier = fill,
                            feedScroll = feedList,
                            resultsScroll = resultsList
                        )
                    }
                }
                composable<ProgressRoute>(
                    enterTransition = tabEnter,
                    exitTransition = tabExit,
                    popEnterTransition = tabEnter,
                    popExitTransition = tabExit
                ) {
                    TabScreen(Tab.PROGRESS, nav, unreachable, selectTab, youAvatar) { fill ->
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
                            onOpenActivity = { id -> go(ActivityRoute(id)) },
                            modifier = fill,
                            listState = progressList
                        )
                    }
                }
                composable<HealthRoute>(
                    enterTransition = tabEnter,
                    exitTransition = tabExit,
                    popEnterTransition = tabEnter,
                    popExitTransition = tabExit
                ) {
                    TabScreen(Tab.HEALTH, nav, unreachable, selectTab, youAvatar) { fill ->
                        com.zankup.android.health.HealthScreen(
                            onWeight = { go(WeightRoute) },
                            onSettings = { go(SettingsSectionRoute("health")) },
                            onOpenActivity = { id -> go(ActivityRoute(id)) },
                            onConnect = { go(SettingsSectionRoute("health")) },
                            modifier = fill,
                            listState = healthList
                        )
                    }
                }
                composable<YouRoute>(
                    enterTransition = tabEnter,
                    exitTransition = tabExit,
                    popEnterTransition = tabEnter,
                    popExitTransition = tabExit
                ) {
                    TabScreen(Tab.YOU, nav, unreachable, selectTab, youAvatar) { fill ->
                        com.zankup.android.you.YouScreen(
                            onStats = { go(StatsRoute) },
                            onEstimates = { go(EstimatesRoute) },
                            onGear = { go(GearRoute) },
                            onImport = { go(ImportRoute) },
                            onAccount = { go(SettingsSectionRoute("account")) },
                            onSettings = { go(SettingsRoute) },
                            onOpenActivity = { id -> go(ActivityRoute(id)) },
                            modifier = fill,
                            listState = youList
                        )
                    }
                }

                val scheme = BuildConfig.LINK_SCHEME
                composable<ActivityRoute>(
                    deepLinks = listOf(navDeepLink<ActivityRoute>(basePath = "$scheme://activity"))
                ) {
                    val route = it.toRoute<ActivityRoute>()
                    com.zankup.android.activity.ActivityScreen(
                        route.id,
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
                        aiAvailable = coach,
                        startAtTop = route.top
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
                        onOpenActivity = { id -> go(ActivityRoute(id, top = true)) }
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
                            onSync = { go(SyncRoute) },
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

/**
 * A tab of the bar: its screen, then the bar. The bar belongs to the tab (as in Strava), so it is part of
 * what slides away when a detail is stacked over the tab and comes back with it on the pop; the page is
 * painted here too, so a tab arriving over another one is never seen through.
 */
@Composable
private fun TabScreen(
    tab: Tab,
    nav: NavHostController,
    offline: Boolean,
    onSelect: (Tab) -> Unit,
    youAvatar: String?,
    content: @Composable (Modifier) -> Unit
) {
    Column(Modifier.fillMaxSize().background(ZankupTheme.colors.page)) {
        content(Modifier.weight(1f))
        if (offline) OfflineNotice()
        TabBar(nav, tab, onSelect, youAvatar)
    }
}

@Composable
private fun TabBar(nav: NavHostController, current: Tab, onSelect: (Tab) -> Unit, youAvatar: String?) {
    val colors = ZankupTheme.colors
    NavigationBar(containerColor = colors.surface) {
        Tab.entries.forEach { tab ->
            val selected = tab == current
            NavigationBarItem(
                modifier = Modifier.testTag("tab_" + tab.name.lowercase()),
                selected = selected,
                onClick = { onSelect(tab) },
                icon = {
                    if (tab == Tab.YOU) {
                        com.zankup.android.you.YouTabIcon(selected, youAvatar)
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
