package com.example.data.remote

import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

object ImgBBUploader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Uploads a [bitmap] to ImgBB and returns the hosted image URL, or null on failure.
     * Runs on [Dispatchers.IO].
     */
    suspend fun uploadImage(bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.IMGBB_API_KEY
        if (apiKey.isBlank()) {
            android.util.Log.w("ImgBBUploader", "IMGBB_API_KEY is not configured — skipping upload")
            return@withContext null
        }

        try {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val imageBytes = outputStream.toByteArray()
            val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

            val requestBody = base64Image.toRequestBody("text/plain".toMediaType())

            val request = Request.Builder()
                .url("https://api.imgbb.com/1/upload?key=$apiKey")
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .post("image=$base64Image".toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null

            if (!response.isSuccessful) {
                android.util.Log.e("ImgBBUploader", "Upload failed: $response — $body")
                return@withContext null
            }

            val json = JSONObject(body)
            if (json.optBoolean("success", false)) {
                json.getJSONObject("data").optString("url", null)
            } else {
                android.util.Log.e("ImgBBUploader", "ImgBB returned success=false: $body")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("ImgBBUploader", "Upload error", e)
            null
        }
    }
}
