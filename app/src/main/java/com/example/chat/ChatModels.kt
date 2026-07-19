package com.example.chat

data class ChatRoom(
    val id: String = "",
    val type: String = "public",
    val name: String = "",
    val imageUrl: String = "",
    val participants: Map<String, Boolean> = emptyMap(),
    val lastMessage: LastMessage? = null,
    val createdAt: Long = 0L
)

data class LastMessage(
    val text: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val timestamp: Long = 0L,
    val type: String = "text"
)

data class Message(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderAvatarUrl: String = "",
    val text: String = "",
    val imageUrl: String = "",
    val type: String = "text",
    val replyTo: ReplyTo? = null,
    val timestamp: Long = 0L
)

data class ReplyTo(
    val messageId: String = "",
    val text: String = "",
    val senderName: String = ""
)

data class UserChat(
    val roomId: String = "",
    val roomType: String = "",
    val otherUserId: String = "",
    val otherUserName: String = "",
    val otherUserAvatarUrl: String = "",
    val lastMessage: LastMessage? = null,
    val lastReadTimestamp: Long = 0L
)
