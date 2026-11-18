package com.zankup.android.shell

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.R
import com.zankup.android.api.endpoints.ActivitiesApi
import com.zankup.android.api.model.ActivityPage
import com.zankup.android.data.CacheKeys
import com.zankup.android.data.Store
import com.zankup.android.designsystem.components.ListRow
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.navigation.ActivityRoute
import com.zankup.android.navigation.BestsRoute
import com.zankup.android.navigation.CalendarRoute
import com.zankup.android.navigation.CoachRoute
import com.zankup.android.navigation.EffortRoute
import com.zankup.android.navigation.EstimatesRoute
import com.zankup.android.navigation.FitnessRoute
import com.zankup.android.navigation.GearRoute
import com.zankup.android.navigation.ImportRoute
import com.zankup.android.navigation.MonthlyRoute
import com.zankup.android.navigation.NotificationsRoute
import com.zankup.android.navigation.ObjectivesRoute
import com.zankup.android.navigation.PredictionsRoute
import com.zankup.android.navigation.ProgressDetailRoute
import com.zankup.android.navigation.SettingsRoute
import com.zankup.android.navigation.SkillsRoute
import com.zankup.android.navigation.SourcesRoute
import com.zankup.android.navigation.StatsRoute
import com.zankup.android.navigation.SyncRoute
import com.zankup.android.navigation.TrainingLogRoute
import com.zankup.android.navigation.WeightRoute
import com.zankup.android.navigation.ZonesRoute
import com.zankup.android.network.call
import com.zankup.android.session.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

// The five tabs as the navigation needs them: their headers as the React Native app has them, and links
// to the screens stacked over them. Each tab's content arrives with its block.

/** Home: the wordmark; the Coach (when the AI is on) and Notifications on the right. */
@Composable
internal fun HomeTab(coach: Boolean, go: (Any) -> Unit) {
    Placeholder(
        title = stringResource(R.string.nav_home),
        ticket = "OS-1130",
        onBack = null,
        actions = {
            if (coach) {
                IconButton(onClick = { go(CoachRoute) }) {
                    ZankupIcon(ZIcon.Coach, contentDescription = stringResource(R.string.nav_coach))
                }
            }
            IconButton(onClick = { go(NotificationsRoute) }) {
                ZankupIcon(ZIcon.Notifications, contentDescription = stringResource(R.string.nav_notifications))
            }
        }
    )
}

/** The feed's first page as it is kept, until the feed itself (OS-1131): opens each activity. */
@HiltViewModel
class LatestModel
@Inject
constructor(store: Store) : ViewModel() {
    val latest = store.query(CacheKeys.FEED_FIRST, ActivityPage.serializer()) { api ->
        call { api.service<ActivitiesApi>().listActivities() }
    }
}

/** Activities: Sync and Calendar on the right; the search bar comes with the feed (OS-1131). */
@Composable
internal fun ActivitiesTab(go: (Any) -> Unit, model: LatestModel = hiltViewModel()) {
    val page by model.latest.state.collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(Unit) { model.latest.refresh() }
    Placeholder(
        title = stringResource(R.string.nav_activities),
        ticket = "OS-1131",
        onBack = null,
        actions = {
            IconButton(onClick = { go(SyncRoute) }) {
                ZankupIcon(ZIcon.Sync, contentDescription = stringResource(R.string.nav_sync))
            }
            IconButton(onClick = { go(CalendarRoute) }) {
                ZankupIcon(ZIcon.Calendar, contentDescription = stringResource(R.string.nav_calendar))
            }
        }
    ) {
        page?.data?.items?.take(10)?.forEach { item ->
            ListRow(
                title = item.name ?: item.sport,
                subtitle = item.startUtc.take(10),
                onClick = { go(ActivityRoute(item.id.toLong())) }
            )
        }
    }
}

@Composable
internal fun ProgressTab(go: (Any) -> Unit) {
    Placeholder(stringResource(R.string.nav_progress), "OS-1140", onBack = null) {
        listOf(
            R.string.nav_progress_detail to ProgressDetailRoute,
            R.string.nav_training_log to TrainingLogRoute,
            R.string.nav_fitness to FitnessRoute,
            R.string.nav_effort to EffortRoute,
            R.string.nav_predictions to PredictionsRoute,
            R.string.nav_objectives to ObjectivesRoute,
            R.string.nav_zones to ZonesRoute,
            R.string.nav_skills to SkillsRoute,
            R.string.nav_bests to BestsRoute(),
            R.string.nav_monthly to MonthlyRoute,
            R.string.nav_sources to SourcesRoute
        ).forEach { (title, route) -> ListRow(stringResource(title), onClick = { go(route) }) }
    }
}

@Composable
internal fun HealthTab(go: (Any) -> Unit) {
    Placeholder(stringResource(R.string.nav_health), "OS-1150", onBack = null) {
        ListRow(stringResource(R.string.nav_weight), onClick = { go(WeightRoute) })
    }
}

/** You: the hub to Stats, Estimates, Gear, Import and Settings, and signing out. */
@Composable
internal fun YouTab(go: (Any) -> Unit, session: Session) {
    val scope = rememberCoroutineScope()
    Placeholder(stringResource(R.string.nav_you), "OS-1160", onBack = null) {
        listOf(
            Triple(R.string.nav_stats, ZIcon.Progress, StatsRoute),
            Triple(R.string.nav_estimates, ZIcon.Run, EstimatesRoute),
            Triple(R.string.nav_gear, ZIcon.Ride, GearRoute),
            Triple(R.string.nav_import, ZIcon.Add, ImportRoute),
            Triple(R.string.nav_settings, ZIcon.Settings, SettingsRoute)
        ).forEach { (title, icon, route) -> ListRow(stringResource(title), icon = icon, onClick = { go(route) }) }
        ListRow(
            stringResource(R.string.sign_out),
            icon = ZIcon.SignOut,
            onClick = { scope.launch { session.signOut() } },
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
