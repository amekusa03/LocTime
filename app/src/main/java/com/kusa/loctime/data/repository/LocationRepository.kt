package com.kusa.loctime.data.repository

import com.kusa.loctime.data.db.AppDatabase
import com.kusa.loctime.data.entity.LocationEntity
import com.kusa.loctime.data.entity.TimeEntryEntity
import kotlinx.coroutines.flow.Flow

// データベースへのアクセスを一元管理するリポジトリ。
// ViewModelはこのクラスを通じてデータ操作を行い、DAOに直接触れない。
class LocationRepository(private val db: AppDatabase) {

    fun getLocationsFlow(): Flow<List<LocationEntity>> = db.locationDao().getAllFlow()

    suspend fun getLocationById(id: Int): LocationEntity? = db.locationDao().getById(id)

    // 新規（id=0）は insert、既存（id!=0）は update を使い分ける。
    // LocationDao で REPLACE を使うと CASCADE により time_entries が削除されるため、
    // 既存の場所を保存する際は必ず update を使う。
    suspend fun saveLocation(location: LocationEntity): Long {
        return if (location.id == 0) {
            db.locationDao().insert(location)
        } else {
            db.locationDao().update(location)
            location.id.toLong()
        }
    }

    suspend fun deleteLocation(location: LocationEntity) = db.locationDao().delete(location)

    fun getTimeEntriesFlow(locationId: Int): Flow<List<TimeEntryEntity>> =
        db.timeEntryDao().getByLocationIdFlow(locationId)

    suspend fun getAllEnabledEntries(): List<TimeEntryEntity> = db.timeEntryDao().getAllEnabled()

    suspend fun saveTimeEntry(entry: TimeEntryEntity): Long = db.timeEntryDao().insert(entry)

    suspend fun deleteTimeEntry(entry: TimeEntryEntity) = db.timeEntryDao().delete(entry)
}
