package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.example.ui.components.bouncyOverscroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Rocket
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.UserChat
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    onNavigateToAdultContent: (String) -> Unit = {},
    onNavigateToSubtitleDownloads: () -> Unit = {},
    onNavigateToWatchlist: () -> Unit = {},
    onNavigateToChat: (String, Boolean) -> Unit = { _, _ -> },
    onNavigateToAiChat: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("watchera_prefs", android.content.Context.MODE_PRIVATE)
    var unlocked by remember { mutableStateOf(prefs.getBoolean("unsafe_mode_unlocked", false)) }
    var showVerifySheet by remember { mutableStateOf(false) }
    var showPinSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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

            Spacer(modifier = Modifier.height(8.dp))

            // Unsafe mode button
            Button(
                onClick = {
                    if (unlocked) onNavigateToAdultContent("")
                    else showVerifySheet = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("فتح الوضع الغير آمن", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            // Genre Browsing Section
            Text(
                text = "تصفح حسب النوع",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )
            val genres = listOf("أكشن" to Icons.Default.Whatshot, "كوميديا" to Icons.Default.SentimentSatisfied, "دراما" to Icons.Default.TheaterComedy, "رعب" to Icons.Default.Dangerous, "خيال علمي" to Icons.Default.Rocket, "رومانسية" to Icons.Default.Favorite)
            LazyRow(
                modifier = Modifier.bouncyOverscroll(isVertical = false).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(genres) { (name, icon) ->
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { /* Navigate to genre filter */ }
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // New Releases section
            Text(
                text = "الإصدارات الجديدة",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )
            ExploreActionRow(
                title = "الأفلام الجديدة",
                subtitle = "أحدث الأفلام المضافة للمنصة",
                icon = {
                    Box(modifier = Modifier.size(54.dp).clip(CircleShape).background(Color(0xFFFF6B6B)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Movie, contentDescription = null, tint = Color.White)
                    }
                },
                onClick = { /* Navigate to new movies */ }
            )
            ExploreActionRow(
                title = "المسلسلات الجديدة",
                subtitle = "أحدث المسلسلات المتاحة للمشاهدة",
                icon = {
                    Box(modifier = Modifier.size(54.dp).clip(CircleShape).background(Color(0xFF4ECDC4)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.LiveTv, contentDescription = null, tint = Color.White)
                    }
                },
                onClick = { /* Navigate to new TV shows */ }
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

    if (showVerifySheet) {
        ModalBottomSheet(
            onDismissRequest = { showVerifySheet = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "ابعت صورة بتاعك",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "الطول: فوق 7 سنتي (اختياري)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Button(
                    onClick = {
                        showVerifySheet = false
                        showPinSheet = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("متابعة", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }

    if (showPinSheet) {
        var pin by remember { mutableStateOf("") }
        ModalBottomSheet(
            onDismissRequest = { showPinSheet = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4) pin = it },
                    label = { Text("أدخل رمز PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        if (pin == "2580") {
                            prefs.edit().putBoolean("unsafe_mode_unlocked", true).apply()
                            unlocked = true
                            showPinSheet = false
                            onNavigateToAdultContent("")
                        } else {
                            android.widget.Toast.makeText(context, "رمز PIN غير صحيح", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("تأكيد", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
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
