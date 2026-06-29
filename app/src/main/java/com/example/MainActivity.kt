package com.example

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import com.example.utils.ChatNotificationService
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MovieViewModel
import com.example.ui.viewmodel.ViewModelFactory
import com.example.data.remote.moviebox.viewmodel.MovieBoxViewModel

class MainActivity : ComponentActivity() {

    companion object {
        var isChatForeground = false
    }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startChatService()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startChatService()
        }

        val openChat = intent.getBooleanExtra("open_chat", false)
        
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppContainer(startWithChat = openChat)
            }
        }
    }

    private fun startChatService() {
        try {
            startService(Intent(this, ChatNotificationService::class.java))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContainer(startWithChat: Boolean = false) {
    val navController = rememberNavController()
    val context = LocalContext.current.applicationContext as android.app.Application
    
    // Instantiate MovieViewModel with proper context
    val movieViewModel: MovieViewModel = viewModel(
        factory = ViewModelFactory(context)
    )

    // Resume downloading tasks on startup
    LaunchedEffect(Unit) {
        movieViewModel.resumePendingDownloads()
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Tab items listing for iOS Style Bar
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
            filledIcon = Icons.Default.ChatBubble,
            outlinedIcon = Icons.Outlined.ChatBubbleOutline
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

    // Hide Bottom bar on Detail, Settings, Player, and Splash views
    val shouldShowBottomBar = currentRoute in listOf("home", "explore", "downloads", "settings")

    LaunchedEffect(currentRoute) {
        MainActivity.isChatForeground = currentRoute == "chat/global"
    }

    LaunchedEffect(startWithChat) {
        if (startWithChat) {
            navController.navigate("chat/global") {
                launchSingleTop = true
            }
        }
    }

    val downloads by movieViewModel.downloads.collectAsState()
    val activeDownloads = downloads.filter { it.status == "downloading" || it.status == "paused" || it.status == "queued" }
    var showActiveDownloadsSheet by remember { mutableStateOf(false) }

    // Draggable FAB position
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
                        // Descending order by addedAt
                        val sortedDls = remember(activeDownloads) { activeDownloads.sortedByDescending { it.addedAt } }
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(sortedDls) { item ->
                                com.example.ui.screens.DownloadItemRow(
                                    item = item,
                                    viewModel = movieViewModel,
                                    onPlayClick = {
                                        navController.navigate("offline_player/${item.id}/${Uri.encode(item.title)}")
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
                if (shouldShowBottomBar) {
                    ModernBottomNavBar(
                        navController = navController,
                        tabs = tabItems,
                        currentRoute = currentRoute ?: "home"
                    )
                }
            },
            floatingActionButton = {
                // Hide FAB on offline_player, player, splash routes
                val isPlayerRoute = currentRoute?.startsWith("offline_player") == true 
                                    || currentRoute?.startsWith("player") == true
                                    || currentRoute == "splash"
                if (activeDownloads.isNotEmpty() && !isPlayerRoute) {
                    val config = LocalConfiguration.current
                    val screenWidthDp = config.screenWidthDp.toFloat()
                    val screenHeightDp = config.screenHeightDp.toFloat()
                    val fabHalfSize = 28f // half of FAB size (56dp/2)
                    
                    // Always show progress circle when active downloads exist
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
                                    // RTL fix: negate horizontal drag
                                    fabOffsetX -= dragAmount.x
                                    fabOffsetY += dragAmount.y
                                    // Clamp: allow only half the FAB to go off-screen
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
            NavHost(
                navController = navController,
                startDestination = "splash",
                modifier = Modifier.fillMaxSize()
            ) {
            composable("splash") {
                SplashScreen(
                    onSplashFinished = {
                        navController.navigate("home") {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                )
            }

            composable("home") {
                HomeScreen(
                    viewModel = movieViewModel,
                    onNavigateToDetails = { id, type ->
                        navController.navigate("detail/$id/$type")
                    },
                    onNavigateToMovieBoxDetails = { id, type, title, posterUrl ->
                        navController.navigate("mb_details/$id/$type?title=${android.net.Uri.encode(title)}&posterUrl=${android.net.Uri.encode(posterUrl)}")
                    },
                    onNavigateToSettings = {
                        navController.navigate("settings")
                    }
                )
            }
            
            composable("explore") {
                val movieBoxViewModel: MovieBoxViewModel = viewModel(
                    factory = ViewModelFactory(context)
                )
                ExploreScreen(
                    viewModel = movieBoxViewModel,
                    onNavigateToMovieBoxDetails = { id, type, title, posterUrl ->
                        navController.navigate("mb_details/$id/$type?title=${android.net.Uri.encode(title)}&posterUrl=${android.net.Uri.encode(posterUrl)}")
                    }
                )
            }

            composable("chat/global") {
                GlobalChatScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable("watchlist") {
                WatchlistScreen(
                    viewModel = movieViewModel,
                    onNavigateToDetails = { id, type ->
                        navController.navigate("detail/$id/$type")
                    }
                )
            }

            composable("downloads") {
                DownloadsScreen(
                    viewModel = movieViewModel,
                    onNavigateToPlayer = { id, title, localPath ->
                        val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
                        val encodedPath = java.net.URLEncoder.encode(localPath, "UTF-8")
                        navController.navigate("offline_player/$id/$encodedTitle?localFilePath=$encodedPath")
                    }
                )
            }

            composable("settings") {
                SettingsScreen(
                    viewModel = movieViewModel,
                    onBackClick = { navController.popBackStack() },
                    onNavigateToWatchlist = { navController.navigate("watchlist") }
                )
            }

            // Media Detail screen router with safe argument parsing
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
                        val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
                        val encodedPath = java.net.URLEncoder.encode(localPath, "UTF-8")
                        if (localPath.isNotEmpty()) {
                            navController.navigate("offline_player/$id/$encodedTitle?localFilePath=$encodedPath")
                        } else {
                            navController.navigate("player/$id/$encodedTitle?localFilePath=")
                        }
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
                    onPlayClick = { id, title ->
                        val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
                        navController.navigate("player/$id/$encodedTitle?localFilePath=")
                    }
                )
            }

            // Dedicated Offline Native Video Player (Optimized for Downloads without interference)
            composable(
                route = "offline_player/{mediaId}/{title}?localFilePath={localFilePath}",
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
                    OfflinePlayerScreen(
                        mediaId = mediaId,
                        title = decodedTitle,
                        localFilePath = decodedPath,
                        viewModel = movieViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            // Dedicated Native Video Player Cinema View (Supports online playing)
            composable(
                route = "player/{mediaId}/{title}?localFilePath={localFilePath}",
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

// Custom Solid Integrated iOS Bottom Navigation Bar (flat, small, elegant)
@Composable
fun ModernBottomNavBar(
    navController: NavHostController,
    tabs: List<NavigationTabItem>,
    currentRoute: String
) {
    NavigationBar(
        modifier = Modifier.height(84.dp).padding(bottom = 6.dp),
        containerColor = Color(0xFFEFECE4), // Base surface color
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 4.dp
    ) {
            tabs.forEach { tab ->
                val isSelected = currentRoute == tab.route
                NavigationBarItem(
                    selected = isSelected,
                    onClick = {
                        if (currentRoute != tab.route) {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
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
                        indicatorColor = Color(0xFFD6C8B3) // A beige shade coordinating with overall Theme
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
