package com.example.passkeydriver.security

import android.content.Context

class TabletLockoutManager(context: Context) {

    private val prefs = context.getSharedPreferences("lockout", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_FAIL_COUNT  = "tablet_fail_count"
        private const val KEY_LOCKED_UNTIL = "tablet_locked_until"
        private const val MAX_ATTEMPTS    = 5
        private const val LOCK_DURATION_MS = 30 * 60 * 1000L // 30 min
    }

    fun isLocked(): Boolean {
        val until = prefs.getLong(KEY_LOCKED_UNTIL, 0L)
        return System.currentTimeMillis() < until
    }

    fun remainingSeconds(): Long {
        val until = prefs.getLong(KEY_LOCKED_UNTIL, 0L)
        return maxOf(0L, (until - System.currentTimeMillis()) / 1000L)
    }

    fun recordFailedPin() {
        val count = prefs.getInt(KEY_FAIL_COUNT, 0) + 1
        prefs.edit().putInt(KEY_FAIL_COUNT, count).apply()
        if (count >= MAX_ATTEMPTS) {
            val lockedUntil = System.currentTimeMillis() + LOCK_DURATION_MS
            prefs.edit().putLong(KEY_LOCKED_UNTIL, lockedUntil).apply()
        }
    }

    fun reset() {
        prefs.edit().remove(KEY_FAIL_COUNT).remove(KEY_LOCKED_UNTIL).apply()
    }
}
