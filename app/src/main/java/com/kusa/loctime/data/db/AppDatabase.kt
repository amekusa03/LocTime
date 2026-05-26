package com.kusa.loctime.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kusa.loctime.data.entity.LocationEntity
import com.kusa.loctime.data.entity.TimeEntryEntity

// Roomデータベースの定義クラス。アプリ全体で1つのインスタンスを共有する（シングルトン）。
// LocationEntity に offsetMinutes を追加したため、バージョンを 2 に上げます。
@Database(
    entities = [LocationEntity::class, TimeEntryEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    abstract fun timeEntryDao(): TimeEntryDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "loctime.db"
                )
                // 開発中のため、スキーマ不一致時は既存データを破棄して再作成する設定を追加
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
            }
    }
}
