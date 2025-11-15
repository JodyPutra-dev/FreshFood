package com.jody.freshfood.network.dto

import com.google.gson.annotations.SerializedName

data class ContributeRequestDto(
    @SerializedName("fruitType")
    val fruitType: String,
    @SerializedName("freshnessLevel")
    val freshnessLevel: String,
    @SerializedName("imageBase64")
    val imageBase64: String,
    @SerializedName("timestamp")
    val timestamp: Long,
    @SerializedName("appVersion")
    val appVersion: String
)
