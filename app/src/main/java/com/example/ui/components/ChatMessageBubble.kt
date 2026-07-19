package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.chat.Message
import com.example.chat.ReplyTo
import kotlin.math.abs

private const val SWIPE_THRESHOLD = 100f

@Composable
fun ChatMessageBubble(
    message: Message,
    isMine: Boolean,
    isFirstInGroup: Boolean,
    isLastInGroup: Boolean,
    showAvatar: Boolean,
    onReply: (Message) -> Unit,
    onImageClick: (String) -> Unit,
    onUserClick: (String, String, String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    var swipeOffset by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 1.dp)
            .graphicsLayer { translationX = swipeOffset }
            .pointerInput(message.id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startTime = System.nanoTime()
                    var isSwiping = false
                    var finalDx = 0f
                    var finalDy = 0f
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        val dx = change.position.x - down.position.x
                        val dy = abs(change.position.y - down.position.y)
                        val elapsed = System.nanoTime() - startTime
                        finalDx = dx
                        finalDy = dy
                        if (dx > 0 && dx > dy * 2f && elapsed > 250_000_000L) {
                            isSwiping = true
                        }
                        if (isSwiping) {
                            if (dx > 0) {
                                swipeOffset = dx.coerceAtMost(SWIPE_THRESHOLD * 1.5f)
                                change.consume()
                            } else {
                                isSwiping = false
                                swipeOffset = 0f
                            }
                        } else {
                            swipeOffset = 0f
                        }
                    } while (event.changes.any { it.pressed })
                    if (swipeOffset > SWIPE_THRESHOLD && finalDx > finalDy * 2f) {
                        onReply(message)
                    }
                    swipeOffset = 0f
                }
            }
    ) {
        if (swipeOffset > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Reply,
                    contentDescription = "رد",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isMine) Modifier.padding(start = 20.dp, end = 4.dp)
                    else Modifier.padding(start = 4.dp, end = 20.dp)
                ),
            horizontalArrangement = if (isMine) Arrangement.Start else Arrangement.End,
            verticalAlignment = Alignment.Bottom
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = false),
                horizontalAlignment = if (isMine) Alignment.Start else Alignment.End
            ) {
                if (isFirstInGroup && !isMine) {
                    Text(
                        text = message.senderName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 2.dp, start = 4.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = if (isFirstInGroup || !isMine) 18.dp else 12.dp,
                                topEnd = if (isFirstInGroup || isMine) 18.dp else 12.dp,
                                bottomStart = if (isLastInGroup || !isMine) 18.dp else 12.dp,
                                bottomEnd = if (isLastInGroup || isMine) 18.dp else 12.dp
                            )
                        )
                        .background(
                            if (isMine) Color(0xFF8C6D4F)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(10.dp)
                ) {
                    if (message.replyTo != null) {
                        ReplyQuote(message.replyTo, isMine)
                    }

                    if (message.type == "image" && message.imageUrl.isNotEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(message.imageUrl)
                                .crossfade(200)
                                .build(),
                            contentDescription = "صورة",
                            modifier = Modifier
                                .widthIn(max = 260.dp)
                                .heightIn(max = 260.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onImageClick(message.imageUrl) },
                            contentScale = ContentScale.Fit
                        )
                    }

                    if (message.text.isNotEmpty()) {
                        Text(
                            text = message.text,
                            fontSize = 15.sp,
                            color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (isMine) {
                Spacer(modifier = Modifier.width(4.dp))
                Box(modifier = Modifier.size(28.dp))
            }

            if (!isMine) {
                if (showAvatar) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onUserClick(message.senderId, message.senderName, message.senderAvatarUrl) }
                    ) {
                        if (message.senderAvatarUrl.isNotEmpty()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(message.senderAvatarUrl)
                                    .crossfade(200)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                } else {
                    Spacer(modifier = Modifier.width(38.dp))
                }
            }
        }
    }
}

@Composable
private fun ReplyQuote(replyTo: ReplyTo, isMine: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isMine) Color.White.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            )
            .padding(6.dp)
    ) {
        Column {
            Text(
                text = replyTo.senderName,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isMine) Color.White else MaterialTheme.colorScheme.primary
            )
            Text(
                text = replyTo.text,
                fontSize = 12.sp,
                color = if (isMine) Color.White.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 2
            )
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
}
