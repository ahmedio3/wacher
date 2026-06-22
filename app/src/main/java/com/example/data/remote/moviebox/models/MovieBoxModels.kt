package com.example.data.remote.moviebox.models

data class SearchResult(
    val subjectId: String,
    val title: String,
    val type: String,                   // "movie" or "series"
    val posterUrl: String,
    val year: String,
    val hasResource: Boolean,
    val rating: Double = 0.0,           // IMDB rating
    val seasons: Int = 0,               // 0 for movies
    val country: String = "",
    val languages: List<String> = emptyList(),
    val genre: List<String> = emptyList(),
    val description: String = "",
    val durationSeconds: Int = 0
)

data class VideoFile(
    val url: String,
    val resolution: Int,
    val size: Long,                     // bytes (0 if unknown)
    val sizeString: String = "",        // original string e.g. "1.2GB"
    val season: Int,                    // 0 for movies
    val episode: Int,                   // 0 for movies
    val resourceId: String,
    val subtitlesAvailable: Boolean,
    val hasArabicSubtitle: Boolean,
    val arabicSubtitleUrl: String?,
    val allSubtitles: List<Subtitle>,
    val codec: String? = null,
    val duration: Int = 0,              // seconds
    val sourceUrl: String? = null
) {
    /**
     * Returns a human-readable size string. Falls back to sizeString if
     * sizeBytes is 0.
     */
    fun formattedSize(): String {
        if (sizeString.isNotEmpty()) return sizeString
        if (size <= 0) return ""
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var v = size.toDouble()
        var i = 0
        while (v >= 1024 && i < units.lastIndex) {
            v /= 1024
            i++
        }
        return String.format("%.1f %s", v, units[i])
    }
}

data class Subtitle(
    val languageCode: String,
    val languageName: String,
    val url: String,
    val size: Int = 0,
    val delay: Int = 0
)

data class SubtitleResponse(
    val hasArabic: Boolean,
    val arabicSubtitle: Subtitle?,
    val allSubtitles: List<Subtitle>,
    val totalLanguages: Int
)
