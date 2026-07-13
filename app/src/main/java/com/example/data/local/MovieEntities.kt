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
    val addedAt: Long = System.currentTimeMillis(),
    val status: String = "PLAN_TO_WATCH", // PLAN_TO_WATCH | WATCHING | COMPLETED
    val isDeleted: Boolean = false,       // soft-delete للمزامنة السحابية
    val updatedAt: Long = System.currentTimeMillis() // timestamp لكل تعديل
)

@Entity(
    tableName = "episode_watch_status",
    primaryKeys = ["tmdbId", "season", "episode"]
)
data class EpisodeWatchStatusEntity(
    val tmdbId: String,
    val season: Int,
    val episode: Int,
    val watched: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "season_meta",
    primaryKeys = ["tmdbId", "seasonNumber"]
)
data class SeasonMetaEntity(
    val tmdbId: Int,
    val seasonNumber: Int,
    val episodeCount: Int,
    val name: String,
    val lastFetchedAt: Long = System.currentTimeMillis()
)

data class LocalVideoFile(
    val id: String,          // file path hash
    val name: String,        // display name
    val filePath: String,    // absolute path
    val size: Long = 0L,     // file size in bytes
    val durationMs: Long = 0L,
    val playlistId: String = ""  // "" if not in a playlist
)

data class LocalPlaylist(
    val id: String,
    val name: String,
    val videoIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

// Simple JSON serialization for user-picked files (list of path strings)
object UserPickedFileList {
    fun toJson(paths: List<String>): String {
        val arr = org.json.JSONArray(paths)
        return arr.toString()
    }
    fun fromJson(json: String): List<String> {
        val arr = org.json.JSONArray(json)
        val result = mutableListOf<String>()
        for (i in 0 until arr.length()) result.add(arr.getString(i))
        return result
    }
}

// Simple JSON serialization for playlists
object LocalPlaylistList {
    fun toJson(playlists: List<LocalPlaylist>): String {
        val arr = org.json.JSONArray()
        playlists.forEach { pl ->
            val obj = org.json.JSONObject()
            obj.put("id", pl.id)
            obj.put("name", pl.name)
            obj.put("createdAt", pl.createdAt)
            val vids = org.json.JSONArray(pl.videoIds)
            obj.put("videoIds", vids)
            arr.put(obj)
        }
        return arr.toString()
    }
    fun fromJson(json: String): List<LocalPlaylist> {
        val arr = org.json.JSONArray(json)
        val result = mutableListOf<LocalPlaylist>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val vids = mutableListOf<String>()
            val vidsArr = obj.getJSONArray("videoIds")
            for (j in 0 until vidsArr.length()) vids.add(vidsArr.getString(j))
            result.add(LocalPlaylist(
                id = obj.getString("id"),
                name = obj.getString("name"),
                videoIds = vids,
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            ))
        }
        return result
    }
}

@Entity(tableName = "subtitle_downloads")
data class SubtitleDownloadEntity(
    @PrimaryKey val id: String,              // tmdbId_s{season}e{matchedEpisode}_{languageCode}[_{unique}]
    val tmdbId: String,                      // TMDB ID
    val title: String,                       // "Inception" or "Breaking Bad S1E3"
    val mediaType: String,                   // "movie" or "tv"
    val posterPath: String = "",             // poster URL for display
    val season: Int = 0,
    val episode: Int = 0,
    val language: String,                    // "Arabic" (display name)
    val languageCode: String,                // "ar"
    val source: String,                      // "Subdl" / "MovieBox" / "OpenSubtitles"
    val localFilePath: String,               // path inside app storage
    val fileName: String = "",               // original file name / release name from search result
    val batchId: String = "",                // UUID grouping all files from one download operation
    val originalUrl: String = "",            // original download URL
    val downloadedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "activity_log")
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,        // "OPENED" | "DOWNLOADED" | "WATCHED"
    val title: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String, // tmdb_id + optional suffix for episodes
    val mediaId: String, // tmdb_id of parent movie or tv show
    val title: String,
    val posterPath: String,
    val stillPath: String = "",   // episode-level still image (16:9) from TMDB
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
