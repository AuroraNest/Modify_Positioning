package com.aurora.modifypositioning.model

data class PlaceSuggestion(
    val id: String,
    val title: String,
    val subtitle: String,
    val lat: Double,
    val lng: Double,
)

data class MapSelection(
    val lat: Double,
    val lng: Double,
    val source: SelectionSource,
)

enum class SelectionSource {
    MAP_DRAG,
    SEARCH,
    FAVORITE,
    MANUAL_INPUT,
}

enum class CoordinateCalibrationMode {
    OFF,
    MAINLAND_CHINA_COMPAT,
}
