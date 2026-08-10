package com.aurora.modifypositioning.model

data class MapCameraSnapshot(
    val lat: Double,
    val lng: Double,
    val zoom: Float,
)

data class MapProviderSettings(
    val mapProvider: MapProvider = MapProvider.OSM,
    val amapAndroidKey: String = "",
    val amapPrivacyAccepted: Boolean = false,
) {
    val canUseAmap: Boolean
        get() = amapAndroidKey.isNotBlank() && amapPrivacyAccepted
}

internal fun resolveMapProvider(raw: String?): MapProvider {
    return runCatching {
        if (raw.isNullOrBlank()) {
            MapProvider.OSM
        } else {
            MapProvider.valueOf(raw)
        }
    }.getOrDefault(MapProvider.OSM)
}

data class FavoriteLocation(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val createdAt: Long,
    val updatedAt: Long,
)

data class RecentSelection(
    val id: String,
    val label: String,
    val lat: Double,
    val lng: Double,
    val selectedAt: Long,
)
