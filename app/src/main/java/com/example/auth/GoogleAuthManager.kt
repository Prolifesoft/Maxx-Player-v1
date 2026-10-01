package com.example.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import androidx.credentials.CustomCredential
import android.util.Log

sealed class GoogleAuthResult {
    data class Success(val user: GoogleUser) : GoogleAuthResult()
    object NoAccountOnDevice : GoogleAuthResult()
    object Cancelled : GoogleAuthResult()
    data class Error(val message: String?, val isConfigurationError: Boolean = false) : GoogleAuthResult()
}

fun Context.findActivity(): Activity? {
    var currentContext: Context? = this
    while (currentContext != null) {
        if (currentContext is Activity) {
            return currentContext
        }
        if (currentContext is ContextWrapper) {
            val base = currentContext.baseContext
            if (base === currentContext || base == null) break
            currentContext = base
        } else {
            break
        }
    }
    return GoogleAuthManager.currentActivity?.get()
}

object GoogleAuthManager {
    var currentActivity: java.lang.ref.WeakReference<Activity>? = null

    suspend fun signInWithGoogle(context: Context, serverClientId: String): GoogleAuthResult {
        val activity = context.findActivity() ?: (context as? Activity) ?: currentActivity?.get()
        if (activity == null) {
            Log.w("GoogleAuth", "No Activity context available for CredentialManager; proceeding in test/emulator mode")
            return GoogleAuthResult.NoAccountOnDevice
        }

        val credentialManager = CredentialManager.create(activity)
        
        // Android CredentialManager requires the Web Client ID (audience), never the Android/Installed client ID
        val effectiveClientId = if (serverClientId.contains("76n7d7o8brpa0f3do0s3jieivh7oqm6s") || serverClientId.isBlank()) {
            "65327632118-hpoe8apnlf4s9aqjhfeicvb1obv4slkc.apps.googleusercontent.com"
        } else {
            serverClientId
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(effectiveClientId)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
            
        return try {
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            
            val googleCred = when {
                credential is GoogleIdTokenCredential -> credential
                credential is CustomCredential && 
                    (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL || 
                     credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL) -> {
                    try {
                        GoogleIdTokenCredential.createFrom(credential.data)
                    } catch (e: Exception) {
                        Log.w("GoogleAuth", "Failed to parse GoogleIdTokenCredential: ${e.message}")
                        null
                    }
                }
                else -> null
            }

            if (googleCred != null) {
                val email = googleCred.id
                val name = googleCred.displayName ?: email.substringBefore("@")
                GoogleAuthResult.Success(
                    GoogleUser(
                        id = email,
                        displayName = name,
                        email = email,
                        givenName = googleCred.givenName,
                        familyName = googleCred.familyName,
                        photoUrl = googleCred.profilePictureUri?.toString()
                    )
                )
            } else {
                GoogleAuthResult.Error("Kimlik yanıtı işlenemedi (${credential.type})")
            }
        } catch (e: NoCredentialException) {
            Log.w("GoogleAuth", "No credential available on device: ${e.message}")
            GoogleAuthResult.NoAccountOnDevice
        } catch (e: GetCredentialCancellationException) {
            Log.i("GoogleAuth", "User cancelled credential picker")
            GoogleAuthResult.Cancelled
        } catch (e: GetCredentialException) {
            Log.w("GoogleAuth", "Sign-in warning with GetCredentialException: ${e.message}")
            val msg = e.message ?: ""
            val isConfigErr = msg.contains("28433") || 
                              msg.contains("Developer error", ignoreCase = true) || 
                              msg.contains("10") ||
                              msg.contains("serverClientId", ignoreCase = true)
            if (msg.contains("No credential", ignoreCase = true) || 
                msg.contains("16", ignoreCase = true) || 
                msg.contains("Activity", ignoreCase = true) ||
                msg.contains("provider", ignoreCase = true)) {
                GoogleAuthResult.NoAccountOnDevice
            } else {
                GoogleAuthResult.Error(msg, isConfigurationError = isConfigErr)
            }
        } catch (e: Exception) {
            Log.w("GoogleAuth", "Sign-in warning: ${e.message}")
            GoogleAuthResult.NoAccountOnDevice
        }
    }
}

data class GoogleUser(
    val id: String,
    val displayName: String?,
    val email: String?,
    val givenName: String? = null,
    val familyName: String? = null,
    val photoUrl: String? = null
)
