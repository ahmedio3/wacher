package com.example.auth

import com.example.data.local.ActivityLogEntity
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
                val list = snapshot.children.mapNotNull { child ->
                    // Populate id from the Firebase push key so each log has a unique,
                    // stable identifier (the stored 'id' field is always empty). This
                    // prevents duplicate LazyColumn keys crashing the Activity Log screen.
                    child.getValue(ActivityLogEntity::class.java)?.copy(id = child.key ?: "")
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
