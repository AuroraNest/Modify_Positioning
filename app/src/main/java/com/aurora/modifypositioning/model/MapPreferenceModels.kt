package com.aurora.modifypositioning.model

data class MapCameraSnapshot(
    val lat: Double,
    val lng: Double,
    val zoom: Float,
)

data class FavoriteLocation(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val createdAt: Long,
    val updatedAt: Long,
)

data class RecentSelection(
    val id: String,
    val label: String,
    val lat: Double,
    val lng: Double,
    val selectedAt: Long,
)
