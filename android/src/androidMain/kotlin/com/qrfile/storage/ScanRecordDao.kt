package com.qrfile.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: ScanRecordEntity)

    @Query("SELECT * FROM scan_records ORDER BY timestampMs DESC")
    fun observeAll(): Flow<List<ScanRecordEntity>>

    @Query("SELECT * FROM scan_records WHERE rawValue LIKE '%' || :query || '%' ORDER BY timestampMs DESC")
    fun search(query: String): Flow<List<ScanRecordEntity>>

    @Query("DELETE FROM scan_records")
    suspend fun deleteAll()
}
