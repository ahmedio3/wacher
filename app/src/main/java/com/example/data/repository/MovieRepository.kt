package com.example.data.repository

import com.example.data.local.DownloadEntity
import com.example.data.local.EpisodeWatchStatusEntity
import com.example.data.local.MovieDao
import com.example.data.local.SubtitleDownloadEntity
import com.example.data.local.WatchlistEntity
import com.example.data.remote.*
import kotlinx.coroutines.flow.Flow

class MovieRepository(private val movieDao: MovieDao) {

    private val tmdbApiKey = "970be69502451a04b3c38cbd368fda36"
    private val tmdbService = RetrofitClient.tmdbService

    // ---- WATCHLIST ----
    val watchlist: Flow<List<WatchlistEntity>> = movieDao.getWatchlist()

    suspend fun addToWatchlist(item: WatchlistEntity) {
        movieDao.insertWatchlist(item)
    }

    suspend fun softDeleteWatchlist(id: String, now: Long = System.currentTimeMillis()) {
        movieDao.softDeleteWatchlistById(id, now)
    }

    fun isItemInWatchlistFlow(id: String): Flow<WatchlistEntity?> {
        return movieDao.getWatchlistByIdFlow(id)
    }

    suspend fun isItemInWatchlist(id: String): Boolean {
        return movieDao.getWatchlistById(id)?.let { !it.isDeleted } ?: false
    }

    suspend fun getWatchlistById(id: String): WatchlistEntity? {
        return movieDao.getWatchlistById(id)
    }

    suspend fun getAllWatchlistItems(): List<WatchlistEntity> {
        return movieDao.getAllWatchlistItems()
    }

    // ---- DOWNLOADS ----
    val downloads: Flow<List<DownloadEntity>> = movieDao.getDownloads()

    suspend fun addDownload(item: DownloadEntity) {
        movieDao.insertDownload(item)
    }

    suspend fun removeDownload(id: String) {
        movieDao.deleteDownloadById(id)
    }

    suspend fun getDownload(id: String): DownloadEntity? {
        return movieDao.getDownloadById(id)
    }

    // ---- SUBTITLE DOWNLOADS ----
    val subtitleDownloads: Flow<List<SubtitleDownloadEntity>> = movieDao.getSubtitleDownloads()

    suspend fun addSubtitleDownload(item: SubtitleDownloadEntity) {
        movieDao.insertSubtitleDownload(item)
    }

    suspend fun removeSubtitleDownload(id: String) {
        movieDao.deleteSubtitleDownload(id)
    }

    // ---- EPISODE WATCH STATUS ----
    suspend fun upsertEpisodeWatchStatus(item: EpisodeWatchStatusEntity) {
        movieDao.upsertEpisodeWatchStatus(item)
    }

    fun getEpisodeWatchStatusForSeason(tmdbId: String, season: Int): Flow<List<EpisodeWatchStatusEntity>> {
        return movieDao.getEpisodeWatchStatusForSeason(tmdbId, season)
    }

    fun getWatchedCountForTvShow(tmdbId: String): Flow<Int> {
        return movieDao.getWatchedCountForTvShow(tmdbId)
    }

    suspend fun getAllEpisodeWatchStatus(): List<EpisodeWatchStatusEntity> {
        return movieDao.getAllEpisodeWatchStatus()
    }

    // ---- REMOTE TMDB APIS ----
    suspend fun getPopularMovies(language: String = "ar", page: Int = 1): TmdbSearchResponse {
        return tmdbService.getPopularMovies(apiKey = tmdbApiKey, language = language, page = page)
    }

    suspend fun getPopularTvShows(language: String = "ar", page: Int = 1): TmdbSearchResponse {
        return tmdbService.getPopularTv(apiKey = tmdbApiKey, language = language, page = page)
    }

    suspend fun searchMulti(query: String, language: String = "ar", page: Int = 1): TmdbSearchResponse {
        return tmdbService.searchMulti(apiKey = tmdbApiKey, query = query, language = language, page = page)
    }

    suspend fun getMovieDetails(movieId: Int, language: String = "ar"): TmdbMovieDetails {
        return tmdbService.getMovieDetails(movieId = movieId, apiKey = tmdbApiKey, language = language)
    }

    suspend fun getTvDetails(tvId: Int, language: String = "ar"): TmdbTvDetails {
        return tmdbService.getTvDetails(tvId = tvId, apiKey = tmdbApiKey, language = language)
    }

    suspend fun getSeasonDetails(tvId: Int, seasonNumber: Int, language: String = "ar"): TmdbSeasonDetails {
        return tmdbService.getSeasonDetails(tvId = tvId, seasonNumber = seasonNumber, apiKey = tmdbApiKey, language = language)
    }
}
