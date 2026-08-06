package com.example.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.example.ai.AiViewModel
import com.example.ai.PROVIDER_CONFIGS
import com.example.ui.components.bouncyOverscroll

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

    LaunchedEffect(state.messages.size, state.isStreaming) {
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
                                .fillMaxWidth()
                                .bouncyOverscroll(),
                            contentPadding = PaddingValues(top = 56.dp, bottom = 8.dp)
                        ) {
                            itemsIndexed(state.messages) { _, msg ->
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
                        isStreaming = state.isStreaming
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
