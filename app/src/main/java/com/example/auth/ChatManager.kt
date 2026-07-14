package com.example.auth

import android.content.Context
import com.example.data.local.ChatEntity
import com.example.data.local.MovieDatabase
import com.example.models.ChatMessage
import com.google.firebase.database.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map

object ChatManager {
    private val firebaseDb = FirebaseDatabase.getInstance().reference.child("global_chat")

    private var isSyncRegistered = false

    fun getMessages(context: Context): Flow<List<ChatMessage>> {
        val db = MovieDatabase.getDatabase(context).movieDao
        
        if (!isSyncRegistered) {
            isSyncRegistered = true
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val messages = mutableListOf<ChatMessage>()
                    for (child in snapshot.children) {
                        child.getValue(ChatMessage::class.java)?.let { messages.add(it) }
                    }
                    messages.sortBy { it.timestamp }
                    val entities = messages.map { 
                        ChatEntity(
                            id = it.id,
                            userId = it.userId,
                            username = it.username,
                            text = it.text,
                            timestamp = it.timestamp,
                            avatarBase64 = it.avatarBase64,
                            repliedToId = it.repliedToId,
                            repliedToName = it.repliedToName,
                            repliedToText = it.repliedToText
                        ) 
                    }
                    
                    CoroutineScope(Dispatchers.IO).launch {
                        db.clearChatMessages()
                        db.insertChatMessages(entities)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    android.util.Log.e("ChatManager", "Database error: ${error.message}")
                }
            }
            firebaseDb.limitToLast(100).addValueEventListener(listener)
        }
        
        return db.getLocalChatMessages().map { entities -> 
            entities.map { 
                ChatMessage(
                    id = it.id,
                    userId = it.userId,
                    username = it.username,
                    text = it.text,
                    timestamp = it.timestamp,
                    avatarBase64 = it.avatarBase64,
                    repliedToId = it.repliedToId,
                    repliedToName = it.repliedToName,
                    repliedToText = it.repliedToText
                ) 
            } 
        }
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
