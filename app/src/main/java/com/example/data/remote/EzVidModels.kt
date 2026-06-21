package com.example.data.remote

data class EzVidSubtitle(
    val label: String? = null,
    val file: String? = null,
    val url: String? = null,
    val lang: String? = null,
    val type: String? = null,
    val source: String? = null,
    val kind: String? = null
) {
    fun getSubtitleUrl(): String? = url ?: file
    fun getLanguageLabel(): String = label ?: lang ?: "Unknown"
}

// Keep EzVidProvider around just to compile PlayerScreen provider selector
// We will hide it from the UI or just leave it empty.
data class EzVidProvider(
    val id: String?,
    val name: String? = null
)
