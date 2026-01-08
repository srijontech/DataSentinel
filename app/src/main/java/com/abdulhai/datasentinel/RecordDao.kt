package com.abdulhai.datasentinel

import androidx.room.*

@Dao
interface RecordDao {
    @Query("SELECT * FROM records ORDER BY id DESC")
    suspend fun getAllRecords(): List<MyRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: MyRecord)

    @Delete
    suspend fun deleteRecord(record: MyRecord)

    @Query("SELECT * FROM records WHERE id = :recordId")
    suspend fun getRecordById(recordId: Int): MyRecord?
}