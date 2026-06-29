package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ai.AiChatState
import com.example.data.ai.AiChatViewModel
import com.example.data.ai.ChatMessage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    viewModel: AiChatViewModel,
    hasProvider: Boolean,
    onBack: () -> Unit,
    onConfigureProvider: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val messages by viewModel.messages.collectAsState()
    val chatState by viewModel.chatState.collectAsState()
    val streamingContent by viewModel.streamingContent.collectAsState()
    val providers by viewModel.providers.collectAsState()
    val selectedProviderId by viewModel.selectedProviderId.collectAsState()
    val selectedModelName by viewModel.selectedModelName.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Show provider config if no providers
    LaunchedEffect(hasProvider, providers) {
        if (!hasProvider && providers.isEmpty()) {
            // Auto-navigate to config
        }
    }

    // Auto-scroll to bottom
    LaunchedEffect(messages.size, streamingContent) {
        if (messages.isNotEmpty() || streamingContent.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        val provider = providers.find { it.id == selectedProviderId }
                        Text(
                            text = provider?.displayName ?: "الذكاء الاصطناعي",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        if (selectedModelName != null) {
                            Text(
                                text = selectedModelName!!,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    // Provider selector
                    if (providers.size > 1) {
                        var showMenu by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { showMenu = true }) {
                                Text("تغيير", fontSize = 13.sp)
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                providers.forEach { p ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                p.displayName,
                                                fontWeight = if (p.id == selectedProviderId) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            viewModel.selectProvider(p.id)
                                            showMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    // Model selector
                    val currentProvider = providers.find { it.id == selectedProviderId }
                    if (currentProvider != null && currentProvider.models.size > 1) {
                        var showModelMenu by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { showModelMenu = true }) {
                                Text(selectedModelName ?: "نموذج", fontSize = 11.sp, maxLines = 1)
                            }
                            DropdownMenu(expanded = showModelMenu, onDismissRequest = { showModelMenu = false }) {
                                currentProvider.models.forEach { m ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                m.name,
                                                fontWeight = if (m.name == selectedModelName) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 13.sp
                                            )
                                        },
                                        onClick = {
                                            viewModel.selectModel(m.name)
                                            showModelMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    // New chat
                    IconButton(onClick = { viewModel.clearChat() }) {
                        Icon(Icons.Default.Add, contentDescription = "محادثة جديدة")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (providers.isEmpty()) {
                // No providers configured
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "لم يتم إضافة مزود ذكاء اصطناعي",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onConfigureProvider,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("إضافة مزود")
                        }
                    }
                }
            } else {
                // Messages list
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(messages) { msg ->
                        ChatBubble(message = msg)
                    }

                    // Streaming response
                    if (streamingContent.isNotEmpty()) {
                        item {
                            ChatBubble(
                                message = ChatMessage(role = "assistant", content = streamingContent),
                                isStreaming = true
                            )
                        }
                    }

                    // Loading indicator
                    if (chatState is AiChatState.Loading && streamingContent.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }

                    // Error
                    if (chatState is AiChatState.Error) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = (chatState as AiChatState.Error).message,
                                    modifier = Modifier.padding(12.dp),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                // Input bar
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .navigationBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = {
                                Text(
                                    "اسأل عن فيلم أو مسلسل...",
                                    fontFamily = FontFamily.Default
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (inputText.isNotBlank() && chatState !is AiChatState.Loading) {
                                        viewModel.sendMessage(inputText.trim())
                                        inputText = ""
                                    }
                                }
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Default  // Default is IBM Plex via MaterialTheme
                            ),
                            maxLines = 4,
                            minLines = 1
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledIconButton(
                            onClick = {
                                if (inputText.isNotBlank() && chatState !is AiChatState.Loading) {
                                    viewModel.sendMessage(inputText.trim())
                                    inputText = ""
                                }
                            },
                            enabled = inputText.isNotBlank() && chatState !is AiChatState.Loading,
                            modifier = Modifier.size(48.dp),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "إرسال", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    isStreaming: Boolean = false
) {
    val isUser = message.role == "user"
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bgColor = if (isUser)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser)
        MaterialTheme.colorScheme.onPrimary
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = LocalConfiguration.current.screenWidthDp.dp * 0.8f)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(bgColor)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = message.content,
                color = textColor,
                fontSize = 15.sp,
                fontFamily = FontFamily.Default, // IBM Plex from theme
                lineHeight = 22.sp
            )
        }
        if (isStreaming) {
            Text(
                text = "يكتب...",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                modifier = Modifier.padding(start = 8.dp, top = 2.dp)
            )
        }
    }
}
