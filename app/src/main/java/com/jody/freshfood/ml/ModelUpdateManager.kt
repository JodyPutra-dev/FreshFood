package com.jody.freshfood.ml

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import com.jody.freshfood.network.ModelUpdateService

/**
 * Orchestrates OTA model updates: fetch manifest, compare versions, download and verify models,
 * update metadata. Emits UpdateStatus events for UI consumption.
 */
object ModelUpdateManager {

    private const val TAG = "ModelUpdateManager"

    fun checkForUpdates(context: Context): Flow<UpdateStatus> = flow {
        emit(UpdateStatus.Checking)
        val service = try {
            ModelUpdateService.create()
        } catch (e: Exception) {
            emit(UpdateStatus.Error("Failed to create update service: ${e.message}", e))
            return@flow
        }

        val manifest = try {
            service.getManifest()
        } catch (e: Exception) {
            emit(UpdateStatus.Error("Failed to fetch manifest: ${e.message}", e))
            return@flow
        }

        val toUpdate = mutableListOf<com.jody.freshfood.network.dto.ModelInfoDto>()
        for (info in manifest.models) {
            val local = ModelManager.getModelMetadata(context, info.name)
            val localVersion = local?.version ?: 0
            if (info.version > localVersion) {
                toUpdate.add(info)
            }
        }

        if (toUpdate.isEmpty()) {
            emit(UpdateStatus.Success(emptyList()))
            return@flow
        }

        val updated = mutableListOf<String>()
        val downloader = ModelDownloader(context)
        for (info in toUpdate) {
            emit(UpdateStatus.Downloading(info.name, 0))
            val result = downloader.downloadAndVerify(info.name, info.downloadUrl, info.sha256)
            if (result.isSuccess) {
                val metadata = ModelMetadata(
                    modelName = info.name,
                    version = info.version,
                    sha256 = info.sha256,
                    lastUpdated = System.currentTimeMillis()
                )
                ModelManager.updateModelMetadata(context, metadata)
                updated.add(info.name)
            } else {
                emit(UpdateStatus.Error("Failed to update ${info.name}: ${result.exceptionOrNull()?.message}", result.exceptionOrNull()))
                return@flow
            }
        }

        emit(UpdateStatus.Success(updated))
    }.flowOn(Dispatchers.IO)
}
