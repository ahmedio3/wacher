package com.example.ai.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AiProviderType
import com.example.ai.PROVIDER_CONFIGS
import com.example.ai.ThinkingLevel

@Composable
fun AiHeader(
    providerType: AiProviderType,
    modelId: String,
    thinkingLevel: ThinkingLevel,
    selectorExpanded: Boolean,
    onBackClick: () -> Unit,
    onModelClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val config = PROVIDER_CONFIGS[providerType]
    val model = config?.models?.find { it.id == modelId }
    val showThinking = thinkingLevel != ThinkingLevel.NONE && (model?.supportsReasoning == true)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 9.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White)
        ) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "رجوع",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = !selectorExpanded,
                enter = slideInVertically(tween(180)) { it / 2 } + fadeIn(tween(180)),
                exit = slideOutVertically(tween(140)) { it / 2 } + fadeOut(tween(120))
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                        .clickable(onClick = onModelClick)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .animateContentSize(animationSpec = tween(220))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = model?.displayName ?: modelId,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showThinking,
                            enter = slideInHorizontally(tween(180)) { it / 2 } + fadeIn(tween(180)),
                            exit = fadeOut(tween(100))
                        ) {
                            Text(
                                text = thinkingLevel.key,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        IconButton(
            onClick = onMenuClick,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White)
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "القائمة",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
