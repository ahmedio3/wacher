package com.example

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.example.auth.ActivityLogManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WatcheraApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        WorkManager.initialize(this, Configuration.Builder().build())
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        android.util.Log.i("WatcheraApplication", "IMGBB_API_KEY length: ${BuildConfig.IMGBB_API_KEY.length}")
        if (FirebaseAuth.getInstance().currentUser != null) {
            CoroutineScope(Dispatchers.IO).launch {
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                if (uid != null) {
                    ActivityLogManager.addLog(uid, "APP_OPENED", "App opened")
                }
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .crossfade(200)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .build()
    }
}
