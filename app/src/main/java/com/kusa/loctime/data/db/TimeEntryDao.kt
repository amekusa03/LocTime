package com.kusa.loctime.data.db

import androidx.room.*
import com.kusa.loctime.data.entity.TimeEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeEntryDao {
    @Query("SELECT * FROM time_entries WHERE locationId = :locationId ORDER BY hour, minute")
    fun getByLocationIdFlow(locationId: Int): Flow<List<TimeEntryEntity>>

    @Query("SELECT * FROM time_entries WHERE id = :id")
    suspend fun getById(id: Int): TimeEntryEntity?

    @Query("SELECT * FROM time_entries WHERE isEnabled = 1")
    suspend fun getAllEnabled(): List<TimeEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: TimeEntryEntity): Long

    @Update
    suspend fun update(entry: TimeEntryEntity)

    @Delete
    suspend fun delete(entry: TimeEntryEntity)
}
