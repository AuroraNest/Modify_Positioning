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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aurora.modifypositioning.model.CoordinateCalibrationMode
import com.aurora.modifypositioning.model.SelectionSource
import com.aurora.modifypositioning.model.TargetLocation
import com.aurora.modifypositioning.ui.components.FavoriteSheet
import com.aurora.modifypositioning.ui.components.PlaceSearchBar
import com.aurora.modifypositioning.ui.map.MapControlUiState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun MapControlScreen(
    uiState: MapControlUiState,
    onSearchQueryChanged: (String) -> Unit,
    onSuggestionSelected: (com.aurora.modifypositioning.model.PlaceSuggestion) -> Unit,
    onMapDraggedSelection: (Double, Double) -> Unit,
    onManualLatChanged: (String) -> Unit,
    onManualLngChanged: (String) -> Unit,
    onApplyManualCoordinate: () -> Unit,
    onCalibrationModeChanged: (CoordinateCalibrationMode) -> Unit,
    onAddFavorite: () -> Unit,
    onFavoriteNameInputChanged: (String) -> Unit,
    onSelectFavorite: (com.aurora.modifypositioning.model.FavoriteLocation) -> Unit,
    onDeleteFavorite: (com.aurora.modifypositioning.model.FavoriteLocation) -> Unit,
    onRenameFavorite: (com.aurora.modifypositioning.model.FavoriteLocation, String) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenDiagnostic: () -> Unit,
    onCameraIdle: (Double, Double, Float) -> Unit,
) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(uiState.camera.lat, uiState.camera.lng),
            uiState.camera.zoom,
        )
    }

    LaunchedEffect(uiState.selectedTarget.latitude, uiState.selectedTarget.longitude, uiState.lastSelectionSource) {
        if (uiState.lastSelectionSource != SelectionSource.MAP_DRAG) {
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLng(
                    LatLng(uiState.selectedTarget.latitude, uiState.selectedTarget.longitude),
                ),
                durationMs = 350,
            )
        }
    }

    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
            val center = cameraPositionState.position.target
            onCameraIdle(center.latitude, center.longitude, cameraPositionState.position.zoom)
            onMapDraggedSelection(center.latitude, center.longitude)
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
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    myLocationButtonEnabled = false,
                ),
                properties = MapProperties(isMyLocationEnabled = false),
            )

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Transparent),
            ) {
                Text("✛", style = MaterialTheme.typography.headlineMedium, color = Color.Red)
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
                Text("坐标：(${uiState.selectedTarget.latitude}, ${uiState.selectedTarget.longitude})")
                Text("会话搜索请求数：${uiState.searchRequestCount}")

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.manualLat,
                        onValueChange = onManualLatChanged,
                        modifier = Modifier.weight(1f),
                        label = { Text("纬度") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = uiState.manualLng,
                        onValueChange = onManualLngChanged,
                        modifier = Modifier.weight(1f),
                        label = { Text("经度") },
                        singleLine = true,
                    )
                    Button(onClick = onApplyManualCoordinate) {
                        Text("应用")
                    }
                }

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
