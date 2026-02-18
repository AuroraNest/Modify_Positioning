package com.aurora.modifypositioning.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
) {
    val stateLabel = when (state) {
        MockState.Idle -> "空闲"
        MockState.Running -> "运行中"
        MockState.Paused -> "已暂停"
        is MockState.Error -> "异常"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "定位修改控制台",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("目标位置")
                Text(target.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "(${target.latitude}, ${target.longitude})",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("当前状态：$stateLabel", style = MaterialTheme.typography.titleMedium)
                Text(statusText, style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (state is MockState.Error) {
            Text(
                text = "异常详情：${state.message}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                Text("开始模拟")
            }
            OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f)) {
                Text("暂停")
            }
            OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) {
                Text("停止")
            }
        }

        TextButton(onClick = onOpenGuide) {
            Text("返回首次引导")
        }
    }
}
