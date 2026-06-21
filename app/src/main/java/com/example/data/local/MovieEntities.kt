package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val username: String,
    val text: String,
    val timestamp: Long,
    val avatarBase64: String,
    val repliedToId: String = "",
    val repliedToName: String = "",
    val repliedToText: String = ""
)

@Entity(tableName = "watchlist")
data class WatchlistEntity(
    @PrimaryKey val id: String, // tmdb_id or imdb_id
    val title: String,
    val posterPath: String,
    val mediaType: String, // "movie" or "tv"
    val rating: Double,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String, // tmdb_id + optional suffix for episodes
    val mediaId: String, // tmdb_id of parent movie or tv show
    val title: String,
    val posterPath: String,
    val mediaType: String, // "movie" or "tv"
    val season: Int = 0, // 0 if movie
    val episode: Int = 0, // 0 if movie
    val progress: Int = 0, // 0 to 100
    val status: String, // "downloading" or "paused" or "completed"
    val localFilePath: String = "",
    val quality: String = "1080p",
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val downloadSpeed: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    val sourceUrl: String = ""
)
