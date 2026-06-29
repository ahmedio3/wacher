package com.example.data.ai

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AiChatState {
    data object Idle : AiChatState()
    data object Loading : AiChatState()
    data class Success(val response: String) : AiChatState()
    data class Error(val message: String) : AiChatState()
}

class AiChatViewModel(private val context: Context) : ViewModel() {

    private val repository = AiChatRepository()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _chatState = MutableStateFlow<AiChatState>(AiChatState.Idle)
    val chatState: StateFlow<AiChatState> = _chatState.asStateFlow()

    private val _providers = MutableStateFlow<List<AiProvider>>(emptyList())
    val providers: StateFlow<List<AiProvider>> = _providers.asStateFlow()

    private val _selectedProviderId = MutableStateFlow<String?>(null)
    val selectedProviderId: StateFlow<String?> = _selectedProviderId.asStateFlow()

    private val _selectedModelName = MutableStateFlow<String?>(null)
    val selectedModelName: StateFlow<String?> = _selectedModelName.asStateFlow()

    private val _streamingContent = MutableStateFlow("")
    val streamingContent: StateFlow<String> = _streamingContent.asStateFlow()

    init {
        loadProviders()
    }

    private fun loadProviders() {
        _providers.value = AiProviderManager.getProviders(context)
        val default = AiProviderManager.getDefaultProvider(context)
        if (default != null) {
            _selectedProviderId.value = default.id
            _selectedModelName.value = default.models.firstOrNull()?.name
            // Load saved messages
            _messages.value = AiProviderManager.loadMessages(context, default.id)
        }
    }

    fun selectProvider(providerId: String) {
        _selectedProviderId.value = providerId
        val provider = _providers.value.find { it.id == providerId }
        _selectedModelName.value = provider?.models?.firstOrNull()?.name
        // Load messages for this provider
        _messages.value = AiProviderManager.loadMessages(context, providerId)
    }

    fun selectModel(modelName: String) {
        _selectedModelName.value = modelName
    }

    fun refreshProviders() {
        loadProviders()
    }

    fun sendMessage(content: String) {
        val providerId = _selectedProviderId.value ?: return
        val modelName = _selectedModelName.value ?: return
        val provider = _providers.value.find { it.id == providerId } ?: return
        val model = provider.models.find { it.name == modelName } ?: return

        // Add user message
        val userMsg = ChatMessage(role = "user", content = content)
        val updatedMessages = _messages.value + userMsg
        _messages.value = updatedMessages
        AiProviderManager.saveMessages(context, providerId, updatedMessages)

        _chatState.value = AiChatState.Loading
        _streamingContent.value = ""

        // Build system message for context
        val systemContext = buildSystemContext()
        val fullMessages = listOf(ChatMessage(role = "system", content = systemContext)) + updatedMessages

        viewModelScope.launch {
            repository.chatCompletion(
                provider = provider,
                model = model,
                messages = fullMessages,
                onEvent = { event ->
                    if (event.error != null) {
                        _chatState.value = AiChatState.Error(event.error)
                        return@launch
                    }
                    if (event.content.isNotEmpty()) {
                        _streamingContent.value += event.content
                    }
                    if (event.isDone) {
                        val finalContent = _streamingContent.value
                        val assistantMsg = ChatMessage(role = "assistant", content = finalContent)
                        val finalMessages = updatedMessages + assistantMsg
                        _messages.value = finalMessages
                        AiProviderManager.saveMessages(context, providerId, finalMessages)
                        _streamingContent.value = ""
                        _chatState.value = AiChatState.Success(finalContent)
                    }
                }
            )
        }
    }

    fun clearChat() {
        val providerId = _selectedProviderId.value ?: return
        _messages.value = emptyList()
        _streamingContent.value = ""
        _chatState.value = AiChatState.Idle
        AiProviderManager.clearMessages(context, providerId)
    }

    fun deleteChat(messageIndex: Int) {
        // Remove messages from this index onward
        val updated = _messages.value.take(messageIndex)
        _messages.value = updated
        val providerId = _selectedProviderId.value ?: return
        AiProviderManager.saveMessages(context, providerId, updated)
    }

    // Provider CRUD
    fun addProvider(provider: AiProvider) {
        AiProviderManager.addProvider(context, provider)
        _providers.value = AiProviderManager.getProviders(context)
        _selectedProviderId.value = provider.id
        _selectedModelName.value = provider.models.firstOrNull()?.name
        _messages.value = emptyList()
        _chatState.value = AiChatState.Idle
    }

    fun updateProvider(provider: AiProvider) {
        AiProviderManager.updateProvider(context, provider)
        _providers.value = AiProviderManager.getProviders(context)
    }

    fun deleteProvider(providerId: String) {
        AiProviderManager.deleteProvider(context, providerId)
        _providers.value = AiProviderManager.getProviders(context)
        if (_selectedProviderId.value == providerId) {
            val default = AiProviderManager.getDefaultProvider(context)
            if (default != null) {
                _selectedProviderId.value = default.id
                _selectedModelName.value = default.models.firstOrNull()?.name
            } else {
                _selectedProviderId.value = null
                _selectedModelName.value = null
            }
        }
    }

    private fun buildSystemContext(): String {
        return """أنت مساعد ذكاء اصطناعي متخصص في الأفلام والمسلسلات. 
اسمك "واتشيرا" (Watchera).
أنت تعمل ضمن تطبيق لمشاهدة وتحميل الأفلام والمسلسلات.

تعليمات مهمة:
- تحدث باللغة العربية الفصحى دائماً.
- كن مفيداً ودقيقاً في معلوماتك.
- إذا سألك المستخدم عن فيلم أو مسلسل معين، قدم معلومات عنه إن كنت تعرفه.
- يمكنك اقتراح أفلام ومسلسلات بناءً على تفضيلات المستخدم.
- إذا طلب منك المستخدم البحث في التحميلات أو المفضلة، أخبره أن هذه الميزة قيد التطوير.
- كن ودوداً ومحترماً.
- لا تقدم محتوى غير لائق أو مسيء."""
    }
}
