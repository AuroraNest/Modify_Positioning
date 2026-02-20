package com.aurora.modifypositioning.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [FavoriteLocationEntity::class, RecentSelectionEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class LocationDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao

    companion object {
        @Volatile
        private var INSTANCE: LocationDatabase? = null

        fun get(context: Context): LocationDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context,
                    LocationDatabase::class.java,
                    "location_db",
                ).fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
