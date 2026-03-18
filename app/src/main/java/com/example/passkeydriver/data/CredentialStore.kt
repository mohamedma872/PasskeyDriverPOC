package com.example.passkeydriver.data

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * Persistent credential storage using SharedPreferences.
 * Survives app restart, app uninstall/reinstall (if backup is enabled), and data clear.
 *
 * POC LAYER: In production, replace this with API calls to your backend server.
 * See [ProductionWebAuthnServer] for what the production version looks like.
 *
 * What's stored here:
 *   credentialId → { userId, username, displayName }
 *
 * What's stored in Google Password Manager (NOT here):
 *   The actual private key used for signing challenges
 */
class CredentialStore(context: Context) {

    companion object {
        private const val TAG = "CredentialStore"
        private const val PREFS_NAME = "passkey_credentials"
        private const val KEY_CREDENTIALS = "stored_credentials"
        private const val KEY_DRIVERS = "registered_drivers"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Credential ID → User ID mapping (what the server would store) ──

    fun storeCredential(credentialId: String, userId: String) {
        val credentials = getAllCredentials().toMutableMap()
        credentials[credentialId] = userId
        saveCredentials(credentials)
        Log.d(TAG, "Stored credential: $credentialId → $userId")
    }

    fun getUserIdByCredentialId(credentialId: String): String? {
        val userId = getAllCredentials()[credentialId]
        Log.d(TAG, "Lookup credentialId=$credentialId → userId=$userId")
        return userId
    }

    fun getAllCredentials(): Map<String, String> {
        val json = prefs.getString(KEY_CREDENTIALS, "{}") ?: "{}"
        return try {
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith { obj.getString(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read credentials", e)
            emptyMap()
        }
    }

    private fun saveCredentials(credentials: Map<String, String>) {
        val json = JSONObject(credentials).toString()
        prefs.edit().putString(KEY_CREDENTIALS, json).apply()
    }

    // ── Driver registration state (which drivers have passkeys) ──

    fun markDriverHasPasskey(driverId: String, credentialId: String, username: String, displayName: String) {
        val drivers = getAllRegisteredDrivers().toMutableMap()
        val driverJson = JSONObject().apply {
            put("driverId", driverId)
            put("credentialId", credentialId)
            put("username", username)
            put("displayName", displayName)
        }
        drivers[driverId] = driverJson.toString()
        saveDrivers(drivers)
        Log.d(TAG, "Marked driver as passkey-registered: $driverId ($displayName)")
    }

    fun getRegisteredDrivers(): List<Driver> {
        return getAllRegisteredDrivers().values.mapNotNull { jsonStr ->
            try {
                val obj = JSONObject(jsonStr)
                Driver(
                    id = obj.getString("driverId"),
                    username = obj.getString("username"),
                    displayName = obj.getString("displayName"),
                    hasPasskey = true,
                    credentialId = obj.getString("credentialId")
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse driver", e)
                null
            }
        }
    }

    fun isDriverRegistered(driverId: String): Boolean {
        return getAllRegisteredDrivers().containsKey(driverId)
    }

    private fun getAllRegisteredDrivers(): Map<String, String> {
        val json = prefs.getString(KEY_DRIVERS, "{}") ?: "{}"
        return try {
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith { obj.getString(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read drivers", e)
            emptyMap()
        }
    }

    private fun saveDrivers(drivers: Map<String, String>) {
        val json = JSONObject(drivers).toString()
        prefs.edit().putString(KEY_DRIVERS, json).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
        Log.d(TAG, "All stored credentials cleared")
    }
}
