package com.example.data.repository

import com.example.data.local.DownloadEntity
import com.example.data.local.EpisodeWatchStatusEntity
import com.example.data.local.MovieDao
import com.example.data.local.SavedImageEntity
import com.example.data.local.SeasonMetaEntity
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

    suspend fun getEpisodeWatchStatus(tmdbId: String, season: Int, episode: Int): EpisodeWatchStatusEntity? {
        return movieDao.getEpisodeWatchStatus(tmdbId, season, episode)
    }

    fun getWatchedCountForTvShow(tmdbId: String): Flow<Int> {
        return movieDao.getWatchedCountForTvShow(tmdbId)
    }

    suspend fun getAllEpisodeWatchStatus(): List<EpisodeWatchStatusEntity> {
        return movieDao.getAllEpisodeWatchStatus()
    }

    suspend fun upsertEpisodeWatchStatusBatch(items: List<EpisodeWatchStatusEntity>) {
        movieDao.upsertEpisodeWatchStatusBatch(items)
    }

    // ---- SAVED IMAGES (Browser) ----
    val savedImages: Flow<List<SavedImageEntity>> = movieDao.getSavedImages()

    suspend fun addSavedImage(item: SavedImageEntity) {
        movieDao.insertSavedImage(item)
    }

    suspend fun removeSavedImage(id: String) {
        movieDao.deleteSavedImage(id)
    }

    suspend fun getSavedImageById(id: String): SavedImageEntity? {
        return movieDao.getSavedImageById(id)
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
        val details = tmdbService.getTvDetails(tvId = tvId, apiKey = tmdbApiKey, language = language)
        // Cache season totals locally so the sheet works offline
        val now = System.currentTimeMillis()
        val metas = details.seasons
            ?.filter { it.seasonNumber > 0 }
            ?.map { s ->
                SeasonMetaEntity(
                    tmdbId = tvId,
                    seasonNumber = s.seasonNumber,
                    episodeCount = s.episodeCount ?: 0,
                    name = s.name ?: "الموسم ${s.seasonNumber}",
                    lastFetchedAt = now
                )
            }.orEmpty()
        if (metas.isNotEmpty()) movieDao.upsertSeasonMeta(metas)
        return details
    }

    suspend fun getSeasonDetails(tvId: Int, seasonNumber: Int, language: String = "ar"): TmdbSeasonDetails {
        val details = tmdbService.getSeasonDetails(tvId = tvId, seasonNumber = seasonNumber, apiKey = tmdbApiKey, language = language)
        movieDao.upsertSeasonMeta(
            listOf(
                SeasonMetaEntity(
                    tmdbId = tvId,
                    seasonNumber = seasonNumber,
                    episodeCount = details.episodes?.size ?: 0,
                    name = "الموسم $seasonNumber",
                    lastFetchedAt = System.currentTimeMillis()
                )
            )
        )
        return details
    }

    suspend fun getSeasonMeta(tvId: Int): List<SeasonMetaEntity> {
        return movieDao.getSeasonMeta(tvId)
    }
}
