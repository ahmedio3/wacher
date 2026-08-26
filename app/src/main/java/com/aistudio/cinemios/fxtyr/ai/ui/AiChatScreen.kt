package com.aistudio.cinemios.fxtyr.ai.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aistudio.cinemios.fxtyr.ai.AiViewModel
import com.aistudio.cinemios.fxtyr.ai.PROVIDER_CONFIGS
@Composable
fun AiChatScreen(
    onBackClick: () -> Unit,
    viewModel: AiViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var selectorExpanded by remember { mutableStateOf(false) }
    var drawerOpen by remember { mutableStateOf(false) }

    val currentModel = PROVIDER_CONFIGS[state.currentProviderType]?.models
        ?.find { it.id == state.currentModelId }
    val supportsVision = currentModel?.supportsVision ?: false

    val totalToolExecutions = state.messages.sumOf { it.toolExecutions.size }
    LaunchedEffect(state.messages.size, state.isStreaming, totalToolExecutions) {
        if (state.messages.isNotEmpty() || state.isStreaming) {
            listState.animateScrollToItem(
                listState.layoutInfo.totalItemsCount.coerceAtLeast(0)
            )
        }
    }

    LaunchedEffect(state.pendingApproval) {
        if (state.pendingApproval != null) {
            listState.animateScrollToItem(
                listState.layoutInfo.totalItemsCount.coerceAtLeast(0)
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            topBar = {}
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (state.messages.isNotEmpty() || state.isStreaming) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(top = 56.dp, bottom = 8.dp)
                        ) {
                            items(
                                items = state.messages,
                                key = { msg -> msg.id }
                            ) { msg ->
                                AiMessageBubble(message = msg)
                            }

                            if (state.pendingApproval != null) {
                                item {
                                    ApprovalRequestCard(
                                        description = state.pendingApproval!!.description,
                                        onApprove = { viewModel.approveAction() },
                                        onReject = { viewModel.rejectAction() }
                                    )
                                }
                            }

                            if (state.isStreaming) {
                                item { AiTypingIndicator() }
                            }

                            item { Spacer(modifier = Modifier.height(8.dp)) }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "كيف يمكنني مساعدتك؟",
                                    fontSize = 17.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "يمكنك سؤالي عن الأفلام، المسلسلات، أو إدارة محتواك",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 40.dp)
                                )
                            }
                        }
                    }

                    AiInputBar(
                        onSendText = { text, imageBase64 ->
                            viewModel.sendMessage(text, imageBase64)
                        },
                        supportsVision = supportsVision,
                        isStreaming = state.isStreaming,
                        onStopStreaming = { viewModel.stopStreaming() }
                    )
                }

                AiHeader(
                    providerType = state.currentProviderType,
                    modelId = state.currentModelId,
                    thinkingLevel = state.thinkingLevel,
                    selectorExpanded = selectorExpanded,
                    onBackClick = onBackClick,
                    onModelClick = { selectorExpanded = true },
                    onMenuClick = { drawerOpen = true },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(2f)
                )

                ModelSelectorPanel(
                    expanded = selectorExpanded,
                    currentProviderType = state.currentProviderType,
                    currentModelId = state.currentModelId,
                    currentThinkingLevel = state.thinkingLevel,
                    onSelectModel = { provider, modelId ->
                        viewModel.selectModel(provider, modelId)
                    },
                    onSelectThinking = { level ->
                        viewModel.setThinkingLevel(level)
                    },
                    onDismiss = { selectorExpanded = false },
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(3f)
                )
            }
        }

        ConversationsDrawer(
            visible = drawerOpen,
            conversations = state.conversations,
            currentConversationId = state.currentConversationId,
            onConversationClick = { conversation ->
                viewModel.loadConversation(conversation)
                drawerOpen = false
            },
            onNewConversation = {
                viewModel.startNewConversation()
                drawerOpen = false
            },
            onDeleteConversation = { id ->
                viewModel.deleteConversation(id)
            },
            onDismiss = { drawerOpen = false }
        )

        if (state.error != null) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .navigationBarsPadding(),
                action = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("حسناً")
                    }
                }
            ) {
                Text(state.error!!)
            }
        }
    }
}
