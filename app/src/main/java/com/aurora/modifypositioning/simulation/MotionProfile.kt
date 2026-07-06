package com.aurora.modifypositioning.simulation

enum class TravelMotionType {
    STATIONARY,
    WALKING,
    DRIVING,
    CYCLING,
    TRANSIT,
}

data class MotionProfile(
    val type: TravelMotionType,
    val minSpeedMps: Double,
    val maxSpeedMps: Double,
    val accelerationMps2: Double,
    val pauseProbability: Double,
    val environment: EnvironmentProfile,
) {
    companion object {
        fun defaultFor(type: TravelMotionType): MotionProfile {
            return when (type) {
                TravelMotionType.STATIONARY -> MotionProfile(type, 0.0, 0.4, 0.2, 0.7, EnvironmentProfile.OUTDOOR_OPEN)
                TravelMotionType.WALKING -> MotionProfile(type, 0.8, 1.7, 0.5, 0.12, EnvironmentProfile.OUTDOOR_OPEN)
                TravelMotionType.CYCLING -> MotionProfile(type, 3.0, 7.0, 1.0, 0.06, EnvironmentProfile.OUTDOOR_OPEN)
                TravelMotionType.DRIVING -> MotionProfile(type, 0.0, 22.0, 2.0, 0.18, EnvironmentProfile.MOVING_VEHICLE)
                TravelMotionType.TRANSIT -> MotionProfile(type, 0.0, 18.0, 1.2, 0.2, EnvironmentProfile.TRANSIT_STATION)
            }
        }
    }
}
