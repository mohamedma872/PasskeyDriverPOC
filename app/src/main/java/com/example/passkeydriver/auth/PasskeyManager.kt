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

class PasskeyManager(context: Context) {

    private val credentialManager = CredentialManager.create(context)
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
            val requestJson = webAuthnServer.generateRegistrationRequest(
                userId = userId,
                username = username,
                displayName = displayName
            )
            Log.d(TAG, "Registration request JSON: $requestJson")

            val createRequest = CreatePublicKeyCredentialRequest(
                requestJson = requestJson
            )
            Log.d(TAG, "CreatePublicKeyCredentialRequest created, calling CredentialManager...")

            val result = credentialManager.createCredential(
                context = context,
                request = createRequest
            )
            Log.d(TAG, "CredentialManager returned: ${result::class.java.simpleName}")

            if (result is CreatePublicKeyCredentialResponse) {
                val responseJson = result.registrationResponseJson
                Log.d(TAG, "Registration response JSON: $responseJson")

                val credentialId = webAuthnServer.processRegistrationResponse(responseJson)
                Log.d(TAG, "Parsed credentialId: $credentialId")

                if (credentialId != null) {
                    webAuthnServer.storeCredential(credentialId, userId)
                    Log.d(TAG, "=== PASSKEY REGISTRATION SUCCESS ===")
                    PasskeyResult.Success(credentialId)
                } else {
                    Log.e(TAG, "Failed to parse credentialId from response")
                    PasskeyResult.Error("Failed to process registration response")
                }
            } else {
                Log.e(TAG, "Unexpected response type: ${result::class.java.name}")
                PasskeyResult.Error("Unexpected response type: ${result::class.java.simpleName}")
            }
        } catch (e: CreateCredentialCancellationException) {
            Log.w(TAG, "User cancelled passkey registration")
            PasskeyResult.Cancelled
        } catch (e: CreateCredentialException) {
            Log.e(TAG, "=== PASSKEY REGISTRATION FAILED ===")
            Log.e(TAG, "Type: ${e.type}")
            Log.e(TAG, "Message: ${e.errorMessage}")
            Log.e(TAG, "Cause: ${e.cause}")
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
            val requestJson = webAuthnServer.generateAuthenticationRequest(credentialId)
            Log.d(TAG, "Authentication request JSON: $requestJson")

            val getRequest = GetCredentialRequest(
                listOf(GetPublicKeyCredentialOption(requestJson = requestJson))
            )
            Log.d(TAG, "Calling CredentialManager.getCredential...")

            val result = credentialManager.getCredential(
                context = context,
                request = getRequest
            )

            val credential = result.credential
            Log.d(TAG, "Got credential type: ${credential::class.java.simpleName}")

            if (credential is PublicKeyCredential) {
                val responseJson = credential.authenticationResponseJson
                Log.d(TAG, "Authentication response JSON: $responseJson")

                val userId = webAuthnServer.verifyAuthenticationResponse(responseJson)
                Log.d(TAG, "Verified userId: $userId")

                if (userId != null) {
                    Log.d(TAG, "=== PASSKEY AUTHENTICATION SUCCESS ===")
                    PasskeyResult.Success(userId)
                } else {
                    Log.e(TAG, "Authentication verification returned null userId")
                    PasskeyResult.Error("Authentication verification failed")
                }
            } else {
                Log.e(TAG, "Unexpected credential type: ${credential::class.java.name}")
                PasskeyResult.Error("Unexpected credential type: ${credential::class.java.simpleName}")
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
