package com.example.ai

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.ai.data.AiConversationEntity
import com.example.ai.data.AiDao
import com.example.data.local.MovieDatabase
import com.example.ai.provider.AiProviderService
import com.example.ai.provider.GeminiProvider
import com.example.ai.provider.OpenAiCompatibleProvider
import com.example.ai.provider.buildToolDeclarationsJson
import com.example.ai.tools.AiToolExecutor
import com.example.ai.tools.ToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AiChatUiState(
    val messages: List<AiChatMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentProviderType: AiProviderType = AiProviderType.AGNES_AI,
    val currentModelId: String = getDefaultModel().id,
    val thinkingLevel: ThinkingLevel = ThinkingLevel.HIGH,
    val conversations: List<AiConversationEntity> = emptyList(),
    val currentConversationId: String? = null,
    val currentConversationTitle: String = "محادثة جديدة",
    val pendingApproval: ApprovalRequest? = null
)

data class ApprovalRequest(
    val description: String,
    val toolName: String,
    val args: Map<String, Any>
)

class AiViewModel(application: Application) : AndroidViewModel(application) {

    private val database = MovieDatabase.getDatabase(application)
    private val aiDao: AiDao = database.aiDao
    private val sessionManager = AiSessionManager(aiDao)
    private val toolExecutor = AiToolExecutor(application)
    private val prefs = application.getSharedPreferences("ai_prefs", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(AiChatUiState())
    val state: StateFlow<AiChatUiState> = _state.asStateFlow()

    private var currentProvider: AiProviderService? = null
    private var streamingJob: Job? = null

    init {
        loadConversations()
        restoreLastSettings()
    }

    private fun restoreLastSettings() {
        val providerName = prefs.getString("last_provider", AiProviderType.AGNES_AI.name)
            ?: AiProviderType.AGNES_AI.name
        val modelId = prefs.getString("last_model", getDefaultModel().id) ?: getDefaultModel().id
        val levelKey = prefs.getString("last_thinking", ThinkingLevel.HIGH.key) ?: ThinkingLevel.HIGH.key

        val providerType = try {
            AiProviderType.valueOf(providerName)
        } catch (_: Exception) {
            AiProviderType.AGNES_AI
        }
        val model = PROVIDER_CONFIGS[providerType]?.models?.find { it.id == modelId }
            ?: getDefaultModel()
        val level = ThinkingLevel.fromKey(levelKey).let { lvl ->
            if (model.supportsReasoning && model.reasoningLevels.contains(lvl)) lvl
            else if (model.supportsReasoning) ThinkingLevel.HIGH
            else ThinkingLevel.NONE
        }

        updateProvider(model.providerType, model.id, level)
        _state.update {
            it.copy(
                currentProviderType = model.providerType,
                currentModelId = model.id,
                thinkingLevel = level
            )
        }
    }

    private fun persistSettings(providerType: AiProviderType, modelId: String, level: ThinkingLevel) {
        prefs.edit()
            .putString("last_provider", providerType.name)
            .putString("last_model", modelId)
            .putString("last_thinking", level.key)
            .apply()
    }

    private fun updateProvider(providerType: AiProviderType, modelId: String, level: ThinkingLevel) {
        currentProvider = when (providerType) {
            AiProviderType.GEMINI -> GeminiProvider(BuildConfig.GEMINI_API_KEY)
            AiProviderType.OPENCODE_ZEN -> OpenAiCompatibleProvider(
                baseUrl = PROVIDER_CONFIGS[AiProviderType.OPENCODE_ZEN]!!.baseUrl!!,
                apiKey = BuildConfig.OPENCODE_ZEN_API_KEY,
                providerName = "OpenCode Zen"
            )
            AiProviderType.BYNARA -> OpenAiCompatibleProvider(
                baseUrl = PROVIDER_CONFIGS[AiProviderType.BYNARA]!!.baseUrl!!,
                apiKey = BuildConfig.BYNARA_API_KEY,
                providerName = "Bynara"
            )
            AiProviderType.AGNES_AI -> OpenAiCompatibleProvider(
                baseUrl = PROVIDER_CONFIGS[AiProviderType.AGNES_AI]!!.baseUrl!!,
                apiKey = BuildConfig.AGNES_API_KEY,
                providerName = "Agnes AI"
            )
        }
        sessionManager.setProviderAndModel(providerType, modelId, level)
    }

    fun selectModel(providerType: AiProviderType, modelId: String) {
        val config = PROVIDER_CONFIGS[providerType] ?: return
        val model = config.models.find { it.id == modelId } ?: return

        val level = if (model.supportsReasoning) {
            val current = _state.value.thinkingLevel
            if (model.reasoningLevels.contains(current) && current != ThinkingLevel.NONE) current
            else ThinkingLevel.HIGH
        } else {
            ThinkingLevel.NONE
        }

        updateProvider(providerType, modelId, level)
        persistSettings(providerType, modelId, level)
        _state.update {
            it.copy(
                currentProviderType = providerType,
                currentModelId = modelId,
                thinkingLevel = level,
                error = null
            )
        }
    }

    fun setThinkingLevel(level: ThinkingLevel) {
        val model = PROVIDER_CONFIGS[_state.value.currentProviderType]
            ?.models?.find { it.id == _state.value.currentModelId }
        if (model != null && !model.reasoningLevels.contains(level)) return

        sessionManager.setThinkingLevel(level)
        persistSettings(_state.value.currentProviderType, _state.value.currentModelId, level)
        _state.update { it.copy(thinkingLevel = level) }
    }

    fun sendMessage(text: String, imageBase64: String? = null) {
        if (_state.value.isStreaming) return
        if (text.isBlank() && imageBase64 == null) return

        viewModelScope.launch {
            try {
                val conversationId = sessionManager.ensureConversationInDb(
                    _state.value.currentConversationTitle.ifBlank { "محادثة جديدة" }
                )
                _state.update { it.copy(currentConversationId = conversationId) }

                val userMsg = AiChatMessage(
                    role = AiMessageRole.USER,
                    content = text,
                    imageUrls = if (imageBase64 != null) listOf(imageBase64) else null
                )

                sessionManager.addMessage(userMsg)
                try {
                    sessionManager.saveMessage(userMsg, conversationId)
                } catch (e: Exception) {
                    android.util.Log.e("AiViewModel", "saveMessage failed", e)
                }

                _state.update {
                    it.copy(
                        messages = sessionManager.getMessages(),
                        isStreaming = true,
                        error = null
                    )
                }

                processStreaming(conversationId)
            } catch (e: Exception) {
                _state.update {
                    it.copy(isStreaming = false, error = "فشل الإرسال: ${e.message}")
                }
            }
        }
    }

    private fun processStreaming(conversationId: String) {
        streamingJob = viewModelScope.launch {
            try {
                val provider = currentProvider ?: run {
                    _state.update { it.copy(isStreaming = false, error = "No provider selected") }
                    return@launch
                }

                val systemPrompt = buildSystemPrompt()
                val allMessages = mutableListOf(
                    AiChatMessage(role = AiMessageRole.SYSTEM, content = systemPrompt)
                )
                allMessages.addAll(sessionManager.getContextMessages())

                try {
                    provider.streamChat(
                        messages = allMessages,
                        model = _state.value.currentModelId,
                        toolsJson = buildToolDeclarationsJson(),
                        thinkingLevel = _state.value.thinkingLevel,
                        onEvent = { event ->
                            viewModelScope.launch {
                                handleStreamEvent(event, conversationId)
                            }
                        }
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.update {
                        it.copy(isStreaming = false, error = "فشل الاتصال: ${e.message}")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                streamingJob = null
            }
        }
    }

    private suspend fun handleStreamEvent(event: AiStreamEvent, conversationId: String) {
        when (event.type) {
            AiStreamEventType.TEXT_CHUNK -> appendToLastAssistantMessage(event.content)
            AiStreamEventType.REASONING_CHUNK -> appendToLastAssistantReasoning(event.reasoningContent ?: "")
            AiStreamEventType.TOOL_CALLS -> event.toolCalls?.let { executeToolCalls(it, conversationId) }
            AiStreamEventType.DONE -> {
                if (event.finishReason != "TOOL_CALLS" && event.finishReason != "tool_calls") {
                    finalizeMessage(conversationId)
                }
            }
            AiStreamEventType.TOOL_RESULTS -> {}
            AiStreamEventType.ERROR -> {
                val clean = event.content
                    .replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(280)
                _state.update { it.copy(isStreaming = false, error = clean.ifBlank { "حدث خطأ" }) }
            }
        }
    }

    private suspend fun executeToolCalls(toolCalls: List<AiToolCall>, conversationId: String) {
        for (call in toolCalls) {
            addToolToLastMessage(
                ToolExecutionDisplay(
                    toolName = call.name,
                    status = ToolExecutionStatus.RUNNING,
                    summary = toolLabel(call.name)
                )
            )

            val result = withContext(Dispatchers.IO) {
                toolExecutor.executeTool(call.name, call.arguments)
            }

            when (result) {
                is ToolResult.Success -> {
                    updateToolInLastMessage(call.name, ToolExecutionStatus.SUCCESS, toolSummary(call.name, result.data))
                    sessionManager.addToolMessages(
                        listOf(AiToolResult(call.id, call.name, result.data))
                    )
                    reInvokeProvider(conversationId)
                }
                is ToolResult.Error -> {
                    updateToolInLastMessage(call.name, ToolExecutionStatus.ERROR, result.message)
                    sessionManager.addToolMessages(
                        listOf(AiToolResult(call.id, call.name, "خطأ: ${result.message}"))
                    )
                    reInvokeProvider(conversationId)
                }
                is ToolResult.NeedsApproval -> {
                    updateToolInLastMessage(call.name, ToolExecutionStatus.SUCCESS, "في انتظار الموافقة...")
                    _state.update {
                        it.copy(
                            pendingApproval = ApprovalRequest(
                                description = result.description,
                                toolName = call.name,
                                args = result.executeData
                            )
                        )
                    }
                }
            }
        }
    }

    private fun addToolToLastMessage(tool: ToolExecutionDisplay) {
        _state.update { currentState ->
            val msgs = currentState.messages
            val lastIdx = msgs.lastIndex
            if (lastIdx >= 0 && msgs[lastIdx].role == AiMessageRole.ASSISTANT) {
                val updatedMessages = msgs.toMutableList()
                val lastMessage = updatedMessages[lastIdx]
                val updatedMessage = lastMessage.copy(
                    toolExecutions = lastMessage.toolExecutions + tool
                )
                updatedMessages[lastIdx] = updatedMessage
                sessionManager.updateLastMessage(updatedMessage)
                currentState.copy(messages = updatedMessages)
            } else {
                currentState
            }
        }
    }

    private fun updateToolInLastMessage(toolName: String, status: ToolExecutionStatus, summary: String) {
        _state.update { currentState ->
            val msgs = currentState.messages
            val lastIdx = msgs.lastIndex
            if (lastIdx >= 0 && msgs[lastIdx].role == AiMessageRole.ASSISTANT) {
                val updatedMessages = msgs.toMutableList()
                val lastMessage = updatedMessages[lastIdx]
                val updatedMessage = lastMessage.copy(
                    toolExecutions = lastMessage.toolExecutions.map {
                        if (it.toolName == toolName) it.copy(status = status, summary = summary) else it
                    }
                )
                updatedMessages[lastIdx] = updatedMessage
                sessionManager.updateLastMessage(updatedMessage)
                currentState.copy(messages = updatedMessages)
            } else {
                currentState
            }
        }
    }

    private fun toolLabel(toolName: String): String = when (toolName) {
        "search_tmdb" -> "جارٍ البحث..."
        "get_watchlist" -> "عرض القائمة"
        "get_downloads" -> "عرض التحميلات"
        "add_to_watchlist" -> "جارٍ الإضافة..."
        "get_tmdb_details" -> "جلب التفاصيل"
        else -> "جارٍ التنفيذ..."
    }

    private fun toolSummary(toolName: String, data: String): String = when (toolName) {
        "search_tmdb" -> {
            try {
                val json = org.json.JSONArray(data)
                val count = json.length()
                if (count == 0) "لا توجد نتائج"
                else {
                    val first = json.getJSONObject(0)
                    val title = first.optString("title", "?")
                    "وجدت $count نتائج: $title..."
                }
            } catch (_: Exception) { "تم" }
        }
        "add_to_watchlist" -> "تمت الإضافة ✓"
        else -> "تم"
    }

    fun approveAction() {
        val approval = _state.value.pendingApproval ?: return
        _state.update { it.copy(pendingApproval = null) }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                toolExecutor.executeTool(approval.toolName, approval.args)
            }
            val conversationId = sessionManager.getCurrentConversationId() ?: return@launch

            when (result) {
                is ToolResult.Success -> {
                    sessionManager.addToolMessages(
                        listOf(
                            AiToolResult(
                                "approved_${System.currentTimeMillis()}",
                                approval.toolName,
                                "تم التنفيذ بعد موافقة المستخدم: ${result.data}"
                            )
                        )
                    )
                    updateToolInLastMessage(approval.toolName, ToolExecutionStatus.SUCCESS, "تم التنفيذ")
                    reInvokeProvider(conversationId)
                }
                is ToolResult.Error -> {
                    sessionManager.addToolMessages(
                        listOf(
                            AiToolResult(
                                "approved_${System.currentTimeMillis()}",
                                approval.toolName,
                                "خطأ بعد الموافقة: ${result.message}"
                            )
                        )
                    )
                    updateToolInLastMessage(approval.toolName, ToolExecutionStatus.ERROR, result.message)
                    reInvokeProvider(conversationId)
                }
                is ToolResult.NeedsApproval -> {}
            }
        }
    }

    fun rejectAction() {
        val approval = _state.value.pendingApproval ?: return
        _state.update { it.copy(pendingApproval = null) }

        viewModelScope.launch {
            val conversationId = sessionManager.getCurrentConversationId() ?: return@launch
            sessionManager.addToolMessages(
                listOf(
                    AiToolResult(
                        "rejected_${System.currentTimeMillis()}",
                        approval.toolName,
                        "تم رفض الإجراء من قبل المستخدم: ${approval.description}"
                    )
                )
            )
            updateToolInLastMessage(approval.toolName, ToolExecutionStatus.ERROR, "تم الرفض")
            reInvokeProvider(conversationId)
        }
    }

    fun stopStreaming() {
        streamingJob?.cancel()
        streamingJob = null
        viewModelScope.launch {
            val conversationId = sessionManager.getCurrentConversationId() ?: return@launch
            _state.update { it.copy(isStreaming = false) }
            finalizeMessage(conversationId)
        }
    }

    private suspend fun reInvokeProvider(conversationId: String) {
        val provider = currentProvider ?: return
        val systemPrompt = buildSystemPrompt()
        val allMessages = mutableListOf(
            AiChatMessage(role = AiMessageRole.SYSTEM, content = systemPrompt)
        )
        allMessages.addAll(sessionManager.getContextMessages())

        provider.streamChat(
            messages = allMessages,
            model = _state.value.currentModelId,
            toolsJson = buildToolDeclarationsJson(),
            thinkingLevel = _state.value.thinkingLevel,
            onEvent = { event ->
                viewModelScope.launch { handleStreamEvent(event, conversationId) }
            }
        )
    }

    private fun appendToLastAssistantMessage(chunk: String) {
        _state.update { currentState ->
            val msgs = currentState.messages
            val lastIdx = msgs.lastIndex
            if (lastIdx >= 0 && msgs[lastIdx].role == AiMessageRole.ASSISTANT) {
                val updatedMessages = msgs.toMutableList()
                val lastMessage = updatedMessages[lastIdx]
                val updatedMessage = lastMessage.copy(content = lastMessage.content + chunk)
                updatedMessages[lastIdx] = updatedMessage
                sessionManager.updateLastMessage(updatedMessage)
                currentState.copy(messages = updatedMessages)
            } else {
                val newMessage = AiChatMessage(role = AiMessageRole.ASSISTANT, content = chunk)
                sessionManager.addMessage(newMessage)
                currentState.copy(messages = msgs + newMessage)
            }
        }
    }

    private fun appendToLastAssistantReasoning(chunk: String) {
        val msgs = _state.value.messages.toMutableList()
        val lastIdx = msgs.lastIndex
        if (lastIdx >= 0 && msgs[lastIdx].role == AiMessageRole.ASSISTANT) {
            val last = msgs[lastIdx]
            msgs[lastIdx] = last.copy(reasoningContent = (last.reasoningContent ?: "") + chunk)
        } else {
            msgs.add(AiChatMessage(role = AiMessageRole.ASSISTANT, content = "", reasoningContent = chunk))
        }
        _state.update { it.copy(messages = msgs) }
    }

    private suspend fun finalizeMessage(conversationId: String) {
        try {
            val msgs = _state.value.messages
            val lastAssistant = msgs.lastOrNull { it.role == AiMessageRole.ASSISTANT }
            if (lastAssistant != null) {
                val alreadyInSession = sessionManager.getMessages().any {
                    it.role == AiMessageRole.ASSISTANT && it.content == lastAssistant.content
                }
                if (!alreadyInSession) sessionManager.addMessage(lastAssistant)
                try {
                    sessionManager.saveMessage(lastAssistant, conversationId)
                } catch (e: Exception) {
                    android.util.Log.e("AiViewModel", "save assistant failed", e)
                }
            }

            val firstUserMsg = msgs.firstOrNull { it.role == AiMessageRole.USER }
            if (firstUserMsg != null && _state.value.currentConversationTitle == "محادثة جديدة") {
                val title = firstUserMsg.content.take(50).trim().let {
                    if (it.length >= 50) "$it..." else it
                }.ifEmpty { "محادثة جديدة" }
                try {
                    sessionManager.updateConversationTitle(conversationId, title)
                } catch (_: Exception) {}
                _state.update { it.copy(currentConversationTitle = title) }
            }

            try {
                sessionManager.saveConversation(_state.value.currentConversationTitle, conversationId)
            } catch (_: Exception) {}
        } finally {
            _state.update { it.copy(isStreaming = false) }
        }
    }

    fun loadConversation(conversationEntity: AiConversationEntity) {
        viewModelScope.launch {
            val providerType = try {
                AiProviderType.valueOf(conversationEntity.providerType)
            } catch (_: Exception) {
                AiProviderType.AGNES_AI
            }
            val level = ThinkingLevel.fromKey(conversationEntity.thinkingLevel)

            updateProvider(providerType, conversationEntity.modelId, level)
            persistSettings(providerType, conversationEntity.modelId, level)

            val entities = sessionManager.loadMessagesFromDb(conversationEntity.id)
            sessionManager.loadSession(conversationEntity.id, entities)

            _state.update {
                it.copy(
                    currentProviderType = providerType,
                    currentModelId = conversationEntity.modelId,
                    thinkingLevel = level,
                    currentConversationId = conversationEntity.id,
                    currentConversationTitle = conversationEntity.title,
                    messages = sessionManager.getMessages(),
                    error = null,

                    pendingApproval = null,
                    isStreaming = false
                )
            }
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            sessionManager.deleteConversation(conversationId)
            if (_state.value.currentConversationId == conversationId) {
                startNewConversation()
            }
            loadConversations()
        }
    }

    fun startNewConversation() {
        val state = _state.value
        val conversationId = sessionManager.startNewSession(
            state.currentProviderType,
            state.currentModelId,
            state.thinkingLevel
        )
        viewModelScope.launch {
            try {
                sessionManager.ensureConversationInDb("محادثة جديدة")
            } catch (e: Exception) {
                android.util.Log.e("AiViewModel", "ensureConversation failed", e)
            }
        }
        _state.update {
            it.copy(
                messages = emptyList(),
                currentConversationId = conversationId,
                currentConversationTitle = "محادثة جديدة",
                error = null,

                pendingApproval = null,
                isStreaming = false
            )
        }
    }

    private fun loadConversations() {
        viewModelScope.launch {
            aiDao.getAllConversations().collect { list ->
                _state.update { it.copy(conversations = list) }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun buildSystemPrompt(): String {
        return """
أنت مساعد ذكي ومفيد لتطبيق "واتشر" (Watcher) لإدارة ومشاهدة الأفلام والمسلسلات.
مهمتك هي مساعدة المستخدم في كل ما يتعلق بالمحتوى السينمائي والتلفزيوني.

لديك صلاحية الوصول إلى:
1. البحث في قاعدة بيانات TMDB (الأفلام والمسلسلات)
2. عرض قائمة المشاهدة الخاصة بالمستخدم
3. عرض التحميلات السابقة
4. إضافة عناصر إلى قائمة المشاهدة
5. قراءة تفاصيل أي فيلم أو مسلسل

ملاحظات مهمة:
- البيانات من TMDB تكون دائماً باللغة الإنجليزية (الأسئلة والإنجليزية)
- عند إضافة عنصر لقائمة المشاهدة، استخدم العنوان الإنجليزي
- استخدم الأدوات المتاحة فقط للإجابة على أسئلة المستخدم
- أجب بنفس لغة المستخدم في المحادثة
- كن موجزاً ومفيداً في إجاباتك
        """.trimIndent()
    }
}
