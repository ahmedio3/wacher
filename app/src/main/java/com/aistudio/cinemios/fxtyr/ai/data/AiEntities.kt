package com.aistudio.cinemios.fxtyr.ai.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_conversations")
data class AiConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val providerType: String,
    val modelId: String,
    val thinkingLevel: String = "high", // stores ThinkingLevel.key: none/low/medium/high
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messageCount: Int = 0
)

@Entity(
    tableName = "ai_messages",
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = AiConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ],
    indices = [androidx.room.Index("conversationId")]
)
data class AiMessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val reasoningContent: String? = null,
    val toolCallsJson: String? = null,
    val toolResultsJson: String? = null,
    val imageUrlsJson: String? = null,
    val toolExecutionsJson: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
