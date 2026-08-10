package com.aurora.modifypositioning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aurora.modifypositioning.model.MockState
import com.aurora.modifypositioning.model.TargetLocation

@Composable
fun MainControlScreen(
    state: MockState,
    statusText: String,
    target: TargetLocation,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenDiagnostic: () -> Unit,
) {
    val stateLabel = when (state) {
        MockState.Idle -> "空闲"
        MockState.Running -> "运行中"
        MockState.Paused -> "已暂停"
        is MockState.Error -> "异常"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF2F2F7), Color(0xFFF7F7FA)),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "控制台",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("当前状态: $stateLabel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("目标位置")
                    Text(target.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = String.format("纬度 %.5f, 经度 %.5f", target.latitude, target.longitude),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("服务信息", style = MaterialTheme.typography.titleMedium)
                    Text(statusText, style = MaterialTheme.typography.bodyMedium)
                    Text("增强注入模式: 已启用", style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (state is MockState.Error) {
                Text(
                    text = "异常详情: ${state.message}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = onStart, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                    Text("准备固定点定位")
                }
                FilledTonalButton(onClick = onPause, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                    Text("暂停")
                }
                OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                    Text("停止")
                }
            }

            TextButton(onClick = onOpenGuide) {
                Text("返回首次引导")
            }

            TextButton(onClick = onOpenDiagnostic) {
                Text("打开诊断页面")
            }
        }
    }
}
