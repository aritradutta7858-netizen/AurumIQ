package com.aurumiq.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.aurumiq.app.data.model.MarketRecord

@Database(
    entities = [MarketRecord::class],
    version = 1,
    exportSchema = true
)
abstract class AurumDatabase : RoomDatabase() {

    abstract fun marketRecordDao(): MarketRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AurumDatabase? = null

        fun getInstance(context: Context): AurumDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AurumDatabase::class.java,
                    "aurumiq_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
