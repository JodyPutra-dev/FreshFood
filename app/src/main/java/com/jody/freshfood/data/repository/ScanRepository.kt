package com.jody.freshfood.data.repository

import android.content.Context
import com.jody.freshfood.data.local.dao.ScanResultDao
import com.jody.freshfood.data.local.database.FreshFoodDatabase
import com.jody.freshfood.data.local.entity.ScanResultEntity

class ScanRepository(private val scanResultDao: ScanResultDao) {

    // Convenience constructor to create repository from Context
    constructor(context: Context) : this(FreshFoodDatabase.getDatabase(context).scanResultDao())

    suspend fun insertScanResult(scanResult: ScanResultEntity): Long {
        return scanResultDao.insert(scanResult)
    }

    suspend fun getAllScanResults(): List<ScanResultEntity> {
        return scanResultDao.getAll()
    }

    suspend fun getScanResultById(id: Int): ScanResultEntity? {
        return scanResultDao.getById(id)
    }

    suspend fun deleteScanResult(scanResult: ScanResultEntity) {
        scanResultDao.delete(scanResult)
    }

    suspend fun deleteAllScanResults() {
        scanResultDao.deleteAll()
    }
}
