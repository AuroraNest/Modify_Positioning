package com.aurora.modifypositioning

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurora.modifypositioning.data.FavoriteLocationRepository
import com.aurora.modifypositioning.data.MapPreferencesStore
import com.aurora.modifypositioning.data.NominatimPlaceSearchRepository
import com.aurora.modifypositioning.data.local.LocationDatabase
import com.aurora.modifypositioning.domain.MockControllerStore
import com.aurora.modifypositioning.service.MockLocationService
import com.aurora.modifypositioning.ui.DiagnosticScreen
import com.aurora.modifypositioning.ui.MainControlScreen
import com.aurora.modifypositioning.ui.MapControlScreen
import com.aurora.modifypositioning.ui.OnboardingScreen
import com.aurora.modifypositioning.ui.map.MapControlViewModel
import com.aurora.modifypositioning.ui.map.MapControlViewModelFactory
import com.aurora.modifypositioning.ui.theme.ModifyPositioningTheme
import com.aurora.modifypositioning.util.LocationDiagnosticsReader
import com.aurora.modifypositioning.util.MockEnvironmentChecker

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

            val mapPreferencesStore = remember { MapPreferencesStore(this@MainActivity) }
            val favoriteRepository = remember {
                FavoriteLocationRepository(LocationDatabase.get(this@MainActivity).locationDao())
            }

            val placeSearchRepository = remember {
                NominatimPlaceSearchRepository()
            }

            val mapViewModel: MapControlViewModel = viewModel(
                factory = MapControlViewModelFactory(
                    mapPreferencesStore = mapPreferencesStore,
                    favoriteRepository = favoriteRepository,
                    placeSearchRepository = placeSearchRepository,
                    controller = controller,
                ),
            )
            val mapUiState by mapViewModel.uiState.collectAsState()

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
                    mapPreferencesStore = mapPreferencesStore,
                )
            }

            fun startMock() {
                refreshStatus()
                if (missingPermissions.isNotEmpty()) {
                    controller.onError("缺少权限，请完成授权")
                    return
                }
                if (!isMockAppSelected) {
                    controller.onError("请先在开发者选项中设置模拟位置信息应用")
                    showOnboarding = true
                    return
                }
                ContextCompat.startForegroundService(
                    this@MainActivity,
                    MockLocationService.startIntent(this@MainActivity),
                )
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

            LaunchedEffect(state, lastInjection, mapUiState.searchRequestCount, mapUiState.calibrationMode) {
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
                                onManualLatChanged = { mapViewModel.onManualLatChanged(it) },
                                onManualLngChanged = { mapViewModel.onManualLngChanged(it) },
                                onApplyManualCoordinate = { mapViewModel.applyManualCoordinate() },
                                onCalibrationModeChanged = { mapViewModel.setCalibrationMode(it) },
                                onAddFavorite = { mapViewModel.addFavoriteFromCurrent() },
                                onFavoriteNameInputChanged = { mapViewModel.onFavoriteNameInputChanged(it) },
                                onSelectFavorite = { mapViewModel.selectFavorite(it) },
                                onDeleteFavorite = { mapViewModel.deleteFavorite(it) },
                                onRenameFavorite = { item, name -> mapViewModel.renameFavorite(item, name) },
                                onStart = { startMock() },
                                onPause = { startService(MockLocationService.pauseIntent(this@MainActivity)) },
                                onStop = { startService(MockLocationService.stopIntent(this@MainActivity)) },
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
                                onStart = { startMock() },
                                onPause = {
                                    startService(MockLocationService.pauseIntent(this@MainActivity))
                                },
                                onStop = {
                                    startService(MockLocationService.stopIntent(this@MainActivity))
                                },
                                onOpenGuide = {
                                    showOnboarding = true
                                },
                                onOpenDiagnostic = {
                                    refreshDiagnostics()
                                    screen = UiScreen.DIAGNOSTIC
                                },
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
    DIAGNOSTIC,
}
