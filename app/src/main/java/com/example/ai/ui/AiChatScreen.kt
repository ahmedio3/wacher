package com.example.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.AiMessageRole
import com.example.ai.AiViewModel
import com.example.ai.PROVIDER_CONFIGS
import com.google.firebase.auth.FirebaseAuth

@Composable
fun AiChatScreen(
    onBackClick: () -> Unit,
    onOpenConversations: () -> Unit,
    viewModel: AiViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var showModelSheet by remember { mutableStateOf(false) }
    var showConversationSheet by remember { mutableStateOf(false) }

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
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(top = 56.dp, bottom = 8.dp)
                        ) {
                            itemsIndexed(state.messages) { index, msg ->
                                AiMessageBubble(message = msg)
                            }

                            items(state.toolExecutions) { execution ->
                                ToolCallCard(execution = execution)
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
                                item {
                                    AiTypingIndicator()
                                }
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
                                    text = "✨",
                                    fontSize = 48.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "كيف يمكنني مساعدتك؟",
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "يمكنك سؤالي عن الأفلام، المسلسلات، أو إدارة محتواك",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
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
                        modifier = Modifier
                    )
                }

                AiHeader(
                    providerType = state.currentProviderType,
                    modelId = state.currentModelId,
                    reasoningEnabled = state.reasoningEnabled,
                    isStreaming = state.isStreaming,
                    onBackClick = onBackClick,
                    onModelClick = { showModelSheet = true },
                    onMenuClick = { showConversationSheet = true },
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }

        if (state.error != null) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = { /* dismiss */ }) {
                        Text("حسناً")
                    }
                }
            ) {
                Text(state.error!!)
            }
        }
    }

    if (showModelSheet) {
        ModelSelectorSheet(
            currentProviderType = state.currentProviderType,
            currentModelId = state.currentModelId,
            onModelSelected = { providerType, modelId ->
                viewModel.selectModel(providerType, modelId)
                showModelSheet = false
            },
            onDismiss = { showModelSheet = false }
        )
    }
}
