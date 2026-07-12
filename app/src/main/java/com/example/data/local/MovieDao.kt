package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MovieDao {
    // ---- Watchlist ----
    @Query("SELECT * FROM watchlist WHERE isDeleted = 0 ORDER BY addedAt DESC")
    fun getWatchlist(): Flow<List<WatchlistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchlist(item: WatchlistEntity)

    @Query("UPDATE watchlist SET isDeleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteWatchlistById(id: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM watchlist WHERE id = :id LIMIT 1")
    fun getWatchlistByIdFlow(id: String): Flow<WatchlistEntity?>

    @Query("SELECT * FROM watchlist WHERE id = :id LIMIT 1")
    suspend fun getWatchlistById(id: String): WatchlistEntity?

    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC")
    suspend fun getAllWatchlistItems(): List<WatchlistEntity>

    // ---- Downloads ----
    @Query("SELECT * FROM downloads ORDER BY addedAt DESC")
    fun getDownloads(): Flow<List<DownloadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(item: DownloadEntity)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownloadById(id: String)

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getDownloadById(id: String): DownloadEntity?

    // ---- Chat ----
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getLocalChatMessages(): Flow<List<ChatEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessages(messages: List<ChatEntity>)

    @Query("DELETE FROM chat_messages")
    suspend fun clearChatMessages()

    // ---- Subtitle downloads ----
    @Query("SELECT * FROM subtitle_downloads ORDER BY downloadedAt DESC")
    fun getSubtitleDownloads(): Flow<List<SubtitleDownloadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubtitleDownload(item: SubtitleDownloadEntity)

    @Query("DELETE FROM subtitle_downloads WHERE id = :id")
    suspend fun deleteSubtitleDownload(vararg id: String)

    // ---- Episode Watch Status ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEpisodeWatchStatus(item: EpisodeWatchStatusEntity)

    @Query("SELECT * FROM episode_watch_status WHERE tmdbId = :tmdbId AND season = :season")
    fun getEpisodeWatchStatusForSeason(tmdbId: String, season: Int): Flow<List<EpisodeWatchStatusEntity>>

    @Query("SELECT * FROM episode_watch_status WHERE tmdbId = :tmdbId AND season = :season AND episode = :episode LIMIT 1")
    suspend fun getEpisodeWatchStatus(tmdbId: String, season: Int, episode: Int): EpisodeWatchStatusEntity?

    @Query("SELECT COUNT(*) FROM episode_watch_status WHERE tmdbId = :tmdbId AND watched = 1")
    fun getWatchedCountForTvShow(tmdbId: String): Flow<Int>

    @Query("SELECT * FROM episode_watch_status ORDER BY tmdbId, season, episode")
    suspend fun getAllEpisodeWatchStatus(): List<EpisodeWatchStatusEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEpisodeWatchStatusBatch(items: List<EpisodeWatchStatusEntity>)

    // ---- Season metadata cache (offline fallback for totals) ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSeasonMeta(items: List<SeasonMetaEntity>)

    @Query("SELECT * FROM season_meta WHERE tmdbId = :id")
    suspend fun getSeasonMeta(id: Int): List<SeasonMetaEntity>

    // ---- Saved Images (Browser) ----
    @Query("SELECT * FROM saved_images ORDER BY downloadedAt DESC")
    fun getSavedImages(): Flow<List<SavedImageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedImage(item: SavedImageEntity)

    @Query("DELETE FROM saved_images WHERE id = :id")
    suspend fun deleteSavedImage(id: String)

    @Query("SELECT * FROM saved_images WHERE id = :id LIMIT 1")
    suspend fun getSavedImageById(id: String): SavedImageEntity?
}
