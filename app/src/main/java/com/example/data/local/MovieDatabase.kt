package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.ai.data.AiConversationEntity
import com.example.ai.data.AiMessageEntity

@Database(entities = [WatchlistEntity::class, DownloadEntity::class, SubtitleDownloadEntity::class, EpisodeWatchStatusEntity::class, SavedImageEntity::class, SeasonMetaEntity::class, AiConversationEntity::class, AiMessageEntity::class], version = 14, exportSchema = false)
abstract class MovieDatabase : RoomDatabase() {
    abstract val movieDao: MovieDao
    abstract val aiDao: com.example.ai.data.AiDao

    companion object {
        @Volatile
        private var INSTANCE: MovieDatabase? = null

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS ai_messages")
                db.execSQL("DROP TABLE IF EXISTS ai_conversations")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ai_conversations` (" +
                            "`id` TEXT NOT NULL, " +
                            "`title` TEXT NOT NULL, " +
                            "`providerType` TEXT NOT NULL, " +
                            "`modelId` TEXT NOT NULL, " +
                            "`reasoningEnabled` INTEGER NOT NULL DEFAULT 0, " +
                            "`createdAt` INTEGER NOT NULL, " +
                            "`updatedAt` INTEGER NOT NULL, " +
                            "`messageCount` INTEGER NOT NULL DEFAULT 0, " +
                            "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ai_messages` (" +
                            "`id` TEXT NOT NULL, " +
                            "`conversationId` TEXT NOT NULL, " +
                            "`role` TEXT NOT NULL, " +
                            "`content` TEXT NOT NULL, " +
                            "`reasoningContent` TEXT, " +
                            "`toolCallsJson` TEXT, " +
                            "`toolResultsJson` TEXT, " +
                            "`imageUrlsJson` TEXT, " +
                            "`createdAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`), " +
                            "FOREIGN KEY(`conversationId`) REFERENCES `ai_conversations`(`id`) ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_messages_conversationId` ON `ai_messages`(`conversationId`)")
            }
        }

        fun getDatabase(context: Context): MovieDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MovieDatabase::class.java,
                    "cinemios_database"
                ).addMigrations(MIGRATION_13_14)
                    .fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
