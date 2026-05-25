package com.aurora.modifypositioning.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FusedLocationDiagnostics(
    val available: Boolean = false,
    val mockModeEnabled: Boolean = false,
    val mockModePending: Boolean = false,
    val lastInjectionPending: Boolean = false,
    val lastInjectionTimeMillis: Long? = null,
    val lastSuccessfulLatitude: Double? = null,
    val lastSuccessfulLongitude: Double? = null,
    val lastError: String? = null,
)

object FusedLocationDiagnosticsStore {
    private val _state = MutableStateFlow(FusedLocationDiagnostics())
    val state: StateFlow<FusedLocationDiagnostics> = _state.asStateFlow()

    fun update(value: FusedLocationDiagnostics) {
        _state.value = value
    }
}
