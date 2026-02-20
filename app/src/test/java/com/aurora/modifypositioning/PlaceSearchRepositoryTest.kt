package com.aurora.modifypositioning

import com.aurora.modifypositioning.data.GooglePlaceSearchRepository
import com.aurora.modifypositioning.model.PlaceSuggestion
import com.aurora.modifypositioning.util.AppSessionMetrics
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PlaceSearchRepositoryTest {

    private lateinit var remote: FakeRemote
    private lateinit var repository: GooglePlaceSearchRepository

    @Before
    fun setup() {
        AppSessionMetrics.reset()
        remote = FakeRemote()
        repository = GooglePlaceSearchRepository(remote, cacheTtlMillis = 5 * 60 * 1000L)
    }

    @Test
    fun autocomplete_cachesSameKeyword() = runTest {
        val first = repository.autocomplete("beijing")
        val second = repository.autocomplete("beijing")

        assertEquals(1, remote.searchCalls)
        assertEquals(first, second)
    }

    @Test
    fun autocomplete_limitsToTopFive() = runTest {
        val result = repository.autocomplete("shanghai")

        assertEquals(5, result.size)
        assertEquals("id_0", result.first().id)
    }

    @Test
    fun autocomplete_ignoresShortQuery() = runTest {
        val result = repository.autocomplete("a")

        assertEquals(emptyList<PlaceSuggestion>(), result)
        assertEquals(0, remote.searchCalls)
    }

    private class FakeRemote : GooglePlaceSearchRepository.PlaceSearchRemote {
        var searchCalls: Int = 0

        override suspend fun searchPredictions(query: String): List<GooglePlaceSearchRepository.Prediction> {
            searchCalls += 1
            return List(8) { index ->
                GooglePlaceSearchRepository.Prediction(
                    id = "id_$index",
                    title = "title_$index",
                    subtitle = "subtitle_$index",
                )
            }
        }

        override suspend fun fetchLatLng(placeId: String): LatLng {
            val index = placeId.removePrefix("id_").toDoubleOrNull() ?: 0.0
            return LatLng(30.0 + index, 120.0 + index)
        }
    }
}
