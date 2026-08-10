package com.aurora.modifypositioning.ui

import android.os.Bundle
import android.preference.PreferenceManager
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.MapView as AMapView
import com.amap.api.maps.model.LatLng
import com.aurora.modifypositioning.model.CoordinateCalibrationMode
import com.aurora.modifypositioning.model.DiagnosticLocation
import com.aurora.modifypositioning.model.DiagnosticSnapshot
import com.aurora.modifypositioning.model.FavoriteLocation
import com.aurora.modifypositioning.model.InjectionReport
import com.aurora.modifypositioning.model.MapProvider
import com.aurora.modifypositioning.model.MockState
import com.aurora.modifypositioning.model.PlaceSuggestion
import com.aurora.modifypositioning.model.SelectionSource
import com.aurora.modifypositioning.ui.components.FavoriteSheet
import com.aurora.modifypositioning.ui.components.PlaceSearchBar
import com.aurora.modifypositioning.ui.map.MapControlUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView as OsmMapView

@Composable
fun MapControlScreen(
    uiState: MapControlUiState,
    appState: MockState,
    statusText: String,
    lastInjection: InjectionReport?,
    diagnostics: DiagnosticSnapshot,
    onSearchQueryChanged: (String) -> Unit,
    onSuggestionSelected: (PlaceSuggestion) -> Unit,
    onMapDraggedSelection: (Double, Double) -> Unit,
    onMapProviderChanged: (MapProvider) -> Unit,
    onAdvancedSettingsVisibleChanged: (Boolean) -> Unit,
    onAmapAndroidKeyChanged: (String) -> Unit,
    onAmapWebKeyChanged: (String) -> Unit,
    onUseSearchTarget: () -> Unit,
    onUseMapCenterTarget: () -> Unit,
    onCalibrationModeChanged: (CoordinateCalibrationMode) -> Unit,
    onAddFavorite: () -> Unit,
    onFavoriteNameInputChanged: (String) -> Unit,
    onSelectFavorite: (FavoriteLocation) -> Unit,
    onDeleteFavorite: (FavoriteLocation) -> Unit,
    onRenameFavorite: (FavoriteLocation, String) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onOpenMovement: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenDiagnostic: () -> Unit,
    onCameraIdle: (Double, Double, Float) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var aMapViewRef by remember { mutableStateOf<AMapView?>(null) }
    var aMapRef by remember { mutableStateOf<AMap?>(null) }
    var osmMapViewRef by remember { mutableStateOf<OsmMapView?>(null) }
    var idleJob by remember { mutableStateOf<Job?>(null) }
    var skipNextCameraEvent by remember { mutableStateOf(false) }
    var mapInteracting by remember { mutableStateOf(false) }

    fun publishCenter(lat: Double, lng: Double, zoom: Float) {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(250)
            onCameraIdle(lat, lng, zoom)
            onMapDraggedSelection(lat, lng)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    aMapViewRef?.onResume()
                    osmMapViewRef?.onResume()
                }

                Lifecycle.Event.ON_PAUSE -> {
                    aMapViewRef?.onPause()
                    osmMapViewRef?.onPause()
                }

                Lifecycle.Event.ON_DESTROY -> {
                    osmMapViewRef?.onDetach()
                }

                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            idleJob?.cancel()
            aMapViewRef = null
            aMapRef = null
            osmMapViewRef = null
        }
    }

    LaunchedEffect(
        uiState.selectedTarget.latitude,
        uiState.selectedTarget.longitude,
        uiState.camera.zoom,
        uiState.lastSelectionSource,
        uiState.mapProvider,
    ) {
        if (uiState.lastSelectionSource == SelectionSource.MAP_DRAG) {
            return@LaunchedEffect
        }

        if (uiState.mapProvider == MapProvider.AMAP) {
            val aMap = aMapRef ?: return@LaunchedEffect
            val desired = LatLng(uiState.selectedTarget.latitude, uiState.selectedTarget.longitude)
            val camera = aMap.cameraPosition ?: return@LaunchedEffect
            val current = camera.target
            val shouldMove =
                abs(current.latitude - desired.latitude) > 0.000001 ||
                    abs(current.longitude - desired.longitude) > 0.000001 ||
                    abs(camera.zoom - uiState.camera.zoom) > 0.05f

            if (shouldMove) {
                skipNextCameraEvent = true
                aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(desired, uiState.camera.zoom))
            }
            return@LaunchedEffect
        }

        val osmMapView = osmMapViewRef ?: return@LaunchedEffect
        val desired = GeoPoint(uiState.selectedTarget.latitude, uiState.selectedTarget.longitude)
        val current = osmMapView.mapCenter
        val shouldMove =
            abs(current.latitude - desired.latitude) > 0.000001 ||
                abs(current.longitude - desired.longitude) > 0.000001
        if (shouldMove) {
            osmMapView.controller.animateTo(desired)
        }
        if (abs(osmMapView.zoomLevelDouble - uiState.camera.zoom.toDouble()) > 0.05) {
            osmMapView.controller.setZoom(uiState.camera.zoom.toDouble())
        }
    }

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFFF2F2F7), Color(0xFFF7F7FA)),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFAFFFFFF),
                    tonalElevation = 0.dp,
                    shadowElevation = 8.dp,
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                ) {
                    CommandDock(
                        appState = appState,
                        onStart = onStart,
                        onPause = onPause,
                        onStop = onStop,
                        onOpenGuide = onOpenGuide,
                        onOpenMovement = onOpenMovement,
                        onOpenDiagnostic = onOpenDiagnostic,
                    )
                }
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .verticalScroll(
                        state = rememberScrollState(),
                        enabled = !mapInteracting,
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppHeader(
                    appState = appState,
                    statusText = statusText,
                    mapProvider = uiState.mapProvider,
                    lastInjection = lastInjection,
                )

                LocationComparisonCard(
                    uiState = uiState,
                    lastInjection = lastInjection,
                    systemLocation = listOfNotNull(
                        diagnostics.gpsLastKnown,
                        diagnostics.networkLastKnown,
                    ).maxByOrNull(DiagnosticLocation::timeMillis),
                )

                PlaceSearchBar(
                    query = uiState.searchQuery,
                    suggestions = uiState.suggestions,
                    isSearching = uiState.isSearching,
                    searchError = uiState.searchError,
                    onQueryChanged = onSearchQueryChanged,
                    onSelectSuggestion = onSuggestionSelected,
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(390.dp),
                    shape = RoundedCornerShape(8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (uiState.mapProvider == MapProvider.AMAP) {
                            AMapPanel(
                                uiState = uiState,
                                onMapReady = { mapView, aMap ->
                                    aMapViewRef = mapView
                                    aMapRef = aMap
                                },
                                onMapTouchStateChanged = { interacting ->
                                    mapInteracting = interacting
                                },
                                onCameraChanged = { lat, lng, zoom ->
                                    if (skipNextCameraEvent) {
                                        skipNextCameraEvent = false
                                    } else {
                                        publishCenter(lat, lng, zoom)
                                    }
                                },
                            )
                        } else {
                            OSMPanel(
                                uiState = uiState,
                                onMapReady = { mapView ->
                                    osmMapViewRef = mapView
                                },
                                onMapTouchStateChanged = { interacting ->
                                    mapInteracting = interacting
                                },
                                onCameraChanged = { lat, lng, zoom ->
                                    publishCenter(lat, lng, zoom)
                                },
                            )
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xEFFFFFFF))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = "${if (uiState.mapProvider == MapProvider.AMAP) "高德" else "OSM"} / 中心候选",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                        Text(
                            text = "+",
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color(0xFFFF3B30),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                TargetControlPanel(
                    uiState = uiState,
                    onUseSearchTarget = onUseSearchTarget,
                    onUseMapCenterTarget = onUseMapCenterTarget,
                    onCalibrationModeChanged = onCalibrationModeChanged,
                    onMapProviderChanged = onMapProviderChanged,
                    onAdvancedSettingsVisibleChanged = onAdvancedSettingsVisibleChanged,
                )

                if (uiState.showAdvancedSettings) {
                    AMapAdvancedSettingsCard(
                        androidKey = uiState.amapAndroidKey,
                        webKey = uiState.amapWebKey,
                        hasBuildAndroidKey = uiState.isAmapAndroidAvailable && uiState.amapAndroidKey.isBlank(),
                        hasBuildWebKey = uiState.isAmapWebSearchAvailable && uiState.amapWebKey.isBlank(),
                        onAndroidKeyChanged = onAmapAndroidKeyChanged,
                        onWebKeyChanged = onAmapWebKeyChanged,
                    )
                }

                FavoriteSheet(
                    favorites = uiState.favorites,
                    favoriteNameInput = uiState.favoriteNameInput,
                    onFavoriteNameInputChanged = onFavoriteNameInputChanged,
                    onAddFavorite = onAddFavorite,
                    onSelectFavorite = onSelectFavorite,
                    onDeleteFavorite = onDeleteFavorite,
                    onRenameFavorite = onRenameFavorite,
                )
            }
        }
    }
}

@Composable
private fun LocationComparisonCard(
    uiState: MapControlUiState,
    lastInjection: InjectionReport?,
    systemLocation: DiagnosticLocation?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("定位三态对照", style = MaterialTheme.typography.titleSmall)
            ComparisonRow(
                title = "已选择目标",
                coordinate = formatComparisonCoordinate(
                    uiState.selectedTarget.latitude,
                    uiState.selectedTarget.longitude,
                ),
                detail = uiState.selectedTarget.name,
                testTag = "comparison_selected_target",
            )
            HorizontalDivider()
            ComparisonRow(
                title = "最近注入",
                coordinate = lastInjection?.let {
                    formatComparisonCoordinate(it.latitude, it.longitude)
                } ?: "暂无",
                detail = lastInjection?.let {
                    "${formatComparisonTime(it.timeMillis)} / 精度 ${"%.1f".format(it.accuracyMeters)}m / " +
                        "验证距离 ${formatComparisonDistance(it.verificationDistanceMeters)}"
                } ?: "尚无注入记录",
                testTag = "comparison_last_injection",
            )
            HorizontalDivider()
            ComparisonRow(
                title = "系统最近返回",
                coordinate = systemLocation?.let {
                    formatComparisonCoordinate(it.latitude, it.longitude)
                } ?: "暂无",
                detail = systemLocation?.let {
                    "${it.provider} / ${formatComparisonTime(it.timeMillis)} / " +
                        "mock=${it.isMock} / 距注入 ${formatComparisonDistance(it.distanceToLastInjectionMeters)}"
                } ?: "GPS / Network 暂无可读位置",
                testTag = "comparison_system_last_returned",
            )
        }
    }
}

@Composable
private fun ComparisonRow(
    title: String,
    coordinate: String,
    detail: String,
    testTag: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(coordinate, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatComparisonCoordinate(latitude: Double, longitude: Double): String {
    return "%.6f, %.6f".format(latitude, longitude)
}

private fun formatComparisonTime(timeMillis: Long): String {
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timeMillis))
}

private fun formatComparisonDistance(distanceMeters: Double?): String {
    return distanceMeters?.let { "${"%.1f".format(it)}m" } ?: "-"
}

@Composable
private fun AppHeader(
    appState: MockState,
    statusText: String,
    mapProvider: MapProvider,
    lastInjection: InjectionReport?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "定位",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(appState)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricBlock(
                    label = "地图",
                    value = if (mapProvider == MapProvider.AMAP) "高德" else "OSM",
                    modifier = Modifier.weight(1f),
                )
                MetricBlock(
                    label = "最近注入",
                    value = lastInjection?.let { "${"%.1f".format(it.accuracyMeters)}m" } ?: "--",
                    modifier = Modifier.weight(1f),
                )
                MetricBlock(
                    label = "兼容窗口",
                    value = "60s",
                    modifier = Modifier.weight(1f),
                )
            }

            if (appState == MockState.Running || appState == MockState.Paused) {
                LinearProgressIndicator(
                    progress = { if (appState == MockState.Running) 1f else 0.42f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusPill(appState: MockState) {
    val (label, color) = when (appState) {
        MockState.Idle -> "待启动" to Color(0xFF8E8E93)
        MockState.Running -> "运行中" to Color(0xFF34C759)
        MockState.Paused -> "已暂停" to Color(0xFFFF9F0A)
        is MockState.Error -> "异常" to Color(0xFFFF3B30)
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.18f),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = color,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun MetricBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            Text(
                value,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun TargetControlPanel(
    uiState: MapControlUiState,
    onUseSearchTarget: () -> Unit,
    onUseMapCenterTarget: () -> Unit,
    onCalibrationModeChanged: (CoordinateCalibrationMode) -> Unit,
    onMapProviderChanged: (MapProvider) -> Unit,
    onAdvancedSettingsVisibleChanged: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text("当前目标", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = uiState.selectedTarget.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${formatCoord(uiState.selectedTarget.latitude)}, ${formatCoord(uiState.selectedTarget.longitude)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFE5F2FF),
                ) {
                    Text(
                        text = if (uiState.calibrationMode == CoordinateCalibrationMode.MAINLAND_CHINA_COMPAT) "大陆兼容" else "原始坐标",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onUseMapCenterTarget,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("使用地图中心")
                }
                FilledTonalButton(
                    onClick = onUseSearchTarget,
                    enabled = uiState.lastSearchTarget != null,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("使用搜索结果")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProviderButton(
                    label = "高德",
                    selected = uiState.mapProvider == MapProvider.AMAP,
                    onClick = { onMapProviderChanged(MapProvider.AMAP) },
                    modifier = Modifier.weight(1f),
                )
                ProviderButton(
                    label = "OSM",
                    selected = uiState.mapProvider == MapProvider.OSM,
                    onClick = { onMapProviderChanged(MapProvider.OSM) },
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(
                    onClick = { onAdvancedSettingsVisibleChanged(!uiState.showAdvancedSettings) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(if (uiState.showAdvancedSettings) "收起" else "高级")
                }
            }

            Text(
                text = "中心候选 ${formatCoord(uiState.mapCenterCandidate.latitude)}, ${formatCoord(uiState.mapCenterCandidate.longitude)} | 搜索 ${uiState.searchRequestCount} 次",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            CalibrationSelector(
                mode = uiState.calibrationMode,
                onModeChanged = onCalibrationModeChanged,
            )

            if (!uiState.isAmapAndroidAvailable || (uiState.mapProvider == MapProvider.AMAP && !uiState.isAmapWebSearchAvailable)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFFF8E8),
                ) {
                    Text(
                        text = if (!uiState.isAmapAndroidAvailable) {
                            "高德底图需要 Android Key, 可先使用 OSM"
                        } else {
                            "高德搜索需要 Web Key, 底图不受影响"
                        },
                        modifier = Modifier.padding(10.dp),
                        color = Color(0xFF7A4A00),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(8.dp)) {
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(8.dp)) {
            Text(label)
        }
    }
}

@Composable
private fun CommandDock(
    appState: MockState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenMovement: () -> Unit,
    onOpenDiagnostic: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onStart,
                modifier = Modifier
                    .weight(1.4f)
                    .testTag("map_start"),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    when (appState) {
                        MockState.Idle -> "准备固定点定位"
                        MockState.Running -> "重新稳定目标点"
                        MockState.Paused -> "重新稳定目标点"
                        is MockState.Error -> "重试固定点定位"
                    },
                )
            }
            FilledTonalButton(
                onClick = onPause,
                enabled = appState == MockState.Running,
                modifier = Modifier
                    .weight(1f)
                    .testTag("map_pause"),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Text("暂停")
            }
            OutlinedButton(
                onClick = onStop,
                enabled = appState != MockState.Idle,
                modifier = Modifier
                    .weight(0.85f)
                    .testTag("map_stop"),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("停止")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = onOpenMovement,
                modifier = Modifier
                    .weight(1f)
                    .testTag("nav_movement"),
            ) {
                Text("路线")
            }
            TextButton(
                onClick = onOpenDiagnostic,
                modifier = Modifier
                    .weight(1f)
                    .testTag("nav_diagnostic"),
            ) {
                Text("诊断")
            }
            TextButton(
                onClick = onOpenGuide,
                modifier = Modifier
                    .weight(1f)
                    .testTag("nav_onboarding"),
            ) {
                Text("设置")
            }
        }
    }
}

@Composable
private fun AMapPanel(
    uiState: MapControlUiState,
    onMapReady: (AMapView, AMap) -> Unit,
    onMapTouchStateChanged: (Boolean) -> Unit,
    onCameraChanged: (Double, Double, Float) -> Unit,
) {
    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .border(
                width = 1.dp,
                color = Color(0xFFCFDCE9),
                shape = RoundedCornerShape(8.dp),
            )
            .clip(RoundedCornerShape(8.dp)),
        factory = { context ->
            if (uiState.effectiveAmapAndroidKey.isNotBlank()) {
                MapsInitializer.setApiKey(uiState.effectiveAmapAndroidKey)
            }
            AMapView(context).apply {
                onCreate(Bundle())
                val aMap = map
                aMap.uiSettings.isZoomControlsEnabled = false
                aMap.uiSettings.isRotateGesturesEnabled = false
                aMap.uiSettings.isTiltGesturesEnabled = false
                aMap.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(uiState.camera.lat, uiState.camera.lng),
                        uiState.camera.zoom,
                    ),
                )
                aMap.setOnCameraChangeListener(
                    object : AMap.OnCameraChangeListener {
                        override fun onCameraChange(cameraPosition: com.amap.api.maps.model.CameraPosition?) {
                            Unit
                        }

                        override fun onCameraChangeFinish(cameraPosition: com.amap.api.maps.model.CameraPosition?) {
                            val position = cameraPosition ?: return
                            onCameraChanged(
                                position.target.latitude,
                                position.target.longitude,
                                position.zoom,
                            )
                        }
                    },
                )
                aMap.setOnMapTouchListener { event ->
                    when (event?.actionMasked) {
                        MotionEvent.ACTION_DOWN,
                        MotionEvent.ACTION_MOVE,
                        -> onMapTouchStateChanged(true)

                        MotionEvent.ACTION_UP,
                        MotionEvent.ACTION_CANCEL,
                        -> onMapTouchStateChanged(false)
                    }
                }
                onMapReady(this, aMap)
            }
        },
        update = { mapView ->
            onMapReady(mapView, mapView.map)
        },
        onRelease = { mapView ->
            // 某些机型销毁时会触发高德 native 崩溃, 先仅暂停以保证切页稳定.
            onMapTouchStateChanged(false)
            mapView.onPause()
        },
    )
}

@Composable
private fun OSMPanel(
    uiState: MapControlUiState,
    onMapReady: (OsmMapView) -> Unit,
    onMapTouchStateChanged: (Boolean) -> Unit,
    onCameraChanged: (Double, Double, Float) -> Unit,
) {
    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .border(
                width = 1.dp,
                color = Color(0xFFCFDCE9),
                shape = RoundedCornerShape(8.dp),
            )
            .clip(RoundedCornerShape(8.dp)),
        factory = { context ->
            Configuration.getInstance().load(
                context,
                PreferenceManager.getDefaultSharedPreferences(context),
            )
            Configuration.getInstance().userAgentValue = context.packageName
            OsmMapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                isTilesScaledToDpi = true
                setBuiltInZoomControls(false)
                controller.setZoom(uiState.camera.zoom.toDouble())
                controller.setCenter(GeoPoint(uiState.camera.lat, uiState.camera.lng))
                addMapListener(
                    object : MapListener {
                        override fun onScroll(event: ScrollEvent): Boolean {
                            if (!this@apply.isAnimating) {
                                val center = mapCenter
                                onCameraChanged(
                                    center.latitude,
                                    center.longitude,
                                    zoomLevelDouble.toFloat(),
                                )
                            }
                            return true
                        }

                        override fun onZoom(event: ZoomEvent): Boolean {
                            if (!this@apply.isAnimating) {
                                val center = mapCenter
                                onCameraChanged(
                                    center.latitude,
                                    center.longitude,
                                    zoomLevelDouble.toFloat(),
                                )
                            }
                            return true
                        }
                    },
                )
                setOnTouchListener { _, event ->
                    when (event?.actionMasked) {
                        MotionEvent.ACTION_DOWN,
                        MotionEvent.ACTION_MOVE,
                        -> onMapTouchStateChanged(true)

                        MotionEvent.ACTION_UP,
                        MotionEvent.ACTION_CANCEL,
                        -> onMapTouchStateChanged(false)
                    }
                    false
                }
            }.also { mapView ->
                onMapReady(mapView)
            }
        },
        update = { mapView ->
            onMapReady(mapView)
        },
        onRelease = { mapView ->
            onMapTouchStateChanged(false)
            mapView.onPause()
            mapView.onDetach()
        },
    )
}

@Composable
private fun AMapAdvancedSettingsCard(
    androidKey: String,
    webKey: String,
    hasBuildAndroidKey: Boolean,
    hasBuildWebKey: Boolean,
    onAndroidKeyChanged: (String) -> Unit,
    onWebKeyChanged: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("高德高级设置", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = androidKey,
                onValueChange = onAndroidKeyChanged,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Android Key") },
                supportingText = {
                    Text(if (hasBuildAndroidKey) "当前使用构建内置 Android Key" else "留空时不启用高德底图")
                },
            )
            OutlinedTextField(
                value = webKey,
                onValueChange = onWebKeyChanged,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Web Key") },
                supportingText = {
                    Text(if (hasBuildWebKey) "当前使用构建内置 Web Key" else "留空时高德搜索不可用")
                },
            )
        }
    }
}

@Composable
private fun CalibrationSelector(
    mode: CoordinateCalibrationMode,
    onModeChanged: (CoordinateCalibrationMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("坐标校准", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { onModeChanged(CoordinateCalibrationMode.OFF) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(if (mode == CoordinateCalibrationMode.OFF) "已选: 关闭" else "关闭")
            }
            FilledTonalButton(
                onClick = { onModeChanged(CoordinateCalibrationMode.MAINLAND_CHINA_COMPAT) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    if (mode == CoordinateCalibrationMode.MAINLAND_CHINA_COMPAT) {
                        "已选: 大陆兼容"
                    } else {
                        "大陆兼容"
                    },
                )
            }
        }
    }
}

private fun formatCoord(value: Double): String {
    return "%.6f".format(value)
}
