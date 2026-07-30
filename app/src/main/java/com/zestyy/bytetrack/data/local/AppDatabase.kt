package com.zestyy.bytetrack.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [DataUsageSample::class, ScreenTimeSession::class, AppInfoEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dataUsageDao(): DataUsageDao
    abstract fun screenTimeDao(): ScreenTimeDao
    abstract fun appInfoDao(): AppInfoDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "byte_track.db"
                ).build().also { instance = it }
            }
    }
}
