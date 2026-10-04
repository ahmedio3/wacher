package com.aistudio.cinemios.fxtyr.ui.components.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.aistudio.cinemios.fxtyr.ui.components.rememberPressState
import com.aistudio.cinemios.fxtyr.ui.theme.AppIcons
import kotlinx.coroutines.delay

data class NavigationTabItem(
    val route: String,
    val label: String,
    val filledIcon: ImageVector,
    val outlinedIcon: ImageVector
)

@Composable
fun FloatingBottomNavBar(
    navController: NavHostController,
    tabs: List<NavigationTabItem>,
    currentRoute: String,
    isSearchActive: Boolean = false,
    searchQuery: String = "",
    onOpenSearch: () -> Unit = {},
    onCloseSearch: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onTriggerSearch: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val navBarBg = if (isDark) Color(0xFF141416) else Color(0xFFFFFFFF)
    val navBarBorder = if (isDark) Color(0xFF28282D) else Color(0xFFE2DDD5)
    val shadowColor = if (isDark) Color.Black.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.12f)
    val activeCapsuleBg = if (isDark) Color(0xFF24242A) else Color(0xFFE8E3DA)
    val inputBg = if (isDark) Color(0xFF222226) else Color(0xFFF2EFE9)
    val primaryText = if (isDark) Color.White else Color(0xFF1C1C1E)

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Outer container: floating above navigation bar
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = isSearchActive,
            transitionSpec = {
                fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                        scaleIn(initialScale = 0.94f) togetherWith
                        fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                        scaleOut(targetScale = 0.94f)
            },
            label = "bottom_dock_mode"
        ) { searchActive ->
            if (searchActive) {
                // MORPHED FLOATING SEARCH BAR DOCK (iOS Style)
                Surface(
                    shape = CircleShape,
                    color = navBarBg,
                    tonalElevation = 0.dp,
                    shadowElevation = 14.dp,
                    border = BorderStroke(1.dp, navBarBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .shadow(
                            elevation = 16.dp,
                            shape = CircleShape,
                            ambientColor = shadowColor,
                            spotColor = shadowColor
                        )
                ) {
                    val focusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) {
                        delay(120)
                        try { focusRequester.requestFocus() } catch (_: Exception) {}
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Cancel / Close Button
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    onCloseSearch()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = AppIcons.Close,
                                contentDescription = "إلغاء البحث",
                                tint = primaryText,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Search Input Field (single, clean text field with no duplicate mode toggle)
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(CircleShape)
                                .background(inputBg)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.Search,
                                contentDescription = null,
                                tint = Color(0xFF8E8E93),
                                modifier = Modifier.size(17.dp)
                            )

                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "ابحث عن فيلم أو مسلسل...",
                                        color = Color(0xFF8E8E93),
                                        fontSize = 13.sp
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = onSearchQueryChange,
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = primaryText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    cursorBrush = SolidColor(primaryText),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = {
                                        keyboardController?.hide()
                                        onTriggerSearch()
                                    }),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(focusRequester)
                                )
                            }

                            if (searchQuery.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(if (isDark) Color(0xFF3A3A3C) else Color(0xFFD6D1C7))
                                        .clickable { onSearchQueryChange("") },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = AppIcons.Close,
                                        contentDescription = "مسح",
                                        tint = if (isDark) Color.White else Color(0xFF2C241E),
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // NORMAL DOCK: Circular Search Button (on the RIGHT) + Sliding Capsule Nav Bar
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. CIRCULAR SEARCH BUTTON (First item in RTL Row -> Renders on the RIGHT!)
                    val (searchInteractionSource, searchPressed) = rememberPressState()
                    val searchScale by animateFloatAsState(
                        targetValue = if (searchPressed) 0.88f else 1.0f,
                        animationSpec = tween(120),
                        label = "search_press_scale"
                    )
                    val searchAlpha by animateFloatAsState(
                        targetValue = if (searchPressed) 0.65f else 1.0f,
                        animationSpec = tween(120),
                        label = "search_press_alpha"
                    )

                    Surface(
                        shape = CircleShape,
                        color = navBarBg,
                        tonalElevation = 0.dp,
                        shadowElevation = 14.dp,
                        border = BorderStroke(1.dp, navBarBorder),
                        modifier = Modifier
                            .size(58.dp)
                            .scale(searchScale)
                            .alpha(searchAlpha)
                            .shadow(
                                elevation = 16.dp,
                                shape = CircleShape,
                                ambientColor = shadowColor,
                                spotColor = shadowColor
                            )
                            .clickable(
                                interactionSource = searchInteractionSource,
                                indication = null,
                                onClick = onOpenSearch
                            )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = AppIcons.Search,
                                contentDescription = "بحث",
                                tint = primaryText,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // 2. NAVIGATION PILL WITH SMOOTH SLIDING ACTIVE CAPSULE
                    val slotWidth = 72.dp
                    val slotHeight = 48.dp
                    val selectedIndex = tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
                    val animatedIndex by animateFloatAsState(
                        targetValue = selectedIndex.toFloat(),
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "tab_pill_slide"
                    )

                    Surface(
                        shape = CircleShape,
                        color = navBarBg,
                        tonalElevation = 0.dp,
                        shadowElevation = 14.dp,
                        border = BorderStroke(1.dp, navBarBorder),
                        modifier = Modifier
                            .height(58.dp)
                            .shadow(
                                elevation = 16.dp,
                                shape = CircleShape,
                                ambientColor = shadowColor,
                                spotColor = shadowColor
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 5.dp, vertical = 5.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            // SMOOTH SLIDING CAPSULE INDICATOR
                            // In Jetpack Compose, Modifier.offset has rtlAware = true by default.
                            // A positive x offset automatically moves from Start towards End (in RTL: moves left; in LTR: moves right).
                            Box(
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            x = (slotWidth.toPx() * animatedIndex).toInt(),
                                            y = 0
                                        )
                                    }
                                    .size(width = slotWidth, height = slotHeight)
                                    .clip(CircleShape)
                                    .background(activeCapsuleBg)
                            )

                            // TABS ROW
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                tabs.forEachIndexed { index, tab ->
                                    val isSelected = currentRoute == tab.route
                                    val iconColor by animateColorAsState(
                                        targetValue = if (isSelected) primaryText else Color(0xFF8E8E93),
                                        label = "icon_color"
                                    )
                                    val textColor by animateColorAsState(
                                        targetValue = if (isSelected) primaryText else Color(0xFF8E8E93),
                                        label = "text_color"
                                    )

                                    Box(
                                        modifier = Modifier
                                            .size(width = slotWidth, height = slotHeight)
                                            .clip(CircleShape)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = {
                                                    if (currentRoute != tab.route) {
                                                        if (tab.route == "home") {
                                                            navController.navigate("home") {
                                                                popUpTo(0) { saveState = true }
                                                                launchSingleTop = true
                                                            }
                                                        } else {
                                                            navController.navigate(tab.route) {
                                                                popUpTo(navController.graph.startDestinationId) {
                                                                    saveState = true
                                                                }
                                                                launchSingleTop = true
                                                                restoreState = true
                                                            }
                                                        }
                                                    }
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isSelected) tab.filledIcon else tab.outlinedIcon,
                                                contentDescription = tab.label,
                                                tint = iconColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = tab.label,
                                                color = textColor,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
