package com.example.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.MovieViewModel

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.example.auth.AuthManager
import com.example.auth.UserManager
import com.example.auth.UserProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MovieViewModel,
    onBackClick: () -> Unit,
    onNavigateToWatchlist: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isArabicPosters by viewModel.isArabicPosters.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var user by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser) }
    var userProfile by remember { mutableStateOf<UserProfile?>(null) }
    var emailInput by remember { mutableStateOf("") }
    var passInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showBroadcastDialog by remember { mutableStateOf(false) }
    var showCustomSectionDialog by remember { mutableStateOf(false) }
    val isAdmin = user?.email == "ahmedsarri123@gmail.com"

    // Fetch user profile on user change
    LaunchedEffect(user) {
        if (user != null) {
            val prof = UserManager.getProfile(user!!.uid)
            userProfile = prof
            if (prof == null || prof.name.isEmpty() || prof.username.isEmpty()) {
                showProfileDialog = true
            }
        } else {
            userProfile = null
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
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
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
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Profile Card (Firebase Auth)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (user != null) {
                        // Signed In State
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (userProfile != null && userProfile!!.avatarBase64.isNotEmpty()) {
                                val bitmap = try {
                                    val imageBytes = Base64.decode(userProfile!!.avatarBase64, Base64.DEFAULT)
                                    BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)?.asImageBitmap()
                                } catch (e: Exception) { null }
                                
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = "Profile avatar",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                }
                            } else {
                                Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                            }
                        }
                        Text(
                            text = if (userProfile?.name?.isNotEmpty() == true) userProfile!!.name else (user?.displayName ?: user?.email ?: "بك"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (userProfile?.username?.isNotEmpty() == true) {
                            Text(
                                text = "@${userProfile!!.username}",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { showProfileDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "تعديل", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تعديل")
                            }
                            
                            Button(
                                onClick = { 
                                    FirebaseAuth.getInstance().signOut()
                                    user = null
                                },
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
                            onValueChange = { emailInput = it },
                            label = { Text("البريد الإلكتروني") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        
                        OutlinedTextField(
                            value = passInput,
                            onValueChange = { passInput = it },
                            label = { Text("كلمة المرور") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    isLoading = true
                                    FirebaseAuth.getInstance().signInWithEmailAndPassword(emailInput, passInput)
                                        .addOnCompleteListener { task ->
                                            isLoading = false
                                            if (task.isSuccessful) {
                                                user = FirebaseAuth.getInstance().currentUser
                                            } else {
                                                FirebaseAuth.getInstance().createUserWithEmailAndPassword(emailInput, passInput)
                                                    .addOnCompleteListener { task2 ->
                                                        if (task2.isSuccessful) {
                                                            user = FirebaseAuth.getInstance().currentUser
                                                        }
                                                    }
                                            }
                                        }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !isLoading && emailInput.isNotEmpty() && passInput.isNotEmpty(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("دخول / حساب جديد")
                            }
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                        
                        Button(
                            onClick = {
                                isLoading = true
                                coroutineScope.launch {
                                    val resultMsg = AuthManager.signInWithGoogle(context)
                                    if (resultMsg == "success") {
                                        user = FirebaseAuth.getInstance().currentUser
                                    } else {
                                        android.widget.Toast.makeText(context, "خطأ: $resultMsg", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                    isLoading = false
                                }
                            },
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
            
            // Watchlist Button
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().clickable { onNavigateToWatchlist() }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Text("قائمتي", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
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
        }
    }
}
