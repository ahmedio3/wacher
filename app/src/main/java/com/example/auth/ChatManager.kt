package com.example.auth

import com.example.models.ChatMessage
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

object ChatManager {
    private val firebaseDb = FirebaseDatabase.getInstance().reference.child("global_chat")

    fun getMessages(): Flow<List<ChatMessage>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val messages = snapshot.children.mapNotNull { it.getValue(ChatMessage::class.java) }
                    .sortedBy { it.timestamp }
                trySend(messages)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        firebaseDb.limitToLast(100).addValueEventListener(listener)
        awaitClose { firebaseDb.limitToLast(100).removeEventListener(listener) }
    }

    fun sendMessage(message: ChatMessage) {
        firebaseDb.child(message.id).setValue(message).addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                android.util.Log.e("ChatManager", "Send message failed: ${task.exception?.message}")
            }
        }
    }

    fun updateMessage(messageId: String, newText: String) {
        firebaseDb.child(messageId).child("text").setValue(newText).addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                android.util.Log.e("ChatManager", "Update message failed: ${task.exception?.message}")
            }
        }
    }

    fun deleteMessage(messageId: String) {
        firebaseDb.child(messageId).removeValue().addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                android.util.Log.e("ChatManager", "Delete message failed: ${task.exception?.message}")
            }
        }
    }

    fun getTypingUsers(): Flow<List<String>> = callbackFlow {
        val typingRef = FirebaseDatabase.getInstance().getReference("typing")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val typing = mutableListOf<String>()
                for (child in snapshot.children) {
                    val name = child.getValue(String::class.java)
                    if (name != null) typing.add(name)
                }
                trySend(typing)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        typingRef.addValueEventListener(listener)
        awaitClose { typingRef.removeEventListener(listener) }
    }
}
