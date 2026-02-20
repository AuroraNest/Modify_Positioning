package com.aurora.modifypositioning.ui

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
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.LatLng
import com.aurora.modifypositioning.model.CoordinateCalibrationMode
import com.aurora.modifypositioning.model.FavoriteLocation
import com.aurora.modifypositioning.model.PlaceSuggestion
import com.aurora.modifypositioning.model.SelectionSource
import com.aurora.modifypositioning.ui.components.FavoriteSheet
import com.aurora.modifypositioning.ui.components.PlaceSearchBar
import com.aurora.modifypositioning.ui.map.MapControlUiState
import kotlinx.coroutines.delay

@Composable
fun MapControlScreen(
    uiState: MapControlUiState,
    onSearchQueryChanged: (String) -> Unit,
    onSuggestionSelected: (PlaceSuggestion) -> Unit,
    onMapDraggedSelection: (Double, Double) -> Unit,
    onManualLatChanged: (String) -> Unit,
    onManualLngChanged: (String) -> Unit,
    onApplyManualCoordinate: () -> Unit,
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
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var suppressCameraCallback by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapViewRef?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapViewRef?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewRef?.onPause()
            mapViewRef?.onDestroy()
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

        suppressCameraCallback = true
        mapView.map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(uiState.selectedTarget.latitude, uiState.selectedTarget.longitude),
                uiState.camera.zoom,
            ),
        )
        delay(420)
        suppressCameraCallback = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("高德地图精细选点控制台", style = MaterialTheme.typography.headlineSmall)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    MapsInitializer.updatePrivacyShow(context, true, true)
                    MapsInitializer.updatePrivacyAgree(context, true)

                    MapView(context).apply {
                        onCreate(null)
                        onResume()
                        val aMap = map
                        aMap.uiSettings.isZoomControlsEnabled = false
                        aMap.uiSettings.isMyLocationButtonEnabled = false
                        aMap.uiSettings.isScaleControlsEnabled = false
                        aMap.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(uiState.camera.lat, uiState.camera.lng),
                                uiState.camera.zoom,
                            ),
                        )
                        aMap.setOnCameraChangeListener(
                            object : AMap.OnCameraChangeListener {
                                override fun onCameraChange(position: com.amap.api.maps.model.CameraPosition?) {
                                    // no-op
                                }

                                override fun onCameraChangeFinish(position: com.amap.api.maps.model.CameraPosition?) {
                                    if (suppressCameraCallback || position == null) {
                                        return
                                    }
                                    onCameraIdle(
                                        position.target.latitude,
                                        position.target.longitude,
                                        position.zoom,
                                    )
                                    onMapDraggedSelection(
                                        position.target.latitude,
                                        position.target.longitude,
                                    )
                                }
                            },
                        )
                    }.also {
                        mapViewRef = it
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
