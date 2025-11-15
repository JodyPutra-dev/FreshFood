package com.jody.freshfood.network.dto

import com.google.gson.annotations.SerializedName

/**
 * DTO representing the remote manifest.json structure used for OTA model updates.
 * Example:
 * {
 *   "models": [
 *     { "name": "fruitid", "version": 2, "downloadUrl": "https://...", "sha256": "abc..." }
 *   ]
 * }
 */
data class ModelManifestDto(
    @SerializedName("models") val models: List<ModelInfoDto>
)

data class ModelInfoDto(
    @SerializedName("name") val name: String,
    @SerializedName("version") val version: Int,
    @SerializedName("downloadUrl") val downloadUrl: String,
    @SerializedName("sha256") val sha256: String
)
