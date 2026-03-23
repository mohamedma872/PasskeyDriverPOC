package com.example.passkeydriver.security

import android.content.Context

class DriverLockoutManager(context: Context) {

    private val prefs = context.getSharedPreferences("lockout", Context.MODE_PRIVATE)

    companion object {
        private const val MAX_ATTEMPTS    = 5
        private const val LOCK_DURATION_MS = 60 * 60 * 1000L // 60 min
    }

    private fun failKey(driverId: String)  = "face_fail_count_$driverId"
    private fun untilKey(driverId: String) = "face_locked_until_$driverId"

    fun isLocked(driverId: String): Boolean {
        val until = prefs.getLong(untilKey(driverId), 0L)
        return System.currentTimeMillis() < until
    }

    fun remainingSeconds(driverId: String): Long {
        val until = prefs.getLong(untilKey(driverId), 0L)
        return maxOf(0L, (until - System.currentTimeMillis()) / 1000L)
    }

    fun recordFailedFace(driverId: String) {
        val count = prefs.getInt(failKey(driverId), 0) + 1
        prefs.edit().putInt(failKey(driverId), count).apply()
        if (count >= MAX_ATTEMPTS) {
            val lockedUntil = System.currentTimeMillis() + LOCK_DURATION_MS
            prefs.edit().putLong(untilKey(driverId), lockedUntil).apply()
        }
    }

    fun reset(driverId: String) {
        prefs.edit().remove(failKey(driverId)).remove(untilKey(driverId)).apply()
    }
}
