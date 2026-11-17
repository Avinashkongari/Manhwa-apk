package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.data.model.DownloadStatus

class DownloadTypeConverters {
    @TypeConverter
    fun fromStatus(status: DownloadStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): DownloadStatus =
        runCatching { DownloadStatus.valueOf(value) }.getOrDefault(DownloadStatus.FAILED)
}

@Database(
    entities = [DownloadTaskEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(DownloadTypeConverters::class)
abstract class AxionDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao

    companion object {
        fun create(context: Context): AxionDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AxionDatabase::class.java,
                "axion_manga_db"
            ).fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }
}
