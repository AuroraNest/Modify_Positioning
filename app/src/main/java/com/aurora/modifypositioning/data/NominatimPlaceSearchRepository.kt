package com.aurora.modifypositioning.data

import com.aurora.modifypositioning.model.PlaceSuggestion
import com.aurora.modifypositioning.util.AppSessionMetrics
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject
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
        val suggestions = runCatching {
            withContext(Dispatchers.IO) {
                remote.search(query)
            }.take(5)
        }.getOrElse { throwable ->
            throw IllegalStateException(resolveFriendlyMessage(throwable))
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

    interface PlaceSearchRemote {
        suspend fun search(query: String): List<PlaceSuggestion>
    }

    private data class CacheEntry(
        val timestamp: Long,
        val suggestions: List<PlaceSuggestion>,
    )

    private fun resolveFriendlyMessage(throwable: Throwable): String {
        return when (throwable) {
            is SocketTimeoutException -> "搜索超时，请检查网络后重试"
            is UnknownHostException -> "网络不可用，请检查连接后重试"
            else -> throwable.message ?: "搜索失败，请稍后再试"
        }
    }
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
            connectTimeout = 6000
            readTimeout = 6000
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

class AmapInputTipsRemoteClient(
    private val apiKey: String,
    private val endpoint: String = "https://restapi.amap.com/v3/assistant/inputtips",
) : NominatimPlaceSearchRepository.PlaceSearchRemote {

    override suspend fun search(query: String): List<PlaceSuggestion> {
        if (apiKey.isBlank()) {
            throw IllegalStateException("搜索服务未配置可用 Key")
        }
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
        val url = "$endpoint?keywords=$encoded&datatype=all&citylimit=false&key=$apiKey"
        val connection = (java.net.URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
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
        val root = JSONObject(response)
        if (root.optString("status") != "1") {
            val info = root.optString("info").ifBlank { "高德搜索请求失败" }
            throw IllegalStateException(info)
        }

        val tips = root.optJSONArray("tips") ?: JSONArray()
        val result = mutableListOf<PlaceSuggestion>()
        for (index in 0 until tips.length()) {
            val item = tips.optJSONObject(index) ?: continue
            val location = item.optString("location").trim()
            if (!location.contains(",")) {
                continue
            }
            val coordinate = location.split(",")
            if (coordinate.size != 2) {
                continue
            }
            val lng = coordinate[0].toDoubleOrNull() ?: continue
            val lat = coordinate[1].toDoubleOrNull() ?: continue
            val name = item.optString("name").ifBlank { "搜索结果" }
            val district = item.optString("district").trim()
            val address = item.optString("address").trim()
            val subtitle = listOf(district, address)
                .filter { it.isNotBlank() && it != "[]" }
                .joinToString(" · ")
            val id = item.optString("id").ifBlank { "amap_$index" }
            result += PlaceSuggestion(
                id = id,
                title = name,
                subtitle = subtitle,
                lat = lat,
                lng = lng,
            )
        }
        return result
    }
}

class FallbackPlaceSearchRemote(
    private val remotes: List<NominatimPlaceSearchRepository.PlaceSearchRemote>,
) : NominatimPlaceSearchRepository.PlaceSearchRemote {

    override suspend fun search(query: String): List<PlaceSuggestion> {
        var lastError: Throwable? = null
        for (remote in remotes) {
            runCatching { remote.search(query) }
                .onSuccess { suggestions ->
                    if (suggestions.isNotEmpty()) {
                        return suggestions
                    }
                }
                .onFailure { error ->
                    lastError = error
                }
        }
        throw lastError ?: IllegalStateException("暂无可用搜索结果")
    }
}

class PhotonPlaceSearchRemoteClient(
    private val endpoint: String = "https://photon.komoot.io/api/",
) : NominatimPlaceSearchRepository.PlaceSearchRemote {

    override suspend fun search(query: String): List<PlaceSuggestion> {
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
        val url = "$endpoint?q=$encoded&limit=5&lang=zh"
        val connection = (java.net.URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6000
            readTimeout = 6000
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
        val root = JSONObject(response)
        val features = root.optJSONArray("features") ?: JSONArray()
        val result = mutableListOf<PlaceSuggestion>()
        for (index in 0 until features.length()) {
            val item = features.optJSONObject(index) ?: continue
            val geometry = item.optJSONObject("geometry")
            val coordinates = geometry?.optJSONArray("coordinates") ?: continue
            if (coordinates.length() < 2) {
                continue
            }
            val lng = coordinates.optDouble(0, Double.NaN)
            val lat = coordinates.optDouble(1, Double.NaN)
            if (lat.isNaN() || lng.isNaN()) {
                continue
            }

            val properties = item.optJSONObject("properties")
            val name = properties?.optString("name").orEmpty().ifBlank { "搜索结果" }
            val city = properties?.optString("city").orEmpty()
            val state = properties?.optString("state").orEmpty()
            val country = properties?.optString("country").orEmpty()
            val subtitle = listOf(city, state, country)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            val id = properties?.optString("osm_id").orEmpty().ifBlank { "photon_$index" }

            result += PlaceSuggestion(
                id = id,
                title = name,
                subtitle = subtitle,
                lat = lat,
                lng = lng,
            )
        }
        return result
    }
}
