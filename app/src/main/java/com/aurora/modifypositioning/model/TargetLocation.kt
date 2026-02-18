package com.aurora.modifypositioning.model

data class TargetLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
) {
    fun isValid(): Boolean {
        return latitude in -90.0..90.0 && longitude in -180.0..180.0
    }
}

val DEFAULT_TARGET = TargetLocation(
    name = "Times Square, New York",
    latitude = 40.7580,
    longitude = -73.9855,
)

const val UPDATE_INTERVAL_MS = 1_000L
const val ENHANCED_UPDATE_INTERVAL_MS = 700L
const val ENHANCED_JITTER_RADIUS_METERS = 8.0
