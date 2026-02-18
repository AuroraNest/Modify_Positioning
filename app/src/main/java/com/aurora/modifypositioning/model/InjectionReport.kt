package com.aurora.modifypositioning.model

data class InjectionReport(
    val provider: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timeMillis: Long,
)
