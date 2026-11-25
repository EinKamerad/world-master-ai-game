package com.example.ai

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

sealed interface GoogleAuthStepResult {
    data class Authorized(val accessToken: String, val email: String) : GoogleAuthStepResult
    data class NeedsConsent(val intentSender: IntentSender) : GoogleAuthStepResult
    data class Failed(val message: String) : GoogleAuthStepResult
}

/**
 * Handles Google Account OAuth 2.0 authorization for Gemini API (Generative Language API)
 * using Play Services Identity AuthorizationClient + minimal least-privilege scopes.
 */
class GoogleOAuthHelper(
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    private val geminiScopes = listOf(
        Scope(SCOPE_GENERATIVE_LANGUAGE_RETRIEVER),
        Scope(SCOPE_USERINFO_EMAIL)
    )

    private fun buildAuthRequest(): AuthorizationRequest {
        return AuthorizationRequest.builder()
            .setRequestedScopes(geminiScopes)
            .build()
    }

    suspend fun beginAuthorization(context: Context): GoogleAuthStepResult {
        return try {
            val result = awaitAuthorizeTask(context, buildAuthRequest())
            if (result.hasResolution()) {
                val sender = result.pendingIntent?.intentSender
                if (sender != null) {
                    GoogleAuthStepResult.NeedsConsent(sender)
                } else {
                    GoogleAuthStepResult.Failed("Не удалось открыть окно подтверждения Google OAuth.")
                }
            } else {
                val token = result.accessToken.orEmpty()
                if (token.isBlank()) {
                    GoogleAuthStepResult.Failed("Google не вернул токен доступа.")
                } else {
                    val email = fetchUserEmail(token)
                    GoogleAuthStepResult.Authorized(accessToken = token, email = email)
                }
            }
        } catch (e: ApiException) {
            GoogleAuthStepResult.Failed(mapPlayServicesAuthError(e))
        } catch (e: Exception) {
            GoogleAuthStepResult.Failed(
                e.localizedMessage ?: "Ошибка авторизации через аккаунт Google."
            )
        }
    }

    suspend fun completeAuthorizationFromIntent(
        context: Context,
        data: Intent?
    ): GoogleAuthStepResult {
        if (data == null) {
            return GoogleAuthStepResult.Failed("Вход через Google отменён.")
        }
        return try {
            val result = Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(data)
            val token = result.accessToken.orEmpty()
            if (token.isBlank()) {
                GoogleAuthStepResult.Failed("Токен доступа не был получен.")
            } else {
                val email = fetchUserEmail(token)
                GoogleAuthStepResult.Authorized(accessToken = token, email = email)
            }
        } catch (e: ApiException) {
            GoogleAuthStepResult.Failed(mapPlayServicesAuthError(e))
        } catch (e: Exception) {
            GoogleAuthStepResult.Failed(e.localizedMessage ?: "Не удалось завершить вход через Google.")
        }
    }

    /**
     * Silently refreshes the short-lived 1-hour OAuth access token if user already granted consent.
     */
    suspend fun trySilentRefreshToken(context: Context): Pair<String, String>? {
        return try {
            val result = awaitAuthorizeTask(context.applicationContext, buildAuthRequest())
            if (!result.hasResolution() && !result.accessToken.isNullOrBlank()) {
                val token = result.accessToken!!
                val email = fetchUserEmail(token)
                token to email
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetchUserEmail(accessToken: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://www.googleapis.com/oauth2/v3/userinfo")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching ""
                val body = resp.body?.string().orEmpty()
                JSONObject(body).optString("email", "")
            }
        }.getOrDefault("")
    }

    private suspend fun awaitAuthorizeTask(
        context: Context,
        request: AuthorizationRequest
    ): AuthorizationResult = suspendCancellableCoroutine { cont ->
        Identity.getAuthorizationClient(context)
            .authorize(request)
            .addOnSuccessListener { res ->
                if (cont.isActive) cont.resume(res)
            }
            .addOnFailureListener { err ->
                if (cont.isActive) cont.resumeWith(Result.failure(err))
            }
    }

    private fun mapPlayServicesAuthError(e: ApiException): String {
        return when (e.statusCode) {
            10 -> "Для прямого входа на этом устройстве ещё не зарегистрирован Android OAuth SHA-1 сертификат в Google Cloud, либо можно вставить OAuth Bearer-токен вручную ниже."
            16, 12501 -> "Авторизация через аккаунт Google была отменена пользователем."
            7 -> "Нет подключения к сети для связи с сервисами Google."
            else -> "Ошибка Google OAuth (код ${e.statusCode}): ${e.localizedMessage ?: "проверьте аккаунт Google на устройстве"}."
        }
    }

    companion object {
        const val SCOPE_GENERATIVE_LANGUAGE_RETRIEVER =
            "https://www.googleapis.com/auth/generative-language.retriever"
        const val SCOPE_USERINFO_EMAIL =
            "https://www.googleapis.com/auth/userinfo.email"
    }
}
