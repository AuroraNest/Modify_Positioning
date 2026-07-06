package com.aurora.modifypositioning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    val permissionsReady = missingPermissions.isEmpty()
    val background = Brush.verticalGradient(
        colors = listOf(Color(0xFFF8FAF8), Color(0xFFEAF1F3)),
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
                color = Color(0xFF17212B),
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "准备控制台",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "完成系统授权后, 进入地图选点并启动虚拟定位.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFC8D6DC),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ReadinessChip(
                            label = "模拟 App",
                            ready = isMockAppSelected,
                            modifier = Modifier.weight(1f),
                        )
                        ReadinessChip(
                            label = "定位权限",
                            ready = permissionsReady,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            SetupStepCard(
                title = "选择模拟位置 App",
                status = if (isMockAppSelected) "已设置为本 App" else "尚未设置",
                detail = "开发者选项 / 模拟位置信息应用 / Modify Positioning",
                ready = isMockAppSelected,
                actionLabel = "打开开发者选项",
                onAction = onOpenDeveloperOptions,
            )

            SetupStepCard(
                title = "授予定位权限",
                status = if (permissionsReady) {
                    "权限已满足"
                } else {
                    "缺少: ${missingPermissions.joinToString()}"
                },
                detail = "允许位置权限后, 后台服务才能持续注入定位.",
                ready = permissionsReady,
                actionLabel = "打开应用权限页",
                onAction = onOpenAppSettings,
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text("检测状态", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = if (isMockAppSelected && permissionsReady) {
                                "已准备好进入控制台"
                            } else {
                                "完成上方项目后刷新一次"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(onClick = onRefreshStatus) {
                        Text("刷新")
                    }
                }
            }

            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("进入地图控制台")
            }
        }
    }
}

@Composable
private fun ReadinessChip(
    label: String,
    ready: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (ready) Color(0xFF203D36) else Color(0xFF303941),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(label, color = Color(0xFF9EAFB8), style = MaterialTheme.typography.labelSmall)
            Text(
                text = if (ready) "已完成" else "待处理",
                color = if (ready) Color(0xFF36C98A) else Color(0xFFFFC857),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SetupStepCard(
    title: String,
    status: String,
    detail: String,
    ready: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusBadge(ready = ready)
            }
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = onAction, shape = RoundedCornerShape(8.dp)) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(ready: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (ready) Color(0xFFE1F7EC) else Color(0xFFFFF3D7),
    ) {
        Text(
            text = if (ready) "OK" else "待配置",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = if (ready) Color(0xFF146C43) else Color(0xFF7A4A00),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
