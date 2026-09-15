package com.notificationforwarder.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.notificationforwarder.app.settings.SettingsStore

@Database(entities = [QueueItem::class, QueueMetrics::class], version = 1, exportSchema = false)
@TypeConverters(QueueConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun queueDao(): QueueDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: SecureInitialization.withInitialization(context) {
                instance ?: synchronized(this) {
                    instance ?: Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "notif_secure.db"
                    ).addCallback(object : Callback() {
                        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                            db.execSQL("INSERT OR IGNORE INTO queue_metrics(id, sentCount, failedCount, expiredCount) VALUES(1, 0, 0, 0)")
                        }

                        override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                            db.execSQL("INSERT OR IGNORE INTO queue_metrics(id, sentCount, failedCount, expiredCount) VALUES(1, 0, 0, 0)")
                        }
                    }).build().let { database ->
                        database.openHelper.writableDatabase
                        if (!SettingsStore(context).markHardeningComplete()) {
                            database.close()
                            error("secure_initialization_marker_failed")
                        }
                        instance = database
                        database
                    }
                }
            }
        }
    }
}
