package com.aistudio.cinemios.fxtyr.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.cinemios.fxtyr.data.remote.moviebox.models.SearchResult
import com.aistudio.cinemios.fxtyr.data.remote.moviebox.repository.MovieBoxRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ExploreTab(val key: String, val label: String) {
    ALL("all", "الكل"),
    MOVIES("movie", "أفلام"),
    SERIES("tv", "مسلسلات"),
    ANIME("anime", "أنمي")
}

data class TrendingUiState(
    val tab: ExploreTab = ExploreTab.ALL,
    val items: List<SearchResult> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null
)

data class AdultUiState(
    val items: List<SearchResult> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
    val type: String = "all",
    val sort: String = "random",
    val keywords: List<String> = emptyList(),
    val selectedKeywords: List<String> = emptyList(),
    val isLoadingKeywords: Boolean = false,
    val keywordsError: String? = null,
    val keywordLimitReached: Boolean = false
)

class ExploreViewModel(
    private val repository: MovieBoxRepository
) : ViewModel() {

    private val _trendingState = MutableStateFlow(TrendingUiState())
    val trendingState: StateFlow<TrendingUiState> = _trendingState.asStateFlow()

    private val _adultState = MutableStateFlow(AdultUiState())
    val adultState: StateFlow<AdultUiState> = _adultState.asStateFlow()

    private var trendingPage = 0
    private var trendingRequestId = 0

    private var adultRequestId = 0
    private var keywordsRequestId = 0

    // ── Trending ─────────────────────────────────────────────────────────────

    fun ensureTrendingLoaded() {
        if (_trendingState.value.items.isEmpty() && !_trendingState.value.isLoading) {
            startTrending(reset = true)
        }
    }

    fun selectTrendingTab(tab: ExploreTab) {
        if (_trendingState.value.tab == tab) return
        _trendingState.value = TrendingUiState(tab = tab)
        startTrending(reset = true)
    }

    fun refreshTrending() {
        startTrending(reset = true, forceRefresh = true)
    }

    fun loadMoreTrending() {
        startTrending(reset = false)
    }

    private fun startTrending(reset: Boolean, forceRefresh: Boolean = false) {
        val snapshot = _trendingState.value
        if (!reset && (snapshot.isLoading || snapshot.isLoadingMore || !snapshot.hasMore)) return

        val tab = snapshot.tab
        val page = if (reset) 1 else trendingPage + 1
        val requestId = ++trendingRequestId

        _trendingState.update {
            if (reset) {
                it.copy(
                    items = emptyList(),
                    isLoading = true,
                    isLoadingMore = false,
                    hasMore = true,
                    error = null
                )
            } else {
                it.copy(isLoadingMore = true, error = null)
            }
        }

        viewModelScope.launch {
            val result = repository.trending(
                tab = tab.key,
                page = page,
                safeMode = true,
                limit = TRENDING_PAGE_SIZE,
                forceRefresh = forceRefresh
            )

            if (requestId != trendingRequestId || _trendingState.value.tab != tab) return@launch

            result.onSuccess { fetched ->
                val distinct = fetched
                    .distinctBy { it.subjectId }
                    .filter { it.subjectId.isNotBlank() }
                val existingIds = _trendingState.value.items.map { it.subjectId }.toSet()
                val added = distinct.filter { it.subjectId !in existingIds }
                val merged = _trendingState.value.items + added

                trendingPage = page
                _trendingState.update {
                    it.copy(
                        items = merged,
                        isLoading = false,
                        isLoadingMore = false,
                        hasMore = distinct.isNotEmpty() && added.isNotEmpty(),
                        error = null
                    )
                }
            }.onFailure { error ->
                _trendingState.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = friendlyError(error)
                    )
                }
            }
        }
    }

    // ── Adult discovery ──────────────────────────────────────────────────────

    fun startAdult() {
        ensureAdultKeywordsLoaded()
        if (_adultState.value.items.isEmpty() && !_adultState.value.isLoading) {
            startAdultRequest(reset = true, force = true)
        }
    }

    fun refreshAdult() {
        startAdultRequest(reset = true, force = true)
    }

    fun loadMoreAdult() {
        startAdultRequest(reset = false)
    }

    fun setAdultType(type: String) {
        if (_adultState.value.type == type) return
        _adultState.update { it.copy(type = type, items = emptyList(), hasMore = true) }
        startAdultRequest(reset = true, force = true)
    }

    fun setAdultSort(sort: String) {
        if (_adultState.value.sort == sort) return
        _adultState.update { it.copy(sort = sort, items = emptyList(), hasMore = true) }
        startAdultRequest(reset = true, force = true)
    }

    fun toggleAdultKeyword(keyword: String) {
        _adultState.update { state ->
            val selected = state.selectedKeywords
            when {
                keyword in selected -> state.copy(
                    selectedKeywords = selected - keyword,
                    keywordLimitReached = false
                )
                selected.size >= ADULT_MAX_KEYWORDS -> state.copy(keywordLimitReached = true)
                else -> state.copy(
                    selectedKeywords = selected + keyword,
                    keywordLimitReached = false
                )
            }
        }
    }

    fun clearAdultKeywords() {
        _adultState.update {
            it.copy(selectedKeywords = emptyList(), keywordLimitReached = false)
        }
    }

    fun applyAdultFilters() {
        startAdultRequest(reset = true, force = true)
    }

    fun ensureAdultKeywordsLoaded() {
        val state = _adultState.value
        if (state.keywords.isNotEmpty() || state.isLoadingKeywords) return

        val requestId = ++keywordsRequestId
        _adultState.update { it.copy(isLoadingKeywords = true, keywordsError = null) }

        viewModelScope.launch {
            val result = repository.adultKeywords()
            if (requestId != keywordsRequestId) return@launch

            result.onSuccess { keywords ->
                _adultState.update {
                    it.copy(
                        keywords = keywords,
                        isLoadingKeywords = false,
                        keywordsError = null
                    )
                }
            }.onFailure { error ->
                _adultState.update {
                    it.copy(
                        isLoadingKeywords = false,
                        keywordsError = friendlyError(error)
                    )
                }
            }
        }
    }

    private fun startAdultRequest(reset: Boolean, force: Boolean = false) {
        val snapshot = _adultState.value
        if (!force && (snapshot.isLoading || snapshot.isLoadingMore)) return
        if (!reset && !snapshot.hasMore) return

        val type = snapshot.type
        val sort = snapshot.sort
        val queries = snapshot.selectedKeywords.joinToString(",")
        val requestId = ++adultRequestId

        _adultState.update {
            it.copy(
                items = if (reset) emptyList() else it.items,
                isLoading = reset,
                isLoadingMore = !reset,
                hasMore = if (reset) true else it.hasMore,
                error = null,
                keywordLimitReached = false
            )
        }

        viewModelScope.launch {
            val result = repository.adult(
                type = type,
                queries = queries,
                sort = sort,
                limit = ADULT_PAGE_SIZE
            )

            val current = _adultState.value
            if (requestId != adultRequestId ||
                current.type != type ||
                current.sort != sort ||
                current.selectedKeywords.joinToString(",") != queries
            ) return@launch

            result.onSuccess { fetched ->
                val distinct = fetched
                    .distinctBy { it.subjectId }
                    .filter { it.subjectId.isNotBlank() }
                val existingIds = _adultState.value.items.map { it.subjectId }.toSet()
                val added = distinct.filter { it.subjectId !in existingIds }
                val merged = _adultState.value.items + added

                _adultState.update {
                    it.copy(
                        items = merged,
                        isLoading = false,
                        isLoadingMore = false,
                        hasMore = distinct.isNotEmpty() && (reset || added.isNotEmpty()),
                        error = null
                    )
                }
            }.onFailure { error ->
                _adultState.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = friendlyError(error)
                    )
                }
            }
        }
    }

    private fun friendlyError(error: Throwable): String {
        val raw = error.message.orEmpty()
        return when {
            raw.contains("429") -> "طلبات كثيرة الآن. انتظر لحظة ثم حاول مجدداً."
            raw.contains("timeout", ignoreCase = true) -> "انتهت مهلة الاتصال. حاول مرة أخرى."
            else -> "تعذر تحميل المحتوى. تحقق من اتصالك بالإنترنت."
        }
    }

    companion object {
        const val ADULT_MAX_KEYWORDS = 10
        private const val TRENDING_PAGE_SIZE = 30
        private const val ADULT_PAGE_SIZE = 30
    }
}
