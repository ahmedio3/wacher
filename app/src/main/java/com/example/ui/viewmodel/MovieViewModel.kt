package com.example.ui.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.DownloadEntity
import com.example.data.local.MovieDatabase
import com.example.data.local.SubtitleDownloadEntity
import com.example.data.local.WatchlistEntity
import com.example.data.remote.*
import com.example.data.repository.MovieRepository
import com.example.data.remote.moviebox.repository.MovieBoxRepository
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

    // Settings Configuration: Arabic vs English posters
    private val sharedPrefs = application.getSharedPreferences("watchera_prefs", android.content.Context.MODE_PRIVATE)
    private val _isArabicPosters = MutableStateFlow(sharedPrefs.getBoolean("arabic_posters", true))
    val isArabicPosters: StateFlow<Boolean> = _isArabicPosters.asStateFlow()

    fun setArabicPosters(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("arabic_posters", enabled).apply()
        _isArabicPosters.value = enabled
        // Clear detail caches so they refresh with new language
        _movieDetails.value = emptyMap()
        _tvDetails.value = emptyMap()
        _seasonDetails.value = emptyMap()
        if (_searchQuery.value.isNotBlank()) searchMedia(_searchQuery.value)
        fetchHomeContent()
    }

    private val currentLang: String get() = if (_isArabicPosters.value) "ar" else "en"

    // Reactively observe local database Watchlist and Downloads
    val watchlist: StateFlow<List<WatchlistEntity>>
    val downloads: StateFlow<List<DownloadEntity>>
    val subtitleDownloads: StateFlow<List<SubtitleDownloadEntity>>
    val subtitleBatchGroups: StateFlow<List<SubtitleBatchGroup>>

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

        // Fetch Home content on startup
        fetchHomeContent()
    }

    private val _isMovieBoxSearchMode = MutableStateFlow(false)
    val isMovieBoxSearchMode: StateFlow<Boolean> = _isMovieBoxSearchMode.asStateFlow()

    private val _movieBoxSearchResults = MutableStateFlow<RequestState<List<com.example.data.remote.moviebox.models.SearchResult>>>(RequestState.Idle)
    val movieBoxSearchResults: StateFlow<RequestState<List<com.example.data.remote.moviebox.models.SearchResult>>> = _movieBoxSearchResults.asStateFlow()

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

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.trim().isEmpty()) {
            _searchResults.value = RequestState.Idle
            _movieBoxSearchResults.value = RequestState.Idle
        } else {
            if (_isMovieBoxSearchMode.value) {
                searchMovieBox(query.trim())
            } else {
                searchMedia(query.trim())
            }
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

    fun fetchSeasonDetails(tvId: Int, seasonNumber: Int) {
        val key = "$tvId-$seasonNumber"
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

    // WATCHLIST OPERATIONS
    fun toggleWatchlist(id: String, title: String, posterPath: String, mediaType: String, rating: Double) {
        viewModelScope.launch {
            if (repository.isItemInWatchlist(id)) {
                repository.removeFromWatchlist(id)
            } else {
                repository.addToWatchlist(
                    WatchlistEntity(id = id, title = title, posterPath = posterPath, mediaType = mediaType, rating = rating)
                )
            }
        }
    }

    fun isItemInWatchlist(id: String): Flow<Boolean> {
        return repository.isItemInWatchlistFlow(id).map { it != null }
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
            com.example.utils.MultiThreadDownloader.pauseDownload(downloadId)
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
            com.example.utils.MultiThreadDownloader.pauseDownload(downloadId)
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

        com.example.utils.MultiThreadDownloader.startDownload(
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
                             
                             // Download Arabic Subtitles if available
                             viewModelScope.launch(Dispatchers.IO) {
                                 try {
                                     val isTv = current.mediaType == "tv"
                                     val tmdbIdString = if (isTv) current.mediaId.substringBefore("-s") else current.mediaId
                                     val season = if (isTv) current.mediaId.substringAfter("-s").substringBefore("-e").toIntOrNull() ?: 1 else 0
                                     val episode = if (isTv) current.mediaId.substringAfter("-e").toIntOrNull() ?: 1 else 0
                                     
                                     val subs = com.example.ui.viewmodel.SubtitleHelper.fetchSubtitles(tmdbIdString, isTv, season, episode, current.title.substringBefore(" - "))
                                     val arSub = subs.firstOrNull { it.lang.contains("AR", ignoreCase = true) } ?: subs.firstOrNull()
                                     if (arSub != null) {
                                         val ctx = getApplication<Application>().applicationContext
                                         com.example.ui.viewmodel.SubtitleHelper.downloadAndExtractSubtitle(ctx, arSub.url, current.id)
                                     }
                                 } catch (e: Exception) { e.printStackTrace() }
                             }
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
        val api = com.example.data.remote.moviebox.api.MovieBoxApiImpl()
        val repository = com.example.data.remote.moviebox.repository.MovieBoxRepositoryImpl(application, api)

        if (modelClass.isAssignableFrom(MovieViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MovieViewModel(application, repository) as T
        }
        if (modelClass.isAssignableFrom(com.example.data.remote.moviebox.viewmodel.MovieBoxViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return com.example.data.remote.moviebox.viewmodel.MovieBoxViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
