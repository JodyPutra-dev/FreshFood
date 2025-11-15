package com.jody.freshfood.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jody.freshfood.data.local.entity.ScanResultEntity

@Dao
interface ScanResultDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(scanResult: ScanResultEntity): Long

    @Query("SELECT * FROM scan_results ORDER BY scanDate DESC")
    suspend fun getAll(): List<ScanResultEntity>

    @Query("SELECT * FROM scan_results WHERE id = :id")
    suspend fun getById(id: Int): ScanResultEntity?

    @Delete
    suspend fun delete(scanResult: ScanResultEntity)

    @Query("DELETE FROM scan_results")
    suspend fun deleteAll()
}
