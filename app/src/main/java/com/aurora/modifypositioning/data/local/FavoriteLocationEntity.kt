package com.aurora.modifypositioning.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_locations")
data class FavoriteLocationEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
