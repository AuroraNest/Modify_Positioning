package com.aurora.modifypositioning.ui.movement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aurora.modifypositioning.BuildConfig
import com.aurora.modifypositioning.data.MapPreferencesStore
import com.aurora.modifypositioning.data.PlaceSearchRepository
import com.aurora.modifypositioning.data.RouteRepository
import com.aurora.modifypositioning.data.toPlannedRoute
import com.aurora.modifypositioning.domain.CustomRouteBuilder
import com.aurora.modifypositioning.domain.MockController
import com.aurora.modifypositioning.domain.routing.RoutePlanner
import com.aurora.modifypositioning.model.CustomRouteDraft
import com.aurora.modifypositioning.model.MapProvider
import com.aurora.modifypositioning.model.MapSelection
import com.aurora.modifypositioning.model.MovementMode
import com.aurora.modifypositioning.model.MovementPageTab
import com.aurora.modifypositioning.model.MovementPoint
import com.aurora.modifypositioning.model.NavigationDraft
import com.aurora.modifypositioning.model.PlannedRoute
import com.aurora.modifypositioning.model.PlannedRouteState
import com.aurora.modifypositioning.model.RouteInputMode
import com.aurora.modifypositioning.model.RoutePoint
import com.aurora.modifypositioning.model.SelectionSource
import com.aurora.modifypositioning.model.TargetLocation
import com.aurora.modifypositioning.model.TravelMode
import com.aurora.modifypositioning.model.effectiveAmapAndroidKey
import com.aurora.modifypositioning.model.resolveMapKeyAvailability
import com.aurora.modifypositioning.util.AppSessionMetrics
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class MovementViewModel(
    private val mapPreferencesStore: MapPreferencesStore,
    private val controller: MockController,
    private val routePlanner: RoutePlanner,
    private val routeRepository: RouteRepository,
    private val placeSearchRepository: PlaceSearchRepository?,
    private val customRouteBuilder: CustomRouteBuilder = CustomRouteBuilder(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(MovementUiState())
    val uiState: StateFlow<MovementUiState> = _uiState.asStateFlow()

    private var pointToPointPlanJob: Job? = null
    private var searchJob: Job? = null

    init {
        observeController()
        observeRouteStorage()
        observeMapProviderSettings()
        loadInitialPreferences()
    }

    fun setSelectedTab(tab: MovementPageTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        viewModelScope.launch {
            mapPreferencesStore.setMovementTab(tab)
        }
    }

    fun setMovementMode(mode: MovementMode) {
        viewModelScope.launch {
            mapPreferencesStore.setMovementMode(mode)
            controller.onMovementModeChanged(mode)
            _uiState.update { it.copy(mode = mode) }
        }
    }

    fun setTravelMode(mode: TravelMode) {
        _uiState.update {
            it.copy(
                navigationDraft = it.navigationDraft.copy(travelMode = mode),
                customRouteDraft = it.customRouteDraft.copy(travelMode = mode),
            )
        }
        controller.onTravelModeChanged(mode)
        viewModelScope.launch {
            mapPreferencesStore.setDefaultTravelMode(mode)
        }
        schedulePointToPointPlan()
    }

    fun setSearchTarget(start: Boolean) {
        _uiState.update { it.copy(searchForStart = start) }
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
            _uiState.update { it.copy(searchSuggestions = emptyList(), isSearching = false) }
            return
        }

        val repository = placeSearchRepository
        if (repository == null) {
            _uiState.update {
                it.copy(
                    searchSuggestions = emptyList(),
                    isSearching = false,
                    searchError = "搜索服务未就绪",
                )
            }
            return
        }

        searchJob = viewModelScope.launch {
            delay(350L)
            _uiState.update { it.copy(isSearching = true) }
            runCatching { repository.autocomplete(query) }
                .onSuccess { suggestions ->
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            searchSuggestions = suggestions.take(5),
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

    fun onSearchSuggestionSelected(selection: MapSelection) {
        val target = TargetLocation(
            name = if (selection.source == SelectionSource.SEARCH) "搜索选点" else "地图选点",
            latitude = selection.lat,
            longitude = selection.lng,
        )
        if (_uiState.value.searchForStart) {
            setPointToPointStart(target)
        } else {
            setPointToPointEnd(target)
        }
        _uiState.update {
            it.copy(
                searchQuery = "",
                searchSuggestions = emptyList(),
            )
        }
    }

    fun setPointToPointStart(target: TargetLocation) {
        _uiState.update {
            it.copy(
                navigationDraft = it.navigationDraft.copy(start = target),
                routeError = null,
            )
        }
        controller.updateTarget(target)
        viewModelScope.launch { mapPreferencesStore.saveTarget(target) }
        schedulePointToPointPlan()
    }

    fun setPointToPointEnd(target: TargetLocation) {
        _uiState.update {
            it.copy(
                navigationDraft = it.navigationDraft.copy(end = target),
                routeError = null,
            )
        }
        schedulePointToPointPlan()
    }

    fun useCurrentAsStart() {
        setPointToPointStart(_uiState.value.currentTarget)
    }

    fun useCurrentAsEnd() {
        setPointToPointEnd(_uiState.value.currentTarget)
    }

    fun setRouteInputMode(mode: RouteInputMode) {
        _uiState.update { it.copy(customRouteDraft = it.customRouteDraft.copy(inputMode = mode)) }
    }

    fun setSnapToRoad(enabled: Boolean) {
        _uiState.update {
            it.copy(
                customRouteDraft = it.customRouteDraft.copy(snapToRoad = enabled),
            )
        }
        viewModelScope.launch {
            mapPreferencesStore.setDefaultSnapToRoad(enabled)
        }
    }

    fun addCustomRoutePointFromCurrent() {
        val now = System.currentTimeMillis()
        val current = _uiState.value.currentTarget
        val nextPoint = RoutePoint(
            lat = current.latitude,
            lng = current.longitude,
            ts = now,
        )
        _uiState.update {
            it.copy(
                customRouteDraft = it.customRouteDraft.copy(
                    points = it.customRouteDraft.points + nextPoint,
                ),
                customRouteState = PlannedRouteState.Idle,
                routeError = null,
            )
        }
    }

    fun addCustomRoutePoint(point: MapSelection) {
        val next = RoutePoint(
            lat = point.lat,
            lng = point.lng,
            ts = System.currentTimeMillis(),
        )
        _uiState.update {
            it.copy(
                customRouteDraft = it.customRouteDraft.copy(points = it.customRouteDraft.points + next),
                customRouteState = PlannedRouteState.Idle,
                routeError = null,
            )
        }
    }

    fun undoCustomRoutePoint() {
        _uiState.update {
            val points = it.customRouteDraft.points
            if (points.isEmpty()) {
                it
            } else {
                it.copy(
                    customRouteDraft = it.customRouteDraft.copy(points = points.dropLast(1)),
                    customRouteState = PlannedRouteState.Idle,
                )
            }
        }
    }

    fun clearCustomRoutePoints() {
        _uiState.update {
            it.copy(
                customRouteDraft = it.customRouteDraft.copy(points = emptyList()),
                customRouteState = PlannedRouteState.Idle,
                routeError = null,
            )
        }
    }

    fun confirmCustomRoute() {
        val draft = _uiState.value.customRouteDraft
        if (draft.points.size < 2) {
            _uiState.update { it.copy(customRouteState = PlannedRouteState.Error("手绘路线至少需要两个点")) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(customRouteState = PlannedRouteState.Loading, routeError = null) }
            val built = runCatching {
                if (draft.snapToRoad) {
                    routePlanner.snapHandDrawn(draft.points, draft.travelMode)
                } else {
                    customRouteBuilder.buildManualRoute(draft.points, draft.travelMode)
                }
            }
            built.onSuccess { route ->
                val routeName = "自定义路线-${System.currentTimeMillis() % 100000}"
                routeRepository.saveRoute(
                    name = routeName,
                    route = route,
                    inputMode = draft.inputMode,
                    snapToRoad = draft.snapToRoad,
                )
                _uiState.update { it.copy(customRouteState = PlannedRouteState.Ready(route)) }
                controller.onRoutePlanned(route)
            }.onFailure { error ->
                val message = error.message ?: "路线规划失败"
                _uiState.update {
                    it.copy(
                        customRouteState = PlannedRouteState.Error(message),
                        routeError = message,
                    )
                }
                controller.onRoutePlanningError(message)
            }
        }
    }

    fun planPointToPointNow() {
        schedulePointToPointPlan(immediate = true)
    }

    fun prepareRandomWalkStart() {
        controller.onMovementModeChanged(MovementMode.RANDOM_WALK)
    }

    fun preparePointToPointStart(): String? {
        val state = _uiState.value.pointToPointRouteState
        if (state !is PlannedRouteState.Ready) {
            return "请先完成两点路线规划"
        }
        val start = _uiState.value.navigationDraft.start ?: return "请先设置起点"
        val route = state.route
        controller.onMovementModeChanged(MovementMode.POINT_TO_POINT_NAV)
        controller.onTravelModeChanged(route.mode)
        controller.onRoutePlanned(route)
        controller.updateTarget(start)
        viewModelScope.launch {
            mapPreferencesStore.saveTarget(start)
            routeRepository.saveRoute(
                name = "导航路线-${System.currentTimeMillis() % 100000}",
                route = route,
                inputMode = RouteInputMode.PIN_POINTS,
                snapToRoad = true,
            )
        }
        return null
    }

    fun prepareCustomRouteStart(): String? {
        val state = _uiState.value.customRouteState
        if (state !is PlannedRouteState.Ready) {
            return "请先确认指定路线"
        }
        val start = state.route.points.firstOrNull()?.toTarget("路线起点") ?: return "路线起点无效"
        controller.onMovementModeChanged(MovementMode.CUSTOM_ROUTE)
        controller.onTravelModeChanged(state.route.mode)
        controller.onRoutePlanned(state.route)
        controller.updateTarget(start)
        viewModelScope.launch {
            mapPreferencesStore.saveTarget(start)
            routeRepository.recordRecent(state.route.id, "指定路线")
        }
        return null
    }

    fun applySavedRoute(routeId: String) {
        viewModelScope.launch {
            val loaded = routeRepository.loadRoute(routeId) ?: return@launch
            val route = loaded.toPlannedRoute()
            _uiState.update {
                it.copy(
                    customRouteState = PlannedRouteState.Ready(route),
                    customRouteDraft = it.customRouteDraft.copy(
                        points = route.points,
                        travelMode = route.mode,
                    ),
                    selectedTab = MovementPageTab.CUSTOM_ROUTE,
                )
            }
            controller.onRoutePlanned(route)
            controller.onTravelModeChanged(route.mode)
        }
    }

    fun deleteSavedRoute(routeId: String) {
        viewModelScope.launch {
            routeRepository.deleteRoute(routeId)
        }
    }

    private fun schedulePointToPointPlan(immediate: Boolean = false) {
        val draft = _uiState.value.navigationDraft
        val start = draft.start ?: return
        val end = draft.end ?: run {
            _uiState.update { it.copy(pointToPointRouteState = PlannedRouteState.Idle) }
            return
        }
        pointToPointPlanJob?.cancel()
        pointToPointPlanJob = viewModelScope.launch {
            if (!immediate) {
                delay(400L)
            }
            _uiState.update { it.copy(pointToPointRouteState = PlannedRouteState.Loading) }
            val planned = runCatching {
                AppSessionMetrics.increaseSearchRequests()
                routePlanner.planPointToPoint(start, end, draft.travelMode)
            }
            planned.onSuccess { route ->
                _uiState.update { it.copy(pointToPointRouteState = PlannedRouteState.Ready(route), routeError = null) }
                controller.onRoutePlanned(route)
            }.onFailure { error ->
                val message = error.message ?: "未找到可行路线"
                _uiState.update {
                    it.copy(
                        pointToPointRouteState = PlannedRouteState.Error(message),
                        routeError = message,
                    )
                }
                controller.onRoutePlanningError(message)
            }
        }
    }

    private fun loadInitialPreferences() {
        viewModelScope.launch {
            val mode = mapPreferencesStore.getMovementMode()
            val config = mapPreferencesStore.getRandomWalkConfig()
            val tab = mapPreferencesStore.getMovementTab()
            val defaultTravelMode = mapPreferencesStore.getDefaultTravelMode()
            val defaultSnap = mapPreferencesStore.getDefaultSnapToRoad()
            val current = controller.target.value
            _uiState.update {
                it.copy(
                    mode = mode,
                    config = config,
                    selectedTab = tab,
                    navigationDraft = NavigationDraft(
                        start = current,
                        end = null,
                        travelMode = defaultTravelMode,
                        snapToRoad = true,
                    ),
                    customRouteDraft = CustomRouteDraft(
                        travelMode = defaultTravelMode,
                        snapToRoad = defaultSnap,
                    ),
                )
            }
            controller.onTravelModeChanged(defaultTravelMode)
        }
    }

    private fun observeController() {
        viewModelScope.launch {
            controller.target.collectLatest { target ->
                _uiState.update { state ->
                    val navStart = state.navigationDraft.start ?: target
                    state.copy(
                        currentTarget = target,
                        centerTarget = state.centerTarget.takeIf { it.name.isNotBlank() } ?: target,
                        navigationDraft = state.navigationDraft.copy(start = navStart),
                    )
                }
            }
        }
        viewModelScope.launch {
            controller.movementCenter.collectLatest { center ->
                _uiState.update { it.copy(centerTarget = center) }
            }
        }
        viewModelScope.launch {
            controller.movementMode.collectLatest { mode ->
                _uiState.update { it.copy(mode = mode) }
            }
        }
        viewModelScope.launch {
            controller.movementState.collectLatest { state ->
                _uiState.update { it.copy(movementState = state) }
            }
        }
        viewModelScope.launch {
            controller.movementTrace.collectLatest { trace ->
                _uiState.update { it.copy(tracePoints = trace) }
            }
        }
        viewModelScope.launch {
            controller.movementCurrentSpeedMps.collectLatest { speed ->
                _uiState.update { it.copy(currentSpeedMps = speed) }
            }
        }
        viewModelScope.launch {
            controller.movementDistanceFromCenterMeters.collectLatest { distance ->
                _uiState.update { it.copy(distanceFromCenterMeters = distance) }
            }
        }
        viewModelScope.launch {
            controller.routeProgress.collectLatest { progress ->
                _uiState.update { it.copy(routeProgress = progress) }
            }
        }
        viewModelScope.launch {
            controller.routeError.collectLatest { error ->
                _uiState.update { it.copy(routeError = error) }
            }
        }
        viewModelScope.launch {
            controller.travelMode.collectLatest { travelMode ->
                _uiState.update {
                    it.copy(
                        navigationDraft = it.navigationDraft.copy(travelMode = travelMode),
                        customRouteDraft = it.customRouteDraft.copy(travelMode = travelMode),
                    )
                }
            }
        }
    }

    private fun observeRouteStorage() {
        viewModelScope.launch {
            routeRepository.observeSavedRoutes().collectLatest { routes ->
                _uiState.update { it.copy(savedRoutes = routes) }
            }
        }
        viewModelScope.launch {
            routeRepository.observeRecentRoutes().collectLatest { routes ->
                _uiState.update { it.copy(recentRoutes = routes) }
            }
        }
    }

    private fun observeMapProviderSettings() {
        viewModelScope.launch {
            mapPreferencesStore.mapProviderSettingsFlow.collectLatest { settings ->
                val availability = resolveMapKeyAvailability(
                    runtimeAndroidKey = settings.amapAndroidKey,
                    buildAndroidKey = BuildConfig.AMAP_API_KEY,
                    runtimeWebKey = settings.amapWebKey,
                    buildWebKey = BuildConfig.AMAP_WEB_API_KEY,
                )
                val provider = if (settings.mapProvider == MapProvider.AMAP && availability.hasAmapAndroidKey) {
                    MapProvider.AMAP
                } else {
                    MapProvider.OSM
                }
                _uiState.update {
                    it.copy(
                        mapProvider = provider,
                        effectiveAmapAndroidKey = effectiveAmapAndroidKey(
                            runtimeAndroidKey = settings.amapAndroidKey,
                            buildAndroidKey = BuildConfig.AMAP_API_KEY,
                        ),
                        isAmapAndroidAvailable = availability.hasAmapAndroidKey,
                    )
                }
            }
        }
    }
}

class MovementViewModelFactory(
    private val mapPreferencesStore: MapPreferencesStore,
    private val controller: MockController,
    private val routePlanner: RoutePlanner,
    private val routeRepository: RouteRepository,
    private val placeSearchRepository: PlaceSearchRepository?,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MovementViewModel::class.java)) {
            return MovementViewModel(
                mapPreferencesStore = mapPreferencesStore,
                controller = controller,
                routePlanner = routePlanner,
                routeRepository = routeRepository,
                placeSearchRepository = placeSearchRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.simpleName}")
    }
}
