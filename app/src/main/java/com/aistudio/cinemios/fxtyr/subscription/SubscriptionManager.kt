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
 * The payment step is simulated by [activate]: it only accepts the secret
 * confirmation PIN configured below and never talks to a payment provider.
 */
class SubscriptionManager(
    private val preferences: SharedPreferences
) {
    private val _state = MutableStateFlow(readState())
    val state: StateFlow<SubscriptionState> = _state.asStateFlow()

    val isActive: Boolean get() = _state.value.isActive

    /**
     * Activates a subscription when [pin] matches the confirmation PIN.
     * Returns true when the subscription was activated.
     */
    fun activate(plan: SubscriptionPlan, pin: String): Boolean {
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

        persist(state)
        _state.value = state
        return true
    }

    fun cancel() {
        val current = _state.value
        val cancelled = current.copy(isActive = false, autoRenew = false)
        persist(cancelled)
        _state.value = cancelled
    }

    fun setAutoRenew(enabled: Boolean) {
        val current = _state.value
        if (!current.isActive) return

        val updated = current.copy(autoRenew = enabled)
        persist(updated)
        _state.value = updated
    }

    fun refresh() {
        _state.value = readState()
    }

    fun verifyPin(pin: String): Boolean = pin == CONFIRMATION_PIN

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
        val plan = SubscriptionPlan.fromKey(preferences.getString(KEY_PLAN, null))
        val expiresAt = preferences.getLong(KEY_EXPIRES_AT, 0L)
        val isActive = preferences.getBoolean(KEY_ACTIVE, false) &&
            plan != null &&
            expiresAt > System.currentTimeMillis()

        return SubscriptionState(
            isActive = isActive,
            plan = plan,
            startedAt = preferences.getLong(KEY_STARTED_AT, 0L),
            expiresAt = expiresAt,
            autoRenew = isActive && preferences.getBoolean(KEY_AUTO_RENEW, true),
            transactionId = preferences.getString(KEY_TRANSACTION_ID, "").orEmpty()
        )
    }

    private fun persist(state: SubscriptionState) {
        preferences.edit()
            .putBoolean(KEY_ACTIVE, state.isActive)
            .putString(KEY_PLAN, state.plan?.storageKey)
            .putLong(KEY_STARTED_AT, state.startedAt)
            .putLong(KEY_EXPIRES_AT, state.expiresAt)
            .putBoolean(KEY_AUTO_RENEW, state.autoRenew)
            .putString(KEY_TRANSACTION_ID, state.transactionId)
            .apply()
    }

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
