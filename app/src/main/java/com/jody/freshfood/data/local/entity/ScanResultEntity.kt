package com.jody.freshfood.data.local.entity

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(
    tableName = "scan_results",
    indices = [Index(value = ["scanDate"]), Index(value = ["fruitType"])],
)
data class ScanResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val imagePath: String,
    val fruitType: String,
    val freshnessLabel: String,
    val confidence: Float,
    val scanDate: Long,
    val daysLeft: Int? = null,
    val advice: String? = null,
) : Parcelable
