package com.example.models

data class ChatMessage(
    val id: String = "",
    val userId: String = "",
    val username: String = "",
    val text: String = "",
    val timestamp: Long = 0,
    val repliedToId: String = "",
    val repliedToName: String = "",
    val repliedToText: String = ""
)
