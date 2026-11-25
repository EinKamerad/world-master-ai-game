package com.example.data.preferences

import java.net.URI

enum class AiProviderType(
    val title: String,
    val shortBadge: String,
    val subtitle: String,
    val supportsNativeGoogleSearch: Boolean
) {
    FIREBASE_AI_LOGIC(
        title = "Встроенный Gemini (Firebase AI Logic)",
        shortBadge = "Firebase ИИ",
        subtitle = "Бесплатный облачный доступ без ввода ключей (защита App Check)",
        supportsNativeGoogleSearch = true
    ),
    ANTIGRAVITY_PROXY(
        title = "Антигравити API (Antigravity Proxy)",
        shortBadge = "Антигравити",
        subtitle = "Перенаправляет запросы через шлюз Antigravity / Cloud Code Assist, расходуя его лимиты",
        supportsNativeGoogleSearch = false
    ),
    GOOGLE_OAUTH_ACCOUNT(
        title = "Аккаунт Google / Gemini (OAuth 2.0)",
        shortBadge = "Google Аккаунт",
        subtitle = "Прямые запросы через ваш Google-аккаунт для расходования персональных квот",
        supportsNativeGoogleSearch = true
    ),
    GEMINI_API_KEY(
        title = "Свои ключи Google AI Studio (Gemini API)",
        shortBadge = "Ключ Gemini",
        subtitle = "Поддержка пула из нескольких ключей AIza... с автопереключением при лимите 429",
        supportsNativeGoogleSearch = true
    ),
    OPENROUTER(
        title = "OpenRouter (Бесплатные :free и 300+ моделей)",
        shortBadge = "OpenRouter",
        subtitle = "Доступ к десяткам бесплатных (:free) и платных моделей DeepSeek, Qwen, Claude, Llama",
        supportsNativeGoogleSearch = false
    ),
    OPENAI_COMPATIBLE(
        title = "Groq / DeepSeek / Mistral / OpenAI",
        shortBadge = "OpenAI-совм.",
        subtitle = "Универсальное подключение к быстрым облачным API с бесплатными и платными тарифами",
        supportsNativeGoogleSearch = false
    ),
    ANTHROPIC_CLAUDE(
        title = "Anthropic Claude API",
        shortBadge = "Claude API",
        subtitle = "Прямой протокол Anthropic Messages API со своими ключами sk-ant-...",
        supportsNativeGoogleSearch = false
    ),
    LOCAL_SERVER(
        title = "Локальный ИИ на ПК (Ollama / LM Studio / Kobold)",
        shortBadge = "Локальный ИИ",
        subtitle = "Полностью без лимитов и цензуры через домашний Wi-Fi или ПК",
        supportsNativeGoogleSearch = false
    )
}

data class EndpointPreset(
    val name: String,
    val baseUrl: String,
    val defaultModel: String,
    val hint: String
)

val AntigravityPresets = listOf(
    EndpointPreset(
        name = "Эмулятор → ПК (:3000)",
        baseUrl = "http://10.0.2.2:3000/v1",
        defaultModel = "gemini-2.5-flash",
        hint = "Если antigravity-proxy запущен на этом же компьютере"
    ),
    EndpointPreset(
        name = "Локальный / ADB Reverse (:3000)",
        baseUrl = "http://127.0.0.1:3000/v1",
        defaultModel = "gemini-2.5-flash",
        hint = "Если прокси поднят в Termux или проброшен через adb reverse"
    ),
    EndpointPreset(
        name = "Порт 8080 (CLIProxyAPI)",
        baseUrl = "http://10.0.2.2:8080/v1",
        defaultModel = "gemini-2.5-pro",
        hint = "Альтернативный порт шлюзов Antigravity / CLI-to-API"
    )
)

val OpenAiCompatiblePresets = listOf(
    EndpointPreset(
        name = "Groq (Быстрый Free-тариф)",
        baseUrl = "https://api.groq.com/openai/v1",
        defaultModel = "llama-3.3-70b-versatile",
        hint = "Бесплатный лимит запросов и мгновенная генерация"
    ),
    EndpointPreset(
        name = "DeepSeek API",
        baseUrl = "https://api.deepseek.com/v1",
        defaultModel = "deepseek-chat",
        hint = "Отличный русский язык и ролевой отыгрыш (V3 / R1)"
    ),
    EndpointPreset(
        name = "Mistral AI (La Plateforme)",
        baseUrl = "https://api.mistral.ai/v1",
        defaultModel = "mistral-small-latest",
        hint = "Есть бесплатный тариф Experiment"
    ),
    EndpointPreset(
        name = "OpenAI",
        baseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o-mini",
        hint = "Официальный API OpenAI"
    ),
    EndpointPreset(
        name = "Together AI",
        baseUrl = "https://api.together.xyz/v1",
        defaultModel = "meta-llama/Llama-3.3-70B-Instruct-Turbo",
        hint = "Облачный хостинг открытых моделей"
    )
)

val LocalServerPresets = listOf(
    EndpointPreset(
        name = "Ollama (Эмулятор → ПК)",
        baseUrl = "http://10.0.2.2:11434/v1",
        defaultModel = "llama3.2",
        hint = "Стандартный порт Ollama на компьютере"
    ),
    EndpointPreset(
        name = "LM Studio (Эмулятор → ПК)",
        baseUrl = "http://10.0.2.2:1234/v1",
        defaultModel = "local-model",
        hint = "Включите Local Server в LM Studio на порту 1234"
    ),
    EndpointPreset(
        name = "KoboldCPP (:5001)",
        baseUrl = "http://10.0.2.2:5001/v1",
        defaultModel = "koboldcpp",
        hint = "Популярный локальный сервер для ролевых GGUF-моделей"
    ),
    EndpointPreset(
        name = "llama.cpp (:8080)",
        baseUrl = "http://10.0.2.2:8080/v1",
        defaultModel = "local",
        hint = "Сервер llama-server"
    )
)

data class AiCredentialsSnapshot(
    val antigravityToken: String = "",
    val googleOAuthAccessToken: String = "",
    val googleOAuthEmail: String = "",
    val googleOAuthSavedAtMs: Long = 0L,
    val geminiApiKeysRaw: String = "",
    val openRouterApiKeysRaw: String = "",
    val openAiCompatibleApiKeysRaw: String = "",
    val anthropicApiKeysRaw: String = "",
    val localServerApiKey: String = ""
) {
    fun geminiKeysPool(): List<String> = parseApiKeyPool(geminiApiKeysRaw)
    fun openRouterKeysPool(): List<String> = parseApiKeyPool(openRouterApiKeysRaw)
    fun openAiCompatibleKeysPool(): List<String> = parseApiKeyPool(openAiCompatibleApiKeysRaw)
    fun anthropicKeysPool(): List<String> = parseApiKeyPool(anthropicApiKeysRaw)

    fun isGoogleOAuthTokenLikelyFresh(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (googleOAuthAccessToken.isBlank()) return false
        if (googleOAuthSavedAtMs <= 0L) return true
        // Access tokens last ~3600s; consider fresh within 50 minutes
        return (nowMs - googleOAuthSavedAtMs) < 50L * 60L * 1000L
    }
}

fun parseApiKeyPool(raw: String): List<String> {
    if (raw.isBlank()) return emptyList()
    return raw.split("\n", "\r", ",", ";")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}

fun maskSecretPreview(secret: String): String {
    val clean = secret.trim()
    if (clean.isEmpty()) return "не задан"
    if (clean.length <= 8) return "••••" + clean.takeLast(2)
    return "${clean.take(4)}••••${clean.takeLast(4)}"
}

object AiEndpointValidator {
    private val cloudHttpsOnlyHosts = setOf(
        "generativelanguage.googleapis.com",
        "openrouter.ai",
        "api.openai.com",
        "api.anthropic.com",
        "api.groq.com",
        "api.deepseek.com",
        "api.mistral.ai",
        "api.together.xyz"
    )

    fun normalizeAndValidateBaseUrl(rawUrl: String): Result<String> {
        val trimmed = rawUrl.trim().trimEnd('/')
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Укажите URL сервера API."))
        }
        val uri = runCatching { URI(trimmed) }.getOrNull()
            ?: return Result.failure(IllegalArgumentException("Некорректный формат URL: $trimmed"))

        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host?.lowercase().orEmpty()
        if (scheme != "https" && scheme != "http") {
            return Result.failure(IllegalArgumentException("Разрешены только протоколы https:// или http://"))
        }
        if (host.isBlank()) {
            return Result.failure(IllegalArgumentException("В URL отсутствует адрес хоста."))
        }
        if (scheme == "http" && host in cloudHttpsOnlyHosts) {
            return Result.failure(
                IllegalArgumentException("Облачный провайдер $host требует защищённого соединения https://")
            )
        }
        if (host == "169.254.169.254" || host == "metadata.google.internal") {
            return Result.failure(IllegalArgumentException("Недопустимый системный адрес хоста."))
        }
        return Result.success(trimmed)
    }
}
