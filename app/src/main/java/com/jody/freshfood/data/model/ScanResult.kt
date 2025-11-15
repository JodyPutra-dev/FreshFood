package com.jody.freshfood.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * UI/transfer model for scan results.
 *
 * This class is intentionally separate from the Room entity (`ScanResultEntity`) to avoid
 * coupling UI/Intent payloads to persistence concerns (id, scanDate, etc.). Use the
 * extension functions in [ScanResultExtensions] to convert between models when crossing
 * the persistence boundary.
 */
@Parcelize
data class ScanResult(
    val fruitType: String,
    val freshnessLabel: String,
    val confidence: Float,
    val imagePath: String,
    val insights: String = "",
    val advice: String? = null,
    val daysLeft: Int? = null,
) : Parcelable
