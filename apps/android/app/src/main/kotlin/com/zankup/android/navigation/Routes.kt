package com.zankup.android.navigation

import kotlinx.serialization.Serializable

// Every screen of the app, as typed routes (OS-1112), in the order of docs/android-screen-map.md.

// The five tabs.
@Serializable data object HomeRoute

@Serializable data object ActivitiesRoute

@Serializable data object ProgressRoute

@Serializable data object HealthRoute

@Serializable data object YouRoute

// Activities and the activity.
@Serializable data class ActivityRoute(val id: Long)

@Serializable data class AnalysisRoute(val id: Long)

@Serializable data class MatchesRoute(val id: Long)

@Serializable data object CalendarRoute

@Serializable data object SyncRoute

// Progress.
@Serializable data object ProgressDetailRoute

@Serializable data object TrainingLogRoute

@Serializable data object FitnessRoute

@Serializable data object EffortRoute

@Serializable data object PredictionsRoute

@Serializable data object ObjectivesRoute

@Serializable data class ObjectiveEditRoute(val id: Long? = null)

@Serializable data object ZonesRoute

@Serializable data object SkillsRoute

@Serializable data class BestsRoute(val key: String = "10k")

@Serializable data object MonthlyRoute

@Serializable data object SourcesRoute

// Stats and reviews.
@Serializable data object StatsRoute

@Serializable data object EstimatesRoute

@Serializable data object YearsRoute

@Serializable data object HeatmapRoute

@Serializable data class ReviewRoute(val period: String)

@Serializable data object RoutesRoute

@Serializable data class RouteRoute(val id: Long)

@Serializable data object SegmentsRoute

@Serializable data class SegmentRoute(val id: Long)

// Health.
@Serializable data object WeightRoute

// You, settings and the rest.
@Serializable data object SettingsRoute

@Serializable data class SettingsSectionRoute(val section: String)

@Serializable data object AppIconRoute

@Serializable data object CoachRoute

@Serializable data object AssistantsRoute

@Serializable data object GearRoute

@Serializable data object ImportRoute

@Serializable data object NotificationsRoute
