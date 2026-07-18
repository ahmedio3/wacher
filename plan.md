# Automated Background Subtitle Fetcher — Blueprint

## 1. Current Architecture Overview

### Directory Structure (copied to `/sdcard/gemini-need/`)

```
app/src/main/java/com/example/
├── data/
│   ├── local/
│   │   ├── MovieEntities.kt          # SubtitleDownloadEntity, DownloadEntity, WatchlistEntity
│   │   ├── MovieDao.kt               # Room DAO (subtitle downloads CRUD)
│   │   └── MovieDatabase.kt          # Room DB (v9→v13 migrations)
│   ├── repository/
│   │   └── MovieRepository.kt        # DAO wrapper + TMDB remote calls
│   └── remote/
│       ├── EzVidModels.kt            # EzVidSubtitle model
│       └── moviebox/models/
│           └── MovieBoxModels.kt     # VideoFile, Subtitle models
├── ui/
│   ├── components/
│   │   ├── SubtitleSourceSheet.kt    # 3-page subtitle source picker (MovieBox/Subdl/OpenSubtitles)
│   │   ├── SubtitleBatchSheet.kt     # Batch detail bottom sheet
│   │   ├── SubtitleBatchCard.kt      # Batch group card UI
│   │   ├── DownloadedSubtitleBrowser.kt  # Browser for downloaded subs grouped by batch
│   │   ├── SubtitleEpisodeRow.kt     # Episode row for browsing
│   │   ├── SubtitleDownloadEpisodeRow.kt # Downloaded subtitle item row
│   │   ├── VideoPlayerView.kt        # ExoPlayer Compose wrapper
│   │   └── moviebox/MovieBoxDownloadSheet.kt
│   ├── screens/
│   │   ├── PlayerScreen.kt           # Online player (MovieBox streams)
│   │   ├── OfflinePlayerScreen.kt    # Offline player (downloaded files, custom subs)
│   │   ├── SubtitleDownloadsScreen.kt # Full subtitle downloads management screen
│   │   └── DownloadsScreen.kt        # Downloads management screen
│   └── viewmodel/
│       ├── MovieViewModel.kt         # Central ViewModel (downloads, subs, watchlist, etc.)
│       ├── SubtitleHelper.kt         # Subtitle fetching from MovieBox/Subdl/OpenSubtitles
│       └── SubtitleParser.kt         # SRT/VTT/ASS parser
├── utils/
│   └── MultiThreadDownloader.kt      # Multi-threaded download manager
└── MainActivity.kt                   # Navigation, download overlay
```

### Key Data Flow

```
SubtitleSourceSheet → SubtitleHelper.fetchSubtitles() / fetchSubdlSubtitles() / fetchOpenSubtitles()
                      ↓
           SubtitleHelper.downloadSubtitleStandalone() / downloadAndExtractSubtitle()
                      ↓
           onSubtitleLoaded(file, lang, code, source, name, matchedEp, batchId)
                      ↓
           MovieViewModel → MovieDao.insertSubtitleDownload() → Room DB
                      ↓
           subtitleBatchGroups StateFlow → DownloadedSubtitleBrowser
```

### SubtitleDownloadEntity (Room table: `subtitle_downloads`)
```kotlin
@PrimaryKey val id: String              // tmdbId_s{season}e{episode}_{langCode}[_{unique}]
val tmdbId: String, title: String, mediaType: String, posterPath: String
val season: Int, episode: Int, language: String, languageCode: String
val source: String, localFilePath: String, fileName: String
val batchId: String, originalUrl: String, downloadedAt: Long
```

### How subtitles currently load in players

**PlayerScreen (Online):**
- Resolves MovieBox download link → gets `arabicSubtitleUrl` or `allSubtitles` from MovieBox API
- Downloads subtitle via `SubtitleHelper.downloadAndExtractSubtitle()` to `context.filesDir/downloads/$activeId.srt`
- Sets as ExoPlayer `MediaItem.SubtitleConfiguration`
- Auto-selects Arabic sub if available

**OfflinePlayerScreen (Offline):**
- On media load, checks for `downloads/$activeId.srt` or `.vtt`
- Auto-fetches Arabic subtitle via `SubtitleHelper.fetchSubtitles()` + `downloadAndExtractSubtitle()` if none found
- Custom overlay rendering using `SubtitleParser.parseBlock()` → renders text in compose
- Has a full subtitle drawer (page 0-6) with search, browser, file picker

### Current subtitle sources
1. **MovieBox** — via `findResourceId()` + `fetchSubtitlesByResource()` (internal API)
2. **Subdl** — `fetchSubdlSubtitles()` using `api.subdl.com` with `SUBDL_API_KEY`
   - Supports `season_number` & `episode_number` params
   - Returns ZIP archives with individual episode files + `unpack_files` array
3. **OpenSubtitles** — `fetchOpenSubtitles()` using `api.opensubtitles.com` with `OPENSUBTITLES_API_KEY`

---

## 2. Proposed: Automated Background Subtitle Fetcher

### Goal
When a user downloads episodes (via `DownloadsScreen` or `MovieBoxDownloadSheet`), automatically fetch & store matching Arabic subtitles in the background using **WorkManager**, without manual intervention.

### Design Principle: Reuse, Don't Rewrite
Do **NOT** rewrite `SubtitleSourceSheet`, `SubtitleHelper`, `SubtitleParser`, or any existing UI. Add a thin orchestration layer.

---

## 3. Trigger Point Analysis

### ❌ What NOT to do
Observing `downloads` Flow from `MovieViewModel.init{}` to detect completions. The Flow re-emits the full list on every DB write (including progress updates), so every subscription would re-process every already-completed download every time the app restarts or any download field changes.

### ✅ The correct trigger
There is **already** a primitive auto-subtitle fetch in `MovieViewModel.triggerNetworkDownload()` at lines 782–797:

```kotlin
onComplete = { success ->
    if (success) {
        repository.addDownload(current.copy(status = "completed", ...))

        // === PRIMITIVE EXISTING AUTO-SUBTITLE CODE (lines 782-797) ===
        viewModelScope.launch(Dispatchers.IO) {
            // Uses MovieBox only, no Room insertion, no Subdl fallback
            val subs = SubtitleHelper.fetchSubtitles(...)
            val arSub = subs.firstOrNull()
            if (arSub != null) {
                SubtitleHelper.downloadAndExtractSubtitle(ctx, arSub.url, current.id)
            }
        }
    }
}
```

This is called exactly **once** per completed download, inside the `onComplete` callback of `MultiThreadDownloader.startDownload()`. This is the correct single-shot trigger point.

### What to change
**Replace** the inline MovieBox-only code (lines 782–797) with an **enqueue** call to a `WorkManager` worker:

```kotlin
onComplete = { success ->
    if (success) {
        repository.addDownload(current.copy(status = "completed", ...))
        // Instead of inline code, enqueue worker:
        if (prefs.getBoolean("auto_subtitle_enabled", true)) {
            val workRequest = OneTimeWorkRequestBuilder<SubtitleAutoWorker>()
                .setInputData(workDataOf(
                    "mediaId" to current.mediaId,
                    "tmdbId" to (if (isTv) current.mediaId.substringBefore("-s") else current.mediaId),
                    "downloadId" to current.id,
                    "title" to current.title,
                    "mediaType" to current.mediaType,
                    "season" to current.season,
                    "episode" to current.episode,
                    "posterPath" to current.posterPath
                ))
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag("subtitle_auto_${current.id}")
                .build()
            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
```

This guarantees:
- **Single execution** — not a Flow subscription
- **Network requirement** — `Constraints.NETWORK_TYPE_CONNECTED`
- **Retry** — exponential backoff if network fails mid-job
- **Survives app kill** — WorkManager persists the task

---

## 4. SubtitleAutoWorker (New File)

**Path:** `app/src/main/java/com/example/worker/SubtitleAutoWorker.kt`

```kotlin
class SubtitleAutoWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val tmdbId = inputData.getString("tmdbId") ?: return Result.failure()
        val downloadId = inputData.getString("downloadId") ?: return Result.failure()
        val title = inputData.getString("title") ?: ""
        val mediaType = inputData.getString("mediaType") ?: "movie"
        val season = inputData.getInt("season", 0)
        val episode = inputData.getInt("episode", 0)
        val posterPath = inputData.getString("posterPath") ?: ""
        val isTv = mediaType == "tv"

        // 1. Fetch from Subdl (primary) — supports season Zips
        val subdlSubs = SubtitleHelper.fetchSubdlSubtitles(tmdbId, isTv, season, episode)
        var arSub = subdlSubs.firstOrNull { it.langCode == "ar" }
            ?: subdlSubs.firstOrNull { it.name.contains("Arabic", true) }

        // 2. Fallback: MovieBox
        if (arSub == null) {
            val movieBoxSubs = SubtitleHelper.fetchSubtitles(tmdbId, isTv, season, episode, title)
            arSub = movieBoxSubs.firstOrNull { it.langCode == "ar" }
                ?: movieBoxSubs.firstOrNull()
        }

        // 3. Fallback: OpenSubtitles
        if (arSub == null) {
            val openSubs = SubtitleHelper.fetchOpenSubtitles(tmdbId, isTv, season, episode)
            arSub = openSubs.firstOrNull { it.langCode == "ar" }
                ?: openSubs.firstOrNull()
        }

        if (arSub == null) return Result.retry() // No subs yet — retry later

        // 4. Download & extract using existing helper
        val downloadUrl = if (arSub.fileId != null)
            SubtitleHelper.getOpenSubtitleDownloadUrl(arSub.fileId) ?: arSub.url
        else arSub.url

        val files = SubtitleHelper.downloadSubtitleStandalone(
            applicationContext, downloadUrl, downloadId
        )

        // 5. Save each extracted file to Room with deterministic ID
        val db = MovieDatabase.getDatabase(applicationContext)
        val tmdbBase = if (isTv) tmdbId else tmdbId
        val batchId = "auto_${downloadId}"

        for ((file, matchedEp) in files) {
            val ep = if (matchedEp > 0) matchedEp else episode
            val id = if (isTv)
                "${tmdbBase}_s${season}e${ep}_${arSub.langCode}"
            else
                "${tmdbBase}_${arSub.langCode}"

            val entity = SubtitleDownloadEntity(
                id = id,                            // ← deterministic: OnConflictStrategy.REPLACE dedup
                tmdbId = tmdbId,
                title = title,
                mediaType = mediaType,
                posterPath = posterPath,
                season = season,
                episode = ep,
                language = arSub.lang,
                languageCode = arSub.langCode,
                source = arSub.source,
                localFilePath = file.absolutePath,
                fileName = arSub.name,
                batchId = batchId,
                originalUrl = downloadUrl,
                downloadedAt = System.currentTimeMillis()
            )
            db.movieDao.insertSubtitleDownload(entity)

            // 6. Copy to player-accessible path for auto-load
            val playerFile = File(
                applicationContext.filesDir,
                "downloads/${if (isTv) "${tmdbBase}-s${season}-e${ep}" else tmdbBase}" +
                if (file.name.endsWith(".vtt")) ".vtt" else ".srt"
            )
            playerFile.parentFile?.mkdirs()
            file.copyTo(playerFile, overwrite = true)
        }

        return Result.success()
    }
}
```

### Why deterministic IDs prevent duplication
- Auto-download ID: `tmdbId_s{season}e{episode}_{langCode}` (e.g., `12345_s2e5_ar`)
- Manual download IDs: `tmdbId_s{season}e{episode}_{langCode}_{timestamp}` (current format)
- Since `insertSubtitleDownload` uses `OnConflictStrategy.REPLACE`, repeated WorkManager retries simply overwrite the same row.
- A subsequent manual download of the same episode+language will get a unique ID (timestamp suffix), so no conflict.

---

## 5. WorkManager Registration

**File:** `WatcheraApplication.kt`

```kotlin
class WatcheraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WorkManager.initialize(this, Configuration.Builder().build())
    }
}
```

In `app/build.gradle.kts`, add dependency:
```kotlin
implementation("androidx.work:work-runtime-ktx:2.9.x")
```

---

## 6. Settings Toggle

**File:** `app/src/main/java/com/example/ui/screens/SettingsScreen.kt`

Add a preference row:
```kotlin
// --- Auto subtitle toggle ---
var autoSubEnabled by remember {
    mutableStateOf(prefs.getBoolean("auto_subtitle_enabled", true))
}
Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Column(modifier = Modifier.weight(1f)) {
        Text("تنزيل الترجمة تلقائياً", style = MaterialTheme.typography.bodyLarge)
        Text(
            "للمحتوى المُحمَّل محلياً فقط (لا يشمل البث المباشر). " +
            "سيتم البحث عن ترجمة عربية من Subdl و MovieBox و OpenSubtitles فور اكتمال التحميل.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            maxLines = 3
        )
    }
    Switch(
        checked = autoSubEnabled,
        onCheckedChange = {
            autoSubEnabled = it
            prefs.edit().putBoolean("auto_subtitle_enabled", it).apply()
        }
    )
}
```

Expose via `MovieViewModel` if needed, or read `SharedPreferences` directly in the `onComplete` callback (simpler, avoids extra StateFlow overhead for a rarely-changing pref).

---

## 7. Subdl Season ZIP Handling (Phase 3 — Fully Reusable)

`SubtitleHelper.downloadSubtitleStandalone()` already handles ZIP extraction and episode matching via regex (`E03`, `S01E03`, `1x03`). No changes needed.

When Subdl receives `season_number=X` without `episode_number`, the Subdl API may return:
- A single ZIP containing all episode subtitle files for that season
- Individual `unpack_files` entries per episode

`downloadSubtitleStandalone()` handles both: it extracts ALL `.srt`/`.vtt` from ZIPs and returns `List<Pair<File, Int>>`. The `SubtitleAutoWorker` iterates these and creates one `SubtitleDownloadEntity` per file, each with the correct episode-specific deterministic ID.

---

## 8. Auto-Load in Players (Phase 4 — Minor Enrichments)

### PlayerScreen (Online)
Already checks `downloads/$activeId.srt/.vtt` at lines 92–99. The `SubtitleAutoWorker` now also copies files there (step 6 in the worker). No code changes needed — it will auto-discover the downloaded sub.

### OfflinePlayerScreen (Offline)
Already auto-fetches at lines 336–353 using MovieBox only. Enrich the fallback chain:
```kotlin
// After MovieBox attempt fails, try finding auto-downloaded sub
if (parsedSubtitles.isEmpty()) {
    val dir = File(context.filesDir, "standalone_subtitles")
    val autoFiles = dir.listFiles()?.filter {
        it.name.startsWith(downloadId) && (it.name.endsWith(".srt") || it.name.endsWith(".vtt"))
    }
    autoFiles?.firstOrNull()?.let { file ->
        parsedSubtitles = SubtitleParser.parseBlock(file)
    }
}
```

---

## 9. Edge Cases

| Edge Case | Solution |
|-----------|----------|
| No internet at completion time | WorkManager `Constraints.NETWORK_TYPE_CONNECTED` + `EXPONENTIAL` backoff (30s → 60s → 120s…) |
| Subdl ZIP with 20+ files | Existing loop in worker iterates all; each gets deterministic ID → REPLACE dedup |
| Repeat download of same episode | Worker runs again; `REPLACE` overwrites old subtitle row with new file |
| Worker fails after 3 retries | `Result.failure()` — user can manually fetch via SubtitleSourceSheet |
| User disables auto-download pref | Check pref before enqueuing worker in `onComplete` |
| Manual subtitle already loaded | Different ID (has timestamp suffix); no conflict with auto ID |
| Subdl API rate limiting | Existing 500ms delay inside `fetchSubdlSubtitles` is sufficient |

---

## 10. Optional: Backfill Old Downloads (Ask User)

**If user opts in:** Enumerate all `DownloadEntity` with `status = "completed"`, filter those that have no matching `SubtitleDownloadEntity` in Room, and enqueue staggered `SubtitleAutoWorker` jobs (one every 2 seconds to avoid API rate limits).

### Implementation sketch

In `SettingsScreen.kt`, add below the toggle:

```kotlin
// --- Backfill button ---
var isBackfilling by remember { mutableStateOf(false) }
OutlinedButton(
    onClick = {
        isBackfilling = true
        viewModelScope.launch(Dispatchers.IO) {
            val completedDownloads = repository.downloads.first()
                .filter { it.status == "completed" && it.mediaType == "tv" }
            val existingSubs = repository.subtitleDownloads.first()
            val needsSub = completedDownloads.filter { dl ->
                val autoId = "${dl.mediaId}_s${dl.season}e${dl.episode}_ar"
                existingSubs.none { it.id == autoId }
            }
            var delay = 0L
            for (dl in needsSub) {
                WorkManager.getInstance(context)
                    .enqueue(OneTimeWorkRequestBuilder<SubtitleAutoWorker>()
                        .setInputData(workDataOf(...))
                        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                        .build())
                delay += 2000
            }
            isBackfilling = false
        }
    },
    enabled = !isBackfilling,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
) {
    if (isBackfilling) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
    else Text("تعقب الحلقات القديمة بدون ترجمة")
}
```

---

## 11. Summary of Changes

| File | Change |
|------|--------|
| **New:** `worker/SubtitleAutoWorker.kt` | ~100 lines — CoroutineWorker that fetches + extracts + saves subtitles |
| `MovieViewModel.kt:782-797` | Replace inline MovieBox fetch with WorkManager enqueue (6 lines) |
| `SettingsScreen.kt` | Add toggle + optional backfill button |
| `OfflinePlayerScreen.kt` | Enrich auto-find to check `standalone_subtitles/` (5 lines) |
| `WatcheraApplication.kt` | Add WorkManager init (1 line) |
| `app/build.gradle.kts` | Add `work-runtime-ktx` dependency |

**Total new code:** ~150 lines (worker) + ~40 lines (settings & integration) + ~5 lines (player enrichment).

**Zero changes to:** `SubtitleHelper`, `SubtitleParser`, `SubtitleSourceSheet`, `SubtitleBatchSheet`, `SubtitleBatchCard`, `DownloadedSubtitleBrowser`, `SubtitleEpisodeRow`, `SubtitleDownloadEpisodeRow`, `SubtitleDownloadsScreen`, `PlayerScreen` (online), `MovieDao`, `MovieDatabase`, `MovieEntities`, `MovieRepository`, `MultiThreadDownloader`.

---

## 12. Files Copied to `/sdcard/gemini-need/`

| Source Path | Description |
|---|---|
| `app/src/main/java/com/example/data/local/MovieEntities.kt` | Room entities |
| `app/src/main/java/com/example/data/local/MovieDao.kt` | Room DAO |
| `app/src/main/java/com/example/data/local/MovieDatabase.kt` | Room database |
| `app/src/main/java/com/example/data/repository/MovieRepository.kt` | Repository |
| `app/src/main/java/com/example/ui/viewmodel/SubtitleHelper.kt` | Subtitle fetching & extraction |
| `app/src/main/java/com/example/ui/viewmodel/SubtitleParser.kt` | Subtitle parser |
| `app/src/main/java/com/example/ui/viewmodel/MovieViewModel.kt` | Central ViewModel |
| `app/src/main/java/com/example/ui/components/SubtitleSourceSheet.kt` | Subtitle source picker |
| `app/src/main/java/com/example/ui/components/SubtitleBatchSheet.kt` | Batch detail sheet |
| `app/src/main/java/com/example/ui/components/SubtitleBatchCard.kt` | Batch card |
| `app/src/main/java/com/example/ui/components/DownloadedSubtitleBrowser.kt` | Subtitle browser |
| `app/src/main/java/com/example/ui/components/SubtitleEpisodeRow.kt` | Episode row |
| `app/src/main/java/com/example/ui/components/SubtitleDownloadEpisodeRow.kt` | Downloaded row |
| `app/src/main/java/com/example/ui/screens/SubtitleDownloadsScreen.kt` | Subtitle screen |
| `app/src/main/java/com/example/ui/screens/PlayerScreen.kt` | Online player |
| `app/src/main/java/com/example/ui/screens/OfflinePlayerScreen.kt` | Offline player |
| `app/src/main/java/com/example/ui/components/VideoPlayerView.kt` | Player wrapper |
| `app/src/main/java/com/example/utils/MultiThreadDownloader.kt` | Download manager |
| `app/src/main/java/com/example/ui/components/moviebox/MovieBoxDownloadSheet.kt` | Download sheet |
| `app/src/main/java/com/example/data/remote/EzVidModels.kt` | Subtitle models |
| `app/src/main/java/com/example/ui/viewmodel/MovieViewModel.kt` | ViewModel (full) |
