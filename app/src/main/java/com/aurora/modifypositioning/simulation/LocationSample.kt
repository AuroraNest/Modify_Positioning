package com.aurora.modifypositioning.simulation

import com.aurora.modifypositioning.model.MovementMode

data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val accuracyMeters: Float,
    val verticalAccuracyMeters: Float?,
    val speedMps: Float?,
    val speedAccuracyMps: Float?,
    val bearingDegrees: Float?,
    val bearingAccuracyDegrees: Float?,
    val timestampMillis: Long,
    val elapsedRealtimeNanos: Long,
    val movementMode: MovementMode,
    val environment: EnvironmentProfile,
    val sourceLabel: String,
)

enum class EnvironmentProfile {
    OUTDOOR_OPEN,
    URBAN_CANYON,
    INDOOR_MALL,
    AIRPORT,
    HOTEL,
    RESTAURANT,
    TRANSIT_STATION,
    MOVING_VEHICLE,
}

enum class SampleQuality {
    EXCELLENT,
    GOOD,
    DEGRADED,
    INDOOR_WEAK,
}

enum class SimulationMode {
    ARRIVED_FIXED_POINT,
    RANDOM_WALK,
    ROUTE_SIMULATION,
    TRAVEL_SCENARIO,
}
