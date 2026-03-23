package com.example.passkeydriver.network

import com.example.passkeydriver.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class DriverInfo(val id: String, val name: String, val isEnrolled: Boolean)
data class PinCheckResult(val driverId: String, val name: String)
data class VerifyResult(val matched: Boolean, val score: Float, val driverId: String, val name: String)
data class DuplicateCheckResult(val isDuplicate: Boolean, val name: String?)

object FleetApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val baseUrl get() = BuildConfig.FLEET_API_URL
    private val secret  get() = BuildConfig.FLEET_SYNC_SECRET

    private fun get(path: String): okhttp3.Response {
        val req = Request.Builder()
            .url("$baseUrl$path")
            .addHeader("Authorization", "Bearer $secret")
            .get()
            .build()
        return client.newCall(req).execute()
    }

    private fun post(path: String, body: JSONObject): okhttp3.Response {
        val req = Request.Builder()
            .url("$baseUrl$path")
            .addHeader("Authorization", "Bearer $secret")
            .post(body.toString().toRequestBody(JSON))
            .build()
        return client.newCall(req).execute()
    }

    /** Fetch the full list of registered drivers for the picker screen. */
    suspend fun listDrivers(): List<DriverInfo>? = withContext(Dispatchers.IO) {
        runCatching {
            val resp = get("/api/fleet/drivers")
            if (!resp.isSuccessful) return@withContext null
            val arr = JSONObject(resp.body!!.string()).getJSONArray("drivers")
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                DriverInfo(
                    id         = o.getString("id"),
                    name       = o.getString("name"),
                    isEnrolled = o.getBoolean("isEnrolled")
                )
            }
        }.getOrNull()
    }

    /**
     * Verify a PIN for a known driver.
     * Driver is already identified from the picker; PIN confirms identity.
     * Argon2id verify runs server-side — no hash computed on device.
     */
    suspend fun checkPin(driverId: String, pin: String): PinCheckResult? = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().put("driverId", driverId).put("pin", pin)
            val resp = post("/api/fleet/verify", body)
            if (!resp.isSuccessful) return@withContext null
            val json = JSONObject(resp.body!!.string())
            PinCheckResult(
                driverId = json.getString("driverId"),
                name     = json.getString("name")
            )
        }.getOrNull()
    }

    /**
     * Verify a face embedding against the stored embeddings for this driver.
     * Server looks up by driverId — no PIN-derived value needed.
     */
    suspend fun verifyFace(driverId: String, embedding: FloatArray): VerifyResult? = withContext(Dispatchers.IO) {
        runCatching {
            val embJson = JSONArray().apply { embedding.forEach { put(it.toDouble()) } }
            val body = JSONObject()
                .put("driverId", driverId)
                .put("embedding", embJson)
            val resp = post("/api/fleet/verify", body)
            if (!resp.isSuccessful) return@withContext null
            val json = JSONObject(resp.body!!.string())
            VerifyResult(
                matched  = json.getBoolean("matched"),
                score    = json.getDouble("score").toFloat(),
                driverId = json.getString("driverId"),
                name     = json.getString("name")
            )
        }.getOrNull()
    }

    /**
     * Check if a face embedding matches any already-enrolled driver.
     * Called on the first enrollment capture to prevent duplicate registrations.
     */
    suspend fun checkDuplicateFace(embedding: FloatArray): DuplicateCheckResult? = withContext(Dispatchers.IO) {
        runCatching {
            val embJson = JSONArray().apply { embedding.forEach { put(it.toDouble()) } }
            val body = JSONObject().put("embedding", embJson)
            val resp = post("/api/fleet/check-duplicate", body)
            if (!resp.isSuccessful) return@withContext null
            val json = JSONObject(resp.body!!.string())
            DuplicateCheckResult(
                isDuplicate = json.getBoolean("isDuplicate"),
                name        = json.optString("name").takeIf { it.isNotEmpty() }
            )
        }.getOrNull()
    }

    /**
     * Upload a newly enrolled driver with all face embeddings to Neon.
     * Raw PIN is sent over HTTPS — server computes and stores the HMAC hash.
     */
    suspend fun registerDriver(
        id: String,
        name: String,
        pin: String,
        embeddings: List<FloatArray>
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val embArray = JSONArray().apply {
                embeddings.forEach { emb ->
                    put(JSONArray().apply { emb.forEach { v -> put(v.toDouble()) } })
                }
            }
            val body = JSONObject()
                .put("id", id)
                .put("name", name)
                .put("pin", pin)
                .put("embeddings", embArray)
            val resp = post("/api/fleet/register", body)
            resp.isSuccessful
        }.getOrElse { false }
    }
}
