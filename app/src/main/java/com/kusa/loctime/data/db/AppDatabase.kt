package com.kusa.loctime.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kusa.loctime.data.entity.LocationEntity
import com.kusa.loctime.data.entity.TimeEntryEntity

// Roomデータベースの定義クラス。アプリ全体で1つのインスタンスを共有する（シングルトン）。
// version を上げるときはマイグレーション処理が必要になる。
@Database(
    entities = [LocationEntity::class, TimeEntryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    abstract fun timeEntryDao(): TimeEntryDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        // スレッドセーフなシングルトン取得。@Volatile + synchronized で二重チェックロックを実現する。
        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "loctime.db"
                ).build().also { instance = it }
            }
    }
}
