<div align="center">

# 🎬 Watchera

**A feature-rich Android streaming & downloading app for movies and TV shows**

[![Build Debug APK](https://github.com/ahmedio3/wacher/actions/workflows/build.yml/badge.svg)](https://github.com/ahmedio3/wacher/actions/workflows/build.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-blue.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-green.svg)](https://developer.android.com/jetpack/compose)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg)](https://developer.android.com/about/versions/nougat)

<br/>

An Arabic-first (RTL) Android application built with **Jetpack Compose** and **Material Design 3** that lets you browse, search, stream, and download movies & TV shows. Powered by **TMDB** for metadata and **MovieBox** for streaming/downloading sources.

</div>

---

## ✨ Features

### 🎥 Streaming & Playback
- **Online Streaming** — Stream movies and TV episodes via ExoPlayer (Media3) with HLS (`.m3u8`) and progressive MP4 support
- **Offline Playback** — Full-featured offline video player with cinema-style landscape UI
- **Smart Controls** — Double-tap seek, long-press 2x speed, auto-hide controls, resume from last position

### 📥 Downloads
- **Multi-threaded Downloader** — Custom 8-thread parallel HTTP range-request downloader for maximum speed
- **Pause/Resume** — Download progress persisted to disk; resume downloads after app restart
- **Quality Selection** — Choose from 360p, 480p, 720p, or 1080p
- **Save to Gallery** — Export downloaded videos to device gallery

### 🔍 Search & Discovery
- **TMDB Integration** — Browse popular movies and TV shows with Arabic language support
- **MovieBox Search** — Search across MovieBox's extensive catalog
- **Smart Scoring** — Results ranked by language match, title relevance, and content availability
- **Custom Sections** — Admin-managed homepage promotional cards via Firebase

### 💬 Subtitles
- **Multi-language Subtitles** — Fetch subtitles in Arabic and other languages from MovieBox
- **Custom Subtitle Engine** — SRT/VTT parser with adjustable position, timing sync, and font size
- **Arabic Priority** — Arabic subtitles automatically detected and prioritized

### 🗨️ Social
- **Global Chat** — Real-time chat powered by Firebase Realtime Database
- **Swipe-to-Reply** — iMessage-style reply gesture on chat bubbles
- **Typing Indicators** — See when other users are typing
- **Push Notifications** — Background service for new messages and admin broadcasts

### 👤 User System
- **Google Sign-In** — Authentication via Firebase Auth + Credential Manager
- **Email/Password Auth** — Traditional login option
- **User Profiles** — Customizable avatars, display names, and unique usernames
- **Watchlist** — Save movies and shows for later (stored locally via Room)

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    UI Layer (Compose)                    │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────┐  │
│  │   Home   │ │  Detail  │ │  Player  │ │  Chat/    │  │
│  │  Screen  │ │  Screen  │ │  Screen  │ │  Settings │  │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └─────┬─────┘  │
├───────┼────────────┼───────────┼──────────────┼─────────┤
│       │      ViewModel Layer   │              │         │
│  ┌────┴────────────────────────┴──────────────┴──────┐  │
│  │           MovieViewModel / MovieBoxViewModel       │  │
│  └────┬────────────────────────┬─────────────────────┘  │
├───────┼────────────────────────┼────────────────────────┤
│       │      Repository Layer   │                       │
│  ┌────┴──────────┐   ┌─────────┴──────────┐            │
│  │ MovieRepository│   │MovieBoxRepository  │            │
│  └────┬──────────┘   └─────────┬──────────┘            │
├───────┼────────────────────────┼────────────────────────┤
│       │         Data Layer      │                       │
│  ┌────┴──────┐ ┌──────┴──────┐ ┌┴──────────────┐       │
│  │   TMDB    │ │  MovieBox   │ │    Firebase    │       │
│  │  (Retrofit)│ │ (OkHttp +   │ │ (Auth + RTDB) │       │
│  │           │ │  FastAPI)   │ │               │       │
│  └───────────┘ └─────────────┘ └───────────────┘       │
│                                                         │
│  ┌───────────────────────────────────────────────────┐  │
│  │  Local Storage (Room DB)                          │  │
│  │  • watchlist  • downloads  • chat_messages        │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

**Pattern:** MVVM with Repository layer  
**State Management:** Kotlin `StateFlow` with sealed `RequestState<T>` (Idle, Loading, Success, Error)

---

## 🛠️ Tech Stack

| Category | Technology | Version |
|----------|-----------|---------|
| **Language** | Kotlin | 2.2.10 |
| **UI** | Jetpack Compose (Material 3) | BOM 2024.09.00 |
| **Build** | Gradle (Kotlin DSL) | 9.3.1 |
| **Min SDK** | Android 7.0 (API 24) | — |
| **Target SDK** | Android 16 (API 36) | — |
| **Video Player** | ExoPlayer (Media3) | 1.4.1 |
| **Networking** | Retrofit 2 + OkHttp 4 | 2.12.0 / 4.10.0 |
| **JSON** | Moshi (KSP codegen) | 1.15.2 |
| **Images** | Coil Compose | 2.7.0 |
| **Database** | Room | 2.7.0 |
| **Navigation** | Navigation Compose | 2.8.9 |
| **Auth** | Firebase Auth + Google ID | BOM 34.12.0 |
| **Realtime DB** | Firebase Realtime Database | BOM 34.12.0 |
| **Fonts** | IBM Plex Sans Arabic (Google Fonts) | — |
| **Coroutines** | Kotlinx Coroutines | 1.10.2 |

---

## 📱 Screens

| Screen | Description |
|--------|-------------|
| **Splash** | Animated branding with 2.2s auto-advance |
| **Home** | Featured carousel, popular movies/TV, search (TMDB + MovieBox), custom sections |
| **Detail** | Movie/TV info: backdrop, poster, metadata, cast, season/episode browser |
| **Player** | Online streaming with ExoPlayer, subtitle selection, fullscreen |
| **Offline Player** | Cinema-style landscape UI, custom subtitle overlay, episode drawer |
| **Downloads** | Offline download management with segmented tabs, progress tracking |
| **Explore** | Chat hub with AI Chat (coming soon) and Global Chat |
| **Global Chat** | Real-time messaging with swipe-to-reply, typing indicators |
| **Settings** | Profile, language toggle, watchlist, admin panel |
| **Watchlist** | Saved items in 3-column poster grid |

---

## 📂 Project Structure

```
app/src/main/java/com/example/
├── MainActivity.kt
├── auth/
│   ├── AuthManager.kt          # Firebase Auth + Google Sign-In
│   ├── ChatManager.kt          # Firebase RTDB chat operations
│   └── UserManager.kt          # User profile management
├── data/
│   ├── local/
│   │   ├── MovieEntities.kt    # Room entities (watchlist, downloads, chat)
│   │   ├── MovieDao.kt         # Room DAO
│   │   └── MovieDatabase.kt    # Room database
│   ├── remote/
│   │   ├── ApiServices.kt      # TMDB Retrofit interface
│   │   ├── RetrofitClient.kt   # Retrofit singleton
│   │   ├── TmdbModels.kt       # TMDB response models
│   │   ├── moviebox/            # MovieBox integration
│   │   │   ├── api/             # API implementation
│   │   │   ├── crypto/          # HMAC-MD5 signing
│   │   │   ├── models/          # Data models
│   │   │   ├── network/         # HTTP client with failover
│   │   │   ├── repository/      # Caching repository
│   │   │   └── viewmodel/       # MovieBox ViewModel
│   │   └── repository/
│   │       └── MovieRepository.kt
│   └── models/
│       └── ChatMessage.kt
├── ui/
│   ├── components/
│   │   ├── SkeletonUI.kt       # Loading skeleton
│   │   ├── VideoPlayerView.kt  # ExoPlayer wrapper
│   │   └── moviebox/
│   │       └── MovieBoxDownloadSheet.kt
│   ├── screens/                 # 13 screen composables
│   ├── theme/
│   │   ├── Color.kt            # Comfort Beige theme
│   │   ├── Theme.kt
│   │   └── Type.kt
│   └── viewmodel/
│       ├── MovieViewModel.kt
│       ├── SubtitleHelper.kt   # Subtitle fetching & extraction
│       └── SubtitleParser.kt   # SRT/VTT parser
└── utils/
    ├── ChatNotificationService.kt  # Foreground notification service
    └── MultiThreadDownloader.kt    # 8-thread parallel downloader
```

---

## 🚀 Getting Started

### Prerequisites
- [Android Studio](https://developer.android.com/studio) (Ladybug or newer)
- JDK 17+
- Android SDK 36

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/ahmedio3/wacher.git
   cd wacher
   ```

2. **Configure environment**
   ```bash
   cp .env.example .env
   # Edit .env if needed (GEMINI_API_KEY is optional)
   ```

3. **Open in Android Studio**
   - Select **Open** → choose the project directory
   - Allow Android Studio to resolve dependencies
   - Sync Gradle

4. **Run**
   - Select a device/emulator (API 24+)
   - Click ▶️ Run

### Build from CLI
```bash
# Debug APK
./gradlew assembleDebug

# Release APK (requires signing config)
./gradlew assembleRelease
```

---

## 🔧 Configuration

### Environment Variables (`.env`)
| Variable | Required | Description |
|----------|----------|-------------|
| `GEMINI_API_KEY` | No | Gemini AI API key (currently unused) |

### Signing (Release builds)
| Variable | Description |
|----------|-------------|
| `KEYSTORE_PATH` | Path to release keystore file |
| `STORE_PASSWORD` | Keystore password |
| `KEY_PASSWORD` | Key password |

---

## 🌐 Backend API

This app uses the **[moviebox-fastapi](https://github.com/ahmedio3/moviebox-fastapi)** backend for MovieBox integration:

| Endpoint | Purpose |
|----------|---------|
| `GET /search` | Search movies/series |
| `GET /get_download_links` | Get streaming URLs + subtitles |
| `GET /get_subtitles` | Get subtitle files (fallback) |

**Base URL:** `https://moviebox-fastapi.vercel.app`

---

## 🎨 Design

- **Theme:** Custom "Comfort Beige" — warm caramel-brown primary (`#8C6D4F`), oatmeal surfaces, gold accents
- **Font:** IBM Plex Sans Arabic (via Google Fonts)
- **Layout:** Full RTL (Right-to-Left) support; player screens switch to LTR
- **Style:** Material Design 3 with dynamic color support

---

## 🧪 Testing

```bash
# Unit tests
./gradlew test

# Instrumented tests
./gradlew connectedAndroidTest

# Screenshot tests (Roborazzi)
./gradlew recordRoborazziDebug
```

---

## 📋 CI/CD

GitHub Actions workflow (`.github/workflows/build.yml`):
- Triggers on push to `main` / `master`
- Builds debug APK on `ubuntu-latest`
- Uses Java 17 (Temurin) + Gradle 9.3.1
- Uploads APK as artifact

---

## 📄 License

This project is for educational purposes. All movie/TV content metadata is provided by [TMDB](https://www.themoviedb.org/) and [MovieBox](https://moviebox.ph/).

---

<div align="center">

**Built with ❤️ using Kotlin & Jetpack Compose**

[Report Bug](https://github.com/ahmedio3/wacher/issues) · [Request Feature](https://github.com/ahmedio3/wacher/issues)

</div>
