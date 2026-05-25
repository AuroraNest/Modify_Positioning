package com.aurora.modifypositioning.ui

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.MotionEvent
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.MapView as AMapView
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.Polyline
import com.amap.api.maps.model.PolylineOptions
import com.aurora.modifypositioning.model.MapProvider
import com.aurora.modifypositioning.model.MovementPageTab
import com.aurora.modifypositioning.model.MovementPoint
import com.aurora.modifypositioning.model.MovementState
import com.aurora.modifypositioning.model.PlaceSuggestion
import com.aurora.modifypositioning.model.PlannedRouteState
import com.aurora.modifypositioning.model.RouteInputMode
import com.aurora.modifypositioning.model.RoutePoint
import com.aurora.modifypositioning.model.TravelMode
import com.aurora.modifypositioning.ui.components.PlaceSearchBar
import com.aurora.modifypositioning.ui.movement.MovementUiState
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView as OsmMapView
import org.osmdroid.views.overlay.Marker as OsmMarker
import org.osmdroid.views.overlay.Polyline as OsmPolyline

@Composable
fun MovementControlScreen(
    uiState: MovementUiState,
    onSelectTab: (MovementPageTab) -> Unit,
    onSetSearchTarget: (Boolean) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onSearchSuggestionSelected: (PlaceSuggestion) -> Unit,
    onSetTravelMode: (TravelMode) -> Unit,
    onSetPointToPointStartFromCenter: (Double, Double) -> Unit,
    onSetPointToPointEndFromCenter: (Double, Double) -> Unit,
    onPlanPointToPointNow: () -> Unit,
    onSetRouteInputMode: (RouteInputMode) -> Unit,
    onSetSnapToRoad: (Boolean) -> Unit,
    onAddCustomPointFromCenter: (Double, Double) -> Unit,
    onUndoCustomPoint: () -> Unit,
    onClearCustomPoints: () -> Unit,
    onConfirmCustomRoute: () -> Unit,
    onApplySavedRoute: (String) -> Unit,
    onDeleteSavedRoute: (String) -> Unit,
    onStartRandomWalk: () -> Unit,
    onStartPointToPoint: () -> Unit,
    onStartCustomRoute: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
) {
    val background = Brush.verticalGradient(
        colors = listOf(Color(0xFFE6EFF8), Color(0xFFF7FAFD)),
    )

    var mapCenter by remember(uiState.currentTarget) {
        mutableStateOf(MapCenter(uiState.currentTarget.latitude, uiState.currentTarget.longitude))
    }
    var mapInteracting by remember { mutableStateOf(false) }

    val activeRoutePoints = when (uiState.selectedTab) {
        MovementPageTab.POINT_TO_POINT -> {
            val state = uiState.pointToPointRouteState
            if (state is PlannedRouteState.Ready) state.route.points else emptyList()
        }

        MovementPageTab.CUSTOM_ROUTE -> {
            val state = uiState.customRouteState
            when (state) {
                is PlannedRouteState.Ready -> state.route.points
                else -> uiState.customRouteDraft.points
            }
        }

        MovementPageTab.RANDOM_WALK -> emptyList()
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
                        text = "路线模拟移动（${if (uiState.mapProvider == MapProvider.AMAP) "高德底图" else "OSM 底图"}）",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "支持随机步行、两点导航、指定路线（手绘/打钉）",
                        color = Color(0xFFBED7F0),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            TabRow(selectedTabIndex = uiState.selectedTab.ordinal) {
                Tab(
                    selected = uiState.selectedTab == MovementPageTab.RANDOM_WALK,
                    onClick = { onSelectTab(MovementPageTab.RANDOM_WALK) },
                    text = { Text("随机步行") },
                )
                Tab(
                    selected = uiState.selectedTab == MovementPageTab.POINT_TO_POINT,
                    onClick = { onSelectTab(MovementPageTab.POINT_TO_POINT) },
                    text = { Text("两点导航") },
                )
                Tab(
                    selected = uiState.selectedTab == MovementPageTab.CUSTOM_ROUTE,
                    onClick = { onSelectTab(MovementPageTab.CUSTOM_ROUTE) },
                    text = { Text("指定路线") },
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(330.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                MovementMap(
                    currentLat = uiState.currentTarget.latitude,
                    currentLng = uiState.currentTarget.longitude,
                    trace = uiState.tracePoints,
                    route = activeRoutePoints,
                    mapProvider = uiState.mapProvider,
                    effectiveAmapAndroidKey = uiState.effectiveAmapAndroidKey,
                    onMapTouchStateChanged = { interacting ->
                        mapInteracting = interacting
                    },
                    onCenterChanged = { lat, lng ->
                        mapCenter = MapCenter(lat, lng)
                    },
                )
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "地图中心：${formatCoord(mapCenter.latitude)}, ${formatCoord(mapCenter.longitude)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PlaceSearchBar(
                query = uiState.searchQuery,
                suggestions = uiState.searchSuggestions,
                isSearching = uiState.isSearching,
                searchError = uiState.searchError,
                onQueryChanged = onSearchQueryChanged,
                onSelectSuggestion = onSearchSuggestionSelected,
            )

            if (uiState.selectedTab != MovementPageTab.RANDOM_WALK) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = uiState.searchForStart,
                        onClick = { onSetSearchTarget(true) },
                        label = { Text("搜索设起点") },
                    )
                    FilterChip(
                        selected = !uiState.searchForStart,
                        onClick = { onSetSearchTarget(false) },
                        label = { Text("搜索设终点") },
                    )
                }
            }

            when (uiState.selectedTab) {
                MovementPageTab.RANDOM_WALK -> {
                    RandomWalkPanel(uiState = uiState)
                    ActionButtons(
                        onStart = onStartRandomWalk,
                        onPause = onPause,
                        onStop = onStop,
                    )
                }

                MovementPageTab.POINT_TO_POINT -> {
                    PointToPointPanel(
                        uiState = uiState,
                        center = mapCenter,
                        onSetTravelMode = onSetTravelMode,
                        onSetStart = { onSetPointToPointStartFromCenter(mapCenter.latitude, mapCenter.longitude) },
                        onSetEnd = { onSetPointToPointEndFromCenter(mapCenter.latitude, mapCenter.longitude) },
                        onPlan = onPlanPointToPointNow,
                    )
                    ActionButtons(
                        onStart = onStartPointToPoint,
                        onPause = onPause,
                        onStop = onStop,
                    )
                }

                MovementPageTab.CUSTOM_ROUTE -> {
                    CustomRoutePanel(
                        uiState = uiState,
                        onSetTravelMode = onSetTravelMode,
                        onSetInputMode = onSetRouteInputMode,
                        onSetSnapToRoad = onSetSnapToRoad,
                        onAddPoint = { onAddCustomPointFromCenter(mapCenter.latitude, mapCenter.longitude) },
                        onUndo = onUndoCustomPoint,
                        onClear = onClearCustomPoints,
                        onConfirm = onConfirmCustomRoute,
                        onApplySavedRoute = onApplySavedRoute,
                        onDeleteSavedRoute = onDeleteSavedRoute,
                    )
                    ActionButtons(
                        onStart = onStartCustomRoute,
                        onPause = onPause,
                        onStop = onStop,
                    )
                }
            }

            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.End)) {
                Text("返回地图控制台")
            }
        }
    }
}

@Composable
private fun RandomWalkPanel(uiState: MovementUiState) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("中心点：${uiState.centerTarget.name}", style = MaterialTheme.typography.titleMedium)
            Text("移动状态：${movementStateLabel(uiState.movementState)}")
            Text("当前速度：${"%.2f".format(uiState.currentSpeedMps)} m/s")
            Text("距中心距离：${"%.1f".format(uiState.distanceFromCenterMeters)} 米")
            Text("轨迹点数：${uiState.tracePoints.size}")
        }
    }
}

@Composable
private fun PointToPointPanel(
    uiState: MovementUiState,
    center: MapCenter,
    onSetTravelMode: (TravelMode) -> Unit,
    onSetStart: () -> Unit,
    onSetEnd: () -> Unit,
    onPlan: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("两点导航", style = MaterialTheme.typography.titleMedium)
            TravelModeSelector(
                mode = uiState.navigationDraft.travelMode,
                onSelect = onSetTravelMode,
            )
            Text("起点：${uiState.navigationDraft.start?.name ?: "未设置"}")
            Text("终点：${uiState.navigationDraft.end?.name ?: "未设置"}")
            Text("中心点可用于设起点/终点：${formatCoord(center.latitude)}, ${formatCoord(center.longitude)}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSetStart, modifier = Modifier.weight(1f)) {
                    Text("设为起点")
                }
                OutlinedButton(onClick = onSetEnd, modifier = Modifier.weight(1f)) {
                    Text("设为终点")
                }
            }
            Button(onClick = onPlan, modifier = Modifier.fillMaxWidth()) {
                Text("重算路线")
            }
            when (val state = uiState.pointToPointRouteState) {
                PlannedRouteState.Idle -> Text("等待设置起终点后自动规划")
                PlannedRouteState.Loading -> Text("正在规划路线...")
                is PlannedRouteState.Error -> Text("规划失败：${state.message}", color = MaterialTheme.colorScheme.error)
                is PlannedRouteState.Ready -> {
                    Text("路线距离：${"%.1f".format(state.route.distanceMeters)} 米")
                    Text("预计时间：${"%.1f".format(state.route.durationSeconds / 60.0)} 分钟")
                }
            }
            uiState.routeProgress?.let { progress ->
                Text("剩余距离：${"%.1f".format(progress.remainingMeters)} 米")
                Text("剩余时间：${"%.1f".format(progress.remainingSeconds / 60.0)} 分钟")
                Text("完成进度：${"%.1f".format(progress.percent)}%")
            }
        }
    }
}

@Composable
private fun CustomRoutePanel(
    uiState: MovementUiState,
    onSetTravelMode: (TravelMode) -> Unit,
    onSetInputMode: (RouteInputMode) -> Unit,
    onSetSnapToRoad: (Boolean) -> Unit,
    onAddPoint: () -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onConfirm: () -> Unit,
    onApplySavedRoute: (String) -> Unit,
    onDeleteSavedRoute: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("指定路线", style = MaterialTheme.typography.titleMedium)
            TravelModeSelector(
                mode = uiState.customRouteDraft.travelMode,
                onSelect = onSetTravelMode,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = uiState.customRouteDraft.inputMode == RouteInputMode.HAND_DRAW,
                    onClick = { onSetInputMode(RouteInputMode.HAND_DRAW) },
                    label = { Text("手绘路线") },
                )
                FilterChip(
                    selected = uiState.customRouteDraft.inputMode == RouteInputMode.PIN_POINTS,
                    onClick = { onSetInputMode(RouteInputMode.PIN_POINTS) },
                    label = { Text("多点打钉") },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("吸附道路（默认开启）")
                Switch(
                    checked = uiState.customRouteDraft.snapToRoad,
                    onCheckedChange = onSetSnapToRoad,
                )
            }
            Text("当前路线点数：${uiState.customRouteDraft.points.size}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAddPoint, modifier = Modifier.weight(1f)) {
                    Text("添加中心点")
                }
                OutlinedButton(onClick = onUndo, modifier = Modifier.weight(1f)) {
                    Text("撤销一步")
                }
                OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                    Text("清空")
                }
            }
            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
                Text("确认路线")
            }

            when (val state = uiState.customRouteState) {
                PlannedRouteState.Idle -> Text("手绘/打钉后点击确认路线")
                PlannedRouteState.Loading -> Text("正在处理路线...")
                is PlannedRouteState.Error -> Text("路线失败：${state.message}", color = MaterialTheme.colorScheme.error)
                is PlannedRouteState.Ready -> {
                    Text("路线距离：${"%.1f".format(state.route.distanceMeters)} 米")
                    Text("预计时间：${"%.1f".format(state.route.durationSeconds / 60.0)} 分钟")
                }
            }

            if (uiState.savedRoutes.isNotEmpty()) {
                Text("已保存路线", style = MaterialTheme.typography.titleSmall)
                uiState.savedRoutes.take(3).forEach { route ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(route.name)
                            Text(
                                "距离 ${"%.1f".format(route.distanceMeters)} 米 · ${"%.1f".format(route.durationSeconds / 60.0)} 分钟",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { onApplySavedRoute(route.id) }) {
                                    Text("使用")
                                }
                                TextButton(onClick = { onDeleteSavedRoute(route.id) }) {
                                    Text("删除")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TravelModeSelector(
    mode: TravelMode,
    onSelect: (TravelMode) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = mode == TravelMode.WALK,
            onClick = { onSelect(TravelMode.WALK) },
            label = { Text("步行") },
        )
        FilterChip(
            selected = mode == TravelMode.BIKE,
            onClick = { onSelect(TravelMode.BIKE) },
            label = { Text("骑行") },
        )
        FilterChip(
            selected = mode == TravelMode.CAR,
            onClick = { onSelect(TravelMode.CAR) },
            label = { Text("汽车") },
        )
    }
}

@Composable
private fun ActionButtons(
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
) {
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
}

@Composable
private fun MovementMap(
    currentLat: Double,
    currentLng: Double,
    trace: List<MovementPoint>,
    route: List<RoutePoint>,
    mapProvider: MapProvider,
    effectiveAmapAndroidKey: String,
    onMapTouchStateChanged: (Boolean) -> Unit,
    onCenterChanged: (Double, Double) -> Unit,
) {
    if (mapProvider == MapProvider.AMAP && effectiveAmapAndroidKey.isNotBlank()) {
        MovementAMap(
            currentLat = currentLat,
            currentLng = currentLng,
            trace = trace,
            route = route,
            effectiveAmapAndroidKey = effectiveAmapAndroidKey,
            onMapTouchStateChanged = onMapTouchStateChanged,
            onCenterChanged = onCenterChanged,
        )
    } else {
        MovementOsmMap(
            currentLat = currentLat,
            currentLng = currentLng,
            trace = trace,
            route = route,
            onMapTouchStateChanged = onMapTouchStateChanged,
            onCenterChanged = onCenterChanged,
        )
    }
}

@Composable
private fun MovementAMap(
    currentLat: Double,
    currentLng: Double,
    trace: List<MovementPoint>,
    route: List<RoutePoint>,
    effectiveAmapAndroidKey: String,
    onMapTouchStateChanged: (Boolean) -> Unit,
    onCenterChanged: (Double, Double) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapViewRef by remember { mutableStateOf<AMapView?>(null) }
    var aMapRef by remember { mutableStateOf<AMap?>(null) }
    var currentMarker by remember { mutableStateOf<Marker?>(null) }
    var traceLine by remember { mutableStateOf<Polyline?>(null) }
    var routeLine by remember { mutableStateOf<Polyline?>(null) }
    var skipNextCameraEvent by remember { mutableStateOf(false) }

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
            mapViewRef = null
            aMapRef = null
            currentMarker = null
            traceLine = null
            routeLine = null
        }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(18.dp)),
        factory = { context ->
            MapsInitializer.setApiKey(effectiveAmapAndroidKey)
            AMapView(context).apply {
                onCreate(Bundle())
                val aMap = map
                val start = LatLng(currentLat, currentLng)
                aMap.uiSettings.isZoomControlsEnabled = false
                aMap.uiSettings.isRotateGesturesEnabled = false
                aMap.uiSettings.isTiltGesturesEnabled = false
                aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(start, 16f))

                currentMarker = aMap.addMarker(
                    MarkerOptions()
                        .position(start)
                        .title("当前位置"),
                )

                routeLine = aMap.addPolyline(
                    PolylineOptions()
                        .color(AndroidColor.parseColor("#1E6091"))
                        .width(12f)
                        .addAll(route.map { LatLng(it.lat, it.lng) }),
                )

                traceLine = aMap.addPolyline(
                    PolylineOptions()
                        .color(AndroidColor.parseColor("#D62828"))
                        .width(10f)
                        .addAll(trace.map { LatLng(it.lat, it.lng) }),
                )

                aMap.setOnCameraChangeListener(
                    object : AMap.OnCameraChangeListener {
                        override fun onCameraChange(cameraPosition: com.amap.api.maps.model.CameraPosition?) {
                            Unit
                        }

                        override fun onCameraChangeFinish(cameraPosition: com.amap.api.maps.model.CameraPosition?) {
                            val position = cameraPosition ?: return
                            if (skipNextCameraEvent) {
                                skipNextCameraEvent = false
                                return
                            }
                            onCenterChanged(position.target.latitude, position.target.longitude)
                        }
                    },
                )

                aMap.setOnMapClickListener { latLng ->
                    onCenterChanged(latLng.latitude, latLng.longitude)
                    skipNextCameraEvent = true
                    aMap.animateCamera(CameraUpdateFactory.newLatLng(latLng))
                }
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

                mapViewRef = this
                aMapRef = aMap
            }
        },
        update = {
            val aMap = aMapRef
            val currentPosition = LatLng(currentLat, currentLng)
            val marker = currentMarker
            if (marker == null && aMap != null) {
                currentMarker = aMap.addMarker(
                    MarkerOptions()
                        .position(currentPosition)
                        .title("当前位置"),
                )
            } else {
                marker?.position = currentPosition
            }
            traceLine?.points = trace.map { LatLng(it.lat, it.lng) }
            routeLine?.points = route.map { LatLng(it.lat, it.lng) }
        },
        onRelease = { mapView ->
            // 某些机型销毁时会触发高德 native 崩溃，先仅暂停以保证切页稳定。
            onMapTouchStateChanged(false)
            mapView.onPause()
        },
    )
}

@Composable
private fun MovementOsmMap(
    currentLat: Double,
    currentLng: Double,
    trace: List<MovementPoint>,
    route: List<RoutePoint>,
    onMapTouchStateChanged: (Boolean) -> Unit,
    onCenterChanged: (Double, Double) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapViewRef by remember { mutableStateOf<OsmMapView?>(null) }
    var currentMarker by remember { mutableStateOf<OsmMarker?>(null) }
    var traceLine by remember { mutableStateOf<OsmPolyline?>(null) }
    var routeLine by remember { mutableStateOf<OsmPolyline?>(null) }

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
            mapViewRef = null
            currentMarker = null
            traceLine = null
            routeLine = null
        }
    }

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
            OsmMapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                isTilesScaledToDpi = true
                setBuiltInZoomControls(false)
                controller.setZoom(16.0)
                controller.setCenter(GeoPoint(currentLat, currentLng))

                currentMarker = OsmMarker(this).apply {
                    position = GeoPoint(currentLat, currentLng)
                    title = "当前位置"
                    setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_BOTTOM)
                }.also { overlays.add(it) }

                routeLine = OsmPolyline().apply {
                    outlinePaint.color = AndroidColor.parseColor("#1E6091")
                    outlinePaint.strokeWidth = 8f
                    setPoints(route.map { GeoPoint(it.lat, it.lng) })
                }.also { overlays.add(it) }

                traceLine = OsmPolyline().apply {
                    outlinePaint.color = AndroidColor.parseColor("#D62828")
                    outlinePaint.strokeWidth = 7f
                    setPoints(trace.map { GeoPoint(it.lat, it.lng) })
                }.also { overlays.add(it) }

                addMapListener(
                    object : MapListener {
                        override fun onScroll(event: ScrollEvent): Boolean {
                            if (!this@apply.isAnimating) {
                                val center = mapCenter
                                onCenterChanged(center.latitude, center.longitude)
                            }
                            return true
                        }

                        override fun onZoom(event: ZoomEvent): Boolean {
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
                mapViewRef = this
            }
        },
        update = { mapView ->
            val currentPoint = GeoPoint(currentLat, currentLng)
            currentMarker?.position = currentPoint
            traceLine?.setPoints(trace.map { GeoPoint(it.lat, it.lng) })
            routeLine?.setPoints(route.map { GeoPoint(it.lat, it.lng) })
            if (!mapView.isAnimating) {
                mapView.controller.animateTo(currentPoint)
            }
            mapView.invalidate()
        },
        onRelease = { mapView ->
            onMapTouchStateChanged(false)
            mapView.onPause()
            mapView.onDetach()
        },
    )
}

private fun movementStateLabel(state: MovementState): String {
    return when (state) {
        MovementState.Idle -> "空闲"
        MovementState.Walking -> "移动中"
        MovementState.ReachedBoundary -> "已到边界"
        MovementState.ReachedDestination -> "已到终点"
        MovementState.Paused -> "已暂停"
        is MovementState.Error -> "异常: ${state.message}"
    }
}

private fun formatCoord(value: Double): String {
    return "%.6f".format(value)
}

private data class MapCenter(
    val latitude: Double,
    val longitude: Double,
)
