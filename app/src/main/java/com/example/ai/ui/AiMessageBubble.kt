package com.example.ai.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AiChatMessage
import com.example.ai.AiMessageRole
import com.example.ai.ToolExecutionStatus
import com.example.ui.theme.IBMPlexSansArabicFontFamily
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun AiMessageBubble(
    message: AiChatMessage,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == AiMessageRole.USER
    val isAssistant = message.role == AiMessageRole.ASSISTANT
    val hasImage = message.imageUrls?.isNotEmpty() == true

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .animateContentSize(animationSpec = tween(200)),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isAssistant && !message.reasoningContent.isNullOrBlank()) {
            ReasoningSection(reasoningContent = message.reasoningContent!!)
            Spacer(modifier = Modifier.height(2.dp))
        }

        if (isUser) {
            if (hasImage) {
                Text(
                    text = "صورة مرفقة",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontFamily = IBMPlexSansArabicFontFamily,
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .padding(horizontal = 4.dp)
                )
            }
            Box(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 6.dp,
                            bottomStart = 18.dp,
                            bottomEnd = 18.dp
                        )
                    )
                    .background(Color(0xFF8C6D4F))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                if (message.content.isNotEmpty()) {
                    Text(
                        text = message.content,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = Color.White,
                        fontFamily = IBMPlexSansArabicFontFamily
                    )
                }
            }
        } else {
            if (message.content.isNotEmpty()) {
                MarkdownText(
                    text = message.content,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        if (isAssistant && message.toolExecutions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            message.toolExecutions.forEach { execution ->
                ToolCallCard(execution = execution)
            }
        }
    }
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier
) {
    val annotatedString = parseMarkdown(text)

    Text(
        text = annotatedString,
        fontSize = 15.sp,
        lineHeight = 23.sp,
        color = MaterialTheme.colorScheme.onSurface,
        fontFamily = IBMPlexSansArabicFontFamily,
        modifier = modifier
    )
}

fun parseMarkdown(text: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        val lines = text.lines()
        var inCodeBlock = false
        val codeBuffer = StringBuilder()

        for (i in lines.indices) {
            val line = lines[i]

            if (line.trimStart().startsWith("```")) {
                if (inCodeBlock) {
                    withStyle(SpanStyle(
                        fontFamily = JetBrainsMonoFontFamily,
                        background = Color(0xFF1E1E1E).copy(alpha = 0.08f),
                        fontSize = 13.sp
                    )) {
                        append(codeBuffer.toString().trimEnd())
                    }
                    codeBuffer.clear()
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                }
                continue
            }

            if (inCodeBlock) {
                if (codeBuffer.isNotEmpty()) codeBuffer.append('\n')
                codeBuffer.append(line)
                continue
            }

            if (line.trimStart().startsWith("# ")) {
                withStyle(SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold)) {
                    append(line.trimStart().removePrefix("# "))
                }
                if (i < lines.lastIndex) append('\n')
                continue
            }
            if (line.trimStart().startsWith("## ")) {
                withStyle(SpanStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold)) {
                    append(line.trimStart().removePrefix("## "))
                }
                if (i < lines.lastIndex) append('\n')
                continue
            }
            if (line.trimStart().startsWith("### ")) {
                withStyle(SpanStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold)) {
                    append(line.trimStart().removePrefix("### "))
                }
                if (i < lines.lastIndex) append('\n')
                continue
            }

            appendFormattedLine(line)
            if (i < lines.lastIndex) append('\n')
        }

        if (inCodeBlock && codeBuffer.isNotEmpty()) {
            withStyle(SpanStyle(
                fontFamily = JetBrainsMonoFontFamily,
                background = Color(0xFF1E1E1E).copy(alpha = 0.08f),
                fontSize = 13.sp
            )) {
                append(codeBuffer.toString().trimEnd())
            }
        }
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendFormattedLine(line: String) {
    var i = 0
    val sb = StringBuilder()

    fun flush() {
        if (sb.isNotEmpty()) {
            append(sb.toString())
            sb.clear()
        }
    }

    while (i < line.length) {
        if (line[i] == '`' && i + 1 < line.length && line[i + 1] != '`') {
            flush()
            val end = line.indexOf('`', i + 1)
            if (end > i) {
                withStyle(SpanStyle(
                    fontFamily = JetBrainsMonoFontFamily,
                    background = Color(0xFF1E1E1E).copy(alpha = 0.08f),
                    fontSize = 14.sp
                )) {
                    append(line.substring(i + 1, end))
                }
                i = end + 1
            } else {
                sb.append(line[i])
                i++
            }
        } else if (line[i] == '*' && i + 1 < line.length && line[i + 1] == '*') {
            flush()
            val end = line.indexOf("**", i + 2)
            if (end > i) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(line.substring(i + 2, end))
                }
                i = end + 2
            } else {
                sb.append(line[i])
                i++
            }
        } else if (line[i] == '*') {
            flush()
            val end = line.indexOf('*', i + 1)
            if (end > i) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(line.substring(i + 1, end))
                }
                i = end + 1
            } else {
                sb.append(line[i])
                i++
            }
        } else {
            sb.append(line[i])
            i++
        }
    }
    flush()
}

@Composable
fun ReasoningSection(
    reasoningContent: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
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
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (expanded) "إخفاء التفكير" else "إظهار التفكير",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = IBMPlexSansArabicFontFamily,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (expanded) "▲" else "▼",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (expanded) {
            Text(
                text = reasoningContent,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontFamily = IBMPlexSansArabicFontFamily,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}
