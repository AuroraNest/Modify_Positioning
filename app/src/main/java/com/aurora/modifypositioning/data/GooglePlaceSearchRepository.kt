package com.aurora.modifypositioning.data

import com.aurora.modifypositioning.model.PlaceSuggestion
import com.aurora.modifypositioning.util.AppSessionMetrics
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class GooglePlaceSearchRepository(
    private val remote: PlaceSearchRemote,
    private val cacheTtlMillis: Long = 5 * 60 * 1000L,
) : PlaceSearchRepository {

    constructor(
        placesClient: PlacesClient,
        cacheTtlMillis: Long = 5 * 60 * 1000L,
    ) : this(
        remote = GooglePlacesRemoteClient(placesClient),
        cacheTtlMillis = cacheTtlMillis,
    )

    private val cache = LinkedHashMap<String, CacheEntry>()

    override suspend fun autocomplete(query: String): List<PlaceSuggestion> {
        val normalized = query.trim().lowercase()
        if (normalized.length < 2) {
            return emptyList()
        }

        val now = System.currentTimeMillis()
        val cached = synchronized(cache) { cache[normalized] }
        if (cached != null && now - cached.timestamp <= cacheTtlMillis) {
            return cached.suggestions
        }

        AppSessionMetrics.increaseSearchRequests()
        val predictions = withContext(Dispatchers.IO) {
            remote.searchPredictions(query)
        }.take(5)

        val suggestions = coroutineScope {
            predictions.map { prediction ->
                async {
                    val place = withContext(Dispatchers.IO) {
                        remote.fetchLatLng(prediction.id)
                    }
                    PlaceSuggestion(
                        id = prediction.id,
                        title = prediction.title,
                        subtitle = prediction.subtitle,
                        lat = place.latitude,
                        lng = place.longitude,
                    )
                }
            }.awaitAll()
        }

        synchronized(cache) {
            cache[normalized] = CacheEntry(now, suggestions)
            if (cache.size > 30) {
                val oldestKey = cache.entries.minByOrNull { it.value.timestamp }?.key
                if (oldestKey != null) {
                    cache.remove(oldestKey)
                }
            }
        }

        return suggestions
    }

    data class Prediction(
        val id: String,
        val title: String,
        val subtitle: String,
    )

    interface PlaceSearchRemote {
        suspend fun searchPredictions(query: String): List<Prediction>
        suspend fun fetchLatLng(placeId: String): LatLng
    }

    private data class CacheEntry(
        val timestamp: Long,
        val suggestions: List<PlaceSuggestion>,
    )
}

class GooglePlacesRemoteClient(
    private val placesClient: PlacesClient,
) : GooglePlaceSearchRepository.PlaceSearchRemote {

    override suspend fun searchPredictions(query: String): List<GooglePlaceSearchRepository.Prediction> {
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .build()

        val predictions = withContext(Dispatchers.IO) {
            suspendFindPredictions(request)
        }

        return predictions.map { prediction ->
            GooglePlaceSearchRepository.Prediction(
                id = prediction.placeId,
                title = prediction.getPrimaryText(null).toString(),
                subtitle = prediction.getSecondaryText(null).toString(),
            )
        }
    }

    override suspend fun fetchLatLng(placeId: String): LatLng {
        val fields = listOf(Place.Field.LAT_LNG)
        val request = FetchPlaceRequest.newInstance(placeId, fields)
        val place = withContext(Dispatchers.IO) {
            suspendFetchPlace(request)
        }
        return place.latLng ?: throw IllegalStateException("地点缺少坐标")
    }

    private suspend fun suspendFindPredictions(request: FindAutocompletePredictionsRequest) =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            placesClient.findAutocompletePredictions(request)
                .addOnSuccessListener { response ->
                    cont.resume(response.autocompletePredictions)
                }
                .addOnFailureListener { error ->
                    cont.resumeWithException(error)
                }
        }

    private suspend fun suspendFetchPlace(request: FetchPlaceRequest) =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            placesClient.fetchPlace(request)
                .addOnSuccessListener { response ->
                    cont.resume(response.place)
                }
                .addOnFailureListener { error ->
                    cont.resumeWithException(error)
                }
        }
}
