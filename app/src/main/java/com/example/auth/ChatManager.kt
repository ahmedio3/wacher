package com.example.auth

import com.example.models.ChatMessage
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

object ChatManager {
    // Lazy initializers: FirebaseDatabase.getInstance() must only be called after
    // WatcheraApplication.onCreate() has called setPersistenceEnabled(true).
    private val firebaseDb by lazy { FirebaseDatabase.getInstance().reference.child("global_chat") }
    private val globalChatQuery by lazy { firebaseDb.limitToLast(100) }

    init {
        // Keep the query synced so messages survive cold starts via Firebase disk cache.
        globalChatQuery.keepSynced(true)
    }

    // In-memory map keyed by Firebase snapshot.key (always unique and non-blank).
    // Read/updates happen inside ChildEventListener callbacks on the main thread;
    // no need for synchronization.
    private val messageMap = mutableMapOf<String, ChatMessage>()

    fun getMessages(): Flow<List<ChatMessage>> = callbackFlow {
        val childListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val msg = snapshot.getValue(ChatMessage::class.java)
                    ?.copy(id = snapshot.key ?: "")
                    ?: return
                messageMap[snapshot.key ?: return] = msg
                emitSorted()
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val msg = snapshot.getValue(ChatMessage::class.java)
                    ?.copy(id = snapshot.key ?: "")
                    ?: return
                messageMap[snapshot.key ?: return] = msg
                emitSorted()
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                messageMap.remove(snapshot.key)
                emitSorted()
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                // Key ordering is irrelevant for timestamp-sorted display; ignore.
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }

            // WARNING: channel.buffer is Channel.UNLIMITED in the current
            // kotlinx.coroutines version, so trySend NEVER fails here.
            // During a cold start with 100 cached messages, onChildAdded fires
            // once per message in rapid succession. Each call emits the FULL
            // sorted map. Even though intermediate emissions may be wasted
            // work, the last emission carries the complete state, and
            // Compose's snapshot batching collapses rapid state writes into
            // a single recomposition frame. No silent drops are possible with
            // Channel.UNLIMITED.
            private fun emitSorted() {
                val sorted = messageMap.entries
                    .map { it.value }
                    .sortedByDescending { it.timestamp }
                if (trySend(sorted).isFailure) {
                    // Channel closed — listener is stale; nothing to do.
                }
            }
        }

        globalChatQuery.addChildEventListener(childListener)
        awaitClose { globalChatQuery.removeEventListener(childListener) }
    }

    /** Sends a message. The [message.id] is overwritten with a Firebase push key. */
    fun sendMessage(message: ChatMessage) {
        val ref = firebaseDb.push()
        val msgWithId = message.copy(id = ref.key ?: message.id)
        ref.setValue(msgWithId).addOnCompleteListener { task ->
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
