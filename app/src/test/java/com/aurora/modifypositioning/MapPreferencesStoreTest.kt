package com.aurora.modifypositioning

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.aurora.modifypositioning.data.MapPreferencesStore
import com.aurora.modifypositioning.model.MapProvider
import com.aurora.modifypositioning.model.resolveMapKeyAvailability
import com.aurora.modifypositioning.model.resolveMapProvider
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class MapPreferencesStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun defaultProvider_isOsm() {
        assertEquals(MapProvider.OSM, resolveMapProvider(null))
        assertEquals(MapProvider.OSM, resolveMapProvider(""))
        assertEquals(MapProvider.OSM, resolveMapProvider("bad"))
    }

    @Test
    fun amapAvailability_requiresRuntimeOrBuildKey() {
        val empty = resolveMapKeyAvailability(
            runtimeAndroidKey = "",
            buildAndroidKey = "",
            runtimeWebKey = "",
            buildWebKey = "",
        )
        val runtime = resolveMapKeyAvailability(
            runtimeAndroidKey = "android-key",
            buildAndroidKey = "",
            runtimeWebKey = "web-key",
            buildWebKey = "",
        )

        assertFalse(empty.hasAmapAndroidKey)
        assertFalse(empty.hasAmapWebKey)
        assertTrue(runtime.hasAmapAndroidKey)
        assertTrue(runtime.hasAmapWebKey)
    }

    @Test
    fun preferences_persistMapProviderAndAmapKeys() = runTest {
        val store = newStore("map-prefs.preferences_pb")

        assertEquals(MapProvider.OSM, store.getMapProvider())

        store.setMapProvider(MapProvider.AMAP)
        store.setAmapAndroidKey(" android-key ")
        store.setAmapWebKey(" web-key ")

        val settings = store.getMapProviderSettings()
        assertEquals(MapProvider.AMAP, settings.mapProvider)
        assertEquals("android-key", settings.amapAndroidKey)
        assertEquals("web-key", settings.amapWebKey)
    }

    private fun newStore(fileName: String): MapPreferencesStore {
        val scope = TestScope(UnconfinedTestDispatcher())
        val file = File(temporaryFolder.root, fileName)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope.backgroundScope,
            produceFile = { file },
        )
        return MapPreferencesStore(dataStore)
    }
}
