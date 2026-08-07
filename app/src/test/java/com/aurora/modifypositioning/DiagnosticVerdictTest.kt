package com.aurora.modifypositioning

import com.aurora.modifypositioning.location.InjectorState
import com.aurora.modifypositioning.model.CoordinateCalibrationMode
import com.aurora.modifypositioning.model.DiagnosticInjectorStatus
import com.aurora.modifypositioning.model.DiagnosticLocation
import com.aurora.modifypositioning.model.DiagnosticSnapshot
import com.aurora.modifypositioning.model.DiagnosticVerdictStatus
import com.aurora.modifypositioning.model.InjectionReport
import com.aurora.modifypositioning.model.MockState
import com.aurora.modifypositioning.model.MovementMode
import com.aurora.modifypositioning.model.MovementState
import com.aurora.modifypositioning.model.realLocationOverwriteSummary
import com.aurora.modifypositioning.model.toCopyableDiagnosticReport
import com.aurora.modifypositioning.model.toDiagnosticVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticVerdictTest {

    @Test
    fun verdict_prioritizesMockAppSelectionBlocker() {
        val verdict = snapshot(isMockAppSelected = false).toDiagnosticVerdict()

        assertEquals(DiagnosticVerdictStatus.Blocked, verdict.status)
        assertEquals("模拟定位未启用", verdict.title)
        assertTrue(verdict.blockers.first().contains("模拟位置信息应用"))
    }

    @Test
    fun verdict_reportsFusedPendingBeforeSuccess() {
        val verdict = snapshot(
            fusedMockModePending = true,
            fusedMockModeEnabled = false,
            fusedLastInjectionTimeMillis = null,
        ).toDiagnosticVerdict()

        assertEquals(DiagnosticVerdictStatus.Warning, verdict.status)
        assertEquals("正在等待 Fused 回调", verdict.title)
    }

    @Test
    fun verdict_blocksFailedInjectors_beforeServiceFallbackVerdict() {
        val verdict = snapshot(
            injectorOverallState = InjectorState.FAILED,
            appState = MockState.Error("all failed"),
        ).toDiagnosticVerdict()

        assertEquals(DiagnosticVerdictStatus.Blocked, verdict.status)
        assertEquals("定位通道全部失败", verdict.title)
    }

    @Test
    fun verdict_warnsWhenOnlySomeInjectorsRun() {
        val verdict = snapshot(injectorOverallState = InjectorState.PARTIAL).toDiagnosticVerdict()

        assertEquals(DiagnosticVerdictStatus.Warning, verdict.status)
        assertEquals("部分定位通道不可用", verdict.title)
    }

    @Test
    fun verdict_warnsWhenOnlyProviderReadingIsStale() {
        val staleGps = DiagnosticLocation(
            provider = "gps",
            latitude = 30.0,
            longitude = 120.0,
            accuracyMeters = 5f,
            timeMillis = 1_000L,
            isMock = false,
            distanceToLastInjectionMeters = 500.0,
        )

        val snapshot = snapshot(gpsLastKnown = staleGps)
        val verdict = snapshot.toDiagnosticVerdict()

        assertEquals(DiagnosticVerdictStatus.Warning, verdict.status)
        assertTrue(verdict.title.contains("未确认"))
        assertTrue(snapshot.realLocationOverwriteSummary().contains("旧缓存"))
        assertTrue(verdict.possibleCauses.any { it.contains("无新鲜 provider 证据") })
    }

    @Test
    fun verdict_warnsWhenProviderReadingsAreMissing() {
        val verdict = snapshot(gpsLastKnown = null).toDiagnosticVerdict()

        assertEquals(DiagnosticVerdictStatus.Warning, verdict.status)
        assertTrue(verdict.title.contains("未确认"))
        assertTrue(verdict.possibleCauses.any { it.contains("无新鲜 provider 证据") })
    }

    @Test
    fun verdict_reportsFreshNonMockOverwrite() {
        val freshNetwork = DiagnosticLocation(
            provider = "network",
            latitude = 30.0,
            longitude = 120.0,
            accuracyMeters = 10f,
            timeMillis = NOW - 1_000L,
            isMock = false,
            distanceToLastInjectionMeters = 500.0,
        )

        val verdict = snapshot(networkLastKnown = freshNetwork).toDiagnosticVerdict()

        assertEquals(DiagnosticVerdictStatus.Warning, verdict.status)
        assertEquals("可能被真实定位覆盖", verdict.title)
        assertTrue(verdict.possibleCauses.first().contains("network"))
    }

    @Test
    fun copyReport_includesVerdictAndChecklistRelevantFields() {
        val report = snapshot().toCopyableDiagnosticReport()

        assertTrue(report.contains("Modify Positioning 诊断报告"))
        assertTrue(report.contains("结论: 系统注入看起来正常"))
        assertTrue(report.contains("device: Aurora Test Device"))
        assertTrue(report.contains("injectorOverall: RUNNING"))
        assertTrue(report.contains("fusedMockModePending: false"))
        assertTrue(report.contains("fusedLastSuccessful: 31.0,121.0"))
    }

    private fun snapshot(
        isMockAppSelected: Boolean = true,
        missingPermissions: List<String> = emptyList(),
        gpsLastKnown: DiagnosticLocation? = FRESH_MOCK_GPS,
        networkLastKnown: DiagnosticLocation? = null,
        fusedMockModeEnabled: Boolean = true,
        fusedMockModePending: Boolean = false,
        fusedLastInjectionPending: Boolean = false,
        fusedLastInjectionTimeMillis: Long? = NOW - 1_000L,
        fusedLastError: String? = null,
        injectorOverallState: InjectorState = InjectorState.RUNNING,
        appState: MockState = MockState.Running,
    ): DiagnosticSnapshot {
        val injectorStatuses = if (injectorOverallState == InjectorState.PARTIAL) {
            listOf(
                diagnosticInjector("GPS / Network", InjectorState.RUNNING),
                diagnosticInjector("Fused", InjectorState.FAILED),
            )
        } else {
            listOf(
                diagnosticInjector("GPS / Network", injectorOverallState),
                diagnosticInjector("Fused", injectorOverallState),
            )
        }
        return DiagnosticSnapshot(
            generatedAtMillis = NOW,
            deviceManufacturer = "Aurora",
            deviceModel = "Test Device",
            androidVersion = "15",
            androidApiLevel = 35,
            appVersionName = "1.0-test",
            appVersionCode = 1L,
            isMockAppSelected = isMockAppSelected,
            missingPermissions = missingPermissions,
            gpsEnabled = true,
            networkEnabled = true,
            gpsLastKnown = gpsLastKnown,
            networkLastKnown = networkLastKnown,
            fusedAvailable = true,
            fusedMockModeEnabled = fusedMockModeEnabled,
            fusedMockModePending = fusedMockModePending,
            fusedLastInjectionPending = fusedLastInjectionPending,
            fusedLastInjectionTimeMillis = fusedLastInjectionTimeMillis,
            fusedLastSuccessfulLatitude = 31.0,
            fusedLastSuccessfulLongitude = 121.0,
            fusedLastError = fusedLastError,
            injectorOverallState = injectorOverallState,
            injectorActiveCount = injectorStatuses.count { it.state == InjectorState.RUNNING },
            injectorFailedCount = injectorStatuses.count { it.state == InjectorState.FAILED },
            injectorStatuses = injectorStatuses,
            injectorWarning = if (injectorOverallState == InjectorState.PARTIAL) {
                "部分定位通道不可用: Fused"
            } else {
                null
            },
            appState = appState,
            lastInjection = InjectionReport(
                provider = "gps",
                latitude = 31.0,
                longitude = 121.0,
                accuracyMeters = 4f,
                timeMillis = NOW - 1_000L,
            ),
            movementMode = MovementMode.FIXED,
            movementState = MovementState.Idle,
            movementCurrentSpeedMps = 0.0,
            movementDistanceFromCenterMeters = 0.0,
            movementLastPointTimeMillis = null,
            routeMode = null,
            travelMode = null,
            routeSource = null,
            remainingDistanceMeters = null,
            remainingDurationSeconds = null,
            routeProgressPercent = null,
            calibrationMode = CoordinateCalibrationMode.OFF,
            searchRequestCount = 0,
        )
    }

    private fun diagnosticInjector(
        name: String,
        state: InjectorState,
    ): DiagnosticInjectorStatus {
        return DiagnosticInjectorStatus(
            id = name,
            displayName = name,
            state = state,
            lastSuccessAtMillis = NOW - 1_000L,
            lastFailureAtMillis = if (state == InjectorState.FAILED) NOW - 500L else null,
            lastErrorCode = if (state == InjectorState.FAILED) "UNKNOWN" else null,
        )
    }

    private companion object {
        const val NOW = 100_000L
        val FRESH_MOCK_GPS = DiagnosticLocation(
            provider = "gps",
            latitude = 31.0,
            longitude = 121.0,
            accuracyMeters = 5f,
            timeMillis = NOW - 1_000L,
            isMock = true,
            distanceToLastInjectionMeters = 0.0,
        )
    }
}
