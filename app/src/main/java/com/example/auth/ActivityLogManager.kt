package com.example.auth

import com.example.data.local.MovieEntities.ActivityLogEntity
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

object ActivityLogManager {
    private val db = FirebaseDatabase.getInstance().reference

    suspend fun addLog(uid: String, type: String, title: String) {
        val key = db.child("activity_logs").child(uid).push().key ?: return
        db.child("activity_logs").child(uid).child(key)
            .setValue(ActivityLogEntity(type = type, title = title))
    }

    fun getLogs(uid: String): Flow<List<ActivityLogEntity>> = callbackFlow {
        val ref = db.child("activity_logs").child(uid)
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull {
                    it.getValue(ActivityLogEntity::class.java)
                }.sortedByDescending { it.timestamp }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        })
        awaitClose { ref.removeEventListener(listener) }
    }
}
