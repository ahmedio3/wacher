package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

enum class NotificationType { SUCCESS, ERROR, INFO }

data class NotificationData(
    val message: String,
    val type: NotificationType = NotificationType.INFO
)

@Composable
fun rememberNotificationState(): MutableState<NotificationData?> {
    return remember { mutableStateOf(null) }
}

@Composable
fun InAppNotificationHost(
    notification: NotificationData?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = notification != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(animationSpec = tween(200)),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(animationSpec = tween(200)),
        modifier = modifier
    ) {
        notification?.let { data ->
            LaunchedEffect(data) {
                delay(2000)
                onDismiss()
            }

            val (bgColor, icon) = when (data.type) {
                NotificationType.SUCCESS -> Color(0xFF2E7D32) to Icons.Default.CheckCircle
                NotificationType.ERROR -> Color(0xFFC62828) to Icons.Default.Error
                NotificationType.INFO -> Color(0xFF1565C0) to Icons.Default.Info
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = bgColor,
                shadowElevation = 6.dp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = data.message,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

fun notify(
    state: MutableState<NotificationData?>,
    message: String,
    type: NotificationType = NotificationType.INFO
) {
    state.value = NotificationData(message, type)
}
