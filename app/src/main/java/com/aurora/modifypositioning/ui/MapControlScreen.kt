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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView as AMapView
import com.amap.api.maps.model.LatLng
import com.aurora.modifypositioning.BuildConfig
import com.aurora.modifypositioning.model.CoordinateCalibrationMode
import com.aurora.modifypositioning.model.FavoriteLocation
import com.aurora.modifypositioning.model.MapProvider
import com.aurora.modifypositioning.model.PlaceSuggestion
import com.aurora.modifypositioning.model.SelectionSource
import com.aurora.modifypositioning.ui.components.FavoriteSheet
import com.aurora.modifypositioning.ui.components.PlaceSearchBar
import com.aurora.modifypositioning.ui.map.MapControlUiState
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
    onSearchQueryChanged: (String) -> Unit,
    onSuggestionSelected: (PlaceSuggestion) -> Unit,
    onMapDraggedSelection: (Double, Double) -> Unit,
    onMapProviderChanged: (MapProvider) -> Unit,
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
        colors = listOf(
            Color(0xFFE5EEF8),
            Color(0xFFF6F9FD),
        ),
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
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    shadowElevation = 10.dp,
                    shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
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
                            Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                                Text("开始")
                            }
                            FilledTonalButton(onClick = onPause, modifier = Modifier.weight(1f)) {
                                Text("暂停")
                            }
                            OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) {
                                Text("停止")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TextButton(onClick = onOpenGuide, modifier = Modifier.weight(1f)) {
                                Text("首次引导")
                            }
                            TextButton(onClick = onOpenMovement, modifier = Modifier.weight(1f)) {
                                Text("模拟移动")
                            }
                            TextButton(onClick = onOpenDiagnostic, modifier = Modifier.weight(1f)) {
                                Text("诊断页面")
                            }
                        }
                    }
                }
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 12.dp)
                    .verticalScroll(
                        state = rememberScrollState(),
                        enabled = !mapInteracting,
                    ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2942)),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "地图精细选点控制台",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "拖动地图后停 0.5 秒，再选择“用地图中心点”",
                            color = Color(0xFFBED7F0),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "当前地图源：${if (uiState.mapProvider == MapProvider.AMAP) "高德(国内优先)" else "OSM(海外优先)"}",
                            color = Color(0xFFBED7F0),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (uiState.mapProvider == MapProvider.AMAP && BuildConfig.AMAP_API_KEY.isBlank()) {
                            Text(
                                text = "未配置高德 Key，建议切换 OSM",
                                color = Color(0xFFFFB4AB),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (uiState.mapProvider == MapProvider.AMAP && BuildConfig.AMAP_WEB_API_KEY.isBlank()) {
                            Text(
                                text = "高德搜索未配置 Web服务 Key，搜索建议切到 OSM",
                                color = Color(0xFFFFD8A8),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (
                            uiState.mapProvider == MapProvider.AMAP &&
                                !isLikelyInChina(uiState.camera.lat, uiState.camera.lng)
                        ) {
                            Text(
                                text = "当前区域在海外，建议切换到 OSM",
                                color = Color(0xFFFFD8A8),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = { onMapProviderChanged(MapProvider.AMAP) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (uiState.mapProvider == MapProvider.AMAP) "已选：高德" else "高德")
                    }
                    FilledTonalButton(
                        onClick = { onMapProviderChanged(MapProvider.OSM) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (uiState.mapProvider == MapProvider.OSM) "已选：OSM" else "OSM")
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(330.dp),
                    shape = RoundedCornerShape(18.dp),
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
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xCC0F2942))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = "十字中心点就是候选目标",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                        Text(
                            text = "+",
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color(0xFFE53935),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                PlaceSearchBar(
                    query = uiState.searchQuery,
                    suggestions = uiState.suggestions,
                    isSearching = uiState.isSearching,
                    searchError = uiState.searchError,
                    onQueryChanged = onSearchQueryChanged,
                    onSelectSuggestion = onSuggestionSelected,
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "当前生效目标：${uiState.selectedTarget.name}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "地图中心候选：${formatCoord(uiState.mapCenterCandidate.latitude)}, ${formatCoord(uiState.mapCenterCandidate.longitude)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilledTonalButton(
                                onClick = onUseSearchTarget,
                                enabled = uiState.lastSearchTarget != null,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("用搜索点")
                            }
                            OutlinedButton(
                                onClick = onUseMapCenterTarget,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("用地图中心点")
                            }
                        }
                        Text(
                            text = "会话搜索请求数：${uiState.searchRequestCount}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        CalibrationSelector(
                            mode = uiState.calibrationMode,
                            onModeChanged = onCalibrationModeChanged,
                        )
                    }
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
                shape = RoundedCornerShape(18.dp),
            )
            .clip(RoundedCornerShape(18.dp)),
        factory = { context ->
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
            // 某些机型销毁时会触发高德 native 崩溃，先仅暂停以保证切页稳定。
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
                shape = RoundedCornerShape(18.dp),
            )
            .clip(RoundedCornerShape(18.dp)),
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
            ) {
                Text(if (mode == CoordinateCalibrationMode.OFF) "已选：关闭" else "关闭")
            }
            FilledTonalButton(
                onClick = { onModeChanged(CoordinateCalibrationMode.MAINLAND_CHINA_COMPAT) },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    if (mode == CoordinateCalibrationMode.MAINLAND_CHINA_COMPAT) {
                        "已选：大陆兼容"
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

private fun isLikelyInChina(lat: Double, lng: Double): Boolean {
    return lat in 3.0..54.0 && lng in 73.0..136.0
}
