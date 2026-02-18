package com.aurora.modifypositioning.model

data class DiagnosticLocation(
    val provider: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timeMillis: Long,
    val isMock: Boolean,
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
)
