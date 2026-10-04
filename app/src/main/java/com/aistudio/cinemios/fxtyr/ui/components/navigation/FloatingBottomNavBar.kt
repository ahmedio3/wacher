package com.aistudio.cinemios.fxtyr.ui.components.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.aistudio.cinemios.fxtyr.ui.theme.AppIcons

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
    isMovieBoxMode: Boolean = false,
    onOpenSearch: () -> Unit = {},
    onCloseSearch: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onTriggerSearch: () -> Unit = {},
    onToggleMovieBoxMode: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val navBarBg = Color(0xFF141416)
    val navBarBorder = Color(0xFF28282D)

    // Outer container: completely transparent background, floating above navigation bar
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
                        .height(56.dp)
                        .shadow(
                            elevation = 16.dp,
                            shape = CircleShape,
                            ambientColor = Color.Black.copy(alpha = 0.45f),
                            spotColor = Color.Black.copy(alpha = 0.65f)
                        )
                ) {
                    val focusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) {
                        try { focusRequester.requestFocus() } catch (_: Exception) {}
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Cancel / Close Button
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onCloseSearch),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = AppIcons.Close,
                                contentDescription = "إلغاء البحث",
                                tint = Color(0xFFE5E5EA),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Search Input Field
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF222226))
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.Search,
                                contentDescription = null,
                                tint = Color(0xFF8E8E93),
                                modifier = Modifier.size(16.dp)
                            )

                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = if (isMovieBoxMode) "ابحث في MovieBox..." else "ابحث عن فيلم أو مسلسل...",
                                        color = Color(0xFF8E8E93),
                                        fontSize = 13.sp
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = onSearchQueryChange,
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Normal
                                    ),
                                    cursorBrush = SolidColor(Color.White),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { onTriggerSearch() }),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(focusRequester)
                                )
                            }

                            if (searchQuery.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF3A3A3C))
                                        .clickable { onSearchQueryChange("") },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = AppIcons.Close,
                                        contentDescription = "مسح",
                                        tint = Color.White,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                        }

                        // MovieBox / TMDB toggle chip
                        Surface(
                            shape = CircleShape,
                            color = if (isMovieBoxMode) MaterialTheme.colorScheme.primary else Color(0xFF222226),
                            border = BorderStroke(1.dp, if (isMovieBoxMode) Color.Transparent else Color(0xFF2F2F35)),
                            modifier = Modifier.clickable(onClick = onToggleMovieBoxMode)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = AppIcons.MovieBox,
                                    contentDescription = null,
                                    tint = if (isMovieBoxMode) Color.White else Color(0xFFB0B0B8),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (isMovieBoxMode) "MB" else "TMDB",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMovieBoxMode) Color.White else Color(0xFFB0B0B8)
                                )
                            }
                        }
                    }
                }
            } else {
                // NORMAL DOCK: Solid Capsule (Bar 3) + Circular Search Button
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Navigation Pill Capsule (Solid, Non-transparent)
                    Surface(
                        shape = CircleShape,
                        color = navBarBg,
                        tonalElevation = 0.dp,
                        shadowElevation = 14.dp,
                        border = BorderStroke(1.dp, navBarBorder),
                        modifier = Modifier.shadow(
                            elevation = 16.dp,
                            shape = CircleShape,
                            ambientColor = Color.Black.copy(alpha = 0.45f),
                            spotColor = Color.Black.copy(alpha = 0.65f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            tabs.forEach { tab ->
                                val isSelected = currentRoute == tab.route
                                FloatingNavItem(
                                    tab = tab,
                                    isSelected = isSelected,
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
                                )
                            }
                        }
                    }

                    // 2. Circular Floating Search Button (Matching Bar 3 in reference image)
                    Surface(
                        shape = CircleShape,
                        color = navBarBg,
                        tonalElevation = 0.dp,
                        shadowElevation = 14.dp,
                        border = BorderStroke(1.dp, navBarBorder),
                        modifier = Modifier
                            .size(54.dp)
                            .shadow(
                                elevation = 16.dp,
                                shape = CircleShape,
                                ambientColor = Color.Black.copy(alpha = 0.45f),
                                spotColor = Color.Black.copy(alpha = 0.65f)
                            )
                            .clickable(onClick = onOpenSearch)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = AppIcons.Search,
                                contentDescription = "بحث",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingNavItem(
    tab: NavigationTabItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.04f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "nav_scale"
    )

    // In Bar 3: active item has a distinct pill background capsule (deep solid dark / accent)
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF24242A) else Color.Transparent,
        label = "bg_color"
    )

    val iconColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color(0xFF8E8E93),
        label = "icon_color"
    )

    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color(0xFF8E8E93),
        label = "text_color"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 7.dp),
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
