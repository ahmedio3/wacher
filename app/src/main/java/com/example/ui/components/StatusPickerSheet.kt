package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusPickerSheet(
    currentDefault: String,
    onDismiss: () -> Unit,
    onStatusSelected: (String) -> Unit,
    onSetAsDefault: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "اختر حالة المشاهدة",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            StatusOption(
                label = "انوي مشاهدته",
                subtitle = "أضفته للقائمة ولم تبدأ بعد",
                icon = Icons.Default.Bookmark,
                color = MaterialTheme.colorScheme.primary,
                isDefault = currentDefault == "PLAN_TO_WATCH",
                onClick = { onStatusSelected("PLAN_TO_WATCH") }
            )

            StatusOption(
                label = "أشاهده",
                subtitle = "بدأت المشاهدة ولم تنتهِ بعد",
                icon = Icons.Default.PlayCircle,
                color = MaterialTheme.colorScheme.tertiary,
                isDefault = currentDefault == "WATCHING",
                onClick = { onStatusSelected("WATCHING") }
            )

            StatusOption(
                label = "تم",
                subtitle = "شاهدته بالكامل",
                icon = Icons.Default.CheckCircle,
                color = Color(0xFF32A852),
                isDefault = currentDefault == "COMPLETED",
                onClick = { onStatusSelected("COMPLETED") }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // "اجعل هذه الحالة هي الافتراضية" — يُفعل عند اختيار حالة
            // هذا يُترك للـ caller: بعد onStatusSelected، يمكن عرض Snackbar يسأل
            // أو إضافة زر ظاهر بعد الاختيار.
        }
    }
}

@Composable
private fun StatusOption(
    label: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    isDefault: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDefault)
                color.copy(alpha = 0.1f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            if (isDefault) {
                Text(
                    text = "افتراضي",
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
