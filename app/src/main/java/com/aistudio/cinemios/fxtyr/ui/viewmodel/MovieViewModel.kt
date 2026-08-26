package com.aistudio.cinemios.fxtyr.ui.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aistudio.cinemios.fxtyr.data.local.DownloadEntity
import com.aistudio.cinemios.fxtyr.data.local.EpisodeWatchStatusEntity
import com.aistudio.cinemios.fxtyr.data.local.MovieDatabase
import com.aistudio.cinemios.fxtyr.data.local.SavedImageEntity
import com.aistudio.cinemios.fxtyr.data.local.ActivityLogEntity
import com.aistudio.cinemios.fxtyr.data.local.SubtitleDownloadEntity
import com.aistudio.cinemios.fxtyr.data.local.WatchlistEntity
import com.aistudio.cinemios.fxtyr.data.remote.*
import com.aistudio.cinemios.fxtyr.data.repository.MovieRepository
import com.aistudio.cinemios.fxtyr.data.remote.moviebox.repository.MovieBoxRepository
import com.aistudio.cinemios.fxtyr.auth.ActivityLogManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

data class SubtitleBatchGroup(
    val batchId: String,
    val title: String,
    val fileName: String,
    val tmdbId: String,
    val mediaType: String,
    val count: Int,
    val items: List<SubtitleDownloadEntity>
)

sealed interface RequestState<out T> {
    object Idle : RequestState<Nothing>
    object Loading : RequestState<Nothing>
    data class Success<out T>(val data: T) : RequestState<T>
    data class Error(val message: String) : RequestState<Nothing>
}

class MovieViewModel(
    application: Application,
    val movieBoxRepository: MovieBoxRepository
) : AndroidViewModel(application) {

    private val repository: MovieRepository

    // Settings Configuration
    private val sharedPrefs = application.getSharedPreferences("watchera_prefs", android.content.Context.MODE_PRIVATE)

    // Arabic vs English posters
    private val _isArabicPosters = MutableStateFlow(sharedPrefs.getBoolean("arabic_posters", false))
    val isArabicPosters: StateFlow<Boolean> = _isArabicPosters.asStateFlow()

    // Default watch status (used by tap in DetailScreen)
    private val _defaultWatchStatus = MutableStateFlow(sharedPrefs.getString("default_watch_status", "PLAN_TO_WATCH") ?: "PLAN_TO_WATCH")
    val defaultWatchStatus: StateFlow<String> = _defaultWatchStatus.asStateFlow()

    fun setDefaultWatchStatus(status: String) {
        sharedPrefs.edit().putString("default_watch_status", status).apply()
        _defaultWatchStatus.value = status
    }

    // Sync state
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // Dark mode toggle
    private val _isDarkMode = MutableStateFlow(sharedPrefs.getBoolean("dark_mode", false))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun setDarkMode(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("dark_mode", enabled).apply()
        _isDarkMode.value = enabled
    }

    fun setArabicPosters(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("arabic_posters", enabled).apply()
        _isArabicPosters.value = enabled
        // Clear detail caches so they refresh with new language
        _movieDetails.value = emptyMap()
        _tvDetails.value = emptyMap()
        _seasonDetails.value = emptyMap()
        _movieSimilar.value = emptyMap()
        _movieRecommendations.value = emptyMap()
        _tvSimilar.value = emptyMap()
        _tvRecommendations.value = emptyMap()
        if (_searchQuery.value.isNotBlank()) searchMedia(_searchQuery.value)
        fetchHomeContent()
    }

    private val currentLang: String get() = if (_isArabicPosters.value) "ar" else "en"

    // Reactively observe local database Watchlist and Downloads
    val watchlist: StateFlow<List<WatchlistEntity>>
    val downloads: StateFlow<List<DownloadEntity>>
    val subtitleDownloads: StateFlow<List<SubtitleDownloadEntity>>
    val subtitleBatchGroups: StateFlow<List<SubtitleBatchGroup>>
    val savedImages: StateFlow<List<SavedImageEntity>>

    // Activity/History log
    val activityLogs: StateFlow<List<ActivityLogEntity>>

    // Episode watch tracking — MUST cache to prevent infinite recomposition loop
    private val episodeStatusMap = mutableMapOf<String, StateFlow<List<EpisodeWatchStatusEntity>>>()
    fun getEpisodeWatchStatusForSeason(tmdbId: String, season: Int): StateFlow<List<EpisodeWatchStatusEntity>> {
        val key = "$tmdbId-$season"
        return episodeStatusMap.getOrPut(key) {
            repository.getEpisodeWatchStatusForSeason(tmdbId, season)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        }
    }

    val watchedCountMap = mutableMapOf<String, StateFlow<Int>>()
    fun getWatchedCountForTvShow(tmdbId: String): StateFlow<Int> {
        return watchedCountMap.getOrPut(tmdbId) {
            repository.getWatchedCountForTvShow(tmdbId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
        }
    }

    // Home items states
    private val _popularMovies = MutableStateFlow<RequestState<List<TmdbMediaItem>>>(RequestState.Idle)
    val popularMovies: StateFlow<RequestState<List<TmdbMediaItem>>> = _popularMovies.asStateFlow()

    private val _popularTvShows = MutableStateFlow<RequestState<List<TmdbMediaItem>>>(RequestState.Idle)
    val popularTvShows: StateFlow<RequestState<List<TmdbMediaItem>>> = _popularTvShows.asStateFlow()

    // Search query and results State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<RequestState<List<TmdbMediaItem>>>(RequestState.Idle)
    val searchResults: StateFlow<RequestState<List<TmdbMediaItem>>> = _searchResults.asStateFlow()

    // Cache details states to prevent redundant network calls when reloading
    private val _movieDetails = MutableStateFlow<Map<Int, RequestState<TmdbMovieDetails>>>(emptyMap())
    val movieDetails: StateFlow<Map<Int, RequestState<TmdbMovieDetails>>> = _movieDetails.asStateFlow()

    private val _tvDetails = MutableStateFlow<Map<Int, RequestState<TmdbTvDetails>>>(emptyMap())
    val tvDetails: StateFlow<Map<Int, RequestState<TmdbTvDetails>>> = _tvDetails.asStateFlow()

    private val _seasonDetails = MutableStateFlow<Map<String, RequestState<TmdbSeasonDetails>>>(emptyMap())
    val seasonDetails: StateFlow<Map<String, RequestState<TmdbSeasonDetails>>> = _seasonDetails.asStateFlow()

    private val _movieCertifications = MutableStateFlow<Map<Int, RequestState<String>>>(emptyMap())
    val movieCertifications: StateFlow<Map<Int, RequestState<String>>> = _movieCertifications.asStateFlow()

    private val _tvContentRatings = MutableStateFlow<Map<Int, RequestState<String>>>(emptyMap())
    val tvContentRatings: StateFlow<Map<Int, RequestState<String>>> = _tvContentRatings.asStateFlow()

    private val _movieSimilar = MutableStateFlow<Map<Int, RequestState<List<TmdbMediaItem>>>>(emptyMap())
    val movieSimilar: StateFlow<Map<Int, RequestState<List<TmdbMediaItem>>>> = _movieSimilar.asStateFlow()

    private val _movieRecommendations = MutableStateFlow<Map<Int, RequestState<List<TmdbMediaItem>>>>(emptyMap())
    val movieRecommendations: StateFlow<Map<Int, RequestState<List<TmdbMediaItem>>>> = _movieRecommendations.asStateFlow()

    private val _tvSimilar = MutableStateFlow<Map<Int, RequestState<List<TmdbMediaItem>>>>(emptyMap())
    val tvSimilar: StateFlow<Map<Int, RequestState<List<TmdbMediaItem>>>> = _tvSimilar.asStateFlow()

    private val _tvRecommendations = MutableStateFlow<Map<Int, RequestState<List<TmdbMediaItem>>>>(emptyMap())
    val tvRecommendations: StateFlow<Map<Int, RequestState<List<TmdbMediaItem>>>> = _tvRecommendations.asStateFlow()

    private val _trendingMovies = MutableStateFlow<RequestState<List<TmdbMediaItem>>>(RequestState.Idle)
    val trendingMovies: StateFlow<RequestState<List<TmdbMediaItem>>> = _trendingMovies.asStateFlow()

    private val _trendingTv = MutableStateFlow<RequestState<List<TmdbMediaItem>>>(RequestState.Idle)
    val trendingTv: StateFlow<RequestState<List<TmdbMediaItem>>> = _trendingTv.asStateFlow()

    private val _topRatedMovies = MutableStateFlow<RequestState<List<TmdbMediaItem>>>(RequestState.Idle)
    val topRatedMovies: StateFlow<RequestState<List<TmdbMediaItem>>> = _topRatedMovies.asStateFlow()

    private val _topRatedTv = MutableStateFlow<RequestState<List<TmdbMediaItem>>>(RequestState.Idle)
    val topRatedTv: StateFlow<RequestState<List<TmdbMediaItem>>> = _topRatedTv.asStateFlow()

    private val _nowPlayingMovies = MutableStateFlow<RequestState<List<TmdbMediaItem>>>(RequestState.Idle)
    val nowPlayingMovies: StateFlow<RequestState<List<TmdbMediaItem>>> = _nowPlayingMovies.asStateFlow()

    private val _onTheAirTv = MutableStateFlow<RequestState<List<TmdbMediaItem>>>(RequestState.Idle)
    val onTheAirTv: StateFlow<RequestState<List<TmdbMediaItem>>> = _onTheAirTv.asStateFlow()

    private val _downloadErrorDetails = MutableStateFlow<String?>(null)
    val downloadErrorDetails: StateFlow<String?> = _downloadErrorDetails.asStateFlow()

    private val _showDownloadErrorDialog = MutableStateFlow(false)
    val showDownloadErrorDialog: StateFlow<Boolean> = _showDownloadErrorDialog.asStateFlow()

    data class BackgroundQueueItem(
        val downloadId: String,
        val mediaId: String,
        val videoUrl: String,
        val headers: Map<String, String>?,
        val quality: String
    )

    private val _backgroundScrapeQueue = MutableStateFlow<BackgroundQueueItem?>(null)
    val backgroundScrapeQueue: StateFlow<BackgroundQueueItem?> = _backgroundScrapeQueue.asStateFlow()

    fun completeBackgroundScrape(queueItem: BackgroundQueueItem, customUrl: String) {
        viewModelScope.launch {
            _backgroundScrapeQueue.value = null
            triggerNetworkDownload(queueItem.downloadId, queueItem.quality, customUrl, queueItem.headers)
        }
    }

    fun failedBackgroundScrape(queueItem: BackgroundQueueItem, reason: String) {
        viewModelScope.launch {
            _backgroundScrapeQueue.value = null
            val dbg = """
                [تفاصيل تشخيص فشل التحميل المسبق للرابط للبث المباشر - من الخارج]
                * رقم التحميل (ID): ${queueItem.downloadId}
                * السبب: $reason
                * الرجاء التأكد من الاتصال أو المحاولة باختيار مصدر آخر.
            """.trimIndent()
            _downloadErrorDetails.value = dbg
            _showDownloadErrorDialog.value = true
        }
    }

    fun dismissDownloadErrorDialog() {
        _showDownloadErrorDialog.value = false
    }

    init {
        val database = MovieDatabase.getDatabase(application)
        repository = MovieRepository(database.movieDao)
        
        watchlist = repository.watchlist
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        downloads = repository.downloads
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        subtitleDownloads = repository.subtitleDownloads
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        subtitleBatchGroups = repository.subtitleDownloads
            .map { list ->
                list.groupBy { it.batchId }.map { (batchId, items) ->
                    SubtitleBatchGroup(
                        batchId = batchId,
                        title = items.first().title,
                        fileName = items.first().fileName,
                        tmdbId = items.first().tmdbId,
                        mediaType = items.first().mediaType,
                        count = items.size,
                        items = items.sortedBy { it.episode }
                    )
                }.sortedByDescending { group -> group.items.maxOf { it.downloadedAt } }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        savedImages = repository.savedImages
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        activityLogs = if (uid != null) {
            ActivityLogManager.getLogs(uid)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        } else {
            emptyFlow<List<ActivityLogEntity>>()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        }

        // Fetch Home content on startup
        fetchHomeContent()

        // Debounced auto-search: waits 300ms after user stops typing, min 3 chars
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .filter { it.length >= 3 }
                .distinctUntilChanged()
                .collect { query ->
                    _searchResults.value = RequestState.Loading
                    if (_isMovieBoxSearchMode.value) {
                        searchMovieBox(query.trim())
                    } else {
                        searchMedia(query.trim())
                    }
                }
        }
    }

    private val _isMovieBoxSearchMode = MutableStateFlow(false)
    val isMovieBoxSearchMode: StateFlow<Boolean> = _isMovieBoxSearchMode.asStateFlow()

    private val _movieBoxSearchResults = MutableStateFlow<RequestState<List<com.aistudio.cinemios.fxtyr.data.remote.moviebox.models.SearchResult>>>(RequestState.Idle)
    val movieBoxSearchResults: StateFlow<RequestState<List<com.aistudio.cinemios.fxtyr.data.remote.moviebox.models.SearchResult>>> = _movieBoxSearchResults.asStateFlow()

    fun updateSearchMode(isMovieBox: Boolean) {
        _isMovieBoxSearchMode.value = isMovieBox
    }

    fun setSearchQueryOnly(query: String) {
        _searchQuery.value = query
        if (query.isEmpty()) {
            _searchResults.value = RequestState.Idle
            _movieBoxSearchResults.value = RequestState.Idle
        }
    }

    fun triggerSearch() {
        val query = _searchQuery.value.trim()
        if (query.isEmpty()) return
        _searchResults.value = RequestState.Loading
        if (_isMovieBoxSearchMode.value) {
            searchMovieBox(query)
        } else {
            searchMedia(query)
        }
    }
    
    private fun searchMovieBox(query: String) {
        viewModelScope.launch {
            _movieBoxSearchResults.value = RequestState.Loading
            try {
                val response = movieBoxRepository.search(query)
                if (response.isSuccess) {
                    _movieBoxSearchResults.value = RequestState.Success(response.getOrNull() ?: emptyList())
                } else {
                    _movieBoxSearchResults.value = RequestState.Error(response.exceptionOrNull()?.message ?: "Unknown error")
                }
            } catch (e: Exception) {
                _movieBoxSearchResults.value = RequestState.Error(e.localizedMessage ?: "Unknown error")
            }
        }
    }

    fun fetchHomeContent() {
        viewModelScope.launch {
            // Load in parallel
            launch { fetchPopularMovies() }
            launch { fetchPopularTvShows() }
            launch { fetchTrendingMovies() }
            launch { fetchTrendingTv() }
            launch { fetchTopRatedMovies() }
            launch { fetchTopRatedTv() }
            launch { fetchNowPlayingMovies() }
            launch { fetchOnTheAirTv() }
        }
    }

    private suspend fun fetchPopularMovies() {
        _popularMovies.value = RequestState.Loading
        try {
            val response = repository.getPopularMovies(language = currentLang)
            val list = response.results?.map { it.copy(mediaType = "movie") } ?: emptyList()
            _popularMovies.value = RequestState.Success(list)
        } catch (e: Exception) {
            _popularMovies.value = RequestState.Error(e.localizedMessage ?: "حدث خطأ غير معروف")
        }
    }

    private suspend fun fetchPopularTvShows() {
        _popularTvShows.value = RequestState.Loading
        try {
            val response = repository.getPopularTvShows(language = currentLang)
            val list = response.results?.map { it.copy(mediaType = "tv") } ?: emptyList()
            _popularTvShows.value = RequestState.Success(list)
        } catch (e: Exception) {
            _popularTvShows.value = RequestState.Error(e.localizedMessage ?: "حدث خطأ غير معروف")
        }
    }

    suspend fun searchDirect(query: String) = repository.searchMulti(query, language = currentLang)

    private fun searchMedia(query: String) {
        viewModelScope.launch {
            _searchResults.value = RequestState.Loading
            try {
                val response = repository.searchMulti(query, language = currentLang)
                // Filter content with valid titles/posters and check types
                val filtered = response.results?.filter {
                    (it.mediaType == "movie" || it.mediaType == "tv") &&
                    !(it.title.isNullOrEmpty() && it.name.isNullOrEmpty())
                } ?: emptyList()
                _searchResults.value = RequestState.Success(filtered)
            } catch (e: Exception) {
                _searchResults.value = RequestState.Error(e.localizedMessage ?: "فشل البحث، حاول مرة أخرى")
            }
        }
    }

    private suspend fun fetchTrendingMovies() {
        _trendingMovies.value = RequestState.Loading
        try {
            val response = repository.getTrendingMovies(language = currentLang)
            val list = response.results?.map { it.copy(mediaType = "movie") } ?: emptyList()
            _trendingMovies.value = RequestState.Success(list)
        } catch (e: Exception) {
            _trendingMovies.value = RequestState.Error(e.localizedMessage ?: "خطأ")
        }
    }

    private suspend fun fetchTrendingTv() {
        _trendingTv.value = RequestState.Loading
        try {
            val response = repository.getTrendingTv(language = currentLang)
            val list = response.results?.map { it.copy(mediaType = "tv") } ?: emptyList()
            _trendingTv.value = RequestState.Success(list)
        } catch (e: Exception) {
            _trendingTv.value = RequestState.Error(e.localizedMessage ?: "خطأ")
        }
    }

    private suspend fun fetchTopRatedMovies() {
        _topRatedMovies.value = RequestState.Loading
        try {
            val response = repository.getTopRatedMovies(language = currentLang)
            val list = response.results?.map { it.copy(mediaType = "movie") } ?: emptyList()
            _topRatedMovies.value = RequestState.Success(list)
        } catch (e: Exception) {
            _topRatedMovies.value = RequestState.Error(e.localizedMessage ?: "خطأ")
        }
    }

    private suspend fun fetchTopRatedTv() {
        _topRatedTv.value = RequestState.Loading
        try {
            val response = repository.getTopRatedTv(language = currentLang)
            val list = response.results?.map { it.copy(mediaType = "tv") } ?: emptyList()
            _topRatedTv.value = RequestState.Success(list)
        } catch (e: Exception) {
            _topRatedTv.value = RequestState.Error(e.localizedMessage ?: "خطأ")
        }
    }

    private suspend fun fetchNowPlayingMovies() {
        _nowPlayingMovies.value = RequestState.Loading
        try {
            val response = repository.getNowPlayingMovies(language = currentLang)
            val list = response.results?.map { it.copy(mediaType = "movie") } ?: emptyList()
            _nowPlayingMovies.value = RequestState.Success(list)
        } catch (e: Exception) {
            _nowPlayingMovies.value = RequestState.Error(e.localizedMessage ?: "خطأ")
        }
    }

    private suspend fun fetchOnTheAirTv() {
        _onTheAirTv.value = RequestState.Loading
        try {
            val response = repository.getOnTheAirTv(language = currentLang)
            val list = response.results?.map { it.copy(mediaType = "tv") } ?: emptyList()
            _onTheAirTv.value = RequestState.Success(list)
        } catch (e: Exception) {
            _onTheAirTv.value = RequestState.Error(e.localizedMessage ?: "خطأ")
        }
    }

    fun fetchMovieDetails(movieId: Int) {
        if (_movieDetails.value.containsKey(movieId) && _movieDetails.value[movieId] is RequestState.Success) return
        
        viewModelScope.launch {
            val currentMap = _movieDetails.value.toMutableMap()
            currentMap[movieId] = RequestState.Loading
            _movieDetails.value = currentMap
            
            try {
                val details = repository.getMovieDetails(movieId, currentLang)
                val updatedMap = _movieDetails.value.toMutableMap()
                updatedMap[movieId] = RequestState.Success(details)
                _movieDetails.value = updatedMap
            } catch (e: Exception) {
                val updatedMap = _movieDetails.value.toMutableMap()
                updatedMap[movieId] = RequestState.Error(e.localizedMessage ?: "فشل تحميل تفاصيل الفيلم")
                _movieDetails.value = updatedMap
            }
        }
    }

    fun fetchTvDetails(tvId: Int) {
        if (_tvDetails.value.containsKey(tvId) && _tvDetails.value[tvId] is RequestState.Success) return

        viewModelScope.launch {
            val currentMap = _tvDetails.value.toMutableMap()
            currentMap[tvId] = RequestState.Loading
            _tvDetails.value = currentMap

            try {
                val details = repository.getTvDetails(tvId, currentLang)
                val updatedMap = _tvDetails.value.toMutableMap()
                updatedMap[tvId] = RequestState.Success(details)
                _tvDetails.value = updatedMap
            } catch (e: Exception) {
                val updatedMap = _tvDetails.value.toMutableMap()
                updatedMap[tvId] = RequestState.Error(e.localizedMessage ?: "فشل تحميل تفاصيل المسلسل")
                _tvDetails.value = updatedMap
            }
        }
    }

    fun fetchSeasonDetails(tvId: Int, seasonNumber: Int) {        val key = "$tvId-$seasonNumber"
        if (_seasonDetails.value.containsKey(key) && _seasonDetails.value[key] is RequestState.Success) return

        viewModelScope.launch {
            val currentMap = _seasonDetails.value.toMutableMap()
            currentMap[key] = RequestState.Loading
            _seasonDetails.value = currentMap

            try {
                val details = repository.getSeasonDetails(tvId, seasonNumber, currentLang)
                val updatedMap = _seasonDetails.value.toMutableMap()
                updatedMap[key] = RequestState.Success(details)
                _seasonDetails.value = updatedMap
            } catch (e: Exception) {
                val updatedMap = _seasonDetails.value.toMutableMap()
                updatedMap[key] = RequestState.Error(e.localizedMessage ?: "فشل تحميل تفاصيل الموسم")
                _seasonDetails.value = updatedMap
            }
        }
    }

    fun fetchMovieCertification(movieId: Int) {
        if (_movieCertifications.value.containsKey(movieId) && _movieCertifications.value[movieId] is RequestState.Success) return
        viewModelScope.launch {
            val currentMap = _movieCertifications.value.toMutableMap()
            currentMap[movieId] = RequestState.Loading
            _movieCertifications.value = currentMap
            try {
                val response = repository.getMovieReleaseDates(movieId)
                val usCert = response.results
                    ?.firstOrNull { it.iso31661 == "US" }
                    ?.releaseDates
                    ?.firstOrNull { !it.certification.isNullOrEmpty() }
                    ?.certification
                val updatedMap = _movieCertifications.value.toMutableMap()
                updatedMap[movieId] = if (usCert != null) RequestState.Success(usCert)
                    else RequestState.Error("No certification")
                _movieCertifications.value = updatedMap
            } catch (e: Exception) {
                val updatedMap = _movieCertifications.value.toMutableMap()
                updatedMap[movieId] = RequestState.Error(e.localizedMessage ?: "فشل")
                _movieCertifications.value = updatedMap
            }
        }
    }

    fun fetchTvContentRating(tvId: Int) {
        if (_tvContentRatings.value.containsKey(tvId) && _tvContentRatings.value[tvId] is RequestState.Success) return
        viewModelScope.launch {
            val currentMap = _tvContentRatings.value.toMutableMap()
            currentMap[tvId] = RequestState.Loading
            _tvContentRatings.value = currentMap
            try {
                val response = repository.getTvContentRatings(tvId)
                val usRating = response.results?.firstOrNull { it.iso31661 == "US" }?.rating
                val updatedMap = _tvContentRatings.value.toMutableMap()
                updatedMap[tvId] = if (usRating != null) RequestState.Success(usRating)
                    else RequestState.Error("No rating")
                _tvContentRatings.value = updatedMap
            } catch (e: Exception) {
                val updatedMap = _tvContentRatings.value.toMutableMap()
                updatedMap[tvId] = RequestState.Error(e.localizedMessage ?: "فشل")
                _tvContentRatings.value = updatedMap
            }
        }
    }

    fun fetchMovieSimilar(movieId: Int) {
        if (_movieSimilar.value.containsKey(movieId) && _movieSimilar.value[movieId] is RequestState.Success) return
        viewModelScope.launch {
            val currentMap = _movieSimilar.value.toMutableMap()
            currentMap[movieId] = RequestState.Loading
            _movieSimilar.value = currentMap
            try {
                val response = repository.getMovieSimilar(movieId, language = currentLang)
                val list = response.results?.filter { !it.posterPath.isNullOrEmpty() } ?: emptyList()
                val updatedMap = _movieSimilar.value.toMutableMap()
                updatedMap[movieId] = if (list.isNotEmpty()) RequestState.Success(list)
                    else RequestState.Error("No similar")
                _movieSimilar.value = updatedMap
            } catch (e: Exception) {
                val updatedMap = _movieSimilar.value.toMutableMap()
                updatedMap[movieId] = RequestState.Error(e.localizedMessage ?: "فشل")
                _movieSimilar.value = updatedMap
            }
        }
    }

    fun fetchMovieRecommendations(movieId: Int) {
        if (_movieRecommendations.value.containsKey(movieId) && _movieRecommendations.value[movieId] is RequestState.Success) return
        viewModelScope.launch {
            val currentMap = _movieRecommendations.value.toMutableMap()
            currentMap[movieId] = RequestState.Loading
            _movieRecommendations.value = currentMap
            try {
                val response = repository.getMovieRecommendations(movieId, language = currentLang)
                val list = response.results?.filter { !it.posterPath.isNullOrEmpty() } ?: emptyList()
                val updatedMap = _movieRecommendations.value.toMutableMap()
                updatedMap[movieId] = if (list.isNotEmpty()) RequestState.Success(list)
                    else RequestState.Error("No recommendations")
                _movieRecommendations.value = updatedMap
            } catch (e: Exception) {
                val updatedMap = _movieRecommendations.value.toMutableMap()
                updatedMap[movieId] = RequestState.Error(e.localizedMessage ?: "فشل")
                _movieRecommendations.value = updatedMap
            }
        }
    }

    fun fetchTvSimilar(tvId: Int) {
        if (_tvSimilar.value.containsKey(tvId) && _tvSimilar.value[tvId] is RequestState.Success) return
        viewModelScope.launch {
            val currentMap = _tvSimilar.value.toMutableMap()
            currentMap[tvId] = RequestState.Loading
            _tvSimilar.value = currentMap
            try {
                val response = repository.getTvSimilar(tvId, language = currentLang)
                val list = response.results?.filter { !it.posterPath.isNullOrEmpty() } ?: emptyList()
                val updatedMap = _tvSimilar.value.toMutableMap()
                updatedMap[tvId] = if (list.isNotEmpty()) RequestState.Success(list)
                    else RequestState.Error("No similar")
                _tvSimilar.value = updatedMap
            } catch (e: Exception) {
                val updatedMap = _tvSimilar.value.toMutableMap()
                updatedMap[tvId] = RequestState.Error(e.localizedMessage ?: "فشل")
                _tvSimilar.value = updatedMap
            }
        }
    }

    fun fetchTvRecommendations(tvId: Int) {
        if (_tvRecommendations.value.containsKey(tvId) && _tvRecommendations.value[tvId] is RequestState.Success) return
        viewModelScope.launch {
            val currentMap = _tvRecommendations.value.toMutableMap()
            currentMap[tvId] = RequestState.Loading
            _tvRecommendations.value = currentMap
            try {
                val response = repository.getTvRecommendations(tvId, language = currentLang)
                val list = response.results?.filter { !it.posterPath.isNullOrEmpty() } ?: emptyList()
                val updatedMap = _tvRecommendations.value.toMutableMap()
                updatedMap[tvId] = if (list.isNotEmpty()) RequestState.Success(list)
                    else RequestState.Error("No recommendations")
                _tvRecommendations.value = updatedMap
            } catch (e: Exception) {
                val updatedMap = _tvRecommendations.value.toMutableMap()
                updatedMap[tvId] = RequestState.Error(e.localizedMessage ?: "فشل")
                _tvRecommendations.value = updatedMap
            }
        }
    }

    // Offline fallback: locally cached season totals
    suspend fun getSeasonMeta(tvId: Int): List<com.aistudio.cinemios.fxtyr.data.local.SeasonMetaEntity> {
        return repository.getSeasonMeta(tvId)
    }

    // WATCHLIST OPERATIONS
    fun toggleWatchlist(id: String, title: String, posterPath: String, mediaType: String, rating: Double) {
        viewModelScope.launch {
            val existing = repository.getWatchlistById(id)
            val now = System.currentTimeMillis()
            val isVisible = existing?.let { !it.isDeleted } ?: false
            if (existing != null && isVisible) {
                // ضغطة ثانية = soft-delete
                repository.softDeleteWatchlist(id, now)
            } else {
                // upsert (جديد أو معاد تفعيله بعد حذف)
                repository.addToWatchlist(
                    WatchlistEntity(
                        id = id, title = title, posterPath = posterPath,
                        mediaType = mediaType, rating = rating,
                        status = _defaultWatchStatus.value,
                        isDeleted = false,
                        addedAt = existing?.addedAt ?: now,
                        updatedAt = now
                    )
                )
            }
        }
    }

    fun saveToWatchlistWithStatus(id: String, title: String, posterPath: String, mediaType: String, rating: Double, status: String) {
        viewModelScope.launch {
            val existing = repository.getWatchlistById(id)
            val now = System.currentTimeMillis()
            repository.addToWatchlist(
                WatchlistEntity(
                    id = id, title = title, posterPath = posterPath,
                    mediaType = mediaType, rating = rating,
                    status = status,
                    isDeleted = false,
                    addedAt = existing?.addedAt ?: now,
                    updatedAt = now
                )
            )
            // Auto-mark all episodes as watched when TV show set to COMPLETED
            if (status == "COMPLETED" && mediaType == "tv") {
                try {
                    val tvId = id.toIntOrNull()
                    if (tvId != null) {
                        markAllTvEpisodesWatched(tvId, currentLang)
                    }
                } catch (_: Exception) {
                    // Silent fail — user still gets COMPLETED status even if auto-mark fails
                }
            }
        }
    }

    private suspend fun markAllTvEpisodesWatched(tvId: Int, lang: String) {
        // 1. Get TV details for seasons list
        val tvDetails = repository.getTvDetails(tvId, lang)
        val seasons = tvDetails.seasons?.map { it.seasonNumber }?.filter { it > 0 } ?: return
        val now = System.currentTimeMillis()
        val allItems = mutableListOf<EpisodeWatchStatusEntity>()
        // 2. For each season, get episodes and create watched entries
        for (s in seasons) {
            try {
                val seasonDetails = repository.getSeasonDetails(tvId, s, lang)
                val episodes = seasonDetails.episodes ?: continue
                for (ep in episodes) {
                    allItems.add(
                        EpisodeWatchStatusEntity(
                            tmdbId = tvId.toString(),
                            season = s,
                            episode = ep.episodeNumber,
                            watched = true,
                            updatedAt = now
                        )
                    )
                }
            } catch (_: Exception) {
                // Skip seasons that fail to load
            }
        }
        if (allItems.isNotEmpty()) {
            repository.upsertEpisodeWatchStatusBatch(allItems)
        }
    }

    fun deleteFromWatchlist(id: String) {
        viewModelScope.launch {
            repository.softDeleteWatchlist(id, System.currentTimeMillis())
        }
    }

    fun isItemInWatchlist(id: String): Flow<Boolean> {
        return repository.isItemInWatchlistFlow(id).map { it != null && !it.isDeleted }
    }

    // EPISODE WATCH STATUS
    fun toggleEpisodeWatchStatus(tmdbId: String, season: Int, episode: Int) {
        viewModelScope.launch {
            val current = repository.getEpisodeWatchStatus(tmdbId, season, episode)
            val now = System.currentTimeMillis()
            repository.upsertEpisodeWatchStatus(
                EpisodeWatchStatusEntity(
                    tmdbId = tmdbId,
                    season = season,
                    episode = episode,
                    watched = current?.watched != true, // true → false flip
                    updatedAt = now
                )
            )
        }
    }

    fun markAllEpisodesAsWatched(tmdbId: String, season: Int) {
        viewModelScope.launch {
            val existingForSeason = withContext(Dispatchers.IO) {
                repository.getEpisodeWatchStatusForSeason(tmdbId, season).first()
            }
            if (existingForSeason.isNotEmpty()) {
                val updated = existingForSeason.map { it.copy(watched = true, updatedAt = System.currentTimeMillis()) }
                repository.upsertEpisodeWatchStatusBatch(updated)
            }
        }
    }

    fun markAllEpisodesAsUnwatched(tmdbId: String, season: Int) {
        viewModelScope.launch {
            val existingForSeason = withContext(Dispatchers.IO) {
                repository.getEpisodeWatchStatusForSeason(tmdbId, season).first()
            }
            if (existingForSeason.isNotEmpty()) {
                val updated = existingForSeason.map { it.copy(watched = false, updatedAt = System.currentTimeMillis()) }
                repository.upsertEpisodeWatchStatusBatch(updated)
            }
        }
    }

    // SYNC
    fun syncWatchlist(onComplete: (Boolean) -> Unit = {}) {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                com.aistudio.cinemios.fxtyr.data.sync.WatchlistSyncManager.sync(
                    repository = repository,
                    getApplication()
                )
                onComplete(true)
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(false)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    // DOWNLOAD OPERATIONS - Single queue (one at a time, FIFO)
    private var currentDownloadId: String? = null

    fun requestDownload(
        mediaId: String,
        title: String,
        posterPath: String,
        stillPath: String = "",
        mediaType: String,
        season: Int = 0,
        episode: Int = 0,
        quality: String = "1080p",
        customUrl: String? = null,
        customHeaders: Map<String, String>? = null
    ) {
        val downloadId = if (mediaType == "tv") "$mediaId-s$season-e$episode" else mediaId
        viewModelScope.launch {
            val existing = repository.getDownload(downloadId)
            if (existing != null && existing.status == "completed") {
                Toast.makeText(getApplication(), "هذا المحتوى تم تنزيله مسبقاً", Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (existing != null && (existing.status == "queued" || existing.status == "downloading")) {
                Toast.makeText(getApplication(), "هذا المحتوى موجود بالفعل في قائمة التحميل", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val nameSuffix = if (mediaType == "tv") " - الموسم $season الحلقة $episode" else ""
            val fullTitle = "$title$nameSuffix"

            val entity = DownloadEntity(
                id = downloadId,
                mediaId = mediaId,
                title = fullTitle,
                posterPath = posterPath,
                stillPath = stillPath,
                mediaType = mediaType,
                season = season,
                episode = episode,
                progress = 0,
                status = "queued",
                quality = quality,
                downloadedBytes = 0L,
                totalBytes = 0L,
                downloadSpeed = "في الانتظار",
                sourceUrl = customUrl ?: ""
            )
            repository.addDownload(entity)
            processQueue()
        }
    }

    fun pauseDownload(downloadId: String) {
        viewModelScope.launch {
            com.aistudio.cinemios.fxtyr.utils.MultiThreadDownloader.pauseDownload(downloadId)
            if (currentDownloadId == downloadId) {
                currentDownloadId = null
            }
            val entity = repository.getDownload(downloadId) ?: return@launch
            val updated = entity.copy(status = "paused", downloadSpeed = "متوقف مؤقتاً")
            repository.addDownload(updated)
            processQueue()
        }
    }

    fun resumeDownload(downloadId: String) {
        viewModelScope.launch {
            val entity = repository.getDownload(downloadId) ?: return@launch
            val updated = entity.copy(status = "queued", downloadSpeed = "في الانتظار")
            repository.addDownload(updated)
            processQueue()
        }
    }

    fun deleteDownload(downloadId: String) {
        viewModelScope.launch {
            com.aistudio.cinemios.fxtyr.utils.MultiThreadDownloader.pauseDownload(downloadId)
            if (currentDownloadId == downloadId) {
                currentDownloadId = null
            }
            val file = File(getApplication<Application>().filesDir, "downloads/$downloadId.mp4")
            if (file.exists()) {
                file.delete()
            }
            val progressFile = File(getApplication<Application>().filesDir, "downloads/$downloadId.mp4.progress")
            if (progressFile.exists()) {
                progressFile.delete()
            }
            val vttFile = File(getApplication<Application>().filesDir, "downloads/$downloadId.vtt")
            if (vttFile.exists()) {
                vttFile.delete()
            }
            repository.removeDownload(downloadId)
            processQueue()
        }
    }

    fun saveSubtitleDownload(
        tmdbId: String,
        title: String,
        posterPath: String,
        language: String,
        languageCode: String,
        source: String,
        localFilePath: String,
        mediaType: String = "movie",
        season: Int = 0,
        episode: Int = 0,
        fileName: String = "",
        batchId: String = ""
    ) {
        viewModelScope.launch {
            val baseId = if (mediaType == "tv") "${tmdbId}_s${season}e${episode}_$languageCode" else "${tmdbId}_$languageCode"
            val id = if (baseId.endsWith("_$languageCode")) "${baseId}_${System.currentTimeMillis()}" else baseId
            val entity = SubtitleDownloadEntity(
                id = id,
                tmdbId = tmdbId,
                title = title,
                mediaType = mediaType,
                posterPath = posterPath,
                language = language,
                languageCode = languageCode,
                source = source,
                localFilePath = localFilePath,
                season = season,
                episode = episode,
                fileName = fileName,
                batchId = batchId,
                downloadedAt = System.currentTimeMillis()
            )
            repository.addSubtitleDownload(entity)
            // Toast removed: unified batch toast is shown via onBatchComplete
        }
    }

    fun deleteSubtitleDownload(id: String) {
        viewModelScope.launch {
            repository.removeSubtitleDownload(id)
        }
    }

    // Process the queue: start the oldest queued download if nothing is running
    private fun processQueue() {
        viewModelScope.launch {
            // If something is already downloading, return
            if (currentDownloadId != null) {
                val cur = currentDownloadId?.let { repository.getDownload(it) }
                if (cur != null && cur.status == "downloading") {
                    return@launch
                }
                // Current download might have finished/failed, clear it
                currentDownloadId = null
            }

            // Find the oldest queued item
            val all = repository.downloads.first()
            val next = all
                .filter { it.status == "queued" }
                .minByOrNull { it.addedAt }

            if (next != null) {
                val updated = next.copy(status = "downloading", downloadSpeed = "0 KB/s")
                repository.addDownload(updated)
                currentDownloadId = next.id
                triggerNetworkDownload(next.id, next.quality, next.sourceUrl.takeIf { it.isNotEmpty() })
            }
        }
    }

    private fun triggerNetworkDownload(
        downloadId: String,
        quality: String,
        customUrl: String? = null,
        customHeaders: Map<String, String>? = null
    ) {
        val finalUrl = customUrl ?: return
        
        val file = File(getApplication<Application>().filesDir, "downloads/$downloadId.mp4")
        if (!file.parentFile!!.exists()) {
            file.parentFile!!.mkdirs()
        }

        com.aistudio.cinemios.fxtyr.utils.MultiThreadDownloader.startDownload(
            downloadId = downloadId,
            url = finalUrl,
            outputFile = file,
            scope = viewModelScope,
            onProgress = { progress, downloaded, total, speedStr ->
                viewModelScope.launch(Dispatchers.Main) {
                    val current = repository.getDownload(downloadId)
                    if (current != null && current.status != "paused") {
                         repository.addDownload(current.copy(
                             progress = progress,
                             downloadedBytes = downloaded,
                             totalBytes = total,
                             status = "downloading",
                             downloadSpeed = speedStr
                         ))
                    }
                }
            },
            onComplete = { success ->
                viewModelScope.launch(Dispatchers.Main) {
                    if (currentDownloadId == downloadId) {
                        currentDownloadId = null
                    }
                    val current = repository.getDownload(downloadId)
                    if (current != null) {
                          if (success) {
                              repository.addDownload(current.copy(
                                  progress = 100,
                                  status = "completed",
                                  localFilePath = file.absolutePath,
                                  downloadSpeed = "مكتمل"
                              ))
                              // Log the completed download to the activity history
                              logActivity("DOWNLOADED", current.title)
                         } else if (current.status != "paused") {
                             repository.addDownload(current.copy(
                                 status = "error",
                                 downloadSpeed = "خطأ في التحميل"
                             ))
                         }
                    }
                    // Process next item in queue
                    processQueue()
                }
            }
        )
    }

    // ---- SAVED IMAGES (Browser) ----
    fun saveImageFromBrowser(sourceUrl: String, pageUrl: String, pageTitle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(sourceUrl.toByteArray(Charsets.UTF_8))
            val imageId = hashBytes.joinToString("") { "%02x".format(it) }
            val fileName = "browser_img_${imageId.take(16)}.jpg"
            val outputDir = File(getApplication<Application>().filesDir, "saved_images")
            outputDir.mkdirs()
            val outputFile = File(outputDir, fileName)
            val now = System.currentTimeMillis()

            val entity = SavedImageEntity(
                id = imageId,
                sourceUrl = sourceUrl,
                pageUrl = pageUrl,
                pageTitle = pageTitle,
                localFilePath = outputFile.absolutePath,
                fileSizeBytes = 0L,
                downloadedAt = now
            )
            // Insert immediately (pending download)
            repository.addSavedImage(entity)

            com.aistudio.cinemios.fxtyr.utils.MultiThreadDownloader.startDownload(
                downloadId = "saved_img_$imageId",
                url = sourceUrl,
                outputFile = outputFile,
                scope = viewModelScope,
                onProgress = { _, _, _, _ -> },
                onComplete = { success ->
                    viewModelScope.launch(Dispatchers.IO) {
                        if (success) {
                            repository.addSavedImage(entity.copy(fileSizeBytes = outputFile.length()))
                        } else {
                            repository.removeSavedImage(imageId)
                            outputFile.delete()
                        }
                    }
                }
            )
        }
    }

    fun deleteSavedImage(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val image = repository.getSavedImageById(id) ?: return@launch
            File(image.localFilePath).delete()
            repository.removeSavedImage(id)
        }
    }

    // Activity logging helper
    fun logActivity(type: String, title: String) {
        viewModelScope.launch {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
            ActivityLogManager.addLog(uid, type, title)
        }
    }

    // Resume any pending downloads on startup
    fun resumePendingDownloads() {
        viewModelScope.launch {
            val all = repository.downloads.first()
            val hasRunning = all.any { it.status == "downloading" }
            // Reset any stuck "downloading" items back to "queued"
            all.filter { it.status == "downloading" }.forEach {
                repository.addDownload(it.copy(status = "queued", downloadSpeed = "في الانتظار"))
            }
            currentDownloadId = null
            processQueue()
        }
    }
}

class ViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val api = com.aistudio.cinemios.fxtyr.data.remote.moviebox.api.MovieBoxApiImpl()
        val repository = com.aistudio.cinemios.fxtyr.data.remote.moviebox.repository.MovieBoxRepositoryImpl(application, api)

        if (modelClass.isAssignableFrom(MovieViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MovieViewModel(application, repository) as T
        }
        if (modelClass.isAssignableFrom(com.aistudio.cinemios.fxtyr.data.remote.moviebox.viewmodel.MovieBoxViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return com.aistudio.cinemios.fxtyr.data.remote.moviebox.viewmodel.MovieBoxViewModel(repository) as T
        }
        if (modelClass.isAssignableFrom(com.aistudio.cinemios.fxtyr.ai.AiViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return com.aistudio.cinemios.fxtyr.ai.AiViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
