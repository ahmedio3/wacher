package com.example.ui.screens

import coil.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.auth.AuthManager
import com.example.auth.UserManager
import com.example.auth.UserProfile
import com.example.auth.ActivityLogManager
import com.example.worker.SubtitleAutoWorker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MovieViewModel,
    onNavigateToHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isArabicPosters by viewModel.isArabicPosters.collectAsState()
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
                .verticalScroll(rememberScrollState()),
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
                }
            }

            // Auto Subtitle Card
            val prefs = context.getSharedPreferences("watchera_prefs", android.content.Context.MODE_PRIVATE)
            var autoSubEnabled by remember { mutableStateOf(prefs.getBoolean("auto_subtitle_enabled", true)) }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "تنزيل الترجمة تلقائياً",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "يبحث عن ترجمة عربية تلقائياً فور اكتمال تحميل أي حلقة.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                maxLines = 3
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = autoSubEnabled,
                            onCheckedChange = {
                                autoSubEnabled = it
                                prefs.edit().putBoolean("auto_subtitle_enabled", it).apply()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    var isBackfilling by remember { mutableStateOf(false) }
                    OutlinedButton(
                        onClick = {
                            isBackfilling = true
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    val db = com.example.data.local.MovieDatabase.getDatabase(context)
                                    val allDownloads = db.movieDao.getDownloads().first()
                                    val completedDownloads = allDownloads.filter { it.status == "completed" && it.mediaType == "tv" }
                                    val allSubs = db.movieDao.getSubtitleDownloads().first()

                                    val needsSub = completedDownloads.filter { dl ->
                                        val autoId = "${dl.mediaId}_s${dl.season}e${dl.episode}_ar"
                                        allSubs.none { it.id == autoId }
                                    }

                                    var delay = 0L
                                    for (dl in needsSub) {
                                        val isTv = dl.mediaType == "tv"
                                        val tmdbIdStr = if (isTv) dl.mediaId.substringBefore("-s") else dl.mediaId
                                        val seasonNum = if (isTv) dl.mediaId.substringAfter("-s").substringBefore("-e").toIntOrNull() ?: 1 else 0
                                        val episodeNum = if (isTv) dl.mediaId.substringAfter("-e").toIntOrNull() ?: 1 else 0

                                        val workData = workDataOf(
                                            "tmdbId" to tmdbIdStr,
                                            "downloadId" to dl.id,
                                            "title" to dl.title,
                                            "mediaType" to dl.mediaType,
                                            "season" to seasonNum,
                                            "episode" to episodeNum,
                                            "posterPath" to dl.posterPath
                                        )
                                        val workRequest = OneTimeWorkRequestBuilder<SubtitleAutoWorker>()
                                            .setInputData(workData)
                                            .setConstraints(
                                                Constraints.Builder()
                                                    .setRequiredNetworkType(NetworkType.CONNECTED)
                                                    .build()
                                            )
                                            .setBackoffCriteria(
                                                androidx.work.BackoffPolicy.EXPONENTIAL,
                                                30,
                                                java.util.concurrent.TimeUnit.SECONDS
                                            )
                                            .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
                                            .build()
                                        WorkManager.getInstance(context).enqueue(workRequest)
                                        delay += 2000
                                    }
                                } catch (_: Exception) { }
                                withContext(Dispatchers.Main) { isBackfilling = false }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBackfilling
                    ) {
                        if (isBackfilling) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("جارٍ التعقب...", fontSize = 13.sp)
                        } else {
                            Text("تعقب الحلقات القديمة بدون ترجمة", fontSize = 13.sp)
                        }
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
