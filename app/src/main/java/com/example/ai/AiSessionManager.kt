package com.example.ai

import com.example.ai.data.AiConversationEntity
import com.example.ai.data.AiDao
import com.example.ai.data.AiMessageEntity

class AiSessionManager(private val aiDao: AiDao) {

    private var messages = mutableListOf<AiChatMessage>()
    private var currentConversationId: String? = null
    private var currentProviderType: AiProviderType = AiProviderType.GEMINI
    private var currentModelId: String = "gemini-3.1-flash-lite"
    private var reasoningEnabled: Boolean = false

    companion object {
        const val MAX_CONTEXT_MESSAGES = 15
    }

    fun startNewSession(providerType: AiProviderType, modelId: String, reasoning: Boolean): String {
        currentProviderType = providerType
        currentModelId = modelId
        reasoningEnabled = reasoning
        val id = java.util.UUID.randomUUID().toString()
        currentConversationId = id
        messages.clear()
        return id
    }

    fun loadSession(conversationId: String, entities: List<AiMessageEntity>) {
        currentConversationId = conversationId
        messages.clear()
        for (entity in entities) {
            messages.add(entityToMessage(entity))
        }
    }

    fun getCurrentConversationId(): String? = currentConversationId

    fun getMessages(): List<AiChatMessage> = messages.toList()

    fun getContextMessages(): List<AiChatMessage> {
        val systemMsg = messages.find { it.role == AiMessageRole.SYSTEM }
        val nonSystem = messages.filter { it.role != AiMessageRole.SYSTEM }
        val recent = nonSystem.takeLast(MAX_CONTEXT_MESSAGES * 2)
        return if (systemMsg != null) listOf(systemMsg) + recent else recent
    }

    fun addMessage(message: AiChatMessage) {
        messages.add(message)
    }

    fun addToolMessages(results: List<AiToolResult>) {
        for (result in results) {
            messages.add(
                AiChatMessage(
                    role = AiMessageRole.TOOL,
                    content = result.content,
                    toolCallId = result.toolCallId
                )
            )
        }
    }

    fun isReasoningEnabled(): Boolean = reasoningEnabled

    fun getCurrentModelId(): String = currentModelId

    fun getCurrentProviderType(): AiProviderType = currentProviderType

    suspend fun saveMessage(message: AiChatMessage, conversationId: String) {
        val entity = messageToEntity(message, conversationId)
        aiDao.insertMessage(entity)
        val count = aiDao.getMessageCount(conversationId)
        aiDao.updateMessageCount(conversationId, count)
    }

    suspend fun saveConversation(title: String, conversationId: String) {
        val existing = aiDao.getConversation(conversationId)
        if (existing == null) {
            aiDao.upsertConversation(
                AiConversationEntity(
                    id = conversationId,
                    title = title,
                    providerType = currentProviderType.name,
                    modelId = currentModelId,
                    reasoningEnabled = reasoningEnabled
                )
            )
        }
    }

    suspend fun updateConversationTitle(conversationId: String, title: String) {
        aiDao.updateConversationTitle(conversationId, title)
    }

    suspend fun deleteConversation(conversationId: String) {
        aiDao.deleteMessages(conversationId)
        aiDao.deleteConversation(conversationId)
    }

    suspend fun loadMessagesFromDb(conversationId: String): List<AiMessageEntity> {
        return aiDao.getMessages(conversationId)
    }

    private fun messageToEntity(msg: AiChatMessage, conversationId: String): AiMessageEntity {
        return AiMessageEntity(
            id = java.util.UUID.randomUUID().toString(),
            conversationId = conversationId,
            role = msg.role.value,
            content = msg.content,
            reasoningContent = msg.reasoningContent,
            toolCallsJson = toolCallsToJson(msg.toolCalls),
            imageUrlsJson = msg.imageUrls?.let { org.json.JSONArray(it).toString() },
            createdAt = System.currentTimeMillis()
        )
    }

    private fun entityToMessage(entity: AiMessageEntity): AiChatMessage {
        return AiChatMessage(
            role = AiMessageRole.fromValue(entity.role),
            content = entity.content,
            reasoningContent = entity.reasoningContent,
            toolCalls = jsonToToolCalls(entity.toolCallsJson),
            imageUrls = entity.imageUrlsJson?.let {
                try {
                    val arr = org.json.JSONArray(it)
                    (0 until arr.length()).map { i -> arr.getString(i) }
                } catch (e: Exception) { null }
            }
        )
    }
}
