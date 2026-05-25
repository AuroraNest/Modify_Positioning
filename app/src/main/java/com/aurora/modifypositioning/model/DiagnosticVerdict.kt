package com.aurora.modifypositioning.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DiagnosticVerdictStatus {
    Ok,
    Warning,
    Blocked,
}

data class DiagnosticVerdict(
    val status: DiagnosticVerdictStatus,
    val title: String,
    val blockers: List<String>,
    val possibleCauses: List<String>,
    val nextSteps: List<String>,
)

data class ThirdPartyValidationChecklist(
    val appName: String,
    val steps: List<String>,
)

fun DiagnosticSnapshot.toDiagnosticVerdict(): DiagnosticVerdict {
    if (!isMockAppSelected) {
        return DiagnosticVerdict(
            status = DiagnosticVerdictStatus.Blocked,
            title = "模拟定位未启用",
            blockers = listOf("开发者选项中未将本应用设为模拟位置信息应用"),
            possibleCauses = emptyList(),
            nextSteps = listOf("打开首次引导, 重新设置模拟位置信息应用", "设置后返回本页刷新诊断"),
        )
    }

    if (missingPermissions.isNotEmpty()) {
        return DiagnosticVerdict(
            status = DiagnosticVerdictStatus.Blocked,
            title = "定位权限不完整",
            blockers = listOf("缺少权限: ${missingPermissions.joinToString()}"),
            possibleCauses = emptyList(),
            nextSteps = listOf("授予缺失权限", "回到控制台重新开始模拟"),
        )
    }

    if (appState != MockState.Running && appState != MockState.Paused) {
        return DiagnosticVerdict(
            status = DiagnosticVerdictStatus.Blocked,
            title = "服务未运行",
            blockers = listOf("前台定位模拟服务当前未运行"),
            possibleCauses = emptyList(),
            nextSteps = listOf("先在控制台开始模拟", "若启动失败, 检查路线是否已规划并确认"),
        )
    }

    if (fusedMockModePending || fusedLastInjectionPending) {
        return DiagnosticVerdict(
            status = DiagnosticVerdictStatus.Warning,
            title = "正在等待 Fused 回调",
            blockers = emptyList(),
            possibleCauses = listOf("Google Play services 的 setMockMode 或 setMockLocation Task 尚未返回"),
            nextSteps = listOf("等待 1-2 秒后刷新诊断", "若长期 pending, 重启目标 app 或停止后重新开始模拟"),
        )
    }

    if (!fusedAvailable) {
        return DiagnosticVerdict(
            status = DiagnosticVerdictStatus.Warning,
            title = "Fused 定位不可用",
            blockers = emptyList(),
            possibleCauses = listOf("设备缺少 Google Play services 或 Fused client 初始化失败"),
            nextSteps = listOf("优先用系统 provider 诊断结果判断", "目标 app 若只读 Fused 定位, 可能无法生效"),
        )
    }

    if (fusedLastError != null) {
        return DiagnosticVerdict(
            status = DiagnosticVerdictStatus.Warning,
            title = "Fused 注入失败",
            blockers = emptyList(),
            possibleCauses = listOf(fusedLastError),
            nextSteps = listOf("停止后重新开始模拟", "确认本应用仍是模拟位置信息应用", "刷新目标 app 定位"),
        )
    }

    val latestInjectionTime = latestInjectionTimeMillis()
    if (latestInjectionTime == null) {
        return DiagnosticVerdict(
            status = DiagnosticVerdictStatus.Warning,
            title = "暂无成功注入记录",
            blockers = emptyList(),
            possibleCauses = listOf("服务刚启动或注入回调尚未成功"),
            nextSteps = listOf("等待诊断刷新", "若仍无记录, 停止后重新开始模拟"),
        )
    }

    if (generatedAtMillis - latestInjectionTime > ACTIVE_INJECTION_STALE_MS) {
        return DiagnosticVerdict(
            status = DiagnosticVerdictStatus.Warning,
            title = "注入记录已变旧",
            blockers = emptyList(),
            possibleCauses = listOf("最近一次成功注入超过 ${ACTIVE_INJECTION_STALE_MS / 1000} 秒"),
            nextSteps = listOf("刷新诊断", "若仍变旧, 停止后重新开始模拟"),
        )
    }

    val overwrite = freshProviderLocations().firstOrNull { !it.isMock }
    if (overwrite != null) {
        return DiagnosticVerdict(
            status = DiagnosticVerdictStatus.Warning,
            title = "可能被真实定位覆盖",
            blockers = emptyList(),
            possibleCauses = listOf("${overwrite.provider} 在新鲜缓存中显示 mock=false"),
            nextSteps = listOf("关闭目标 app 后重新打开", "等待本页诊断刷新后再验证目标 app", "必要时关闭系统高精度真实定位再试"),
        )
    }

    val far = freshProviderLocations().firstOrNull { (it.distanceToLastInjectionMeters ?: 0.0) > FAR_FROM_TARGET_METERS }
    if (far != null) {
        return DiagnosticVerdict(
            status = DiagnosticVerdictStatus.Warning,
            title = "provider 位置偏离目标",
            blockers = emptyList(),
            possibleCauses = listOf("${far.provider} 距注入目标 ${formatDistance(far.distanceToLastInjectionMeters)}"),
            nextSteps = listOf("等待下一轮注入后刷新", "停止后重新开始模拟", "确认目标坐标和校准模式是否正确"),
        )
    }

    val staleCaches = staleProviderLocations()
    return DiagnosticVerdict(
        status = DiagnosticVerdictStatus.Ok,
        title = "系统注入看起来正常",
        blockers = emptyList(),
        possibleCauses = buildList {
            if (staleCaches.isNotEmpty()) {
                add("部分 provider 最近位置是旧缓存, 不作为真实定位覆盖判断")
            }
            add("若第三方 app 仍显示真实位置, 更可能是 anti-mock, app 缓存或服务端校验")
        },
        nextSteps = listOf("按第三方 app 验证清单逐项确认", "诊断正常但目标 app 异常时, 强制停止并重开目标 app"),
    )
}

fun DiagnosticSnapshot.toCopyableDiagnosticReport(): String {
    val verdict = toDiagnosticVerdict()
    return buildString {
        appendLine("Modify Positioning 诊断报告")
        appendLine("生成时间: ${formatTime(generatedAtMillis)}")
        appendLine("结论: ${verdict.title} (${verdict.status})")
        appendLine()
        appendLine("系统环境")
        appendLine("- 模拟位置信息应用: ${if (isMockAppSelected) "已设置" else "未设置"}")
        appendLine("- 权限: ${if (missingPermissions.isEmpty()) "完整" else missingPermissions.joinToString()}")
        appendLine("- GPS: ${if (gpsEnabled) "开启" else "关闭"}")
        appendLine("- Network: ${if (networkEnabled) "开启" else "关闭"}")
        appendLine("- 坐标校准: $calibrationMode")
        appendLine()
        appendLine("服务状态")
        appendLine("- appState: $appState")
        appendLine("- movementMode: $movementMode")
        appendLine("- movementState: $movementState")
        appendLine("- routeMode: ${routeMode ?: "-"}")
        appendLine("- routeProgress: ${routeProgressPercent?.let { "${"%.1f".format(it)}%" } ?: "-"}")
        appendLine()
        appendLine("注入状态")
        appendLine("- lastInjection: ${lastInjection?.let { "${it.provider} ${it.latitude},${it.longitude} @ ${formatTime(it.timeMillis)}" } ?: "-"}")
        appendLine("- fusedAvailable: $fusedAvailable")
        appendLine("- fusedMockModeEnabled: $fusedMockModeEnabled")
        appendLine("- fusedMockModePending: $fusedMockModePending")
        appendLine("- fusedLastInjectionPending: $fusedLastInjectionPending")
        appendLine("- fusedLastInjectionTime: ${fusedLastInjectionTimeMillis?.let(::formatTime) ?: "-"}")
        appendLine("- fusedLastSuccessful: ${formatCoordinate(fusedLastSuccessfulLatitude, fusedLastSuccessfulLongitude)}")
        appendLine("- fusedLastError: ${fusedLastError ?: "-"}")
        appendLine()
        appendProvider("gps", gpsLastKnown)
        appendProvider("network", networkLastKnown)
        appendLine()
        appendList("阻断项", verdict.blockers)
        appendList("可能原因", verdict.possibleCauses)
        appendList("下一步", verdict.nextSteps)
    }.trimEnd()
}

fun thirdPartyValidationChecklists(): List<ThirdPartyValidationChecklist> {
    val sharedSteps = listOf(
        "先开始 mock, 等本页诊断刷新到正常或明确失败",
        "强制停止并重新打开目标 app, 或在目标 app 内手动刷新定位",
        "对比目标 app 显示位置和本页注入目标",
        "若本页正常但目标 app 仍是真实位置, 优先判断为 anti-mock, 缓存或服务端校验",
    )
    return listOf(
        ThirdPartyValidationChecklist("高德", sharedSteps),
        ThirdPartyValidationChecklist("微信共享位置", sharedSteps),
        ThirdPartyValidationChecklist("美团/QQ", sharedSteps),
    )
}

fun DiagnosticSnapshot.realLocationOverwriteSummary(): String {
    val recovery = lastInjection?.recoveryStatus
    if (!recovery.isNullOrBlank()) {
        return "检测到可能被真实定位覆盖: $recovery"
    }
    val freshNonMock = freshProviderLocations().firstOrNull { !it.isMock }
    if (freshNonMock != null) {
        return "检测到 ${freshNonMock.provider} 新鲜位置 mock=false, 可能被真实定位覆盖"
    }
    val freshFar = freshProviderLocations().firstOrNull { (it.distanceToLastInjectionMeters ?: 0.0) > FAR_FROM_TARGET_METERS }
    if (freshFar != null) {
        return "检测到 ${freshFar.provider} 新鲜位置距注入目标较远, 可能被真实定位覆盖"
    }
    val stale = staleProviderLocations().firstOrNull()
    if (stale != null) {
        return "${stale.provider} 最近位置是旧缓存, 暂不判断为真实定位覆盖"
    }
    return "若目标 app 仍显示真实位置, 可能是其读取了其他定位源或服务端校验"
}

private fun DiagnosticSnapshot.latestInjectionTimeMillis(): Long? {
    return listOfNotNull(lastInjection?.timeMillis, fusedLastInjectionTimeMillis).maxOrNull()
}

private fun DiagnosticSnapshot.freshProviderLocations(): List<DiagnosticLocation> {
    return providerLocations().filter { it.isFreshEnough(this) }
}

private fun DiagnosticSnapshot.staleProviderLocations(): List<DiagnosticLocation> {
    return providerLocations().filterNot { it.isFreshEnough(this) }
}

private fun DiagnosticSnapshot.providerLocations(): List<DiagnosticLocation> {
    return listOfNotNull(gpsLastKnown, networkLastKnown)
}

private fun DiagnosticLocation.isFreshEnough(snapshot: DiagnosticSnapshot): Boolean {
    if (timeMillis <= 0L) {
        return false
    }
    if (snapshot.generatedAtMillis - timeMillis <= PROVIDER_CACHE_FRESH_MS) {
        return true
    }
    val latestInjection = snapshot.latestInjectionTimeMillis() ?: return false
    return timeMillis >= latestInjection - PROVIDER_INJECTION_GRACE_MS
}

private fun StringBuilder.appendProvider(label: String, location: DiagnosticLocation?) {
    appendLine("$label provider")
    if (location == null) {
        appendLine("- 无可读位置")
        return
    }
    appendLine("- provider: ${location.provider}")
    appendLine("- coordinate: ${location.latitude},${location.longitude}")
    appendLine("- time: ${formatTime(location.timeMillis)}")
    appendLine("- mock: ${location.isMock}")
    appendLine("- distanceToInjection: ${formatDistance(location.distanceToLastInjectionMeters)}")
}

private fun StringBuilder.appendList(title: String, values: List<String>) {
    appendLine("$title:")
    if (values.isEmpty()) {
        appendLine("- 无")
    } else {
        values.forEach { appendLine("- $it") }
    }
}

private fun formatCoordinate(lat: Double?, lng: Double?): String {
    return if (lat == null || lng == null) "-" else "$lat,$lng"
}

private fun formatDistance(value: Double?): String {
    return value?.let { "${"%.1f".format(it)} 米" } ?: "-"
}

private fun formatTime(timeMillis: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timeMillis))
}

private const val PROVIDER_CACHE_FRESH_MS = 15_000L
private const val PROVIDER_INJECTION_GRACE_MS = 2_000L
private const val ACTIVE_INJECTION_STALE_MS = 20_000L
private const val FAR_FROM_TARGET_METERS = 75.0
