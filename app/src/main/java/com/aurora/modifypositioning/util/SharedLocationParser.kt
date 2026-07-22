package com.aurora.modifypositioning.util

import com.aurora.modifypositioning.model.TargetLocation
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object SharedLocationParser {
    private const val NUMBER = "[-+]?(?:\\d+(?:\\.\\d+)?|\\.\\d+)"

    private val coordinateRegex = Regex("^\\s*($NUMBER)\\s*[,，]\\s*($NUMBER)\\s*$")
    private val embeddedCoordinateRegex = Regex("^\\s*($NUMBER)\\s*[,，]\\s*($NUMBER)(?:\\s*\\(([^)]*)\\))?.*$")
    private val geoRegex = Regex("^geo:\\s*($NUMBER)\\s*[,，]\\s*($NUMBER)", RegexOption.IGNORE_CASE)
    private val atCoordinateRegex = Regex("@\\s*($NUMBER)\\s*[,，]\\s*($NUMBER)")
    private val latitudeParameterRegex = Regex(
        "(?:[?&#]|\\b)(?:lat|latitude)=($NUMBER)(?:[&#]|$)",
        RegexOption.IGNORE_CASE,
    )
    private val longitudeParameterRegex = Regex(
        "(?:[?&#]|\\b)(?:lng|lon|longitude)=($NUMBER)(?:[&#]|$)",
        RegexOption.IGNORE_CASE,
    )

    fun parse(value: String): TargetLocation? {
        val input = value.trim()
        if (input.isEmpty()) {
            return null
        }
        val decoded = decode(input)

        if (decoded.startsWith("geo:", ignoreCase = true)) {
            val query = queryParameter(decoded, "q")
            query?.let(::parseEmbeddedCoordinate)?.let { return it }
            geoRegex.find(decoded)?.let { match ->
                if (query != null && match.groupValues[1].toDoubleOrNull() == 0.0 &&
                    match.groupValues[2].toDoubleOrNull() == 0.0
                ) {
                    return null
                }
                return target(match.groupValues[1], match.groupValues[2], null)
            }
        }

        atCoordinateRegex.find(decoded)?.let { match ->
            return target(match.groupValues[1], match.groupValues[2], null)
        }

        if (decoded.contains("://")) {
            val latitude = latitudeParameterRegex.find(decoded)?.groupValues?.get(1)
            val longitude = longitudeParameterRegex.find(decoded)?.groupValues?.get(1)
            if (latitude != null && longitude != null) {
                return target(latitude, longitude, null)
            }
        }

        return coordinateRegex.matchEntire(decoded)?.let { match ->
            target(match.groupValues[1], match.groupValues[2], null)
        }
    }

    private fun parseEmbeddedCoordinate(value: String): TargetLocation? {
        val match = embeddedCoordinateRegex.find(value) ?: return null
        return target(
            latitude = match.groupValues[1],
            longitude = match.groupValues[2],
            label = match.groupValues.getOrNull(3),
        )
    }

    private fun target(latitude: String, longitude: String, label: String?): TargetLocation? {
        val target = TargetLocation(
            name = label?.trim()?.takeIf { it.isNotEmpty() } ?: "分享位置",
            latitude = latitude.toDoubleOrNull() ?: return null,
            longitude = longitude.toDoubleOrNull() ?: return null,
        )
        return target.takeIf(TargetLocation::isValid)
    }

    private fun queryParameter(value: String, name: String): String? {
        val query = value.substringAfter('?', "")
        return query.split('&')
            .firstOrNull { it.substringBefore('=').equals(name, ignoreCase = true) }
            ?.substringAfter('=', "")
    }

    private fun decode(value: String): String {
        return runCatching {
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        }.getOrDefault(value)
    }
}
