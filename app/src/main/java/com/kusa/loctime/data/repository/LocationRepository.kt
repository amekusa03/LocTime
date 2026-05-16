package com.kusa.loctime.data.repository

import com.kusa.loctime.data.db.AppDatabase
import com.kusa.loctime.data.entity.LocationEntity
import com.kusa.loctime.data.entity.TimeEntryEntity
import kotlinx.coroutines.flow.Flow

class LocationRepository(private val db: AppDatabase) {

    fun getLocationsFlow(): Flow<List<LocationEntity>> = db.locationDao().getAllFlow()

    suspend fun getLocationById(id: Int): LocationEntity? = db.locationDao().getById(id)

    suspend fun saveLocation(location: LocationEntity): Long = db.locationDao().insert(location)

    suspend fun deleteLocation(location: LocationEntity) = db.locationDao().delete(location)

    fun getTimeEntriesFlow(locationId: Int): Flow<List<TimeEntryEntity>> =
        db.timeEntryDao().getByLocationIdFlow(locationId)

    suspend fun getAllEnabledEntries(): List<TimeEntryEntity> = db.timeEntryDao().getAllEnabled()

    suspend fun saveTimeEntry(entry: TimeEntryEntity): Long = db.timeEntryDao().insert(entry)

    suspend fun deleteTimeEntry(entry: TimeEntryEntity) = db.timeEntryDao().delete(entry)
}
