package com.qrfile.storage

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Entity(tableName = "transfer_records")
data class TransferRecordEntity(
    @PrimaryKey val id: String,
    val direction: String,
    val remoteDeviceName: String,
    val fileNamesJson: String,
    val totalBytes: Long,
    val timestampMs: Long,
    val success: Boolean,
)

fun TransferRecordEntity.toTransferRecord() = TransferRecord(
    id = id,
    direction = TransferRecord.Direction.valueOf(direction),
    remoteDeviceName = remoteDeviceName,
    fileNames = Json.decodeFromString(ListSerializer(String.serializer()), fileNamesJson),
    totalBytes = totalBytes,
    timestampMs = timestampMs,
    success = success,
)

fun TransferRecord.toEntity() = TransferRecordEntity(
    id = id,
    direction = direction.name,
    remoteDeviceName = remoteDeviceName,
    fileNamesJson = Json.encodeToString(ListSerializer(String.serializer()), fileNames),
    totalBytes = totalBytes,
    timestampMs = timestampMs,
    success = success,
)
