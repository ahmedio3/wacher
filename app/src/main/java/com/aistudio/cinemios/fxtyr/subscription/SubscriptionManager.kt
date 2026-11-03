package com.aistudio.cinemios.fxtyr.subscription

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import java.util.Locale
import java.util.UUID

/**
 * Persists the local seePlus subscription and exposes it as a StateFlow.
 *
 * The subscription is scoped to the signed-in Firebase user: when no user is
 * signed in, the effective state is always inactive and no mutation is allowed.
 * The payment step is simulated by [activate]: it only accepts the secret
 * confirmation PIN configured below and never talks to a payment provider.
 */
class SubscriptionManager(
    private val preferences: SharedPreferences,
    private val uidProvider: () -> String?
) {
    private val _state = MutableStateFlow(readState())
    val state: StateFlow<SubscriptionState> = _state.asStateFlow()

    val isActive: Boolean get() = _state.value.isActive

    /**
     * Activates a subscription when [pin] matches the confirmation PIN.
     * Returns true when the subscription was activated.
     */
    fun activate(plan: SubscriptionPlan, pin: String): Boolean {
        val uid = currentUid() ?: return false
        if (!verifyPin(pin)) return false

        val now = System.currentTimeMillis()
        val state = SubscriptionState(
            isActive = true,
            plan = plan,
            startedAt = now,
            expiresAt = calculateExpiry(plan, now),
            autoRenew = true,
            transactionId = generateTransactionId(now)
        )

        persist(state, uid)
        _state.value = state
        return true
    }

    fun cancel() {
        val uid = currentUid() ?: return
        val current = _state.value
        val cancelled = current.copy(isActive = false, autoRenew = false)
        persist(cancelled, uid)
        _state.value = cancelled
    }

    fun setAutoRenew(enabled: Boolean) {
        val uid = currentUid() ?: return
        val current = _state.value
        if (!current.isActive) return

        val updated = current.copy(autoRenew = enabled)
        persist(updated, uid)
        _state.value = updated
    }

    fun refresh() {
        _state.value = readState()
    }

    fun verifyPin(pin: String): Boolean = pin == CONFIRMATION_PIN

    private fun currentUid(): String? =
        uidProvider()?.takeIf { it.isNotBlank() }

    private fun calculateExpiry(plan: SubscriptionPlan, now: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.MONTH, plan.durationMonths)
        }
        return calendar.timeInMillis
    }

    private fun generateTransactionId(now: Long): String {
        val date = Calendar.getInstance().apply { timeInMillis = now }
        val stamp = String.format(
            Locale.US,
            "%04d%02d%02d",
            date.get(Calendar.YEAR),
            date.get(Calendar.MONTH) + 1,
            date.get(Calendar.DAY_OF_MONTH)
        )
        val suffix = UUID.randomUUID().toString().take(6).uppercase(Locale.US)
        return "SP-$stamp-$suffix"
    }

    private fun readState(): SubscriptionState {
        val uid = currentUid() ?: return SubscriptionState()
        migrateLegacyIfNeeded(uid)

        val plan = SubscriptionPlan.fromKey(preferences.getString(key(KEY_PLAN, uid), null))
        val expiresAt = preferences.getLong(key(KEY_EXPIRES_AT, uid), 0L)
        val isActive = preferences.getBoolean(key(KEY_ACTIVE, uid), false) &&
            plan != null &&
            expiresAt > System.currentTimeMillis()

        return SubscriptionState(
            isActive = isActive,
            plan = plan,
            startedAt = preferences.getLong(key(KEY_STARTED_AT, uid), 0L),
            expiresAt = expiresAt,
            autoRenew = isActive && preferences.getBoolean(key(KEY_AUTO_RENEW, uid), true),
            transactionId = preferences.getString(key(KEY_TRANSACTION_ID, uid), "").orEmpty()
        )
    }

    private fun persist(state: SubscriptionState, uid: String) {
        preferences.edit()
            .putBoolean(key(KEY_ACTIVE, uid), state.isActive)
            .putString(key(KEY_PLAN, uid), state.plan?.storageKey)
            .putLong(key(KEY_STARTED_AT, uid), state.startedAt)
            .putLong(key(KEY_EXPIRES_AT, uid), state.expiresAt)
            .putBoolean(key(KEY_AUTO_RENEW, uid), state.autoRenew)
            .putString(key(KEY_TRANSACTION_ID, uid), state.transactionId)
            .apply()
    }

    /**
     * Older builds stored a single subscription without a user scope. Move it
     * to the first account that signs in so existing members keep their plan.
     */
    private fun migrateLegacyIfNeeded(uid: String) {
        val scopedPlanKey = key(KEY_PLAN, uid)
        if (preferences.contains(scopedPlanKey)) return

        val legacyPlan = preferences.getString(KEY_PLAN, null) ?: return
        preferences.edit()
            .putBoolean(key(KEY_ACTIVE, uid), preferences.getBoolean(KEY_ACTIVE, false))
            .putString(scopedPlanKey, legacyPlan)
            .putLong(key(KEY_STARTED_AT, uid), preferences.getLong(KEY_STARTED_AT, 0L))
            .putLong(key(KEY_EXPIRES_AT, uid), preferences.getLong(KEY_EXPIRES_AT, 0L))
            .putBoolean(key(KEY_AUTO_RENEW, uid), preferences.getBoolean(KEY_AUTO_RENEW, true))
            .putString(
                key(KEY_TRANSACTION_ID, uid),
                preferences.getString(KEY_TRANSACTION_ID, "").orEmpty()
            )
            .apply()

        preferences.edit()
            .remove(KEY_ACTIVE)
            .remove(KEY_PLAN)
            .remove(KEY_STARTED_AT)
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_AUTO_RENEW)
            .remove(KEY_TRANSACTION_ID)
            .apply()
    }

    private fun key(base: String, uid: String): String = "${base}_$uid"

    companion object {
        private const val CONFIRMATION_PIN = "8999"

        private const val KEY_ACTIVE = "seeplus_active"
        private const val KEY_PLAN = "seeplus_plan"
        private const val KEY_STARTED_AT = "seeplus_started_at"
        private const val KEY_EXPIRES_AT = "seeplus_expires_at"
        private const val KEY_AUTO_RENEW = "seeplus_auto_renew"
        private const val KEY_TRANSACTION_ID = "seeplus_transaction_id"
    }
}
