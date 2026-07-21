package com.example.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.ai.data.AiConversationEntity
import com.example.ai.data.AiDao
import com.example.ai.data.AiMessageEntity
import com.example.data.local.MovieDatabase
import com.example.ai.provider.AiProviderService
import com.example.ai.provider.GeminiProvider
import com.example.ai.provider.OpenAiCompatibleProvider
import com.example.ai.provider.buildToolDeclarationsJson
import com.example.ai.tools.AiToolExecutor
import com.example.ai.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AiChatUiState(
    val messages: List<AiChatMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentProviderType: AiProviderType = AiProviderType.GEMINI,
    val currentModelId: String = getDefaultModel().id,
    val reasoningEnabled: Boolean = false,
    val conversations: List<AiConversationEntity> = emptyList(),
    val currentConversationId: String? = null,
    val currentConversationTitle: String = "محادثة جديدة",
    val toolExecutions: List<ToolExecutionDisplay> = emptyList(),
    val pendingApproval: ApprovalRequest? = null
)

data class ToolExecutionDisplay(
    val toolName: String,
    val status: ToolExecutionStatus,
    val summary: String = ""
)

enum class ToolExecutionStatus { RUNNING, SUCCESS, ERROR }

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

    private val _state = MutableStateFlow(AiChatUiState())
    val state: StateFlow<AiChatUiState> = _state.asStateFlow()

    private var currentProvider: AiProviderService? = null

    init {
        loadConversations()
        val defaultModel = getDefaultModel()
        updateProvider(defaultModel.providerType, defaultModel.id)
    }

    private fun updateProvider(providerType: AiProviderType, modelId: String) {
        currentProvider = when (providerType) {
            AiProviderType.GEMINI -> GeminiProvider(BuildConfig.GEMINI_API_KEY)
            AiProviderType.OPENCODE_ZEN -> OpenAiCompatibleProvider(
                PROVIDER_CONFIGS[AiProviderType.OPENCODE_ZEN]!!.baseUrl!!,
                BuildConfig.OPENCODE_ZEN_API_KEY
            )
            AiProviderType.BYNARA -> OpenAiCompatibleProvider(
                PROVIDER_CONFIGS[AiProviderType.BYNARA]!!.baseUrl!!,
                BuildConfig.BYNARA_API_KEY
            )
        }
    }

    fun selectModel(providerType: AiProviderType, modelId: String) {
        val config = PROVIDER_CONFIGS[providerType] ?: return
        val model = config.models.find { it.id == modelId } ?: return

        updateProvider(providerType, modelId)

        sessionManager.startNewSession(providerType, modelId, _state.value.reasoningEnabled)
        _state.update {
            it.copy(
                currentProviderType = providerType,
                currentModelId = modelId,
                currentConversationId = sessionManager.getCurrentConversationId(),
                currentConversationTitle = "محادثة جديدة",
                messages = emptyList(),
                error = null,
                toolExecutions = emptyList(),
                pendingApproval = null
            )
        }
    }

    fun toggleReasoning() {
        val enabled = !_state.value.reasoningEnabled
        _state.update { it.copy(reasoningEnabled = enabled) }
    }

    fun sendMessage(text: String, imageBase64: String? = null) {
        val state = _state.value
        if (state.isStreaming) return
        if (text.isBlank() && imageBase64 == null) return

        viewModelScope.launch {
            val conversationId = ensureConversation()

            val userMsg = AiChatMessage(
                role = AiMessageRole.USER,
                content = text,
                imageUrls = if (imageBase64 != null) listOf(imageBase64) else null
            )

            sessionManager.addMessage(userMsg)
            sessionManager.saveMessage(userMsg, conversationId)

            _state.update {
                it.copy(
                    messages = sessionManager.getMessages(),
                    isStreaming = true,
                    error = null,
                    toolExecutions = emptyList()
                )
            }

            processStreaming(conversationId)
        }
    }

    private suspend fun ensureConversation(): String {
        var conversationId = sessionManager.getCurrentConversationId()
        if (conversationId == null) {
            val state = _state.value
            conversationId = sessionManager.startNewSession(
                state.currentProviderType,
                state.currentModelId,
                state.reasoningEnabled
            )
            _state.update { it.copy(currentConversationId = conversationId) }
        }
        return conversationId
    }

    private suspend fun processStreaming(conversationId: String) {
        val provider = currentProvider ?: run {
            _state.update { it.copy(isStreaming = false, error = "No provider selected") }
            return
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
                reasoningEnabled = _state.value.reasoningEnabled,
                onEvent = { event ->
                    viewModelScope.launch {
                        handleStreamEvent(event, conversationId)
                    }
                }
            )
        } catch (e: Exception) {
            _state.update {
                it.copy(isStreaming = false, error = "فشل الاتصال: ${e.message}")
            }
        }
    }

    private suspend fun handleStreamEvent(event: AiStreamEvent, conversationId: String) {
        when (event.type) {
            AiStreamEventType.TEXT_CHUNK -> {
                appendToLastAssistantMessage(event.content)
            }
            AiStreamEventType.REASONING_CHUNK -> {
                appendToLastAssistantReasoning(event.reasoningContent ?: "")
            }
            AiStreamEventType.TOOL_CALLS -> {
                event.toolCalls?.let { executeToolCalls(it, conversationId) }
            }
            AiStreamEventType.DONE -> {
                if (event.finishReason == "TOOL_CALLS") {
                    // Tool results already handled
                } else {
                    finalizeMessage(conversationId)
                }
            }
            AiStreamEventType.TOOL_RESULTS -> {
                // handled by tool execution flow
            }
            AiStreamEventType.ERROR -> {
                _state.update { it.copy(isStreaming = false, error = event.content) }
            }
        }
    }

    private suspend fun executeToolCalls(toolCalls: List<AiToolCall>, conversationId: String) {
        for (call in toolCalls) {
            val display = ToolExecutionDisplay(
                toolName = call.name,
                status = ToolExecutionStatus.RUNNING,
                summary = "جارٍ التنفيذ..."
            )
            _state.update { it.copy(toolExecutions = it.toolExecutions + display) }

            val result = withContext(Dispatchers.IO) {
                toolExecutor.executeTool(call.name, call.arguments)
            }

            when (result) {
                is ToolResult.Success -> {
                    updateToolExecution(call.name, ToolExecutionStatus.SUCCESS, "تم بنجاح ✅")
                    val toolResult = AiToolResult(
                        toolCallId = call.id,
                        name = call.name,
                        content = result.data
                    )
                    sessionManager.addToolMessages(listOf(toolResult))

                    // Send tool results back to the provider
                    reInvokeProvider(conversationId)
                }
                is ToolResult.Error -> {
                    updateToolExecution(call.name, ToolExecutionStatus.ERROR, result.message)
                    val toolResult = AiToolResult(
                        toolCallId = call.id,
                        name = call.name,
                        content = "خطأ: ${result.message}"
                    )
                    sessionManager.addToolMessages(listOf(toolResult))
                    reInvokeProvider(conversationId)
                }
                is ToolResult.NeedsApproval -> {
                    _state.update {
                        it.copy(
                            pendingApproval = ApprovalRequest(
                                description = result.description,
                                toolName = call.name,
                                args = result.executeData
                            ),
                            toolExecutions = it.toolExecutions.map { exec ->
                                if (exec.toolName == call.name) exec.copy(
                                    status = ToolExecutionStatus.SUCCESS,
                                    summary = "في انتظار الموافقة..."
                                ) else exec
                            }
                        )
                    }
                }
            }
        }
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
                    val toolResult = AiToolResult(
                        toolCallId = "approved_${System.currentTimeMillis()}",
                        name = approval.toolName,
                        content = "تم التنفيذ بعد موافقة المستخدم: ${result.data}"
                    )
                    sessionManager.addToolMessages(listOf(toolResult))
                    updateToolExecution(approval.toolName, ToolExecutionStatus.SUCCESS, "تم التنفيذ ✅")
                    reInvokeProvider(conversationId)
                }
                is ToolResult.Error -> {
                    val toolResult = AiToolResult(
                        toolCallId = "approved_${System.currentTimeMillis()}",
                        name = approval.toolName,
                        content = "خطأ بعد الموافقة: ${result.message}"
                    )
                    sessionManager.addToolMessages(listOf(toolResult))
                    updateToolExecution(approval.toolName, ToolExecutionStatus.ERROR, result.message)
                    reInvokeProvider(conversationId)
                }
                is ToolResult.NeedsApproval -> { /* ignore recursive approval */ }
            }
        }
    }

    fun rejectAction() {
        val approval = _state.value.pendingApproval ?: return
        _state.update { it.copy(pendingApproval = null) }

        viewModelScope.launch {
            val conversationId = sessionManager.getCurrentConversationId() ?: return@launch

            val toolResult = AiToolResult(
                toolCallId = "rejected_${System.currentTimeMillis()}",
                name = approval.toolName,
                content = "تم رفض الإجراء من قبل المستخدم: ${approval.description}"
            )
            sessionManager.addToolMessages(listOf(toolResult))
            updateToolExecution(approval.toolName, ToolExecutionStatus.ERROR, "تم الرفض ❌")
            reInvokeProvider(conversationId)
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
            reasoningEnabled = _state.value.reasoningEnabled,
            onEvent = { event ->
                viewModelScope.launch {
                    handleStreamEvent(event, conversationId)
                }
            }
        )
    }

    private fun appendToLastAssistantMessage(chunk: String) {
        val msgs = _state.value.messages.toMutableList()
        val lastIdx = msgs.lastIndex
        if (lastIdx >= 0 && msgs[lastIdx].role == AiMessageRole.ASSISTANT) {
            val last = msgs[lastIdx]
            msgs[lastIdx] = last.copy(content = last.content + chunk)
        } else {
            msgs.add(AiChatMessage(role = AiMessageRole.ASSISTANT, content = chunk))
        }
        _state.update { it.copy(messages = msgs) }
    }

    private fun appendToLastAssistantReasoning(chunk: String) {
        val msgs = _state.value.messages.toMutableList()
        val lastIdx = msgs.lastIndex
        if (lastIdx >= 0 && msgs[lastIdx].role == AiMessageRole.ASSISTANT) {
            val last = msgs[lastIdx]
            val currentReasoning = last.reasoningContent ?: ""
            msgs[lastIdx] = last.copy(reasoningContent = currentReasoning + chunk)
        } else {
            msgs.add(AiChatMessage(role = AiMessageRole.ASSISTANT, content = "", reasoningContent = chunk))
        }
        _state.update { it.copy(messages = msgs) }
    }

    private fun updateToolExecution(toolName: String, status: ToolExecutionStatus, summary: String) {
        _state.update { state ->
            val updated = state.toolExecutions.map {
                if (it.toolName == toolName) it.copy(status = status, summary = summary) else it
            }
            state.copy(toolExecutions = updated)
        }
    }

    private suspend fun finalizeMessage(conversationId: String) {
        val msgs = _state.value.messages
        val lastAssistant = msgs.lastOrNull { it.role == AiMessageRole.ASSISTANT }
        if (lastAssistant != null) {
            sessionManager.addMessage(lastAssistant)
            sessionManager.saveMessage(lastAssistant, conversationId)
        }

        // Auto-generate title from first user message
        val firstUserMsg = msgs.firstOrNull { it.role == AiMessageRole.USER }
        if (firstUserMsg != null && _state.value.currentConversationTitle == "محادثة جديدة") {
            val title = firstUserMsg.content.take(50).trim().let {
                if (it.length >= 50) "$it..." else it
            }
            sessionManager.updateConversationTitle(conversationId, title.ifEmpty { "محادثة جديدة" })
            _state.update { it.copy(currentConversationTitle = title.ifEmpty { "محادثة جديدة" }) }
        }

        sessionManager.saveConversation(
            _state.value.currentConversationTitle,
            conversationId
        )

        _state.update { it.copy(isStreaming = false) }
        loadConversations()
    }

    fun loadConversation(conversationEntity: AiConversationEntity) {
        viewModelScope.launch {
            val providerType = try {
                AiProviderType.valueOf(conversationEntity.providerType)
            } catch (e: Exception) { AiProviderType.GEMINI }

            updateProvider(providerType, conversationEntity.modelId)

            val entities = sessionManager.loadMessagesFromDb(conversationEntity.id)
            sessionManager.loadSession(conversationEntity.id, entities)

            _state.update {
                it.copy(
                    currentProviderType = providerType,
                    currentModelId = conversationEntity.modelId,
                    reasoningEnabled = conversationEntity.reasoningEnabled,
                    currentConversationId = conversationEntity.id,
                    currentConversationTitle = conversationEntity.title,
                    messages = sessionManager.getMessages(),
                    error = null,
                    toolExecutions = emptyList(),
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
        sessionManager.startNewSession(state.currentProviderType, state.currentModelId, state.reasoningEnabled)
        _state.update {
            it.copy(
                messages = emptyList(),
                currentConversationId = sessionManager.getCurrentConversationId(),
                currentConversationTitle = "محادثة جديدة",
                error = null,
                toolExecutions = emptyList(),
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

    private fun buildSystemPrompt(): String {
        return """
أنت مساعد ذكي ومفيد لتطبيق "واتشر" (Watcher) لإدارة ومشاهدة الأفلام والمسلسلات.
مهمتك هي مساعدة المستخدم في كل ما يتعلق بالمحتوى السينمائي والتلفزيوني.

لديك صلاحية الوصول إلى:
1. البحث في قاعدة بيانات TMDB (الأفلام والمسلسلات)
2. عرض قائمة المشاهدة الخاصة بالمستخدم
3. عرض التحميلات السابقة
4. عرض سجل النشاطات
5. إضافة عناصر إلى قائمة المشاهدة
6. قراءة تفاصيل أي فيلم أو مسلسل

ملاحظات مهمة:
- إجراء "تحميل حلقة أو مسلسل" يتطلب موافقة المستخدم أولاً - اطلب الموافقة وانتظرها
- استخدم الأدوات المتاحة فقط للإجابة على أسئلة المستخدم
- أجب بنفس لغة المستخدم (إذا كتب عربي أجب عربي، إذا كتب إنجليزي أجب إنجليزي)
- كن موجزاً ومفيداً في إجاباتك
- عندما تبحث عن شيء، اشرح للمستخدم ماذا تفعل
        """.trimIndent()
    }
}
