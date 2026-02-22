package com.aurora.modifypositioning.model

enum class MovementMode {
    FIXED,
    RANDOM_WALK,
    POINT_TO_POINT_NAV,
    CUSTOM_ROUTE,
}

data class RandomWalkConfig(
    val radiusMeters: Double = 300.0,
    val minSpeedMps: Double = 0.8,
    val maxSpeedMps: Double = 1.6,
    val stepIntervalMs: Long = 800L,
) {
    fun normalized(): RandomWalkConfig {
        val fixedRadius = radiusMeters.coerceIn(30.0, 5_000.0)
        val fixedMinSpeed = minSpeedMps.coerceIn(0.2, 5.0)
        val fixedMaxSpeed = maxSpeedMps.coerceIn(fixedMinSpeed, 6.0)
        val fixedStepInterval = stepIntervalMs.coerceIn(200L, 5_000L)
        return copy(
            radiusMeters = fixedRadius,
            minSpeedMps = fixedMinSpeed,
            maxSpeedMps = fixedMaxSpeed,
            stepIntervalMs = fixedStepInterval,
        )
    }
}

data class MovementPoint(
    val lat: Double,
    val lng: Double,
    val timestampMs: Long,
)

enum class MovementBoundaryPolicy {
    STOP_AT_EDGE,
}

sealed class MovementState {
    data object Idle : MovementState()
    data object Walking : MovementState()
    data object ReachedBoundary : MovementState()
    data object ReachedDestination : MovementState()
    data object Paused : MovementState()
    data class Error(val message: String) : MovementState()
}
