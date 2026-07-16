package com.example.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
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
            
            // Check username uniqueness
            if (oldProfile?.username != profile.username) {
                val exists = checkUsernameExists(profile.username)
                if (exists) return false
                
                // Clear old username
                if (oldProfile?.username?.isNotEmpty() == true) {
                    db.child("usernames").child(oldProfile.username).removeValue().await()
                }
            }
            
            // Save new profile
            db.child("users").child(userId).setValue(profile).await()
            // Save username mapping
            if (profile.username.isNotEmpty()) {
                db.child("usernames").child(profile.username).setValue(userId).await()
            }
            profileCache[userId] = profile // update cache
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
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

    suspend fun checkUsernameExists(username: String): Boolean {
        return try {
            val snapshot = db.child("usernames").child(username).get().await()
            snapshot.exists()
        } catch (e: Exception) {
            false
        }
    }
}
