package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.util.Patterns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.AssistChip
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.SavedImageEntity
import com.example.ui.viewmodel.MovieViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BrowserScreen(
    viewModel: MovieViewModel,
    onBackClick: () -> Unit
) {
    // ---- WebView state ----
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf("about:blank") }
    var urlInput by remember { mutableStateOf(currentUrl) }
    var showHome by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var pageTitle by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var loadProgress by remember { mutableFloatStateOf(0f) }

    // ---- UI state ----
    var showSavedImages by remember { mutableStateOf(false) }
    var showSaveImageDialog by remember { mutableStateOf(false) }
    var pendingImageUrl by remember { mutableStateOf("") }
    var saveImageFailed by remember { mutableStateOf(false) }
    var viewerIndex by remember { mutableIntStateOf(-1) }

    val context = LocalContext.current
    val savedImages by viewModel.savedImages.collectAsState()

    // ---- BackHandler: WebView back vs system back ----
    BackHandler(enabled = true) {
        if (showSavedImages) {
            showSavedImages = false
        } else if (viewerIndex >= 0) {
            viewerIndex = -1
        } else if (webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        } else {
            onBackClick()
        }
    }

    // ---- Cleanup WebView on dispose ----
    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.stopLoading()
            webViewRef?.destroy()
            webViewRef = null
        }
    }

    // ---- Helper: navigate to a URL ----
    fun navigateTo(input: String) {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return
        val target = if (isLikelyUrl(trimmed)) {
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) "https://$trimmed" else trimmed
        } else {
            // Smart search — properly UTF-8 encode for Google (Arabic + English safe)
            "https://www.google.com/search".toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("q", trimmed)
                ?.build()
                ?.toString()
                ?: "https://www.google.com/search?q=${Uri.encode(trimmed)}"
        }
        currentUrl = target
        urlInput = target
        webViewRef?.loadUrl(target)
    }

    // ---- Saved images mode ----
    if (showSavedImages && viewerIndex < 0) {
        SavedImagesGrid(
            savedImages = savedImages,
            onDeleteImage = { viewModel.deleteSavedImage(it) },
            onBack = { showSavedImages = false },
            onImageClick = { index -> viewerIndex = index }
        )
        return
    }

    // ---- Full-screen image viewer ----
    if (viewerIndex >= 0 && viewerIndex < savedImages.size) {
        FullScreenImageViewer(
            images = savedImages,
            initialIndex = viewerIndex,
            onDeleteImage = { id ->
                viewModel.deleteSavedImage(id)
                if (savedImages.size <= 1) {
                    viewerIndex = -1
                    showSavedImages = false
                }
            },
            onClose = { viewerIndex = -1 }
        )
        return
    }

    // ---- Save image dialog ----
    if (showSaveImageDialog) {
        AlertDialog(
            onDismissRequest = { showSaveImageDialog = false; pendingImageUrl = "" },
            title = { Text("حفظ الصورة") },
            text = { Text("هل تريد حفظ هذه الصورة داخل التطبيق؟\nلن تظهر في معرض الصور العام.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveImageFromBrowser(
                        sourceUrl = pendingImageUrl,
                        pageUrl = currentUrl,
                        pageTitle = pageTitle
                    )
                    showSaveImageDialog = false
                    pendingImageUrl = ""
                    Toast.makeText(context, "جاري حفظ الصورة...", Toast.LENGTH_SHORT).show()
                }) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveImageDialog = false; pendingImageUrl = "" }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // ---- Image extraction failed dialog ----
    if (saveImageFailed) {
        AlertDialog(
            onDismissRequest = { saveImageFailed = false },
            title = { Text("تعذر حفظ الصورة") },
            text = { Text("لم نتمكن من استخراج رابط الصورة الحقيقي. قد تكون الصورة محمية أو محملة بتقنية لا تدعم الحفظ المباشر.\n\nيمكنك استخدام لقطة شاشة بدلاً من ذلك.") },
            confirmButton = {
                TextButton(onClick = { saveImageFailed = false }) {
                    Text("حسناً")
                }
            }
        )
    }

    // ---- Main browser UI ----
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column(
                modifier = Modifier.statusBarsPadding()
            ) {
                // ---- iOS-style address bar ----
                Surface(
                    tonalElevation = 0.dp,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // [←] Exit browser
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "خروج",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // URL TextField (rounded pill)
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            singleLine = true,
                            placeholder = {
                                Text(
                                    "ابحث أو أدخل رابطاً",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            },
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { navigateTo(urlInput) }),
                            shape = RoundedCornerShape(22.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            )
                        )

                        // [🏠] Home
                        IconButton(
                            onClick = {
                                currentUrl = "about:blank"
                                urlInput = ""
                                showHome = true
                                webViewRef?.loadUrl("about:blank")
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Home,
                                contentDescription = "الصفحة الرئيسية",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // [↻/✕] Refresh / Stop
                        IconButton(
                            onClick = {
                                if (isLoading) {
                                    webViewRef?.stopLoading()
                                } else {
                                    webViewRef?.reload()
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            if (isLoading) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "إيقاف التحميل",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "تحديث",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // [🖼️] Saved images
                        IconButton(
                            onClick = { showSavedImages = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.PhotoLibrary,
                                contentDescription = "الصور المحفوظة",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // ---- Loading progress bar ----
                AnimatedVisibility(visible = isLoading, enter = fadeIn(), exit = fadeOut()) {
                    LinearProgressIndicator(
                        progress = { loadProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
    ) { paddingValues ->
        // ---- WebView + home overlay ----
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    // WebSettings
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        builtInZoomControls = true
                        displayZoomControls = false
                        setSupportMultipleWindows(false)
                        javaScriptCanOpenWindowsAutomatically = false
                        allowFileAccess = false
                        allowContentAccess = false
                        allowFileAccessFromFileURLs = false
                        allowUniversalAccessFromFileURLs = false
                        databaseEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                    }

                    // WebViewClient
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            url?.let {
                                currentUrl = it
                                urlInput = it
                                if (it != "about:blank") showHome = false
                            }
                            isLoading = true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false
                            canGoBack = view?.canGoBack() ?: false
                            canGoForward = view?.canGoForward() ?: false
                            view?.title?.let { pageTitle = it }
                        }

                        override fun doUpdateVisitedHistory(
                            view: WebView?,
                            url: String?,
                            isReload: Boolean
                        ) {
                            super.doUpdateVisitedHistory(view, url, isReload)
                            canGoBack = view?.canGoBack() ?: false
                            canGoForward = view?.canGoForward() ?: false
                        }

                        @Deprecated("Deprecated in API 24")
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: String?
                        ): Boolean {
                            request?.let { view?.loadUrl(it) }
                            return true
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            request?.url?.toString()?.let { view?.loadUrl(it) }
                            return true
                        }
                    }

                    // WebChromeClient with loading progress
                    webChromeClient = object : WebChromeClient() {
                        override fun onReceivedTitle(view: WebView?, title: String?) {
                            super.onReceivedTitle(view, title)
                            title?.let { pageTitle = it }
                        }

                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            super.onProgressChanged(view, newProgress)
                            loadProgress = newProgress / 100f
                        }
                    }

                    // Long-press to save images
                    setOnLongClickListener { v ->
                        val wv = v as? WebView ?: return@setOnLongClickListener false
                        val result = wv.hitTestResult
                        when (result.type) {
                            WebView.HitTestResult.IMAGE_TYPE,
                            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                                val imgUrl = result.extra
                                if (!imgUrl.isNullOrBlank()) {
                                    pendingImageUrl = imgUrl
                                    showSaveImageDialog = true
                                    true
                                } else {
                                    saveImageFailed = true
                                    true
                                }
                            }
                            else -> false
                        }
                    }

                    // Load initial URL
                    loadUrl(currentUrl)
                }
            },
            update = { webView ->
                if (webViewRef == null) {
                    webViewRef = webView
                }
                if (webView.url.isNullOrEmpty()) {
                    webView.loadUrl(currentUrl)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

            if (showHome) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.98f))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Watchera",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        var homeQuery by remember { mutableStateOf("") }

                        OutlinedTextField(
                            value = homeQuery,
                            onValueChange = { homeQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            singleLine = true,
                            placeholder = { Text("ابحث أو أدخل رابطاً") },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        if (homeQuery.isNotBlank()) {
                                            navigateTo(homeQuery)
                                            showHome = false
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.ArrowForward, contentDescription = "ذهاب", modifier = Modifier.size(20.dp))
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = {
                                if (homeQuery.isNotBlank()) {
                                    navigateTo(homeQuery)
                                    showHome = false
                                }
                            }),
                            shape = RoundedCornerShape(26.dp)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AssistChip(
                                onClick = { navigateTo("https://www.google.com"); showHome = false },
                                label = { Text("Google") }
                            )
                            AssistChip(
                                onClick = { navigateTo("https://www.youtube.com"); showHome = false },
                                label = { Text("YouTube") }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ====================================================================
// Saved Images Grid (with aspect ratio toggle — B2)
// ====================================================================
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SavedImagesGrid(
    savedImages: List<SavedImageEntity>,
    onDeleteImage: (String) -> Unit,
    onBack: () -> Unit,
    onImageClick: (Int) -> Unit
) {
    // Default: real dimensions (square = false)
    var useSquareAspect by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("الصور المحفوظة", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                },
                actions = {
                    // Aspect ratio toggle
                    IconButton(onClick = { useSquareAspect = !useSquareAspect }) {
                        Icon(
                            if (useSquareAspect) Icons.Default.CropSquare else Icons.Default.FitScreen,
                            contentDescription = if (useSquareAspect) "عرض بالأبعاد الحقيقية" else "عرض مربع"
                        )
                    }
                }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        if (savedImages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "لا توجد صور محفوظة بعد",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        } else {
            if (useSquareAspect) {
                // === Mode A: Square grid (fixed aspect ratio) ===
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(savedImages, key = { it.id }) { image ->
                        ImageGridItem(
                            image = image,
                            aspectRatio = 1f,
                            contentScale = ContentScale.Crop,
                            onClick = { onImageClick(savedImages.indexOf(image)) },
                            onDeleteImage = onDeleteImage
                        )
                    }
                }
            } else {
                // === Mode B: Real dimensions (staggered grid, no fixed aspect) ===
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(minSize = 120.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalItemSpacing = 8.dp
                ) {
                    items(savedImages, key = { it.id }) { image ->
                        val index = savedImages.indexOf(image)
                        ImageGridItem(
                            image = image,
                            aspectRatio = null,  // natural height
                            contentScale = ContentScale.Fit,
                            onClick = { onImageClick(index) },
                            onDeleteImage = onDeleteImage
                        )
                    }
                }
            }
        }
    }
}

// ====================================================================
// Single image grid item (shared by both grid modes)
// ====================================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageGridItem(
    image: SavedImageEntity,
    aspectRatio: Float?,
    contentScale: ContentScale,
    onClick: () -> Unit,
    onDeleteImage: (String) -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .then(if (aspectRatio != null) Modifier.aspectRatio(aspectRatio) else Modifier)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showDeleteConfirm = true }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val file = File(image.localFilePath)
            if (file.exists()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(file)
                        .crossfade(200)
                        .build(),
                    contentDescription = "صورة محفوظة",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "غير متوفرة",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }

            // File size badge (bottom-right)
            if (image.fileSizeBytes > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = formatFileSize(image.fileSizeBytes),
                        fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("حذف الصورة") },
            text = { Text("هل أنت متأكد من حذف هذه الصورة؟\nلا يمكن التراجع عن هذا الإجراء.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteImage(image.id)
                    showDeleteConfirm = false
                }) {
                    Text("حذف", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

// ====================================================================
// Helper: format file size
// ====================================================================
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024f)
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024f * 1024f))
        else -> String.format("%.1f GB", bytes / (1024f * 1024f * 1024f))
    }
}

// ====================================================================
// Helper: detect whether typed text is a URL or a search query
// ====================================================================
private fun isLikelyUrl(text: String): Boolean {
    val t = text.trim()
    if (t.startsWith("http://") || t.startsWith("https://")) return true
    // contains a dot and no spaces, and looks like a host/path
    return t.contains(".") && !t.contains(" ") && Patterns.WEB_URL.matcher(t).matches()
}
