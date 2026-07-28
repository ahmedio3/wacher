package com.example.ui.screens

import coil.compose.AsyncImage
import androidx.compose.foundation.background
import com.example.ui.components.bouncyOverscroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.MovieViewModel

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.example.auth.AuthManager
import com.example.auth.UserManager
import com.example.auth.UserProfile
import com.example.auth.ActivityLogManager
import com.example.ui.theme.AccentColors
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MovieViewModel,
    onNavigateToHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isArabicPosters by viewModel.isArabicPosters.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var user by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser) }
    var userProfile by remember { mutableStateOf<UserProfile?>(null) }
    var isProfileLoading by remember { mutableStateOf(true) }
    var emailInput by remember { mutableStateOf("") }
    var passInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showBroadcastDialog by remember { mutableStateOf(false) }
    var showCustomSectionDialog by remember { mutableStateOf(false) }
    val isAdmin = user?.email == "ahmedsarri123@gmail.com"

    // Fetch user profile on user change
    LaunchedEffect(user) {
        isProfileLoading = true
        if (user != null) {
            val prof = UserManager.getProfile(user!!.uid)
            userProfile = prof
            isProfileLoading = false
            if (prof == null || prof.name.isEmpty() || prof.username.isEmpty()) {
                showProfileDialog = true
            }
        } else {
            userProfile = null
            isProfileLoading = false
        }
    }

    if (showProfileDialog && user != null) {
        ProfileSetupDialog(
            initialProfile = userProfile,
            userId = user!!.uid,
            onDismiss = { showProfileDialog = false },
            onSuccess = { 
                showProfileDialog = false 
                coroutineScope.launch {
                    userProfile = UserManager.getProfile(user!!.uid)
                }
            }
        )
    }

    if (showCustomSectionDialog) {
        CustomSectionDialog(
            viewModel = viewModel,
            onDismiss = { showCustomSectionDialog = false }
        )
    }

    if (showBroadcastDialog) {
        var bdTitle by remember { mutableStateOf("") }
        var bdText by remember { mutableStateOf("") }
        var isSending by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!isSending) showBroadcastDialog = false },
            title = { Text("إرسال إشعار للجميع", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = bdTitle, onValueChange = { bdTitle = it }, label = { Text("العنوان") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = bdText, onValueChange = { bdText = it }, label = { Text("النص") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (bdTitle.isNotBlank() && bdText.isNotBlank()) {
                            isSending = true
                            val ref = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("broadcasts").push()
                            val data = mapOf(
                                "title" to bdTitle.trim(),
                                "text" to bdText.trim(),
                                "timestamp" to System.currentTimeMillis()
                            )
                            ref.setValue(data).addOnCompleteListener {
                                showBroadcastDialog = false
                            }
                        }
                    },
                    enabled = !isSending && bdTitle.isNotBlank() && bdText.isNotBlank()
                ) {
                    Text(if (isSending) "جاري الإرسال..." else "إرسال")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBroadcastDialog = false }, enabled = !isSending) { Text("إلغاء") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الإعدادات", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState())
                .bouncyOverscroll(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Profile Card (Firebase Auth)
            ProfileSection(
                user = user,
                userProfile = userProfile,
                isProfileLoading = isProfileLoading,
                onEditClick = { showProfileDialog = true },
                onLogoutClick = {
                    FirebaseAuth.getInstance().signOut()
                    user = null
                },
                emailInput = emailInput,
                onEmailChange = { emailInput = it },
                passInput = passInput,
                onPassChange = { passInput = it },
                isLoading = isLoading,
                onSignInClick = {
                    isLoading = true
                    FirebaseAuth.getInstance().signInWithEmailAndPassword(emailInput, passInput)
                        .addOnCompleteListener { task ->
                            isLoading = false
                            if (task.isSuccessful) {
                                val u = FirebaseAuth.getInstance().currentUser
                                val uid = u?.uid
                                if (uid != null) {
                                    coroutineScope.launch {
                                        ActivityLogManager.addLog(uid, "LOGIN", u.email ?: emailInput)
                                    }
                                }
                                user = u
                            } else {
                                FirebaseAuth.getInstance().createUserWithEmailAndPassword(emailInput, passInput)
                                    .addOnCompleteListener { task2 ->
                                        if (task2.isSuccessful) {
                                            val u = FirebaseAuth.getInstance().currentUser
                                            val uid = u?.uid
                                            if (uid != null) {
                                                coroutineScope.launch {
                                                    ActivityLogManager.addLog(uid, "ACCOUNT_CREATED", u.email ?: emailInput)
                                                }
                                            }
                                            user = u
                                        }
                                    }
                            }
                        }
                },
                onGoogleSignInClick = {
                    isLoading = true
                    coroutineScope.launch {
                        val resultMsg = AuthManager.signInWithGoogle(context)
                        if (resultMsg == "success") {
                            val u = FirebaseAuth.getInstance().currentUser
                            val uid = u?.uid
                            if (uid != null) {
                                ActivityLogManager.addLog(uid, "LOGIN", u.displayName ?: u.email ?: "Google")
                            }
                            user = u
                        } else {
                            android.widget.Toast.makeText(context, "خطأ: $resultMsg", android.widget.Toast.LENGTH_LONG).show()
                        }
                        isLoading = false
                    }
                }
            )

            // Activity History button
            Button(
                onClick = onNavigateToHistory,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(imageVector = Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("سجل النشاطات", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            if (isAdmin) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth().clickable { showCustomSectionDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("إدارة قسم \"بتاع\" بالرئيسية", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth().clickable { showBroadcastDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFE91E63).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Campaign, contentDescription = null, tint = Color(0xFFE91E63))
                            }
                            Text("إرسال إشعار للجميع", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFE91E63))
                        }
                    }
                }
            }

            // Config Card: Display & Languages
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = "لغة عرض البوسترات والعناوين",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "وصف القصة وترجمة المحتوى تظل عربية دائمًا",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }

                    // iOS Premium Segmented Control / Capsule Picker
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Arabic Select Capsule
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isArabicPosters) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { viewModel.setArabicPosters(true) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "العربية (Arabic)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (isArabicPosters) Color.White else MaterialTheme.colorScheme.onBackground
                            )
                        }

                        // English Select Capsule
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (!isArabicPosters) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { viewModel.setArabicPosters(false) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "الإنجليزية (English)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (!isArabicPosters) Color.White else MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    // Dark Mode Toggle
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setDarkMode(!isDarkMode) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.DarkMode,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = "الوضع الليلي",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "خلفية داكنة لتجربة مريحة للعين",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                )
                            }
                        }
                        Switch(
                            checked = isDarkMode,
                            onCheckedChange = { viewModel.setDarkMode(it) }
                        )
                    }
                }
            }

            // Storage & Cache Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Text("التخزين والذاكرة", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
                    }

                    // Cache size display
                    var cacheSizeText by remember { mutableStateOf("جاري الحساب...") }
                    LaunchedEffect(Unit) {
                        try {
                            val cacheDir = context.cacheDir
                            var size = 0L
                            cacheDir.walkTopDown().forEach { size += it.length() }
                            cacheSizeText = if (size > 1048576) String.format("%.1f MB", size / 1048576f) else String.format("%.1f KB", size / 1024f)
                        } catch (_: Exception) { cacheSizeText = "غير معروف" }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("حجم ذاكرة التخزين المؤقت", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                        Text(cacheSizeText, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    }

                    Button(
                        onClick = {
                            try {
                                context.cacheDir.deleteRecursively()
                                context.cacheDir.mkdirs()
                            } catch (_: Exception) {}
                            cacheSizeText = "0 KB"
                            android.widget.Toast.makeText(context, "تم مسح ذاكرة التخزين المؤقت", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("مسح ذاكرة التخزين المؤقت", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    // Downloads size
                    var dlSizeText by remember { mutableStateOf("جاري الحساب...") }
                    LaunchedEffect(Unit) {
                        try {
                            val dlDir = File(context.filesDir, "downloads")
                            var size = 0L
                            if (dlDir.exists()) dlDir.walkTopDown().forEach { size += it.length() }
                            dlSizeText = if (size > 1048576) String.format("%.1f MB", size / 1048576f) else String.format("%.1f KB", size / 1024f)
                        } catch (_: Exception) { dlSizeText = "غير معروف" }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("حجم التحميلات", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                        Text(dlSizeText, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }

            // Download Settings Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Text("إعدادات التحميل", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
                    }

                    var defaultQuality by remember { mutableStateOf(context.getSharedPreferences("watchera_prefs", android.content.Context.MODE_PRIVATE).getString("default_quality", "1080p") ?: "1080p") }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("الجودة الافتراضية", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                        var expandQuality by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { expandQuality = true }) { Text(defaultQuality, fontWeight = FontWeight.Bold) }
                            DropdownMenu(expanded = expandQuality, onDismissRequest = { expandQuality = false }) {
                                listOf("480p", "720p", "1080p", "2160p").forEach { q ->
                                    DropdownMenuItem(text = { Text(q) }, onClick = {
                                        defaultQuality = q
                                        context.getSharedPreferences("watchera_prefs", android.content.Context.MODE_PRIVATE).edit().putString("default_quality", q).apply()
                                        expandQuality = false
                                    })
                                }
                            }
                        }
                    }

                    var wifiOnly by remember { mutableStateOf(context.getSharedPreferences("watchera_prefs", android.content.Context.MODE_PRIVATE).getBoolean("wifi_only_download", false)) }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("التحميل عبر WiFi فقط", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
                            Text("يمنع التحميل عبر بيانات الجوال", fontSize = 10.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                        }
                        Switch(checked = wifiOnly, onCheckedChange = {
                            wifiOnly = it
                            context.getSharedPreferences("watchera_prefs", android.content.Context.MODE_PRIVATE).edit().putBoolean("wifi_only_download", it).apply()
                        })
                    }

                    var dataSaver by remember { mutableStateOf(context.getSharedPreferences("watchera_prefs", android.content.Context.MODE_PRIVATE).getBoolean("data_saver", false)) }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("توفير البيانات", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
                            Text("تحميل صور بجودة منخفضة", fontSize = 10.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                        }
                        Switch(checked = dataSaver, onCheckedChange = {
                            dataSaver = it
                            context.getSharedPreferences("watchera_prefs", android.content.Context.MODE_PRIVATE).edit().putBoolean("data_saver", it).apply()
                        })
                    }
                }
            }

            // Display Settings Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.DisplaySettings, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Text("إعدادات العرض", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
                    }

                    var autoPlay by remember { mutableStateOf(context.getSharedPreferences("watchera_prefs", android.content.Context.MODE_PRIVATE).getBoolean("auto_play_trailers", false)) }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("تشغيل تلقائي للعروض", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
                            Text("عرض دعاية الفيلم تلقائياً", fontSize = 10.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                        }
                        Switch(checked = autoPlay, onCheckedChange = {
                            autoPlay = it
                            context.getSharedPreferences("watchera_prefs", android.content.Context.MODE_PRIVATE).edit().putBoolean("auto_play_trailers", it).apply()
                        })
                    }

                    // Font scale
                    val prefs = context.getSharedPreferences("watchera_prefs", android.content.Context.MODE_PRIVATE)
                    var fontSizeScale by remember { mutableIntStateOf(prefs.getInt("font_scale", 0)) }
                    val scaleLabels = listOf("صغير", "وسط", "كبير", "كبير جداً")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("حجم الخط", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            scaleLabels.forEachIndexed { idx, label ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (fontSizeScale == idx) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            fontSizeScale = idx
                                            prefs.edit().putInt("font_scale", idx).apply()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (fontSizeScale == idx) Color.White else MaterialTheme.colorScheme.onBackground)
                                }
                            }
                        }
                    }

                    // Accent color picker
                    val currentAccent = prefs.getString("accent_color", "") ?: ""
                    Text("لون التطبيق", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        items(AccentColors.toList()) { (name, color) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(color)
                                        .clickable { prefs.edit().putString("accent_color", name).apply() }
                                        .then(
                                            if (currentAccent == name) Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, androidx.compose.foundation.shape.CircleShape)
                                            else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (currentAccent == name) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                                Text(name, fontSize = 8.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            }

            // About Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Text("حول التطبيق", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("الإصدار", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                        Text("1.0.0 (build 1)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("المطور", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                        Text("فريق ووتشيرا", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    }
                    Text(
                        text = "ووتشيرا هو تطبيق لمشاهدة وتحميل الأفلام والمسلسلات مع دعم الذكاء الاصطناعي والترجمة.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        lineHeight = 18.sp
                    )
                    // Rate app button
                    Button(
                        onClick = {
                            try {
                                val uri = android.net.Uri.parse("market://details?id=${context.packageName}")
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
                            } catch (_: Exception) {
                                val uri = android.net.Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700).copy(alpha = 0.2f), contentColor = Color(0xFFB8860B))
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("تقييم التطبيق", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    // Share app button
                    Button(
                        onClick = {
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, "جرب ووتشيرا لمشاهدة وتحميل الأفلام والمسلسلات!\nhttps://watchera.com")
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "مشاركة ووتشيرا"))
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("مشاركة التطبيق", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    // Feedback button
                    Button(
                        onClick = {
                            val emailIntent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                                data = android.net.Uri.parse("mailto:watchera@example.com")
                                putExtra(android.content.Intent.EXTRA_SUBJECT, "اقتراح بخصوص ووتشيرا")
                            }
                            context.startActivity(emailIntent)
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Feedback, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("إرسال اقتراح", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // Player Settings Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Videocam, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Text("إعدادات المشغل", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
                    }
                    val playerPrefs = context.getSharedPreferences("player_prefs", android.content.Context.MODE_PRIVATE)
                    var backgroundPlayback by remember { mutableStateOf(playerPrefs.getBoolean("background_playback", false)) }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("التشغيل في الخلفية", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
                            Text("استمرار الصوت عند تصغير التطبيق", fontSize = 10.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                        }
                        Switch(checked = backgroundPlayback, onCheckedChange = {
                            backgroundPlayback = it
                            playerPrefs.edit().putBoolean("background_playback", it).apply()
                        })
                    }
                    var pipMode by remember { mutableStateOf(playerPrefs.getBoolean("pip_mode", false)) }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("صورة داخل صورة (PiP)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
                            Text("مشاهدة مصغرة أثناء استخدام تطبيقات أخرى", fontSize = 10.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                        }
                        Switch(checked = pipMode, onCheckedChange = {
                            pipMode = it
                            playerPrefs.edit().putBoolean("pip_mode", it).apply()
                        })
                    }
                }
            }

            Spacer(modifier = Modifier.height(100.dp))

        }
    }
}

@Composable
fun ProfileSection(
    user: FirebaseUser?,
    userProfile: UserProfile?,
    isProfileLoading: Boolean = false,
    onEditClick: () -> Unit,
    onLogoutClick: () -> Unit,
    emailInput: String,
    onEmailChange: (String) -> Unit,
    passInput: String,
    onPassChange: (String) -> Unit,
    isLoading: Boolean,
    onSignInClick: () -> Unit,
    onGoogleSignInClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (user != null) {
                if (isProfileLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .width(120.dp)
                                    .height(18.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            )
                            Box(
                                modifier = Modifier
                                    .width(80.dp)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            )
                        }
                    }
                } else {
                // Signed In State — horizontal header (avatar + name/username)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (userProfile?.avatarUrl?.isNotEmpty() == true) {
                            AsyncImage(
                                model = userProfile!!.avatarUrl,
                                contentDescription = "Profile avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        }
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (userProfile?.name?.isNotEmpty() == true) userProfile.name else "بك",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (userProfile?.username?.isNotEmpty() == true) {
                            Text(
                                text = "@${userProfile.username}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                // Bio (read-only display)
                if (userProfile?.bio?.isNotEmpty() == true) {
                    Text(
                        text = userProfile.bio,
                        fontSize = 13.sp,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onEditClick,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "تعديل", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تعديل")
                    }

                    Button(
                        onClick = onLogoutClick,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("تسجيل الخروج")
                    }
                }
                }
            } else {
                // Sign Out State (Login Form)
                Text(
                    text = "قم بتسجيل الدخول الان",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )

                OutlinedTextField(
                    value = emailInput,
                    onValueChange = onEmailChange,
                    label = { Text("البريد الإلكتروني") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = passInput,
                    onValueChange = onPassChange,
                    label = { Text("كلمة المرور") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onSignInClick,
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading && emailInput.isNotEmpty() && passInput.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("دخول / حساب جديد")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.surfaceVariant)

                Button(
                    onClick = onGoogleSignInClick,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("الاستمرار بواسطة Google", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
