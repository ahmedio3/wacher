package com.aistudio.cinemios.fxtyr.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.aistudio.cinemios.fxtyr.auth.UserManager
import com.aistudio.cinemios.fxtyr.auth.UserProfile
import com.aistudio.cinemios.fxtyr.data.remote.ImgBBUploader
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.InputStream

@Composable
fun ProfileSetupDialog(
    initialProfile: UserProfile?,
    userId: String,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var name by remember { mutableStateOf(initialProfile?.name ?: "") }
    var username by remember { mutableStateOf(initialProfile?.username ?: "") }
    var bio by remember { mutableStateOf(initialProfile?.bio ?: "") }
    var base64Image by remember { mutableStateOf(initialProfile?.avatarBase64 ?: "") }
    var avatarUrl by remember { mutableStateOf(initialProfile?.avatarUrl ?: "") }
    var pickedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                
                // Downscale bitmap if it's too large
                val maxDim = 512
                val ratio: Float = Math.min(
                    maxDim.toFloat() / bitmap.width,
                    maxDim.toFloat() / bitmap.height
                )
                val width = Math.round(ratio * bitmap.width)
                val height = Math.round(ratio * bitmap.height)
                val newBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
                
                pickedBitmap = newBitmap

                val outputStream = ByteArrayOutputStream()
                newBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
                val byteArray = outputStream.toByteArray()
                base64Image = Base64.encodeToString(byteArray, Base64.DEFAULT)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "إعداد الملف الشخصي",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { launcher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarUrl.isNotEmpty()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = "Profile avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else if (base64Image.isNotEmpty()) {
                        val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)
                        val decodedImage = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        if (decodedImage != null) {
                            Image(
                                bitmap = decodedImage.asImageBitmap(),
                                contentDescription = "Profile avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Pick picture",
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("الاسم") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.filter { char -> char.isLetterOrDigit() || char == '_' } },
                    label = { Text("اسم المستخدم (يجب أن يكون مميزاً)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("الوصف (Bio)") },
                    placeholder = { Text("اكتب نبذة قصيرة عنك") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2,
                    maxLines = 4
                )
                
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
                
                Button(
                    onClick = {
                        if (name.isEmpty() || username.isEmpty() || (avatarUrl.isEmpty() && base64Image.isEmpty())) {
                            errorMessage = "يرجى تعبئة جميع البيانات واختيار صورة"
                            return@Button
                        }
                        isLoading = true
                        errorMessage = null
                        coroutineScope.launch {
                            var uploadFailed = false
                            if (pickedBitmap != null) {
                                val url = ImgBBUploader.uploadImage(pickedBitmap!!)
                                if (url != null) {
                                    avatarUrl = url
                                } else {
                                    uploadFailed = true
                                }
                            }
                            val newProfile = UserProfile(userId, name, username, base64Image, avatarUrl, bio)
                            val success = UserManager.saveProfile(userId, newProfile)
                            if (success) {
                                if (uploadFailed) {
                                    errorMessage = "تم حفظ البيانات بنجاح. فشل تحميل الصورة وتم الاحتفاظ بالصورة القديمة."
                                } else {
                                    onSuccess()
                                }
                            } else {
                                errorMessage = if (uploadFailed) {
                                    "فشل تحميل الصورة واسم المستخدم مأخوذ. جرب اسماً آخر."
                                } else {
                                    "اسم المستخدم هذا مأخوذ أو حدث خطأ. جرب اسماً آخر."
                                }
                            }
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    } else {
                        Text("حفظ التغييرات")
                    }
                }
            }
        }
    }
}
