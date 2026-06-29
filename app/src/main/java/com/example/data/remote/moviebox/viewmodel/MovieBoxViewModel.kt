package com.example.data.remote.moviebox.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.moviebox.models.*
import com.example.data.remote.moviebox.repository.MovieBoxRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

import kotlinx.coroutines.withTimeout

sealed class MovieBoxState<out T> {
    object Idle : MovieBoxState<Nothing>()
    object Loading : MovieBoxState<Nothing>()
    data class Success<T>(val data: T) : MovieBoxState<T>()
    data class Error(val message: String) : MovieBoxState<Nothing>()
}

class MovieBoxViewModel(private val repository: MovieBoxRepository) : ViewModel() {

    private val _searchResults = MutableStateFlow<MovieBoxState<List<SearchResult>>>(MovieBoxState.Idle)
    val searchResults: StateFlow<MovieBoxState<List<SearchResult>>> = _searchResults

    private val _downloadLinks = MutableStateFlow<MovieBoxState<List<VideoFile>>>(MovieBoxState.Idle)
    val downloadLinks: StateFlow<MovieBoxState<List<VideoFile>>> = _downloadLinks

    private val _browseResults = MutableStateFlow<MovieBoxState<List<SearchResult>>>(MovieBoxState.Idle)
    val browseResults: StateFlow<MovieBoxState<List<SearchResult>>> = _browseResults

    private val _trendingResults = MutableStateFlow<MovieBoxState<List<SearchResult>>>(MovieBoxState.Idle)
    val trendingResults: StateFlow<MovieBoxState<List<SearchResult>>> = _trendingResults

    private val _randomResults = MutableStateFlow<MovieBoxState<List<SearchResult>>>(MovieBoxState.Idle)
    val randomResults: StateFlow<MovieBoxState<List<SearchResult>>> = _randomResults

    private val _itemDetails = MutableStateFlow<MovieBoxState<ItemDetailResult>>(MovieBoxState.Idle)
    val itemDetails: StateFlow<MovieBoxState<ItemDetailResult>> = _itemDetails

    private val _adultContent = MutableStateFlow<MovieBoxState<List<SearchResult>>>(MovieBoxState.Idle)
    val adultContent: StateFlow<MovieBoxState<List<SearchResult>>> = _adultContent

    fun search(keyword: String, language: String? = null) {
        viewModelScope.launch {
            _searchResults.value = MovieBoxState.Loading
            try {
                val result = withTimeout(30_000L) {
                    repository.search(keyword, language)
                }
                result
                    .onSuccess { _searchResults.value = MovieBoxState.Success(it) }
                    .onFailure { _searchResults.value = MovieBoxState.Error(it.message ?: "Unknown error") }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                _searchResults.value = MovieBoxState.Error("انتهت مهلة البحث. تحقق من شبكتك.")
            } catch (e: Exception) {
                _searchResults.value = MovieBoxState.Error(e.localizedMessage ?: "حدث خطأ غير معروف")
            }
        }
    }

    fun getDownloadLinks(subjectId: String, resolution: Int? = null) {
        viewModelScope.launch {
            _downloadLinks.value = MovieBoxState.Loading
            try {
                val result = withTimeout(30_000L) {
                    repository.getDownloadLinks(subjectId, resolution)
                }
                result
                    .onSuccess { _downloadLinks.value = MovieBoxState.Success(it) }
                    .onFailure { _downloadLinks.value = MovieBoxState.Error(it.message ?: "Unknown error") }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                _downloadLinks.value = MovieBoxState.Error("انتهت مهلة تحميل الروابط. تحقق من شبكتك.")
            } catch (e: Exception) {
                _downloadLinks.value = MovieBoxState.Error(e.localizedMessage ?: "حدث خطأ غير معروف")
            }
        }
    }

    fun browse(genre: String? = null, type: String? = null, sort: String? = null, safeMode: Boolean? = null, limit: Int = 20) {
        viewModelScope.launch {
            _browseResults.value = MovieBoxState.Loading
            try {
                val result = withTimeout(30_000L) {
                    repository.browse(genre, type, sort, safeMode, limit)
                }
                result
                    .onSuccess { _browseResults.value = MovieBoxState.Success(it) }
                    .onFailure { _browseResults.value = MovieBoxState.Error(it.message ?: "Unknown error") }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                _browseResults.value = MovieBoxState.Error("انتهت مهلة التصفح. تحقق من شبكتك.")
            } catch (e: Exception) {
                _browseResults.value = MovieBoxState.Error(e.localizedMessage ?: "حدث خطأ غير معروف")
            }
        }
    }

    fun trending(genre: String? = null, page: Int = 1, limit: Int = 12) {
        viewModelScope.launch {
            _trendingResults.value = MovieBoxState.Loading
            try {
                val result = withTimeout(30_000L) {
                    repository.trending(genre, page, limit)
                }
                result
                    .onSuccess { _trendingResults.value = MovieBoxState.Success(it) }
                    .onFailure { _trendingResults.value = MovieBoxState.Error(it.message ?: "Unknown error") }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                _trendingResults.value = MovieBoxState.Error("انتهت مهلة تحميل الرائج. تحقق من شبكتك.")
            } catch (e: Exception) {
                _trendingResults.value = MovieBoxState.Error(e.localizedMessage ?: "حدث خطأ غير معروف")
            }
        }
    }

    fun fetchItemDetails(subjectId: String) {
        viewModelScope.launch {
            _itemDetails.value = MovieBoxState.Loading
            try {
                val result = withTimeout(30_000L) {
                    repository.itemDetails(subjectId)
                }
                result
                    .onSuccess { _itemDetails.value = MovieBoxState.Success(it) }
                    .onFailure { _itemDetails.value = MovieBoxState.Error(it.message ?: "Unknown error") }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                _itemDetails.value = MovieBoxState.Error("انتهت مهلة تحميل التفاصيل. تحقق من شبكتك.")
            } catch (e: Exception) {
                _itemDetails.value = MovieBoxState.Error(e.localizedMessage ?: "حدث خطأ غير معروف")
            }
        }
    }

    fun fetchAdultContent(type: String? = null, queries: String? = null, limit: Int = 10, sort: String? = null) {
        viewModelScope.launch {
            _adultContent.value = MovieBoxState.Loading
            try {
                val result = withTimeout(30_000L) {
                    repository.adultContent(type, queries, limit, sort)
                }
                result
                    .onSuccess { _adultContent.value = MovieBoxState.Success(it) }
                    .onFailure { _adultContent.value = MovieBoxState.Error(it.message ?: "Unknown error") }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                _adultContent.value = MovieBoxState.Error("انتهت مهلة التحميل. تحقق من شبكتك.")
            } catch (e: Exception) {
                _adultContent.value = MovieBoxState.Error(e.localizedMessage ?: "حدث خطأ غير معروف")
            }
        }
    }

    fun randomContent(type: String? = null, safeMode: Boolean? = null, limit: Int = 1) {
        viewModelScope.launch {
            _randomResults.value = MovieBoxState.Loading
            try {
                val result = withTimeout(30_000L) {
                    repository.randomContent(type, safeMode, limit)
                }
                result
                    .onSuccess { _randomResults.value = MovieBoxState.Success(it) }
                    .onFailure { _randomResults.value = MovieBoxState.Error(it.message ?: "Unknown error") }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                _randomResults.value = MovieBoxState.Error("انتهت مهلة تحميل العشوائي. تحقق من شبكتك.")
            } catch (e: Exception) {
                _randomResults.value = MovieBoxState.Error(e.localizedMessage ?: "حدث خطأ غير معروف")
            }
        }
    }
}
