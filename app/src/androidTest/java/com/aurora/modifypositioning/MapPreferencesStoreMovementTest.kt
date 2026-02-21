package com.aurora.modifypositioning

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aurora.modifypositioning.data.MapPreferencesStore
import com.aurora.modifypositioning.model.MovementMode
import com.aurora.modifypositioning.model.RandomWalkConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapPreferencesStoreMovementTest {

    private lateinit var store: MapPreferencesStore

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        store = MapPreferencesStore(context)
        runBlocking {
            store.setMovementMode(MovementMode.FIXED)
            store.saveRandomWalkConfig(RandomWalkConfig())
        }
    }

    @Test
    fun movementMode_canPersistAndReadBack() = runBlocking {
        store.setMovementMode(MovementMode.RANDOM_WALK)

        assertEquals(MovementMode.RANDOM_WALK, store.getMovementMode())
    }

    @Test
    fun randomWalkConfig_isNormalizedWhenSaved() = runBlocking {
        store.saveRandomWalkConfig(
            RandomWalkConfig(
                radiusMeters = 10.0,
                minSpeedMps = 8.0,
                maxSpeedMps = 1.0,
                stepIntervalMs = 100L,
            ),
        )

        val config = store.getRandomWalkConfig()
        assertEquals(30.0, config.radiusMeters, 0.0001)
        assertEquals(5.0, config.minSpeedMps, 0.0001)
        assertEquals(5.0, config.maxSpeedMps, 0.0001)
        assertEquals(200L, config.stepIntervalMs)
    }
}
