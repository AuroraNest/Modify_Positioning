package com.aurora.modifypositioning.model

data class InjectionReport(
    val provider: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timeMillis: Long,
    val providerRebuildTimeMillis: Long? = null,
    val verificationMockStatus: String? = null,
    val verificationDistanceMeters: Double? = null,
    val recoveryStatus: String? = null,
)
