<div align="center">

# واتشر — Watchera (Cinemios)

**تطبيق Android لمشاهدة وتحميل الأفلام والمسلسلات مع دردشة ومساعد ذكاء اصطناعي**

</div>

---

## 📋 المحتويات

- [نظرة عامة](#نظرة-عامة)
- [إحصائيات المشروع](#إحصائيات-المشروع)
- [بنية التطبيق](#بنية-التطبيق)
- [الشاشات](#الشاشات)
- [التقنيات والمكتبات](#التقنيات-والمكتبات)
- [الأنظمة الفرعية](#الأنظمة-الفرعية)
- [هيكل الملفات](#هيكل-الملفات)
- [التشغيل](#التشغيل)

---

## نظرة عامة

**واتشر** تطبيق Android باللغة العربية (RTL) يتيح:

- **تصفح واكتشاف.** الأفلام والمسلسلات عبر TMDB
- **مشاهدة البث المباشر** عبر MovieBox API باستخدام ExoPlayer
- **تحميل متعدد الخيوط** (8 خيوط) للمشاهدة بدون إنترنت
- **ترجمة من 3 مصادر**: MovieBox + Subdl + OpenSubtitles
- **دردشة جماعية وخاصة** عبر Firebase Realtime Database
- **مساعد AI** مع Gemini و OpenAI-compatible providers
- **مزامنة قائمة المشاهدة** عبر Firebase

---

## إحصائيات المشروع

| المقياس | القيمة |
|---------|--------|
| إجمالي الملفات | **131** |
| ملفات Kotlin | **92** |
| الشاشات | **16** شاشة |
| مكونات UI | **17** مكون قابل لإعادة الاستخدام |
| عدد ViewModels | 4 (Movie, MovieBox, Ai) |
| عدد Entities | 8 (Room) |
| قاعدة البيانات | Room — version 15 |
| التطبيق ID | `com.aistudio.cinemios.fxtyr` |
| Kotlin | 2.2.10 |
| Compose BOM | 2024.09.00 |
| AGP | 9.1.1 |
| Min SDK | 24 (Android 6.0) |
| Target/Compile SDK | 36 (Android 16) |

---

## بنية التطبيق

**النمط المعماري: MVVM + Repository**

```
┌─────────────────────────────────────────┐
│           UI Layer (Compose)            │
│  ┌─────────┐ ┌─────────┐ ┌───────────┐ │
│  │ Screens │ │Componen-│ │  Theme    │ │
│  │ (16)    │ │ ts (17) │ │(Beige/    │ │
│  │         │ │         │ │ Dark)     │ │
│  └────┬────┘ └────┬────┘ └───────────┘ │
│       │           │                     │
├───────┼───────────┼─────────────────────┤
│  ┌────┴───────────┴────────────────┐    │
│  │        ViewModel Layer          │    │
│  │  ┌──────────┐ ┌──────────────┐ │    │
│  │  │  Movie   │ │  MovieBox    │ │    │
│  │  │ViewModel │ │  ViewModel   │ │    │
│  │  └────┬─────┘ └──────┬───────┘ │    │
│  │  ┌────┴──────────────┐         │    │
│  │  │    AiViewModel    │         │    │
│  │  └───────────────────┘         │    │
│  └────────────┬───────────────────┘    │
│               │                         │
├───────────────┼─────────────────────────┤
│  ┌────────────┴───────────────────┐    │
│  │        Repository Layer        │    │
│  │  ┌──────────┐ ┌──────────────┐ │    │
│  │  │  Movie   │ │  MovieBox    │ │    │
│  │  │  Repo    │ │   Repo       │ │    │
│  │  └────┬─────┘ └──────┬───────┘ │    │
│  └───────┼──────────────┼─────────┘    │
│          │              │               │
├──────────┼──────────────┼───────────────┤
│  ┌───────┴──────────────┴────────────┐  │
│  │         Data Sources              │  │
│  │  ┌─────────┐ ┌─────────┐ ┌─────┐ │  │
│  │  │  TMDB   │ │MovieBox │ │ AI  │ │  │
│  │  │ API     │ │  API    │ │Prov.│ │  │
│  │  └─────────┘ └─────────┘ └─────┘ │  │
│  │  ┌─────────┐ ┌─────────────────┐ │  │
│  │  │Subtitles│ │   Firebase      │ │  │
│  │  │ 3 مصادر │ │Auth + RTDB      │ │  │
│  │  └─────────┘ └─────────────────┘ │  │
│  └──────────────────────────────────┘  │
│  ┌──────────────────────────────────┐  │
│  │      Room Database (Local)       │  │
│  │  watchlist │ downloads │ ai_chat │  │
│  │  subtitle  │ episodes  │ images  │  │
│  │  season_meta                    │  │
│  └──────────────────────────────────┘  │
└─────────────────────────────────────────┘
```

---

## الشاشات

### 1. HomeScreen — الصفحة الرئيسية

**الملف:** `ui/screens/HomeScreen.kt` (~1108 سطر)

**المحتوى:**
- Header: "مرحباً بك في ووتشيرا — Watchera"
- مربع بحث مع تبديل بين **TMDB search** و **MovieBox search**
- قسم مخصص من الأدمن (Custom Section) — عنوانه "بتاع"
- **Featured Carousel**: 5 أفلام رائجة مع صور backdrop
- قسم "قائمتي" (Watchlist) — يظهر إذا وُجدت عناصر
- 8 صفوف أفقية: أفلام رائجة، مسلسلات رائجة، ترندينج أفلام، ترندينج مسلسلات، الأعلى تقيماً، الأفلام الحالية، المسلسلات المعروضة
- نتائج البحث تظهر كشبكة 3 أعمدة

**ميزات حقيقية:**
- ضغط مطوّل على أي بوستر → قائمة سياقية (إضافة لقائمة المشاهدة + مشاركة)
- تبديل وضع البحث TMDB/MovieBox بأيقونة CloudSync
- Skeleton loading أثناء التحميل
- Bouncy overscroll effect
- ربط بـ Share Sheet

**لا يحتوي على:**
- ❌ Splash screen
- ❌ أي إيماءات خاصة

---

### 2. DetailScreen — تفاصيل المحتوى

**الملف:** `ui/screens/DetailScreen.kt` (~1506 سطر)

**المحتوى:**
- Immersive header: صورة backdrop + ملصق في المنتصف
- صف البيانات: السنة + التقييم + المدة/عدد المواسم + شهادة التصنيف
- رقائق الأنواع (Genres)
- أزرار الإجراءات:
  - **Play** (رئيسي) — يفتح المشغّل
  - **Bookmark** — يضيف لقائمة المشاهدة (ضغط مطوّل → StatusPickerSheet)
  - **Download** — يفتح MovieBoxDownloadSheet
  - **Subtitle** — يفتح SubtitleSourceSheet
- ملخص القصة
- صف الممثلين (10 أقصى، صور دائرية)
- صفوف أفقية: أعمال مشابهة + توصيات
- للمسلسلات: اختيار الموسم + قائمة الحلقات (تشغيل/تحميل/ترجمة لكل حلقة)

**ميزات حقيقية:**
- ضغط مطوّل على Bookmark → StatusPickerSheet بـ 5 حالات (Plan to Watch / Watching / Completed / On Hold / Dropped)
- ضغط مطوّل على حلقة مسلسل → تحميل ترجمة
- تخزين تفاصيل الفيلم محلياً في Activity log

---

### 3. MovieBoxDetailScreen — تفاصيل MovieBox

**الملف:** `ui/screens/MovieBoxDetailScreen.kt` (~676 سطر)

**المحتوى:**
- نفس تخطيط DetailScreen ولكن لبيانات MovieBox API
- للأفلام: تشغيل مباشر من روابط MovieBox
- للمسلسلات: اختيار الموسم والحلقات

**ملاحظة:** لا يحتوي على long-press status picker على Bookmark

---

### 4. PlayerScreen — مشغّل البث المباشر

**الملف:** `ui/screens/PlayerScreen.kt` (~650 سطر)

**المحتوى (وضع Portrait):**
- TopAppBar + مشغّل ExoPlayer بنسبة 16:9
- زر ملء الشاشة + شريط السرعة + اختيار الترجمة

**المحتوى (وضع Fullscreen):**
- خلفية سوداء + مشغّل
- Gesture overlay
- قائمة سرعة (أعلى اليمين) + زر رجوع

**ميزات حقيقية مؤكد:**
- ✅ نقر مزدوج (Double-tap) → تقديم/تأخير 10 ثوانٍ
- ✅ سحب رأسي يمين → التحكم بالصوت
- ✅ 6 خيارات سرعة: 0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 2.0x
- ✅ اختيار الترجمة من مصادر متعددة
- ✅ حفظ موضع التشغيل (SharedPreferences كل 5 ثوانٍ)
- ✅ وضع ملء الشاشة (landscape)

**لا يحتوي على:**
- ❌ إيماءة السطوع (فقط في OfflinePlayer)
- ❌ ضغط مطوّل للسرعة 2x

---

### 5. OfflinePlayerScreen — المشغّل المحلي

**الملف:** `ui/screens/OfflinePlayerScreen.kt` (~1714 سطر) — **أكبر ملف**

**المحتوى:**
- وضع أفقي إجباري + إبقاء الشاشة مستيقظة + إخفاء شريط النظام
- تراكب ترجمة مخصص (Canvas + خط IBM Plex Sans Arabic مع ظل)
- شريط علوي: رجوع + العنوان + S/E indicator + الرئيسية + التحميلات النشطة + ترجمة + حلقات
- مركز: إعادة 10ث / تشغيل-إيقاف / تقديم 10ث
- شريط سحب مخصص (Canvas with thumb)
- زر الحلقة التالية + مؤشر البطارية + الساعة
- **درج الترجمة** (360dp، 7 صفحات)
- **درج الحلقات** للمسلسلات

**ميزات حقيقية مؤكد:**
- ✅ نقر مزدوج (±10 ثوانٍ)
- ✅ سحب رأسي: يمين = صوت، يسار = سطوع
- ✅ ضغط مطوّل = سرعة 2x
- ✅ تراكب ترجمة مع تحكم بالموضع (Y Offset) والحجم ومزامنة الوقت
- ✅ درج ترجمة بـ 7 صفحات
- ✅ اختيار ملف ترجمة من الجهاز (SAF)
- ✅ تحميل ترجمة عربية تلقائي عند فتح حلقة
- ✅ مؤشر البطارية (كل 30 ثانية) + الساعة
- ✅ إخفاء تلقائي للتحكمات بعد 4 ثوانٍ
- ✅ زر الحلقة التالية

---

### 6. DownloadsScreen — إدارة التحميلات

**الملف:** `ui/screens/DownloadsScreen.kt` (~1074 سطر)

**المحتوى:**
- Header: "التحميلات غير المتصلة"
- 3 تبويبات: **مسلسلات** | **أفلام** | **محلي**
- المسلسلات: مجمعة حسب mediaId كبطاقات بملصق + عدد الحلقات
- الأفلام: صفوف فردية
- محلي: متصفح ملفات الجهاز (SAF)

**ميزات حقيقية:**
- ✅ وضع تحديد متعدد مع حذف دفعة
- ✅ تبديل المواسم مع انيميشن
- ✅ ضغط مطوّل على كل حلقة → قائمة سياقية
- ✅ زر تحميل حلقات إضافية (يفتح MovieBoxDownloadSheet)
- ✅ عرض حجم الملف من خيط منفصل

---

### 7. SettingsScreen — الإعدادات

**الملف:** `ui/screens/SettingsScreen.kt` (~614 سطر)

**المحتوى:**
- بطاقة الملف الشخصي:
  - صورة + اسم + اسم مستخدم + بايو
  - أزرار تعديل / تسجيل خروج
  - مصادقة Firebase (Email/Password + Google Sign-In)
- قسم الأدمن (للبريد `ahmedsarri123@gmail.com` فقط):
  - إرسال إشعار بث لجميع المستخدمين
  - إدارة الأقسام المخصصة
- بطاقة العرض:
  - تبديل الملصقات العربية/الإنجليزية
  - تبديل الوضع الليلي

**ميزات حقيقية:**
- ✅ Firebase Auth (إيميل/باسورد + جوجل)
- ✅ حوار إعداد الملف الشخصي (ProfileSetupDialog) عند أول تسجيل
- ✅ رفع الصورة إلى ImgBB
- ✅ بث إشعارات من الأدمن
- ✅ تخصيص الأقسام في الصفحة الرئيسية

---

### 8. ChatScreen — الدردشة

**الملف:** `ui/screens/ChatScreen.kt` (~502 سطر)

**المحتوى:**
- Header: زر رجوع + عنوان الغرفة
- قائمة رسائل (ChatMessageBubble)
- شريط إدخال (ChatInputBar) مع نص + صورة + رد
- حوار عرض الصور
- عرض ملف المستخدم (UserProfileBottomSheet)

**ميزات حقيقية:**
- ✅ رسائل نصية في الوقت الحقيقي (Firebase RTDB)
- ✅ رسائل صور (رفع إلى ImgBB)
- ✅ الرد على رسالة (replyTo)
- ✅ صفحة 50 رسالة مع Load more
- ✅ إنشاء محادثة خاصة عند الضغط على مستخدم
- ✅ تغيير صورة الغرفة العامة

**لا يحتوي على:**
- ❌ سحب للرد (Swipe-to-reply)
- ❌ مؤشر الكتابة (Typing indicator)
- ❌ حذف الرسائل

---

### 9. ChatRoomList — قائمة غرف الدردشة

**الملف:** `ui/screens/ChatRoomList.kt`

**المحتوى:** عرض قائمة الغرف العامة والخاصة التي شارك فيها المستخدم

---

### 10. ExploreScreen — مركز الاستكشاف

**الملف:** `ui/screens/ExploreScreen.kt` (~173 سطر)

**المحتوى:**
- زر AI Chat (أيجواني)
- زر تحميل الترجمات
- زر قائمة المشاهدة
- قسم غرف الدردشة (للمسجلين فقط)

**ملاحظة:** مرجع AI Chat موجود لكن الشاشة المنفصلة غير موجودة كملف مستقل — الـ UI موجود في `ai/ui/AiChatScreen.kt`

---

### 11. WatchlistScreen — قائمة المشاهدة

**الملف:** `ui/screens/WatchlistScreen.kt` (~153 سطر)

**المحتوى:** شبكة 3 أعمدة من الملصقات + زر مزامنة Firebase

**ميزات حقيقية:**
- ✅ مزامنة سحابية (WatchlistSyncManager)
- ✅ زر مزامنة يدوي
- ✅ التمييز بين عناصر TMDB و MovieBox (بادئة `mb_`)

---

### 12. DownloadsScreen — (مذكور أعلاه)

### 13. BrowserScreen — المتصفح الداخلي

**الملف:** `ui/screens/browserScreen.kt`

**المحتوى:** متصفح WebView داخلي للتنقل بين المواقع وحفظ الصور

---

### 14. HistoryScreen — السجل

**الملف:** `ui/screens/HistoryScreen.kt`

**المحتوى:** عرض سجل نشاط المستخدم (الأفلام المفتوحة، التحميلات، تسجيلات الدخول)

---

### 15. SubtitleDownloadsScreen — تحميلات الترجمة

**الملف:** `ui/screens/SubtitleDownloadsScreen.kt`

**المحتوى:** عرض وإدارة ملفات الترجمة المحملة

---

### 16. FullScreenImageViewer — عارض الصور

**الملف:** `ui/screens/FullScreenImageViewer.kt`

**المحتوى:** عرض الصور بحجم كامل مع Zoom

---

### 17. CustomSectionDialog — الأقسام المخصصة

**الملف:** `ui/screens/CustomSectionDialog.kt` (~227 سطر)

**المحتوى:** حوار إدارة الأقسام الترويجية في الصفحة الرئيسية (إضافة/حذف/بحث TMDB)

---

### 18. ProfileSetupDialog — إعداد الملف الشخصي

**الملف:** `ui/screens/ProfileSetupDialog.kt` (~231 سطر)

**المحتوى:** حوار اختيار الاسم + اسم المستخدم + البايو + الصورة (رفع إلى ImgBB)

---

## التقنيات والمكتبات

### الأساسيات
| التقنية | الإصدار | الغرض |
|---------|---------|-------|
| Kotlin | 2.2.10 | لغة البرمجة |
| Jetpack Compose | BOM 2024.09.00 | واجهة المستخدم |
| Material Design 3 | — | نظام التصميم |
| AGP | 9.1.1 | نظام البناء |
| KSP | 2.2.10-2.0.2 | معالجة Annotations |

### التشغيل
| المكتبة | الإصدار | الغرض |
|---------|---------|-------|
| Media3 ExoPlayer | 1.4.1 | مشغل الفيديو |
| Media3 HLS | 1.4.1 | دعم HLS/m3u8 |
| Media3 UI | 1.4.1 | PlayerView |

### الشبكات
| المكتبة | الإصدار | الغرض |
|---------|---------|-------|
| Retrofit | 2.12.0 | HTTP client لـ TMDB |
| OkHttp | 4.10.0 | HTTP client + logging |
| Moshi | 1.15.2 | JSON parsing (KSP) |
| Coil | 2.7.0 | تحميل الصور |
| DataStore | 1.1.7 | تفضيلات المستخدم |

### Firebase
| المكتبة | الإصدار | الغرض |
|---------|---------|-------|
| Firebase BOM | 34.12.0 | إدارة الإصدارات |
| Firebase Auth | — | مصادقة (جوجل + إيميل) |
| Firebase Database | — | دردشة + أقسام + سجل |

### المحلية
| المكتبة | الإصدار | الغرض |
|---------|---------|-------|
| Room | 2.7.0 | قاعدة بيانات محلية |
| Navigation Compose | 2.8.9 | التنقل بين الشاشات |

### أخرى
| المكتبة | الإصدار | الغرض |
|---------|---------|-------|
| Haze | 1.7.2 | تأثيرات الضبابية |
| WorkManager | 2.9.0 | مهام الخلفية |
| CameraX | 1.5.0 | الكاميرا |
| Accompanist | 0.37.3 | الأذونات |
| Roborazzi | 1.59.0 | اختبار لقطات الشاشة |

---

## الأنظمة الفرعية

### 1. نظام التحميل متعدد الخيوط

**الملف:** `utils/MultiThreadDownloader.kt` (~201 سطر)

**الآلية:**
- 8 خيوط متوازية عبر HTTP Range Requests
- يستخدم `RandomAccessFile` للكتابة الآمنة المتوازية
- حفظ تقدم كل خيط في ملف `.progress`
- استئناف: قراءة الملف الموجود وتحديد الأجزاء المفقودة
- إعادة المحاولة 5 مرات مع backoff تصاعدي (2s → 10s)
- تتبع السرعة على نافذة 5 ثوانٍ
- حفظ التقدم كل ~512KB
- HttpURLConnection (ليس OkHttp رغم وجوده)
- User-Agent ثابت: `okhttp/4.10.0`

---

### 2. نظام الترجمة — 3 مصادر

**الملف:** `ui/viewmodel/SubtitleHelper.kt` (~605 سطر)

**المصادر:**
1. **MovieBox** — عبر `moviebox-fastapi.vercel.app/get_subtitles`
2. **Subdl** — عبر `api.subdl.com/api/v1/subtitles`
3. **OpenSubtitles** — عبر `api.opensubtitles.com/api/v1/subtitles`

**القدرات:**
- تحليل SRT/VTT/ASS (SubtitleParser)
- فك ضغط ZIP و GZip
- مطابقة الحلقات في ملفات ZIP الموسمية
- تحميل ترجمة عربية تلقائي عند فتح حلقة

**درج الترجمة (OfflinePlayerScreen):**
- 7 صفحات: التحكم + MovieBox + Subdl + OpenSubtitles + محلية + دفعات + متصفح ملفات
- تحكم بـ: Y Offset، حجم الخط، مزامنة الوقت (±100ms)

---

### 3. نظام MovieBox

**الملفات:** `data/remote/moviebox/` (6 ملفات)

| الملف | الوظيفة |
|-------|---------|
| `MovieBoxApiImpl` | تنفيذ API (بحث، روابط، ترجمة، تصفح) |
| `MovieBoxHttpClient` | عميل HTTP مع failover بين 7 خوادم |
| `MovieBoxSigner` | توقيع HMAC-MD5 للطلبات |
| `MovieBoxDeviceInfo` | تزييف هوية الجهاز (Redmi/OneRoom) |
| `MovieBoxRepository` | تخزين مؤقت + SharedPreferences cache |
| `MovieBoxViewModel` | StateFlows مع timeout 30s |

**الـ Backend:** `moviebox-fastapi.vercel.app` كوسيط

**الخوادم المباشرة:** api6.aoneroom.com, api5.aoneroom.com, api4.aoneroom.com, api4sg.aoneroom.com, api3.aoneroom.com, api6sg.aoneroom.com, api.inmoviebox.com

---

### 4. نظام AI Chat

**الملفات:** `ai/` (16 ملف)

**المزودون:**
1. **Google Gemini** — عبر Generative Language API
2. **OpenCode Zen** — OpenAI-compatible
3. **Bynara** — OpenAI-compatible
4. **Agnes AI** — OpenAI-compatible

**القدرات:**
- SSE Streaming للردود
- Tool use (7 أدوات): بحث TMDB، قائمة المشاهدة، التحميلات، سجل النشاط، إضافة للمشاهدة، تفاصيل TMDB، تحميل محتوى
- نظام موافقة المستخدم على الأدوات الحساسة (تحميل المحتوى)
- حفظ المحادثات في Room DB
- System prompt بالعربية

---

### 5. نظام الدردشة

**الملف:** `chat/ChatManager.kt` (~256 سطر)

- Firebase Realtime Database
- نوعان من الغرف: **عامة** (public) و**خاصة** (DM)
- رسائل نصية + صور (ImgBB)
- تزامن آخر رسالة في `chat_rooms` و `user_rooms`
- Pagination: 50 رسالة في كل مرة
- تخزين مؤقت في الذاكرة

---

### 6. نظام المصادقة

**الملف:** `auth/AuthManager.kt` (~68 سطر)

- Google Sign-In عبر Credential Manager + Firebase Auth
- Email/Password عبر Firebase Auth
- SHA-256 nonce للأمان
- Google OAuth Client ID ثابت في الكود

---

### 7. قاعدة البيانات المحلية (Room)

**الاسم:** `cinemios_database` — version 15

**الجداول:**
| الجدول | الغرض |
|--------|-------|
| `watchlist` | قائمة المشاهدة (مع soft-delete) |
| `downloads` | التحميلات (التقدم، الحالة، الجودة) |
| `subtitle_downloads` | ملفات الترجمة المحملة |
| `episode_watch_status` | حالة مشاهدة الحلقات |
| `season_meta` | بيانات المواسم المؤقتة |
| `saved_images` | الصور المحفوظة من المتصفح |
| `ai_conversations` | محادثات AI |
| `ai_messages` | رسائل AI |

---

## هيكل الملفات

```
wacher/
├── .github/workflows/build.yml          # CI: بناء APK على push إلى main
├── app/
│   ├── build.gradle.kts                  # إعدادات البناء + API keys
│   ├── google-services.json              # إعدادات Firebase
│   └── src/main/
│       ├── AndroidManifest.xml           # الأذونات + Deep links
│       ├── java/com/example/
│       │   ├── MainActivity.kt           # النقطة الرئيسية + Navigation + BottomBar
│       │   ├── WatcheraApplication.kt    # Application class
│       │   │
│       │   ├── ai/                       # ★ نظام AI
│       │   │   ├── AiModels.kt           # نماذج البيانات
│       │   │   ├── AiSessionManager.kt   # إدارة الجلسات
│       │   │   ├── AiViewModel.kt       # ViewModel (~560 سطر)
│       │   │   ├── data/                 # Room DAO + Entities
│       │   │   ├── provider/             # Gemini + OpenAI-compatible
│       │   │   ├── tools/                # تنفيذ الأدوات
│       │   │   └── ui/                   # مكونات UI للشات
│       │   │
│       │   ├── auth/                     # المصادقة
│       │   │   ├── AuthManager.kt        # Google Sign-In + Email
│       │   │   ├── UserManager.kt        # الملف الشخصي
│       │   │   └── ActivityLogManager.kt # سجل النشاط
│       │   │
│       │   ├── chat/                     # الدردشة
│       │   │   ├── ChatManager.kt        # Firebase RTDB operations
│       │   │   └── ChatModels.kt         # نماذج البيانات
│       │   │
│       │   ├── data/
│       │   │   ├── local/                # Room DB
│       │   │   ├── remote/               # APIs (TMDB + MovieBox)
│       │   │   ├── repository/           # MovieRepository
│       │   │   └── sync/                 # WatchlistSyncManager
│       │   │
│       │   ├── ui/
│       │   │   ├── components/           # 17 مكون قابل لإعادة الاستخدام
│       │   │   ├── screens/              # 16 شاشة
│       │   │   ├── theme/                # ألوان + خط + Theme
│       │   │   └── viewmodel/            # MovieVM + SubtitleHelper
│       │   │
│       │   └── utils/
│       │       ├── MultiThreadDownloader.kt  # تحميل متوازي
│       │       └── TextDirection.kt          # RTL detection
│       │
│       └── res/                          # موارد Android
│
├── gradle/libs.versions.toml             # Version Catalog
├── firebase.json                         # إعدادات Firebase
├── .env.example                          # متغيرات البيئة
└── build.gradle.kts                      # Root build
```

---

## التشغيل

### المتطلبات
1. Android Studio (Ladybug+)
2. JDK 17+
3. Android SDK 36

### الخطوات

```bash
# 1. استنساخ
git clone https://github.com/ahmedio3/wacher.git
cd wacher

# 2. إنشاء ملف .env من المثال
cp .env.example .env

# 3. إضافة المفاتيح في .env:
# GEMINI_API_KEY=
# OPENCODE_ZEN_API_KEY=
# BYNARA_API_KEY=
# AGNES_API_KEY=
# TMDB_API_KEY=
# SUBDL_API_KEY=
# OPENSUBTITLES_API_KEY=
# IMGBB_API_KEY=

# 4. فتح في Android Studio والتشغيل
```

### أو بناء من Terminal:
```bash
./gradlew assembleDebug
```

---

## Deep Links

التطبيق يدعم:
- `cinemios://show/{type}/{id}`
- `https://watchera.com/show/{type}/{id}`

---

## الألوان (Theme)

**الوضع الفاتح (Comfort Beige):**
- الخلفية: `#F9F5EE` (بيج ناعم)
- السطح: `#EFECE4` (شوفان)
- الأساسي: `#8C6D4F` (بني كراميل)
- التقييم: `#D6A45C` (ذهبي دافئ)
- النص: `#2C241E` (إسبريسو)

**الوضع الليلي:**
- الخلفاء: `#0D1117`
- السطح: `#161B22`
- الأساسي: `#3B4A6B` (أزرق داكن)

**الخط:** IBM Plex Sans Arabic (Google Fonts)

---

## الإعدادات المطلوبة (API Keys)

| المفتاح | الخدمة | مطلوب |
|---------|--------|-------|
| `TMDB_API_KEY` | The Movie Database | نعم (بيانات الأفلام) |
| `SUBDL_API_KEY` | Subdl Subtitles | نعم (ترجمة) |
| `OPENSUBTITLES_API_KEY` | OpenSubtitles | نعم (ترجمة) |
| `IMGBB_API_KEY` | رفع الصور | نعم (محادثة + بروفايل) |
| `GEMINI_API_KEY` | Google Gemini AI | اختياري |
| `OPENCODE_ZEN_API_KEY` | OpenCode Zen AI | اختياري |
| `BYNARA_API_KEY` | Bynara AI | اختياري |
| `AGNES_API_KEY` | Agnes AI | اختياري |

---

<div align="center">

**بُني بـ ❤️ باستخدام Kotlin و Jetpack Compose**

</div>
