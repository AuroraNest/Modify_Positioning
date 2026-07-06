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
const val ENHANCED_UPDATE_INTERVAL_MS = 400L
const val ENHANCED_STARTUP_BURST_INTERVAL_MS = 120L
const val ENHANCED_STARTUP_BURST_COUNT = 30
const val ENHANCED_JITTER_RADIUS_METERS = 6.0
const val THIRD_PARTY_COMPATIBILITY_WARMUP_MS = 60_000L
const val THIRD_PARTY_COMPATIBILITY_INTERVAL_MS = 300L
const val THIRD_PARTY_RECOVERY_BURST_COUNT = 12
