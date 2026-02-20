package com.aurora.modifypositioning.data

import com.aurora.modifypositioning.model.PlaceSuggestion
import com.aurora.modifypositioning.util.AppSessionMetrics
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NominatimPlaceSearchRepository(
    private val remote: PlaceSearchRemote = NominatimRemoteClient(),
    private val cacheTtlMillis: Long = 5 * 60 * 1000L,
) : PlaceSearchRepository {

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
        val suggestions = withContext(Dispatchers.IO) {
            remote.search(query)
        }.take(5)

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

    interface PlaceSearchRemote {
        suspend fun search(query: String): List<PlaceSuggestion>
    }

    private data class CacheEntry(
        val timestamp: Long,
        val suggestions: List<PlaceSuggestion>,
    )
}

class NominatimRemoteClient(
    private val endpoint: String = "https://nominatim.openstreetmap.org/search",
    private val userAgent: String = "ModifyPositioning/1.0 (Android)",
) : NominatimPlaceSearchRepository.PlaceSearchRemote {

    override suspend fun search(query: String): List<PlaceSuggestion> {
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
        val url = "$endpoint?format=jsonv2&limit=5&q=$encoded"

        val connection = (java.net.URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        }

        return try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IllegalStateException("搜索服务异常: HTTP $responseCode")
            }
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            parseResponse(response)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseResponse(response: String): List<PlaceSuggestion> {
        val array = JSONArray(response)
        val suggestions = mutableListOf<PlaceSuggestion>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val lat = item.optString("lat").toDoubleOrNull() ?: continue
            val lng = item.optString("lon").toDoubleOrNull() ?: continue
            val displayName = item.optString("display_name").trim()
            val title = displayName.substringBefore(',').ifBlank { "搜索结果" }
            val subtitle = displayName.substringAfter(',', "").trim()
            val id = item.optString("place_id").ifBlank { "result_$index" }
            suggestions += PlaceSuggestion(
                id = id,
                title = title,
                subtitle = subtitle,
                lat = lat,
                lng = lng,
            )
        }
        return suggestions
    }
}
