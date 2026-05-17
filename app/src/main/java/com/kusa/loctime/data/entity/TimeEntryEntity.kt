package com.kusa.loctime.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// 場所ごとに設定する「時刻」を表すデータクラス。Roomデータベースの "time_entries" テーブルに対応する。
// locationId で LocationEntity と紐づく。場所が削除されると関連する時刻も自動削除（CASCADE）される。
@Entity(
    tableName = "time_entries",
    foreignKeys = [ForeignKey(
        entity = LocationEntity::class,
        parentColumns = ["id"],
        childColumns = ["locationId"],
        onDelete = ForeignKey.CASCADE // 親（場所）削除時に子（時刻）も連動削除
    )],
    indices = [Index("locationId")] // locationId での検索を高速化するインデックス
)
data class TimeEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0, // 自動採番のID
    val locationId: Int,       // 紐づく場所のID
    val hour: Int,             // 通知する時（0〜23）
    val minute: Int,           // 通知する分（0〜59）
    val message: String,       // 通知に表示するメッセージ
    val isEnabled: Boolean = true // 有効/無効フラグ（無効時はアラームを発火しない）
)
