package com.jody.freshfood.data.model

import com.jody.freshfood.data.local.entity.ScanResultEntity

/**
 * Convert a UI-level [ScanResult] into a persistence [ScanResultEntity].
 * The resulting entity will have id=0 so Room can auto-generate it, and scanDate set to now.
 *
 * This extension works with ScanResult instances created from any source (ML predictions,
 * Intent extras reconstruction, or entity conversion).
 */
fun ScanResult.toEntity(): ScanResultEntity {
    return ScanResultEntity(
        id = 0,
        imagePath = this.imagePath,
        freshnessLabel = this.freshnessLabel,
        confidence = this.confidence,
        scanDate = System.currentTimeMillis(),
        daysLeft = this.daysLeft,
        advice = this.advice
    )
}

/**
 * Convert a persistence [ScanResultEntity] into a UI-level [ScanResult].
 * Entities do not store `insights`; an optional `insights` parameter can be provided.
 */
fun ScanResultEntity.toScanResult(insights: String = ""): ScanResult {
    return ScanResult(
        freshnessLabel = this.freshnessLabel,
        confidence = this.confidence,
        imagePath = this.imagePath,
        insights = insights,
        advice = this.advice,
        daysLeft = this.daysLeft
    )
}
