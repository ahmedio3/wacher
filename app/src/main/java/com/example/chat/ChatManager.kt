package com.example.chat

import android.graphics.Bitmap
import com.example.data.remote.ImgBBUploader
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object ChatManager {

    private val db = FirebaseDatabase.getInstance().reference

    private val messagesCache = mutableMapOf<String, MutableList<Message>>()
    private val listeners = mutableMapOf<String, ChildEventListener>()
    private val messageListeners = mutableMapOf<String, MutableList<(List<Message>) -> Unit>>()

    fun generateDMRoomId(uid1: String, uid2: String): String {
        val sorted = listOf(uid1, uid2).sorted()
        return "dm_${sorted[0]}_${sorted[1]}"
    }

    suspend fun getOrCreateDMRoom(currentUserId: String, otherUserId: String, otherUserName: String, otherUserAvatarUrl: String): String {
        val roomId = generateDMRoomId(currentUserId, otherUserId)

        val snapshot = db.child("chat_rooms").child(roomId).get().await()
        if (!snapshot.exists()) {
            val room = ChatRoom(
                id = roomId,
                type = "private",
                participants = mapOf(currentUserId to true, otherUserId to true),
                createdAt = System.currentTimeMillis()
            )
            db.child("chat_rooms").child(roomId).setValue(room).await()

            addUserRoom(currentUserId, roomId, "private", otherUserId, otherUserName, otherUserAvatarUrl)
            addUserRoom(otherUserId, roomId, "private", currentUserId, "", "")
        }
        return roomId
    }

    private suspend fun addUserRoom(userId: String, roomId: String, roomType: String, otherUserId: String, otherUserName: String, otherUserAvatarUrl: String) {
        val ref = db.child("user_rooms").child(userId).child(roomId)
        val existing = ref.child("otherUserId").get().await()
        if (!existing.exists() || existing.getValue(String::class.java) != otherUserId) {
            val userChat = UserChat(
                roomId = roomId,
                roomType = roomType,
                otherUserId = otherUserId,
                otherUserName = otherUserName,
                otherUserAvatarUrl = otherUserAvatarUrl
            )
            ref.setValue(userChat).await()
        }
    }

    suspend fun sendMessage(roomId: String, message: Message) {
        val ref = db.child("messages").child(roomId).push()
        val key = ref.key ?: return
        ref.setValue(message.copy(id = key)).await()

        val lastMsg = LastMessage(
            text = if (message.type == "image") "🖼️ صورة" else message.text,
            senderId = message.senderId,
            senderName = message.senderName,
            timestamp = message.timestamp,
            type = message.type
        )
        db.child("chat_rooms").child(roomId).child("lastMessage").setValue(lastMsg).await()

        db.child("user_rooms").child(message.senderId).child(roomId).child("lastMessage").setValue(lastMsg).await()
    }

    suspend fun sendImageMessage(roomId: String, bitmap: Bitmap, senderId: String, senderName: String, senderAvatarUrl: String, replyTo: ReplyTo? = null): Boolean {
        val imageUrl = withContext(Dispatchers.IO) {
            ImgBBUploader.uploadImage(bitmap)
        }
        if (imageUrl == null) return false

        val message = Message(
            senderId = senderId,
            senderName = senderName,
            senderAvatarUrl = senderAvatarUrl,
            text = "",
            imageUrl = imageUrl,
            type = "image",
            replyTo = replyTo,
            timestamp = System.currentTimeMillis()
        )
        sendMessage(roomId, message)
        return true
    }

    fun listenForMessages(roomId: String, onMessages: (List<Message>) -> Unit) {
        messageListeners.getOrPut(roomId) { mutableListOf() }.add(onMessages)

        if (listeners.containsKey(roomId)) return

        val childListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val msg = snapshot.getValue(Message::class.java)?.copy(id = snapshot.key ?: "")
                if (msg != null) {
                    val list = messagesCache.getOrPut(roomId) { mutableListOf() }
                    list.add(msg)
                    list.sortBy { it.timestamp }
                    notifyListeners(roomId)
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }

        db.child("messages").child(roomId)
            .orderByChild("timestamp")
            .limitToLast(50)
            .addChildEventListener(childListener)

        listeners[roomId] = childListener
    }

    suspend fun loadMoreMessages(roomId: String, oldestTimestamp: Long): List<Message> {
        val snapshot = db.child("messages").child(roomId)
            .orderByChild("timestamp")
            .endBefore(oldestTimestamp.toDouble())
            .limitToLast(50)
            .get().await()

        val olderMessages = snapshot.children.mapNotNull { it.getValue(Message::class.java)?.copy(id = it.key ?: "") }
        val list = messagesCache.getOrPut(roomId) { mutableListOf() }
        val existingIds = list.map { it.id }.toSet()
        for (msg in olderMessages) {
            if (msg.id !in existingIds) {
                list.add(msg)
            }
        }
        list.sortBy { it.timestamp }
        notifyListeners(roomId)
        return olderMessages
    }

    fun removeListener(roomId: String) {
        listeners.remove(roomId)?.let {
            db.child("messages").child(roomId).removeEventListener(it)
        }
        messagesCache.remove(roomId)
        messageListeners.remove(roomId)
    }

    fun getCachedMessages(roomId: String): List<Message> {
        return messagesCache[roomId]?.toList() ?: emptyList()
    }

    private fun notifyListeners(roomId: String) {
        val messages = getCachedMessages(roomId)
        messageListeners[roomId]?.forEach { it(messages) }
    }

    suspend fun getUserChatList(userId: String): List<UserChat> {
        val snapshot = db.child("user_rooms").child(userId).get().await()
        return snapshot.children.mapNotNull { it.getValue(UserChat::class.java)?.copy(roomId = it.key ?: "") }
    }

    fun listenForUserChats(userId: String, onChats: (List<UserChat>) -> Unit) {
        val ref = db.child("user_rooms").child(userId)
        ref.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val chats = snapshot.children.mapNotNull { it.getValue(UserChat::class.java)?.copy(roomId = it.key ?: "") }
                onChats(chats)
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    suspend fun updateChatRoomImage(roomId: String, imageUrl: String) {
        db.child("chat_rooms").child(roomId).child("imageUrl").setValue(imageUrl).await()
    }

    suspend fun getChatRoomName(roomId: String): String? {
        val snapshot = db.child("chat_rooms").child(roomId).child("name").get().await()
        return snapshot.getValue(String::class.java)
    }

    suspend fun getChatRoomImage(roomId: String): String? {
        val snapshot = db.child("chat_rooms").child(roomId).child("imageUrl").get().await()
        return snapshot.getValue(String::class.java)
    }

    suspend fun updateOtherUserInfoInDmRoom(roomId: String, currentUserId: String, otherUserName: String, otherUserAvatarUrl: String) {
        val updates = mapOf(
            "user_rooms/$currentUserId/$roomId/otherUserName" to otherUserName,
            "user_rooms/$currentUserId/$roomId/otherUserAvatarUrl" to otherUserAvatarUrl
        )
        db.updateChildren(updates).await()
    }
}
