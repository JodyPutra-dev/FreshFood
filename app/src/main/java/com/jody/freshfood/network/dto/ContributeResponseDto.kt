package com.jody.freshfood.network.dto

import com.google.gson.annotations.SerializedName

data class ContributeResponseDto(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("message")
    val message: String? = null,
    @SerializedName("contributionId")
    val contributionId: String? = null
)
