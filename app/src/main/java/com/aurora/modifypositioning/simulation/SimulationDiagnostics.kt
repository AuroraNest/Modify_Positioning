package com.aurora.modifypositioning.simulation

data class SimulationDiagnostics(
    val lastSample: LocationSample?,
    val generatedAtMillis: Long,
)
