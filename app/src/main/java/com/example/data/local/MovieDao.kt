package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MovieDao {
    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC")
    fun getWatchlist(): Flow<List<WatchlistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchlist(item: WatchlistEntity)

    @Query("DELETE FROM watchlist WHERE id = :id")
    suspend fun deleteWatchlistById(id: String)

    @Query("SELECT * FROM watchlist WHERE id = :id LIMIT 1")
    fun getWatchlistByIdFlow(id: String): Flow<WatchlistEntity?>

    @Query("SELECT * FROM watchlist WHERE id = :id LIMIT 1")
    suspend fun getWatchlistById(id: String): WatchlistEntity?

    @Query("SELECT * FROM downloads ORDER BY addedAt DESC")
    fun getDownloads(): Flow<List<DownloadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(item: DownloadEntity)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownloadById(id: String)

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getDownloadById(id: String): DownloadEntity?
    
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getLocalChatMessages(): Flow<List<ChatEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessages(messages: List<ChatEntity>)

    @Query("DELETE FROM chat_messages")
    suspend fun clearChatMessages()
}
