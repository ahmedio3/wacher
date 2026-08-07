package com.example.ai

import com.example.ai.data.AiConversationEntity
import com.example.ai.data.AiDao
import com.example.ai.data.AiMessageEntity

class AiSessionManager(private val aiDao: AiDao) {

    private var messages = mutableListOf<AiChatMessage>()
    private var currentConversationId: String? = null
    private var currentProviderType: AiProviderType = AiProviderType.AGNES_AI
    private var currentModelId: String = "agnes-2.0-flash"
    private var thinkingLevel: ThinkingLevel = ThinkingLevel.HIGH

    companion object {
        const val MAX_CONTEXT_MESSAGES = 15
    }

    fun startNewSession(providerType: AiProviderType, modelId: String, level: ThinkingLevel): String {
        currentProviderType = providerType
        currentModelId = modelId
        thinkingLevel = level
        val id = java.util.UUID.randomUUID().toString()
        currentConversationId = id
        messages.clear()
        return id
    }

    fun setProviderAndModel(providerType: AiProviderType, modelId: String, level: ThinkingLevel) {
        currentProviderType = providerType
        currentModelId = modelId
        thinkingLevel = level
    }

    fun setThinkingLevel(level: ThinkingLevel) {
        thinkingLevel = level
    }

    fun getThinkingLevel(): ThinkingLevel = thinkingLevel

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
        val nonSystem = messages.filter { it.role != AiMessageRole.SYSTEM }
        return nonSystem.takeLast(MAX_CONTEXT_MESSAGES * 2)
    }

    fun addMessage(message: AiChatMessage) {
        messages.add(message)
    }

    fun updateLastMessage(message: AiChatMessage) {
        val lastIdx = messages.lastIndex
        if (lastIdx >= 0) {
            messages[lastIdx] = message
        }
    }

    fun upsertByRole(role: AiMessageRole, message: AiChatMessage) {
        val idx = messages.indexOfLast { it.role == role }
        if (idx >= 0) messages[idx] = message
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

    fun getCurrentModelId(): String = currentModelId
    fun getCurrentProviderType(): AiProviderType = currentProviderType

    suspend fun ensureConversationInDb(title: String = "محادثة جديدة"): String {
        var conversationId = currentConversationId
        var isNew = false
        if (conversationId == null) {
            conversationId = startNewSession(currentProviderType, currentModelId, thinkingLevel)
            isNew = true
        }
        val id = aiDao.insertConversation(
            AiConversationEntity(
                id = conversationId,
                title = title,
                providerType = currentProviderType.name,
                modelId = currentModelId,
                thinkingLevel = thinkingLevel.key,
                updatedAt = System.currentTimeMillis()
            )
        )
        if (id == -1L && !isNew) {
            aiDao.updateConversationTimestamp(conversationId)
        }
        return conversationId
    }

    suspend fun saveMessage(message: AiChatMessage, conversationId: String) {
        ensureConversationInDb()
        val entity = messageToEntity(message, conversationId)
        aiDao.insertMessage(entity)
        val count = aiDao.getMessageCount(conversationId)
        aiDao.updateMessageCount(conversationId, count)
    }

    suspend fun saveConversation(title: String, conversationId: String) {
        val id = aiDao.insertConversation(
            AiConversationEntity(
                id = conversationId,
                title = title,
                providerType = currentProviderType.name,
                modelId = currentModelId,
                thinkingLevel = thinkingLevel.key,
                updatedAt = System.currentTimeMillis()
            )
        )
        if (id == -1L) {
            aiDao.updateConversationTitle(conversationId, title)
        }
    }

    suspend fun updateConversationTitle(conversationId: String, title: String) {
        aiDao.updateConversationTitle(conversationId, title)
    }

    suspend fun deleteConversation(conversationId: String) {
        aiDao.deleteMessages(conversationId)
        aiDao.deleteConversation(conversationId)
        if (currentConversationId == conversationId) {
            currentConversationId = null
            messages.clear()
        }
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
            toolExecutionsJson = if (msg.toolExecutions.isNotEmpty())
                toolExecutionsToJson(msg.toolExecutions) else null,
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
            },
            toolExecutions = jsonToToolExecutions(entity.toolExecutionsJson)
        )
    }

    private fun toolExecutionsToJson(executions: List<ToolExecutionDisplay>): String {
        val arr = org.json.JSONArray()
        executions.forEach { exec ->
            val obj = org.json.JSONObject()
            obj.put("toolName", exec.toolName)
            obj.put("status", exec.status.name)
            obj.put("summary", exec.summary)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun jsonToToolExecutions(json: String?): List<ToolExecutionDisplay> {
        if (json == null) return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                ToolExecutionDisplay(
                    toolName = obj.getString("toolName"),
                    status = try { ToolExecutionStatus.valueOf(obj.getString("status")) } catch (_: Exception) { ToolExecutionStatus.SUCCESS },
                    summary = obj.optString("summary", "")
                )
            }
        } catch (_: Exception) { emptyList() }
    }
}
