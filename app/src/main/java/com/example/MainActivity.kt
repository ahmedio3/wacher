package com.example

import android.os.Build
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.core.view.WindowCompat

import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.ui.platform.LocalOverscrollConfiguration
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.navigation.isForwardNavigation
import com.example.ui.navigation.slideIn
import com.example.ui.navigation.slideOut
import com.example.ui.screens.*
import com.example.ui.components.bouncyOverscroll
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MovieViewModel
import com.example.ui.viewmodel.ViewModelFactory
import com.example.data.remote.moviebox.viewmodel.MovieBoxViewModel

class MainActivity : ComponentActivity() {

    private val deepLinkState = androidx.compose.runtime.mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        deepLinkState.value = extractDeepLink(intent)
        
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            val context = applicationContext
            val prefs = context.getSharedPreferences("watchera_prefs", MODE_PRIVATE)
            var isDarkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }

            // Listen for preference changes
            LaunchedEffect(Unit) {
                while (true) {
                    val current = prefs.getBoolean("dark_mode", false)
                    if (current != isDarkMode) {
                        isDarkMode = current
                    }
                    kotlinx.coroutines.delay(200)
                }
            }

            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView)
                    .isAppearanceLightStatusBars = !isDarkMode
            }

            CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                MyApplicationTheme(darkTheme = isDarkMode) {
                    MainAppContainer(deepLinkState = deepLinkState)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val dl = extractDeepLink(intent)
        if (dl != null) deepLinkState.value = dl
    }

    private fun extractDeepLink(intent: Intent): String? {
        val uri = intent.data ?: return null
        if (uri.scheme == "cinemios" && uri.host == "show") {
            val mediaType = uri.pathSegments.getOrNull(0)
            val id = uri.pathSegments.getOrNull(1)
            if (mediaType != null && id != null && id.toIntOrNull() != null) {
                return "detail/$id/$mediaType"
            }
        }
        if (uri.scheme == "https" && uri.host == "watchera.com") {
            val mediaType = uri.pathSegments.getOrNull(1)
            val id = uri.pathSegments.getOrNull(2)
            if (mediaType != null && id != null && id.toIntOrNull() != null) {
                return "detail/$id/$mediaType"
            }
        }
        return null
    }


}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContainer(deepLinkState: androidx.compose.runtime.MutableState<String?> = androidx.compose.runtime.mutableStateOf(null)) {
    val navController = rememberNavController()
    val context = LocalContext.current.applicationContext as android.app.Application
    
    val movieViewModel: MovieViewModel = viewModel(
        factory = ViewModelFactory(context)
    )

    LaunchedEffect(Unit) {
        movieViewModel.resumePendingDownloads()
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val tabItems = listOf(
        NavigationTabItem(
            route = "home",
            label = "الرئيسية",
            filledIcon = Icons.Default.Home,
            outlinedIcon = Icons.Outlined.Home
        ),
        NavigationTabItem(
            route = "explore",
            label = "استكشاف",
            filledIcon = Icons.Default.Explore,
            outlinedIcon = Icons.Outlined.Explore
        ),
        NavigationTabItem(
            route = "downloads",
            label = "التحميلات",
            filledIcon = Icons.Default.ArrowCircleDown,
            outlinedIcon = Icons.Outlined.ArrowCircleDown
        ),
        NavigationTabItem(
            route = "settings",
            label = "الإعدادات",
            filledIcon = Icons.Default.Settings,
            outlinedIcon = Icons.Outlined.Settings
        )
    )

    val shouldShowBottomBar = currentRoute in listOf("home", "explore", "downloads", "settings")

    LaunchedEffect(deepLinkState.value, currentRoute) {
        if (deepLinkState.value != null && currentRoute != null) {
            navController.navigate(deepLinkState.value!!) {
                launchSingleTop = true
            }
            deepLinkState.value = null
        }
    }

    val downloads by movieViewModel.downloads.collectAsState()
    val activeDownloads = downloads.filter { it.status == "downloading" || it.status == "paused" || it.status == "queued" }
    var showActiveDownloadsSheet by remember { mutableStateOf(false) }

    var fabOffsetX by remember { mutableFloatStateOf(0f) }
    var fabOffsetY by remember { mutableFloatStateOf(0f) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        if (showActiveDownloadsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showActiveDownloadsSheet = false },
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("التحميلات النشطة", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                    if (activeDownloads.isEmpty()) {
                        Text("لا يوجد تحميلات نشطة حالياً", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 16.dp))
                    } else {
                        val sortedDls = remember(activeDownloads) { activeDownloads.sortedByDescending { it.addedAt } }
                        LazyColumn(modifier = Modifier.fillMaxWidth().bouncyOverscroll()) {
                            items(sortedDls) { item ->
                                com.example.ui.screens.DownloadItemRow(
                                    item = item,
                                    viewModel = movieViewModel,
                                    onPlayClick = {
                                        val encodedId = Uri.encode(item.id)
                                        val encodedTitle = Uri.encode(item.title)
                                        val encodedPath = Uri.encode(item.localFilePath)
                                        navController.navigate("offline_player/$encodedId/$encodedTitle?localFilePath=$encodedPath")
                                        showActiveDownloadsSheet = false
                                    }
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                AnimatedVisibility(
                    visible = shouldShowBottomBar,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it }
                ) {
                    ModernBottomNavBar(
                        navController = navController,
                        tabs = tabItems,
                        currentRoute = currentRoute ?: "home"
                    )
                }
            },
            floatingActionButton = {
                val isPlayerRoute = currentRoute?.startsWith("offline_player") == true 
                                    || currentRoute?.startsWith("player") == true
                                    || currentRoute == "adult_content"
                                    || currentRoute?.startsWith("adult_content") == true
                if (activeDownloads.isNotEmpty() && !isPlayerRoute) {
                    val config = LocalConfiguration.current
                    val screenWidthDp = config.screenWidthDp.toFloat()
                    val screenHeightDp = config.screenHeightDp.toFloat()
                    val fabHalfSize = 28f
                    
                    val overallProgress = remember(activeDownloads) {
                        val progresses = activeDownloads.map { it.progress }
                        if (progresses.isEmpty()) 0f 
                        else progresses.average().toFloat() / 100f
                    }
                    
                    FloatingActionButton(
                        onClick = { showActiveDownloadsSheet = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(bottom = if (shouldShowBottomBar) 0.dp else 16.dp)
                            .offset(x = fabOffsetX.dp, y = fabOffsetY.dp)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    fabOffsetX -= dragAmount.x
                                    fabOffsetY += dragAmount.y
                                    fabOffsetX = fabOffsetX.coerceIn(
                                        -(screenWidthDp / 2 - fabHalfSize),
                                        (screenWidthDp / 2 - fabHalfSize)
                                    )
                                    fabOffsetY = fabOffsetY.coerceIn(
                                        -(screenHeightDp / 2 - fabHalfSize),
                                        (screenHeightDp / 2 - fabHalfSize)
                                    )
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.ArrowCircleDown, 
                                contentDescription = "التحميلات", 
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            CircularProgressIndicator(
                                progress = { overallProgress },
                                modifier = Modifier.size(52.dp),
                                color = MaterialTheme.colorScheme.tertiary,
                                strokeWidth = 3.dp
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            val layoutDirection = LocalLayoutDirection.current
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.fillMaxSize(),
                enterTransition = {
                    val forward = isForwardNavigation(
                        initialState.destination.route,
                        targetState.destination.route,
                        isPopTransition = false
                    )
                    slideIn(forward, layoutDirection)
                },
                exitTransition = {
                    val forward = isForwardNavigation(
                        initialState.destination.route,
                        targetState.destination.route,
                        isPopTransition = false
                    )
                    slideOut(forward, layoutDirection)
                },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { slideOut(false, layoutDirection) }
            ) {
            composable("home") {
                HomeScreen(
                    viewModel = movieViewModel,
                    onNavigateToDetails = { id, type ->
                        navController.navigate("detail/$id/$type")
                    },
                    onNavigateToMovieBoxDetails = { id, type, title, posterUrl ->
                        navController.navigate(
                            "mb_details/$id/$type?title=${java.net.URLEncoder.encode(title, "UTF-8")}&posterUrl=${java.net.URLEncoder.encode(posterUrl, "UTF-8")}"
                        )
                    },
                    onNavigateToSettings = {
                        navController.navigate("settings")
                    },
                    onNavigateToWatchlist = {
                        navController.navigate("watchlist")
                    },
                    onNavigateToBrowser = {
                        navController.navigate("browser")
                    }
                )
            }
            
            composable("browser") {
                BrowserScreen(
                    viewModel = movieViewModel,
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable("explore") {
                ExploreScreen(
                    onNavigateToAdultContent = { queries ->
                        val encQueries = java.net.URLEncoder.encode(queries, "UTF-8")
                        navController.navigate("adult_content?queries=$encQueries")
                    },
                    onNavigateToSubtitleDownloads = {
                        navController.navigate("subtitle-downloads")
                    },
                    onNavigateToWatchlist = {
                        navController.navigate("watchlist")
                    },
                    onNavigateToChat = { roomId, isPublic ->
                        navController.navigate("chat/${Uri.encode(roomId)}/$isPublic")
                    },
                    onNavigateToAiChat = {
                        navController.navigate("ai_chat")
                    }
                )
            }

            composable("ai_chat") {
                val aiViewModel: com.example.ai.AiViewModel = viewModel(
                    factory = ViewModelFactory(context)
                )
                com.example.ai.ui.AiChatScreen(
                    viewModel = aiViewModel,
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(
                route = "adult_content?queries={queries}",
                arguments = listOf(
                    navArgument("queries") { type = NavType.StringType; defaultValue = "" }
                )
            ) { backStackEntry ->
                val queries = backStackEntry.arguments?.getString("queries") ?: ""
                val adultViewModel: MovieBoxViewModel = viewModel(
                    factory = ViewModelFactory(context)
                )
                AdultContentScreen(
                    viewModel = adultViewModel,
                    initialQueries = java.net.URLDecoder.decode(queries, "UTF-8"),
                    onNavigateToMovieBoxDetails = { id, type, title, posterUrl ->
                        navController.navigate(
                            "mb_details/$id/$type?title=${java.net.URLEncoder.encode(title, "UTF-8")}&posterUrl=${java.net.URLEncoder.encode(posterUrl, "UTF-8")}"
                        )
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable("watchlist") {
                WatchlistScreen(
                    viewModel = movieViewModel,
                    onBackClick = { navController.popBackStack() },
                    onNavigateToDetails = { id, type ->
                        navController.navigate("detail/$id/$type")
                    },
                    onNavigateToMovieBoxDetails = { id, type, title, posterUrl ->
                        navController.navigate(
                            "mb_details/$id/$type?title=${java.net.URLEncoder.encode(title, "UTF-8")}&posterUrl=${java.net.URLEncoder.encode(posterUrl, "UTF-8")}"
                        )
                    }
                )
            }

            composable(
                route = "downloads",
                enterTransition = {
                    val forward = isForwardNavigation(
                        initialState.destination.route,
                        targetState.destination.route,
                        isPopTransition = false
                    )
                    slideIn(forward, layoutDirection)
                },
                exitTransition = {
                    val forward = isForwardNavigation(
                        initialState.destination.route,
                        targetState.destination.route,
                        isPopTransition = false
                    )
                    slideOut(forward, layoutDirection)
                }
            ) {
                DownloadsScreen(
                    viewModel = movieViewModel,
                    onNavigateToPlayer = { id, title, localPath ->
                        val encodedId = Uri.encode(id)
                        val encodedTitle = Uri.encode(title)
                        val encodedPath = Uri.encode(localPath)
                        navController.navigate("offline_player/$encodedId/$encodedTitle?localFilePath=$encodedPath")
                    },
                    navController = navController
                )
            }

            composable("subtitle-downloads") {
                SubtitleDownloadsScreen(
                    viewModel = movieViewModel,
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable("settings") {
                SettingsScreen(
                    viewModel = movieViewModel,
                    onNavigateToHistory = { navController.navigate("history") }
                )
            }

            composable("history") {
                HistoryScreen(
                    viewModel = movieViewModel,
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(
                route = "chat/{roomId}/{isPublic}",
                arguments = listOf(
                    navArgument("roomId") { type = NavType.StringType },
                    navArgument("isPublic") { type = NavType.BoolType }
                )
            ) { backStackEntry ->
                val roomId = backStackEntry.arguments?.getString("roomId") ?: "public"
                val isPublic = backStackEntry.arguments?.getBoolean("isPublic") ?: true
                ChatScreen(
                    roomId = roomId,
                    isPublic = isPublic,
                    onBackClick = { navController.popBackStack() },
                    onNavigateToDM = { dmRoomId, _ ->
                        navController.navigate("chat/${Uri.encode(dmRoomId)}/false")
                    }
                )
            }

            composable(
                route = "detail/{mediaId}/{mediaType}",
                arguments = listOf(
                    navArgument("mediaId") { type = NavType.IntType },
                    navArgument("mediaType") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val mediaId = backStackEntry.arguments?.getInt("mediaId") ?: 0
                val mediaType = backStackEntry.arguments?.getString("mediaType") ?: "movie"

                DetailScreen(
                    mediaId = mediaId,
                    mediaType = mediaType,
                    viewModel = movieViewModel,
                    onBackClick = { navController.popBackStack() },
                onNavigateToPlayer = { id, title, localPath ->
                    val encodedId = Uri.encode(id)
                    val encodedTitle = Uri.encode(title)
                    val encodedPath = Uri.encode(localPath)
                    if (localPath.isNotEmpty()) {
                        navController.navigate("offline_player/$encodedId/$encodedTitle?localFilePath=$encodedPath")
                    } else {
                        navController.navigate("player/$encodedId/$encodedTitle?localFilePath=")
                    }
                }
            )

        }

        composable(
            route = "series_downloads/{seriesId}",
            arguments = listOf(
                navArgument("seriesId") { type = NavType.StringType }
            ),
            popExitTransition = { ExitTransition.None }
        ) { backStackEntry ->
            val seriesId = backStackEntry.arguments?.getString("seriesId") ?: ""
            SeriesDetailPage(
                seriesId = seriesId,
                viewModel = movieViewModel,
                onNavigateToPlayer = { id, title, localPath ->
                    val encodedId = Uri.encode(id)
                    val encodedTitle = Uri.encode(title)
                    val encodedPath = Uri.encode(localPath)
                    navController.navigate("offline_player/$encodedId/$encodedTitle?localFilePath=$encodedPath")
                },
                onBack = { navController.popBackStack() },
                onPillClick = {
                    val id = seriesId.toIntOrNull() ?: 0
                    if (id > 0) navController.navigate("detail/$id/tv")
                }
            )
        }

            composable(
                route = "mb_details/{mediaId}/{mediaType}?title={title}&posterUrl={posterUrl}",
                arguments = listOf(
                    navArgument("mediaId") { type = NavType.StringType },
                    navArgument("mediaType") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType; defaultValue = "" },
                    navArgument("posterUrl") { type = NavType.StringType; defaultValue = "" }
                )
            ) { backStackEntry ->
                val mediaId = backStackEntry.arguments?.getString("mediaId") ?: ""
                val mediaType = backStackEntry.arguments?.getString("mediaType") ?: "movie"
                val title = backStackEntry.arguments?.getString("title") ?: ""
                val posterUrl = backStackEntry.arguments?.getString("posterUrl") ?: ""

                com.example.ui.screens.MovieBoxDetailScreen(
                    subjectId = mediaId,
                    mediaType = mediaType,
                    titleParams = title,
                    posterParams = posterUrl,
                    viewModel = movieViewModel,
                    onBackClick = { navController.popBackStack() },
                    onPlayClick = { id, playTitle ->
                        val encodedTitle = java.net.URLEncoder.encode(playTitle, "UTF-8")
                        navController.navigate("player/$id/$encodedTitle?localFilePath=")
                    }
                )
            }

            composable(
                route = "offline_player/{mediaId}/{title}?localFilePath={localFilePath}",
                enterTransition = { fadeIn() },
                exitTransition = { fadeOut() },
                arguments = listOf(
                    navArgument("mediaId") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType },
                    navArgument("localFilePath") { type = NavType.StringType; defaultValue = "" }
                )
            ) { backStackEntry ->
                val rawMediaId = backStackEntry.arguments?.getString("mediaId") ?: ""
                val rawTitle = backStackEntry.arguments?.getString("title") ?: ""
                val rawPath = backStackEntry.arguments?.getString("localFilePath") ?: ""
                
                val mediaId = java.net.URLDecoder.decode(rawMediaId, "UTF-8")
                val decodedTitle = java.net.URLDecoder.decode(rawTitle, "UTF-8")
                val decodedPath = java.net.URLDecoder.decode(rawPath, "UTF-8")

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    OfflinePlayerScreen(
                        mediaId = mediaId,
                        title = decodedTitle,
                        localFilePath = decodedPath,
                        viewModel = movieViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable(
                route = "player/{mediaId}/{title}?localFilePath={localFilePath}",
                enterTransition = { fadeIn() },
                exitTransition = { fadeOut() },
                arguments = listOf(
                    navArgument("mediaId") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType },
                    navArgument("localFilePath") { type = NavType.StringType; defaultValue = "" }
                )
            ) { backStackEntry ->
                val mediaId = backStackEntry.arguments?.getString("mediaId") ?: ""
                val rawTitle = backStackEntry.arguments?.getString("title") ?: ""
                val rawPath = backStackEntry.arguments?.getString("localFilePath") ?: ""
                
                val decodedTitle = java.net.URLDecoder.decode(rawTitle, "UTF-8")
                val decodedPath = java.net.URLDecoder.decode(rawPath, "UTF-8")

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    PlayerScreen(
                        mediaId = mediaId,
                        title = decodedTitle,
                        localFilePath = decodedPath,
                        viewModel = movieViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
    }
}

@Composable
fun ModernBottomNavBar(
    navController: NavHostController,
    tabs: List<NavigationTabItem>,
    currentRoute: String
) {
    NavigationBar(
        modifier = Modifier.height(84.dp).padding(bottom = 6.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 4.dp
    ) {
            tabs.forEach { tab ->
                val isSelected = currentRoute == tab.route
                NavigationBarItem(
                    selected = isSelected,
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
                    },
                    icon = {
                        Icon(
                            imageVector = if (isSelected) tab.filledIcon else tab.outlinedIcon,
                            contentDescription = tab.label,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    label = {
                        Text(
                            text = tab.label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 10.sp
                        )
                    },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                )
            }
        }
}

data class NavigationTabItem(
    val route: String,
    val label: String,
    val filledIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val outlinedIcon: androidx.compose.ui.graphics.vector.ImageVector
)
