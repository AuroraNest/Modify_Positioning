package com.aurora.modifypositioning

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.aurora.modifypositioning.data.MapPreferencesStore
import com.aurora.modifypositioning.model.MapProvider
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
    fun preferences_persistProviderTrimmedAndroidKeyAndPrivacyConsent() = runTest {
        val store = newStore("map-prefs.preferences_pb")

        assertEquals(MapProvider.OSM, store.getMapProvider())
        assertEquals("", store.getAmapAndroidKey())
        assertFalse(store.getAmapPrivacyAccepted())

        store.setAmapAndroidKey(" android-key ")
        store.setAmapPrivacyAccepted(true)
        store.setMapProvider(MapProvider.AMAP)

        val settings = store.getMapProviderSettings()
        assertEquals(MapProvider.AMAP, settings.mapProvider)
        assertEquals("android-key", settings.amapAndroidKey)
        assertTrue(settings.amapPrivacyAccepted)
        assertTrue(settings.canUseAmap)

        store.setAmapAndroidKey("replacement-key")
        assertEquals(MapProvider.OSM, store.getMapProvider())
        assertEquals("replacement-key", store.getAmapAndroidKey())

        store.setMapProvider(MapProvider.AMAP)

        store.setAmapPrivacyAccepted(false)
        assertEquals(MapProvider.OSM, store.getMapProvider())
        assertFalse(store.getAmapPrivacyAccepted())
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
