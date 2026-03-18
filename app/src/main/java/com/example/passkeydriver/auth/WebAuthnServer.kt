package com.example.passkeydriver.auth

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.passkeydriver.data.CredentialStore
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

/**
 * Mock WebAuthn/FIDO2 server — now with persistent storage.
 *
 * ┌─────────────────────────────────────────────────────────┐
 * │                    POC (this class)                      │
 * │                                                         │
 * │  Challenge generation  → in-memory (OK for POC)         │
 * │  Credential storage    → SharedPreferences (persistent) │
 * │  Signature verification → skipped (mock)                │
 * ├─────────────────────────────────────────────────────────┤
 * │                    PRODUCTION                            │
 * │                                                         │
 * │  Challenge generation  → server-side, stored in Redis   │
 * │  Credential storage    → PostgreSQL/DynamoDB            │
 * │  Signature verification → real crypto verification      │
 * │  All via HTTPS API calls from the app                   │
 * └─────────────────────────────────────────────────────────┘
 */
class WebAuthnServer(context: Context) {

    private val TAG = "WebAuthnServer"
    private val RP_ID = "passkeydriver-poc.web.app"
    private val RP_NAME = "Driver Passkey App"

    private val credentialStore = CredentialStore(context)

    // Active challenges (in-memory is fine — they're short-lived)
    private val activeChallenges = mutableSetOf<String>()

    fun generateRegistrationRequest(userId: String, username: String, displayName: String): String {
        val challenge = generateChallenge()
        activeChallenges.add(challenge)

        val json = JSONObject().apply {
            put("challenge", challenge)
            put("rp", JSONObject().apply {
                put("id", RP_ID)
                put("name", RP_NAME)
            })
            put("user", JSONObject().apply {
                put("id", base64UrlEncode(userId.toByteArray()))
                put("name", username)
                put("displayName", displayName)
            })
            put("pubKeyCredParams", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "public-key")
                    put("alg", -7)
                })
                put(JSONObject().apply {
                    put("type", "public-key")
                    put("alg", -257)
                })
            })
            put("timeout", 60000)
            put("attestation", "none")
            put("authenticatorSelection", JSONObject().apply {
                put("authenticatorAttachment", "platform")
                put("residentKey", "required")
                put("requireResidentKey", true)
                put("userVerification", "required")
            })
        }

        Log.d(TAG, "Generated registration request for $username")
        return json.toString()
    }

    fun processRegistrationResponse(responseJson: String): String? {
        return try {
            Log.d(TAG, "Processing registration response: $responseJson")
            val response = JSONObject(responseJson)
            val credentialId = response.getString("id")
            Log.d(TAG, "Extracted credentialId: $credentialId")
            credentialId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse registration response", e)
            null
        }
    }

    fun storeCredential(credentialId: String, userId: String) {
        credentialStore.storeCredential(credentialId, userId)
        Log.d(TAG, "Credential persisted: $credentialId → $userId")
    }

    fun generateAuthenticationRequest(credentialId: String? = null): String {
        val challenge = generateChallenge()
        activeChallenges.add(challenge)

        val json = JSONObject().apply {
            put("challenge", challenge)
            put("rpId", RP_ID)
            put("timeout", 60000)
            put("userVerification", "required")

            if (credentialId != null) {
                put("allowCredentials", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "public-key")
                        put("id", credentialId)
                        put("transports", JSONArray().apply {
                            put("internal")
                        })
                    })
                })
            }
        }

        Log.d(TAG, "Generated authentication request, credentialId=$credentialId")
        return json.toString()
    }

    fun verifyAuthenticationResponse(responseJson: String): String? {
        return try {
            Log.d(TAG, "Verifying authentication response: $responseJson")
            val response = JSONObject(responseJson)
            val credentialId = response.getString("id")
            val userId = credentialStore.getUserIdByCredentialId(credentialId)
            Log.d(TAG, "credentialId=$credentialId, resolved userId=$userId")
            Log.d(TAG, "Known credentials: ${credentialStore.getAllCredentials().keys}")
            userId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to verify authentication response", e)
            null
        }
    }

    fun getCredentialStore(): CredentialStore = credentialStore

    private fun generateChallenge(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return base64UrlEncode(bytes)
    }

    private fun base64UrlEncode(bytes: ByteArray): String {
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
