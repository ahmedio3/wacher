package com.aistudio.cinemios.fxtyr.subscription

import java.util.Calendar

/**
 * Plans shown on the seePlus subscription page.
 */
enum class SubscriptionPlan(
    val storageKey: String,
    val title: String,
    val priceLabel: String,
    val periodLabel: String,
    val monthlyEquivalent: String,
    val billingDescription: String,
    val durationMonths: Int,
    val savingsLabel: String? = null
) {
    MONTHLY(
        storageKey = "monthly",
        title = "شهري",
        priceLabel = "49 ج.م",
        periodLabel = "شهر",
        monthlyEquivalent = "49 ج.م شهريًا",
        billingDescription = "يتجدد تلقائيًا كل شهر",
        durationMonths = 1
    ),
    YEARLY(
        storageKey = "yearly",
        title = "سنوي",
        priceLabel = "399 ج.م",
        periodLabel = "سنة",
        monthlyEquivalent = "33 ج.م شهريًا",
        billingDescription = "يتجدد تلقائيًا كل سنة",
        durationMonths = 12,
        savingsLabel = "وفّر 32%"
    );

    companion object {
        fun fromKey(key: String?): SubscriptionPlan? =
            entries.firstOrNull { it.storageKey == key }
    }
}

/**
 * Locally persisted subscription state.
 */
data class SubscriptionState(
    val isActive: Boolean = false,
    val plan: SubscriptionPlan? = null,
    val startedAt: Long = 0L,
    val expiresAt: Long = 0L,
    val autoRenew: Boolean = false,
    val transactionId: String = ""
) {
    fun daysRemaining(now: Long = System.currentTimeMillis()): Int {
        if (!isActive || expiresAt <= now) return 0
        return ((expiresAt - now + DAY_MILLIS - 1) / DAY_MILLIS).toInt()
    }

    companion object {
        private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}

/**
 * Features that may be limited to seePlus members in the future.
 *
 * Everything is available to every user today ([requiresPremium] is false).
 * To gate a feature later, flip [requiresPremium] to true and check
 * [PremiumFeatureAccess.isUnlocked] before exposing the capability.
 */
enum class PremiumFeature(
    val title: String,
    val description: String,
    val requiresPremium: Boolean = false
) {
    OFFLINE_DOWNLOADS(
        title = "تحميل بلا حدود",
        description = "شاهد أعمالك المفضلة بدون إنترنت، في أي وقت.",
        requiresPremium = false
    ),
    EXCLUSIVE_SUBTITLES(
        title = "ترجمات استثنائية",
        description = "ترجمات دقيقة ومختارة بعناية لكل الأفلام والمسلسلات.",
        requiresPremium = false
    ),
    HIGH_QUALITY_PLAYBACK(
        title = "جودة مشاهدة أعلى",
        description = "تجربة مشاهدة أوضح وأكثر سلاسة على جميع الشاشات.",
        requiresPremium = false
    ),
    WATCHLIST_SYNC(
        title = "مزامنة مكتبتك",
        description = "قائمة مشاهدتك ومفضلاتك معك دائمًا وأينما ذهبت.",
        requiresPremium = false
    );

    companion object {
        val all: List<PremiumFeature> get() = entries
    }
}

/**
 * Single place to decide whether a feature is available for the current state.
 */
object PremiumFeatureAccess {
    fun isUnlocked(feature: PremiumFeature, state: SubscriptionState): Boolean =
        !feature.requiresPremium || state.isActive

    fun lockedFeatures(state: SubscriptionState): List<PremiumFeature> =
        PremiumFeature.entries.filter { it.requiresPremium && !state.isActive }
}

private val arabicMonths = arrayOf(
    "يناير",
    "فبراير",
    "مارس",
    "أبريل",
    "مايو",
    "يونيو",
    "يوليو",
    "أغسطس",
    "سبتمبر",
    "أكتوبر",
    "نوفمبر",
    "ديسمبر"
)

fun formatSubscriptionDate(timestamp: Long): String {
    if (timestamp <= 0L) return "—"
    val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
    return "${calendar.get(Calendar.DAY_OF_MONTH)} ${arabicMonths[calendar.get(Calendar.MONTH)]} ${calendar.get(Calendar.YEAR)}"
}
