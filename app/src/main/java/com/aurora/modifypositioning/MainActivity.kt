package com.aurora.modifypositioning

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurora.modifypositioning.data.FavoriteLocationRepository
import com.aurora.modifypositioning.data.FallbackPlaceSearchRemote
import com.aurora.modifypositioning.data.MapPreferencesStore
import com.aurora.modifypositioning.data.AMapWebPlaceSearchRepository
import com.aurora.modifypositioning.data.NominatimPlaceSearchRepository
import com.aurora.modifypositioning.data.NominatimRemoteClient
import com.aurora.modifypositioning.data.PhotonPlaceSearchRemoteClient
import com.aurora.modifypositioning.data.RouteRepository
import com.aurora.modifypositioning.data.local.LocationDatabase
import com.aurora.modifypositioning.domain.MockControllerStore
import com.aurora.modifypositioning.domain.routing.OsrmRoutePlanner
import com.aurora.modifypositioning.location.FusedLocationDiagnosticsStore
import com.aurora.modifypositioning.model.MapSelection
import com.aurora.modifypositioning.model.MovementMode
import com.aurora.modifypositioning.model.MovementPageTab
import com.aurora.modifypositioning.model.RouteInputMode
import com.aurora.modifypositioning.model.SelectionSource
import com.aurora.modifypositioning.model.TargetLocation
import com.aurora.modifypositioning.model.TravelMode
import com.aurora.modifypositioning.model.effectiveAmapWebKey
import com.aurora.modifypositioning.service.MockLocationService
import com.aurora.modifypositioning.ui.DiagnosticScreen
import com.aurora.modifypositioning.ui.MainControlScreen
import com.aurora.modifypositioning.ui.MapControlScreen
import com.aurora.modifypositioning.ui.MovementControlScreen
import com.aurora.modifypositioning.ui.OnboardingScreen
import com.aurora.modifypositioning.ui.map.MapControlViewModel
import com.aurora.modifypositioning.ui.map.MapControlViewModelFactory
import com.aurora.modifypositioning.ui.movement.MovementViewModel
import com.aurora.modifypositioning.ui.movement.MovementViewModelFactory
import com.aurora.modifypositioning.ui.theme.ModifyPositioningTheme
import com.aurora.modifypositioning.util.LocationDiagnosticsReader
import com.aurora.modifypositioning.util.MockEnvironmentChecker
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val controller = MockControllerStore.instance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val hasCompletedOnboarding = prefs.getBoolean(KEY_ONBOARDING_DONE, false)

        setContent {
            val state by controller.state.collectAsState()
            val statusText by controller.statusText.collectAsState()
            val target by controller.target.collectAsState()
            val lastInjection by controller.lastInjection.collectAsState()
            val movementMode by controller.movementMode.collectAsState()
            val movementState by controller.movementState.collectAsState()
            val movementCurrentSpeedMps by controller.movementCurrentSpeedMps.collectAsState()
            val movementDistanceFromCenterMeters by controller.movementDistanceFromCenterMeters.collectAsState()
            val movementTrace by controller.movementTrace.collectAsState()
            val plannedRoute by controller.plannedRoute.collectAsState()
            val travelMode by controller.travelMode.collectAsState()
            val routeProgress by controller.routeProgress.collectAsState()
            val fusedDiagnostics by FusedLocationDiagnosticsStore.state.collectAsState()

            val mapPreferencesStore = remember { MapPreferencesStore(this@MainActivity) }
            val favoriteRepository = remember {
                FavoriteLocationRepository(LocationDatabase.get(this@MainActivity).locationDao())
            }
            val routeRepository = remember {
                RouteRepository(LocationDatabase.get(this@MainActivity).locationDao())
            }
            val routePlanner = remember { OsrmRoutePlanner() }

            val osmPlaceSearchRepository = remember {
                NominatimPlaceSearchRepository(
                    remote = FallbackPlaceSearchRemote(
                        remotes = listOf(
                            NominatimRemoteClient(),
                            PhotonPlaceSearchRemoteClient(),
                        ),
                    ),
                )
            }
            val amapPlaceSearchRepository = remember {
                AMapWebPlaceSearchRepository(
                    apiKeyProvider = {
                        effectiveAmapWebKey(
                            runtimeWebKey = mapPreferencesStore.getAmapWebKey(),
                            buildWebKey = BuildConfig.AMAP_WEB_API_KEY,
                        )
                    },
                )
            }

            val mapViewModel: MapControlViewModel = viewModel(
                factory = MapControlViewModelFactory(
                    mapPreferencesStore = mapPreferencesStore,
                    favoriteRepository = favoriteRepository,
                    osmPlaceSearchRepository = osmPlaceSearchRepository,
                    amapPlaceSearchRepository = amapPlaceSearchRepository,
                    controller = controller,
                ),
            )
            val mapUiState by mapViewModel.uiState.collectAsState()
            val movementViewModel: MovementViewModel = viewModel(
                factory = MovementViewModelFactory(
                    mapPreferencesStore = mapPreferencesStore,
                    controller = controller,
                    routePlanner = routePlanner,
                    routeRepository = routeRepository,
                    placeSearchRepository = osmPlaceSearchRepository,
                ),
            )
            val movementUiState by movementViewModel.uiState.collectAsState()

            var showOnboarding by rememberSaveable { mutableStateOf(!hasCompletedOnboarding) }
            var isMockAppSelected by remember { mutableStateOf(false) }
            var missingPermissions by remember { mutableStateOf(emptyList<String>()) }
            var screen by rememberSaveable {
                mutableStateOf(UiScreen.MAP)
            }
            var diagnostics by remember {
                mutableStateOf(
                    LocationDiagnosticsReader.read(
                        context = this@MainActivity,
                        state = state,
                        lastInjection = lastInjection,
                        movementMode = movementMode,
                        movementState = movementState,
                        movementCurrentSpeedMps = movementCurrentSpeedMps,
                        movementDistanceFromCenterMeters = movementDistanceFromCenterMeters,
                        movementTrace = movementTrace,
                        plannedRoute = plannedRoute,
                        travelMode = travelMode,
                        routeProgress = routeProgress,
                        mapPreferencesStore = mapPreferencesStore,
                    ),
                )
            }

            fun refreshStatus() {
                isMockAppSelected = MockEnvironmentChecker.isMockLocationAppSelected(this@MainActivity)
                missingPermissions = MockEnvironmentChecker.missingPermissions(this@MainActivity)
            }

            fun refreshDiagnostics() {
                diagnostics = LocationDiagnosticsReader.read(
                    context = this@MainActivity,
                    state = state,
                    lastInjection = lastInjection,
                    movementMode = movementMode,
                    movementState = movementState,
                    movementCurrentSpeedMps = movementCurrentSpeedMps,
                    movementDistanceFromCenterMeters = movementDistanceFromCenterMeters,
                    movementTrace = movementTrace,
                    plannedRoute = plannedRoute,
                    travelMode = travelMode,
                    routeProgress = routeProgress,
                    mapPreferencesStore = mapPreferencesStore,
                )
            }

            fun startMock(mode: MovementMode) {
                refreshStatus()
                if (missingPermissions.isNotEmpty()) {
                    controller.onError("缺少权限，请完成授权")
                    Toast.makeText(this@MainActivity, "启动失败：缺少权限", Toast.LENGTH_SHORT).show()
                    return
                }
                if (!isMockAppSelected) {
                    controller.onError("请先在开发者选项中设置模拟位置信息应用")
                    showOnboarding = true
                    Toast.makeText(
                        this@MainActivity,
                        "启动失败：请先设置模拟位置信息应用",
                        Toast.LENGTH_SHORT,
                    ).show()
                    return
                }
                controller.onMovementModeChanged(mode)
                lifecycleScope.launch {
                    mapPreferencesStore.setMovementMode(mode)
                    ContextCompat.startForegroundService(
                        this@MainActivity,
                        MockLocationService.startIntent(this@MainActivity),
                    )
                    val tip = when (mode) {
                        MovementMode.RANDOM_WALK -> "已开始随机步行模拟"
                        MovementMode.POINT_TO_POINT_NAV -> "已开始两点导航模拟"
                        MovementMode.CUSTOM_ROUTE -> "已开始指定路线模拟"
                        MovementMode.FIXED -> "已开始修改定位"
                    }
                    Toast.makeText(this@MainActivity, tip, Toast.LENGTH_SHORT).show()
                }
            }

            fun pauseMock() {
                startService(MockLocationService.pauseIntent(this@MainActivity))
                Toast.makeText(this@MainActivity, "已暂停定位修改", Toast.LENGTH_SHORT).show()
            }

            fun stopMock() {
                startService(MockLocationService.stopIntent(this@MainActivity))
                Toast.makeText(this@MainActivity, "已停止定位修改", Toast.LENGTH_SHORT).show()
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions(),
            ) {
                refreshStatus()
                refreshDiagnostics()
            }

            LaunchedEffect(Unit) {
                val savedTarget = mapPreferencesStore.getTargetOrNull()
                if (savedTarget != null) {
                    controller.updateTarget(savedTarget)
                }
            }

            LaunchedEffect(showOnboarding) {
                refreshStatus()
                refreshDiagnostics()
            }

            LaunchedEffect(
                state,
                lastInjection,
                mapUiState.searchRequestCount,
                mapUiState.calibrationMode,
                                movementMode,
                                movementState,
                                movementCurrentSpeedMps,
                                movementDistanceFromCenterMeters,
                                movementTrace.size,
                                plannedRoute?.id,
                                routeProgress?.percent,
                                fusedDiagnostics,
                            ) {
                refreshDiagnostics()
            }

            ModifyPositioningTheme {
                if (showOnboarding) {
                    OnboardingScreen(
                        isMockAppSelected = isMockAppSelected,
                        missingPermissions = missingPermissions,
                        onOpenDeveloperOptions = { openDeveloperOptions() },
                        onOpenAppSettings = { openAppDetailSettings() },
                        onRefreshStatus = {
                            refreshStatus()
                            refreshDiagnostics()
                        },
                        onContinue = {
                            refreshStatus()
                            if (missingPermissions.isNotEmpty()) {
                                permissionLauncher.launch(missingPermissions.toTypedArray())
                                controller.onError("请先授予定位权限")
                            } else {
                                showOnboarding = false
                                prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
                            }
                        },
                    )
                } else {
                    when (screen) {
                        UiScreen.MAP -> {
                            MapControlScreen(
                                uiState = mapUiState,
                                onSearchQueryChanged = { mapViewModel.onSearchQueryChanged(it) },
                                onSuggestionSelected = { mapViewModel.onSuggestionSelected(it) },
                                onMapDraggedSelection = { lat, lng -> mapViewModel.onMapDraggedSelection(lat, lng) },
                                onMapProviderChanged = { mapViewModel.setMapProvider(it) },
                                onAdvancedSettingsVisibleChanged = { mapViewModel.setAdvancedSettingsVisible(it) },
                                onAmapAndroidKeyChanged = { mapViewModel.onAmapAndroidKeyChanged(it) },
                                onAmapWebKeyChanged = { mapViewModel.onAmapWebKeyChanged(it) },
                                onUseSearchTarget = { mapViewModel.applyLastSearchTarget() },
                                onUseMapCenterTarget = { mapViewModel.applyMapCenterAsTarget() },
                                onCalibrationModeChanged = { mapViewModel.setCalibrationMode(it) },
                                onAddFavorite = { mapViewModel.addFavoriteFromCurrent() },
                                onFavoriteNameInputChanged = { mapViewModel.onFavoriteNameInputChanged(it) },
                                onSelectFavorite = { mapViewModel.selectFavorite(it) },
                                onDeleteFavorite = { mapViewModel.deleteFavorite(it) },
                                onRenameFavorite = { item, name -> mapViewModel.renameFavorite(item, name) },
                                onStart = { startMock(MovementMode.FIXED) },
                                onPause = { pauseMock() },
                                onStop = { stopMock() },
                                onOpenMovement = { screen = UiScreen.MOVEMENT },
                                onOpenGuide = { showOnboarding = true },
                                onOpenDiagnostic = {
                                    refreshDiagnostics()
                                    screen = UiScreen.DIAGNOSTIC
                                },
                                onCameraIdle = { lat, lng, zoom -> mapViewModel.onCameraIdle(lat, lng, zoom) },
                            )
                        }

                        UiScreen.LEGACY_CONTROL -> {
                            MainControlScreen(
                                state = state,
                                statusText = statusText,
                                target = target,
                                onStart = { startMock(MovementMode.FIXED) },
                                onPause = { pauseMock() },
                                onStop = { stopMock() },
                                onOpenGuide = {
                                    showOnboarding = true
                                },
                                onOpenDiagnostic = {
                                    refreshDiagnostics()
                                    screen = UiScreen.DIAGNOSTIC
                                },
                            )
                        }

                        UiScreen.MOVEMENT -> {
                            MovementControlScreen(
                                uiState = movementUiState,
                                onSelectTab = { movementViewModel.setSelectedTab(it) },
                                onSetSearchTarget = { movementViewModel.setSearchTarget(it) },
                                onSearchQueryChanged = { movementViewModel.onSearchQueryChanged(it) },
                                onSearchSuggestionSelected = { suggestion ->
                                    movementViewModel.onSearchSuggestionSelected(
                                        MapSelection(
                                            lat = suggestion.lat,
                                            lng = suggestion.lng,
                                            source = SelectionSource.SEARCH,
                                        ),
                                    )
                                },
                                onSetTravelMode = { movementViewModel.setTravelMode(it) },
                                onSetPointToPointStartFromCenter = { lat, lng ->
                                    movementViewModel.setPointToPointStart(
                                        TargetLocation("地图起点", lat, lng),
                                    )
                                },
                                onSetPointToPointEndFromCenter = { lat, lng ->
                                    movementViewModel.setPointToPointEnd(
                                        TargetLocation("地图终点", lat, lng),
                                    )
                                },
                                onPlanPointToPointNow = { movementViewModel.planPointToPointNow() },
                                onSetRouteInputMode = { movementViewModel.setRouteInputMode(it) },
                                onSetSnapToRoad = { movementViewModel.setSnapToRoad(it) },
                                onAddCustomPointFromCenter = { lat, lng ->
                                    movementViewModel.addCustomRoutePoint(
                                        MapSelection(
                                            lat = lat,
                                            lng = lng,
                                            source = SelectionSource.MAP_DRAG,
                                        ),
                                    )
                                },
                                onUndoCustomPoint = { movementViewModel.undoCustomRoutePoint() },
                                onClearCustomPoints = { movementViewModel.clearCustomRoutePoints() },
                                onConfirmCustomRoute = { movementViewModel.confirmCustomRoute() },
                                onApplySavedRoute = { movementViewModel.applySavedRoute(it) },
                                onDeleteSavedRoute = { movementViewModel.deleteSavedRoute(it) },
                                onStartRandomWalk = {
                                    movementViewModel.prepareRandomWalkStart()
                                    startMock(MovementMode.RANDOM_WALK)
                                },
                                onStartPointToPoint = {
                                    val error = movementViewModel.preparePointToPointStart()
                                    if (error != null) {
                                        Toast.makeText(this@MainActivity, error, Toast.LENGTH_SHORT).show()
                                    } else {
                                        startMock(MovementMode.POINT_TO_POINT_NAV)
                                    }
                                },
                                onStartCustomRoute = {
                                    val error = movementViewModel.prepareCustomRouteStart()
                                    if (error != null) {
                                        Toast.makeText(this@MainActivity, error, Toast.LENGTH_SHORT).show()
                                    } else {
                                        startMock(MovementMode.CUSTOM_ROUTE)
                                    }
                                },
                                onPause = { pauseMock() },
                                onStop = { stopMock() },
                                onBack = { screen = UiScreen.MAP },
                            )
                        }

                        UiScreen.DIAGNOSTIC -> {
                            DiagnosticScreen(
                                snapshot = diagnostics,
                                onRefresh = { refreshDiagnostics() },
                                onBack = {
                                    screen = UiScreen.MAP
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun openDeveloperOptions() {
        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        runCatching { startActivity(intent) }
            .onFailure {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
    }

    private fun openAppDetailSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    companion object {
        private const val PREFS_NAME = "app_prefs"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
    }
}

private enum class UiScreen {
    MAP,
    LEGACY_CONTROL,
    MOVEMENT,
    DIAGNOSTIC,
}
