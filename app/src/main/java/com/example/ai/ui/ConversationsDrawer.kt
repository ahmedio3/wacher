package com.example.ai.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.data.AiConversationEntity
import com.example.ui.components.bouncyOverscroll

@Composable
fun ConversationsDrawer(
    visible: Boolean,
    conversations: List<AiConversationEntity>,
    currentConversationId: String?,
    onConversationClick: (AiConversationEntity) -> Unit,
    onNewConversation: () -> Unit,
    onDeleteConversation: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(onClick = onDismiss)
            )

            // RTL: CenterEnd = left edge; slide from -X = from left
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.82f)
                    .statusBarsPadding()
                    .animateEnterExit(
                        enter = slideInHorizontally(tween(280)) { -it },
                        exit = slideOutHorizontally(tween(240)) { -it }
                    ),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp,
                shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "المحادثات",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = onNewConversation,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "محادثة جديدة",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    if (conversations.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "لا توجد محادثات بعد",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().bouncyOverscroll(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(conversations, key = { it.id }) { conversation ->
                                val isSelected = conversation.id == currentConversationId
                                var showDelete by remember { mutableStateOf(false) }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 3.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                            else Color.Transparent
                                        )
                                        .clickable { onConversationClick(conversation) }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = conversation.title,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${conversation.messageCount} رسالة · ${conversation.modelId}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = { showDelete = true },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "حذف",
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                if (showDelete) {
                                    AlertDialog(
                                        onDismissRequest = { showDelete = false },
                                        title = { Text("حذف المحادثة") },
                                        text = { Text("هل أنت متأكد من حذف هذه المحادثة؟") },
                                        confirmButton = {
                                            TextButton(onClick = {
                                                onDeleteConversation(conversation.id)
                                                showDelete = false
                                            }) {
                                                Text("حذف", color = Color(0xFFFF3B30))
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showDelete = false }) {
                                                Text("إلغاء")
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
