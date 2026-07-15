package com.example.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.ChatManager
import com.example.auth.UserManager
import com.example.auth.UserProfile
import com.example.models.ChatMessage
import com.example.ui.components.MessageContextMenu
import com.example.ui.components.ProfileBottomSheet
import com.example.ui.components.SharedFloatingHeader
import com.example.ui.theme.IBMPlexSansArabicFontFamily
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GlobalChatScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    var user by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser) }
    var userProfile by remember { mutableStateOf<UserProfile?>(null) }
    var isProfileLoading by remember { mutableStateOf(user != null) }
    var messageText by remember { mutableStateOf("") }
    
    // For swipe-to-reply active state
    var replyToMessage by remember { mutableStateOf<ChatMessage?>(null) }

    // For long-press context menu
    var menuMsg by remember { mutableStateOf<ChatMessage?>(null) }
    // For editing an existing message
    var editingMsgId by remember { mutableStateOf<String?>(null) }

    // For profile bottom sheet
    var profileUser by remember { mutableStateOf<UserProfile?>(null) }
    var profileAvatar by remember { mutableStateOf("") }
    var showProfile by remember { mutableStateOf(false) }
    
    // Read chat live from Firebase RTDB. The Flow emits descending (newest first).
    val messages by remember { ChatManager.getMessages() }.collectAsState(initial = emptyList())

    val typingUsers by ChatManager.getTypingUsers().collectAsState(initial = emptyList())
    val listState = rememberLazyListState()

    LaunchedEffect(user) {
        if (user != null) {
            userProfile = UserManager.getProfile(user!!.uid)
            isProfileLoading = false
        }
    }
    
    LaunchedEffect(messageText) {
        val typingRef = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("typing/${user?.uid}")
        if (messageText.isNotBlank() && userProfile != null) {
            typingRef.setValue(userProfile!!.name)
        } else {
            if (user != null) typingRef.removeValue()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (user != null) {
                com.google.firebase.database.FirebaseDatabase.getInstance().getReference("typing/${user?.uid}").removeValue()
            }
        }
    }

    // Scroll to bottom on first load; afterwards smart auto-scroll (see LaunchedEffect below).
    var hasScrolledOnce by remember { mutableStateOf(false) }
    var justSent by remember { mutableStateOf(false) }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && !hasScrolledOnce) {
            listState.scrollToItem(0)
            hasScrolledOnce = true
            return@LaunchedEffect
        }
        // reverseLayout = true => index 0 is the bottom. "Near bottom" = small firstVisibleItemIndex.
        val nearBottom = listState.firstVisibleItemIndex <= 5
        if (justSent || nearBottom) {
            listState.scrollToItem(0)
        }
        justSent = false
    }

    Scaffold(
        topBar = { },
        bottomBar = {
            if (isProfileLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else if (user != null && userProfile != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    val othersTyping = typingUsers.filter { it != userProfile?.name }
                    if (othersTyping.isNotEmpty()) {
                        val text = if (othersTyping.size == 1) "${othersTyping[0]} يكتب..." else "${othersTyping.joinToString("، ")} يكتبون..."
                        Text(
                            text = text,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
                        )
                    }
                    
                    // Reply Box Banner right above the input field
                    AnimatedVisibility(visible = replyToMessage != null) {
                        replyToMessage?.let { msgRply ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 6.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Reply, 
                                    contentDescription = null, 
                                    tint = MaterialTheme.colorScheme.primary, 
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = msgRply.username, 
                                        color = MaterialTheme.colorScheme.primary, 
                                        fontSize = 13.sp, 
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = msgRply.text, 
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, 
                                        fontSize = 12.sp, 
                                        maxLines = 1, 
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { replyToMessage = null }, 
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close, 
                                        contentDescription = "إلغاء", 
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Polished input bar with dynamic shape (pill for 1 line, rounded rect for multi-line)
                    val lineCount = messageText.lines().count().coerceAtMost(4).coerceAtLeast(1)
                    val inputCorner by animateDpAsState(
                        if (lineCount <= 1) 50.dp else 20.dp,
                        label = "inputCorner"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 2.dp)
                            .shadow(2.dp, RoundedCornerShape(inputCorner))
                            .clip(RoundedCornerShape(inputCorner))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (messageText.isNotBlank()) {
                                    if (editingMsgId != null) {
                                        ChatManager.updateMessage(editingMsgId!!, messageText.trim())
                                        editingMsgId = null
                                        messageText = ""
                                        replyToMessage = null
                                    } else {
                                        val clientId =
                                            (user?.uid ?: "me") + "-" + System.currentTimeMillis()
                                        val msg = ChatMessage(
                                            id = clientId,
                                            userId = user!!.uid,
                                            username = userProfile!!.name,
                                            text = messageText.trim(),
                                            timestamp = System.currentTimeMillis(),
                                            avatarBase64 = userProfile!!.avatarBase64,
                                            repliedToId = replyToMessage?.id ?: "",
                                            repliedToName = replyToMessage?.username ?: "",
                                            repliedToText = replyToMessage?.text ?: ""
                                        )
                                        ChatManager.sendMessage(msg)
                                        justSent = true
                                        messageText = ""
                                        replyToMessage = null
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (messageText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "إرسال",
                                tint = if (messageText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        
                        BasicTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                fontFamily = IBMPlexSansArabicFontFamily
                            ),
                            maxLines = 4,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { innerTextField ->
                                if (messageText.isEmpty()) {
                                    Text(
                                        text = "مراسلة...", 
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), 
                                        fontSize = 15.sp,
                                        fontFamily = IBMPlexSansArabicFontFamily
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 30.dp).padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("يجب تسجيل الدخول من الإعدادات لاستخدام الدردشة", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
            }
        },
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(top = 90.dp, bottom = 16.dp, start = 16.dp, end = 16.dp)
        ) {
            itemsIndexed(messages, key = { _, msg -> msg.id }) { index, msg ->
                val isMe = msg.userId == user?.uid
                
                // Reversed indexes mapping for correct continuous bubble spacing calculation
                val olderMsg = if (index < messages.size - 1) messages[index + 1] else null
                val newerMsg = if (index > 0) messages[index - 1] else null
                
                val isFirstFromUser = olderMsg?.userId != msg.userId
                val isLastFromUser = newerMsg?.userId != msg.userId
                
                val cornerLarge = 16.dp
                val cornerSmall = 4.dp
                val cornerTail = 0.dp
                
                val topStart = if (!isMe) (if (isFirstFromUser) cornerLarge else cornerSmall) else cornerLarge
                val bottomStart = if (!isMe) (if (isLastFromUser) cornerTail else cornerSmall) else cornerLarge

                val topEnd = if (isMe) (if (isFirstFromUser) cornerLarge else cornerSmall) else cornerLarge
                val bottomEnd = if (isMe) (if (isLastFromUser) cornerTail else cornerSmall) else cornerLarge

                val offsetX = remember { Animatable(0f) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = if (isLastFromUser) 12.dp else 2.dp)
                ) {
                    // Revealable Swipe-Reply Icon behind the text bubble
                    if (offsetX.value < 0f) {
                        val revealAlpha = (offsetX.value / -75f).coerceIn(0f, 1f)
                        val revealScale = (offsetX.value / -75f).coerceIn(0.6f, 1.2f)
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Reply,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = revealAlpha),
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer {
                                        scaleX = revealScale
                                        scaleY = revealScale
                                    }
                            )
                        }
                    }

                    // Row layout with sliding behavior.
                    // LTR-localized offset: prevents RTL mirroring by Modifier.offset, so
                    // a left drag always translates the bubble left on screen.
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                                .pointerInput(msg.id) {
                                    detectHorizontalDragGestures(
                                        onDragEnd = {
                                            if (offsetX.value < -75f) {
                                                replyToMessage = msg
                                            }
                                            coroutineScope.launch {
                                                offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                            }
                                        },
                                        onDragCancel = {
                                            coroutineScope.launch {
                                                offsetX.animateTo(0f)
                                            }
                                        }
                                    ) { change, dragAmount ->
                                        change.consume()
                                        coroutineScope.launch {
                                            val targetOffset = (offsetX.value + dragAmount).coerceIn(-120f, 0f)
                                            offsetX.snapTo(targetOffset)
                                        }
                                    }
                                },
                            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column(
                                horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
                                modifier = Modifier.fillMaxWidth(0.85f)
                            ) {
                                if (!isMe && isFirstFromUser) {
                                    Text(
                                        text = msg.username,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                                    )
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart))
                                        .background(if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .combinedClickable(
                                onClick = { menuMsg = msg },
                                onLongClick = { menuMsg = msg }
                            )
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Column {
                                        // Reply preview nested container inside the bubble
                                        if (msg.repliedToId.isNotEmpty()) {
                                            Row(
                                                modifier = Modifier
                                                    .padding(bottom = 6.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background((if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.15f))
                                                    .padding(8.dp)
                                                    .fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(3.dp)
                                                        .height(30.dp)
                                                        .background(if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(
                                                        text = msg.repliedToName, 
                                                        fontSize = 11.sp, 
                                                        fontWeight = FontWeight.Bold, 
                                                        color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(
                                                        text = msg.repliedToText, 
                                                        fontSize = 11.sp, 
                                                        color = (if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.8f), 
                                                        maxLines = 1, 
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }

                                        Text(
                                            text = msg.text,
                                            color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 15.sp,
                                            lineHeight = 20.sp
                                        )
                                    }
                                }
                            }

                            if (!isMe && isLastFromUser) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable {
                                    coroutineScope.launch {
                                        profileUser = UserManager.getProfile(msg.userId)
                                        profileAvatar = msg.avatarBase64
                                        showProfile = true
                                    }
                                }) {
                                    if (msg.avatarBase64.isNotEmpty()) {
                                        val bitmap = try {
                                            val bytes = Base64.decode(msg.avatarBase64, Base64.DEFAULT)
                                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                                        } catch (e: Exception) { null }
                                        
                                        if (bitmap != null) {
                                            Image(
                                                bitmap = bitmap, 
                                                contentDescription = null, 
                                                contentScale = ContentScale.Crop, 
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            } else if (!isMe) {
                                Spacer(modifier = Modifier.width(40.dp))
                            }
                        }
                    }

                    // Long-press context menu for this message, anchored to the bubble's outer edge
                    // so it opens on the right for own messages (isMe) and left for others (RTL).
                    Box(modifier = Modifier.align(if (isMe) Alignment.CenterEnd else Alignment.CenterStart)) {
                        MessageContextMenu(
                            expanded = menuMsg?.id == msg.id,
                            onDismiss = { menuMsg = null },
                            canEdit = msg.userId == user?.uid,
                            canDelete = msg.userId == user?.uid,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(msg.text))
                                menuMsg = null
                            },
                            onReply = {
                                replyToMessage = msg
                                menuMsg = null
                            },
                            onEdit = {
                                messageText = msg.text
                                editingMsgId = msg.id
                                menuMsg = null
                            },
                            onDelete = {
                                ChatManager.deleteMessage(msg.id)
                                menuMsg = null
                            }
                        )
                    }
                }
            }

        }

            // Top fade overlay: opaque at the status bar, fading to transparent over the messages.
            // Pointer-transparent (no clickable) so scrolling passes through.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .align(Alignment.TopCenter)
                    .zIndex(1f)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.background.copy(alpha = 0f)
                            )
                        )
                    )
            )

            // Floating header (above the fade): elevated circular back button + white pill title.
            SharedFloatingHeader(
                title = "General Chat",
                onBackClick = onBackClick,
                modifier = Modifier.fillMaxWidth().zIndex(2f)
            )
        }

        // Profile bottom sheet
        ProfileBottomSheet(
            visible = showProfile,
            onDismiss = { showProfile = false },
            userProfile = profileUser,
            avatarBase64 = profileAvatar
        )
    }
}
