package com.example.ai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.ToolExecutionDisplay
import com.example.ai.ToolExecutionStatus
import com.example.ui.theme.IBMPlexSansArabicFontFamily

@Composable
fun ToolCallCard(
    execution: ToolExecutionDisplay,
    modifier: Modifier = Modifier
) {
    val icon = when (execution.toolName) {
        "search_tmdb" -> Icons.Default.Search
        "get_watchlist", "add_to_watchlist" -> Icons.Default.Bookmark
        "get_downloads" -> Icons.Default.Search
        "get_tmdb_details" -> Icons.Default.Search
        else -> Icons.Default.Search
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

    val label = when (execution.toolName) {
        "search_tmdb" -> execution.summary.ifEmpty { "البحث في TMDB" }
        "get_watchlist" -> "عرض قائمة المشاهدة"
        "get_downloads" -> "عرض التحميلات"
        "add_to_watchlist" -> execution.summary.ifEmpty { "إضافة لقائمة المشاهدة" }
        "get_tmdb_details" -> execution.summary.ifEmpty { "جلب التفاصيل" }
        else -> execution.summary.ifEmpty { execution.toolName }
    }

    Row(
        modifier = modifier.padding(start = 20.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            fontFamily = IBMPlexSansArabicFontFamily
        )
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            imageVector = statusIcon,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = statusColor
        )
    }
}
