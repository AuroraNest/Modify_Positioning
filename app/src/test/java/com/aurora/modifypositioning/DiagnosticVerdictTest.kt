package com.aurora.modifypositioning

import com.aurora.modifypositioning.model.CoordinateCalibrationMode
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
    fun verdict_doesNotTreatStaleNonMockCacheAsOverwrite() {
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

        assertEquals(DiagnosticVerdictStatus.Ok, verdict.status)
        assertTrue(snapshot.realLocationOverwriteSummary().contains("旧缓存"))
        assertTrue(verdict.possibleCauses.any { it.contains("旧缓存") })
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
        assertTrue(report.contains("fusedMockModePending: false"))
        assertTrue(report.contains("fusedLastSuccessful: 31.0,121.0"))
    }

    private fun snapshot(
        isMockAppSelected: Boolean = true,
        missingPermissions: List<String> = emptyList(),
        gpsLastKnown: DiagnosticLocation? = null,
        networkLastKnown: DiagnosticLocation? = null,
        fusedMockModeEnabled: Boolean = true,
        fusedMockModePending: Boolean = false,
        fusedLastInjectionPending: Boolean = false,
        fusedLastInjectionTimeMillis: Long? = NOW - 1_000L,
        fusedLastError: String? = null,
    ): DiagnosticSnapshot {
        return DiagnosticSnapshot(
            generatedAtMillis = NOW,
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
            appState = MockState.Running,
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

    private companion object {
        const val NOW = 100_000L
    }
}
