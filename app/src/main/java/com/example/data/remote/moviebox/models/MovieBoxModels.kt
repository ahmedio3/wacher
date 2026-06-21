package com.example.data.remote.moviebox.models

data class SearchResult(
    val subjectId: String,
    val title: String,
    val type: String,
    val posterUrl: String,
    val year: String,
    val hasResource: Boolean
)

data class VideoFile(
    val url: String,
    val resolution: Int,
    val size: Long,
    val season: Int,
    val episode: Int,
    val resourceId: String,
    val subtitlesAvailable: Boolean,
    val hasArabicSubtitle: Boolean,
    val arabicSubtitleUrl: String?,
    val allSubtitles: List<Subtitle>
)

data class Subtitle(
    val languageCode: String,
    val languageName: String,
    val url: String
)

data class SubtitleResponse(
    val hasArabic: Boolean,
    val arabicSubtitle: Subtitle?,
    val allSubtitles: List<Subtitle>,
    val totalLanguages: Int
)
