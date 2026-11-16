package com.example.geckobrowser.sync

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import net.openid.appauth.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Native OAuth only: AppAuth launches the external browser/Custom Tab, never an embedded WebView. */
class OAuthSyncManager(private val context: Context) {
    private val service = AuthorizationService(context)
    private val prefs by lazy {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(context, "sync_tokens", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }
    private val http = OkHttpClient()

    fun authorizeGoogle(activity: Activity, clientId: String, redirectUri: String, onResult: (AuthState?, String?) -> Unit) {
        val config = AuthorizationServiceConfiguration(Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"), Uri.parse("https://oauth2.googleapis.com/token"))
        val request = AuthorizationRequest.Builder(config, clientId, ResponseTypeValues.CODE, Uri.parse(redirectUri))
            .setScopes("openid", "email", "https://www.googleapis.com/auth/drive.appdata", "offline_access")
            .build()
        // AppAuth opens the external browser/Custom Tab; never embed the provider login page.
        val intent = service.getAuthorizationRequestIntent(request)
        activity.startActivityForResult(intent, REQUEST_AUTH)
        pendingProvider = "google"; pendingRedirect = redirectUri
        callback = onResult
    }

    fun authorizeMicrosoft(activity: Activity, clientId: String, redirectUri: String, tenant: String = "consumers", onResult: (AuthState?, String?) -> Unit) {
        val discovery = Uri.parse("https://login.microsoftonline.com/$tenant/v2.0/.well-known/openid-configuration")
        val config = AuthorizationServiceConfiguration(Uri.parse("https://login.microsoftonline.com/$tenant/oauth2/v2.0/authorize"), Uri.parse("https://login.microsoftonline.com/$tenant/oauth2/v2.0/token"))
        val request = AuthorizationRequest.Builder(config, clientId, ResponseTypeValues.CODE, Uri.parse(redirectUri))
            .setScopes("openid", "profile", "offline_access", "Files.ReadWrite.AppFolder")
            .build()
        pendingProvider = "microsoft"; pendingRedirect = redirectUri; callback = onResult
        activity.startActivityForResult(service.getAuthorizationRequestIntent(request), REQUEST_AUTH)
    }

    fun handleAuthorizationResponse(intent: android.content.Intent, onComplete: (AuthState?, String?) -> Unit = { _, _ -> }) {
        val response = AuthorizationResponse.fromIntent(intent)
        val error = AuthorizationException.fromIntent(intent)
        if (response == null) { onComplete(null, error?.errorDescription ?: "Authorization cancelled"); return }
        val state = AuthState(response.request.configuration)
        state.update(response, error)
        val tokenRequest = response.createTokenExchangeRequest()
        service.performTokenRequest(tokenRequest) { tokenResponse, tokenError ->
            state.update(tokenResponse, tokenError)
            if (tokenError != null) onComplete(null, tokenError.errorDescription)
            else { prefs.edit().putString("auth_state_${pendingProvider}", state.jsonSerializeString()).apply(); onComplete(state, null) }
        }
    }

    fun syncGoogleAppData(accessToken: String, json: String): String = putGoogleFile(accessToken, json)
    private fun putGoogleFile(token: String, json: String): String {
        val metadata = """{"name":"browser-sync.json","mimeType":"application/json","parents":["appDataFolder"]}"""
        val boundary = "gecko-sync-boundary"
        val body = """--$boundary
Content-Type: application/json; charset=UTF-8

$metadata
--$boundary
Content-Type: application/json

$json
--$boundary--"""
        val req = Request.Builder().url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name")
            .header("Authorization", "Bearer $token").post(body.toRequestBody("multipart/related; boundary=$boundary".toMediaType())).build()
        http.newCall(req).execute().use { r -> check(r.isSuccessful) { "Drive sync failed: ${r.code} ${r.body?.string()}" }; return r.body?.string().orEmpty() }
    }

    fun syncOneDriveAppFolder(accessToken: String, json: String): String {
        val req = Request.Builder().url("https://graph.microsoft.com/v1.0/me/drive/special/approot:/browser-sync.json:/content")
            .header("Authorization", "Bearer $accessToken").put(json.toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { r -> check(r.isSuccessful) { "OneDrive sync failed: ${r.code} ${r.body?.string()}" }; return r.body?.string().orEmpty() }
    }

    companion object {
        const val REQUEST_AUTH = 7102
        private var pendingProvider: String = "google"
        private var pendingRedirect: String = ""
        private var callback: ((AuthState?, String?) -> Unit)? = null
    }
}
