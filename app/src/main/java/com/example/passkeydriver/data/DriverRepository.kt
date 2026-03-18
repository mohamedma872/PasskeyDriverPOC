package com.example.passkeydriver.data

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Driver storage — now loads persisted passkey registrations on startup.
 *
 * ┌─────────────────────────────────────────────────────────┐
 * │  POC: Mock credentials in code + CredentialStore        │
 * │  PRODUCTION: All of this lives on your backend API      │
 * │                                                         │
 * │  POST /api/auth/login        → verify username/password │
 * │  POST /api/passkey/register  → store public key         │
 * │  POST /api/passkey/verify    → verify signature         │
 * │  GET  /api/drivers           → list drivers             │
 * └─────────────────────────────────────────────────────────┘
 */
object DriverRepository {

    private const val TAG = "DriverRepository"

    // Pre-loaded mock drivers (simulating existing backend accounts)
    private val mockCredentials = mapOf(
        "ahmed" to "pass123",
        "fatima" to "pass123",
        "omar" to "pass123",
        "sara" to "pass123"
    )

    private val mockDisplayNames = mapOf(
        "ahmed" to "Ahmed Hassan",
        "fatima" to "Fatima Ali",
        "omar" to "Omar Khalid",
        "sara" to "Sara Mohamed"
    )

    private val _drivers = MutableStateFlow<List<Driver>>(emptyList())
    val drivers: StateFlow<List<Driver>> = _drivers.asStateFlow()

    private var credentialStore: CredentialStore? = null

    /**
     * Initialize with persistent storage and load previously registered drivers.
     * Call this once from MainActivity.
     */
    fun init(store: CredentialStore) {
        credentialStore = store

        // Load drivers that were previously registered with passkeys
        val persistedDrivers = store.getRegisteredDrivers()
        if (persistedDrivers.isNotEmpty()) {
            _drivers.value = persistedDrivers
            Log.d(TAG, "Loaded ${persistedDrivers.size} persisted drivers: ${persistedDrivers.map { it.displayName }}")
        }
    }

    fun verifyCredentials(username: String, password: String): Driver? {
        val normalizedUsername = username.lowercase().trim()
        if (mockCredentials[normalizedUsername] != password) return null

        val existingDriver = _drivers.value.find { it.username == normalizedUsername }
        if (existingDriver != null) return existingDriver

        val driver = Driver(
            id = "driver_${normalizedUsername}",
            username = normalizedUsername,
            displayName = mockDisplayNames[normalizedUsername] ?: normalizedUsername
        )
        _drivers.update { it + driver }
        return driver
    }

    fun markPasskeyRegistered(driverId: String, credentialId: String) {
        _drivers.update { list ->
            list.map {
                if (it.id == driverId) {
                    val updated = it.copy(hasPasskey = true, credentialId = credentialId)
                    // Persist to survive app restart
                    credentialStore?.markDriverHasPasskey(
                        driverId = updated.id,
                        credentialId = credentialId,
                        username = updated.username,
                        displayName = updated.displayName
                    )
                    Log.d(TAG, "Persisted passkey registration for ${updated.displayName}")
                    updated
                } else it
            }
        }
    }

    fun getPasskeyDrivers(): List<Driver> =
        _drivers.value.filter { it.hasPasskey }

    fun findByCredentialId(credentialId: String): Driver? =
        _drivers.value.find { it.credentialId == credentialId }

    fun findById(driverId: String): Driver? =
        _drivers.value.find { it.id == driverId }
}
