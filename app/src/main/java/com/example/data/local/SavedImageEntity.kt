package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_images")
data class SavedImageEntity(
    @PrimaryKey val id: String,                    // SHA-256 hash of sourceUrl
    val sourceUrl: String,                         // Original image URL from the web
    val pageUrl: String = "",                      // URL of the page where image was saved
    val pageTitle: String = "",                    // Page title at time of saving
    val localFilePath: String,                     // Absolute path inside app internal storage
    val fileSizeBytes: Long = 0L,                  // File size after download completes
    val downloadedAt: Long = System.currentTimeMillis()
)
