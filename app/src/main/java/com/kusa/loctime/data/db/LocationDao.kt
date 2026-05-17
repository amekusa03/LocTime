package com.kusa.loctime.data.db

import androidx.room.*
import com.kusa.loctime.data.entity.LocationEntity
import kotlinx.coroutines.flow.Flow

// 場所テーブルへのデータアクセスオブジェクト（DAO）。
// Roomが自動的に実装クラスを生成する。
@Dao
interface LocationDao {
    // 全場所を名前順で取得。Flow で返すことでデータ変更を自動的にUIに反映できる。
    @Query("SELECT * FROM locations ORDER BY name")
    fun getAllFlow(): Flow<List<LocationEntity>>

    // IDで場所を1件取得。存在しない場合はnullを返す。
    @Query("SELECT * FROM locations WHERE id = :id")
    suspend fun getById(id: Int): LocationEntity?

    // 場所を挿入する。id=0 の場合は新規登録（autoGenerate により自動採番）。
    // 注意: REPLACE戦略は使わない。既存レコードを REPLACE すると CASCADE で time_entries が消えるため。
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: LocationEntity): Long

    // 既存の場所を更新する。REPLACE を使わず UPDATE することで time_entries を保持する。
    @Update
    suspend fun update(location: LocationEntity)

    // 場所を削除する。ForeignKey の CASCADE により関連する time_entries も自動削除される。
    @Delete
    suspend fun delete(location: LocationEntity)
}
