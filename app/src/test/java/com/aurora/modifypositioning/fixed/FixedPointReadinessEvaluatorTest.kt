package com.aurora.modifypositioning.fixed

import com.aurora.modifypositioning.location.CompositeInjectorStatus
import com.aurora.modifypositioning.location.InjectorState
import com.aurora.modifypositioning.model.DiagnosticLocation
import com.aurora.modifypositioning.model.FixedPointReadinessState
import com.aurora.modifypositioning.model.InjectionReport
import com.aurora.modifypositioning.model.MockState
import com.aurora.modifypositioning.model.MovementMode
import com.aurora.modifypositioning.model.TargetLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FixedPointReadinessEvaluatorTest {
    @Test
    fun nonFixedMode_hasNoSnapshot() {
        assertNull(FixedPointReadinessEvaluator.evaluate(input().copy(movementMode = MovementMode.RANDOM_WALK)))
    }

    @Test
    fun idle_isNotReportedBlocked() {
        val result = evaluate(
            input().copy(
                appState = MockState.Idle,
                isMockAppSelected = false,
                session = session(phase = FixedPointReadinessState.IDLE),
            ),
        )

        assertEquals(FixedPointReadinessState.IDLE, result.state)
    }

    @Test
    fun stopped_isNotReportedBlocked() {
        val result = evaluate(
            input().copy(
                appState = MockState.Idle,
                session = session(phase = FixedPointReadinessState.STOPPED),
            ),
        )

        assertEquals(FixedPointReadinessState.STOPPED, result.state)
    }

    @Test
    fun missingMockApp_isBlocked() {
        assertEquals(
            FixedPointReadinessState.BLOCKED,
            evaluate(input().copy(isMockAppSelected = false)).state,
        )
    }

    @Test
    fun missingPermission_isBlocked() {
        assertEquals(
            FixedPointReadinessState.BLOCKED,
            evaluate(input().copy(missingPermissions = listOf("fine"))).state,
        )
    }

    @Test
    fun disabledSystemLocation_isBlocked() {
        assertEquals(
            FixedPointReadinessState.BLOCKED,
            evaluate(input().copy(systemLocationEnabled = false)).state,
        )
    }

    @Test
    fun allInjectorsFailed_isBlocked() {
        val failed = CompositeInjectorStatus(InjectorState.FAILED, 0, 2, emptyList())

        assertEquals(
            FixedPointReadinessState.BLOCKED,
            evaluate(input().copy(injectorStatus = failed)).state,
        )
    }

    @Test
    fun freshGpsAndFused_afterWarmupAndStableWindow_isReady() {
        val result = evaluate(input())

        assertEquals(FixedPointReadinessState.READY, result.state)
        assertEquals(0L, result.recommendedWaitMillis)
        assertTrue(result.score in 0..100)
    }

    @Test
    fun stableWindowUnderTenSeconds_isSettling() {
        val result = evaluate(
            input().copy(session = session(stableSinceElapsedRealtimeMillis = NOW - 5_000L)),
        )

        assertEquals(FixedPointReadinessState.SETTLING, result.state)
        assertTrue(result.recommendedWaitMillis >= 5_000L)
    }

    @Test
    fun missingFused_withFreshGpsAndNetwork_isDegradedNotBlocked() {
        val result = evaluate(
            input().copy(
                fusedAvailable = false,
                fusedMockModeEnabled = false,
                fusedLastInjectionElapsedRealtimeMillis = null,
                fusedLastSuccessfulLatitude = null,
                fusedLastSuccessfulLongitude = null,
            ),
        )

        assertEquals(FixedPointReadinessState.DEGRADED, result.state)
    }

    @Test
    fun freshNonMockGps_preventsReadyAndExplainsOverwrite() {
        val result = evaluate(input().copy(gpsLastKnown = location("gps", isMock = false)))

        assertEquals(FixedPointReadinessState.DEGRADED, result.state)
        assertTrue(result.checks.single { it.id == "overwrite" }.message.contains("mock=false"))
    }

    @Test
    fun staleGps_isNotFreshAndCannotBeReady() {
        val staleGps = location("gps").copy(elapsedRealtimeNanos = (NOW - 3_000L) * 1_000_000L)
        val result = evaluate(input().copy(gpsLastKnown = staleGps))

        assertFalse(result.gps.fresh)
        assertEquals(FixedPointReadinessState.DEGRADED, result.state)
    }

    @Test
    fun coarseNetworkAccuracy_canExpandNearTargetLimit() {
        val network = location("network").copy(
            longitude = TARGET.longitude + 0.0015,
            accuracyMeters = 200f,
        )
        val result = evaluate(
            input().copy(
                networkLastKnown = network,
                fusedAvailable = false,
                fusedMockModeEnabled = false,
                fusedLastInjectionElapsedRealtimeMillis = null,
                fusedLastSuccessfulLatitude = null,
                fusedLastSuccessfulLongitude = null,
            ),
        )

        assertTrue(result.network.nearTarget)
        assertEquals(FixedPointReadinessState.DEGRADED, result.state)
    }

    @Test
    fun rawAndInjectedTargets_areBothPreservedWithOffset() {
        val injected = TARGET.copy(latitude = TARGET.latitude + 0.002)
        val result = evaluate(input().copy(injectedTarget = injected))

        assertEquals(TARGET, result.rawTarget)
        assertEquals(injected, result.injectedTarget)
        assertTrue(result.calibrationOffsetMeters > 0.0)
        assertTrue(result.score in 0..100)
    }

    private fun evaluate(input: FixedPointReadinessInput) =
        checkNotNull(FixedPointReadinessEvaluator.evaluate(input))

    private fun input(): FixedPointReadinessInput {
        return FixedPointReadinessInput(
            nowElapsedRealtimeMillis = NOW,
            appState = MockState.Running,
            movementMode = MovementMode.FIXED,
            rawTarget = TARGET,
            injectedTarget = TARGET,
            session = session(),
            isMockAppSelected = true,
            missingPermissions = emptyList(),
            systemLocationEnabled = true,
            batteryIgnoringOptimizations = true,
            gpsEnabled = true,
            networkEnabled = true,
            gpsLastKnown = location("gps"),
            networkLastKnown = location("network"),
            fusedAvailable = true,
            fusedMockModeEnabled = true,
            fusedMockModePending = false,
            fusedLastInjectionPending = false,
            fusedLastInjectionElapsedRealtimeMillis = NOW - 500L,
            fusedLastSuccessfulLatitude = TARGET.latitude,
            fusedLastSuccessfulLongitude = TARGET.longitude,
            fusedLastSuccessfulAccuracyMeters = 5f,
            injectorStatus = CompositeInjectorStatus(InjectorState.RUNNING, 2, 0, emptyList()),
            lastInjection = InjectionReport("gps", TARGET.latitude, TARGET.longitude, 5f, 1_000L),
        )
    }

    private fun session(
        phase: FixedPointReadinessState = FixedPointReadinessState.SETTLING,
        stableSinceElapsedRealtimeMillis: Long? = NOW - 10_000L,
    ) = FixedPointSessionSnapshot(
        generation = 1L,
        phase = phase,
        rawTarget = TARGET,
        injectedTarget = TARGET,
        startedAtElapsedRealtimeMillis = NOW - 15_000L,
        stableSinceElapsedRealtimeMillis = stableSinceElapsedRealtimeMillis,
    )

    private fun location(provider: String, isMock: Boolean = true) = DiagnosticLocation(
        provider = provider,
        latitude = TARGET.latitude,
        longitude = TARGET.longitude,
        accuracyMeters = 5f,
        timeMillis = 1_000L,
        isMock = isMock,
        elapsedRealtimeNanos = (NOW - 500L) * 1_000_000L,
    )

    private companion object {
        const val NOW = 50_000L
        val TARGET = TargetLocation("target", 30.0, 120.0)
    }
}
