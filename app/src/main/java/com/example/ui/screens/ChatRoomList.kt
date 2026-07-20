package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.auth.UserManager
import com.example.chat.ChatManager
import com.example.chat.LastMessage
import com.example.chat.UserChat
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

@Composable
fun ChatRoomList(
    userId: String,
    onChatClick: (UserChat) -> Unit,
    modifier: Modifier = Modifier
) {
    var chats by remember { mutableStateOf<List<UserChat>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadedProfileCount by remember { mutableStateOf(0) }

    LaunchedEffect(userId) {
        ChatManager.ensurePublicRoom(userId)
        ChatManager.listenForUserChats(userId) { chatList ->
            chats = chatList.sortedByDescending { it.lastMessage?.timestamp ?: 0L }
        }
    }

    LaunchedEffect(chats, loadedProfileCount) {
        if (chats.isNotEmpty() && loadedProfileCount >= chats.size) {
            isLoading = false
        }
    }

    Column(modifier = modifier) {
        if (isLoading) {
            repeat(4) { ChatRoomSkeleton() }
        } else {
            chats.forEach { chat ->
                ChatRoomItem(
                    chat = chat,
                    onClick = { onChatClick(chat) },
                    onProfileLoaded = { loadedProfileCount++ }
                )
            }
        }
    }
}

@Composable
private fun ChatRoomSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .width(200.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            )
        }
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        )
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 76.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    )
}

@Composable
private fun ChatRoomItem(
    chat: UserChat,
    onClick: () -> Unit,
    onProfileLoaded: () -> Unit = {}
) {
    var displayName by remember(chat.roomId) {
        mutableStateOf(
            chat.otherUserName.ifEmpty {
                if (chat.roomType == "public") "General Chat" else "User"
            }
        )
    }
    var displayAvatar by remember(chat.roomId) { mutableStateOf(chat.otherUserAvatarUrl) }
    var globalLastMessage by remember(chat.roomId) { mutableStateOf<LastMessage?>(null) }
    var profileLoaded by remember(chat.roomId) { mutableStateOf(false) }
    val isPublic = chat.roomType == "public"

    LaunchedEffect(chat.roomId, chat.roomType, chat.otherUserId) {
        if (chat.roomType == "public") {
            displayName = ChatManager.getChatRoomName(chat.roomId) ?: "General Chat"
            displayAvatar = ChatManager.getChatRoomImage(chat.roomId) ?: ""

            val snap = FirebaseDatabase.getInstance().reference
                .child("chat_rooms").child(chat.roomId).child("lastMessage")
                .get().await()
            val msg = snap.getValue(LastMessage::class.java)
            if (msg != null) globalLastMessage = msg
        } else {
            if (chat.otherUserId.isNotEmpty()) {
                val profile = UserManager.getProfile(chat.otherUserId)
                if (profile != null) {
                    if (profile.name.isNotEmpty()) displayName = profile.name
                    if (profile.avatarUrl.isNotEmpty()) displayAvatar = profile.avatarUrl
                }
            }
        }
        if (!profileLoaded) {
            profileLoaded = true
            onProfileLoaded()
        }
    }

    if (isPublic) {
        val publicRef = FirebaseDatabase.getInstance().reference
            .child("chat_rooms").child(chat.roomId).child("lastMessage")
        DisposableEffect(chat.roomId) {
            val listener = object : ValueEventListener {
                override fun onDataChange(s: com.google.firebase.database.DataSnapshot) {
                    val m = s.getValue(LastMessage::class.java)
                    if (m != null) globalLastMessage = m
                }
                override fun onCancelled(e: com.google.firebase.database.DatabaseError) {}
            }
            publicRef.addValueEventListener(listener)
            onDispose { publicRef.removeEventListener(listener) }
        }
    }

    val lastMsg = if (isPublic) (globalLastMessage ?: chat.lastMessage) else chat.lastMessage

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
            if (displayAvatar.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(displayAvatar)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = if (isPublic) JetBrainsMonoFontFamily else null,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                if (isPublic) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "group",
                        fontSize = 10.sp,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = lastMsg?.let {
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
            text = lastMsg?.let { formatChatTimestamp(it.timestamp) } ?: "",
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
