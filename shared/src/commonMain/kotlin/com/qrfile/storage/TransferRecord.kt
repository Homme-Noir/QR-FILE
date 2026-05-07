package com.qrfile.storage

import kotlinx.serialization.Serializable

@Serializable
data class TransferRecord(
    val id: String,
    val direction: Direction,
    val remoteDeviceName: String,
    val fileNames: List<String>,
    val totalBytes: Long,
    val timestampMs: Long,
    val success: Boolean,
) {
    enum class Direction { SENT, RECEIVED }
}

@Serializable
data class ScanRecord(
    val id: String,
    val rawValue: String,
    val category: Category,
    val timestampMs: Long,
    val isFavourite: Boolean = false,
) {
    enum class Category { URL, WIFI, CONTACT, PRODUCT, TEXT, OTHER }
}
