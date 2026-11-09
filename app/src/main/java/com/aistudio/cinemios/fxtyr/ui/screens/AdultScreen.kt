package com.aistudio.cinemios.fxtyr.ui.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.cinemios.fxtyr.ui.components.IosIcons
import com.aistudio.cinemios.fxtyr.ui.components.explore.ExploreFilterChip
import com.aistudio.cinemios.fxtyr.ui.components.explore.ExplorePageHeader
import com.aistudio.cinemios.fxtyr.ui.components.explore.ExplorePosterGrid
import com.aistudio.cinemios.fxtyr.ui.components.explore.ExploreSkeletonGrid
import com.aistudio.cinemios.fxtyr.ui.components.explore.ExploreStateMessage
import com.aistudio.cinemios.fxtyr.ui.components.explore.FloatingIconAction
import com.aistudio.cinemios.fxtyr.ui.theme.PaletteMutedRed
import com.aistudio.cinemios.fxtyr.ui.viewmodel.AdultUiState
import com.aistudio.cinemios.fxtyr.ui.viewmodel.ExploreViewModel

private const val ADULT_CONSENT_KEY = "adult_content_consent"

@Composable
fun AdultScreen(
    viewModel: ExploreViewModel,
    onBackClick: () -> Unit,
    onNavigateToMovieBoxDetails: (String, String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("watchera_prefs", Context.MODE_PRIVATE)
    }
    var hasConsented by rememberSaveable {
        mutableStateOf(prefs.getBoolean(ADULT_CONSENT_KEY, false))
    }
    val state by viewModel.adultState.collectAsState()

    var showKeywordsSheet by rememberSaveable { mutableStateOf(false) }
    var showSortSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(hasConsented) {
        if (hasConsented) {
            viewModel.startAdult()
        }
    }

    LaunchedEffect(showKeywordsSheet) {
        if (showKeywordsSheet) {
            viewModel.ensureAdultKeywordsLoaded()
        }
    }

    if (!hasConsented) {
        AdultConsentGate(
            onBackClick = onBackClick,
            onConfirm = {
                prefs.edit().putBoolean(ADULT_CONSENT_KEY, true).apply()
                hasConsented = true
            },
            modifier = modifier
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
    ) {
        ExplorePageHeader(
            title = "محتوى +18",
            subtitle = "للبالغين فقط • اختر الكلمات المفتاحية وطريقة الترتيب",
            onBackClick = onBackClick,
            onRefresh = viewModel::refreshAdult,
            isRefreshing = state.isLoading,
            badge = { AdultBadge() }
        )

        Spacer(modifier = Modifier.height(14.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                ExploreFilterChip(
                    label = "الكل",
                    selected = state.type == "all",
                    onClick = { viewModel.setAdultType("all") }
                )
            }
            item {
                ExploreFilterChip(
                    label = "أفلام",
                    selected = state.type == "movie",
                    onClick = { viewModel.setAdultType("movie") }
                )
            }
            item {
                ExploreFilterChip(
                    label = "مسلسلات",
                    selected = state.type == "series",
                    onClick = { viewModel.setAdultType("series") }
                )
            }
            item {
                ExploreFilterChip(
                    label = sortLabel(state.sort),
                    selected = state.sort != "random",
                    onClick = { showSortSheet = true },
                    icon = IosIcons.Sort,
                    showChevron = true
                )
            }
            item {
                ExploreFilterChip(
                    label = if (state.selectedKeywords.isEmpty()) {
                        "الكلمات المفتاحية"
                    } else {
                        "${state.selectedKeywords.size} كلمات"
                    },
                    selected = state.selectedKeywords.isNotEmpty(),
                    onClick = { showKeywordsSheet = true },
                    icon = IosIcons.Tag
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            state.isLoading && state.items.isEmpty() -> {
                ExploreSkeletonGrid()
            }

            state.error != null && state.items.isEmpty() -> {
                ExploreStateMessage(
                    icon = IosIcons.Lock,
                    title = "تعذر تحميل المحتوى",
                    message = state.error.orEmpty(),
                    actionLabel = "إعادة المحاولة",
                    onAction = viewModel::refreshAdult
                )
            }

            state.items.isEmpty() -> {
                ExploreStateMessage(
                    icon = IosIcons.Lock,
                    title = "لا توجد نتائج",
                    message = "جرّب كلمات مفتاحية أخرى أو غيّر طريقة الترتيب.",
                    actionLabel = "تحديث",
                    onAction = viewModel::refreshAdult
                )
            }

            else -> {
                ExplorePosterGrid(
                    items = state.items,
                    isLoadingMore = state.isLoadingMore,
                    hasMore = state.hasMore,
                    loadMoreError = state.error,
                    onLoadMore = viewModel::loadMoreAdult,
                    onClick = { item ->
                        onNavigateToMovieBoxDetails(
                            item.subjectId,
                            if (item.type == "series") "tv" else "movie",
                            item.title,
                            item.posterUrl
                        )
                    }
                )
            }
        }
    }

    if (showSortSheet) {
        AdultSortSheet(
            selected = state.sort,
            onSelect = { sort ->
                viewModel.setAdultSort(sort)
                showSortSheet = false
            },
            onDismiss = { showSortSheet = false }
        )
    }

    if (showKeywordsSheet) {
        AdultKeywordsSheet(
            state = state,
            onToggle = viewModel::toggleAdultKeyword,
            onClear = viewModel::clearAdultKeywords,
            onApply = {
                viewModel.applyAdultFilters()
                showKeywordsSheet = false
            },
            onRetry = viewModel::ensureAdultKeywordsLoaded,
            onDismiss = { showKeywordsSheet = false }
        )
    }
}

@Composable
private fun AdultBadge(modifier: Modifier = Modifier) {
    Text(
        text = "18+",
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(PaletteMutedRed.copy(alpha = 0.13f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        color = PaletteMutedRed,
        fontSize = 11.sp,
        fontWeight = FontWeight.Black
    )
}

private fun sortLabel(sort: String): String = when (sort) {
    "newest" -> "الأحدث"
    "rating" -> "الأعلى تقييماً"
    else -> "عشوائي"
}

@Composable
private fun AdultConsentGate(
    onBackClick: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FloatingIconAction(
                icon = IosIcons.ChevronRight,
                contentDescription = "رجوع",
                onClick = onBackClick
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 30.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(78.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(PaletteMutedRed.copy(alpha = 0.12f))
                    .border(
                        width = 1.dp,
                        color = PaletteMutedRed.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = IosIcons.Lock,
                    contentDescription = null,
                    tint = PaletteMutedRed,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "محتوى للبالغين",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "هذا القسم يعرض محتوى +18 من MovieBox. بالمتابعة أنت تؤكد أن عمرك 18 عامًا أو أكثر.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                fontSize = 13.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "أنا أبلغ 18 عامًا أو أكثر",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = onBackClick) {
                Text(
                    text = "العودة إلى الإعدادات",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdultSortSheet(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "ترتيب النتائج",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "اختر طريقة ترتيب المحتوى",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(4.dp))

            listOf(
                "random" to "عشوائي",
                "newest" to "الأحدث",
                "rating" to "الأعلى تقييماً"
            ).forEach { (value, label) ->
                AdultSortRow(
                    label = label,
                    selected = selected == value,
                    onClick = { onSelect(value) }
                )
            }
        }
    }
}

@Composable
private fun AdultSortRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)

    Surface(
        shape = shape,
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (selected) {
                Icon(
                    imageVector = IosIcons.CheckmarkCircle,
                    contentDescription = "محدد",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AdultKeywordsSheet(
    state: AdultUiState,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    onApply: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "الكلمات المفتاحية",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "اختر حتى ${ExploreViewModel.ADULT_MAX_KEYWORDS} كلمات لتخصيص النتائج",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
                if (state.selectedKeywords.isNotEmpty()) {
                    TextButton(onClick = onClear) {
                        Text(
                            text = "مسح",
                            color = PaletteMutedRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            when {
                state.isLoadingKeywords && state.keywords.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }

                state.keywordsError != null && state.keywords.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.keywordsError,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        TextButton(onClick = onRetry) {
                            Text("إعادة المحاولة", fontSize = 12.sp)
                        }
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 330.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            state.keywords.forEach { keyword ->
                                val isSelected = keyword in state.selectedKeywords
                                KeywordChip(
                                    label = keyword,
                                    selected = isSelected,
                                    enabled = isSelected ||
                                        state.selectedKeywords.size < ExploreViewModel.ADULT_MAX_KEYWORDS,
                                    onClick = { onToggle(keyword) }
                                )
                            }
                        }
                    }
                }
            }

            if (state.keywordLimitReached) {
                Text(
                    text = "وصلت إلى الحد الأقصى (${ExploreViewModel.ADULT_MAX_KEYWORDS} كلمات).",
                    color = PaletteMutedRed,
                    fontSize = 11.sp
                )
            }

            if (state.selectedKeywords.isNotEmpty()) {
                Text(
                    text = "${state.selectedKeywords.size} / ${ExploreViewModel.ADULT_MAX_KEYWORDS} محددة",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                    fontSize = 11.sp
                )
            }

            Button(
                onClick = onApply,
                shape = RoundedCornerShape(15.dp),
                enabled = !state.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = "تطبيق",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun KeywordChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onBackground.copy(alpha = if (enabled) 0.68f else 0.28f)
    }

    Surface(
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        },
        border = if (selected) {
            null
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        },
        modifier = Modifier
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (selected) {
                Icon(
                    imageVector = IosIcons.Checkmark,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(10.dp)
                )
            }
            Text(
                text = label,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}
