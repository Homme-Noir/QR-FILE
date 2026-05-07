package com.qrfile.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_records")
data class ScanRecordEntity(
    @PrimaryKey val id: String,
    val rawValue: String,
    val category: String,
    val timestampMs: Long,
    val isFavourite: Boolean,
)

fun ScanRecordEntity.toScanRecord() = ScanRecord(
    id = id,
    rawValue = rawValue,
    category = ScanRecord.Category.valueOf(category),
    timestampMs = timestampMs,
    isFavourite = isFavourite,
)

fun ScanRecord.toEntity() = ScanRecordEntity(
    id = id,
    rawValue = rawValue,
    category = category.name,
    timestampMs = timestampMs,
    isFavourite = isFavourite,
)
