package com.example.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.NavBackStackEntry

/**
 * ترتيب التابات الأربعة (لحساب اتجاه slide بينهم).
 * كلما زاد index = تقدّم (مثلاً من home=0 إلى explore=1).
 */
val TAB_ROUTES = listOf("home", "explore", "downloads", "settings")

/**
 * يحسب إذا كان الانتقال "تقدّماً" (forward) أم "رجوعاً" (back).
 *
 * @param fromRoute مسار الشاشة المُغادرة
 * @param toRoute مسار الشاشة القادمة
 * @param isPopTransition `true` إذا كان هذا يُستدعى من popEnterTransition/popExitTransition (أي رجوع)
 * @return `true` = حركة للأمام (تقدّم)، `false` = رجوع
 */
fun isForwardNavigation(
    fromRoute: String?,
    toRoute: String?,
    isPopTransition: Boolean
): Boolean {
    val fromIdx = TAB_ROUTES.indexOf(fromRoute)
    val toIdx = TAB_ROUTES.indexOf(toRoute)
    return when {
        // كلا المسارين من التابات: قارن الفهرس
        fromIdx >= 0 && toIdx >= 0 -> toIdx > fromIdx
        // غير ذلك: push = forward، pop = back
        else -> !isPopTransition
    }
}

/**
 * دخول الشاشة: تحدد من أين تبدأ الشاشة الجديدة (اليسار أو اليمين).
 *
 * @param forward `true` = تقدّم، `false` = رجوع
 * @param layoutDirection اتجاه الكتابة (RTL/LTR) — يُستخدم ديناميكياً
 * @param durationMillis مدة الـ animation بالمللي ثانية
 */
fun AnimatedContentTransitionScope<NavBackStackEntry>.slideIn(
    forward: Boolean,
    layoutDirection: LayoutDirection,
    durationMillis: Int = 350
): EnterTransition {
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val initialOffsetX = { fullWidth: Int ->
        if (forward) {
            if (isRtl) -fullWidth else fullWidth
        } else {
            if (isRtl) fullWidth else -fullWidth
        }
    }
    return slideInHorizontally(
        initialOffsetX = initialOffsetX,
        animationSpec = tween(durationMillis)
    )
}

/**
 * خروج الشاشة: تحدد إلى أين تذهب الشاشة المُغادرة (اليسار أو اليمين).
 *
 * @param forward `true` = تقدّم، `false` = رجوع
 * @param layoutDirection اتجاه الكتابة (RTL/LTR)
 * @param durationMillis مدة الـ animation بالمللي ثانية
 */
fun AnimatedContentTransitionScope<NavBackStackEntry>.slideOut(
    forward: Boolean,
    layoutDirection: LayoutDirection,
    durationMillis: Int = 350
): ExitTransition {
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val targetOffsetX = { fullWidth: Int ->
        if (forward) {
            if (isRtl) fullWidth else -fullWidth
        } else {
            if (isRtl) -fullWidth else fullWidth
        }
    }
    return slideOutHorizontally(
        targetOffsetX = targetOffsetX,
        animationSpec = tween(durationMillis)
    )
}
