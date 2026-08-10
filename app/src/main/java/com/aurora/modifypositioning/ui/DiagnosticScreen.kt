package com.aurora.modifypositioning.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aurora.modifypositioning.model.DiagnosticLocation
import com.aurora.modifypositioning.model.DiagnosticSnapshot
import com.aurora.modifypositioning.model.DiagnosticVerdictStatus
import com.aurora.modifypositioning.model.FixedPointChannelReadiness
import com.aurora.modifypositioning.model.FixedPointReadinessSnapshot
import com.aurora.modifypositioning.model.FixedPointReadinessState
import com.aurora.modifypositioning.model.MockState
import com.aurora.modifypositioning.model.MovementMode
import com.aurora.modifypositioning.model.MovementState
import com.aurora.modifypositioning.model.RouteSource
import com.aurora.modifypositioning.model.RestorationState
import com.aurora.modifypositioning.model.TravelMode
import com.aurora.modifypositioning.model.realLocationOverwriteSummary
import com.aurora.modifypositioning.model.thirdPartyValidationChecklists
import com.aurora.modifypositioning.model.toCopyableDiagnosticReport
import com.aurora.modifypositioning.model.toDiagnosticVerdict
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticScreen(
    snapshot: DiagnosticSnapshot,
    onRefresh: () -> Unit,
    onRestabilize: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val verdict = snapshot.toDiagnosticVerdict()
    val report = snapshot.toCopyableDiagnosticReport()
    val background = Brush.verticalGradient(
        colors = listOf(Color(0xFFF2F2F7), Color(0xFFF7F7FA)),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = Color.Transparent,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "诊断",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "检查系统授权, 服务状态和第三方 App 生效条件.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("诊断结论: ${verdictStatusLabel(verdict.status)}")
                Text(verdict.title, style = MaterialTheme.typography.titleMedium)
                DiagnosticList("阻断项", verdict.blockers)
                DiagnosticList("可能原因", verdict.possibleCauses)
                DiagnosticList("下一步", verdict.nextSteps)
            }
        }

        snapshot.fixedPointReadiness?.let { readiness ->
            FixedPointReadinessCard(
                readiness = readiness,
                restabilizeEnabled = snapshot.appState == MockState.Running || snapshot.appState == MockState.Paused,
                onRestabilize = onRestabilize,
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("系统环境")
                Text("检测时间: ${formatTime(snapshot.generatedAtMillis)}")
                Text("设备: ${snapshot.deviceManufacturer} ${snapshot.deviceModel}")
                Text("Android: ${snapshot.androidVersion} (API ${snapshot.androidApiLevel})")
                Text("App 版本: ${snapshot.appVersionName} (${snapshot.appVersionCode})")
                Text("模拟位置信息应用: ${if (snapshot.isMockAppSelected) "已设置" else "未设置"}")
                Text("GPS 开关: ${if (snapshot.gpsEnabled) "开启" else "关闭"}")
                Text("网络定位开关: ${if (snapshot.networkEnabled) "开启" else "关闭"}")
                Text("坐标校准模式: ${snapshot.calibrationMode}")
                Text("会话搜索请求数: ${snapshot.searchRequestCount}")
                Text(
                    if (snapshot.missingPermissions.isEmpty()) {
                        "权限: 完整"
                    } else {
                        "权限缺失: ${snapshot.missingPermissions.joinToString()}"
                    },
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("应用运行状态")
                Text("服务状态: ${stateLabel(snapshot.appState)}")
                Text("停止闭环: ${restorationStateLabel(snapshot.restorationState)}")
                Text("移动模式: ${movementModeLabel(snapshot.movementMode)}")
                Text("移动状态: ${movementStateLabel(snapshot.movementState)}")
                Text("当前速度: ${"%.2f".format(snapshot.movementCurrentSpeedMps)} m/s")
                Text("距中心距离: ${"%.1f".format(snapshot.movementDistanceFromCenterMeters)} 米")
                if (snapshot.routeMode != null) {
                    Text("路线模式: ${movementModeLabel(snapshot.routeMode)}")
                    Text("交通方式: ${travelModeLabel(snapshot.travelMode)}")
                    Text("路线来源: ${routeSourceLabel(snapshot.routeSource)}")
                    Text("剩余距离: ${formatDistance(snapshot.remainingDistanceMeters)}")
                    Text("剩余时间: ${formatDuration(snapshot.remainingDurationSeconds)}")
                    Text("路线进度: ${formatPercent(snapshot.routeProgressPercent)}")
                }
                Text(
                    text = if (snapshot.movementLastPointTimeMillis == null) {
                        "最近移动点: 暂无"
                    } else {
                        "最近移动点: ${formatTime(snapshot.movementLastPointTimeMillis)}"
                    },
                )
                val injection = snapshot.lastInjection
                if (injection == null) {
                    Text("最近注入: 暂无")
                } else {
                    Text(
                        "最近注入: ${injection.provider} @ (${injection.latitude}, ${injection.longitude})",
                    )
                    Text("注入精度: ${"%.1f".format(injection.accuracyMeters)} 米")
                    Text("注入时间: ${formatTime(injection.timeMillis)}")
                    Text("验证 mock: ${injection.verificationMockStatus ?: "-"}")
                    Text("验证距离: ${formatDistance(injection.verificationDistanceMeters)}")
                    Text("provider 重建时间: ${formatTimeOrDash(injection.providerRebuildTimeMillis)}")
                    Text("恢复状态: ${injection.recoveryStatus ?: "-"}")
                }
            }
        }

        DiagnosticLocationCard(title = "GPS 最近位置", location = snapshot.gpsLastKnown)
        DiagnosticLocationCard(title = "Network 最近位置", location = snapshot.networkLastKnown)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("注入通道状态")
                Text("整体: ${snapshot.injectorOverallState}")
                Text("运行: ${snapshot.injectorActiveCount}, 失败: ${snapshot.injectorFailedCount}")
                Text("提示: ${snapshot.injectorWarning ?: "-"}")
                snapshot.injectorStatuses.forEach { status ->
                    Text("${status.displayName}: ${status.state}")
                    Text(
                        "最近成功: ${formatTimeOrDash(status.lastSuccessAtMillis)}, " +
                            "最近失败: ${formatTimeOrDash(status.lastFailureAtMillis)}, " +
                            "错误码: ${status.lastErrorCode ?: "-"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Fused 定位状态")
                Text("可用: ${if (snapshot.fusedAvailable) "true" else "false"}")
                Text("mock mode: ${if (snapshot.fusedMockModeEnabled) "true" else "false"}")
                Text("mock mode pending: ${if (snapshot.fusedMockModePending) "true" else "false"}")
                Text("注入 pending: ${if (snapshot.fusedLastInjectionPending) "true" else "false"}")
                Text("最近注入: ${formatTimeOrDash(snapshot.fusedLastInjectionTimeMillis)}")
                Text(
                    "最近成功坐标: ${formatCoordinate(snapshot.fusedLastSuccessfulLatitude, snapshot.fusedLastSuccessfulLongitude)}",
                )
                Text("最近错误: ${snapshot.fusedLastError ?: "-"}")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("判断建议")
                Text("1) 多数使用系统定位或 fused 定位的 app 可生效; 显式检测 mock 或服务端校验的 app 可能无效")
                Text("2) 若本页注入坐标和最近位置都不变, 优先检查模拟位置信息应用是否仍为本 App")
                Text("3) ${snapshot.realLocationOverwriteSummary()}")
                Text("4) 若服务状态异常, 请先停止再开始, 并保持应用后台不被清理")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("第三方 app 验证清单")
                thirdPartyValidationChecklists().forEach { checklist ->
                    Text(checklist.appName, fontWeight = FontWeight.SemiBold)
                    checklist.steps.forEachIndexed { index, step ->
                        Text("${index + 1}) $step")
                    }
                }
            }
        }

        Button(
            onClick = {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(
                    ClipData.newPlainText("Modify Positioning 诊断报告", report),
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text("复制诊断")
        }

        Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
            Text("刷新诊断")
        }

        TextButton(onClick = onBack) {
            Text("返回控制台")
        }
        }
    }
}

@Composable
private fun FixedPointReadinessCard(
    readiness: FixedPointReadinessSnapshot,
    restabilizeEnabled: Boolean,
    onRestabilize: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("固定点准备状态", style = MaterialTheme.typography.titleMedium)
            Text("状态: ${fixedPointStateLabel(readiness.state)}")
            Text("分数: ${readiness.score}/100")
            Text("建议: ${readiness.summary}")
            Text(
                "原始目标: ${readiness.rawTarget.name} " +
                    "(${"%.6f".format(readiness.rawTarget.latitude)}, ${"%.6f".format(readiness.rawTarget.longitude)})",
            )
            Text(
                "实际注入: (${"%.6f".format(readiness.injectedTarget.latitude)}, " +
                    "${"%.6f".format(readiness.injectedTarget.longitude)})",
            )
            Text("坐标校准偏移: ${"%.1f".format(readiness.calibrationOffsetMeters)} 米")
            FixedPointChannelLine(readiness.gps)
            FixedPointChannelLine(readiness.network)
            FixedPointChannelLine(readiness.fused)
            readiness.checks.forEach { check ->
                Text("${if (check.passed) "[OK]" else "[!]"} ${check.title}: ${check.message}")
            }
            Text(
                "本页面只反映 Android 标准 GPS/Network/Fused 定位链路. " +
                    "本项目不隐藏 mock 状态, 也不保证任何第三方 App 接受模拟定位.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onRestabilize,
                enabled = restabilizeEnabled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("重新稳定目标点")
            }
        }
    }
}

@Composable
private fun FixedPointChannelLine(channel: FixedPointChannelReadiness) {
    Text(
        "${channel.provider}: ${channel.message}, 距离 ${formatDistance(channel.distanceToTargetMeters)}, " +
            "年龄 ${channel.ageMillis?.let { "${it}ms" } ?: "-"}, " +
            "精度 ${channel.accuracyMeters?.let { "${"%.1f".format(it)} 米" } ?: "-"}",
    )
}

private fun fixedPointStateLabel(state: FixedPointReadinessState): String {
    return when (state) {
        FixedPointReadinessState.IDLE -> "未启动"
        FixedPointReadinessState.STARTING -> "正在启动"
        FixedPointReadinessState.BURSTING -> "快速注入中"
        FixedPointReadinessState.SETTLING -> "稳定中"
        FixedPointReadinessState.READY -> "已稳定"
        FixedPointReadinessState.DEGRADED -> "部分可用"
        FixedPointReadinessState.BLOCKED -> "被阻断"
        FixedPointReadinessState.STOPPED -> "已停止"
    }
}

@Composable
private fun DiagnosticList(title: String, items: List<String>) {
    if (items.isEmpty()) {
        Text("$title: 无")
        return
    }
    Text("$title:")
    items.forEach { item ->
        Text("- $item")
    }
}

@Composable
private fun DiagnosticLocationCard(
    title: String,
    location: DiagnosticLocation?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title)
            if (location == null) {
                Text("无可读位置")
            } else {
                Text("坐标: (${location.latitude}, ${location.longitude})")
                Text("精度: ${"%.1f".format(location.accuracyMeters)} 米")
                Text("mock 标记: ${if (location.isMock) "true" else "false"}")
                Text("距注入目标: ${formatDistance(location.distanceToLastInjectionMeters)}")
                Text("时间: ${formatTime(location.timeMillis)}")
            }
        }
    }
}

private fun stateLabel(state: MockState): String {
    return when (state) {
        MockState.Idle -> "空闲"
        MockState.Running -> "运行中"
        MockState.Paused -> "已暂停"
        is MockState.Error -> "异常: ${state.message}"
    }
}

private fun restorationStateLabel(state: RestorationState): String {
    return when (state) {
        RestorationState.NOT_REQUESTED -> "未请求"
        RestorationState.CLEANING -> "正在关闭模拟通道"
        RestorationState.RESTORED -> "模拟通道已关闭"
        RestorationState.ATTENTION_REQUIRED -> "模拟通道关闭待确认"
    }
}

private fun movementModeLabel(mode: MovementMode?): String {
    return when (mode) {
        MovementMode.FIXED -> "固定定位"
        MovementMode.RANDOM_WALK -> "随机步行"
        MovementMode.POINT_TO_POINT_NAV -> "两点导航"
        MovementMode.CUSTOM_ROUTE -> "指定路线"
        null -> "-"
    }
}

private fun movementStateLabel(state: MovementState): String {
    return when (state) {
        MovementState.Idle -> "空闲"
        MovementState.Walking -> "步行中"
        MovementState.ReachedBoundary -> "已到边界"
        MovementState.ReachedDestination -> "已到终点"
        MovementState.Paused -> "已暂停"
        is MovementState.Error -> "异常: ${state.message}"
    }
}

private fun travelModeLabel(mode: TravelMode?): String {
    return when (mode) {
        TravelMode.WALK -> "步行"
        TravelMode.BIKE -> "骑行"
        TravelMode.CAR -> "汽车"
        null -> "-"
    }
}

private fun routeSourceLabel(source: RouteSource?): String {
    return when (source) {
        RouteSource.OSRM -> "OSRM"
        RouteSource.MANUAL -> "手动"
        null -> "-"
    }
}

private fun formatDistance(value: Double?): String {
    if (value == null) {
        return "-"
    }
    return "${"%.1f".format(value)} 米"
}

private fun formatDuration(value: Double?): String {
    if (value == null) {
        return "-"
    }
    val minute = (value / 60.0).coerceAtLeast(0.0)
    return "${"%.1f".format(minute)} 分钟"
}

private fun formatPercent(value: Double?): String {
    if (value == null) {
        return "-"
    }
    return "${"%.1f".format(value.coerceIn(0.0, 100.0))}%"
}

private fun formatTime(timeMillis: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timeMillis))
}

private fun formatTimeOrDash(timeMillis: Long?): String {
    return if (timeMillis == null) {
        "-"
    } else {
        formatTime(timeMillis)
    }
}

private fun formatCoordinate(lat: Double?, lng: Double?): String {
    return if (lat == null || lng == null) "-" else "$lat, $lng"
}

private fun verdictStatusLabel(status: DiagnosticVerdictStatus): String {
    return when (status) {
        DiagnosticVerdictStatus.Ok -> "正常"
        DiagnosticVerdictStatus.Warning -> "需确认"
        DiagnosticVerdictStatus.Blocked -> "阻断"
    }
}
