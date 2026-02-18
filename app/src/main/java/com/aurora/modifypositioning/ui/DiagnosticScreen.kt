package com.aurora.modifypositioning.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aurora.modifypositioning.model.DiagnosticLocation
import com.aurora.modifypositioning.model.DiagnosticSnapshot
import com.aurora.modifypositioning.model.MockState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticScreen(
    snapshot: DiagnosticSnapshot,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "诊断页面",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("系统环境")
                Text("检测时间：${formatTime(snapshot.generatedAtMillis)}")
                Text("模拟位置信息应用：${if (snapshot.isMockAppSelected) "已设置" else "未设置"}")
                Text("GPS 开关：${if (snapshot.gpsEnabled) "开启" else "关闭"}")
                Text("网络定位开关：${if (snapshot.networkEnabled) "开启" else "关闭"}")
                Text(
                    if (snapshot.missingPermissions.isEmpty()) {
                        "权限：完整"
                    } else {
                        "权限缺失：${snapshot.missingPermissions.joinToString()}"
                    },
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("应用运行状态")
                Text("服务状态：${stateLabel(snapshot.appState)}")
                val injection = snapshot.lastInjection
                if (injection == null) {
                    Text("最近注入：暂无")
                } else {
                    Text(
                        "最近注入：${injection.provider} @ (${injection.latitude}, ${injection.longitude})",
                    )
                    Text("注入精度：${"%.1f".format(injection.accuracyMeters)} 米")
                    Text("注入时间：${formatTime(injection.timeMillis)}")
                }
            }
        }

        DiagnosticLocationCard(title = "GPS 最近位置", location = snapshot.gpsLastKnown)
        DiagnosticLocationCard(title = "Network 最近位置", location = snapshot.networkLastKnown)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("判断建议")
                Text("1) 若本页显示 mock=true，但高德仍是真实位置，说明高德做了反模拟拦截。")
                Text("2) 若本页注入坐标和最近位置都不变，优先检查“模拟位置信息应用”是否仍为本 App。")
                Text("3) 若服务状态异常，请先停止再开始，并保持应用后台不被清理。")
            }
        }

        Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
            Text("刷新诊断")
        }

        TextButton(onClick = onBack) {
            Text("返回控制台")
        }
    }
}

@Composable
private fun DiagnosticLocationCard(
    title: String,
    location: DiagnosticLocation?,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title)
            if (location == null) {
                Text("无可读位置")
            } else {
                Text("坐标：(${location.latitude}, ${location.longitude})")
                Text("精度：${"%.1f".format(location.accuracyMeters)} 米")
                Text("mock 标记：${if (location.isMock) "true" else "false"}")
                Text("时间：${formatTime(location.timeMillis)}")
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

private fun formatTime(timeMillis: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timeMillis))
}
