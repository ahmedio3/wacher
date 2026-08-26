package com.aistudio.cinemios.fxtyr.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

data class UserProfile(
    val id: String = "",
    val name: String = "",
    val username: String = "",
    val avatarBase64: String = "",
    val avatarUrl: String = "",
    val bio: String = ""
)

object UserManager {
    private val db = FirebaseDatabase.getInstance().reference

    private val profileCache = mutableMapOf<String, UserProfile>()

    suspend fun saveProfile(userId: String, profile: UserProfile): Boolean {
        return try {
            val oldProfile = getProfile(userId)

            if (oldProfile?.username != profile.username) {
                val exists = checkUsernameExists(profile.username)
                if (exists) return false

                if (oldProfile?.username?.isNotEmpty() == true) {
                    db.child("usernames").child(oldProfile.username).removeValue().await()
                }
            }

            db.child("users").child(userId).setValue(profile).await()
            if (profile.username.isNotEmpty()) {
                db.child("usernames").child(profile.username).setValue(userId).await()
            }
            profileCache[userId] = profile

            if (oldProfile != null && (oldProfile.name != profile.name || oldProfile.avatarUrl != profile.avatarUrl)) {
                try { syncUserRoomsWithNewProfile(userId, profile.name, profile.avatarUrl) } catch (_: Exception) {}
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun syncUserRoomsWithNewProfile(userId: String, newName: String, newAvatarUrl: String) {
        val userRoomsSnap = db.child("user_rooms").child(userId).get().await()
        val updates = mutableMapOf<String, Any?>()
        userRoomsSnap.children.forEach { roomSnap ->
            val roomId = roomSnap.key ?: return@forEach
            if (roomId.startsWith("dm_")) {
                val parts = roomId.removePrefix("dm_").split("_")
                if (parts.size >= 2) {
                    val otherId = if (parts[0] == userId) parts[1] else parts[0]
                    if (otherId.isNotEmpty()) {
                        updates["user_rooms/$otherId/$roomId/otherUserName"] = newName
                        updates["user_rooms/$otherId/$roomId/otherUserAvatarUrl"] = newAvatarUrl
                    }
                }
            }
        }
        if (updates.isNotEmpty()) {
            db.updateChildren(updates).await()
        }
    }

    suspend fun getProfile(userId: String): UserProfile? {
        if (profileCache.containsKey(userId)) return profileCache[userId]
        return try {
            val snapshot = db.child("users").child(userId).get().await()
            val profile = snapshot.getValue(UserProfile::class.java)
            if (profile != null) profileCache[userId] = profile
            profile
        } catch (e: Exception) {
            null
        }
    }

    suspend fun prefetchProfiles(userIds: List<String>) {
        val missing = userIds.filter { it.isNotEmpty() && !profileCache.containsKey(it) }.distinct()
        if (missing.isEmpty()) return
        coroutineScope {
            missing.map { uid ->
                async {
                    try {
                        val snapshot = db.child("users").child(uid).get().await()
                        val profile = snapshot.getValue(UserProfile::class.java)
                        if (profile != null) profileCache[uid] = profile
                    } catch (_: Exception) {}
                }
            }.awaitAll()
        }
    }

    suspend fun checkUsernameExists(username: String): Boolean {
        return try {
            val snapshot = db.child("usernames").child(username).get().await()
            snapshot.exists()
        } catch (e: Exception) {
            false
        }
    }
}
