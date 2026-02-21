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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.aurora.modifypositioning.model.MovementPoint
import com.aurora.modifypositioning.model.MovementState
import com.aurora.modifypositioning.ui.movement.MovementUiState
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Composable
fun MovementControlScreen(
    uiState: MovementUiState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
) {
    val background = Brush.verticalGradient(
        colors = listOf(Color(0xFFE6EFF8), Color(0xFFF7FAFD)),
    )
    val stateLabel = when (val state = uiState.movementState) {
        MovementState.Idle -> "空闲"
        MovementState.Walking -> "步行中"
        MovementState.ReachedBoundary -> "已到边界"
        MovementState.Paused -> "已暂停"
        is MovementState.Error -> "异常: ${state.message}"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 12.dp)
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
                        text = "模拟移动（随机步行）",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "到边界会自动停止移动，但保持当前位置注入",
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
                RandomWalkMap(
                    center = GeoPoint(uiState.centerTarget.latitude, uiState.centerTarget.longitude),
                    current = GeoPoint(uiState.currentTarget.latitude, uiState.currentTarget.longitude),
                    trace = uiState.tracePoints,
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(text = "中心点：${uiState.centerTarget.name}", style = MaterialTheme.typography.titleMedium)
                    Text(text = "移动状态：$stateLabel")
                    Text(text = "当前速度：${"%.2f".format(uiState.currentSpeedMps)} m/s")
                    Text(text = "距中心距离：${"%.1f".format(uiState.distanceFromCenterMeters)} 米")
                    Text(text = "轨迹点数：${uiState.tracePoints.size}")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                    Text("开始移动")
                }
                FilledTonalButton(onClick = onPause, modifier = Modifier.weight(1f)) {
                    Text("暂停")
                }
                OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) {
                    Text("停止")
                }
            }

            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.End)) {
                Text("返回地图控制台")
            }
        }
    }
}

@Composable
private fun RandomWalkMap(
    center: GeoPoint,
    current: GeoPoint,
    trace: List<MovementPoint>,
) {
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var centerMarker by remember { mutableStateOf<Marker?>(null) }
    var currentMarker by remember { mutableStateOf<Marker?>(null) }
    var trackPolyline by remember { mutableStateOf<Polyline?>(null) }

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(18.dp)),
        factory = { context ->
            Configuration.getInstance().load(
                context,
                PreferenceManager.getDefaultSharedPreferences(context),
            )
            Configuration.getInstance().userAgentValue = context.packageName

            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                isTilesScaledToDpi = true
                controller.setZoom(17.0)
                controller.setCenter(center)

                val centerPointMarker = Marker(this).apply {
                    position = center
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "中心点"
                }
                overlays.add(centerPointMarker)

                val currentPointMarker = Marker(this).apply {
                    position = current
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "当前位置"
                }
                overlays.add(currentPointMarker)

                val polyline = Polyline().apply {
                    outlinePaint.strokeWidth = 6f
                    outlinePaint.color = android.graphics.Color.parseColor("#145374")
                    setPoints(trace.map { GeoPoint(it.lat, it.lng) })
                }
                overlays.add(polyline)

                centerMarker = centerPointMarker
                currentMarker = currentPointMarker
                trackPolyline = polyline
            }.also {
                mapViewRef = it
            }
        },
        update = { mapView ->
            centerMarker?.position = center
            currentMarker?.position = current

            if (trace.isNotEmpty()) {
                trackPolyline?.setPoints(trace.map { GeoPoint(it.lat, it.lng) })
            } else {
                trackPolyline?.setPoints(emptyList())
            }

            mapView.controller.animateTo(current)
            mapView.invalidate()
        },
        onRelease = {
            it.onDetach()
            mapViewRef = null
            centerMarker = null
            currentMarker = null
            trackPolyline = null
        },
    )
}
