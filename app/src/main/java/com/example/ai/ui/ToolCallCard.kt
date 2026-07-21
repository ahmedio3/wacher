package com.example.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.ToolExecutionDisplay
import com.example.ai.ToolExecutionStatus

@Composable
fun ToolCallCard(
    execution: ToolExecutionDisplay,
    modifier: Modifier = Modifier
) {
    val icon = when (execution.toolName) {
        "search_tmdb" -> Icons.Default.Search
        "get_watchlist" -> Icons.Default.Bookmark
        "get_downloads" -> Icons.Default.Download
        "get_activity_log" -> Icons.Default.List
        "add_to_watchlist" -> Icons.Default.Bookmark
        "get_tmdb_details" -> Icons.Default.Info
        "download_content" -> Icons.Default.Download
        else -> Icons.Default.Info
    }

    val statusIcon = when (execution.status) {
        ToolExecutionStatus.RUNNING -> Icons.Default.HourglassTop
        ToolExecutionStatus.SUCCESS -> Icons.Default.CheckCircle
        ToolExecutionStatus.ERROR -> Icons.Default.Error
    }

    val statusColor = when (execution.status) {
        ToolExecutionStatus.RUNNING -> Color(0xFF2196F3)
        ToolExecutionStatus.SUCCESS -> Color(0xFF34C759)
        ToolExecutionStatus.ERROR -> Color(0xFFFF3B30)
    }

    val toolDisplayName = when (execution.toolName) {
        "search_tmdb" -> "البحث في TMDB"
        "get_watchlist" -> "عرض قائمة المشاهدة"
        "get_downloads" -> "عرض التحميلات"
        "get_activity_log" -> "عرض سجل النشاطات"
        "add_to_watchlist" -> "إضافة إلى المفضلة"
        "get_tmdb_details" -> "تفاصيل المحتوى"
        "download_content" -> "تحميل محتوى"
        else -> execution.toolName
    }

    Row(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = toolDisplayName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (execution.summary.isNotEmpty()) {
                Text(
                    text = execution.summary,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        Icon(
            imageVector = statusIcon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = statusColor
        )
    }
}
