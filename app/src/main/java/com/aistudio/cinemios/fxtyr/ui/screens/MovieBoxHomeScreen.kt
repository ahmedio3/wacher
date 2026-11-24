package com.aistudio.cinemios.fxtyr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aistudio.cinemios.fxtyr.data.local.RecentViewedItem
import com.aistudio.cinemios.fxtyr.ui.components.IosIcons
import com.aistudio.cinemios.fxtyr.ui.components.explore.ExplorePageHeader
import com.aistudio.cinemios.fxtyr.ui.components.explore.ExploreStateMessage
import com.aistudio.cinemios.fxtyr.ui.components.home.MovieBoxHomeRail
import com.aistudio.cinemios.fxtyr.ui.components.home.MovieBoxHomeRailsSkeleton
import com.aistudio.cinemios.fxtyr.ui.theme.AppIcons
import com.aistudio.cinemios.fxtyr.ui.viewmodel.MovieViewModel
import com.aistudio.cinemios.fxtyr.ui.viewmodel.RequestState

/**
 * Dedicated screen for the curated MovieBox `/home` rails.
 *
 * The rails used to live inside the main home screen, which made startup heavy.
 * They are now fetched lazily on the first visit and rendered with a
 * [LazyColumn] so only the visible rails are composed.
 */
@Composable
fun MovieBoxHomeScreen(
    viewModel: MovieViewModel,
    onBackClick: () -> Unit,
    onNavigateToMovieBoxDetails: (String, String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val homeState by viewModel.homeSections.collectAsState()
    val isRefreshingHome by viewModel.isRefreshingHome.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.ensureMovieBoxHomeLoaded()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
    ) {
        ExplorePageHeader(
            title = "مختارات MovieBox",
            subtitle = "أقسام منسّقة تُحمّل عند فتح هذه الصفحة فقط",
            onBackClick = onBackClick,
            onRefresh = viewModel::refreshMovieBoxHome,
            isRefreshing = isRefreshingHome
        )

        when (val state = homeState) {
            is RequestState.Success -> {
                val sections = remember(state.data) {
                    state.data
                        .filter { it.items.isNotEmpty() }
                        .distinctBy { it.title }
                }

                if (sections.isEmpty()) {
                    ExploreStateMessage(
                        icon = AppIcons.MovieBox,
                        title = "لا توجد أقسام حاليًا",
                        message = "لم تُرجع MovieBox أي أقسام لهذه الصفحة. جرّب التحديث بعد قليل.",
                        actionLabel = "تحديث",
                        onAction = viewModel::refreshMovieBoxHome
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp)
                    ) {
                        items(sections, key = { it.title }) { section ->
                            MovieBoxHomeRail(
                                section = section,
                                onItemClick = { item ->
                                    val mediaType = if (item.type == "series") "tv" else "movie"
                                    viewModel.recordRecentView(
                                        RecentViewedItem(
                                            id = item.subjectId,
                                            title = item.title,
                                            posterPath = item.posterUrl,
                                            mediaType = mediaType,
                                            year = item.year,
                                            rating = item.rating,
                                            isMovieBox = true
                                        )
                                    )
                                    onNavigateToMovieBoxDetails(
                                        item.subjectId,
                                        mediaType,
                                        item.title,
                                        item.posterUrl
                                    )
                                }
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                    }
                }
            }

            is RequestState.Error -> {
                ExploreStateMessage(
                    icon = IosIcons.Refresh,
                    title = "تعذر تحميل الأقسام",
                    message = state.message,
                    actionLabel = "إعادة المحاولة",
                    onAction = viewModel::refreshMovieBoxHome
                )
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 8.dp)
                ) {
                    MovieBoxHomeRailsSkeleton()
                }
            }
        }
    }
}
