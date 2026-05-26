package com.kusa.loctime.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// 登録する「場所」を表すデータクラス。Roomデータベースの "locations" テーブルに対応する。
@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0, // 自動採番のID（新規登録時は0を渡す）
    val name: String,          // 場所名（例: 自宅、会社）
    val latitude: Double,      // 緯度
    val longitude: Double,     // 経度
    val radiusMeters: Float = 1000f, // 判定する半径（メートル）。デフォルト1000m
    val offsetMinutes: Int = 0 // オフセット時間（分）。-30〜30
)
