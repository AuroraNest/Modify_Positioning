package com.aurora.modifypositioning.ui.movement

import com.aurora.modifypositioning.model.CustomRouteDraft
import com.aurora.modifypositioning.model.DEFAULT_TARGET
import com.aurora.modifypositioning.model.MovementMode
import com.aurora.modifypositioning.model.MovementPageTab
import com.aurora.modifypositioning.model.MovementPoint
import com.aurora.modifypositioning.model.MovementState
import com.aurora.modifypositioning.model.NavigationDraft
import com.aurora.modifypositioning.model.PlaceSuggestion
import com.aurora.modifypositioning.model.PlannedRouteState
import com.aurora.modifypositioning.model.RandomWalkConfig
import com.aurora.modifypositioning.model.RecentRouteSelection
import com.aurora.modifypositioning.model.RouteProgress
import com.aurora.modifypositioning.model.SavedRouteSummary
import com.aurora.modifypositioning.model.TargetLocation

data class MovementUiState(
    val selectedTab: MovementPageTab = MovementPageTab.RANDOM_WALK,
    val mode: MovementMode = MovementMode.FIXED,
    val movementState: MovementState = MovementState.Idle,
    val centerTarget: TargetLocation = DEFAULT_TARGET,
    val currentTarget: TargetLocation = DEFAULT_TARGET,
    val tracePoints: List<MovementPoint> = emptyList(),
    val currentSpeedMps: Double = 0.0,
    val distanceFromCenterMeters: Double = 0.0,
    val config: RandomWalkConfig = RandomWalkConfig(),
    val navigationDraft: NavigationDraft = NavigationDraft(start = DEFAULT_TARGET),
    val pointToPointRouteState: PlannedRouteState = PlannedRouteState.Idle,
    val customRouteDraft: CustomRouteDraft = CustomRouteDraft(),
    val customRouteState: PlannedRouteState = PlannedRouteState.Idle,
    val routeProgress: RouteProgress? = null,
    val routeError: String? = null,
    val savedRoutes: List<SavedRouteSummary> = emptyList(),
    val recentRoutes: List<RecentRouteSelection> = emptyList(),
    val searchQuery: String = "",
    val searchSuggestions: List<PlaceSuggestion> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val searchForStart: Boolean = false,
)

