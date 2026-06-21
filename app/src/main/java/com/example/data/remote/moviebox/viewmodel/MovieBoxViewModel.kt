package com.example.data.remote.moviebox.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.moviebox.models.*
import com.example.data.remote.moviebox.repository.MovieBoxRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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
            repository.search(keyword, language)
                .onSuccess {
                    _searchResults.value = MovieBoxState.Success(it)
                }
                .onFailure {
                    _searchResults.value = MovieBoxState.Error(it.message ?: "Unknown error")
                }
        }
    }

    fun getDownloadLinks(subjectId: String, resolution: Int? = null) {
        viewModelScope.launch {
            _downloadLinks.value = MovieBoxState.Loading
            repository.getDownloadLinks(subjectId, resolution)
                .onSuccess {
                    _downloadLinks.value = MovieBoxState.Success(it)
                }
                .onFailure {
                    _downloadLinks.value = MovieBoxState.Error(it.message ?: "Unknown error")
                }
        }
    }
}
