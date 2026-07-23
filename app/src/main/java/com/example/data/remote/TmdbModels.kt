package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TmdbSearchResponse(
    val results: List<TmdbMediaItem>?
)

@JsonClass(generateAdapter = true)
data class TmdbCertificationResponse(
    val id: Int?,
    val results: List<TmdbReleaseDatesResult>?
)

@JsonClass(generateAdapter = true)
data class TmdbReleaseDatesResult(
    @Json(name = "iso_3166_1") val iso31661: String?,
    @Json(name = "release_dates") val releaseDates: List<TmdbReleaseDateItem>?
)

@JsonClass(generateAdapter = true)
data class TmdbReleaseDateItem(
    val certification: String?,
    val type: Int?,
    @Json(name = "release_date") val releaseDate: String?,
    val note: String?
)

@JsonClass(generateAdapter = true)
data class TmdbContentRatingsResponse(
    val id: Int?,
    val results: List<TmdbContentRatingItem>?
)

@JsonClass(generateAdapter = true)
data class TmdbContentRatingItem(
    @Json(name = "iso_3166_1") val iso31661: String?,
    val rating: String?
)

@JsonClass(generateAdapter = true)
data class TmdbMediaItem(
    val id: Int,
    val title: String?,
    val name: String?,
    @Json(name = "poster_path") val posterPath: String?,
    @Json(name = "backdrop_path") val backdropPath: String?,
    @Json(name = "media_type") val mediaType: String?, // "movie" or "tv"
    @Json(name = "vote_average") val voteAverage: Double?,
    @Json(name = "release_date") val releaseDate: String?,
    @Json(name = "first_air_date") val firstAirDate: String?,
    val overview: String?
)

@JsonClass(generateAdapter = true)
data class TmdbMovieDetails(
    val id: Int,
    val title: String?,
    @Json(name = "poster_path") val posterPath: String?,
    @Json(name = "backdrop_path") val backdropPath: String?,
    @Json(name = "vote_average") val voteAverage: Double?,
    val overview: String?,
    @Json(name = "release_date") val releaseDate: String?,
    val runtime: Int?,
    val genres: List<TmdbGenre>?,
    val credits: TmdbCredits?
)

@JsonClass(generateAdapter = true)
data class TmdbTvDetails(
    val id: Int,
    val name: String?,
    @Json(name = "poster_path") val posterPath: String?,
    @Json(name = "backdrop_path") val backdropPath: String?,
    @Json(name = "vote_average") val voteAverage: Double?,
    val overview: String?,
    @Json(name = "first_air_date") val firstAirDate: String?,
    val genres: List<TmdbGenre>?,
    val seasons: List<TmdbSeason>?,
    val credits: TmdbCredits?
)

@JsonClass(generateAdapter = true)
data class TmdbGenre(
    val id: Int,
    val name: String?
)

@JsonClass(generateAdapter = true)
data class TmdbSeason(
    val id: Int,
    val name: String?,
    @Json(name = "season_number") val seasonNumber: Int,
    @Json(name = "episode_count") val episodeCount: Int?,
    @Json(name = "poster_path") val posterPath: String?
)

@JsonClass(generateAdapter = true)
data class TmdbCredits(
    val cast: List<TmdbCast>?
)

@JsonClass(generateAdapter = true)
data class TmdbCast(
    val id: Int,
    val name: String?,
    @Json(name = "profile_path") val profilePath: String?
)

@JsonClass(generateAdapter = true)
data class TmdbSeasonDetails(
    val name: String?,
    @Json(name = "season_number") val seasonNumber: Int,
    val episodes: List<TmdbEpisode>?
)

@JsonClass(generateAdapter = true)
data class TmdbEpisode(
    val id: Int,
    val name: String?,
    @Json(name = "episode_number") val episodeNumber: Int,
    val overview: String?,
    @Json(name = "still_path") val stillPath: String?
)
