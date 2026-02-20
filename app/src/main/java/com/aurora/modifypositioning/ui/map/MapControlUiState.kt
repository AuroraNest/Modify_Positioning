package com.aurora.modifypositioning.ui.map

import com.aurora.modifypositioning.model.CoordinateCalibrationMode
import com.aurora.modifypositioning.model.DEFAULT_TARGET
import com.aurora.modifypositioning.model.FavoriteLocation
import com.aurora.modifypositioning.model.MapCameraSnapshot
import com.aurora.modifypositioning.model.PlaceSuggestion
import com.aurora.modifypositioning.model.SelectionSource
import com.aurora.modifypositioning.model.TargetLocation

data class MapControlUiState(
    val selectedTarget: TargetLocation = DEFAULT_TARGET,
    val searchQuery: String = "",
    val suggestions: List<PlaceSuggestion> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val favorites: List<FavoriteLocation> = emptyList(),
    val calibrationMode: CoordinateCalibrationMode = CoordinateCalibrationMode.OFF,
    val manualLat: String = "",
    val manualLng: String = "",
    val favoriteNameInput: String = "",
    val camera: MapCameraSnapshot = MapCameraSnapshot(
        lat = DEFAULT_TARGET.latitude,
        lng = DEFAULT_TARGET.longitude,
        zoom = 16f,
    ),
    val searchRequestCount: Int = 0,
    val lastSelectionSource: SelectionSource = SelectionSource.SEARCH,
)
