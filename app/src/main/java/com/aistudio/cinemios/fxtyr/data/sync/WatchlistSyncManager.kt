package com.aistudio.cinemios.fxtyr.data.sync

import android.content.Context
import com.aistudio.cinemios.fxtyr.data.local.EpisodeWatchStatusEntity
import com.aistudio.cinemios.fxtyr.data.local.WatchlistEntity
import com.aistudio.cinemios.fxtyr.data.repository.MovieRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

/**
 * مزامنة ثنائية الاتجاه (two-way merge) لقائمة المشاهدة مع Firebase Realtime Database.
 * استراتيجية الدمج: latest-updatedAt-wins، مع soft-delete (isDeleted).
 */
object WatchlistSyncManager {

    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()

    suspend fun sync(
        repository: MovieRepository,
        context: Context
    ): Boolean {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: throw IllegalStateException("User not authenticated")

        val ref = database.reference.child("watchlist_sync").child(uid)

        // 1. اقرأ local
        val localItems = repository.getAllWatchlistItems().associateBy { it.id }
        val localEpisodeStatus = repository.getAllEpisodeWatchStatus()

        // 2. اقرأ remote
        val remoteSnapshot = ref.get().await()
        val remoteItems = parseWatchlistSnapshot(remoteSnapshot.child("watchlist"))
        val remoteEpisodeStatus = parseEpisodeStatusSnapshot(remoteSnapshot.child("episode_watch_status"))

        // 3. ادمج watchlist
        val watchlistRef = ref.child("watchlist")
        val mergedWatchlist = mergeWatchlist(localItems, remoteItems)
        for ((id, data) in mergedWatchlist) {
            repository.addToWatchlist(data)
            watchlistRef.child(id).setValue(
                mapOf(
                    "title" to data.title,
                    "posterPath" to data.posterPath,
                    "mediaType" to data.mediaType,
                    "rating" to data.rating,
                    "status" to data.status,
                    "isDeleted" to data.isDeleted,
                    "addedAt" to data.addedAt,
                    "updatedAt" to data.updatedAt
                )
            ).await()
        }

        // 4. ادمج episode watch status
        val episodeRef = ref.child("episode_watch_status")
        val mergedEpisodeStatus = mergeEpisodeStatus(localEpisodeStatus, remoteEpisodeStatus)
        for (status in mergedEpisodeStatus) {
            repository.upsertEpisodeWatchStatus(status)
            val key = "${status.season}_${status.episode}"
            episodeRef.child(status.tmdbId).child(key).setValue(
                mapOf(
                    "watched" to status.watched,
                    "updatedAt" to status.updatedAt
                )
            ).await()
        }

        // 5. سجّل وقت آخر مزامنة
        ref.child("lastSyncedAt").setValue(System.currentTimeMillis()).await()

        return true
    }

    // ---- Helpers ----

    private fun parseWatchlistSnapshot(snapshot: com.google.firebase.database.DataSnapshot): Map<String, Map<String, Any?>> {
        val result = mutableMapOf<String, Map<String, Any?>>()
        for (child in snapshot.children) {
            val value = child.value as? Map<String, Any?> ?: continue
            val key = child.key ?: continue
            result[key] = value
        }
        return result
    }

    private fun parseEpisodeStatusSnapshot(snapshot: com.google.firebase.database.DataSnapshot): List<EpisodeWatchStatusEntity> {
        val result = mutableListOf<EpisodeWatchStatusEntity>()
        for (tmdbChild in snapshot.children) {
            val tmdbId = tmdbChild.key ?: continue
            for (statusChild in tmdbChild.children) {
                val value = statusChild.value as? Map<String, Any?> ?: continue
                val parts = statusChild.key?.split("_") ?: continue
                if (parts.size != 2) continue
                val season = parts[0].toIntOrNull() ?: continue
                val episode = parts[1].toIntOrNull() ?: continue
                result.add(
                    EpisodeWatchStatusEntity(
                        tmdbId = tmdbId,
                        season = season,
                        episode = episode,
                        watched = value["watched"] as? Boolean ?: false,
                        updatedAt = (value["updatedAt"] as? Number)?.toLong() ?: 0L
                    )
                )
            }
        }
        return result
    }

    /**
     * دمج watchlist: latest-updatedAt-wins مع الاحتفاظ بـ isDeleted.
     */
    private fun mergeWatchlist(
        local: Map<String, WatchlistEntity>,
        remote: Map<String, Map<String, Any?>>
    ): Map<String, WatchlistEntity> {
        val result = mutableMapOf<String, WatchlistEntity>()
        val allIds = local.keys + remote.keys

        for (id in allIds) {
            val l = local[id]
            val r = remote[id]

            result[id] = when {
                l != null && r == null -> l    // موجود محلياً فقط
                l == null && r != null -> remoteToWatchlist(id, r)  // موجود سحابياً فقط
                l != null && r != null -> {
                    // موجود في الاثنين → latest-updatedAt wins
                    val rTime = (r["updatedAt"] as? Number)?.toLong() ?: 0L
                    if (l.updatedAt >= rTime) l else remoteToWatchlist(id, r)
                }
                else -> continue
            }
        }
        return result
    }

    /**
     * دمج episode watch status: كل إدخال بـ (tmdbId, season, episode) كمفتاح.
     */
    private fun mergeEpisodeStatus(
        local: List<EpisodeWatchStatusEntity>,
        remote: List<EpisodeWatchStatusEntity>
    ): List<EpisodeWatchStatusEntity> {
        val localMap = local.associateBy { "${it.tmdbId}_${it.season}_${it.episode}" }
        val remoteMap = remote.associateBy { "${it.tmdbId}_${it.season}_${it.episode}" }
        val result = mutableListOf<EpisodeWatchStatusEntity>()
        val allKeys = localMap.keys + remoteMap.keys

        for (key in allKeys) {
            val l = localMap[key]
            val r = remoteMap[key]
            result.add(when {
                l != null && r == null -> l
                l == null && r != null -> r
                l != null && r != null -> if (l.updatedAt >= r.updatedAt) l else r
                else -> continue
            })
        }
        return result
    }

    private fun remoteToWatchlist(id: String, data: Map<String, Any?>): WatchlistEntity {
        return WatchlistEntity(
            id = id,
            title = data["title"] as? String ?: "",
            posterPath = data["posterPath"] as? String ?: "",
            mediaType = data["mediaType"] as? String ?: "movie",
            rating = (data["rating"] as? Number)?.toDouble() ?: 0.0,
            status = data["status"] as? String ?: "PLAN_TO_WATCH",
            isDeleted = data["isDeleted"] as? Boolean ?: false,
            addedAt = (data["addedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }
}
