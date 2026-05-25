package com.aurora.modifypositioning.model

data class MapCameraSnapshot(
    val lat: Double,
    val lng: Double,
    val zoom: Float,
)

data class MapProviderSettings(
    val mapProvider: MapProvider = MapProvider.OSM,
    val amapAndroidKey: String = "",
    val amapWebKey: String = "",
)

data class MapKeyAvailability(
    val hasAmapAndroidKey: Boolean,
    val hasAmapWebKey: Boolean,
)

internal fun resolveMapProvider(raw: String?): MapProvider {
    return runCatching {
        if (raw.isNullOrBlank()) {
            MapProvider.OSM
        } else {
            MapProvider.valueOf(raw)
        }
    }.getOrDefault(MapProvider.OSM)
}

internal fun resolveMapKeyAvailability(
    runtimeAndroidKey: String,
    buildAndroidKey: String,
    runtimeWebKey: String,
    buildWebKey: String,
): MapKeyAvailability {
    return MapKeyAvailability(
        hasAmapAndroidKey = runtimeAndroidKey.isNotBlank() || buildAndroidKey.isNotBlank(),
        hasAmapWebKey = runtimeWebKey.isNotBlank() || buildWebKey.isNotBlank(),
    )
}

internal fun effectiveAmapAndroidKey(runtimeAndroidKey: String, buildAndroidKey: String): String {
    return runtimeAndroidKey.trim().ifBlank { buildAndroidKey.trim() }
}

internal fun effectiveAmapWebKey(runtimeWebKey: String, buildWebKey: String): String {
    return runtimeWebKey.trim().ifBlank { buildWebKey.trim() }
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
