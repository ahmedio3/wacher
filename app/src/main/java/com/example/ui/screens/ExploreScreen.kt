package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.example.ui.components.bouncyOverscroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Search
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
import com.example.chat.UserChat
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    onNavigateToSubtitleDownloads: () -> Unit = {},
    onNavigateToWatchlist: () -> Unit = {},
    onNavigateToChat: (String, Boolean) -> Unit = { _, _ -> },
    onNavigateToAiChat: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("اكتشف", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .bouncyOverscroll()
                .padding(start = 12.dp, end = 12.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "الأدوات",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )

            // AI Chat
            ExploreActionRow(
                title = "الذكاء الاصطناعي",
                subtitle = "مساعد ذكي للبحث عن الأفلام والمسلسلات وإدارة محتواك",
                icon = {
                    Box(
                        modifier = Modifier.size(54.dp).clip(CircleShape).background(Color(0xFF6C5CE7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.White)
                    }
                },
                onClick = onNavigateToAiChat
            )

            // Subtitle Downloads
            ExploreActionRow(
                title = "الترجمات المحملة",
                subtitle = "تصدير وحذف الترجمات التي قمت بتحميلها",
                icon = {
                    Box(
                        modifier = Modifier.size(54.dp).clip(CircleShape).background(Color(0xFF9C27B0)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ClosedCaption, contentDescription = null, tint = Color.White)
                    }
                },
                onClick = onNavigateToSubtitleDownloads
            )

            // Watchlist Card
            ExploreActionRow(
                title = "قائمتي",
                subtitle = "أفلام ومسلسلات محفوظة لمشاهدتها لاحقاً",
                icon = {
                    Box(
                        modifier = Modifier.size(54.dp).clip(CircleShape).background(Color(0xFFB85C38)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Bookmark, contentDescription = null, tint = Color.White)
                    }
                },
                onClick = onNavigateToWatchlist
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (currentUserId.isNotEmpty()) {
                Text(
                    text = "المحادثات",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                )

                ChatRoomList(
                    userId = currentUserId,
                    onChatClick = { chat ->
                        if (chat.roomType == "public") {
                            onNavigateToChat(chat.roomId, true)
                        } else {
                            onNavigateToChat(chat.roomId, false)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

}

@Composable
fun ExploreActionRow(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
