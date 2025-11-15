package com.jody.freshfood.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.jody.freshfood.data.local.dao.ScanResultDao
import com.jody.freshfood.data.local.entity.ScanResultEntity

@Database(entities = [ScanResultEntity::class], version = 1, exportSchema = false)
abstract class FreshFoodDatabase : RoomDatabase() {
    abstract fun scanResultDao(): ScanResultDao

    companion object {
        @Volatile
        private var INSTANCE: FreshFoodDatabase? = null

        fun getDatabase(context: Context): FreshFoodDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FreshFoodDatabase::class.java,
                    "freshfood_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
