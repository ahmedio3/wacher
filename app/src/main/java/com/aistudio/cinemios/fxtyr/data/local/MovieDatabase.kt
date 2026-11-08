package com.aistudio.cinemios.fxtyr.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WatchlistEntity::class,
        DownloadEntity::class,
        SubtitleDownloadEntity::class,
        EpisodeWatchStatusEntity::class,
        SeasonMetaEntity::class
    ],
    version = 18,
    exportSchema = false
)
abstract class MovieDatabase : RoomDatabase() {
    abstract val movieDao: MovieDao

    companion object {
        @Volatile
        private var INSTANCE: MovieDatabase? = null

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS ai_messages")
                db.execSQL("DROP TABLE IF EXISTS ai_conversations")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS ai_messages")
                db.execSQL("DROP TABLE IF EXISTS ai_conversations")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Historical migration for column addition on dropped table
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `ai_messages`")
                db.execSQL("DROP TABLE IF EXISTS `ai_conversations`")
                db.execSQL("DROP TABLE IF EXISTS `saved_images`")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Recreate instead of ALTER: Room 2.7 validates column defaults,
                // and the entity declares no SQL defaults. mediaId is backfilled
                // so existing downloads resolve without user action.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `subtitle_downloads_new` (" +
                        "`id` TEXT NOT NULL, " +
                        "`tmdbId` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`mediaType` TEXT NOT NULL, " +
                        "`posterPath` TEXT NOT NULL, " +
                        "`season` INTEGER NOT NULL, " +
                        "`episode` INTEGER NOT NULL, " +
                        "`language` TEXT NOT NULL, " +
                        "`languageCode` TEXT NOT NULL, " +
                        "`source` TEXT NOT NULL, " +
                        "`localFilePath` TEXT NOT NULL, " +
                        "`fileName` TEXT NOT NULL, " +
                        "`batchId` TEXT NOT NULL, " +
                        "`originalUrl` TEXT NOT NULL, " +
                        "`downloadedAt` INTEGER NOT NULL, " +
                        "`mediaId` TEXT NOT NULL, " +
                        "`format` TEXT NOT NULL, " +
                        "`releaseName` TEXT NOT NULL, " +
                        "`fps` REAL, " +
                        "`isSelected` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "INSERT OR REPLACE INTO `subtitle_downloads_new` (" +
                        "`id`, `tmdbId`, `title`, `mediaType`, `posterPath`, `season`, `episode`, " +
                        "`language`, `languageCode`, `source`, `localFilePath`, `fileName`, `batchId`, " +
                        "`originalUrl`, `downloadedAt`, `mediaId`, `format`, `releaseName`, `fps`, `isSelected`) " +
                        "SELECT `id`, `tmdbId`, `title`, `mediaType`, `posterPath`, `season`, `episode`, " +
                        "`language`, `languageCode`, `source`, `localFilePath`, `fileName`, `batchId`, " +
                        "`originalUrl`, `downloadedAt`, " +
                        "CASE WHEN `mediaType` = 'tv' THEN `tmdbId` || '-s' || `season` || '-e' || `episode` ELSE `tmdbId` END, " +
                        "LOWER(SUBSTR(`localFilePath`, LENGTH(`localFilePath`) - 2)), '', NULL, 0 " +
                        "FROM `subtitle_downloads`"
                )
                db.execSQL("DROP TABLE `subtitle_downloads`")
                db.execSQL("ALTER TABLE `subtitle_downloads_new` RENAME TO `subtitle_downloads`")
            }
        }

        fun getDatabase(context: Context): MovieDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MovieDatabase::class.java,
                    "cinemios_database"
                ).addMigrations(
                    MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16,
                    MIGRATION_16_17, MIGRATION_17_18
                )
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
