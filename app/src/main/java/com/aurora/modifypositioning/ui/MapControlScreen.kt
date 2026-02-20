package com.aurora.modifypositioning.ui

import android.preference.PreferenceManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var idleJob by remember { mutableStateOf<Job?>(null) }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("地图精细选点控制台", style = MaterialTheme.typography.headlineSmall)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    Configuration.getInstance().load(
                        context,
                        PreferenceManager.getDefaultSharedPreferences(context),
                    )
                    Configuration.getInstance().userAgentValue = context.packageName

                    MapView(context).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        isTilesScaledToDpi = true
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
                        mapViewRef = createdMapView
                    }
                },
                update = { updatedMapView ->
                    mapViewRef = updatedMapView
                },
            )

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Transparent),
            ) {
                Text("+", style = MaterialTheme.typography.headlineMedium, color = Color.Red)
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

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("当前选点：${uiState.selectedTarget.name}")
                Text("会话搜索请求数：${uiState.searchRequestCount}")

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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                Text("开始")
            }
            OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f)) {
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

@Composable
private fun CalibrationSelector(
    mode: CoordinateCalibrationMode,
    onModeChanged: (CoordinateCalibrationMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("坐标校准")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onModeChanged(CoordinateCalibrationMode.OFF) },
                modifier = Modifier.weight(1f),
            ) {
                Text(if (mode == CoordinateCalibrationMode.OFF) "已选：关闭" else "关闭")
            }
            OutlinedButton(
                onClick = { onModeChanged(CoordinateCalibrationMode.MAINLAND_CHINA_COMPAT) },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    if (mode == CoordinateCalibrationMode.MAINLAND_CHINA_COMPAT) {
                        "已选：中国大陆兼容"
                    } else {
                        "中国大陆兼容"
                    },
                )
            }
        }
    }
}
