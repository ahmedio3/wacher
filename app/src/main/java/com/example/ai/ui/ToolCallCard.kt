package com.example.ai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    val isRunning = execution.status == ToolExecutionStatus.RUNNING

    Row(
        modifier = modifier.padding(start = 20.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isRunning) {
            Icon(
                imageVector = Icons.Default.HourglassTop,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        Text(
            text = execution.summary.ifEmpty {
                when (execution.toolName) {
                    "search_tmdb" -> "البحث في TMDB"
                    "get_watchlist" -> "عرض قائمة المشاهدة"
                    "get_downloads" -> "عرض التحميلات"
                    "add_to_watchlist" -> "إضافة لقائمة المشاهدة"
                    "get_tmdb_details" -> "جلب التفاصيل"
                    else -> execution.toolName
                }
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontFamily = IBMPlexSansArabicFontFamily
        )
    }
}
