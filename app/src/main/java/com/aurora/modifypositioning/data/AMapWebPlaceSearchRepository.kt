package com.aurora.modifypositioning.data

import com.aurora.modifypositioning.model.PlaceSuggestion
import com.aurora.modifypositioning.util.AppSessionMetrics
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class AMapWebPlaceSearchRepository(
    private val apiKey: String,
    private val endpoint: String = "https://restapi.amap.com/v3/assistant/inputtips",
    private val cacheTtlMillis: Long = 5 * 60 * 1000L,
) : PlaceSearchRepository {

    private val cache = LinkedHashMap<String, CacheEntry>()

    override suspend fun autocomplete(query: String): List<PlaceSuggestion> {
        val normalized = query.trim().lowercase()
        if (normalized.length < 2) {
            return emptyList()
        }
        if (apiKey.isBlank()) {
            throw IllegalStateException("高德搜索未配置 Key")
        }

        val now = System.currentTimeMillis()
        val cached = synchronized(cache) { cache[normalized] }
        if (cached != null && now - cached.timestamp <= cacheTtlMillis) {
            return cached.suggestions
        }

        AppSessionMetrics.increaseSearchRequests()
        val suggestions = runCatching {
            withContext(Dispatchers.IO) {
                requestSuggestions(normalized)
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

    private fun requestSuggestions(query: String): List<PlaceSuggestion> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val url = "$endpoint?key=$apiKey&keywords=$encoded&datatype=all&citylimit=false&output=JSON"
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
                throw IllegalStateException("高德搜索异常: HTTP $responseCode")
            }
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            parseResponse(response)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseResponse(response: String): List<PlaceSuggestion> {
        val root = JSONObject(response)
        val status = root.optString("status")
        if (status != "1") {
            val info = root.optString("info").ifBlank { "高德搜索失败" }
            val infocode = root.optString("infocode")
            throw IllegalStateException(if (infocode.isNotBlank()) "$info($infocode)" else info)
        }

        val tips = root.optJSONArray("tips") ?: JSONArray()
        val result = mutableListOf<PlaceSuggestion>()
        for (index in 0 until tips.length()) {
            val item = tips.optJSONObject(index) ?: continue
            val locationRaw = item.optString("location").trim()
            if (!locationRaw.contains(',')) {
                continue
            }
            val locationParts = locationRaw.split(',')
            if (locationParts.size < 2) {
                continue
            }
            val lng = locationParts[0].toDoubleOrNull() ?: continue
            val lat = locationParts[1].toDoubleOrNull() ?: continue
            val title = item.optString("name").ifBlank { "搜索结果" }
            val district = item.optString("district").trim()
            val address = item.optString("address").trim()
            val subtitle = listOf(district, address)
                .filter { it.isNotBlank() && it != "[]" }
                .joinToString(" · ")
            val id = item.optString("id").ifBlank { "amap_$index" }
            result += PlaceSuggestion(
                id = id,
                title = title,
                subtitle = subtitle,
                lat = lat,
                lng = lng,
            )
        }
        return result
    }

    private fun resolveFriendlyMessage(throwable: Throwable): String {
        return when (throwable) {
            is SocketTimeoutException -> "高德搜索超时，请重试"
            is UnknownHostException -> "网络不可用，请检查后重试"
            is IllegalStateException -> {
                val message = throwable.message.orEmpty()
                when {
                    message.contains("HTTP 4") -> "高德搜索请求异常，请稍后重试"
                    message.contains("HTTP 5") -> "高德搜索服务繁忙，请稍后重试"
                    message.contains("INVALID_USER_KEY") -> "高德 Key 无效，请检查配置"
                    message.contains("USERKEY_PLAT_NOMATCH") || message.contains("10009") ->
                        "高德搜索 Key 平台不匹配：请使用“Web服务”类型 Key"
                    message.contains("DAILY_QUERY_OVER_LIMIT") -> "高德配额已用完，请明日再试"
                    else -> message.ifBlank { "高德搜索失败，请稍后再试" }
                }
            }
            else -> throwable.message ?: "高德搜索失败，请稍后再试"
        }
    }

    private data class CacheEntry(
        val timestamp: Long,
        val suggestions: List<PlaceSuggestion>,
    )
}
