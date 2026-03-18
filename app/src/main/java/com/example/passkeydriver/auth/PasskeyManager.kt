package com.example.passkeydriver.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException

class PasskeyManager(context: Context, private val api: PasskeyApi) {

    private val credentialManager = CredentialManager.create(context)

    // WebAuthnServer kept only for local CredentialStore (driver list persistence)
    val webAuthnServer = WebAuthnServer(context)

    companion object {
        private const val TAG = "PasskeyManager"
    }

    suspend fun registerPasskey(
        context: Context,
        userId: String,
        username: String,
        displayName: String
    ): PasskeyResult {
        Log.d(TAG, "=== PASSKEY REGISTRATION START ===")
        Log.d(TAG, "userId=$userId, username=$username, displayName=$displayName")

        return try {
            // Step 1: Get challenge + WebAuthn options from real backend
            val beginResult = api.registerBegin(userId, username, displayName)
            Log.d(TAG, "Got options from backend, challengeId=${beginResult.challengeId}")
            Log.d(TAG, "Options JSON: ${beginResult.optionsJson}")

            // Step 2: Ask Android Credential Manager to create the passkey (shows fingerprint UI)
            val createRequest = CreatePublicKeyCredentialRequest(
                requestJson = beginResult.optionsJson
            )
            val result = credentialManager.createCredential(context = context, request = createRequest)
            Log.d(TAG, "CredentialManager returned: ${result::class.java.simpleName}")

            if (result !is CreatePublicKeyCredentialResponse) {
                return PasskeyResult.Error("Unexpected response type: ${result::class.java.simpleName}")
            }

            val responseJson = result.registrationResponseJson
            Log.d(TAG, "Registration response JSON: $responseJson")

            // Step 3: Send signed response to backend for real crypto verification
            val credentialId = api.registerFinish(beginResult.challengeId, userId, responseJson)
            Log.d(TAG, "registerFinish credentialId: $credentialId")

            if (credentialId != null) {
                // Store locally so the driver list survives app restart
                webAuthnServer.storeCredential(credentialId, userId)
                Log.d(TAG, "=== PASSKEY REGISTRATION SUCCESS ===")
                PasskeyResult.Success(credentialId)
            } else {
                Log.e(TAG, "Backend verification returned no credentialId")
                PasskeyResult.Error("Registration verification failed on server")
            }

        } catch (e: CreateCredentialCancellationException) {
            Log.w(TAG, "User cancelled passkey registration")
            PasskeyResult.Cancelled
        } catch (e: CreateCredentialException) {
            Log.e(TAG, "=== PASSKEY REGISTRATION FAILED ===")
            Log.e(TAG, "Type: ${e.type}")
            Log.e(TAG, "Message: ${e.errorMessage}")
            Log.e(TAG, "Full exception:", e)
            PasskeyResult.Error("Registration failed: ${e.type} - ${e.errorMessage}")
        } catch (e: Exception) {
            Log.e(TAG, "=== UNEXPECTED ERROR DURING REGISTRATION ===", e)
            PasskeyResult.Error("Unexpected error: ${e.message}")
        }
    }

    suspend fun authenticateWithPasskey(
        context: Context,
        credentialId: String? = null
    ): PasskeyResult {
        Log.d(TAG, "=== PASSKEY AUTHENTICATION START ===")
        Log.d(TAG, "credentialId=$credentialId")

        return try {
            // Step 1: Get auth challenge from real backend
            val beginResult = api.authBegin(
                credentialIds = if (credentialId != null) listOf(credentialId) else null
            )
            Log.d(TAG, "Got auth options from backend, challengeId=${beginResult.challengeId}")
            Log.d(TAG, "Auth options JSON: ${beginResult.optionsJson}")

            // Step 2: Ask Android Credential Manager to sign the challenge (shows fingerprint UI)
            val getRequest = GetCredentialRequest(
                listOf(GetPublicKeyCredentialOption(requestJson = beginResult.optionsJson))
            )
            val result = credentialManager.getCredential(context = context, request = getRequest)
            val credential = result.credential
            Log.d(TAG, "Got credential type: ${credential::class.java.simpleName}")

            if (credential !is PublicKeyCredential) {
                return PasskeyResult.Error("Unexpected credential type: ${credential::class.java.simpleName}")
            }

            val responseJson = credential.authenticationResponseJson
            Log.d(TAG, "Authentication response JSON: $responseJson")

            // Step 3: Send signed response to backend for real crypto verification
            val driverId = api.authFinish(beginResult.challengeId, responseJson)
            Log.d(TAG, "authFinish driverId: $driverId")

            if (driverId != null) {
                Log.d(TAG, "=== PASSKEY AUTHENTICATION SUCCESS ===")
                PasskeyResult.Success(driverId)
            } else {
                Log.e(TAG, "Backend signature verification failed")
                PasskeyResult.Error("Authentication verification failed")
            }

        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "User cancelled passkey authentication")
            PasskeyResult.Cancelled
        } catch (e: NoCredentialException) {
            Log.e(TAG, "No passkey credential found", e)
            PasskeyResult.Error("No passkey found. Please register first.")
        } catch (e: GetCredentialException) {
            Log.e(TAG, "=== PASSKEY AUTHENTICATION FAILED ===")
            Log.e(TAG, "Type: ${e.type}")
            Log.e(TAG, "Message: ${e.errorMessage}")
            Log.e(TAG, "Full exception:", e)
            PasskeyResult.Error("Authentication failed: ${e.type} - ${e.errorMessage}")
        } catch (e: Exception) {
            Log.e(TAG, "=== UNEXPECTED ERROR DURING AUTHENTICATION ===", e)
            PasskeyResult.Error("Unexpected error: ${e.message}")
        }
    }
}

sealed class PasskeyResult {
    data class Success(val data: String) : PasskeyResult()
    data class Error(val message: String) : PasskeyResult()
    data object Cancelled : PasskeyResult()
}
