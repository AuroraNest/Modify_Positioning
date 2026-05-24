package com.aurora.modifypositioning.model

data class DiagnosticLocation(
    val provider: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timeMillis: Long,
    val isMock: Boolean,
    val distanceToLastInjectionMeters: Double? = null,
)

data class DiagnosticSnapshot(
    val generatedAtMillis: Long,
    val isMockAppSelected: Boolean,
    val missingPermissions: List<String>,
    val gpsEnabled: Boolean,
    val networkEnabled: Boolean,
    val gpsLastKnown: DiagnosticLocation?,
    val networkLastKnown: DiagnosticLocation?,
    val appState: MockState,
    val lastInjection: InjectionReport?,
    val movementMode: MovementMode,
    val movementState: MovementState,
    val movementCurrentSpeedMps: Double,
    val movementDistanceFromCenterMeters: Double,
    val movementLastPointTimeMillis: Long?,
    val routeMode: MovementMode?,
    val travelMode: TravelMode?,
    val routeSource: RouteSource?,
    val remainingDistanceMeters: Double?,
    val remainingDurationSeconds: Double?,
    val routeProgressPercent: Double?,
    val calibrationMode: CoordinateCalibrationMode,
    val searchRequestCount: Int,
)
