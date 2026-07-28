package com.example.ai.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
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
import com.example.ai.AiChatMessage
import com.example.ai.AiMessageRole
import com.example.ui.theme.IBMPlexSansArabicFontFamily
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.delay

@Composable
fun AiMessageBubble(
    message: AiChatMessage,
    modifier: Modifier = Modifier,
    onCopy: ((String) -> Unit)? = null
) {
    val isUser = message.role == AiMessageRole.USER
    val isAssistant = message.role == AiMessageRole.ASSISTANT
    val clipboardManager = LocalClipboardManager.current
    var showCopiedToast by remember { mutableStateOf(false) }

    LaunchedEffect(showCopiedToast) {
        if (showCopiedToast) {
            delay(1500)
            showCopiedToast = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isAssistant && !message.reasoningContent.isNullOrBlank()) {
            ReasoningSection(reasoningContent = message.reasoningContent!!)
            Spacer(modifier = Modifier.height(4.dp))
        }

        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = if (isUser) 20.dp else 4.dp,
                        topEnd = if (isUser) 4.dp else 20.dp,
                        bottomStart = 20.dp,
                        bottomEnd = 20.dp
                    )
                )
                .background(
                    if (isUser) Color(0xFF8C6D4F)
                    else MaterialTheme.colorScheme.surface
                )
                .padding(14.dp)
        ) {
            Column {
                if (message.content.isNotEmpty()) {
                    Text(
                        text = message.content,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontFamily = IBMPlexSansArabicFontFamily
                    )
                }
                // Copy button for assistant messages
                if (!isUser && message.content.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(message.content))
                                showCopiedToast = true
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "نسخ",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                if (showCopiedToast) {
                    Text(
                        text = "تم النسخ",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(if (isUser) Alignment.End else Alignment.Start)
                    )
                }
            }
        }
    }
}

@Composable
fun ReasoningSection(
    reasoningContent: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .widthIn(max = 320.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Code,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (expanded) "إخفاء التفكير" else "إظهار التفكير",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = IBMPlexSansArabicFontFamily,
                    color = MaterialTheme.colorScheme.primary
                )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (expanded) "▲" else "▼",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (expanded) {
            Text(
                text = reasoningContent,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                fontFamily = IBMPlexSansArabicFontFamily,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}
