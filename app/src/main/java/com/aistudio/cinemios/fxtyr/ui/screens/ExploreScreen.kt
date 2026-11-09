package com.aistudio.cinemios.fxtyr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aistudio.cinemios.fxtyr.ui.components.IosIcons
import com.aistudio.cinemios.fxtyr.ui.components.explore.ExploreFilterChip
import com.aistudio.cinemios.fxtyr.ui.components.explore.ExplorePageHeader
import com.aistudio.cinemios.fxtyr.ui.components.explore.ExplorePosterGrid
import com.aistudio.cinemios.fxtyr.ui.components.explore.ExploreSkeletonGrid
import com.aistudio.cinemios.fxtyr.ui.components.explore.ExploreStateMessage
import com.aistudio.cinemios.fxtyr.ui.theme.AppIcons
import com.aistudio.cinemios.fxtyr.ui.viewmodel.ExploreTab
import com.aistudio.cinemios.fxtyr.ui.viewmodel.ExploreViewModel

@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel,
    onBackClick: () -> Unit,
    onNavigateToMovieBoxDetails: (String, String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.trendingState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.ensureTrendingLoaded()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
    ) {
        ExplorePageHeader(
            title = "استكشف",
            subtitle = "الرائج على MovieBox الآن — أفلام ومسلسلات وأنمي",
            onBackClick = onBackClick,
            onRefresh = viewModel::refreshTrending,
            isRefreshing = state.isLoading
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(ExploreTab.entries, key = { it.key }) { tab ->
                ExploreFilterChip(
                    label = tab.label,
                    selected = state.tab == tab,
                    onClick = { viewModel.selectTrendingTab(tab) }
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
                    icon = IosIcons.Refresh,
                    title = "تعذر تحميل المحتوى",
                    message = state.error.orEmpty(),
                    actionLabel = "إعادة المحاولة",
                    onAction = viewModel::refreshTrending
                )
            }

            state.items.isEmpty() -> {
                ExploreStateMessage(
                    icon = AppIcons.Search,
                    title = "لا توجد نتائج",
                    message = "جرّب تبويبًا آخر أو حدّث الصفحة.",
                    actionLabel = "تحديث",
                    onAction = viewModel::refreshTrending
                )
            }

            else -> {
                ExplorePosterGrid(
                    items = state.items,
                    isLoadingMore = state.isLoadingMore,
                    hasMore = state.hasMore,
                    loadMoreError = state.error,
                    onLoadMore = viewModel::loadMoreTrending,
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
}
