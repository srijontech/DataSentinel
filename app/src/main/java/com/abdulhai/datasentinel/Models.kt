package com.abdulhai.datasentinel

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "records")
data class MyRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val category: String,
    val subCategory: String,
    val content: String,
    val reminderTime: Long = 0L // NEW: Stores the scheduled notification time
)