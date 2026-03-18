package com.example.passkeydriver.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class DriverApi(private val baseUrl: String = "https://passkey-backend-tau.vercel.app") {

    companion object {
        private const val TAG = "DriverApi"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = "application/json".toMediaType()

    suspend fun getDrivers(): List<Driver> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/api/drivers").get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw Exception("Empty response")
            Log.d(TAG, "getDrivers: $body")
            val arr = JSONObject(body).getJSONArray("drivers")
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Driver(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    username = obj.getString("username"),
                    createdAt = obj.optString("createdAt").takeIf { it.isNotEmpty() },
                    cardIssuedAt = obj.optString("cardIssuedAt").takeIf { it.isNotEmpty() && it != "null" }
                )
            }
        }
    }

    suspend fun getDriverById(driverId: String): Driver? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/api/drivers/$driverId").get().build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext null
            val body = response.body?.string() ?: throw Exception("Empty response")
            Log.d(TAG, "getDriverById: $body")
            val obj = JSONObject(body).getJSONObject("driver")
            Driver(
                id = obj.getString("id"),
                name = obj.getString("name"),
                username = obj.getString("username"),
                createdAt = obj.optString("createdAt").takeIf { it.isNotEmpty() },
                password = obj.optString("password").takeIf { it.isNotEmpty() && it != "null" },
                pin = obj.optString("pin").takeIf { it.isNotEmpty() && it != "null" },
                cardIssuedAt = obj.optString("cardIssuedAt").takeIf { it.isNotEmpty() && it != "null" }
            )
        }
    }

    suspend fun markCardIssued(driverId: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().apply { put("driverId", driverId) }
            .toString().toRequestBody(json)
        val request = Request.Builder()
            .url("$baseUrl/api/drivers/issue-card")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: throw Exception("Empty response")
            Log.d(TAG, "markCardIssued: $responseBody")
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
        }
    }

    suspend fun verifyPin(driverId: String, pin: String): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("driverId", driverId)
            put("pin", pin)
        }.toString().toRequestBody(json)
        val request = Request.Builder()
            .url("$baseUrl/api/drivers/verify-pin")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: throw Exception("Empty response")
            JSONObject(responseBody).optBoolean("valid", false)
        }
    }

    suspend fun verifyPassword(username: String, password: String): Driver? = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("username", username)
            put("password", password)
        }.toString().toRequestBody(json)
        val request = Request.Builder()
            .url("$baseUrl/api/drivers/verify-password")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: throw Exception("Empty response")
            val obj = JSONObject(responseBody)
            if (!obj.optBoolean("valid", false)) return@withContext null
            Driver(
                id = obj.getString("driverId"),
                name = obj.getString("name"),
                username = obj.getString("username")
            )
        }
    }
}
