package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "rpg_master_settings"
)

enum class AppThemeMode(val label: String) {
    DARK("Тёмная (Обсидиан)"),
    LIGHT("Светлая (Пергамент)"),
    SYSTEM("Системная")
}

data class AppSettings(
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val aiProvider: AiProviderType = AiProviderType.FIREBASE_AI_LOGIC,
    val autoFallbackToFirebase: Boolean = true,
    val modelName: String = "gemini-3.8-flash", // Firebase AI Logic model
    val antigravityBaseUrl: String = "http://10.0.2.2:3000/v1",
    val antigravityModel: String = "gemini-2.5-flash",
    val googleOAuthModel: String = "gemini-2.5-flash",
    val googleCloudProjectId: String = "gen-lang-client-0798860006",
    val geminiKeyModel: String = "gemini-2.5-flash",
    val openRouterModel: String = "deepseek/deepseek-chat-v3-0324:free",
    val openAiCompatibleBaseUrl: String = "https://api.groq.com/openai/v1",
    val openAiCompatibleModel: String = "llama-3.3-70b-versatile",
    val anthropicModel: String = "claude-3-7-sonnet-latest",
    val localServerBaseUrl: String = "http://10.0.2.2:11434/v1",
    val localServerModel: String = "llama3.2",
    val webSearchEnabled: Boolean = false,
    val autoTrackWorldState: Boolean = true,
    val autoCompressMemory: Boolean = false,
    val memoryWarningPercent: Int = 78,
    val defaultMaxTokens: Int = 8000,
    val activeCampaignId: Long = 0L
) {
    fun currentEffectiveModel(): String = when (aiProvider) {
        AiProviderType.FIREBASE_AI_LOGIC -> modelName
        AiProviderType.ANTIGRAVITY_PROXY -> antigravityModel
        AiProviderType.GOOGLE_OAUTH_ACCOUNT -> googleOAuthModel
        AiProviderType.GEMINI_API_KEY -> geminiKeyModel
        AiProviderType.OPENROUTER -> openRouterModel
        AiProviderType.OPENAI_COMPATIBLE -> openAiCompatibleModel
        AiProviderType.ANTHROPIC_CLAUDE -> anthropicModel
        AiProviderType.LOCAL_SERVER -> localServerModel
    }
}

class SettingsDataStore(private val context: Context) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val AUTO_FALLBACK_FIREBASE = booleanPreferencesKey("auto_fallback_firebase")
        val MODEL_NAME = stringPreferencesKey("model_name")
        val ANTIGRAVITY_BASE_URL = stringPreferencesKey("antigravity_base_url")
        val ANTIGRAVITY_MODEL = stringPreferencesKey("antigravity_model")
        val GOOGLE_OAUTH_MODEL = stringPreferencesKey("google_oauth_model")
        val GOOGLE_CLOUD_PROJECT_ID = stringPreferencesKey("google_cloud_project_id")
        val GEMINI_KEY_MODEL = stringPreferencesKey("gemini_key_model")
        val OPENROUTER_MODEL = stringPreferencesKey("openrouter_model")
        val OPENAI_COMPAT_BASE_URL = stringPreferencesKey("openai_compat_base_url")
        val OPENAI_COMPAT_MODEL = stringPreferencesKey("openai_compat_model")
        val ANTHROPIC_MODEL = stringPreferencesKey("anthropic_model")
        val LOCAL_SERVER_BASE_URL = stringPreferencesKey("local_server_base_url")
        val LOCAL_SERVER_MODEL = stringPreferencesKey("local_server_model")
        val WEB_SEARCH = booleanPreferencesKey("web_search_enabled")
        val AUTO_TRACK_STATE = booleanPreferencesKey("auto_track_state")
        val AUTO_COMPRESS_MEMORY = booleanPreferencesKey("auto_compress_memory")
        val MEMORY_WARNING_PERCENT = intPreferencesKey("memory_warning_percent")
        val DEFAULT_MAX_TOKENS = intPreferencesKey("default_max_tokens")
        val ACTIVE_CAMPAIGN_ID = longPreferencesKey("active_campaign_id")
    }

    val settingsFlow: Flow<AppSettings> = context.appDataStore.data.map { prefs ->
        val rawTheme = prefs[Keys.THEME_MODE] ?: AppThemeMode.DARK.name
        val theme = runCatching { AppThemeMode.valueOf(rawTheme) }.getOrDefault(AppThemeMode.DARK)

        val rawProvider = prefs[Keys.AI_PROVIDER] ?: AiProviderType.FIREBASE_AI_LOGIC.name
        val provider = runCatching { AiProviderType.valueOf(rawProvider) }
            .getOrDefault(AiProviderType.FIREBASE_AI_LOGIC)

        AppSettings(
            themeMode = theme,
            aiProvider = provider,
            autoFallbackToFirebase = prefs[Keys.AUTO_FALLBACK_FIREBASE] ?: true,
            modelName = prefs[Keys.MODEL_NAME] ?: "gemini-3.8-flash",
            antigravityBaseUrl = prefs[Keys.ANTIGRAVITY_BASE_URL] ?: "http://10.0.2.2:3000/v1",
            antigravityModel = prefs[Keys.ANTIGRAVITY_MODEL] ?: "gemini-2.5-flash",
            googleOAuthModel = prefs[Keys.GOOGLE_OAUTH_MODEL] ?: "gemini-2.5-flash",
            googleCloudProjectId = prefs[Keys.GOOGLE_CLOUD_PROJECT_ID] ?: "gen-lang-client-0798860006",
            geminiKeyModel = prefs[Keys.GEMINI_KEY_MODEL] ?: "gemini-2.5-flash",
            openRouterModel = prefs[Keys.OPENROUTER_MODEL] ?: "deepseek/deepseek-chat-v3-0324:free",
            openAiCompatibleBaseUrl = prefs[Keys.OPENAI_COMPAT_BASE_URL] ?: "https://api.groq.com/openai/v1",
            openAiCompatibleModel = prefs[Keys.OPENAI_COMPAT_MODEL] ?: "llama-3.3-70b-versatile",
            anthropicModel = prefs[Keys.ANTHROPIC_MODEL] ?: "claude-3-7-sonnet-latest",
            localServerBaseUrl = prefs[Keys.LOCAL_SERVER_BASE_URL] ?: "http://10.0.2.2:11434/v1",
            localServerModel = prefs[Keys.LOCAL_SERVER_MODEL] ?: "llama3.2",
            webSearchEnabled = prefs[Keys.WEB_SEARCH] ?: false,
            autoTrackWorldState = prefs[Keys.AUTO_TRACK_STATE] ?: true,
            autoCompressMemory = prefs[Keys.AUTO_COMPRESS_MEMORY] ?: false,
            memoryWarningPercent = (prefs[Keys.MEMORY_WARNING_PERCENT] ?: 78).coerceIn(50, 95),
            defaultMaxTokens = (prefs[Keys.DEFAULT_MAX_TOKENS] ?: 8000).coerceIn(2000, 32000),
            activeCampaignId = prefs[Keys.ACTIVE_CAMPAIGN_ID] ?: 0L
        )
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.appDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setAiProvider(provider: AiProviderType) {
        context.appDataStore.edit { it[Keys.AI_PROVIDER] = provider.name }
    }

    suspend fun setAutoFallbackToFirebase(enabled: Boolean) {
        context.appDataStore.edit { it[Keys.AUTO_FALLBACK_FIREBASE] = enabled }
    }

    suspend fun setModelName(model: String) {
        context.appDataStore.edit { it[Keys.MODEL_NAME] = model.trim() }
    }

    suspend fun updateAntigravityConfig(baseUrl: String, model: String) {
        context.appDataStore.edit {
            it[Keys.ANTIGRAVITY_BASE_URL] = baseUrl.trim()
            it[Keys.ANTIGRAVITY_MODEL] = model.trim().ifBlank { "gemini-2.5-flash" }
        }
    }

    suspend fun updateGoogleOAuthConfig(model: String, projectId: String) {
        context.appDataStore.edit {
            it[Keys.GOOGLE_OAUTH_MODEL] = model.trim().ifBlank { "gemini-2.5-flash" }
            it[Keys.GOOGLE_CLOUD_PROJECT_ID] = projectId.trim()
        }
    }

    suspend fun setGeminiKeyModel(model: String) {
        context.appDataStore.edit {
            it[Keys.GEMINI_KEY_MODEL] = model.trim().ifBlank { "gemini-2.5-flash" }
        }
    }

    suspend fun setOpenRouterModel(model: String) {
        context.appDataStore.edit {
            it[Keys.OPENROUTER_MODEL] = model.trim().ifBlank { "deepseek/deepseek-chat-v3-0324:free" }
        }
    }

    suspend fun updateOpenAiCompatibleConfig(baseUrl: String, model: String) {
        context.appDataStore.edit {
            it[Keys.OPENAI_COMPAT_BASE_URL] = baseUrl.trim()
            it[Keys.OPENAI_COMPAT_MODEL] = model.trim().ifBlank { "gpt-4o-mini" }
        }
    }

    suspend fun setAnthropicModel(model: String) {
        context.appDataStore.edit {
            it[Keys.ANTHROPIC_MODEL] = model.trim().ifBlank { "claude-3-7-sonnet-latest" }
        }
    }

    suspend fun updateLocalServerConfig(baseUrl: String, model: String) {
        context.appDataStore.edit {
            it[Keys.LOCAL_SERVER_BASE_URL] = baseUrl.trim()
            it[Keys.LOCAL_SERVER_MODEL] = model.trim().ifBlank { "llama3.2" }
        }
    }

    suspend fun setWebSearchEnabled(enabled: Boolean) {
        context.appDataStore.edit { it[Keys.WEB_SEARCH] = enabled }
    }

    suspend fun setAutoTrackWorldState(enabled: Boolean) {
        context.appDataStore.edit { it[Keys.AUTO_TRACK_STATE] = enabled }
    }

    suspend fun setAutoCompressMemory(enabled: Boolean) {
        context.appDataStore.edit { it[Keys.AUTO_COMPRESS_MEMORY] = enabled }
    }

    suspend fun setMemoryWarningPercent(percent: Int) {
        context.appDataStore.edit { it[Keys.MEMORY_WARNING_PERCENT] = percent.coerceIn(50, 95) }
    }

    suspend fun setDefaultMaxTokens(tokens: Int) {
        context.appDataStore.edit { it[Keys.DEFAULT_MAX_TOKENS] = tokens.coerceIn(2000, 32000) }
    }

    suspend fun setActiveCampaignId(campaignId: Long) {
        context.appDataStore.edit { it[Keys.ACTIVE_CAMPAIGN_ID] = campaignId }
    }
}
