package com.kusa.loctime.data.db

import androidx.room.*
import com.kusa.loctime.data.entity.TimeEntryEntity
import kotlinx.coroutines.flow.Flow

// 時刻エントリテーブルへのデータアクセスオブジェクト（DAO）。
@Dao
interface TimeEntryDao {
    // 指定した場所IDに紐づく時刻を時刻順で取得。Flow で返すことで自動的にUIに反映される。
    @Query("SELECT * FROM time_entries WHERE locationId = :locationId ORDER BY hour, minute")
    fun getByLocationIdFlow(locationId: Int): Flow<List<TimeEntryEntity>>

    // IDで時刻エントリを1件取得。アラーム発火時に有効確認で使用する。
    @Query("SELECT * FROM time_entries WHERE id = :id")
    suspend fun getById(id: Int): TimeEntryEntity?

    // 有効な全時刻エントリとその場所のオフセット時間を取得。端末再起動時のアラーム復元で使用する。
    @Query("""
        SELECT t.*, l.offsetMinutes 
        FROM time_entries t
        JOIN locations l ON t.locationId = l.id
        WHERE t.isEnabled = 1
    """)
    suspend fun getAllEnabledWithOffset(): List<TimeEntryWithOffset>

    // 有効な全時刻エントリを取得。
    @Query("SELECT * FROM time_entries WHERE isEnabled = 1")
    suspend fun getAllEnabled(): List<TimeEntryEntity>

    // 時刻エントリを挿入または更新する。id=0 なら新規、それ以外は上書き。
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: TimeEntryEntity): Long

    @Update
    suspend fun update(entry: TimeEntryEntity)

    @Delete
    suspend fun delete(entry: TimeEntryEntity)
}

data class TimeEntryWithOffset(
    @Embedded val entry: TimeEntryEntity,
    val offsetMinutes: Int
)
