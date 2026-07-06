package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: MovieViewModel,
    onBackClick: () -> Unit
) {
    // ---- WebView state ----
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf("https://www.google.com") }
    var urlInput by remember { mutableStateOf(currentUrl) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var pageTitle by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    // ---- UI state ----
    var showToolsMenu by remember { mutableStateOf(false) }
    var showSavedImages by remember { mutableStateOf(false) }
    var showSaveImageDialog by remember { mutableStateOf(false) }
    var pendingImageUrl by remember { mutableStateOf("") }
    var saveImageFailed by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val savedImages by viewModel.savedImages.collectAsState()

    // ---- BackHandler: WebView back vs system back ----
    BackHandler(enabled = true) {
        if (showSavedImages) {
            showSavedImages = false
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
        val fullUrl = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "https://$trimmed"
        } else {
            trimmed
        }
        currentUrl = fullUrl
        urlInput = fullUrl
        webViewRef?.loadUrl(fullUrl)
    }

    // ---- Saved images mode ----
    if (showSavedImages) {
        SavedImagesGrid(
            savedImages = savedImages,
            onDeleteImage = { viewModel.deleteSavedImage(it) },
            onBack = { showSavedImages = false }
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
        topBar = {
            Column {
                // Address bar row
                Surface(
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Back (system / close)
                        IconButton(onClick = {
                            if (webViewRef?.canGoBack() == true) {
                                webViewRef?.goBack()
                            } else {
                                onBackClick()
                            }
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "رجوع"
                            )
                        }

                        // URL TextField
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            singleLine = true,
                            placeholder = {
                                Text(
                                    "ابحث أو أدخل رابطاً",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            },
                            textStyle = MaterialTheme.typography.bodySmall,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { navigateTo(urlInput) }),
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        )

                        // Tools menu (⋮)
                        Box {
                            IconButton(onClick = { showToolsMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "أدوات")
                            }
                            DropdownMenu(
                                expanded = showToolsMenu,
                                onDismissRequest = { showToolsMenu = false }
                            ) {
                                // Share current link
                                DropdownMenuItem(
                                    text = { Text("مشاركة الرابط") },
                                    onClick = {
                                        showToolsMenu = false
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, currentUrl)
                                        }
                                        context.startActivity(
                                            Intent.createChooser(shareIntent, "مشاركة الرابط")
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Share, contentDescription = null)
                                    }
                                )
                                // Saved images
                                DropdownMenuItem(
                                    text = { Text("الصور المحفوظة") },
                                    onClick = {
                                        showToolsMenu = false
                                        showSavedImages = true
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                                    }
                                )
                            }
                        }
                    }
                }

                // Navigation controls row
                Surface(
                    tonalElevation = 1.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Page title (centered) or loading indicator
                        if (pageTitle.isNotBlank()) {
                            Text(
                                text = pageTitle,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Back (page history)
                        IconButton(
                            onClick = { webViewRef?.goBack() },
                            enabled = canGoBack,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "الصفحة السابقة",
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Forward (page history)
                        IconButton(
                            onClick = { webViewRef?.goForward() },
                            enabled = canGoForward,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.ArrowForward,
                                contentDescription = "الصفحة التالية",
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Refresh / Stop
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
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "تحديث",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Spacer for symmetry
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        // ---- WebView ----
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    // WebSettings — exactly as specified in the architecture plan
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

                    // WebChromeClient
                    webChromeClient = object : WebChromeClient() {
                        override fun onReceivedTitle(view: WebView?, title: String?) {
                            super.onReceivedTitle(view, title)
                            title?.let { pageTitle = it }
                        }

                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            super.onProgressChanged(view, newProgress)
                            // Could show progress in the future
                        }
                    }

                    // ---- Long-press to save images ----
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
                                    true // Consume — prevent default context menu
                                } else {
                                    saveImageFailed = true
                                    true
                                }
                            }
                            else -> false // Let default behavior handle it
                        }
                    }

                    // Load initial URL
                    loadUrl(currentUrl)
                }
            },
            update = { webView ->
                // Store reference for BackHandler
                if (webViewRef == null) {
                    webViewRef = webView
                }
                // If URL was changed externally (e.g. navigateTo called), load it
                if (webView.url.isNullOrEmpty()) {
                    webView.loadUrl(currentUrl)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }
}

// ====================================================================
// Saved Images Grid (displayed inside BrowserScreen when toggled)
// ====================================================================
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SavedImagesGrid(
    savedImages: List<SavedImageEntity>,
    onDeleteImage: (String) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "الصور المحفوظة",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع"
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
                    var showDeleteConfirm by remember { mutableStateOf(false) }

                    Card(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .combinedClickable(
                                onClick = { /* Preview — future enhancement */ },
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
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                // File deleted but DB still has record — show placeholder
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
                                    Text(
                                        "حذف",
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
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
            }
        }
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
