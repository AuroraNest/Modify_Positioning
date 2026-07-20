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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(
    isMockAppSelected: Boolean,
    isSystemLocationEnabled: Boolean,
    missingLocationPermissions: List<String>,
    areNotificationsEnabled: Boolean,
    isIgnoringBatteryOptimizations: Boolean,
    onOpenDeveloperOptions: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onRefreshStatus: () -> Unit,
    onContinue: () -> Unit,
) {
    val locationPermissionsReady = missingLocationPermissions.isEmpty()
    val requiredReady = onboardingRequiredStepsReady(
        isMockAppSelected = isMockAppSelected,
        isSystemLocationEnabled = isSystemLocationEnabled,
        missingLocationPermissions = missingLocationPermissions,
    )
    val recommendedReady = areNotificationsEnabled && isIgnoringBatteryOptimizations
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
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "设置",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "完成 3 项必需设置后, 即可进入地图并启动虚拟定位.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            label = "系统定位",
                            ready = isSystemLocationEnabled,
                            modifier = Modifier.weight(1f),
                        )
                        ReadinessChip(
                            label = "精确位置",
                            ready = locationPermissionsReady,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Text(
                text = "必须完成",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )

            SetupStepCard(
                title = "选择模拟位置 App",
                status = if (isMockAppSelected) "已设置为本 App" else "尚未设置",
                detail = "若没有开发者选项, 先在关于手机连续点击系统版本或版本号 7 次. 然后选择模拟位置信息应用 / Modify Positioning.",
                ready = isMockAppSelected,
                actionLabel = "打开开发者选项",
                onAction = onOpenDeveloperOptions,
            )

            SetupStepCard(
                title = "开启系统定位",
                status = if (isSystemLocationEnabled) "系统定位已开启" else "系统定位已关闭",
                detail = "目标 App 需要通过系统定位服务读取 GPS / Network / Fused 位置.",
                ready = isSystemLocationEnabled,
                actionLabel = "打开定位设置",
                onAction = onOpenLocationSettings,
            )

            SetupStepCard(
                title = "允许精确位置",
                status = if (locationPermissionsReady) {
                    "精确位置权限已允许"
                } else {
                    "尚未授予定位权限"
                },
                detail = "在应用权限中允许定位, 并开启使用精确位置.",
                ready = locationPermissionsReady,
                actionLabel = "打开权限设置",
                onAction = onOpenAppSettings,
            )

            Text(
                text = "建议设置",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )

            SetupStepCard(
                title = "显示运行通知",
                status = if (areNotificationsEnabled) "通知已开启" else "通知未开启",
                detail = "用于查看持续定位状态和前台服务提示. 不开启也不会阻止定位.",
                ready = areNotificationsEnabled,
                required = false,
                actionLabel = "打开通知设置",
                onAction = onOpenNotificationSettings,
            )

            SetupStepCard(
                title = "放宽电池限制",
                status = if (isIgnoringBatteryOptimizations) "电池限制已放宽" else "仍受系统电池优化",
                detail = "长时间运行或切换到微信, 美团后容易被清理时, 建议设为不限制.",
                ready = isIgnoringBatteryOptimizations,
                required = false,
                actionLabel = "打开电池设置",
                onAction = onOpenBatterySettings,
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
                            text = if (requiredReady) {
                                if (recommendedReady) {
                                    "全部设置已完成"
                                } else {
                                    "必需项已完成, 建议项可稍后设置"
                                }
                            } else {
                                "完成 3 项必需设置后刷新"
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
                enabled = !locationPermissionsReady || requiredReady,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_continue"),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(if (locationPermissionsReady) "进入地图控制台" else "授予精确位置权限")
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
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            Text(
                text = if (ready) "已完成" else "待处理",
                color = if (ready) Color(0xFF34C759) else Color(0xFFFF9F0A),
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
    required: Boolean = true,
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
                StatusBadge(ready = ready, required = required)
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
private fun StatusBadge(ready: Boolean, required: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = when {
            ready -> Color(0xFFE1F7EC)
            required -> Color(0xFFFFF3D7)
            else -> Color(0xFFE8F0FE)
        },
    ) {
        Text(
            text = when {
                ready -> "OK"
                required -> "待配置"
                else -> "建议开启"
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = when {
                ready -> Color(0xFF146C43)
                required -> Color(0xFF7A4A00)
                else -> Color(0xFF2457A6)
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

internal fun onboardingRequiredStepsReady(
    isMockAppSelected: Boolean,
    isSystemLocationEnabled: Boolean,
    missingLocationPermissions: List<String>,
): Boolean {
    return isMockAppSelected && isSystemLocationEnabled && missingLocationPermissions.isEmpty()
}
