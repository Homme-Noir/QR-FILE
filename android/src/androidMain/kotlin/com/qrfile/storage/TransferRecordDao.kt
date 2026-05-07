package com.qrfile.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: TransferRecordEntity)

    @Query("SELECT * FROM transfer_records ORDER BY timestampMs DESC")
    fun observeAll(): Flow<List<TransferRecordEntity>>

    @Query("DELETE FROM transfer_records")
    suspend fun deleteAll()
}
