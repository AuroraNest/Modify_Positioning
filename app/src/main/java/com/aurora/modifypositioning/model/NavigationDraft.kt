package com.aurora.modifypositioning.model

data class NavigationDraft(
    val start: TargetLocation? = null,
    val end: TargetLocation? = null,
    val travelMode: TravelMode = TravelMode.WALK,
    val snapToRoad: Boolean = true,
)

data class CustomRouteDraft(
    val inputMode: RouteInputMode = RouteInputMode.HAND_DRAW,
    val points: List<RoutePoint> = emptyList(),
    val travelMode: TravelMode = TravelMode.WALK,
    val snapToRoad: Boolean = true,
)

enum class MovementPageTab {
    RANDOM_WALK,
    POINT_TO_POINT,
    CUSTOM_ROUTE,
}
