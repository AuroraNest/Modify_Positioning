package com.aurora.modifypositioning.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {

    @Query("SELECT * FROM favorite_locations ORDER BY updated_at DESC")
    fun observeFavorites(): Flow<List<FavoriteLocationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(entity: FavoriteLocationEntity)

    @Update
    suspend fun updateFavorite(entity: FavoriteLocationEntity)

    @Delete
    suspend fun deleteFavorite(entity: FavoriteLocationEntity)

    @Query("SELECT * FROM recent_selections ORDER BY selected_at DESC LIMIT 20")
    fun observeRecentSelections(): Flow<List<RecentSelectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecent(entity: RecentSelectionEntity)

    @Query("DELETE FROM recent_selections WHERE id NOT IN (SELECT id FROM recent_selections ORDER BY selected_at DESC LIMIT 20)")
    suspend fun trimRecentTo20()
}
