package com.aurora.modifypositioning.model

enum class FixedPointReadinessState {
    IDLE,
    STARTING,
    BURSTING,
    SETTLING,
    READY,
    DEGRADED,
    BLOCKED,
    STOPPED,
}

enum class FixedPointCheckSeverity {
    OK,
    WARNING,
    BLOCKER,
}

data class FixedPointCheck(
    val id: String,
    val title: String,
    val message: String,
    val severity: FixedPointCheckSeverity,
    val passed: Boolean,
)

data class FixedPointChannelReadiness(
    val provider: String,
    val available: Boolean,
    val fresh: Boolean,
    val nearTarget: Boolean,
    val stable: Boolean,
    val isMock: Boolean?,
    val distanceToTargetMeters: Double?,
    val ageMillis: Long?,
    val accuracyMeters: Float?,
    val message: String,
)

data class FixedPointReadinessSnapshot(
    val state: FixedPointReadinessState,
    val score: Int,
    val generatedAtElapsedRealtimeMillis: Long,
    val rawTarget: TargetLocation,
    val injectedTarget: TargetLocation,
    val calibrationOffsetMeters: Double,
    val stableSinceElapsedRealtimeMillis: Long?,
    val recommendedWaitMillis: Long,
    val gps: FixedPointChannelReadiness,
    val network: FixedPointChannelReadiness,
    val fused: FixedPointChannelReadiness,
    val checks: List<FixedPointCheck>,
    val summary: String,
)
