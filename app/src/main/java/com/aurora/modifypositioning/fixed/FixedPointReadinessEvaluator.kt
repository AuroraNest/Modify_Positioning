package com.aurora.modifypositioning.fixed

import com.aurora.modifypositioning.location.CompositeInjectorStatus
import com.aurora.modifypositioning.location.InjectorState
import com.aurora.modifypositioning.location.distanceMeters
import com.aurora.modifypositioning.model.DiagnosticLocation
import com.aurora.modifypositioning.model.FixedPointChannelReadiness
import com.aurora.modifypositioning.model.FixedPointCheck
import com.aurora.modifypositioning.model.FixedPointCheckSeverity
import com.aurora.modifypositioning.model.FixedPointReadinessSnapshot
import com.aurora.modifypositioning.model.FixedPointReadinessState
import com.aurora.modifypositioning.model.InjectionReport
import com.aurora.modifypositioning.model.MockState
import com.aurora.modifypositioning.model.MovementMode
import com.aurora.modifypositioning.model.TargetLocation
import kotlin.math.max

data class FixedPointReadinessInput(
    val nowElapsedRealtimeMillis: Long,
    val appState: MockState,
    val movementMode: MovementMode,
    val rawTarget: TargetLocation,
    val injectedTarget: TargetLocation,
    val session: FixedPointSessionSnapshot,
    val isMockAppSelected: Boolean,
    val missingPermissions: List<String>,
    val systemLocationEnabled: Boolean,
    val batteryIgnoringOptimizations: Boolean,
    val gpsEnabled: Boolean,
    val networkEnabled: Boolean,
    val gpsLastKnown: DiagnosticLocation?,
    val networkLastKnown: DiagnosticLocation?,
    val fusedAvailable: Boolean,
    val fusedMockModeEnabled: Boolean,
    val fusedMockModePending: Boolean,
    val fusedLastInjectionPending: Boolean,
    val fusedLastInjectionElapsedRealtimeMillis: Long?,
    val fusedLastSuccessfulLatitude: Double?,
    val fusedLastSuccessfulLongitude: Double?,
    val fusedLastSuccessfulAccuracyMeters: Float?,
    val injectorStatus: CompositeInjectorStatus,
    val lastInjection: InjectionReport?,
)

object FixedPointReadinessEvaluator {
    private const val GPS_MAX_AGE_MILLIS = 2_500L
    private const val NETWORK_MAX_AGE_MILLIS = 4_000L
    private const val FUSED_MAX_AGE_MILLIS = 3_000L
    private const val GPS_DISTANCE_LIMIT_METERS = 35.0
    private const val NETWORK_DISTANCE_LIMIT_METERS = 120.0
    private const val FUSED_DISTANCE_LIMIT_METERS = 50.0
    private const val MIN_STABLE_DURATION_MILLIS = 10_000L
    private const val MIN_WARMUP_DURATION_MILLIS = 15_000L
    private const val NO_RESULT_BLOCK_AFTER_MILLIS = 5_000L

    fun evaluate(input: FixedPointReadinessInput): FixedPointReadinessSnapshot? {
        if (input.movementMode != MovementMode.FIXED) {
            return null
        }

        val gps = providerChannel(
            provider = "GPS",
            enabled = input.gpsEnabled,
            location = input.gpsLastKnown,
            target = input.injectedTarget,
            nowElapsedRealtimeMillis = input.nowElapsedRealtimeMillis,
            maxAgeMillis = GPS_MAX_AGE_MILLIS,
            distanceLimitMeters = GPS_DISTANCE_LIMIT_METERS,
            accuracyMarginMeters = 10.0,
        )
        val network = providerChannel(
            provider = "Network",
            enabled = input.networkEnabled,
            location = input.networkLastKnown,
            target = input.injectedTarget,
            nowElapsedRealtimeMillis = input.nowElapsedRealtimeMillis,
            maxAgeMillis = NETWORK_MAX_AGE_MILLIS,
            distanceLimitMeters = NETWORK_DISTANCE_LIMIT_METERS,
            accuracyMarginMeters = 30.0,
        )
        val fused = fusedChannel(input)
        val serviceActive = input.appState == MockState.Running || input.appState == MockState.Paused
        val startedAt = input.session.startedAtElapsedRealtimeMillis
        val warmupDurationMillis = startedAt
            ?.let { (input.nowElapsedRealtimeMillis - it).coerceAtLeast(0L) }
            ?: 0L
        val gpsReady = gps.fresh && gps.nearTarget && gps.isMock == true
        val networkReady = network.fresh && network.nearTarget && network.isMock == true
        val fusedReady = fused.fresh && fused.nearTarget && fused.stable
        val nonMockOverwrite = listOf(gps, network).any { it.fresh && it.isMock == false }
        val stableCandidate = gpsReady && (fusedReady || networkReady) && !nonMockOverwrite
        val stableSince = if (stableCandidate) {
            input.session.stableSinceElapsedRealtimeMillis ?: input.nowElapsedRealtimeMillis
        } else {
            null
        }
        val stableDurationMillis = stableSince
            ?.let { (input.nowElapsedRealtimeMillis - it).coerceAtLeast(0L) }
            ?: 0L
        val stableLongEnough = stableDurationMillis >= MIN_STABLE_DURATION_MILLIS
        val warmupComplete = warmupDurationMillis >= MIN_WARMUP_DURATION_MILLIS
        val anyFreshNear = listOf(gps, network, fused).any { it.fresh && it.nearTarget }
        val hardBlock = when {
            !input.isMockAppSelected -> "未选择本 App 为模拟位置信息应用"
            input.missingPermissions.isNotEmpty() -> "定位权限不完整"
            !input.systemLocationEnabled -> "系统定位服务未开启"
            input.injectorStatus.overallState == InjectorState.FAILED -> "所有定位注入通道均失败"
            serviceActive && warmupDurationMillis >= NO_RESULT_BLOCK_AFTER_MILLIS &&
                input.lastInjection == null && input.fusedLastInjectionElapsedRealtimeMillis == null -> {
                "服务启动后仍没有定位注入结果"
            }
            serviceActive && warmupDurationMillis >= NO_RESULT_BLOCK_AFTER_MILLIS &&
                !anyFreshNear -> "GPS, Network 和 Fused 均没有新鲜的目标附近结果"
            else -> null
        }

        val state = when {
            input.appState == MockState.Idle -> {
                if (input.session.phase == FixedPointReadinessState.STOPPED) {
                    FixedPointReadinessState.STOPPED
                } else {
                    FixedPointReadinessState.IDLE
                }
            }
            hardBlock != null -> FixedPointReadinessState.BLOCKED
            input.session.phase == FixedPointReadinessState.STARTING -> FixedPointReadinessState.STARTING
            input.session.phase == FixedPointReadinessState.BURSTING -> FixedPointReadinessState.BURSTING
            !serviceActive -> FixedPointReadinessState.BLOCKED
            stableLongEnough && warmupComplete && fusedReady -> FixedPointReadinessState.READY
            stableLongEnough && warmupComplete -> FixedPointReadinessState.DEGRADED
            stableCandidate -> FixedPointReadinessState.SETTLING
            warmupDurationMillis < MIN_WARMUP_DURATION_MILLIS -> FixedPointReadinessState.SETTLING
            anyFreshNear -> FixedPointReadinessState.DEGRADED
            else -> FixedPointReadinessState.DEGRADED
        }
        val recommendedWaitMillis = if (state == FixedPointReadinessState.READY) {
            0L
        } else {
            max(
                (MIN_WARMUP_DURATION_MILLIS - warmupDurationMillis).coerceAtLeast(0L),
                (MIN_STABLE_DURATION_MILLIS - stableDurationMillis).coerceAtLeast(0L),
            )
        }
        val checks = buildChecks(
            input = input,
            gps = gps,
            network = network,
            fused = fused,
            serviceActive = serviceActive,
            nonMockOverwrite = nonMockOverwrite,
            stableLongEnough = stableLongEnough,
            warmupComplete = warmupComplete,
        )
        val score = readinessScore(
            input = input,
            serviceActive = serviceActive,
            gpsReady = gpsReady,
            networkReady = networkReady,
            fusedReady = fusedReady,
            stableLongEnough = stableLongEnough,
            nonMockOverwrite = nonMockOverwrite,
        )

        return FixedPointReadinessSnapshot(
            state = state,
            score = score,
            generatedAtElapsedRealtimeMillis = input.nowElapsedRealtimeMillis,
            rawTarget = input.rawTarget,
            injectedTarget = input.injectedTarget,
            calibrationOffsetMeters = distanceMeters(
                startLatitude = input.rawTarget.latitude,
                startLongitude = input.rawTarget.longitude,
                endLatitude = input.injectedTarget.latitude,
                endLongitude = input.injectedTarget.longitude,
            ),
            stableSinceElapsedRealtimeMillis = stableSince,
            recommendedWaitMillis = recommendedWaitMillis,
            gps = gps,
            network = network,
            fused = fused,
            checks = checks,
            summary = readinessSummary(state, hardBlock, recommendedWaitMillis, fusedReady),
        )
    }

    private fun providerChannel(
        provider: String,
        enabled: Boolean,
        location: DiagnosticLocation?,
        target: TargetLocation,
        nowElapsedRealtimeMillis: Long,
        maxAgeMillis: Long,
        distanceLimitMeters: Double,
        accuracyMarginMeters: Double,
    ): FixedPointChannelReadiness {
        val elapsedRealtimeMillis = location?.elapsedRealtimeNanos
            ?.takeIf { it > 0L }
            ?.div(1_000_000L)
        val ageMillis = elapsedRealtimeMillis
            ?.let { (nowElapsedRealtimeMillis - it).coerceAtLeast(0L) }
        val distanceMeters = location?.let {
            distanceMeters(
                startLatitude = it.latitude,
                startLongitude = it.longitude,
                endLatitude = target.latitude,
                endLongitude = target.longitude,
            )
        }
        val fresh = enabled && ageMillis != null && ageMillis <= maxAgeMillis
        val allowedDistance = location?.let {
            max(distanceLimitMeters, it.accuracyMeters.toDouble() + accuracyMarginMeters)
        } ?: distanceLimitMeters
        val near = enabled && distanceMeters != null && distanceMeters <= allowedDistance
        val stable = fresh && near && location?.isMock == true
        val message = when {
            !enabled -> "$provider provider 未开启"
            location == null -> "$provider 暂无最近位置"
            ageMillis == null -> "$provider 缺少 monotonic 时间戳"
            !fresh -> "$provider 最近位置已过期"
            location.isMock.not() -> "$provider 最近位置 mock=false, 可能被真实定位覆盖"
            !near -> "$provider 最近位置距目标过远"
            else -> "$provider 已新鲜接近目标"
        }
        return FixedPointChannelReadiness(
            provider = provider,
            available = enabled && location != null,
            fresh = fresh,
            nearTarget = near,
            stable = stable,
            isMock = location?.isMock,
            distanceToTargetMeters = distanceMeters,
            ageMillis = ageMillis,
            accuracyMeters = location?.accuracyMeters,
            message = message,
        )
    }

    private fun fusedChannel(input: FixedPointReadinessInput): FixedPointChannelReadiness {
        val ageMillis = input.fusedLastInjectionElapsedRealtimeMillis
            ?.let { (input.nowElapsedRealtimeMillis - it).coerceAtLeast(0L) }
        val distance = if (
            input.fusedLastSuccessfulLatitude != null && input.fusedLastSuccessfulLongitude != null
        ) {
            distanceMeters(
                startLatitude = input.fusedLastSuccessfulLatitude,
                startLongitude = input.fusedLastSuccessfulLongitude,
                endLatitude = input.injectedTarget.latitude,
                endLongitude = input.injectedTarget.longitude,
            )
        } else {
            null
        }
        val fresh = input.fusedAvailable && ageMillis != null && ageMillis <= FUSED_MAX_AGE_MILLIS
        val near = input.fusedAvailable && distance != null && distance <= FUSED_DISTANCE_LIMIT_METERS
        val stable = fresh && near && input.fusedMockModeEnabled &&
            !input.fusedMockModePending && !input.fusedLastInjectionPending
        val message = when {
            !input.fusedAvailable -> "Fused 不可用, 固定点只能以降级状态运行"
            input.fusedMockModePending -> "Fused mock mode 正在等待"
            input.fusedLastInjectionPending -> "Fused setMockLocation 正在等待"
            !input.fusedMockModeEnabled -> "Fused mock mode 未开启"
            ageMillis == null -> "Fused 暂无成功位置"
            !fresh -> "Fused 最近成功位置已过期"
            !near -> "Fused 最近成功位置距目标过远"
            else -> "Fused 已新鲜接近目标"
        }
        return FixedPointChannelReadiness(
            provider = "Fused",
            available = input.fusedAvailable,
            fresh = fresh,
            nearTarget = near,
            stable = stable,
            isMock = null,
            distanceToTargetMeters = distance,
            ageMillis = ageMillis,
            accuracyMeters = input.fusedLastSuccessfulAccuracyMeters,
            message = message,
        )
    }

    private fun buildChecks(
        input: FixedPointReadinessInput,
        gps: FixedPointChannelReadiness,
        network: FixedPointChannelReadiness,
        fused: FixedPointChannelReadiness,
        serviceActive: Boolean,
        nonMockOverwrite: Boolean,
        stableLongEnough: Boolean,
        warmupComplete: Boolean,
    ): List<FixedPointCheck> {
        return listOf(
            check("mock_app", "Mock App", input.isMockAppSelected, "已选择本 App", "未选择本 App"),
            check("permissions", "定位权限", input.missingPermissions.isEmpty(), "权限完整", "缺少定位权限"),
            check("system_location", "系统定位", input.systemLocationEnabled, "系统定位已开启", "系统定位未开启"),
            check("service", "前台服务", serviceActive, "固定点服务运行中", "固定点服务未运行"),
            channelCheck("gps", gps, required = true),
            channelCheck("network", network, required = false),
            channelCheck("fused", fused, required = false),
            check("overwrite", "真实定位覆盖", !nonMockOverwrite, "未发现新鲜 non-mock 覆盖", "发现新鲜 mock=false 位置"),
            check("stable", "连续稳定", stableLongEnough, "已连续稳定至少 10 秒", "连续稳定不足 10 秒"),
            check("warmup", "预热时间", warmupComplete, "已预热至少 15 秒", "预热不足 15 秒"),
            FixedPointCheck(
                id = "battery",
                title = "电池优化",
                message = if (input.batteryIgnoringOptimizations) {
                    "后台运行不受系统电池优化限制"
                } else {
                    "电池优化可能限制长时间后台运行"
                },
                severity = if (input.batteryIgnoringOptimizations) {
                    FixedPointCheckSeverity.OK
                } else {
                    FixedPointCheckSeverity.WARNING
                },
                passed = input.batteryIgnoringOptimizations,
            ),
        )
    }

    private fun check(
        id: String,
        title: String,
        passed: Boolean,
        passedMessage: String,
        failedMessage: String,
    ): FixedPointCheck {
        return FixedPointCheck(
            id = id,
            title = title,
            message = if (passed) passedMessage else failedMessage,
            severity = if (passed) FixedPointCheckSeverity.OK else FixedPointCheckSeverity.BLOCKER,
            passed = passed,
        )
    }

    private fun channelCheck(
        id: String,
        channel: FixedPointChannelReadiness,
        required: Boolean,
    ): FixedPointCheck {
        val passed = channel.stable
        return FixedPointCheck(
            id = id,
            title = channel.provider,
            message = channel.message,
            severity = when {
                passed -> FixedPointCheckSeverity.OK
                required -> FixedPointCheckSeverity.BLOCKER
                else -> FixedPointCheckSeverity.WARNING
            },
            passed = passed,
        )
    }

    private fun readinessScore(
        input: FixedPointReadinessInput,
        serviceActive: Boolean,
        gpsReady: Boolean,
        networkReady: Boolean,
        fusedReady: Boolean,
        stableLongEnough: Boolean,
        nonMockOverwrite: Boolean,
    ): Int {
        var score = 0
        if (input.isMockAppSelected) score += 10
        if (input.missingPermissions.isEmpty()) score += 10
        if (input.systemLocationEnabled) score += 10
        if (serviceActive) score += 5
        if (gpsReady) score += 20
        if (networkReady) score += 10
        if (fusedReady) score += 15
        if (stableLongEnough) score += 10
        if (!nonMockOverwrite) score += 5
        if (input.batteryIgnoringOptimizations) score += 5
        return score.coerceIn(0, 100)
    }

    private fun readinessSummary(
        state: FixedPointReadinessState,
        hardBlock: String?,
        recommendedWaitMillis: Long,
        fusedReady: Boolean,
    ): String {
        return when (state) {
            FixedPointReadinessState.IDLE -> "固定点服务尚未启动"
            FixedPointReadinessState.STARTING -> "正在初始化固定点定位通道"
            FixedPointReadinessState.BURSTING -> "正在快速写入固定点样本"
            FixedPointReadinessState.SETTLING -> "继续等待约 ${(recommendedWaitMillis + 999L) / 1_000L} 秒"
            FixedPointReadinessState.READY -> "Android 标准定位链路已连续稳定到目标附近"
            FixedPointReadinessState.DEGRADED -> if (fusedReady) {
                "部分标准定位通道不稳定, 可查看检查项"
            } else {
                "Fused 未达到稳定条件, 当前为部分可用"
            }
            FixedPointReadinessState.BLOCKED -> hardBlock ?: "固定点准备被阻断"
            FixedPointReadinessState.STOPPED -> "固定点服务已停止"
        }
    }
}
