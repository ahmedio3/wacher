package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [WatchlistEntity::class, DownloadEntity::class, ChatEntity::class, SubtitleDownloadEntity::class, EpisodeWatchStatusEntity::class, SavedImageEntity::class, SeasonMetaEntity::class], version = 12, exportSchema = false)
abstract class MovieDatabase : RoomDatabase() {
    abstract val movieDao: MovieDao

    companion object {
        @Volatile
        private var INSTANCE: MovieDatabase? = null

        // v9 -> v10: add season_meta table for offline season-total caching (additive, non-destructive)
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS season_meta (" +
                            "tmdbId INTEGER NOT NULL, " +
                            "seasonNumber INTEGER NOT NULL, " +
                            "episodeCount INTEGER NOT NULL, " +
                            "name TEXT NOT NULL, " +
                            "lastFetchedAt INTEGER NOT NULL, " +
                            "PRIMARY KEY(tmdbId, seasonNumber))"
                )
            }
        }

        // v10 -> v11: add activity_log table for the history/activity feature
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS activity_log (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, type TEXT NOT NULL, title TEXT NOT NULL, timestamp INTEGER NOT NULL)")
            }
        }

        // v11 -> v12: drop obsolete activity_log table (now stored in Firebase RTDB)
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS activity_log")
            }
        }

        fun getDatabase(context: Context): MovieDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MovieDatabase::class.java,
                    "cinemios_database"
                ).addMigrations(MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
