package com.aistudio.cinemios.fxtyr.data.remote.moviebox.models

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
    val sourceUrl: String? = null,
    val streamType: String = "mp4",
    val cookie: String? = null,
    val headers: Map<String, String>? = null
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

data class ItemDetailResult(
    val subjectId: String,
    val title: String,
    val description: String,
    val posterUrl: String,
    val rating: Double,
    val year: String,
    val type: String,
    val languages: List<String> = emptyList(),
    val country: String = "",
    val genre: List<String> = emptyList(),
    val seasonsCount: Int = 0,
    val durationSeconds: Int = 0,
    val hasResource: Boolean = false
)

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

/** One autocomplete entry returned by `/suggest`. */
data class SearchSuggestion(
    val text: String,
    val type: Int = 0
)

/** A titled group inside a homepage section (e.g. one ranking tab). */
data class HomeSectionGroup(
    val title: String,
    val results: List<SearchResult> = emptyList()
)

/** One horizontal row of the MovieBox homepage. */
data class HomeSection(
    val title: String,
    val type: String,
    val items: List<SearchResult> = emptyList(),
    val groups: List<HomeSectionGroup> = emptyList()
)

/** One "hot" category returned by `/popular_searches`. */
data class PopularSearchCategory(
    val category: String,
    val name: String,
    val results: List<SearchResult> = emptyList()
)

/** The "everyone searches" list plus hot Movie/TV rails. */
data class PopularSearches(
    val everyoneSearch: List<String> = emptyList(),
    val hot: List<PopularSearchCategory> = emptyList()
)

/** A tab offered by `/search_v2` (All, Movie, TV, Sports...). */
data class SearchV2Tab(
    val id: String,
    val name: String,
    val parentId: String = ""
)

/** A related-result group returned by `/search_v2`. */
data class SearchV2Group(
    val title: String,
    val topicType: String = "",
    val results: List<SearchResult> = emptyList()
)

/** Full payload of `/search_v2`: tabs, grouped extras, and flat results. */
data class SearchV2Result(
    val tabs: List<SearchV2Tab> = emptyList(),
    val groups: List<SearchV2Group> = emptyList(),
    val results: List<SearchResult> = emptyList(),
    val totalResults: Int = 0
)
