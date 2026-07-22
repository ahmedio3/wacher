package com.example.ai.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AiProviderType
import com.example.ai.PROVIDER_CONFIGS
import com.example.ai.ThinkingLevel

private enum class SelectorPage { PROVIDERS, MODELS, THINKING }

/**
 * Inline model selector that expands from the header pill.
 * Navigation: Providers -> Models -> Thinking levels
 * Forward: slide start + fade | Back: slide end + fade
 * RTL-aware via LocalLayoutDirection.
 */
@Composable
fun ModelSelectorPanel(
    expanded: Boolean,
    currentProviderType: AiProviderType,
    currentModelId: String,
    currentThinkingLevel: ThinkingLevel,
    onSelectModel: (AiProviderType, String) -> Unit,
    onSelectThinking: (ThinkingLevel) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var page by remember { mutableStateOf(SelectorPage.PROVIDERS) }
    var selectedProvider by remember { mutableStateOf(currentProviderType) }
    var selectedModelId by remember { mutableStateOf(currentModelId) }
    var isForward by remember { mutableStateOf(true) }

    LaunchedEffect(expanded) {
        if (expanded) {
            page = SelectorPage.PROVIDERS
            selectedProvider = currentProviderType
            selectedModelId = currentModelId
            isForward = true
        }
    }

    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // In RTL "start" is right; forward enter from start (right), exit to end (left)
    val forwardEnter = if (isRtl) {
        slideInHorizontally(tween(220)) { it / 3 } + fadeIn(tween(220))
    } else {
        slideInHorizontally(tween(220)) { -it / 3 } + fadeIn(tween(220))
    }
    val forwardExit = if (isRtl) {
        slideOutHorizontally(tween(220)) { -it / 3 } + fadeOut(tween(220))
    } else {
        slideOutHorizontally(tween(220)) { it / 3 } + fadeOut(tween(220))
    }
    val backEnter = if (isRtl) {
        slideInHorizontally(tween(220)) { -it / 3 } + fadeIn(tween(220))
    } else {
        slideInHorizontally(tween(220)) { it / 3 } + fadeIn(tween(220))
    }
    val backExit = if (isRtl) {
        slideOutHorizontally(tween(220)) { it / 3 } + fadeOut(tween(220))
    } else {
        slideOutHorizontally(tween(220)) { -it / 3 } + fadeOut(tween(220))
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // Scrim
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(top = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = tween(280),
                    expandFrom = Alignment.Top
                ) + fadeIn(tween(200)),
                exit = shrinkVertically(
                    animationSpec = tween(260),
                    shrinkTowards = Alignment.Top
                ) + fadeOut(tween(180))
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(min = 200.dp, max = 320.dp)
                        .shadow(8.dp, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .animateContentSize(animationSpec = tween(260))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                        .padding(vertical = 8.dp)
                ) {
                    AnimatedContent(
                        targetState = page,
                        transitionSpec = {
                            if (isForward) {
                                forwardEnter togetherWith forwardExit
                            } else {
                                backEnter togetherWith backExit
                            }
                        },
                        label = "selector_page"
                    ) { currentPage ->
                        when (currentPage) {
                            SelectorPage.PROVIDERS -> ProvidersPage(
                                currentProviderType = currentProviderType,
                                onProviderClick = { provider ->
                                    selectedProvider = provider
                                    isForward = true
                                    page = SelectorPage.MODELS
                                }
                            )
                            SelectorPage.MODELS -> ModelsPage(
                                providerType = selectedProvider,
                                currentModelId = currentModelId,
                                onBack = {
                                    isForward = false
                                    page = SelectorPage.PROVIDERS
                                },
                                onModelClick = { modelId ->
                                    selectedModelId = modelId
                                    val model = PROVIDER_CONFIGS[selectedProvider]
                                        ?.models?.find { it.id == modelId }
                                    onSelectModel(selectedProvider, modelId)
                                    if (model != null && model.supportsReasoning && model.reasoningLevels.size > 1) {
                                        isForward = true
                                        page = SelectorPage.THINKING
                                    } else {
                                        onDismiss()
                                    }
                                }
                            )
                            SelectorPage.THINKING -> ThinkingPage(
                                providerType = selectedProvider,
                                modelId = selectedModelId,
                                currentLevel = currentThinkingLevel,
                                onBack = {
                                    isForward = false
                                    page = SelectorPage.MODELS
                                },
                                onLevelClick = { level ->
                                    onSelectThinking(level)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProvidersPage(
    currentProviderType: AiProviderType,
    onProviderClick: (AiProviderType) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "المزود",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
        PROVIDER_CONFIGS.keys.forEach { provider ->
            SelectorRow(
                title = provider.displayName,
                selected = provider == currentProviderType,
                trailing = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    )
                },
                onClick = { onProviderClick(provider) }
            )
        }
    }
}

@Composable
private fun ModelsPage(
    providerType: AiProviderType,
    currentModelId: String,
    onBack: () -> Unit,
    onModelClick: (String) -> Unit
) {
    val models = PROVIDER_CONFIGS[providerType]?.models.orEmpty()
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onBack)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowLeft,
                contentDescription = "رجوع",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = providerType.displayName,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        models.forEach { model ->
            SelectorRow(
                title = model.displayName,
                selected = model.id == currentModelId,
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (model.supportsVision) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "Vision",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                            )
                        }
                        if (model.supportsReasoning) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = "Reasoning",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                            )
                        }
                        if (model.id == currentModelId) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                onClick = { onModelClick(model.id) }
            )
        }
    }
}

@Composable
private fun ThinkingPage(
    providerType: AiProviderType,
    modelId: String,
    currentLevel: ThinkingLevel,
    onBack: () -> Unit,
    onLevelClick: (ThinkingLevel) -> Unit
) {
    val model = PROVIDER_CONFIGS[providerType]?.models?.find { it.id == modelId }
    val levels = model?.reasoningLevels ?: listOf(ThinkingLevel.NONE)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onBack)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowLeft,
                contentDescription = "رجوع",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "مستوى التفكير",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        levels.forEach { level ->
            SelectorRow(
                title = when (level) {
                    ThinkingLevel.NONE -> "بدون (تعطيل)"
                    ThinkingLevel.LOW -> "منخفض (low)"
                    ThinkingLevel.MEDIUM -> "متوسط (medium)"
                    ThinkingLevel.HIGH -> "مرتفع (high)"
                },
                selected = level == currentLevel,
                trailing = {
                    if (level == currentLevel) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                onClick = { onLevelClick(level) }
            )
        }
    }
}

@Composable
private fun SelectorRow(
    title: String,
    selected: Boolean,
    trailing: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        trailing()
    }
}
