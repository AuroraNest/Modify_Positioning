package com.aurora.modifypositioning.ui

import android.preference.PreferenceManager
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.aurora.modifypositioning.model.CoordinateCalibrationMode
import com.aurora.modifypositioning.model.FavoriteLocation
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
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView

@Composable
fun MapControlScreen(
    uiState: MapControlUiState,
    onSearchQueryChanged: (String) -> Unit,
    onSuggestionSelected: (PlaceSuggestion) -> Unit,
    onMapDraggedSelection: (Double, Double) -> Unit,
    onCalibrationModeChanged: (CoordinateCalibrationMode) -> Unit,
    onAddFavorite: () -> Unit,
    onFavoriteNameInputChanged: (String) -> Unit,
    onSelectFavorite: (FavoriteLocation) -> Unit,
    onDeleteFavorite: (FavoriteLocation) -> Unit,
    onRenameFavorite: (FavoriteLocation, String) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenDiagnostic: () -> Unit,
    onCameraIdle: (Double, Double, Float) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var idleJob by remember { mutableStateOf<Job?>(null) }
    var mapTileProvider by rememberSaveable { mutableStateOf(MapTileProvider.AMAP_ROAD) }

    fun publishCenter(mapView: MapView) {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(250)
            val center = mapView.mapCenter
            val lat = center.latitude
            val lng = center.longitude
            val zoom = mapView.zoomLevelDouble.toFloat()
            onCameraIdle(lat, lng, zoom)
            onMapDraggedSelection(lat, lng)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapViewRef?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapViewRef?.onPause()
                Lifecycle.Event.ON_DESTROY -> mapViewRef?.onDetach()
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            idleJob?.cancel()
            mapViewRef?.onDetach()
            mapViewRef = null
        }
    }

    LaunchedEffect(
        uiState.selectedTarget.latitude,
        uiState.selectedTarget.longitude,
        uiState.camera.zoom,
        uiState.lastSelectionSource,
    ) {
        val mapView = mapViewRef ?: return@LaunchedEffect
        if (uiState.lastSelectionSource == SelectionSource.MAP_DRAG) {
            return@LaunchedEffect
        }

        val desired = GeoPoint(uiState.selectedTarget.latitude, uiState.selectedTarget.longitude)
        val current = mapView.mapCenter
        if (
            abs(current.latitude - desired.latitude) > 0.000001 ||
            abs(current.longitude - desired.longitude) > 0.000001
        ) {
            mapView.controller.animateTo(desired)
        }

        if (abs(mapView.zoomLevelDouble - uiState.camera.zoom.toDouble()) > 0.05) {
            mapView.controller.setZoom(uiState.camera.zoom.toDouble())
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            TextButton(onClick = onOpenGuide) {
                                Text("首次引导")
                            }
                            TextButton(onClick = onOpenDiagnostic) {
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
                    .verticalScroll(rememberScrollState()),
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
                            text = "拖动地图后停 0.5 秒，再点开始即可生效",
                            color = Color(0xFFBED7F0),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(330.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AndroidMap(
                            uiState = uiState,
                            tileProvider = mapTileProvider,
                            publishCenter = { mapView -> publishCenter(mapView) },
                            onMapCreated = { mapViewRef = it },
                            onMapUpdated = { mapViewRef = it },
                        )

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xCC0F2942))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = "十字中心点就是目标",
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = { mapTileProvider = MapTileProvider.AMAP_ROAD },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            if (mapTileProvider == MapTileProvider.AMAP_ROAD) {
                                "已选：高德路网"
                            } else {
                                "切换高德路网"
                            },
                        )
                    }
                    OutlinedButton(
                        onClick = { mapTileProvider = MapTileProvider.OSM_MAPNIK },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            if (mapTileProvider == MapTileProvider.OSM_MAPNIK) {
                                "已选：OSM备用"
                            } else {
                                "OSM备用"
                            },
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
                            text = "当前选点：${uiState.selectedTarget.name}",
                            style = MaterialTheme.typography.titleMedium,
                        )
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
private fun AndroidMap(
    uiState: MapControlUiState,
    tileProvider: MapTileProvider,
    publishCenter: (MapView) -> Unit,
    onMapCreated: (MapView) -> Unit,
    onMapUpdated: (MapView) -> Unit,
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

            MapView(context).apply {
                setTileSource(resolveTileSource(tileProvider))
                setMultiTouchControls(true)
                isTilesScaledToDpi = true
                setBuiltInZoomControls(false)
                controller.setZoom(uiState.camera.zoom.toDouble())
                controller.setCenter(GeoPoint(uiState.camera.lat, uiState.camera.lng))
                addMapListener(
                    object : MapListener {
                        override fun onScroll(event: ScrollEvent): Boolean {
                            if (!this@apply.isAnimating) {
                                publishCenter(this@apply)
                            }
                            return true
                        }

                        override fun onZoom(event: ZoomEvent): Boolean {
                            if (!this@apply.isAnimating) {
                                publishCenter(this@apply)
                            }
                            return true
                        }
                    },
                )
            }.also { createdMapView ->
                onMapCreated(createdMapView)
            }
        },
        update = { updatedMapView ->
            val expectedTileSource = resolveTileSource(tileProvider)
            if (updatedMapView.tileProvider.tileSource.name() != expectedTileSource.name()) {
                updatedMapView.setTileSource(expectedTileSource)
                updatedMapView.invalidate()
            }
            onMapUpdated(updatedMapView)
        },
    )
}

private enum class MapTileProvider {
    AMAP_ROAD,
    OSM_MAPNIK,
}

private fun resolveTileSource(provider: MapTileProvider) = when (provider) {
    MapTileProvider.AMAP_ROAD -> AmapRoadTileSource
    MapTileProvider.OSM_MAPNIK -> TileSourceFactory.MAPNIK
}

private val AmapRoadTileSource = object : OnlineTileSourceBase(
    "AMAP_ROAD",
    3,
    19,
    256,
    ".png",
    arrayOf(
        "https://webrd01.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=7",
        "https://webrd02.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=7",
        "https://webrd03.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=7",
        "https://webrd04.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=7",
    ),
    "©AutoNavi",
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        return "${getBaseUrl()}&x=$x&y=$y&z=$zoom"
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
