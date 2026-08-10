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
    fun fusedInjection_coalescesToLatestSampleWhileTaskIsInFlight() {
        val client = RecordingFusedClient()
        val injector = FusedLocationInjectorCore(
            client = client,
            updateIntervalMs = 60_000L,
            onError = {},
        )
        val secondSample = SAMPLE.copy(
            latitude = 31.0,
            longitude = 121.0,
            timestampMillis = 13_000L,
            elapsedRealtimeNanos = 46_000L,
        )

        injector.start(TARGET)
        client.completeMockModeSuccess()
        injector.inject(SAMPLE)
        injector.inject(secondSample)

        assertEquals(listOf(SAMPLE.latitude), client.locations.map { it.latitude })

        client.completeLocationSuccess()
        val firstCompletion = com.aurora.modifypositioning.location.FusedLocationDiagnosticsStore.state.value
        assertEquals(SAMPLE.timestampMillis, firstCompletion.lastInjectionTimeMillis)
        assertTrue(firstCompletion.lastInjectionPending)
        assertEquals(SAMPLE, injector.status().lastInjectedSample)
        assertEquals(listOf(SAMPLE.latitude, secondSample.latitude), client.locations.map { it.latitude })

        client.completeLocationSuccess()
        val secondCompletion = com.aurora.modifypositioning.location.FusedLocationDiagnosticsStore.state.value
        assertEquals(false, secondCompletion.lastInjectionPending)
        assertEquals(secondSample.timestampMillis, secondCompletion.lastInjectionTimeMillis)
        assertEquals(secondSample.latitude, secondCompletion.lastSuccessfulLatitude!!, 0.0)
        assertEquals(secondSample.longitude, secondCompletion.lastSuccessfulLongitude!!, 0.0)
        assertEquals(secondSample, injector.status().lastInjectedSample)
    }

    @Test
    fun fusedStop_ignoresLateLocationSuccess() {
        val client = RecordingFusedClient()
        val injector = FusedLocationInjectorCore(
            client = client,
            updateIntervalMs = 60_000L,
            onError = {},
        )

        injector.start(TARGET)
        client.completeMockModeSuccess()
        injector.inject(SAMPLE)
        injector.stop()
        client.completeLocationSuccess()

        val diagnostics = com.aurora.modifypositioning.location.FusedLocationDiagnosticsStore.state.value
        assertEquals(InjectorState.STOPPED, injector.status().state)
        assertEquals(null, injector.status().lastSuccessAtMillis)
        assertEquals(null, diagnostics.lastInjectionTimeMillis)
    }

    @Test
    fun fusedMockMode_ignoresStaleCallbacksAcrossTrueFalseTrue() {
        val client = RecordingFusedClient()
        val injector = FusedLocationInjectorCore(
            client = client,
            updateIntervalMs = 60_000L,
            onError = {},
        )

        injector.start(TARGET)
        injector.stop()
        injector.start(TARGET)

        assertEquals(listOf(true, false, true), client.mockModeCalls)
        client.completeMockModeSuccessAt(0)
        assertTrue(com.aurora.modifypositioning.location.FusedLocationDiagnosticsStore.state.value.mockModePending)
        assertEquals(false, com.aurora.modifypositioning.location.FusedLocationDiagnosticsStore.state.value.mockModeEnabled)

        client.completeMockModeSuccessAt(2)
        assertEquals(InjectorState.RUNNING, injector.status().state)
        assertEquals(true, com.aurora.modifypositioning.location.FusedLocationDiagnosticsStore.state.value.mockModeEnabled)
        assertEquals(false, com.aurora.modifypositioning.location.FusedLocationDiagnosticsStore.state.value.mockModePending)

        client.completeMockModeSuccessAt(1)
        assertEquals(InjectorState.RUNNING, injector.status().state)
        assertEquals(true, com.aurora.modifypositioning.location.FusedLocationDiagnosticsStore.state.value.mockModeEnabled)
    }

    @Test
    fun fusedRestart_dispatchesWhenOldLocationTaskNeverCompletes() {
        val client = RecordingFusedClient()
        val injector = FusedLocationInjectorCore(
            client = client,
            updateIntervalMs = 60_000L,
            onError = {},
        )
        val restartedSample = SAMPLE.copy(
            latitude = 31.0,
            longitude = 121.0,
            timestampMillis = 13_000L,
            elapsedRealtimeNanos = 46_000L,
        )

        injector.start(TARGET)
        client.completeMockModeSuccessAt(0)
        injector.inject(SAMPLE)
        injector.stop()
        injector.start(TARGET)
        client.completeMockModeSuccessAt(2)
        injector.inject(restartedSample)

        assertEquals(listOf(SAMPLE.latitude, restartedSample.latitude), client.locations.map { it.latitude })
        client.completeLocationSuccessAt(0)
        assertTrue(com.aurora.modifypositioning.location.FusedLocationDiagnosticsStore.state.value.lastInjectionPending)
        assertEquals(null, injector.status().lastInjectedSample)

        client.completeLocationSuccessAt(1)
        assertEquals(false, com.aurora.modifypositioning.location.FusedLocationDiagnosticsStore.state.value.lastInjectionPending)
        assertEquals(restartedSample, injector.status().lastInjectedSample)
    }

    @Test
    fun fusedMockModeTimeout_retriesLatestDesiredModeAndIgnoresStaleCallback() {
        var now = 100L
        val client = RecordingFusedClient()
        val injector = FusedLocationInjectorCore(
            client = client,
            updateIntervalMs = 60_000L,
            onError = {},
            monotonicNowMillis = { now },
            mockModeRequestTimeoutMillis = 3_000L,
        )
        val latest = SAMPLE.copy(latitude = 31.0, longitude = 121.0)

        injector.start(TARGET)
        now += 3_001L
        injector.inject(latest)

        assertEquals(listOf(true, true), client.mockModeCalls)
        assertTrue(com.aurora.modifypositioning.location.FusedLocationDiagnosticsStore.state.value.mockModePending)
        assertTrue(
            com.aurora.modifypositioning.location.FusedLocationDiagnosticsStore.state.value.lastError
                ?.contains("超时") == true,
        )

        client.completeMockModeSuccessAt(0)
        assertTrue(com.aurora.modifypositioning.location.FusedLocationDiagnosticsStore.state.value.mockModePending)
        client.completeMockModeSuccessAt(1)

        assertEquals(listOf(latest.latitude), client.locations.map { it.latitude })
        assertTrue(com.aurora.modifypositioning.location.FusedLocationDiagnosticsStore.state.value.lastInjectionPending)
    }

    @Test
    fun fusedLocationTimeout_retriesLatestSampleAndIgnoresStaleCallback() {
        var now = 100L
        val client = RecordingFusedClient()
        val injector = FusedLocationInjectorCore(
            client = client,
            updateIntervalMs = 60_000L,
            onError = {},
            monotonicNowMillis = { now },
            setLocationRequestTimeoutMillis = 2_000L,
        )
        val second = SAMPLE.copy(latitude = 31.0, longitude = 121.0)
        val latest = SAMPLE.copy(latitude = 32.0, longitude = 122.0)

        injector.start(TARGET)
        client.completeMockModeSuccess()
        injector.inject(SAMPLE)
        injector.inject(second)
        now += 2_001L
        injector.inject(latest)

        assertEquals(listOf(SAMPLE.latitude, latest.latitude), client.locations.map { it.latitude })
        assertTrue(
            com.aurora.modifypositioning.location.FusedLocationDiagnosticsStore.state.value.lastError
                ?.contains("超时") == true,
        )

        client.completeLocationSuccessAt(0)
        assertTrue(com.aurora.modifypositioning.location.FusedLocationDiagnosticsStore.state.value.lastInjectionPending)
        assertEquals(null, injector.status().lastInjectedSample)
        client.completeLocationSuccessAt(1)

        assertEquals(latest, injector.status().lastInjectedSample)
        assertEquals(false, com.aurora.modifypositioning.location.FusedLocationDiagnosticsStore.state.value.lastInjectionPending)
    }

    @Test
    fun fusedTargetUpdate_retiresOldSuccessBeforePublishingNewTarget() {
        val client = RecordingFusedClient()
        val injector = FusedLocationInjectorCore(
            client = client,
            updateIntervalMs = 60_000L,
            onError = {},
        )
        val nextTarget = TargetLocation("next", 31.0, 121.0)
        val nextSample = SAMPLE.copy(latitude = nextTarget.latitude, longitude = nextTarget.longitude)

        injector.start(TARGET)
        client.completeMockModeSuccess()
        injector.inject(SAMPLE)
        injector.updateTarget(nextTarget)
        injector.inject(nextSample)

        assertEquals(listOf(SAMPLE.latitude), client.locations.map { it.latitude })
        client.completeLocationSuccessAt(0)

        assertEquals(null, injector.status().lastInjectedSample)
        assertEquals(null, com.aurora.modifypositioning.location.FusedLocationDiagnosticsStore.state.value.lastSuccessfulLatitude)
        assertEquals(listOf(SAMPLE.latitude, nextSample.latitude), client.locations.map { it.latitude })

        client.completeLocationSuccessAt(1)
        assertEquals(nextSample, injector.status().lastInjectedSample)
    }

    @Test
    fun fusedStopTimeout_statusPollRetriesFalseAndIgnoresStaleCallback() {
        var now = 100L
        val client = RecordingFusedClient()
        val injector = FusedLocationInjectorCore(
            client = client,
            updateIntervalMs = 60_000L,
            onError = {},
            monotonicNowMillis = { now },
            mockModeRequestTimeoutMillis = 3_000L,
        )

        injector.start(TARGET)
        client.completeMockModeSuccessAt(0)
        injector.stop()
        now += 3_001L
        injector.status()

        assertEquals(listOf(true, false, false), client.mockModeCalls)
        assertTrue(com.aurora.modifypositioning.location.FusedLocationDiagnosticsStore.state.value.mockModePending)

        client.completeMockModeSuccessAt(1)
        assertTrue(com.aurora.modifypositioning.location.FusedLocationDiagnosticsStore.state.value.mockModePending)
        assertEquals(true, com.aurora.modifypositioning.location.FusedLocationDiagnosticsStore.state.value.mockModeEnabled)

        client.completeMockModeSuccessAt(2)
        assertEquals(false, com.aurora.modifypositioning.location.FusedLocationDiagnosticsStore.state.value.mockModePending)
        assertEquals(false, com.aurora.modifypositioning.location.FusedLocationDiagnosticsStore.state.value.mockModeEnabled)
        assertEquals(InjectorState.STOPPED, injector.status().state)
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
        private val mockModeSuccessCallbacks = mutableListOf<(() -> Unit)?>()
        private val mockModeFailureCallbacks = mutableListOf<((Throwable) -> Unit)?>()
        private val locationSuccessCallbacks = mutableListOf<(() -> Unit)?>()
        private val locationFailureCallbacks = mutableListOf<((Throwable) -> Unit)?>()

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
            completeMockModeSuccessAt(mockModeSuccessCallbacks.indexOfFirst { it != null })
        }

        fun completeMockModeFailure(throwable: Throwable) {
            val index = mockModeFailureCallbacks.indexOfFirst { it != null }
            val callback = checkNotNull(mockModeFailureCallbacks[index])
            mockModeFailureCallbacks[index] = null
            callback(throwable)
        }

        fun completeLocationSuccess() {
            completeLocationSuccessAt(locationSuccessCallbacks.indexOfFirst { it != null })
        }

        fun completeLocationFailure(throwable: Throwable) {
            val index = locationFailureCallbacks.indexOfFirst { it != null }
            val callback = checkNotNull(locationFailureCallbacks[index])
            locationFailureCallbacks[index] = null
            callback(throwable)
        }

        fun completeMockModeSuccessAt(callIndex: Int) {
            val callback = mockModeSuccessCallbacks[callIndex] ?: error("mock mode success callback missing")
            mockModeSuccessCallbacks[callIndex] = null
            callback()
        }

        fun completeLocationSuccessAt(callIndex: Int) {
            val callback = locationSuccessCallbacks[callIndex] ?: error("location success callback missing")
            locationSuccessCallbacks[callIndex] = null
            callback()
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
