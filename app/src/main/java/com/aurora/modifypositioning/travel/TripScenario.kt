package com.aurora.modifypositioning.travel

import com.aurora.modifypositioning.simulation.EnvironmentProfile
import com.aurora.modifypositioning.simulation.TravelMotionType

data class TripScenario(
    val id: String,
    val title: String,
    val cityName: String,
    val timezoneId: String,
    val stops: List<TripStop>,
    val segments: List<TripSegment>,
)

data class TripStop(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val environment: EnvironmentProfile,
    val stayMinutesMin: Int,
    val stayMinutesMax: Int,
)

data class TripSegment(
    val fromStopId: String,
    val toStopId: String,
    val motionType: TravelMotionType,
    val durationMinutesMin: Int,
    val durationMinutesMax: Int,
)
