package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.auth.UserManager
import com.example.chat.ChatManager
import com.example.chat.Message
import com.example.chat.ReplyTo
import com.example.data.remote.ImgBBUploader
import com.example.ui.components.ChatInputBar
import com.example.ui.components.ChatMessageBubble
import com.example.ui.components.UserProfileBottomSheet
import com.example.utils.isLatinText
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    roomId: String,
    isPublic: Boolean,
    onBackClick: () -> Unit,
    onNavigateToDM: (String, Boolean) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val listState = rememberLazyListState()

    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var replyTo by remember { mutableStateOf<ReplyTo?>(null) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    var chatRoomName by remember { mutableStateOf("") }
    var chatRoomImageUrl by remember { mutableStateOf("") }
    var isChangingImage by remember { mutableStateOf(false) }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    var userProfileSheet by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var hasScrolledToBottom by remember { mutableStateOf(false) }

    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            if (layoutInfo.totalItemsCount == 0) return@derivedStateOf true
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItem >= layoutInfo.totalItemsCount - 2
        }
    }

    val headerImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isChangingImage = true
            scope.launch {
                val bitmap = uriToBitmap(uri, context)
                if (bitmap != null) {
                    val url = withContext(Dispatchers.IO) { ImgBBUploader.uploadImage(bitmap) }
                    if (url != null) {
                        ChatManager.updateChatRoomImage(roomId, url)
                        chatRoomImageUrl = url
                    }
                }
                isChangingImage = false
            }
        }
    }

    LaunchedEffect(roomId) {
        if (isPublic) {
            chatRoomName = ChatManager.getChatRoomName(roomId) ?: "General Chat"
            chatRoomImageUrl = ChatManager.getChatRoomImage(roomId) ?: ""
        }

        ChatManager.listenForMessages(roomId) { msgList ->
            messages = msgList
        }
    }

    LaunchedEffect(messages) {
        if (messages.isEmpty()) return@LaunchedEffect
        val lastMessage = messages.last()
        if (!hasScrolledToBottom) {
            listState.scrollToItem(messages.size - 1)
            hasScrolledToBottom = true
        } else if (lastMessage.senderId == currentUserId) {
            listState.animateScrollToItem(messages.size - 1)
        } else if (isAtBottom) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(replyTo) {
        if (replyTo != null && hasScrolledToBottom && messages.isNotEmpty()) {
            kotlinx.coroutines.delay(100)
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    DisposableEffect(roomId) {
        onDispose {
            ChatManager.removeListener(roomId)
        }
    }

    val displayName = if (isPublic) chatRoomName.ifEmpty { "General Chat" } else ""

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            topBar = { /* empty - we use floating header */ }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (messages.isNotEmpty()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            reverseLayout = false
                        ) {
                            if (hasMore && messages.size >= 50) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isLoadingMore) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            TextButton(onClick = {
                                                scope.launch {
                                                    isLoadingMore = true
                                                    val oldest = messages.minOfOrNull { it.timestamp } ?: 0L
                                                    val older = ChatManager.loadMoreMessages(roomId, oldest)
                                                    if (older.isEmpty()) hasMore = false
                                                    isLoadingMore = false
                                                }
                                            }) {
                                                Text("تحميل المزيد")
                                            }
                                        }
                                    }
                                }
                            }

                            val groupedMessages = groupMessages(messages)
                            items(groupedMessages, key = { "${it.message.id}_${it.isLast}_${it.showAvatar}" }) { group ->
                                ChatMessageBubble(
                                    message = group.message,
                                    isMine = group.message.senderId == currentUserId,
                                    isFirstInGroup = group.isFirst,
                                    isLastInGroup = group.isLast,
                                    showAvatar = group.showAvatar,
                                    onReply = { msg ->
                                        replyTo = ReplyTo(
                                            messageId = msg.id,
                                            text = msg.text.ifEmpty { "🖼️ صورة" },
                                            senderName = msg.senderName
                                        )
                                    },
                                    onImageClick = { url -> previewImageUrl = url },
                                    onUserClick = { userId, name, avatarUrl ->
                                        if (userId != currentUserId) {
                                            userProfileSheet = Triple(userId, name, avatarUrl)
                                        }
                                    }
                                )
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
                            Text(
                                text = "No messages yet.\nStart chatting!",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                fontSize = 14.sp
                            )
                        }
                    }

                    ChatInputBar(
                        replyTo = replyTo,
                        onSendText = { text ->
                            val currentReply = replyTo
                            replyTo = null
                            scope.launch {
                                val profile = UserManager.getProfile(currentUserId)
                                val name = profile?.name ?: FirebaseAuth.getInstance().currentUser?.displayName ?: "User"
                                val avatarUrl = profile?.avatarUrl ?: ""
                                val msg = Message(
                                    senderId = currentUserId,
                                    senderName = name,
                                    senderAvatarUrl = avatarUrl,
                                    text = text,
                                    type = "text",
                                    replyTo = currentReply,
                                    timestamp = System.currentTimeMillis()
                                )
                                ChatManager.sendMessage(roomId, msg)
                            }
                        },
                        onImagePicked = { uri ->
                            val currentReply = replyTo
                            replyTo = null
                            scope.launch {
                                val profile = UserManager.getProfile(currentUserId)
                                val name = profile?.name ?: FirebaseAuth.getInstance().currentUser?.displayName ?: "User"
                                val avatarUrl = profile?.avatarUrl ?: ""
                                val bitmap = uriToBitmap(uri, context)
                                if (bitmap != null) {
                                    val success = ChatManager.sendImageMessage(
                                        roomId = roomId,
                                        bitmap = bitmap,
                                        senderId = currentUserId,
                                        senderName = name,
                                        senderAvatarUrl = avatarUrl,
                                        replyTo = currentReply
                                    )
                                    if (!success) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Failed to upload image",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        },
                        onDismissReply = { replyTo = null },
                        modifier = Modifier
                    )
                }

                Row(
                    modifier = Modifier
                        .zIndex(1f)
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(top = 9.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "رجوع",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    if (isPublic) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White)
                                    .clickable { headerImagePicker.launch("image/*") }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (chatRoomImageUrl.isNotEmpty()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(chatRoomImageUrl)
                                                .crossfade(200)
                                                .build(),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    Text(
                                        text = displayName,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = if (isLatinText(displayName))
                                                androidx.compose.ui.text.font.FontFamily.Monospace
                                            else null,
                                            fontSize = 15.sp
                                        )
                                    )
                                    if (isChangingImage) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (previewImageUrl != null) {
            AlertDialog(
                onDismissRequest = { previewImageUrl = null },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                shape = RoundedCornerShape(16.dp),
                confirmButton = {
                    TextButton(onClick = { previewImageUrl = null }) {
                        Text("إغلاق")
                    }
                },
                text = {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(previewImageUrl)
                            .crossfade(200)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit
                    )
                }
            )
        }

        if (userProfileSheet != null) {
            val (targetId, targetName, targetAvatarUrl) = userProfileSheet!!
            UserProfileBottomSheet(
                userId = targetId,
                initialName = targetName,
                initialAvatarUrl = targetAvatarUrl,
                currentUserId = currentUserId,
                onDismiss = { userProfileSheet = null },
                onStartChat = { otherUserId ->
                    userProfileSheet = null
                    scope.launch {
                        val profile = UserManager.getProfile(currentUserId)
                        val name = profile?.name ?: FirebaseAuth.getInstance().currentUser?.displayName ?: "User"
                        val avatarUrl = profile?.avatarUrl ?: ""
                        val roomId = ChatManager.getOrCreateDMRoom(
                            currentUserId = currentUserId,
                            otherUserId = otherUserId,
                            otherUserName = name,
                            otherUserAvatarUrl = avatarUrl
                        )
                        onNavigateToDM(roomId, false)
                    }
                }
            )
        }
    }
}

private suspend fun uriToBitmap(uri: Uri, context: android.content.Context): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        if (bitmap != null) {
            val maxDim = 1024
            val ratio = minOf(
                maxDim.toFloat() / bitmap.width,
                maxDim.toFloat() / bitmap.height
            )
            if (ratio < 1f) {
                val w = (ratio * bitmap.width).toInt()
                val h = (ratio * bitmap.height).toInt()
                Bitmap.createScaledBitmap(bitmap, w, h, true)
            } else bitmap
        } else null
    } catch (e: Exception) {
        null
    }
}

private data class MessageGroup(
    val message: Message,
    val isFirst: Boolean,
    val isLast: Boolean,
    val showAvatar: Boolean
)

private fun groupMessages(messages: List<Message>): List<MessageGroup> {
    if (messages.isEmpty()) return emptyList()
    val sorted = messages.sortedBy { it.timestamp }
    val result = mutableListOf<MessageGroup>()

    var i = 0
    while (i < sorted.size) {
        val current = sorted[i]
        val nextSameSender = (i + 1 until sorted.size).firstOrNull { sorted[it].senderId != current.senderId }
            ?: sorted.size
        val groupEnd = nextSameSender - 1

        for (j in i..groupEnd) {
            val msg = sorted[j]
            result.add(
                MessageGroup(
                    message = msg,
                    isFirst = j == i,
                    isLast = j == groupEnd,
                    showAvatar = j == groupEnd
                )
            )
        }
        i = nextSameSender
    }

    return result
}


