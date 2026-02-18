package com.aurora.modifypositioning.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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

@Composable
fun OnboardingScreen(
    isMockAppSelected: Boolean,
    missingPermissions: List<String>,
    onOpenDeveloperOptions: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onRefreshStatus: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "首次使用引导",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = "目标：将系统定位固定到纽约时代广场。请先完成以下准备。",
            style = MaterialTheme.typography.bodyLarge,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = "步骤 1：打开开发者选项")
                Button(onClick = onOpenDeveloperOptions) {
                    Text("打开开发者选项")
                }
                Text(
                    text = "在开发者选项里找到“模拟位置信息应用”，选择本 App。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = "步骤 2：授予定位权限")
                Button(onClick = onOpenAppSettings) {
                    Text("打开应用权限页")
                }
                val permissionStatus = if (missingPermissions.isEmpty()) {
                    "权限状态：已满足"
                } else {
                    "权限状态：缺少 ${missingPermissions.joinToString()}"
                }
                Text(permissionStatus, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("当前配置状态")
                Text(
                    text = if (isMockAppSelected) {
                        "模拟位置信息应用：已设置为本 App"
                    } else {
                        "模拟位置信息应用：尚未设置"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onRefreshStatus, contentPadding = PaddingValues(0.dp)) {
                    Text("刷新检测状态")
                }
            }
        }

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("我已完成设置，进入控制台")
        }
    }
}
