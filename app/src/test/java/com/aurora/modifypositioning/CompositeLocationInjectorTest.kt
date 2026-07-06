package com.aurora.modifypositioning

import com.aurora.modifypositioning.location.CompositeLocationInjector
import com.aurora.modifypositioning.location.FusedLocationInjectorCore
import com.aurora.modifypositioning.location.FusedMockLocation
import com.aurora.modifypositioning.location.FusedMockLocationClient
import com.aurora.modifypositioning.location.InjectorState
import com.aurora.modifypositioning.location.LocationInjector
import com.aurora.modifypositioning.model.TargetLocation
import com.aurora.modifypositioning.model.MovementMode
import com.aurora.modifypositioning.simulation.EnvironmentProfile
import com.aurora.modifypositioning.simulation.LocationSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositeLocationInjectorTest {

    @Test
    fun lifecycle_callsBothChildrenAndContinuesAfterFailure() {
        val failing = RecordingInjector(failOnStart = true)
        val healthy = RecordingInjector()
        val errors = mutableListOf<String>()
        val composite = CompositeLocationInjector(
            injectors = listOf(failing, healthy),
            onError = { errors += it },
        )

        composite.start(TARGET)
        composite.updateTarget(TARGET)
        composite.pause()
        composite.stop()
        composite.cleanup()

        assertEquals(listOf("start", "updateTarget", "pause", "stop", "cleanup"), healthy.calls)
        assertEquals(listOf("start", "updateTarget", "pause", "stop", "cleanup"), failing.calls)
        assertTrue(errors.any { it.contains("start") })
    }

    @Test
    fun fusedStopAndCleanup_disableMockMode() {
        val client = RecordingFusedClient()
        val injector = FusedLocationInjectorCore(
            client = client,
            updateIntervalMs = 1_000L,
            onError = {},
        )

        injector.stop()
        injector.cleanup()

        assertEquals(listOf(false, false), client.mockModeCalls)
    }

    @Test
    fun fusedStart_waitsForTaskSuccessBeforePublishingSuccess() {
        val client = RecordingFusedClient()
        val injector = FusedLocationInjectorCore(
            client = client,
            updateIntervalMs = 60_000L,
            onError = {},
        )

        injector.start(TARGET)
        injector.inject(SAMPLE)
        assertEquals(listOf(true), client.mockModeCalls)
        assertTrue(com.aurora.modifypositioning.location.FusedLocationDiagnosticsStore.state.value.mockModePending)
        assertEquals(false, com.aurora.modifypositioning.location.FusedLocationDiagnosticsStore.state.value.mockModeEnabled)
        assertTrue(client.locations.isEmpty())

        client.completeMockModeSuccess()
        assertEquals(true, com.aurora.modifypositioning.location.FusedLocationDiagnosticsStore.state.value.mockModeEnabled)
        assertTrue(com.aurora.modifypositioning.location.FusedLocationDiagnosticsStore.state.value.lastInjectionPending)
        assertEquals(null, com.aurora.modifypositioning.location.FusedLocationDiagnosticsStore.state.value.lastInjectionTimeMillis)

        client.completeLocationSuccess()
        val diagnostics = com.aurora.modifypositioning.location.FusedLocationDiagnosticsStore.state.value
        assertEquals(false, diagnostics.lastInjectionPending)
        assertTrue(diagnostics.lastInjectionTimeMillis != null)
        assertTrue(diagnostics.lastSuccessfulLatitude != null)
        assertTrue(diagnostics.lastSuccessfulLongitude != null)
    }

    @Test
    fun fusedStart_failureDoesNotPublishMockModeOrInjectionSuccess() {
        val client = RecordingFusedClient()
        val errors = mutableListOf<String>()
        val injector = FusedLocationInjectorCore(
            client = client,
            updateIntervalMs = 60_000L,
            onError = { errors += it },
        )

        injector.start(TARGET)
        client.completeMockModeFailure(IllegalStateException("denied"))

        val diagnostics = com.aurora.modifypositioning.location.FusedLocationDiagnosticsStore.state.value
        assertEquals(false, diagnostics.mockModePending)
        assertEquals(false, diagnostics.mockModeEnabled)
        assertEquals(null, diagnostics.lastInjectionTimeMillis)
        assertTrue(client.locations.isEmpty())
        assertTrue(errors.any { it.contains("denied") })
    }

    @Test
    fun fusedInjection_failureKeepsPreviousSuccessDataUnset() {
        val client = RecordingFusedClient()
        val errors = mutableListOf<String>()
        val injector = FusedLocationInjectorCore(
            client = client,
            updateIntervalMs = 60_000L,
            onError = { errors += it },
        )

        injector.start(TARGET)
        client.completeMockModeSuccess()
        injector.inject(SAMPLE)
        client.completeLocationFailure(IllegalStateException("location denied"))

        val diagnostics = com.aurora.modifypositioning.location.FusedLocationDiagnosticsStore.state.value
        assertEquals(false, diagnostics.lastInjectionPending)
        assertEquals(null, diagnostics.lastInjectionTimeMillis)
        assertEquals(null, diagnostics.lastSuccessfulLatitude)
        assertTrue(diagnostics.lastError?.contains("location denied") == true)
        assertTrue(errors.any { it.contains("location denied") })
    }

    @Test
    fun aggregateStatus_reportsPartialWhenOneChildFails() {
        val failing = StatusInjector(InjectorState.FAILED)
        val healthy = StatusInjector(InjectorState.RUNNING)
        val composite = CompositeLocationInjector(
            injectors = listOf(failing, healthy),
            onError = {},
        )

        val status = composite.aggregateStatus()

        assertEquals(InjectorState.PARTIAL, status.overallState)
        assertEquals(1, status.activeCount)
        assertEquals(1, status.failedCount)
    }

    private class RecordingInjector(
        private val failOnStart: Boolean = false,
    ) : LocationInjector {
        val calls = mutableListOf<String>()

        override fun start(target: TargetLocation) {
            calls += "start"
            if (failOnStart) {
                error("boom")
            }
        }

        override fun updateTarget(target: TargetLocation) {
            calls += "updateTarget"
        }

        override fun pause() {
            calls += "pause"
        }

        override fun stop() {
            calls += "stop"
        }

        override fun cleanup() {
            calls += "cleanup"
        }
    }

    private class StatusInjector(
        private val state: InjectorState,
    ) : LocationInjector {
        override fun start(target: TargetLocation) = Unit
        override fun updateTarget(target: TargetLocation) = Unit
        override fun pause() = Unit
        override fun stop() = Unit

        override fun status(): com.aurora.modifypositioning.location.InjectorStatus {
            return com.aurora.modifypositioning.location.InjectorStatus(
                id = state.name,
                displayName = state.name,
                state = state,
            )
        }
    }

    private class RecordingFusedClient : FusedMockLocationClient {
        val mockModeCalls = mutableListOf<Boolean>()
        val locations = mutableListOf<FusedMockLocation>()
        private val mockModeSuccessCallbacks = ArrayDeque<() -> Unit>()
        private val mockModeFailureCallbacks = ArrayDeque<(Throwable) -> Unit>()
        private val locationSuccessCallbacks = ArrayDeque<() -> Unit>()
        private val locationFailureCallbacks = ArrayDeque<(Throwable) -> Unit>()

        override fun setMockMode(
            enabled: Boolean,
            onSuccess: () -> Unit,
            onFailure: (Throwable) -> Unit,
        ) {
            mockModeCalls += enabled
            mockModeSuccessCallbacks += onSuccess
            mockModeFailureCallbacks += onFailure
        }

        override fun setMockLocation(
            location: FusedMockLocation,
            onSuccess: () -> Unit,
            onFailure: (Throwable) -> Unit,
        ) {
            locations += location
            locationSuccessCallbacks += onSuccess
            locationFailureCallbacks += onFailure
        }

        fun completeMockModeSuccess() {
            mockModeSuccessCallbacks.removeFirst().invoke()
        }

        fun completeMockModeFailure(error: Throwable) {
            mockModeFailureCallbacks.removeFirst().invoke(error)
        }

        fun completeLocationSuccess() {
            locationSuccessCallbacks.removeFirst().invoke()
        }

        fun completeLocationFailure(error: Throwable) {
            locationFailureCallbacks.removeFirst().invoke(error)
        }
    }

    private companion object {
        val TARGET = TargetLocation(
            name = "target",
            latitude = 30.0,
            longitude = 120.0,
        )
        val SAMPLE = LocationSample(
            latitude = TARGET.latitude,
            longitude = TARGET.longitude,
            altitudeMeters = null,
            accuracyMeters = 5f,
            verticalAccuracyMeters = 8f,
            speedMps = 0.2f,
            speedAccuracyMps = 0.3f,
            bearingDegrees = 20f,
            bearingAccuracyDegrees = 8f,
            timestampMillis = 12_000L,
            elapsedRealtimeNanos = 45_000L,
            movementMode = MovementMode.FIXED,
            environment = EnvironmentProfile.OUTDOOR_OPEN,
            sourceLabel = "test",
        )
    }
}
