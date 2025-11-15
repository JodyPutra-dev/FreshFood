package com.jody.freshfood.ml

import org.json.JSONObject

data class ModelMetadata(
    val modelName: String,
    val version: Int,
    val sha256: String,
    val lastUpdated: Long
) {
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("modelName", modelName)
        obj.put("version", version)
        obj.put("sha256", sha256)
        obj.put("lastUpdated", lastUpdated)
        return obj.toString()
    }

    companion object {
        fun fromJson(json: String?): ModelMetadata? {
            if (json == null) return null
            return try {
                val obj = JSONObject(json)
                ModelMetadata(
                    modelName = obj.optString("modelName"),
                    version = obj.optInt("version", 1),
                    sha256 = obj.optString("sha256"),
                    lastUpdated = obj.optLong("lastUpdated", 0L)
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
