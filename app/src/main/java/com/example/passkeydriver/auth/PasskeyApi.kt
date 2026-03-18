package com.example.passkeydriver.auth

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * HTTP client for the Vercel passkey backend.
 * Calls the 4 WebAuthn endpoints that handle real cryptographic verification.
 */
class PasskeyApi(private val baseUrl: String) {

    companion object {
        private const val TAG = "PasskeyApi"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json".toMediaType()

    data class RegisterBeginResult(val challengeId: String, val optionsJson: String)
    data class AuthBeginResult(val challengeId: String, val optionsJson: String)

    /** Step 1 of registration: server generates challenge + WebAuthn options */
    suspend fun registerBegin(
        driverId: String,
        username: String,
        displayName: String
    ): RegisterBeginResult {
        val body = JSONObject().apply {
            put("driverId", driverId)
            put("username", username)
            put("displayName", displayName)
        }
        Log.d(TAG, "registerBegin → driverId=$driverId")
        val response = post("/api/passkey/register-begin", body.toString())
        return RegisterBeginResult(
            challengeId = response.getString("challengeId"),
            optionsJson = response.getJSONObject("options").toString()
        )
    }

    /** Step 2 of registration: server verifies attestation and stores public key */
    suspend fun registerFinish(
        challengeId: String,
        driverId: String,
        responseJson: String
    ): String? {
        val body = JSONObject().apply {
            put("challengeId", challengeId)
            put("driverId", driverId)
            put("response", JSONObject(responseJson))
        }
        Log.d(TAG, "registerFinish → challengeId=$challengeId")
        val result = post("/api/passkey/register-finish", body.toString())
        return if (result.optBoolean("verified")) result.optString("credentialId").takeIf { it.isNotEmpty() }
        else null
    }

    /** Step 1 of authentication: server generates challenge */
    suspend fun authBegin(credentialIds: List<String>? = null): AuthBeginResult {
        val body = JSONObject()
        if (!credentialIds.isNullOrEmpty()) {
            body.put("credentialIds", JSONArray(credentialIds))
        }
        Log.d(TAG, "authBegin → credentialIds=$credentialIds")
        val response = post("/api/passkey/auth-begin", body.toString())
        return AuthBeginResult(
            challengeId = response.getString("challengeId"),
            optionsJson = response.getJSONObject("options").toString()
        )
    }

    /** Step 2 of authentication: server verifies signature with stored public key */
    suspend fun authFinish(challengeId: String, responseJson: String): String? {
        val body = JSONObject().apply {
            put("challengeId", challengeId)
            put("response", JSONObject(responseJson))
        }
        Log.d(TAG, "authFinish → challengeId=$challengeId")
        val result = post("/api/passkey/auth-finish", body.toString())
        return if (result.optBoolean("verified")) result.optString("driverId").takeIf { it.isNotEmpty() }
        else null
    }

    private suspend fun post(path: String, bodyJson: String): JSONObject = withContext(Dispatchers.IO) {
        Log.d(TAG, "POST $baseUrl$path")
        val request = Request.Builder()
            .url("$baseUrl$path")
            .post(bodyJson.toRequestBody(jsonMediaType))
            .header("Content-Type", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: throw Exception("Empty response body")
            Log.d(TAG, "Response ${response.code}: $responseBody")
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: $responseBody")
            }
            JSONObject(responseBody)
        }
    }
}
