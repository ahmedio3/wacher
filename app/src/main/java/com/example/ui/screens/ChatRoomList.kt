package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.chat.ChatManager
import com.example.chat.UserChat

@Composable
fun ChatRoomList(
    userId: String,
    onChatClick: (UserChat) -> Unit,
    modifier: Modifier = Modifier
) {
    var chats by remember { mutableStateOf<List<UserChat>>(emptyList()) }

    LaunchedEffect(userId) {
        ChatManager.listenForUserChats(userId) { chatList ->
            chats = chatList.filter { it.lastMessage != null }
                .sortedByDescending { it.lastMessage?.timestamp ?: 0L }
        }
    }

    DisposableEffect(userId) {
        onDispose { /* listener cleanup handled internally */ }
    }

    if (chats.isEmpty()) return

    Column(modifier = modifier) {
        chats.forEach { chat ->
            ChatRoomItem(chat = chat, onClick = { onChatClick(chat) })
        }
    }
}

@Composable
private fun ChatRoomItem(
    chat: UserChat,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (chat.otherUserAvatarUrl.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(chat.otherUserAvatarUrl)
                        .crossfade(200)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chat.otherUserName.ifEmpty {
                    if (chat.roomType == "public") "General Chat" else "User"
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = chat.lastMessage?.let {
                    if (it.type == "image") "🖼️ صورة"
                    else it.text
                } ?: "",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = chat.lastMessage?.let { formatChatTimestamp(it.timestamp) } ?: "",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )
    }

    HorizontalDivider(
        modifier = Modifier.padding(start = 76.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    )
}

private fun formatChatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "الآن"
        diff < 3_600_000 -> "${diff / 60_000} د"
        diff < 86_400_000 -> "${diff / 3_600_000} س"
        diff < 604_800_000 -> "${diff / 86_400_000} ي"
        else -> {
            val sdf = java.text.SimpleDateFormat("MMM d", java.util.Locale.US)
            sdf.format(java.util.Date(timestamp))
        }
    }
}
