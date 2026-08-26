package com.aistudio.cinemios.fxtyr.ai.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AiDao {
    @Query("SELECT * FROM ai_conversations ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<AiConversationEntity>>

    @Query("SELECT * FROM ai_conversations WHERE id = :id LIMIT 1")
    suspend fun getConversation(id: String): AiConversationEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertConversation(conversation: AiConversationEntity): Long

    @Query("UPDATE ai_conversations SET updatedAt = :now WHERE id = :id")
    suspend fun updateConversationTimestamp(id: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM ai_conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Query("UPDATE ai_conversations SET title = :title, updatedAt = :now WHERE id = :id")
    suspend fun updateConversationTitle(id: String, title: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM ai_messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getMessages(conversationId: String): List<AiMessageEntity>

    @Query("SELECT * FROM ai_messages WHERE conversationId = :conversationId ORDER BY createdAt ASC LIMIT :limit OFFSET :offset")
    suspend fun getMessagesPaged(conversationId: String, limit: Int, offset: Int): List<AiMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: AiMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<AiMessageEntity>)

    @Query("DELETE FROM ai_messages WHERE conversationId = :conversationId")
    suspend fun deleteMessages(conversationId: String)

    @Query("SELECT COUNT(*) FROM ai_messages WHERE conversationId = :conversationId")
    suspend fun getMessageCount(conversationId: String): Int

    @Query("UPDATE ai_conversations SET messageCount = :count WHERE id = :id")
    suspend fun updateMessageCount(id: String, count: Int)
}
