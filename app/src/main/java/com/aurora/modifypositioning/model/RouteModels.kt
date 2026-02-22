package com.aurora.modifypositioning.model

import java.util.UUID

enum class TravelMode {
    WALK,
    BIKE,
    CAR,
}

enum class RouteInputMode {
    HAND_DRAW,
    PIN_POINTS,
}

enum class RouteSource {
    OSRM,
    MANUAL,
}

data class RoutePoint(
    val lat: Double,
    val lng: Double,
    val ts: Long,
) {
    fun toTarget(name: String): TargetLocation {
        return TargetLocation(
            name = name,
            latitude = lat,
            longitude = lng,
        )
    }
}

data class PlannedRoute(
    val id: String = UUID.randomUUID().toString(),
    val points: List<RoutePoint>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val source: RouteSource,
    val mode: TravelMode,
)

data class RouteProgress(
    val traveledMeters: Double,
    val remainingMeters: Double,
    val remainingSeconds: Double,
    val percent: Double,
)

data class SpeedProfile(
    val minSpeedMps: Double,
    val maxSpeedMps: Double,
    val avgSpeedMps: Double,
    val minPauseIntervalSeconds: Int,
    val maxPauseIntervalSeconds: Int,
    val minPauseDurationSeconds: Int,
    val maxPauseDurationSeconds: Int,
) {
    companion object {
        fun from(mode: TravelMode): SpeedProfile {
            return when (mode) {
                TravelMode.WALK -> SpeedProfile(
                    minSpeedMps = 1.0,
                    maxSpeedMps = 1.8,
                    avgSpeedMps = 1.35,
                    minPauseIntervalSeconds = 45,
                    maxPauseIntervalSeconds = 120,
                    minPauseDurationSeconds = 2,
                    maxPauseDurationSeconds = 6,
                )

                TravelMode.BIKE -> SpeedProfile(
                    minSpeedMps = 3.0,
                    maxSpeedMps = 6.5,
                    avgSpeedMps = 4.5,
                    minPauseIntervalSeconds = 80,
                    maxPauseIntervalSeconds = 200,
                    minPauseDurationSeconds = 1,
                    maxPauseDurationSeconds = 3,
                )

                TravelMode.CAR -> SpeedProfile(
                    minSpeedMps = 8.0,
                    maxSpeedMps = 18.0,
                    avgSpeedMps = 12.0,
                    minPauseIntervalSeconds = 90,
                    maxPauseIntervalSeconds = 240,
                    minPauseDurationSeconds = 1,
                    maxPauseDurationSeconds = 4,
                )
            }
        }
    }
}

sealed class PlannedRouteState {
    data object Idle : PlannedRouteState()
    data object Loading : PlannedRouteState()
    data class Ready(val route: PlannedRoute) : PlannedRouteState()
    data class Error(val message: String) : PlannedRouteState()
}

data class SavedRouteSummary(
    val id: String,
    val name: String,
    val travelMode: TravelMode,
    val inputMode: RouteInputMode,
    val snapToRoad: Boolean,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val updatedAt: Long,
)

data class SavedRouteDetail(
    val summary: SavedRouteSummary,
    val points: List<RoutePoint>,
)

data class RecentRouteSelection(
    val routeId: String,
    val routeName: String,
    val selectedAt: Long,
)

