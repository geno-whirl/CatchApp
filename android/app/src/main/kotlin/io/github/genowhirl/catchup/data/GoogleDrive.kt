package io.github.genowhirl.catchup.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import io.github.genowhirl.catchup.core.sync.RemoteException
import kotlinx.coroutines.tasks.await

/** Google Drive access with the `drive.file` scope: CatchUp only sees files it created. */
object GoogleDrive {
    private const val SCOPE = "https://www.googleapis.com/auth/drive.file"

    private fun request() = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    sealed interface Result {
        data class Token(val value: String) : Result
        /** The user has to approve access: launch this. */
        data class NeedsConsent(val intent: PendingIntent) : Result
    }

    suspend fun authorize(context: Context): Result {
        val result: AuthorizationResult = Identity.getAuthorizationClient(context).authorize(request()).await()
        val pending = result.pendingIntent
        return if (result.hasResolution() && pending != null) Result.NeedsConsent(pending)
        else Result.Token(result.accessToken ?: throw RemoteException("Google didn't return an access token.", RemoteException.Kind.AUTH))
    }

    fun tokenFromConsent(context: Context, data: Intent?): String? =
        runCatching { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data).accessToken }.getOrNull()

    /** For background sync: never shows UI, asks to reconnect instead. */
    suspend fun token(context: Context): String = try {
        when (val r = authorize(context)) {
            is Result.Token -> r.value
            is Result.NeedsConsent -> throw RemoteException("Reconnect Google Drive in Settings.", RemoteException.Kind.AUTH)
        }
    } catch (e: RemoteException) {
        throw e
    } catch (e: Exception) {
        throw RemoteException("Couldn't sign in to Google: ${e.message}", RemoteException.Kind.AUTH, e)
    }
}
