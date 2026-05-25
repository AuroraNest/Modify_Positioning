package com.aurora.modifypositioning

import android.location.Location
import com.aurora.modifypositioning.location.CompositeLocationInjector
import com.aurora.modifypositioning.location.FusedLocationInjectorCore
import com.aurora.modifypositioning.location.FusedMockLocationClient
import com.aurora.modifypositioning.location.LocationInjector
import com.aurora.modifypositioning.model.TargetLocation
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

    private class RecordingFusedClient : FusedMockLocationClient {
        val mockModeCalls = mutableListOf<Boolean>()
        val locations = mutableListOf<Location>()

        override fun setMockMode(enabled: Boolean, onFailure: (Throwable) -> Unit) {
            mockModeCalls += enabled
        }

        override fun setMockLocation(location: Location, onFailure: (Throwable) -> Unit) {
            locations += location
        }
    }

    private companion object {
        val TARGET = TargetLocation(
            name = "target",
            latitude = 30.0,
            longitude = 120.0,
        )
    }
}
