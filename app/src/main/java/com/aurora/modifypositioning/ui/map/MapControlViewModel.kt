package com.aurora.modifypositioning.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aurora.modifypositioning.data.FavoriteLocationRepository
import com.aurora.modifypositioning.data.MapPreferencesStore
import com.aurora.modifypositioning.data.PlaceSearchRepository
import com.aurora.modifypositioning.domain.MockController
import com.aurora.modifypositioning.model.CoordinateCalibrationMode
import com.aurora.modifypositioning.model.FavoriteLocation
import com.aurora.modifypositioning.model.MapCameraSnapshot
import com.aurora.modifypositioning.model.MapProvider
import com.aurora.modifypositioning.model.PlaceSuggestion
import com.aurora.modifypositioning.model.SelectionSource
import com.aurora.modifypositioning.model.TargetLocation
import com.aurora.modifypositioning.util.AppSessionMetrics
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MapControlViewModel(
    private val mapPreferencesStore: MapPreferencesStore,
    private val favoriteRepository: FavoriteLocationRepository,
    private val osmPlaceSearchRepository: PlaceSearchRepository?,
    private val amapPlaceSearchRepository: PlaceSearchRepository?,
    private val controller: MockController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapControlUiState())
    val uiState: StateFlow<MapControlUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        observeFavorites()
        observeCalibrationMode()
        restoreInitialState()
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                searchError = null,
            )
        }

        searchJob?.cancel()

        if (query.trim().length < 2) {
            _uiState.update {
                it.copy(
                    suggestions = emptyList(),
                    isSearching = false,
                )
            }
            return
        }

        val searchRepository = when (_uiState.value.mapProvider) {
            MapProvider.AMAP -> amapPlaceSearchRepository
            MapProvider.OSM -> osmPlaceSearchRepository
        }

        if (searchRepository == null) {
            _uiState.update {
                it.copy(
                    suggestions = emptyList(),
                    isSearching = false,
                    searchError = "当前地图源搜索未就绪",
                )
            }
            return
        }

        searchJob = viewModelScope.launch {
            delay(600)
            _uiState.update { it.copy(isSearching = true) }

            runCatching { searchRepository.autocomplete(query) }
                .onSuccess { suggestions ->
                    _uiState.update {
                        it.copy(
                            suggestions = suggestions.take(5),
                            isSearching = false,
                            searchRequestCount = AppSessionMetrics.searchRequests,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            searchError = error.message ?: "搜索失败",
                        )
                    }
                }
        }
    }

    fun onSuggestionSelected(suggestion: PlaceSuggestion) {
        val target = TargetLocation(
            name = suggestion.title.ifBlank { suggestion.subtitle.ifBlank { "搜索结果" } },
            latitude = suggestion.lat,
            longitude = suggestion.lng,
        )
        applyTarget(target, SelectionSource.SEARCH)
        _uiState.update {
            it.copy(
                suggestions = emptyList(),
                searchQuery = suggestion.title,
                lastSearchTarget = target,
            )
        }
    }

    fun onMapDraggedSelection(lat: Double, lng: Double) {
        val current = _uiState.value.mapCenterCandidate
        if (
            kotlin.math.abs(current.latitude - lat) < 0.000001 &&
            kotlin.math.abs(current.longitude - lng) < 0.000001
        ) {
            return
        }
        _uiState.update {
            it.copy(
                mapCenterCandidate = TargetLocation(
                    name = "地图中心点",
                    latitude = lat,
                    longitude = lng,
                ),
            )
        }
    }

    fun applyMapCenterAsTarget() {
        val candidate = _uiState.value.mapCenterCandidate
        val target = candidate.copy(
            name = "地图拖动",
        )
        applyTarget(target, SelectionSource.MAP_DRAG)
    }

    fun applyLastSearchTarget() {
        val searchTarget = _uiState.value.lastSearchTarget ?: run {
            _uiState.update { it.copy(searchError = "暂无搜索结果点可应用") }
            return
        }
        applyTarget(searchTarget, SelectionSource.SEARCH)
    }

    fun setMapProvider(provider: MapProvider) {
        _uiState.update {
            it.copy(
                mapProvider = provider,
                suggestions = emptyList(),
                isSearching = false,
                searchError = null,
            )
        }
    }

    fun autoSwitchProviderForCurrentCamera() {
        val camera = _uiState.value.camera
        val provider = if (isLikelyInChina(camera.lat, camera.lng)) MapProvider.AMAP else MapProvider.OSM
        _uiState.update { it.copy(mapProvider = provider) }
    }

    private fun isLikelyInChina(lat: Double, lng: Double): Boolean {
        return lat in 3.0..54.0 && lng in 73.0..136.0
    }

    fun onManualLatChanged(value: String) {
        _uiState.update { it.copy(manualLat = value) }
    }

    fun onManualLngChanged(value: String) {
        _uiState.update { it.copy(manualLng = value) }
    }

    fun applyManualCoordinate() {
        val lat = _uiState.value.manualLat.toDoubleOrNull()
        val lng = _uiState.value.manualLng.toDoubleOrNull()

        if (lat == null || lng == null) {
            _uiState.update { it.copy(searchError = "请输入合法经纬度") }
            return
        }

        val target = TargetLocation(
            name = "手动坐标",
            latitude = lat,
            longitude = lng,
        )

        if (!target.isValid()) {
            _uiState.update { it.copy(searchError = "经纬度超出范围") }
            return
        }

        applyTarget(target, SelectionSource.MANUAL_INPUT)
    }

    fun onFavoriteNameInputChanged(value: String) {
        _uiState.update { it.copy(favoriteNameInput = value) }
    }

    fun addFavoriteFromCurrent() {
        val current = _uiState.value.selectedTarget
        val name = _uiState.value.favoriteNameInput.trim().ifEmpty { current.name }
        viewModelScope.launch {
            favoriteRepository.addFavorite(name, current.latitude, current.longitude)
            _uiState.update { it.copy(favoriteNameInput = "") }
        }
    }

    fun renameFavorite(item: FavoriteLocation, newName: String) {
        val normalized = newName.trim()
        if (normalized.isEmpty()) {
            return
        }
        viewModelScope.launch {
            favoriteRepository.renameFavorite(
                id = item.id,
                name = normalized,
                lat = item.lat,
                lng = item.lng,
                createdAt = item.createdAt,
            )
        }
    }

    fun deleteFavorite(item: FavoriteLocation) {
        viewModelScope.launch {
            favoriteRepository.deleteFavorite(item)
        }
    }

    fun selectFavorite(item: FavoriteLocation) {
        applyTarget(
            TargetLocation(
                name = item.name,
                latitude = item.lat,
                longitude = item.lng,
            ),
            SelectionSource.FAVORITE,
        )
    }

    fun setCalibrationMode(mode: CoordinateCalibrationMode) {
        _uiState.update { it.copy(calibrationMode = mode) }
        viewModelScope.launch {
            mapPreferencesStore.setCalibrationMode(mode)
        }
    }

    fun onCameraIdle(lat: Double, lng: Double, zoom: Float) {
        val snapshot = MapCameraSnapshot(lat = lat, lng = lng, zoom = zoom)
        _uiState.update { it.copy(camera = snapshot) }
        viewModelScope.launch {
            mapPreferencesStore.saveCamera(snapshot)
        }
    }

    private fun restoreInitialState() {
        viewModelScope.launch {
            val target = mapPreferencesStore.getTargetOrNull()
            val camera = mapPreferencesStore.getCameraOrNull()

            if (target != null) {
                controller.updateTarget(target)
            }

            _uiState.update {
                it.copy(
                    selectedTarget = target ?: it.selectedTarget,
                    mapCenterCandidate = target ?: it.mapCenterCandidate,
                    lastSearchTarget = target ?: it.lastSearchTarget,
                    camera = camera ?: it.camera,
                    mapProvider = if (
                        isLikelyInChina(
                            (target ?: it.selectedTarget).latitude,
                            (target ?: it.selectedTarget).longitude,
                        )
                    ) {
                        MapProvider.AMAP
                    } else {
                        MapProvider.OSM
                    },
                    searchRequestCount = AppSessionMetrics.searchRequests,
                )
            }
        }
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            favoriteRepository.observeFavorites().collect { favorites ->
                _uiState.update { it.copy(favorites = favorites) }
            }
        }
    }

    private fun observeCalibrationMode() {
        viewModelScope.launch {
            mapPreferencesStore.calibrationModeFlow.collect { mode ->
                _uiState.update { it.copy(calibrationMode = mode) }
            }
        }
    }

    private fun applyTarget(target: TargetLocation, source: SelectionSource) {
        _uiState.update {
            it.copy(
                selectedTarget = target,
                camera = it.camera.copy(lat = target.latitude, lng = target.longitude),
                mapCenterCandidate = target,
                searchError = null,
                lastSelectionSource = source,
                lastSearchTarget = if (source == SelectionSource.SEARCH) target else it.lastSearchTarget,
            )
        }
        controller.updateTarget(target)

        viewModelScope.launch {
            mapPreferencesStore.saveTarget(target)
            favoriteRepository.recordRecent(
                label = target.name.ifBlank { source.name },
                lat = target.latitude,
                lng = target.longitude,
            )
        }
    }
}

class MapControlViewModelFactory(
    private val mapPreferencesStore: MapPreferencesStore,
    private val favoriteRepository: FavoriteLocationRepository,
    private val osmPlaceSearchRepository: PlaceSearchRepository?,
    private val amapPlaceSearchRepository: PlaceSearchRepository?,
    private val controller: MockController,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MapControlViewModel::class.java)) {
            return MapControlViewModel(
                mapPreferencesStore = mapPreferencesStore,
                favoriteRepository = favoriteRepository,
                osmPlaceSearchRepository = osmPlaceSearchRepository,
                amapPlaceSearchRepository = amapPlaceSearchRepository,
                controller = controller,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.simpleName}")
    }
}
