package br.com.helpdev.velocimetroalerta.auth

import android.app.Activity
import android.content.Context
import android.os.CancellationSignal
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.FirebaseAuthException
import br.com.helpdev.velocimetroalerta.FirebaseServices

class GoogleAuthRepository(context: Context) {
    private companion object {
        const val TAG = "GoogleAuthRepository"
    }

    private val appContext = context.applicationContext

    fun currentSession(): AccountSession? = FirebaseServices.auth(appContext)?.currentUser?.let { user ->
        AccountSession(user.uid, user.displayName, user.email)
    }

    fun isConfigured(): Boolean = FirebaseServices.auth(appContext) != null

    fun signIn(activity: Activity, callback: (Result<AccountSession>) -> Unit) {
        val auth = FirebaseServices.auth(activity)
        if (auth == null) {
            callback(Result.failure(IllegalStateException(activity.getString(br.com.helpdev.velocimetroalerta.R.string.firebase_not_configured))))
            return
        }
        val clientIdResource = activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName)
        if (clientIdResource == 0) {
            callback(Result.failure(IllegalStateException(activity.getString(br.com.helpdev.velocimetroalerta.R.string.google_sign_in_not_configured))))
            return
        }

        val clientId = activity.getString(clientIdResource)
        val option = GetSignInWithGoogleOption.Builder(clientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credentialManager = CredentialManager.create(activity)
        credentialManager.getCredentialAsync(
            activity,
            request,
            CancellationSignal(),
            ContextCompat.getMainExecutor(activity),
            object : CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
                override fun onResult(result: GetCredentialResponse) {
                    val credential = result.credential
                    Log.d(TAG, "Credential Manager returned type=${credential.type}")
                    if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        callback(Result.failure(IllegalStateException(activity.getString(br.com.helpdev.velocimetroalerta.R.string.google_sign_in_failed))))
                        return
                    }
                    val idToken = try {
                        GoogleIdTokenCredential.createFrom(credential.data).idToken
                    } catch (error: GoogleIdTokenParsingException) {
                        callback(Result.failure(error))
                        return
                    }
                    auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                        .addOnSuccessListener { resultAuth ->
                            val user = resultAuth.user
                            if (user == null) {
                                Log.e(TAG, "Firebase Google sign-in succeeded without a user")
                                callback(Result.failure(IllegalStateException(activity.getString(br.com.helpdev.velocimetroalerta.R.string.google_sign_in_failed))))
                            } else {
                                Log.i(TAG, "Firebase Google sign-in succeeded")
                                callback(Result.success(AccountSession(user.uid, user.displayName, user.email)))
                            }
                        }
                        .addOnFailureListener { error ->
                            val reason = (error as? FirebaseAuthException)?.errorCode ?: error.javaClass.simpleName
                            Log.e(TAG, "Firebase Google sign-in failed: $reason")
                            callback(Result.failure(error))
                        }
                }

                override fun onError(error: GetCredentialException) {
                    Log.e(TAG, "Google credential request failed: ${error.type}")
                    callback(Result.failure(error))
                }
            }
        )
    }

    fun signOut(activity: Activity) {
        FirebaseServices.auth(activity)?.signOut()
        CredentialManager.create(activity).clearCredentialStateAsync(
            ClearCredentialStateRequest(),
            CancellationSignal(),
            ContextCompat.getMainExecutor(activity),
            object : CredentialManagerCallback<Void?, androidx.credentials.exceptions.ClearCredentialException> {
                override fun onResult(result: Void?) = Unit
                override fun onError(error: androidx.credentials.exceptions.ClearCredentialException) = Unit
            }
        )
    }
}
