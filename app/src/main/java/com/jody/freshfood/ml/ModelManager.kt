package com.jody.freshfood.ml

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

object ModelManager {
    private const val TAG = "ModelManager"
    private const val PREFS = "freshfood_models"
    private const val MODEL_NAME = "fruit_ripeness_model"

    private val bundledModels = emptyList<String>()

    @Synchronized
    fun initialize(context: Context) {
        try {
            val modelsDir = File(context.filesDir, "models")
            if (!modelsDir.exists()) {
                val created = modelsDir.mkdirs()
                Log.i(TAG, "Created models dir: $created -> ${modelsDir.absolutePath}")
            }

            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

            for (assetName in bundledModels) {
                val outFile = File(modelsDir, assetName)
                if (!outFile.exists()) {
                    // copy from assets
                    try {
                        context.assets.open("models/$assetName").use { input ->
                            FileOutputStream(outFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        // compute sha256
                        val sha = sha256(outFile)
                        val metadata = ModelMetadata(
                            modelName = assetName.removeSuffix(".tflite"),
                            version = 1,
                            sha256 = sha,
                            lastUpdated = System.currentTimeMillis()
                        )
                        prefs.edit().putString("metadata_${metadata.modelName}", metadata.toJson()).apply()
                        Log.i(TAG, "Copied and registered model: ${assetName} sha=$sha")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to copy asset $assetName: ${e.message}")
                    }
                } else {
                    // ensure metadata exists
                    val modelKey = assetName.removeSuffix(".tflite")
                    val existing = prefs.getString("metadata_$modelKey", null)
                    if (existing == null) {
                        val sha = sha256(outFile)
                        val metadata = ModelMetadata(modelName = modelKey, version = 1, sha256 = sha, lastUpdated = System.currentTimeMillis())
                        prefs.edit().putString("metadata_$modelKey", metadata.toJson()).apply()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "ModelManager initialization failed: ${e.message}")
        }
    }

    fun getModelMetadata(context: Context, modelName: String): ModelMetadata? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString("metadata_$modelName", null)
        return ModelMetadata.fromJson(json)
    }

    fun updateModelMetadata(context: Context, metadata: ModelMetadata) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString("metadata_${metadata.modelName}", metadata.toJson()).apply()
    }

    fun getModelFile(context: Context, modelName: String): File {
        val modelsDir = File(context.filesDir, "models")
        return File(modelsDir, "$modelName.tflite")
    }

    /**
     * Get TFLite predictor with OTA model priority.
     * Downloaded models override bundled assets with automatic fallback.
     * 
     * @param context Application context for accessing model files
     * @return TFLitePredictor instance using best available model
     */
    fun getPredictor(context: Context): TFLitePredictor {
        val metadata = getModelMetadata(context, MODEL_NAME)
        val modelFile = getModelFile(context, MODEL_NAME)
        
        return if (metadata != null && modelFile.exists()) {
            Log.i(TAG, "Using downloaded model v${metadata.version}: ${modelFile.absolutePath}")
            RealTFLitePredictor(context, modelFile.absolutePath)
        } else {
            Log.i(TAG, "Using bundled asset model")
            RealTFLitePredictor(context)
        }
    }

    /**
     * Get human-readable info about the active model.
     * 
     * @param context Application context
     * @return String like "Model: fruit_ripeness_model v2 (OTA)" or "Model: bundled asset"
     */
    fun getActiveModelInfo(context: Context): String {
        val metadata = getModelMetadata(context, MODEL_NAME)
        val modelFile = getModelFile(context, MODEL_NAME)
        
        return if (metadata != null && modelFile.exists()) {
            "Model: ${metadata.modelName} v${metadata.version} (OTA)"
        } else {
            "Model: bundled asset"
        }
    }

    /**
     * Compute SHA-256 for a file. Visible within module for reuse by downloader.
     */
    internal fun sha256(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = file.readBytes()
            val hash = digest.digest(bytes)
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Return all registered model metadata from SharedPreferences.
     */
    fun getAllModelMetadata(context: Context): List<ModelMetadata> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val out = mutableListOf<ModelMetadata>()
        for (entry in prefs.all) {
            val key = entry.key
            if (key.startsWith("metadata_")) {
                val json = entry.value as? String
                ModelMetadata.fromJson(json)?.let { out.add(it) }
            }
        }
        return out
    }

    /**
     * Clear all stored model metadata. Call this when models are fully managed by server.
     */
    fun clearAllMetadata(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        Log.i(TAG, "Cleared all model metadata")
    }
}
