<div align="center">

# 🎬 واتشر — Watcher (Cinemios)

**تطبيق Android متكامل لعرض وتحميل الأفلام والمسلسلات مع دعم كامل للترجمة العربية**

[![Build Debug APK](https://github.com/ahmedio3/wacher/actions/workflows/build.yml/badge.svg)](https://github.com/ahmedio3/wacher/actions/workflows/build.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-blue.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-green.svg)](https://developer.android.com/jetpack/compose)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg)](https://developer.android.com/about/versions/nougat)
[![License](https://img.shields.io/badge/License-Educational-purple.svg)](LICENSE)

</div>

---

## 📋 جدول المحتويات

- [🎯 نظرة عامة](#-نظرة-عامة)
- [✨ المميزات بالتفصيل](#-المميزات-بالتفصيل)
- [🏗️ بنية التطبيق (Architecture)](#️-بنية-التطبيق-architecture)
- [📱 جميع الشاشات](#-جميع-الشاشات)
- [📂 هيكل المشروع الكامل](#-هيكل-المشروع-الكامل)
- [🛠️ التقنيات المستخدمة (Tech Stack)](#️-التقنيات-المستخدمة-tech-stack)
- [💬 نظام الترجمة (3 مصادر)](#-نظام-الترجمة-3-مصادر)
- [📥 نظام التحميل متعدد الخيوط](#-نظام-التحميل-متعدد-الخيوط)
- [🤖 نظام AI Chat](#-نظام-ai-chat)
- [🔐 نظام المصادقة](#-نظام-المصادقة)
- [🎬 تكامل MovieBox](#-تكامل-moviebox)
- [🔥 تكامل Firebase](#-تكامل-firebase)
- [🚀 البدء مع المشروع](#-البدء-مع-المشروع)
- [🔧 الإعدادات والبيئة](#-الإعدادات-والبيئة)
- [📋 CI/CD و GitHub Actions](#-cicd-و-github-actions)
- [🧪 الاختبارات](#-الاختبارات)
- [📝 سجل التحديثات](#-سجل-التحديثات)

---

## 🎯 نظرة عامة

**واتشر** هو تطبيق Android مبني بالكامل باستخدام **Jetpack Compose** و **Material Design 3** مع دعم كامل للغة العربية (RTL). يتيح التطبيق تصفح الأفلام والمسلسلات، مشاهدتها عبر البث المباشر (streaming)، تحميلها للمشاهدة بدون إنترنت، مع نظام ترجمة متكامل يدعم 3 مصادر مختلفة.

**البيانات الوصفية** (posters, descriptions, cast) تأتي من **TMDB**.
**مصادر البث والتحميل** تأتي من **MovieBox API** عبر Backend وسيط.
**الترجمة** من 3 مصادر: MovieBox (الأساسي)، Subdl، و OpenSubtitles.
**المحادثة الجماعية** عبر Firebase Realtime Database.
**AI Chat** عبر أي مزود متوافق مع OpenAI API.

### 📊 إحصائيات المشروع

| المقياس | القيمة |
|---------|--------|
| إجمالي ملفات Kotlin | ~55 ملف |
| إجمالي أسطر الكود | ~14,000 سطر |
| عدد الشاشات | 16 شاشة |
| عدد الـ Composables | 25+ |
| APIs خارجية | TMDB + MovieBox + Subdl + OpenSubtitles + Firebase |

---

## ✨ المميزات بالتفصيل

### 🎥 1. نظام البث والمشاهدة

#### التشغيل عبر الإنترنت (Online Streaming)
- **ExoPlayer / Media3** — المشغل الأساسي
- **HLS (m3u8)** + **MP4 progressive** — صيغ البث المدعومة
- **جودة متعددة** — 360p, 480p, 720p, 1080p (حسب المصدر)
- **واجهة مشاهدة كاملة** — PlayerScreen مع تحكمات متقدمة
- **إيماءات التحكم**:
  - **نقر مزدوج (Double-tap)** — التقديم / التأخير 10 ثوانٍ (الجهة اليمنى واليسرى)
  - **ضغط مطول (Long-press)** — تشغيل بسرعة 2x مع صوت
  - **سحب رأسي (Vertical swipe)** — تعديل مستوى الصوت (الجهة اليسرى) / الإضاءة (الجهة اليمنى)
  - **إخفاء تلقائي** — التحكمات تختفي بعد 4 ثوانٍ من عدم التفاعل
  - **شاشة كاملة** — وضع landscape تلقائي
  - **استئناف التشغيل** — حفظ آخر موضع تشغيل في SharedPreferences لكل mediaId

#### التشغيل بدون إنترنت (Offline Playback)
- **OfflinePlayerScreen** — مشغل سينمائي كامل بواجهة أفقية
- **تراكب الترجمة المخصص (Custom Subtitle Overlay)** — Canvas مخصص يعرض الترجمة على الفيديو
- **قائمة الحلقات الجانبية** — التبديل السريع بين حلقات المسلسل المحمّلة
- **التحكم بالسرعة** — 0.5x إلى 3x
- **مؤشر البطارية والوقت** — معروض أعلى الشاشة
- **تحكم بالصوت والإضاءة** — عن طريق السحب الرأسي
- **دعم التشغيل الخلفي** — استمرار الصوت عند تصغير التطبيق

### 📥 2. نظام التحميل متعدد الخيوط

- **8 خيوط متوازية (8-thread parallel)** — `MultiThreadDownloader` يستخدم HTTP Range Requests
- **استئناف التحميل** — التحميل يستأنف بعد إغلاق التطبيق (download progress يُحفظ على القرص)
- **إيقاف مؤقت / متابعة** — pause/resume لكل تحميل على حدة
- **إعادة المحاولة التلقائية** — عند فشل جزء من التحميل
- **حساب السرعة** — عرض سرعة التحميل الفعلية (مثل: 2.5 MB/s)
- **إدارة المساحة** — التحقق من المساحة المتوفرة قبل بدء التحميل
- **واجهة تحميل تفاعلية** — `MovieBoxDownloadSheet` مع اختيار الجودة، شريط تقدم، زر إلغاء
- **حفظ في المعرض** — إمكانية تصدير الفيديو إلى معرض الجهاز
- **إشعار التحميل** — foreground service مع تحديث مستمر

### 💬 3. نظام الترجمة (3 مصادر)

#### نظام متكامل بثلاث صفحات
- **MovieBox (المصدر الأساسي)** — ترجمات من MovieBox API عبر الـ backend
- **Subdl (مصدر إضافي)** — API مباشر: `api.subdl.com` باستخدام مفتاح API
- **OpenSubtitles (مصدر إضافي)** — API مباشر: `api.opensubtitles.com` مع مصادقة Api-Key

#### محرك الترجمة المخصص
- **دقة عالية** — `SubtitleParser` يدقق في كل تفاصيل صيغة SRT/VTT
- **تعديل الموضع (Y Offset)** — تحريك الترجمة لأعلى/أسفل بمقدار 1 أو 10 بكسل
- **مزامنة الوقت (Time Sync)** — تقديم أو تأخير الترجمة بـ ±100ms
- **تغيير الحجم** — تكبير/تصغير خط الترجمة
- **إخفاء/إظهار** — إخفاء الترجمة مؤقتاً مع إمكانية الرجوع
- **ألوان مخصصة** — خلفية شبه شفافة + نص أبيض بظل

#### آلية التحميل
- **كشف نوع الملف تلقائياً** — ZIP, GZip, SRT, VTT
- **دعم GZip** — OpenSubtitles تُرجع ملفات `.gz` يتم فك ضغطها بـ `GZIPInputStream`
- **استخراج ZIP** — فك ضغط ملفات ZIP واستخراج ملف `.srt/.vtt` المناسب
- **مطابقة الحلقات** — في ملفات ZIP الموسمية، يستخرج التطبيق الترجمة الخاصة بكل حلقة تلقائياً
- **مهلات زمنية (Timeouts)** — connectTimeout 15s, readTimeout 20-30s

### 4. 🗨️ الدردشة الجماعية (Global Chat)

- **Firebase Realtime Database** — تخزين ومزامنة الرسائل فورياً
- **سحب للرد (Swipe-to-Reply)** — إيماءة شبيهة بـ iMessage
- **مؤشر الكتابة (Typing Indicator)** — رؤية المستخدمين الآخرين أثناء الكتابة
- **إشعارات الخلفية** — `ChatNotificationService` foreground service
- **رموز تعبيرية** — دعم كامل للإيموجي
- **تنسيق الوقت** — عرض تاريخ ووقت كل رسالة
- **حذف الرسائل** — حذف رسائلك الخاصة
- **رسائل النظام (Admin broadcasts)** — إشعارات من الأدمن (Firebase RTDB key: `admin_broadcasts`)

### 5. 🤖 AI Chat

- **مزودون متعددون** — أي مزود متوافق مع OpenAI API (مثل: OpenAI, Groq, OpenRouter, DeepSeek، وغيرها)
- **إدارة المزودين** — إضافة، تعديل، حذف المزودين عبر واجهة المستخدم
- **نماذج متعددة لكل مزود** — إضافة عدة نماذج مع خيارات `thinkingEffort` و `webSearch`
- **تدفق الردود (Streaming)** — عرض الردود فور ورودها عبر SSE
- **البحث في الويب (Web Search)** — دعم أداة `web_search` المدمجة في النماذج المدعومة
- **حفظ المحادثات** — تخزين الرسائل في SharedPreferences لكل مزود
- **حذف المحادثة** — مسح كل الرسائل

### 6. 🔐 المصادقة والمستخدمين

- **Google Sign-In** — عبر Credential Manager + Firebase Auth
- **البريد الإلكتروني/كلمة المرور** — التسجيل وتسجيل الدخول التقليدي
- **ملفات المستخدمين** — `UserManager` لإدارة: الصورة الرمزية، الاسم المعروض، معرف المستخدم الفريد
- **حوار إعداد الملف الشخصي (ProfileSetupDialog)** — أول مرة يسجل الدخول
- **قائمة المشاهدة (Watchlist)** — حفظ الأفلام والمسلسلات محلياً عبر Room

### 7. 🔍 البحث والاكتشاف

- **TMDB API** — تصفح الأفلام والمسلسلات الرائجة مع دعم اللغة العربية
- **بحث MovieBox** — البحث في فهرس MovieBox الكامل
- **بحث ذكي** — النتائج مرتبة حسب تطابق اللغة، صلة العنوان، وتوفر المحتوى
- **أقسام مخصصة (Custom Sections)** — بطاقات ترويجية يديرها الأدمن عبر Firebase RTDB
- **Skeleton UI** — عرض هيكل عظمي أثناء تحميل البيانات
- **Pagination** — تحميل المزيد عند التمرير للأسفل

### 8. الشاشات
#### تفاصيل المحتوى — DetailScreen + MovieBoxDetailScreen
- ملخص + معدل التقييم + سنة الإنتاج
- قائمة الممثلين (Cast)
- معرض الصور (Backdrop + Posters)
- للمسلسلات: اختيار الموسم وعرض الحلقات
- زر التحميل للمسلسلات: تحميل حلقات محددة أو كل الموسم
- **تبويبات (Tabs)**: TMDB (البيانات الوصفية) و MovieBox (روابط التحميل)

#### شاشة التحميلات — DownloadsScreen
- 3 تبويبات: **قيد التحميل (Downloading)** | **مكتملة (Completed)** | **الكل (All)**
- بطاقات بتحكمات: إيقاف، متابعة، فتح، حذف
- `PosterCard` — غلاف المسلسل مع شريط تقدم متدرج (gradient progress bar) مدمج في الصورة
- `CompactEpisodeRow` — عرض مضغوط للحلقات في وضع المسلسل
- `PlaylistFolderCard` — عرض مجلدات المسلسلات (بوكس بوستر المسلسل، عدد الحلقات المحمّلة، شريط تقدم إجمالي)
- زر "تنزيل باقي حلقات المسلسل" — تحميل الحلقات المتبقية دفعة واحدة

#### الشاشات الأخرى
- **الإعدادات (SettingsScreen)** — الملف الشخصي، المزودين AI، قائمة المشاهدة، لوحة الأدمن
- **قائمة المشاهدة (WatchlistScreen)** — شبكة من 3 أعمدة مع `WatchlistPosterCard`
- **شاشة البالغين (AdultContentScreen)** — فلترة المحتوى للبالغين
- **ExploreScreen** — مركز الاستكشاف (AI Chat و Global Chat)
- **SplashScreen** — شاشة ترحيب متحركة (2.2 ثانية)

---

## 🏗️ بنية التطبيق (Architecture)

### النمط المعماري: MVVM + Repository

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           UI Layer (Compose)                             │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────────┐  │
│  │  Home    │ │ Detail   │ │ Player   │ │Offline   │ │  Chat/AI/     │  │
│  │  Screen  │ │ Screen   │ │ Screen   │ │Player    │ │  Explore      │  │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘ └──────┬────────┘  │
│       │            │            │            │              │           │
├───────┼────────────┼────────────┼────────────┼──────────────┼───────────┤
│  ┌────┴────────────┴────────────┴────────────┴──────────────┴────────┐  │
│  │                      ViewModel Layer                               │  │
│  │  ┌────────────────┐ ┌───────────────────┐ ┌──────────────────┐    │  │
│  │  │ MovieViewModel │ │ MovieBoxViewModel  │ │AiChatViewModel   │    │  │
│  │  │ (TMDB + Auth)  │ │ (MovieBox API)     │ │ (AI Chat logic)  │    │  │
│  │  └───────┬────────┘ └─────────┬─────────┘ └────────┬─────────┘    │  │
│  └──────────┼────────────────────┼─────────────────────┼──────────────┘  │
├─────────────┼────────────────────┼─────────────────────┼────────────────┤
│  ┌──────────┴──────────┐  ┌──────┴───────┐  ┌─────────┴──────────┐     │
│  │   MovieRepository   │  │MovieBoxRepo  │  │ AiChatRepository   │     │
│  │   (TMDB + Firebase)  │  │(MovieBoxApi) │  │ (OpenAI-compatible)│     │
│  └──────────┬──────────┘  └──────┬───────┘  └─────────┬──────────┘     │
├─────────────┼────────────────────┼────────────────────┼────────────────┤
│  ┌──────────┴──────────┐  ┌──────┴───────────────────┴──────────┐      │
│  │    Data Sources     │  │       Subtitle System               │      │
│  │  ┌───────────────┐  │  │  ┌────────────┐ ┌───────────────┐  │      │
│  │  │ TMDB (Retrofit)│  │  │  │ MovieBox   │ │  Subdl API   │  │      │
│  │  │ MovieBox (OkHttp│  │  │  │ FastAPI    │ │ (direct HTTP)│  │      │
│  │  │ + FastAPI)     │  │  │  └────────────┘ └───────────────┘  │      │
│  │  │ Firebase (Auth)│  │  │  ┌────────────┐ ┌───────────────┐  │      │
│  │  │ Firebase (RTDB)│  │  │  │OpenSubtitles│ │ SubtitleParser│  │      │
│  │  └───────────────┘  │  │  │ API (direct)│ │ SRT/VTT       │  │      │
│  └─────────────────────┘  │  └────────────┘ └───────────────┘  │      │
│                            └────────────────────────────────────┘      │
├────────────────────────────────────────────────────────────────────────┤
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                    Local Storage (Room DB)                        │  │
│  │  • watchlist (movies/shows saved by user)                        │  │
│  │  • downloads (metadata about downloaded files)                   │  │
│  │  • chat_messages (cached — main storage is Firebase RTDB)        │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │              Background Services                                  │  │
│  │  • ChatNotificationService (foreground) — إشعارات الدردشة        │  │
│  │  • MultiThreadDownloader — تحميل متوازي 8 خيوط                   │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

### إدارة الحالة (State Management)
- **StateFlow** في ViewModels
- **RequestState<T>** — sealed class: `Idle`, `Loading`, `Success(data)`, `Error(msg)`
- **remember { mutableStateOf() }** للـ UI state المحلي في Composables
- **remember { mutableStateListOf() }** للقوائم

### التنقل (Navigation)
- **Navigation Compose** مع routes لكل شاشة رئيسية
- المسارات: `/home`, `/detail/{id}`, `/player/{id}`, `/settings`, `/chat`, `/ai-chat`, إلخ
- Parameters تمر عبر route arguments

---

## 📱 جميع الشاشات

| # | الشاشة | الملف | الوظيفة |
|---|--------|-------|---------|
| 1 | **SplashScreen** | `SplashScreen.kt` | شاشة افتتاحية متحركة، تنتقل تلقائياً إلى Home بعد 2.2 ثانية |
| 2 | **HomeScreen** | `HomeScreen.kt` | الصفحة الرئيسية: كاروسيل أفلام رائجة، أقسام مخصصة، بحث (TMDB + MovieBox) |
| 3 | **DetailScreen** | `DetailScreen.kt` | تفاصيل المحتوى من TMDB (ملخص، تقييم، ممثلين، صور) + حوار اختيار الموسم للمسلسلات |
| 4 | **MovieBoxDetailScreen** | `MovieBoxDetailScreen.kt` | تفاصيل MovieBox: روابط البث، اختيار الجودة، تحميل |
| 5 | **PlayerScreen** | `PlayerScreen.kt` | مشغل البث المباشر مع ExoPlayer + تحكمات متقدمة + اختيار الترجمة |
| 6 | **OfflinePlayerScreen** | `OfflinePlayerScreen.kt` | مشغل الفيديو المحلّي: واجهة سينمائية مع تراكب ترجمة مخصص، 3 صفحات ترجمة (MovieBox, Subdl, OpenSubtitles) |
| 7 | **DownloadsScreen** | `DownloadsScreen.kt` | إدارة التحميلات: 3 تبويبات، بطاقات مضغوطة للمسلسلات، تحكمات (إيقاف/متابعة/حذف) |
| 8 | **SettingsScreen** | `SettingsScreen.kt` | الإعدادات: الملف الشخصي (تعديل الاسم، الصورة)، إدارة مزودي AI، قائمة المشاهدة، لوحة الأدمن |
| 9 | **ExploreScreen** | `ExploreScreen.kt` | مركز الاستكشاف: بطاقات الدخول إلى AI Chat و Global Chat |
| 10 | **GlobalChatScreen** | `GlobalChatScreen.kt` | الدردشة الجماعية المباشرة عبر Firebase RTDB مع سحب للرد ومؤشر كتابة |
| 11 | **AiChatScreen** | `AiChatScreen.kt` | محادثة ذكاء اصطناعي مع تدفق الردود (streaming) عبر أي مزود OpenAI-compatible |
| 12 | **AiProviderConfigScreen** | `AiProviderConfigScreen.kt` | إضافة/تعديل مزود AI: endpoint, API key, إدارة النماذج |
| 13 | **WatchlistScreen** | `WatchlistScreen.kt` | قائمة المشاهدة المحفوظة محلياً (3 أعمدة) مع `WatchlistPosterCard` |
| 14 | **CustomSectionDialog** | `CustomSectionDialog.kt` | حوار إضافة/تعديل الأقسام المخصصة (لوحة الأدمن) |
| 15 | **ProfileSetupDialog** | `ProfileSetupDialog.kt` | حوار إعداد الملف الشخصي لأول مرة بعد تسجيل الدخول |
| 16 | **AdultContentScreen** | `AdultContentScreen.kt` | تحذير المحتوى للبالغين مع خيار الاستمرار أو العودة |

---

## 📂 هيكل المشروع الكامل

```
app/src/main/java/com/example/
│
├── MainActivity.kt                          # النقطة الرئيسية — Navigation graph + edge-to-edge
│
├── auth/                                    # المصادقة والمستخدمين
│   ├── AuthManager.kt                       # Firebase Auth + Google Sign-In (Credential Manager)
│   ├── ChatManager.kt                       # عمليات Firebase RTDB للدردشة (إرسال، استقبال، typing)
│   └── UserManager.kt                       # إدارة ملفات المستخدمين (SharedPreferences)
│
├── data/
│   ├── ai/                                  # نظام AI Chat
│   │   ├── AiChatRepository.kt              # الاتصال بمزودي OpenAI-compatible (streaming SSE + web_search tool)
│   │   ├── AiChatViewModel.kt              # ViewModel لإدارة المحادثة: إرسال، حفظ، تحميل
│   │   ├── AiProvider.kt                   # Data models: AiProvider, AiModel, ChatMessage
│   │   ├── AiProviderManager.kt            # CRUD للمزودين والرسائل في SharedPreferences
│   │   └── AiViewModelFactory.kt           # Factory لـ AiChatViewModel
│   │
│   ├── local/                               # قاعدة البيانات المحلية (Room)
│   │   ├── MovieEntities.kt                 # Entity: watchlist, download, chat_message, user_profile
│   │   ├── MovieDao.kt                      # DAO: استعلامات (Insert, Delete, Query, GetWatchlistWithDetails)
│   │   └── MovieDatabase.kt                 # Room Database (version 1)
│   │
│   ├── remote/                              # مصادر البيانات البعيدة
│   │   ├── ApiServices.kt                   # Retrofit interface لـ TMDB API
│   │   ├── RetrofitClient.kt                # Retrofit singleton مع Moshi converter
│   │   ├── TmdbModels.kt                    # Models لاستجابات TMDB
│   │   ├── EzVidModels.kt                   # Models لاستجابات EzVid
│   │   ├── CustomSectionManager.kt          # إدارة الأقسام المخصصة عبر Firebase RTDB
│   │   ├── moviebox/                        # تكامل MovieBox (مجلد كامل — 6 ملفات)
│   │   │   ├── api/MovieBoxApiImpl.kt       # تنفيذ API MovieBox: search, getDownloadLinks, getSubtitles
│   │   │   ├── crypto/MovieBoxDeviceInfo.kt # إنشاء device info (fingerprint, timestamp)
│   │   │   ├── crypto/MovieBoxSigner.kt     # توقيع HMAC-MD5 للطلبات
│   │   │   ├── models/MovieBoxModels.kt     # نماذج بيانات MovieBox
│   │   │   ├── network/MovieBoxHttpClient.kt# عميل HTTP مع failover بين 2 domain + keep-alive
│   │   │   ├── repository/MovieBoxRepository.kt # تخزين مؤقت (in-memory cache) للنتائج
│   │   │   └── viewmodel/MovieBoxViewModel.kt# ViewModel لبيانات MovieBox
│   │   └── repository/
│   │       └── MovieRepository.kt           # Repository الرئيسي (يجمع TMDB + Firebase)
│   └── models/
│       └── ChatMessage.kt                   # نموذج رسالة الدردشة
│
├── ui/
│   ├── components/                          # مكونات UI قابلة لإعادة الاستخدام
│   │   ├── SkeletonUI.kt                    # شاشة تحميل هيكل عظمي (5 أنماط مختلفة)
│   │   ├── VideoPlayerView.kt               # غلاف ExoPlayer + تحكمات (نقر مزدوج، سحب مستوى صوت/إضاءة)
│   │   └── moviebox/
│   │       └── MovieBoxDownloadSheet.kt     # شيت تحميل MovieBox: اختيار جودة، تقدم، تحكمات — 502 سطر
│   │
│   ├── screens/                             # ★ 16 شاشة رئيسية + شاشتا مساعد ★
│   │   ├── SplashScreen.kt                  # شاشة افتتاحية (25)
│   │   ├── HomeScreen.kt                    # الصفحة الرئيسية (49)
│   │   ├── DetailScreen.kt                  # تفاصيل TMDB (41)
│   │   ├── MovieBoxDetailScreen.kt          # تفاصيل MovieBox (36)
│   │   ├── PlayerScreen.kt                  # مشغل البث (55)
│   │   ├── OfflinePlayerScreen.kt           # ★ المشغل المحلّي — 1667 سطر ★ أكبر ملف في المشروع
│   │   ├── DownloadsScreen.kt               # إدارة التحميلات (43)
│   │   ├── SettingsScreen.kt                # الإعدادات (43)
│   │   ├── ExploreScreen.kt                 # مركز الاستكشاف (52)
│   │   ├── GlobalChatScreen.kt              # الدردشة الجماعية (55)
│   │   ├── AiChatScreen.kt                  # محادثة AI (39)
│   │   ├── AiProviderConfigScreen.kt        # إعدادات مزود AI (45)
│   │   ├── WatchlistScreen.kt               # قائمة المشاهدة (33) + WatchlistPosterCard (127)
│   │   ├── AdultContentScreen.kt            # تحذير محتوى بالغين (48)
│   │   ├── CustomSectionDialog.kt           # حوار الأقسام المخصصة (34)
│   │   └── ProfileSetupDialog.kt            # حوار إعداد الملف الشخصي (38)
│   │
│   ├── theme/                               # سمات التطبيق
│   │   ├── Color.kt                         # لوحة ألوان "Comfort Beige": بني كراميل، ذهبي، بيج
│   │   ├── Theme.kt                         # MaterialTheme مع dynamic colors + dark mode
│   │   └── Type.kt                          # أنماط الخطوط (IBM Plex Sans Arabic)
│   │
│   └── viewmodel/                           # منطق الأعمال
│       ├── MovieViewModel.kt                # ViewModel الرئيسي — TMDB، Firebase، تحميل، 566 سطر
│       ├── SubtitleHelper.kt                # ★ نظام الترجمة بالكامل — 430 سطر ★
│       └── SubtitleParser.kt                # محلل SRT/VTT (81 سطر)
│
└── utils/
    ├── ChatNotificationService.kt            # Foreground service لإشعارات الدردشة
    └── MultiThreadDownloader.kt              # ★ محمّل متوازي 8 خيوط — 193 سطر ★
```

### 📄 ملفات التهيئة

```
app/
├── build.gradle.kts                          # Build script مع version catalog
├── proguard-rules.pro
└── src/main/
    ├── AndroidManifest.xml                   # تصريح الأنشطة، الأذونات، الخدمات
    └── res/
        ├── values/
        │   ├── strings.xml                   # اسم التطبيق
        │   ├── colors.xml                    # ألوان ثابتة
        │   ├── themes.xml                    # سمات Android
        │   └── font_certs.xml                # شهادات التحقق من الخط
        ├── drawable/                         # أيقونات
        └── mipmap-anydpi-v26/                # أيقونات adaptive

build.gradle.kts                              # root build script
settings.gradle.kts                           # إعدادات المشروع
gradle.properties                             # خصائص Gradle
gradle/libs.versions.toml                     # ★ Version Catalog — جميع الإصدارات ★
local.properties                              # SDK path
.env.example                                  # مثال لمتغيرات البيئة
debug.keystore                                # مفتاح التوقيع للتطوير (مسجل في Firebase)
```

---

## 🛠️ التقنيات المستخدمة (Tech Stack)

### الأساسيات
| التقنية | الغرض | الإصدار |
|----------|--------|---------|
| **Kotlin** | لغة البرمجة | 2.0.21 |
| **Jetpack Compose** | واجهة المستخدم التصريحية | BOM 2024.09.00 |
| **Material Design 3** | نظام التصميم | — |
| **Gradle (Kotlin DSL)** | نظام البناء | — |
| **compileSdk / targetSdk** | إصدار SDK المستهدف | 36 |
| **minSdk** | أقل إصدار Android مدعوم | 24 |

### واجهة المستخدم
| المكتبة | الغرض |
|----------|--------|
| `androidx.compose.material3` | مكونات Material 3 (Card, Scaffold, TopAppBar, BottomSheet, Snackbar) |
| `androidx.compose.material.icons.extended` | أيقونات Material (3000+) |
| `androidx.compose.ui.text.google.fonts` | خط IBM Plex Sans Arabic |
| `androidx.navigation.compose` | التنقل بين الشاشات |
| `androidx.lifecycle.viewmodel.compose` | ViewModel في Compose |
| `androidx.activity.compose` | Activity Result API + Edge-to-Edge |

### التشغيل والصوت
| المكتبة | الغرض | الإصدار |
|----------|--------|---------|
| `androidx.media3:exoplayer` | مشغل الفيديو | 1.4.1 |
| `androidx.media3:hls` | دعم HLS (m3u8) | 1.4.1 |
| `androidx.media3:ui` | واجهة PlayerView | 1.4.1 |

### الشبكات والبيانات
| المكتبة | الغرض | الإصدار |
|----------|--------|---------|
| **Retrofit 2** | HTTP client لـ TMDB | 2.12.0 |
| **OkHttp 4** | HTTP client لـ MovieBox والترجمة | 4.10.0 |
| **Moshi** | JSON parsing (مع KSP codegen) | 1.15.2 |
| **Coil Compose** | تحميل الصور | 2.7.0 |
| **Room** | قاعدة البيانات المحلية | 2.7.0 |

### Firebase
| المكتبة | الغرض | الإصدار |
|----------|--------|---------|
| `firebase-bom` | Firebase Bill of Materials | 34.12.0 |
| `firebase-auth` | المصادقة (Google + Email) | — |
| `firebase-database` | Firebase Realtime Database (دردشة + أقسام مخصصة) | — |
| `androidx.credentials` | Credential Manager API | — |
| `androidx.credentials.play.services.auth` | Google ID credential | — |
| `googleid` | Google Sign-In | — |

### تطوير واختبار
| المكتبة | الغرض |
|----------|--------|
| Robolectric | اختبارات وحدة مع Android resources |
| Roborazzi | اختبارات صور (Screenshot tests) |
| JUnit 4 | إطار الاختبارات |

---

## 💬 نظام الترجمة (3 مصادر)

### لمحة عامة

نظام الترجمة في واتشر مبني على 3 مصادر مستقلة، وواجهة مستخدم من 4 صفحات داخل شيت جانبي (Subtitle Drawer).

### `SubtitleHelper.kt` — 430 سطر

```
SubtitleHelper
├── SubtitleItem                      # نموذج بيانات الترجمة (name, lang, url, langCode, source, fileId, downloadCount)
├── CONSTANTS
│   ├── API_KEY / SUBDL_API_KEY       # مفتاح Subdl API
│   └── OPENSUBTITLES_API_KEY         # مفتاح OpenSubtitles API
│   └── USER_AGENT                    # معرف الطلب
├── fetchSubtitles()                  # ★ MovieBox — المصدر الأساسي ★
│   ├── findResourceId()              # البحث عن resource_id في download_links
│   ├── fetchSubtitlesByResource()    # جلب الترجمات من /get_subtitles
│   └── title-based fallback          # البحث بالعنوان إذا فشل الرقم
├── fetchSubdlSubtitles()             # ★ Subdl API — المصدر الثاني ★
│   ├── يدعم unpack_files (الملفات الفردية)
│   └── يدعم season_number + episode_number
├── fetchOpenSubtitles()              # ★ OpenSubtitles API — المصدر الثالث ★
│   ├── يدعم parent_tmdb_id + tmdb_id
│   └── يدعم season_number + episode_number
├── getOpenSubtitleDownloadUrl()      # POST /download للحصول على رابط التحميل الفعلي
└── downloadAndExtractSubtitle()      # ★ محرك التحميل والاستخراج ★
    ├── كشف ZIP (.zip URL) → ZipInputStream
    ├── كشف GZip (.gz / gzip encoding) → GZIPInputStream
    ├── ملفات مباشرة (.srt / .vtt) → حفظ مباشر
    ├── مطابقة الحلقات في ZIP الموسمية (regex E|EP|Episode)
    └── مهلات زمنية: connectTimeout=15s, readTimeout=20-30s
```

### `SubtitleParser.kt` — 81 سطر

```
SubtitleParser
├── parseBlock(File)                  # تحليل ملف SRT/VTT إلى List<SubtitleLine>
└── parseTimestamp(String)            # تحويل 00:01:23,456 إلى مللي ثانية
```

### واجهة المستخدم — Subtitle Drawer (OfflinePlayerScreen.kt)

```
Subtitle Drawer (AnimatedVisibility — دخول/خروج من اليسار)
│
├── AnimatedContent (4 صفحات مع fade + slide transitions)
│   │
│   ├── الصفحة 0: التحكمات الرئيسية (قابلة للتمرير)
│   │   ├── ضبط موضع الترجمة (±1px, ±10px)
│   │   ├── مزامنة الوقت (±100ms)
│   │   ├── إخفاء/إظهار الترجمة
│   │   ├── حجم الخط
│   │   ├── زر ← الصفحة 1 (بحث MovieBox)
│   │   ├── زر ← الصفحة 2 (Subdl)
│   │   └── زر ← الصفحة 3 (OpenSubtitles)
│   │   └── اختيار ملف ترجمة من الجهاز
│   │
│   ├── الصفحة 1: نتائج MovieBox
│   │   ├── LaunchedEffect — جلب تلقائي عند الدخول
│   │   ├── زر إعادة البحث
│   │   └── LazyColumn مع بطاقات الترجمة
│   │
│   ├── الصفحة 2: نتائج Subdl (لون أزرق #4A90D9)
│   │   ├── LaunchedEffect — جلب تلقائي عند الدخول
│   │   ├── زر إعادة البحث
│   │   └── LazyColumn مع SectionHeader + SourceSubtitleCard
│   │
│   └── الصفحة 3: نتائج OpenSubtitles (لون أخضر #7CB342)
│       ├── LaunchedEffect — جلب تلقائي عند الدخول
│       ├── زر إعادة البحث
│       └── LazyColumn مع SectionHeader + SourceSubtitleCard
│
├── SectionHeader(title, count, color)    # رأس القسم مع عداد ملون
└── SourceSubtitleCard(item, onDownload)  # بطاقة ترجمة مع: شارة المصدر، الاسم، اللغة، عداد التحميل، زر تحميل
```

---

## 📥 نظام التحميل متعدد الخيوط

### `MultiThreadDownloader.kt` — 193 سطر

```
MultiThreadDownloader (Singleton)
│
├── startDownload()                      # بدء تحميل جديد
│   ├── التحقق من عدم وجود تحميل مكرر
│   ├── HEAD request لمعرفة حجم الملف
│   ├── التحقق من المساحة المتوفرة
│   ├── استئناف التحميل (قراءة الملف الموجود)
│   └── تقسيم الملف إلى 8 أجزاء متساوية
│
├── DownloadWorker (inner coroutine)     # عامل تحميل واحد
│   ├── HTTP Range Request (BytesRange)
│   ├── كتابة الجزء المحمّل في RandomAccessFile
│   ├── تحديث progress
│   └── قبول الإلغاء (cancellation)
│
├── pauseDownload()                      # إيقاف مؤقت
│   ├── إلغاء Job بدون استثناء
│   └── حفظ progress في ملف مؤقت
│
├── resumeDownload()                     # استئناف
│   └── scanExistingFile() لمعرفة ما تم تحميله
│
├── cancelDownload()                     # إلغاء كامل
│   └── إلغاء Job + حذف الملف المؤقت
│
└── scanExistingFile()                   # مسح الملف الموجود لاستئناف التحميل
    └── قراءة الفجوات (gaps) في الملف لتحديد الأجزاء المفقودة
```

### `MovieBoxDownloadSheet.kt` — 502 سطر

شيت تحميل كامل (BottomSheet) يتضمن:
- **قائمة الجودات** — 360p، 480p، 720p، 1080p مع أيقونات وحجم كل جودة
- **تحديث الجودات** — زر إعادة تحميل القائمة
- **شريط تقدم** — مع نسبة مئوية وحجم محمّل/إجمالي وسرعة التحميل
- **تحكمات** — إيقاف (Pause) | متابعة (Resume) | إلغاء (Cancel)
- **حالة التحذير** — إذا كانت مساحة التخزين غير كافية
- **تصدير إلى المعرض** — بعد اكتمال التحميل

---

## 🤖 نظام AI Chat

### `AiChatRepository.kt` — 359 سطر

```
AiChatRepository
│
└── chatCompletion(provider, model, messages, onEvent)
    ├── إرسال طلب POST إلى {endpoint}/chat/completions
    ├── قراءة SSE (Server-Sent Events) stream
    │   ├── content delta → onEvent(content)
    │   ├── finish_reason → onEvent(done)
    │   └── error → onEvent(error)
    ├── دعم web_search tool calling:
    │   ├── استدعاء tool → إجراء بحث ويب
    │   ├── إرسال نتيجة البحث كـ tool_response
    │   └── استقبال الرد النهائي
    └── حد أقصى 5 جولات (turns) لأمان الـ tool calling
```

### `AiProvider.kt` — نماذج البيانات
```kotlin
AiProvider(id, displayName, endpoint, apiKey, models[], isDefault)
AiModel(name, thinkingEffort, webSearch)
ChatMessage(role, content, timestamp)
```

### `AiProviderManager.kt` — 99 سطر
- **تخزين المزودين** في SharedPreferences (JSON)
- **CRUD** كامل: getProviders, saveProviders, add, update, delete
- **إدارة الرسائل** لكل مزود: saveMessages, loadMessages, clearMessages
- **getDefaultProvider()** — المزود الافتراضي

---

## 🔐 نظام المصادقة

### `AuthManager.kt`
- **Google Sign-In** عبر Credential Manager API
- **Email/Password** — تسجيل وتسجيل دخول
- **GoogleSignInClient** مع Request ID Token
- **Firebase Auth** — ربط مع Firebase Authentication
- **مفتاح SHA-1** للتطوير مسجل في Firebase Console (debug.keystore)

### `UserManager.kt`
- تخزين الملف الشخصي: username، displayName، avatarUrl في SharedPreferences
- أسماء مستخدمين فريدة (عشوائية + اختيار المستخدم)
- الصورة الرمزية: رابط URL (عادة من Google)

### `ProfileSetupDialog.kt`
- يظهر عند أول تسجيل دخول
- إدخال الاسم المعروض
- اختيار الصورة الرمزية
- إنشاء معرف مستخدم فريد

---

## 🎬 تكامل MovieBox

### آلية العمل

```
التطبيق ←→ moviebox-fastapi (Vercel backend) ←→ MovieBox API
```

### `MovieBoxApiImpl.kt`
| الدالة | الوظيفة |
|--------|---------|
| `search(query)` | البحث في فهرس MovieBox |
| `getDownloadLinks(subjectId)` | جلب روابط البث والتحميل |
| `getSubtitles(subjectId, resourceId)` | جلب الترجمات |

### `MovieBoxHttpClient.kt`
- **Failover بين دومينين**: `https://web-api.movie-box.ai` و `https://api.phim.buzz`
- **Keep-Alive** — إعادة استخدام الاتصالات
- **توقيع HMAC-MD5** — `MovieBoxSigner` يوقع كل طلب بـ device fingerprint + timestamp
- **Device Info** — `MovieBoxDeviceInfo` يولد معرف جهاز فريد (device_id عشوائي + app_version)

### `MovieBoxRepository.kt`
- **In-memory cache** — تخزين نتائج مؤقتة لمدة محددة
- تجنب الطلبات المتكررة لنفس البيانات

### Backend (خارجي): `ahmedio3/moviebox-fastapi`
- **مستضاف على Vercel**
- **API**:
  - `GET /search?query=...&limit=...` — بحث
  - `GET /get_download_links?subject_id=...` — روابط التحميل
  - `GET /get_subtitles?subject_id=...&resource_id=...` — ترجمات

---

## 🔥 تكامل Firebase

| الخدمة | الاستخدام |
|--------|-----------|
| **Firebase Authentication** | Google Sign-In, Email/Password |
| **Firebase Realtime Database** | الرسائل الجماعية (Global Chat), مؤشرات الكتابة, إشعارات الأدمن, الأقسام المخصصة |
| **Firebase Console** | إدارة المستخدمين, لوحة الأدمن للأقسام المخصصة |

### هيكل Realtime Database
```
/chat
├── messages/{msgId}
│   ├── text, senderName, senderId, avatarUrl, timestamp, replyTo
├── typing/{userId}
│   └── isTyping: Boolean, timestamp
└── admin_broadcasts/{msgId}
    └── text, timestamp, active
```

---

## 🚀 البدء مع المشروع

### المتطلبات الأساسية
1. **Android Studio** (Ladybug أو أحدث)
2. **JDK 17+**
3. **Android SDK 36**
4. حساب **GitHub** (لـ CI/CD)

### خطوات التشغيل

```bash
# 1. استنساخ المشروع
git clone https://github.com/ahmedio3/wacher.git
cd wacher

# 2. إعداد متغيرات البيئة
cp .env.example .env
# GEMINI_API_KEY اختياري (غير مستخدم حالياً)

# 3. فتح في Android Studio
# File → Open → اختيار مجلد wacher
# انتظار حل الاعتماديات (dependency resolution)

# 4. تشغيل
# اختيار جهاز/محاكي (API 24+)
# الضغط على ▶️ Run

# أو بناء من CLI:
./gradlew assembleDebug
```

### مشاكل شائعة

| المشكلة | الحل |
|---------|------|
| `debug.keystore` غير موجود | `keytool -genkey -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"` |
| Firebase Auth لا يعمل مع Google | تأكد من تسجيل SHA-1 الخاص بـ debug.keystore في Firebase Console |
| Room migration error | احذف التطبيق وأعد تثبيته (أو increment version + provide migration) |

---

## 🔧 الإعدادات والبيئة

### متغيرات البيئة (`.env`)
| المتغير | مطلوب | الوصف |
|---------|--------|-------|
| `GEMINI_API_KEY` | لا | مفتاح Gemini API (اختياري، غير مستخدم حالياً) |

### إصدارات مهمة في `gradle/libs.versions.toml`

| الإصدار | القيمة |
|---------|--------|
| AGP | 8.10.1 |
| Kotlin | 2.0.21 |
| Compose BOM | 2024.09.00 |
| Firebase BOM | 34.12.0 |
| ExoPlayer/Media3 | 1.4.1 |
| Room | 2.7.0 |
| Retrofit | 2.12.0 |
| OkHttp | 4.10.0 |

### التوقيع (Release builds)
| المتغير | الوصف |
|---------|--------|
| `KEYSTORE_PATH` | مسار ملف keystore |
| `STORE_PASSWORD` | كلمة مرور keystore |
| `KEY_PASSWORD` | كلمة مرور المفتاح |

---

## 📋 CI/CD و GitHub Actions

### `build.yml`
```yaml
الحدث: push إلى main
البيئة: ubuntu-latest
Java: Temurin 17
Gradle: 8.11.1
المخرجات: app-debug.apk (محفوظ 7 أيام)
```

### سير العمل
1. **Checkout** — استنساخ المستودع
2. **Setup Java** — تثبيت JDK 17
3. **Setup Gradle** — تثبيت Gradle 8.11.1 مع caching
4. **Build Debug APK** — `gradle assembleDebug`
5. **Upload Artifact** — رفع APK إلى GitHub Actions artifacts

### Flow
```
commit + push (main)
    ↓
GitHub Actions starts build
    ↓
gradle assembleDebug
    ↓
BUILD SUCCESS → upload APK
    ↓
BUILD FAILED → gh run view --log-failed → fix → commit → push → loop
```

---

## 🧪 الاختبارات

```bash
# اختبارات الوحدة
./gradlew test

# اختبارات الأجهزة (Instrumented)
./gradlew connectedAndroidTest

# اختبارات الصور (Roborazzi screenshot tests)
./gradlew recordRoborazziDebug

# فحص الصور المسجلة
./gradlew verifyRoborazziDebug
```

### أنواع الاختبارات
| النوع | الإطار | الموقع |
|-------|--------|--------|
| اختبارات وحدة | JUnit 4 + Robolectric | `app/src/test/` |
| اختبارات أجهزة | AndroidX Test + Espresso | `app/src/androidTest/` |
| اختبارات صور | Roborazzi | `app/src/test/` |

---

## 📝 سجل التحديثات

### `11a0dc0` — إصلاح تعليق coroutine عند تبديل الصفحات
- **المشكلة**: أزرار الصفحة 0 كانت تبدأ fetch في `rememberCoroutineScope()` الخاص بها، ثم تنتقل إلى صفحة أخرى عبر `subtitlePage = N`، مما يلغي الـ scope ويوقف الـ fetch
- **الحل**: إزالة `scope.launch` من أزرار الصفحة 0 (تكتفي بـ `subtitlePage = N` فقط)، وإضافة `LaunchedEffect(Unit)` في كل صفحة فرعية لجلب البيانات تلقائياً عند الدخول
- **ملفات متأثرة**: `OfflinePlayerScreen.kt`

### `8d9bd9a` — فصل الصفحة 2 إلى صفحات منفصلة (Subdl + OpenSubtitles)
- فصل الصفحة 2 المدمجة إلى صفحة 2 (Subdl) وصفحة 3 (OpenSubtitles) منفصلتين
- إزالة `async import` غير المستخدم
- إضافة `rememberScrollState` و `verticalScroll` imports

### `5a70134` — إعادة تصميم واجهة الترجمة (3 صفحات)
- **الصفحة 0**: قابلة للتمرير (verticalScroll) مع أزرار منفصلة لكل مصدر
- **الصفحة 1**: نتائج MovieBox
- **الصفحة 2**: نتائج Subdl + OpenSubtitles (مدمجة سابقاً)
- **SectionHeader** و **SourceSubtitleCard** — مكونات عرض جديدة

### `9004162` — إضافة Subdl + OpenSubtitles
- `SubtitleHelper.fetchSubdlSubtitles()` — مباشر من Subdl API
- `SubtitleHelper.fetchOpenSubtitles()` — مباشر من OpenSubtitles API
- `SubtitleHelper.getOpenSubtitleDownloadUrl()` — POST للحصول على رابط التحميل
- `downloadAndExtractSubtitle()` — دعم ZIP detection + GZip + timeouts + redirects

### قبل ذلك
- دعم Firebase + Google Sign-In
- إعادة تصميم شاشة التحميلات (PosterCard, CompactEpisodeRow, PlaylistFolderCard)
- إضافة AI Chat مع مزودين متعددين
- إضافة download flow مع `MovieBoxDownloadSheet` (502 سطر)
- تحسينات الأداء وإصلاحات أخطاء متعددة

---

<div align="center">

**بُني بـ ❤️ باستخدام Kotlin و Jetpack Compose**

[Report Bug](https://github.com/ahmedio3/wacher/issues) · [Request Feature](https://github.com/ahmedio3/wacher/issues)

</div>
