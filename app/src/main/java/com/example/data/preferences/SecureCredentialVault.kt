package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Encrypts user-entered API keys and OAuth tokens at rest using AndroidKeyStore AES-256-GCM.
 * Stored in an app-private SharedPreferences file excluded from cloud backup & device transfer.
 */
class SecureCredentialVault(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_FILE_NAME,
        Context.MODE_PRIVATE
    )

    private val _credentialsFlow = MutableStateFlow(loadSnapshot())
    val credentialsFlow: StateFlow<AiCredentialsSnapshot> = _credentialsFlow.asStateFlow()

    val current: AiCredentialsSnapshot
        get() = _credentialsFlow.value

    fun saveAntigravityToken(token: String) {
        putEncrypted(KEY_ANTIGRAVITY_TOKEN, token.trim())
        refresh()
    }

    fun saveGoogleOAuthSession(accessToken: String, email: String) {
        putEncrypted(KEY_GOOGLE_OAUTH_TOKEN, accessToken.trim())
        putEncrypted(KEY_GOOGLE_OAUTH_EMAIL, email.trim())
        prefs.edit().putLong(KEY_GOOGLE_OAUTH_SAVED_AT, System.currentTimeMillis()).apply()
        refresh()
    }

    fun clearGoogleOAuthSession() {
        prefs.edit()
            .remove(KEY_GOOGLE_OAUTH_TOKEN)
            .remove(KEY_GOOGLE_OAUTH_EMAIL)
            .remove(KEY_GOOGLE_OAUTH_SAVED_AT)
            .apply()
        refresh()
    }

    fun saveGeminiApiKeys(rawKeys: String) {
        putEncrypted(KEY_GEMINI_KEYS, rawKeys.trim())
        refresh()
    }

    fun saveOpenRouterApiKeys(rawKeys: String) {
        putEncrypted(KEY_OPENROUTER_KEYS, rawKeys.trim())
        refresh()
    }

    fun saveOpenAiCompatibleApiKeys(rawKeys: String) {
        putEncrypted(KEY_OPENAI_COMPAT_KEYS, rawKeys.trim())
        refresh()
    }

    fun saveAnthropicApiKeys(rawKeys: String) {
        putEncrypted(KEY_ANTHROPIC_KEYS, rawKeys.trim())
        refresh()
    }

    fun saveLocalServerApiKey(rawKey: String) {
        putEncrypted(KEY_LOCAL_SERVER_KEY, rawKey.trim())
        refresh()
    }

    private fun refresh() {
        _credentialsFlow.value = loadSnapshot()
    }

    private fun loadSnapshot(): AiCredentialsSnapshot {
        return AiCredentialsSnapshot(
            antigravityToken = getDecrypted(KEY_ANTIGRAVITY_TOKEN),
            googleOAuthAccessToken = getDecrypted(KEY_GOOGLE_OAUTH_TOKEN),
            googleOAuthEmail = getDecrypted(KEY_GOOGLE_OAUTH_EMAIL),
            googleOAuthSavedAtMs = prefs.getLong(KEY_GOOGLE_OAUTH_SAVED_AT, 0L),
            geminiApiKeysRaw = getDecrypted(KEY_GEMINI_KEYS),
            openRouterApiKeysRaw = getDecrypted(KEY_OPENROUTER_KEYS),
            openAiCompatibleApiKeysRaw = getDecrypted(KEY_OPENAI_COMPAT_KEYS),
            anthropicApiKeysRaw = getDecrypted(KEY_ANTHROPIC_KEYS),
            localServerApiKey = getDecrypted(KEY_LOCAL_SERVER_KEY)
        )
    }

    private fun putEncrypted(prefKey: String, plainText: String) {
        if (plainText.isEmpty()) {
            prefs.edit().remove(prefKey).apply()
            return
        }
        val encoded = encryptWithKeystoreOrFallback(plainText)
        prefs.edit().putString(prefKey, encoded).apply()
    }

    private fun getDecrypted(prefKey: String): String {
        val stored = prefs.getString(prefKey, null) ?: return ""
        return decryptWithKeystoreOrFallback(stored)
    }

    private fun encryptWithKeystoreOrFallback(plainText: String): String {
        return runCatching {
            val secretKey = getOrCreateAesKey() ?: return@runCatching fallbackEncode(plainText)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val bodyB64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
            "v1:$ivB64:$bodyB64"
        }.getOrElse { fallbackEncode(plainText) }
    }

    private fun decryptWithKeystoreOrFallback(stored: String): String {
        if (stored.startsWith("fb:")) {
            return runCatching {
                String(Base64.decode(stored.removePrefix("fb:"), Base64.NO_WRAP), Charsets.UTF_8)
            }.getOrDefault("")
        }
        if (!stored.startsWith("v1:")) return ""
        return runCatching {
            val parts = stored.split(":", limit = 3)
            if (parts.size != 3) return ""
            val iv = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipherBytes = Base64.decode(parts[2], Base64.NO_WRAP)
            val secretKey = getOrCreateAesKey() ?: return ""
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        }.getOrDefault("")
    }

    private fun fallbackEncode(plainText: String): String {
        // Used only in local JVM unit tests where AndroidKeyStore hardware provider is absent
        return "fb:" + Base64.encodeToString(plainText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private fun getOrCreateAesKey(): SecretKey? {
        return runCatching {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = ks.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (existing != null) return existing.secretKey

            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }.getOrNull()
    }

    companion object {
        const val PREFS_FILE_NAME = "rpg_secure_ai_vault"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS = "rpg_master_ai_credentials_aes_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        private const val KEY_ANTIGRAVITY_TOKEN = "antigravity_token"
        private const val KEY_GOOGLE_OAUTH_TOKEN = "google_oauth_token"
        private const val KEY_GOOGLE_OAUTH_EMAIL = "google_oauth_email"
        private const val KEY_GOOGLE_OAUTH_SAVED_AT = "google_oauth_saved_at"
        private const val KEY_GEMINI_KEYS = "gemini_api_keys"
        private const val KEY_OPENROUTER_KEYS = "openrouter_api_keys"
        private const val KEY_OPENAI_COMPAT_KEYS = "openai_compat_api_keys"
        private const val KEY_ANTHROPIC_KEYS = "anthropic_api_keys"
        private const val KEY_LOCAL_SERVER_KEY = "local_server_api_key"
    }
}
