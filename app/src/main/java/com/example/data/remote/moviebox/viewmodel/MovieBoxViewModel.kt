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
}
