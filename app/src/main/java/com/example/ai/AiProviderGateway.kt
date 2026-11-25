package com.example.ai

import android.content.Context
import android.util.Log
import com.example.data.local.GroundingSource
import com.example.data.preferences.AiCredentialsSnapshot
import com.example.data.preferences.AiEndpointValidator
import com.example.data.preferences.AiProviderType
import com.example.data.preferences.AppSettings
import com.example.data.preferences.SecureCredentialVault
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "AiProviderGateway"

data class GatewayChatMessage(
    val isUser: Boolean,
    val text: String
)

data class GatewayResponse(
    val text: String,
    val groundingSources: List<GroundingSource> = emptyList(),
    val providerUsed: AiProviderType,
    val fallbackNotice: String? = null
)

data class ConnectionProbeResult(
    val ok: Boolean,
    val latencyMs: Long,
    val providerTitle: String,
    val modelUsed: String,
    val message: String
)

class RateLimitOrQuotaException(message: String, val httpCode: Int = 429) : IOException(message)

/**
 * Unified AI execution router supporting:
 * 1. Firebase AI Logic (keyless via App Check)
 * 2. Antigravity API / Proxy (OpenAI-compatible SSE + model discovery)
 * 3. Google / Gemini Account OAuth 2.0 (Bearer token + auto silent refresh + optional GCP project header)
 * 4. Custom Gemini API Keys (with multi-key pool rotation on 429/403)
 * 5. OpenRouter (300+ & :free models with multi-key rotation)
 * 6. OpenAI-Compatible providers (Groq, DeepSeek, Mistral, OpenAI, Together)
 * 7. Anthropic Claude Messages API
 * 8. Local / LAN PC servers (Ollama, LM Studio, KoboldCPP, llama.cpp)
 */
class AiProviderGateway(
    private val appContext: Context? = null,
    private val credentialVault: SecureCredentialVault? = null,
    private val googleOAuthHelper: GoogleOAuthHelper = GoogleOAuthHelper()
) {
    // Deliberately configured WITHOUT body/header logging interceptors so API keys & OAuth tokens never leak to Logcat
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(75, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val keyRotationCursor = AtomicInteger(0)

    private fun currentCredentials(): AiCredentialsSnapshot =
        credentialVault?.current ?: AiCredentialsSnapshot()

    suspend fun generateChatOrText(
        settings: AppSettings,
        systemInstruction: String? = null,
        history: List<GatewayChatMessage> = emptyList(),
        userPrompt: String,
        useWebSearch: Boolean = false,
        expectJson: Boolean = false,
        firebaseSchema: Schema? = null,
        onStreamPartial: ((String) -> Unit)? = null
    ): GatewayResponse {
        val creds = currentCredentials()
        return try {
            executeOnProvider(
                provider = settings.aiProvider,
                settings = settings,
                creds = creds,
                systemInstruction = systemInstruction,
                history = history,
                userPrompt = userPrompt,
                useWebSearch = useWebSearch,
                expectJson = expectJson,
                firebaseSchema = firebaseSchema,
                onStreamPartial = onStreamPartial
            )
        } catch (e: CancellationException) {
            if (e !is TimeoutCancellationException) throw e
            maybeFallbackToFirebase(
                originalError = e,
                settings = settings,
                systemInstruction = systemInstruction,
                history = history,
                userPrompt = userPrompt,
                useWebSearch = useWebSearch,
                expectJson = expectJson,
                firebaseSchema = firebaseSchema,
                onStreamPartial = onStreamPartial
            )
        } catch (e: Exception) {
            maybeFallbackToFirebase(
                originalError = e,
                settings = settings,
                systemInstruction = systemInstruction,
                history = history,
                userPrompt = userPrompt,
                useWebSearch = useWebSearch,
                expectJson = expectJson,
                firebaseSchema = firebaseSchema,
                onStreamPartial = onStreamPartial
            )
        }
    }

    private suspend fun maybeFallbackToFirebase(
        originalError: Exception,
        settings: AppSettings,
        systemInstruction: String?,
        history: List<GatewayChatMessage>,
        userPrompt: String,
        useWebSearch: Boolean,
        expectJson: Boolean,
        firebaseSchema: Schema?,
        onStreamPartial: ((String) -> Unit)?
    ): GatewayResponse {
        val canFallback = settings.autoFallbackToFirebase &&
            settings.aiProvider != AiProviderType.FIREBASE_AI_LOGIC &&
            (originalError is RateLimitOrQuotaException || originalError is IOException || originalError is TimeoutCancellationException)

        if (!canFallback) throw originalError

        Log.w(TAG, "Primary provider ${settings.aiProvider} failed (${originalError.message}); falling back to Firebase AI Logic")
        val fbResult = executeFirebaseAiLogic(
            modelName = settings.modelName,
            systemInstruction = systemInstruction,
            history = history,
            userPrompt = userPrompt,
            useWebSearch = useWebSearch,
            expectJson = expectJson,
            firebaseSchema = firebaseSchema,
            onStreamPartial = onStreamPartial
        )
        return fbResult.copy(
            fallbackNotice = "Лимит или связь «${settings.aiProvider.shortBadge}» недоступны — ход выполнен через резервный Firebase Gemini"
        )
    }

    private suspend fun executeOnProvider(
        provider: AiProviderType,
        settings: AppSettings,
        creds: AiCredentialsSnapshot,
        systemInstruction: String?,
        history: List<GatewayChatMessage>,
        userPrompt: String,
        useWebSearch: Boolean,
        expectJson: Boolean,
        firebaseSchema: Schema?,
        onStreamPartial: ((String) -> Unit)?
    ): GatewayResponse {
        return when (provider) {
            AiProviderType.FIREBASE_AI_LOGIC -> executeFirebaseAiLogic(
                modelName = settings.modelName,
                systemInstruction = systemInstruction,
                history = history,
                userPrompt = userPrompt,
                useWebSearch = useWebSearch,
                expectJson = expectJson,
                firebaseSchema = firebaseSchema,
                onStreamPartial = onStreamPartial
            )

            AiProviderType.GOOGLE_OAUTH_ACCOUNT -> {
                val token = resolveFreshGoogleOAuthToken(creds)
                executeDirectGeminiRest(
                    providerType = AiProviderType.GOOGLE_OAUTH_ACCOUNT,
                    modelName = settings.googleOAuthModel,
                    authHeaderName = "Authorization",
                    authHeaderValue = "Bearer $token",
                    projectHeader = settings.googleCloudProjectId.trim().takeIf { it.isNotEmpty() },
                    systemInstruction = systemInstruction,
                    history = history,
                    userPrompt = userPrompt,
                    useWebSearch = useWebSearch,
                    expectJson = expectJson,
                    onStreamPartial = onStreamPartial
                )
            }

            AiProviderType.GEMINI_API_KEY -> {
                val pool = creds.geminiKeysPool()
                if (pool.isEmpty()) {
                    throw IllegalStateException("Добавьте хотя бы один API-ключ Gemini (AIza...) во вкладке «ИИ и Ключи».")
                }
                executeWithKeyPoolRotation(pool) { apiKey ->
                    executeDirectGeminiRest(
                        providerType = AiProviderType.GEMINI_API_KEY,
                        modelName = settings.geminiKeyModel,
                        authHeaderName = "x-goog-api-key",
                        authHeaderValue = apiKey,
                        projectHeader = null,
                        systemInstruction = systemInstruction,
                        history = history,
                        userPrompt = userPrompt,
                        useWebSearch = useWebSearch,
                        expectJson = expectJson,
                        onStreamPartial = onStreamPartial
                    )
                }
            }

            AiProviderType.ANTIGRAVITY_PROXY -> {
                val baseUrl = AiEndpointValidator.normalizeAndValidateBaseUrl(settings.antigravityBaseUrl)
                    .getOrThrow()
                val token = creds.antigravityToken.ifBlank { "antigravity" }
                executeAntigravityWithDualProtocol(
                    baseUrl = baseUrl,
                    modelName = settings.antigravityModel,
                    token = token,
                    systemInstruction = systemInstruction,
                    history = history,
                    userPrompt = userPrompt,
                    expectJson = expectJson,
                    onStreamPartial = onStreamPartial
                )
            }

            AiProviderType.OPENROUTER -> {
                val pool = creds.openRouterKeysPool()
                if (pool.isEmpty()) {
                    throw IllegalStateException("Добавьте API-ключ OpenRouter (sk-or-...) во вкладке «ИИ и Ключи».")
                }
                executeWithKeyPoolRotation(pool) { apiKey ->
                    executeOpenAiCompatible(
                        providerType = AiProviderType.OPENROUTER,
                        baseUrl = "https://openrouter.ai/api/v1",
                        modelName = settings.openRouterModel,
                        bearerToken = apiKey,
                        extraHeaders = mapOf(
                            "HTTP-Referer" to "https://aistudio.google.com",
                            "X-Title" to "Master Mirov RPG"
                        ),
                        systemInstruction = systemInstruction,
                        history = history,
                        userPrompt = userPrompt,
                        expectJson = expectJson,
                        onStreamPartial = onStreamPartial
                    )
                }
            }

            AiProviderType.OPENAI_COMPATIBLE -> {
                val baseUrl = AiEndpointValidator.normalizeAndValidateBaseUrl(settings.openAiCompatibleBaseUrl)
                    .getOrThrow()
                val pool = creds.openAiCompatibleKeysPool()
                if (pool.isEmpty()) {
                    throw IllegalStateException("Добавьте API-ключ для выбранного совместимого провайдера во вкладке «ИИ и Ключи».")
                }
                executeWithKeyPoolRotation(pool) { apiKey ->
                    executeOpenAiCompatible(
                        providerType = AiProviderType.OPENAI_COMPATIBLE,
                        baseUrl = baseUrl,
                        modelName = settings.openAiCompatibleModel,
                        bearerToken = apiKey,
                        extraHeaders = emptyMap(),
                        systemInstruction = systemInstruction,
                        history = history,
                        userPrompt = userPrompt,
                        expectJson = expectJson,
                        onStreamPartial = onStreamPartial
                    )
                }
            }

            AiProviderType.ANTHROPIC_CLAUDE -> {
                val pool = creds.anthropicKeysPool()
                if (pool.isEmpty()) {
                    throw IllegalStateException("Добавьте API-ключ Anthropic Claude (sk-ant-...) во вкладке «ИИ и Ключи».")
                }
                executeWithKeyPoolRotation(pool) { apiKey ->
                    executeAnthropicMessages(
                        modelName = settings.anthropicModel,
                        apiKey = apiKey,
                        systemInstruction = systemInstruction,
                        history = history,
                        userPrompt = userPrompt,
                        expectJson = expectJson,
                        onStreamPartial = onStreamPartial
                    )
                }
            }

            AiProviderType.LOCAL_SERVER -> {
                val baseUrl = AiEndpointValidator.normalizeAndValidateBaseUrl(settings.localServerBaseUrl)
                    .getOrThrow()
                executeOpenAiCompatible(
                    providerType = AiProviderType.LOCAL_SERVER,
                    baseUrl = baseUrl,
                    modelName = settings.localServerModel,
                    bearerToken = creds.localServerApiKey.ifBlank { "ollama" },
                    extraHeaders = emptyMap(),
                    systemInstruction = systemInstruction,
                    history = history,
                    userPrompt = userPrompt,
                    expectJson = expectJson,
                    onStreamPartial = onStreamPartial
                )
            }
        }
    }

    private suspend fun <T> executeWithKeyPoolRotation(
        keys: List<String>,
        block: suspend (String) -> T
    ): T {
        val count = keys.size
        val startIndex = (keyRotationCursor.getAndIncrement() and Int.MAX_VALUE) % count
        var lastError: Exception? = null
        for (attempt in 0 until count) {
            val key = keys[(startIndex + attempt) % count]
            try {
                return block(key)
            } catch (rateEx: RateLimitOrQuotaException) {
                lastError = rateEx
                Log.w(TAG, "Key rotation: key slot ${(startIndex + attempt) % count + 1}/$count hit quota (${rateEx.httpCode}), trying next key")
            }
        }
        throw lastError ?: RateLimitOrQuotaException("Исчерпаны лимиты на всех добавленных ключах ($count шт.).")
    }

    private suspend fun resolveFreshGoogleOAuthToken(creds: AiCredentialsSnapshot): String {
        if (creds.isGoogleOAuthTokenLikelyFresh()) {
            return creds.googleOAuthAccessToken
        }
        if (appContext != null) {
            val refreshed = googleOAuthHelper.trySilentRefreshToken(appContext)
            if (refreshed != null) {
                credentialVault?.saveGoogleOAuthSession(refreshed.first, refreshed.second)
                return refreshed.first
            }
        }
        if (creds.googleOAuthAccessToken.isNotBlank()) {
            return creds.googleOAuthAccessToken
        }
        throw IllegalStateException("Войдите в аккаунт Google или вставьте OAuth-токен во вкладке «ИИ и Ключи».")
    }

    // --- 1. Firebase AI Logic Implementation ---
    private suspend fun executeFirebaseAiLogic(
        modelName: String,
        systemInstruction: String?,
        history: List<GatewayChatMessage>,
        userPrompt: String,
        useWebSearch: Boolean,
        expectJson: Boolean,
        firebaseSchema: Schema?,
        onStreamPartial: ((String) -> Unit)?
    ): GatewayResponse {
        val sysContent = systemInstruction?.takeIf { it.isNotBlank() }?.let { content { text(it) } }
        val genConfig = if (expectJson && !useWebSearch) {
            generationConfig {
                responseMimeType = "application/json"
                if (firebaseSchema != null) {
                    responseSchema = firebaseSchema
                }
            }
        } else null

        val model = when {
            useWebSearch -> Firebase.ai.generativeModel(
                modelName = modelName,
                systemInstruction = sysContent,
                tools = listOf(Tool.googleSearch())
            )
            genConfig != null -> Firebase.ai.generativeModel(
                modelName = modelName,
                systemInstruction = sysContent,
                generationConfig = genConfig
            )
            else -> Firebase.ai.generativeModel(
                modelName = modelName,
                systemInstruction = sysContent
            )
        }

        val historyContents: List<Content> = history.map { m ->
            content(if (m.isUser) "user" else "model") { text(m.text) }
        }

        return if (useWebSearch || expectJson || onStreamPartial == null) {
            val response = withTimeout(60_000L) {
                if (historyContents.isNotEmpty()) {
                    model.startChat(historyContents).sendMessage(userPrompt)
                } else {
                    model.generateContent(userPrompt)
                }
            }
            val rawText = response.text?.trim().orEmpty()
            if (rawText.isBlank()) throw IllegalStateException("Модель вернула пустой ответ.")
            val cleanText = if (expectJson) extractCleanJson(rawText) else rawText
            onStreamPartial?.invoke(cleanText)

            val sources = mutableListOf<GroundingSource>()
            response.candidates.firstOrNull()?.groundingMetadata?.groundingChunks?.forEach { chunk ->
                val uri = chunk.web?.uri?.trim().orEmpty()
                val title = chunk.web?.title?.trim().orEmpty()
                if (uri.isNotEmpty()) {
                    sources.add(GroundingSource(title = title.ifEmpty { uri }, uri = uri))
                }
            }
            GatewayResponse(
                text = cleanText,
                groundingSources = sources.distinctBy { it.uri },
                providerUsed = AiProviderType.FIREBASE_AI_LOGIC
            )
        } else {
            val accumulated = StringBuilder()
            val streamFlow = if (historyContents.isNotEmpty()) {
                model.startChat(historyContents).sendMessageStream(userPrompt)
            } else {
                model.generateContentStream(userPrompt)
            }
            withTimeout(60_000L) {
                streamFlow.collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        onStreamPartial(accumulated.toString())
                    }
                }
            }
            val finalText = accumulated.toString().trim()
            if (finalText.isBlank()) throw IllegalStateException("Модель вернула пустой ответ.")
            GatewayResponse(
                text = finalText,
                providerUsed = AiProviderType.FIREBASE_AI_LOGIC
            )
        }
    }

    // --- 2. Direct Gemini REST API (OAuth Account OR Custom Gemini API Keys) ---
    private suspend fun executeDirectGeminiRest(
        providerType: AiProviderType,
        modelName: String,
        authHeaderName: String,
        authHeaderValue: String,
        projectHeader: String?,
        systemInstruction: String?,
        history: List<GatewayChatMessage>,
        userPrompt: String,
        useWebSearch: Boolean,
        expectJson: Boolean,
        onStreamPartial: ((String) -> Unit)?
    ): GatewayResponse = withContext(Dispatchers.IO) {
        val cleanModel = modelName.trim().removePrefix("models/")
        val payload = JSONObject().apply {
            if (!systemInstruction.isNullOrBlank()) {
                put(
                    "systemInstruction",
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", systemInstruction))
                    )
                )
            }
            val contentsArr = JSONArray()
            history.forEach { msg ->
                contentsArr.put(
                    JSONObject()
                        .put("role", if (msg.isUser) "user" else "model")
                        .put("parts", JSONArray().put(JSONObject().put("text", msg.text)))
                )
            }
            contentsArr.put(
                JSONObject()
                    .put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", userPrompt)))
            )
            put("contents", contentsArr)

            if (useWebSearch && !expectJson) {
                put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
            } else if (expectJson) {
                put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
            }
        }

        val shouldStream = !useWebSearch && !expectJson && onStreamPartial != null
        val endpoint = if (shouldStream) {
            "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:streamGenerateContent?alt=sse"
        } else {
            "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent"
        }

        val reqBuilder = Request.Builder()
            .url(endpoint)
            .header(authHeaderName, authHeaderValue)
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))

        if (!projectHeader.isNullOrBlank()) {
            reqBuilder.header("x-goog-user-project", projectHeader)
        }

        httpClient.newCall(reqBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                val errBody = resp.body?.string().orEmpty()
                throw mapHttpError(resp.code, errBody, providerType)
            }

            if (shouldStream) {
                val accumulated = StringBuilder()
                val reader = resp.body?.charStream()?.buffered()
                    ?: throw IOException("Пустой поток ответа от Gemini API.")
                reader.forEachLine { line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("data:")) {
                        val dataPayload = trimmed.removePrefix("data:").trim()
                        if (dataPayload.isNotEmpty() && dataPayload != "[DONE]") {
                            val chunkObj = runCatching { JSONObject(dataPayload) }.getOrNull()
                            val partText = extractGeminiCandidateText(chunkObj)
                            if (partText.isNotEmpty()) {
                                accumulated.append(partText)
                                onStreamPartial?.invoke(accumulated.toString())
                            }
                        }
                    }
                }
                val finalStr = accumulated.toString().trim()
                if (finalStr.isBlank()) throw IllegalStateException("Пустой ответ от Gemini API.")
                GatewayResponse(text = finalStr, providerUsed = providerType)
            } else {
                val rawBody = resp.body?.string().orEmpty()
                val json = JSONObject(rawBody)
                val rawText = extractGeminiCandidateText(json).trim()
                if (rawText.isBlank()) throw IllegalStateException("Пустой ответ от Gemini API.")
                val finalOut = if (expectJson) extractCleanJson(rawText) else rawText
                onStreamPartial?.invoke(finalOut)

                val sources = extractGeminiRestGroundingSources(json)
                GatewayResponse(
                    text = finalOut,
                    groundingSources = sources,
                    providerUsed = providerType
                )
            }
        }
    }

    private fun extractGeminiCandidateText(root: JSONObject?): String {
        if (root == null) return ""
        val candidates = root.optJSONArray("candidates") ?: return ""
        val first = candidates.optJSONObject(0) ?: return ""
        val parts = first.optJSONObject("content")?.optJSONArray("parts") ?: return ""
        return buildString {
            for (i in 0 until parts.length()) {
                val p = parts.optJSONObject(i) ?: continue
                append(p.optString("text", ""))
            }
        }
    }

    private fun extractGeminiRestGroundingSources(root: JSONObject): List<GroundingSource> {
        val candidates = root.optJSONArray("candidates") ?: return emptyList()
        val first = candidates.optJSONObject(0) ?: return emptyList()
        val chunks = first.optJSONObject("groundingMetadata")?.optJSONArray("groundingChunks")
            ?: return emptyList()
        return buildList {
            for (i in 0 until chunks.length()) {
                val web = chunks.optJSONObject(i)?.optJSONObject("web") ?: continue
                val uri = web.optString("uri", "").trim()
                val title = web.optString("title", "").trim()
                if (uri.isNotEmpty()) {
                    add(GroundingSource(title = title.ifEmpty { uri }, uri = uri))
                }
            }
        }.distinctBy { it.uri }
    }

    // --- 3. OpenAI-Compatible Protocol (Antigravity Proxy, OpenRouter, Groq, DeepSeek, Local Ollama/LM Studio) ---
    private suspend fun executeOpenAiCompatible(
        providerType: AiProviderType,
        baseUrl: String,
        modelName: String,
        bearerToken: String,
        extraHeaders: Map<String, String>,
        systemInstruction: String?,
        history: List<GatewayChatMessage>,
        userPrompt: String,
        expectJson: Boolean,
        onStreamPartial: ((String) -> Unit)?
    ): GatewayResponse = withContext(Dispatchers.IO) {
        val shouldStream = !expectJson && onStreamPartial != null
        val messagesArr = JSONArray()
        if (!systemInstruction.isNullOrBlank()) {
            val sys = if (expectJson) {
                "$systemInstruction\n\nВАЖНО: Верни ТОЛЬКО валидный JSON-объект без пояснений и без обёрток ```json."
            } else {
                systemInstruction
            }
            messagesArr.put(JSONObject().put("role", "system").put("content", sys))
        } else if (expectJson) {
            messagesArr.put(
                JSONObject()
                    .put("role", "system")
                    .put("content", "Отвечай строго валидным JSON-объектом без markdown-разметки и комментариев.")
            )
        }

        history.forEach { m ->
            messagesArr.put(
                JSONObject()
                    .put("role", if (m.isUser) "user" else "assistant")
                    .put("content", m.text)
            )
        }
        messagesArr.put(JSONObject().put("role", "user").put("content", userPrompt))

        val payload = JSONObject()
            .put("model", modelName.trim())
            .put("messages", messagesArr)
            .put("stream", shouldStream)

        val endpoint = if (baseUrl.endsWith("/chat/completions")) {
            baseUrl
        } else {
            "$baseUrl/chat/completions"
        }

        val reqBuilder = Request.Builder()
            .url(endpoint)
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))

        if (bearerToken.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer $bearerToken")
        }
        extraHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }

        httpClient.newCall(reqBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                val errBody = resp.body?.string().orEmpty()
                throw mapHttpError(resp.code, errBody, providerType)
            }

            val contentType = resp.header("Content-Type").orEmpty().lowercase()
            if (shouldStream && contentType.contains("event-stream")) {
                val accumulated = StringBuilder()
                val reader = resp.body?.charStream()?.buffered()
                    ?: throw IOException("Пустой SSE-поток от сервера.")
                reader.forEachLine { line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("data:")) {
                        val data = trimmed.removePrefix("data:").trim()
                        if (data.isNotEmpty() && data != "[DONE]") {
                            val obj = runCatching { JSONObject(data) }.getOrNull()
                            val delta = obj?.optJSONArray("choices")
                                ?.optJSONObject(0)
                                ?.optJSONObject("delta")
                                ?.optString("content", "")
                                .orEmpty()
                            if (delta.isNotEmpty()) {
                                accumulated.append(delta)
                                onStreamPartial?.invoke(stripReasoningTagsForDisplay(accumulated.toString()))
                            }
                        }
                    }
                }
                val cleaned = stripReasoningTagsForDisplay(accumulated.toString()).trim()
                if (cleaned.isBlank()) throw IllegalStateException("Сервер вернул пустой ответ.")
                GatewayResponse(text = cleaned, providerUsed = providerType)
            } else {
                val rawBody = resp.body?.string().orEmpty()
                val json = JSONObject(rawBody)
                val rawContent = json.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content", "")
                    .orEmpty()
                val stripped = stripReasoningTagsForDisplay(rawContent).trim()
                if (stripped.isBlank()) throw IllegalStateException("Сервер вернул пустой ответ.")
                val finalOut = if (expectJson) extractCleanJson(stripped) else stripped
                onStreamPartial?.invoke(finalOut)
                GatewayResponse(text = finalOut, providerUsed = providerType)
            }
        }
    }

    private suspend fun executeAntigravityWithDualProtocol(
        baseUrl: String,
        modelName: String,
        token: String,
        systemInstruction: String?,
        history: List<GatewayChatMessage>,
        userPrompt: String,
        expectJson: Boolean,
        onStreamPartial: ((String) -> Unit)?
    ): GatewayResponse {
        return try {
            executeOpenAiCompatible(
                providerType = AiProviderType.ANTIGRAVITY_PROXY,
                baseUrl = baseUrl,
                modelName = modelName,
                bearerToken = token,
                extraHeaders = mapOf("x-api-key" to token),
                systemInstruction = systemInstruction,
                history = history,
                userPrompt = userPrompt,
                expectJson = expectJson,
                onStreamPartial = onStreamPartial
            )
        } catch (e: HttpEndpointNotFoundException) {
            // Some Antigravity proxy forks (built for Claude Code CLI) only expose Anthropic /v1/messages
            val messagesUrl = when {
                baseUrl.endsWith("/messages") -> baseUrl
                baseUrl.endsWith("/chat/completions") -> baseUrl.removeSuffix("/chat/completions") + "/messages"
                else -> "$baseUrl/messages"
            }
            Log.i(TAG, "Antigravity /chat/completions returned 404, falling back to Anthropic protocol $messagesUrl")
            executeAnthropicMessages(
                modelName = modelName,
                apiKey = token,
                systemInstruction = systemInstruction,
                history = history,
                userPrompt = userPrompt,
                expectJson = expectJson,
                onStreamPartial = onStreamPartial,
                endpointUrl = messagesUrl,
                providerType = AiProviderType.ANTIGRAVITY_PROXY,
                sendBearerHeader = true
            )
        }
    }

    // --- 4. Anthropic Claude Messages API (Direct & Antigravity Claude Bridge) ---
    private suspend fun executeAnthropicMessages(
        modelName: String,
        apiKey: String,
        systemInstruction: String?,
        history: List<GatewayChatMessage>,
        userPrompt: String,
        expectJson: Boolean,
        onStreamPartial: ((String) -> Unit)?,
        endpointUrl: String = "https://api.anthropic.com/v1/messages",
        providerType: AiProviderType = AiProviderType.ANTHROPIC_CLAUDE,
        sendBearerHeader: Boolean = false
    ): GatewayResponse = withContext(Dispatchers.IO) {
        val shouldStream = !expectJson && onStreamPartial != null
        val messagesArr = JSONArray()
        history.forEach { m ->
            if (m.text.isNotBlank()) {
                messagesArr.put(
                    JSONObject()
                        .put("role", if (m.isUser) "user" else "assistant")
                        .put("content", m.text)
                )
            }
        }
        val finalUserText = if (expectJson) {
            "$userPrompt\n\nВерни строго валидный JSON-объект без обёрток ```json и лишнего текста."
        } else userPrompt
        messagesArr.put(JSONObject().put("role", "user").put("content", finalUserText))

        val payload = JSONObject()
            .put("model", modelName.trim())
            .put("max_tokens", 2048)
            .put("messages", messagesArr)
            .put("stream", shouldStream)

        if (!systemInstruction.isNullOrBlank()) {
            payload.put("system", systemInstruction)
        }

        val reqBuilder = Request.Builder()
            .url(endpointUrl)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))

        if (sendBearerHeader && apiKey.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer $apiKey")
        }

        httpClient.newCall(reqBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                val errBody = resp.body?.string().orEmpty()
                throw mapHttpError(resp.code, errBody, providerType)
            }

            if (shouldStream) {
                val accumulated = StringBuilder()
                val reader = resp.body?.charStream()?.buffered()
                    ?: throw IOException("Пустой поток ответа от Messages API.")
                reader.forEachLine { line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("data:")) {
                        val data = trimmed.removePrefix("data:").trim()
                        val obj = runCatching { JSONObject(data) }.getOrNull()
                        if (obj?.optString("type") == "content_block_delta") {
                            val piece = obj.optJSONObject("delta")?.optString("text", "").orEmpty()
                            if (piece.isNotEmpty()) {
                                accumulated.append(piece)
                                onStreamPartial?.invoke(stripReasoningTagsForDisplay(accumulated.toString()))
                            }
                        }
                    }
                }
                val finalStr = stripReasoningTagsForDisplay(accumulated.toString()).trim()
                if (finalStr.isBlank()) throw IllegalStateException("Пустой ответ от Messages API.")
                GatewayResponse(text = finalStr, providerUsed = providerType)
            } else {
                val rawBody = resp.body?.string().orEmpty()
                val json = JSONObject(rawBody)
                val contentArr = json.optJSONArray("content") ?: JSONArray()
                val text = buildString {
                    for (i in 0 until contentArr.length()) {
                        val item = contentArr.optJSONObject(i) ?: continue
                        if (item.optString("type") == "text") {
                            append(item.optString("text", ""))
                        }
                    }
                }.let { stripReasoningTagsForDisplay(it) }.trim()
                if (text.isBlank()) throw IllegalStateException("Пустой ответ от Messages API.")
                val finalOut = if (expectJson) extractCleanJson(text) else text
                onStreamPartial?.invoke(finalOut)
                GatewayResponse(text = finalOut, providerUsed = providerType)
            }
        }
    }

    // --- Live Model Catalog Discovery (/models) ---
    suspend fun fetchProviderModels(settings: AppSettings): Result<List<String>> = withContext(Dispatchers.IO) {
        val creds = currentCredentials()
        runCatching {
            when (settings.aiProvider) {
                AiProviderType.FIREBASE_AI_LOGIC -> {
                    listOf("gemini-3.8-flash", "gemini-3.5-flash-lite")
                }

                AiProviderType.GOOGLE_OAUTH_ACCOUNT -> {
                    val token = resolveFreshGoogleOAuthToken(creds)
                    fetchGeminiModelsRest("Authorization", "Bearer $token", settings.googleCloudProjectId)
                }

                AiProviderType.GEMINI_API_KEY -> {
                    val key = creds.geminiKeysPool().firstOrNull()
                        ?: throw IllegalStateException("Сначала укажите ключ Gemini API.")
                    fetchGeminiModelsRest("x-goog-api-key", key, null)
                }

                AiProviderType.ANTIGRAVITY_PROXY -> {
                    val baseUrl = AiEndpointValidator.normalizeAndValidateBaseUrl(settings.antigravityBaseUrl).getOrThrow()
                    val token = creds.antigravityToken.ifBlank { "antigravity" }
                    val primary = fetchOpenAiStyleModels(
                        baseUrl = baseUrl,
                        bearerToken = token,
                        prioritizeFree = false,
                        includeAnthropicHeaders = true
                    )
                    if (primary.isNotEmpty()) {
                        primary
                    } else if (baseUrl.endsWith("/v1")) {
                        // Fallback for Antigravity dashboard endpoints (/api/models)
                        val altRoot = baseUrl.removeSuffix("/v1")
                        runCatching {
                            fetchOpenAiStyleModels(
                                baseUrl = "$altRoot/api",
                                bearerToken = token,
                                prioritizeFree = false,
                                includeAnthropicHeaders = true
                            )
                        }.getOrDefault(emptyList())
                    } else {
                        emptyList()
                    }
                }

                AiProviderType.OPENROUTER -> {
                    fetchOpenAiStyleModels(
                        baseUrl = "https://openrouter.ai/api/v1",
                        bearerToken = creds.openRouterKeysPool().firstOrNull().orEmpty(),
                        prioritizeFree = true
                    )
                }

                AiProviderType.OPENAI_COMPATIBLE -> {
                    val baseUrl = AiEndpointValidator.normalizeAndValidateBaseUrl(settings.openAiCompatibleBaseUrl).getOrThrow()
                    val key = creds.openAiCompatibleKeysPool().firstOrNull()
                        ?: throw IllegalStateException("Укажите API-ключ для загрузки списка моделей.")
                    fetchOpenAiStyleModels(baseUrl, key, prioritizeFree = false)
                }

                AiProviderType.ANTHROPIC_CLAUDE -> {
                    listOf(
                        "claude-3-7-sonnet-latest",
                        "claude-3-5-sonnet-latest",
                        "claude-3-5-haiku-latest"
                    )
                }

                AiProviderType.LOCAL_SERVER -> {
                    val baseUrl = AiEndpointValidator.normalizeAndValidateBaseUrl(settings.localServerBaseUrl).getOrThrow()
                    fetchOpenAiStyleModels(baseUrl, creds.localServerApiKey, prioritizeFree = false)
                }
            }
        }
    }

    private fun fetchGeminiModelsRest(
        headerName: String,
        headerValue: String,
        projectId: String?
    ): List<String> {
        val builder = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models?pageSize=50")
            .header(headerName, headerValue)
            .get()
        if (!projectId.isNullOrBlank()) {
            builder.header("x-goog-user-project", projectId.trim())
        }
        httpClient.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw mapHttpError(resp.code, resp.body?.string().orEmpty(), AiProviderType.GEMINI_API_KEY)
            }
            val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("models") ?: JSONArray()
            return buildList {
                for (i in 0 until arr.length()) {
                    val m = arr.optJSONObject(i) ?: continue
                    val methods = m.optJSONArray("supportedGenerationMethods")?.toString().orEmpty()
                    val name = m.optString("name", "").removePrefix("models/")
                    if (name.isNotBlank() && methods.contains("generateContent")) {
                        add(name)
                    }
                }
            }.distinct().take(30)
        }
    }

    private fun fetchOpenAiStyleModels(
        baseUrl: String,
        bearerToken: String,
        prioritizeFree: Boolean,
        includeAnthropicHeaders: Boolean = false
    ): List<String> {
        val url = if (baseUrl.endsWith("/models")) baseUrl else "$baseUrl/models"
        val builder = Request.Builder().url(url).get()
        if (bearerToken.isNotBlank()) {
            builder.header("Authorization", "Bearer $bearerToken")
            if (includeAnthropicHeaders) {
                builder.header("x-api-key", bearerToken)
                builder.header("anthropic-version", "2023-06-01")
            }
        }
        httpClient.newCall(builder.build()).execute().use { resp ->
            val rawBody = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw mapHttpError(
                    resp.code,
                    rawBody,
                    if (includeAnthropicHeaders) AiProviderType.ANTIGRAVITY_PROXY else AiProviderType.OPENAI_COMPATIBLE
                )
            }
            val allIds = parseModelIdsFromJson(rawBody)

            return if (prioritizeFree) {
                val freeOnes = allIds.filter { it.endsWith(":free") }
                val paidOnes = allIds.filterNot { it.endsWith(":free") }
                (freeOnes + paidOnes).take(50)
            } else {
                allIds.take(50)
            }
        }
    }

    // --- Live Connection Probe ---
    suspend fun testActiveConnection(settings: AppSettings): ConnectionProbeResult {
        val startMs = System.currentTimeMillis()
        val testSettings = settings.copy(autoFallbackToFirebase = false) // Test the exact selected provider!
        return try {
            val response = generateChatOrText(
                settings = testSettings,
                userPrompt = "Ответь одной короткой фразой на русском (до 8 слов): готов ли ты вести ролевую игру?"
            )
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            ConnectionProbeResult(
                ok = true,
                latencyMs = elapsed,
                providerTitle = settings.aiProvider.title,
                modelUsed = settings.currentEffectiveModel(),
                message = "Успешно (${elapsed} мс): «${response.text.take(90)}»"
            )
        } catch (e: Exception) {
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            ConnectionProbeResult(
                ok = false,
                latencyMs = elapsed,
                providerTitle = settings.aiProvider.title,
                modelUsed = settings.currentEffectiveModel(),
                message = mapReadableError(e)
            )
        }
    }

    private fun mapHttpError(code: Int, rawBody: String, provider: AiProviderType): Exception {
        val extractedMessage = runCatching {
            val root = JSONObject(rawBody)
            root.optJSONObject("error")?.optString("message")
                ?.ifBlank { root.optString("message") }
                .orEmpty()
        }.getOrDefault("").take(220)

        val detailSuffix = if (extractedMessage.isNotBlank()) ": $extractedMessage" else ""
        return when (code) {
            429 -> RateLimitOrQuotaException(
                "Превышен лимит запросов (429) у «${provider.shortBadge}»$detailSuffix",
                httpCode = 429
            )
            401 -> IllegalStateException(
                "Ошибка авторизации (401) у «${provider.shortBadge}» — проверьте API-ключ или токен прокси$detailSuffix"
            )
            403 -> {
                if (rawBody.contains("quota", ignoreCase = true) || rawBody.contains("RESOURCE_EXHAUSTED", ignoreCase = true)) {
                    RateLimitOrQuotaException(
                        "Квота ключа исчерпана (403) у «${provider.shortBadge}»$detailSuffix",
                        httpCode = 403
                    )
                } else {
                    IllegalStateException("Доступ запрещён (403) у «${provider.shortBadge}»$detailSuffix")
                }
            }
            404 -> HttpEndpointNotFoundException(
                "Модель или эндпоинт не найдены (404) у «${provider.shortBadge}». Проверьте название модели и адрес сервера$detailSuffix"
            )
            else -> IOException("Ошибка HTTP $code от «${provider.shortBadge}»$detailSuffix")
        }
    }

    fun mapReadableError(error: Throwable): String {
        return when (error) {
            is TimeoutCancellationException ->
                "Превышено время ожидания ответа ИИ (60 сек). Проверьте соединение или выберите более быструю модель."
            is java.net.ConnectException ->
                "Не удалось подключиться к серверу. Если вы используете Антигравити прокси или локальный Ollama/LM Studio, убедитесь, что сервер запущен и адрес указан верно."
            else -> error.localizedMessage ?: "Ошибка обращения к ИИ-провайдеру."
        }
    }

    companion object {
        fun stripReasoningTagsForDisplay(raw: String): String {
            // Removes completed <think>...</think> blocks from reasoning models (DeepSeek R1, QwQ)
            return raw.replace(Regex("""<think>[\s\S]*?</think>""", RegexOption.IGNORE_CASE), "").trimStart()
        }

        fun extractCleanJson(raw: String): String {
            val withoutThink = stripReasoningTagsForDisplay(raw).trim()
            val withoutFences = withoutThink
                .removePrefix("```json")
                .removePrefix("```JSON")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            val firstBrace = withoutFences.indexOf('{')
            val lastBrace = withoutFences.lastIndexOf('}')
            return if (firstBrace >= 0 && lastBrace > firstBrace) {
                withoutFences.substring(firstBrace, lastBrace + 1)
            } else {
                withoutFences
            }
        }

        /**
         * Parses model IDs from OpenAI (`{data:[{id}]}`), Gemini/Anthropic (`{models:[{id/name}]}`),
         * Cloud Code / Antigravity object maps (`{models:{"gemini-2.5-pro":{...}}}`),
         * or plain JSON arrays (`["gemini-2.5-flash", ...]`) without relying on Android's stubbed org.json in JVM tests.
         */
        fun parseModelIdsFromJson(rawBody: String): List<String> {
            val trimmed = rawBody.trim()
            if (trimmed.isEmpty()) return emptyList()
            val root = SimpleJsonReader(trimmed).readRootOrNull() ?: return emptyList()

            fun extractFromArray(items: List<SimpleJsonValue>): List<String> = buildList {
                for (item in items) {
                    when (item) {
                        is SimpleJsonValue.Obj -> {
                            val id = (item.fields["id"] as? SimpleJsonValue.Str)?.value
                                ?: (item.fields["name"] as? SimpleJsonValue.Str)?.value
                                ?: (item.fields["model"] as? SimpleJsonValue.Str)?.value
                                ?: ""
                            val clean = id.trim().removePrefix("models/")
                            if (clean.isNotBlank()) add(clean)
                        }
                        is SimpleJsonValue.Str -> {
                            val clean = item.value.trim().removePrefix("models/")
                            if (clean.isNotBlank()) add(clean)
                        }
                        else -> Unit
                    }
                }
            }

            if (root is SimpleJsonValue.Arr) {
                return extractFromArray(root.items).distinct()
            }
            if (root !is SimpleJsonValue.Obj) return emptyList()

            val candidateArrayKeys = listOf("data", "models", "availableModels", "items", "result")
            for (key in candidateArrayKeys) {
                val arr = root.fields[key] as? SimpleJsonValue.Arr
                if (arr != null) {
                    val parsed = extractFromArray(arr.items)
                    if (parsed.isNotEmpty()) return parsed.distinct()
                }
            }

            val candidateMapKeys = listOf("models", "availableModels", "modelMapping", "data", "result")
            for (key in candidateMapKeys) {
                val mapObj = root.fields[key] as? SimpleJsonValue.Obj
                if (mapObj != null && mapObj.fields.isNotEmpty()) {
                    val nestedArr = (mapObj.fields["models"] as? SimpleJsonValue.Arr)
                        ?: (mapObj.fields["data"] as? SimpleJsonValue.Arr)
                    if (nestedArr != null) {
                        val nestedParsed = extractFromArray(nestedArr.items)
                        if (nestedParsed.isNotEmpty()) return nestedParsed.distinct()
                    }
                    val keys = mapObj.fields.keys
                        .map { it.trim().removePrefix("models/") }
                        .filter { it.isNotBlank() }
                    if (keys.isNotEmpty()) return keys.distinct()
                }
            }

            return emptyList()
        }
    }
}

private class HttpEndpointNotFoundException(message: String) : IllegalStateException(message)

private sealed interface SimpleJsonValue {
    data class Obj(val fields: LinkedHashMap<String, SimpleJsonValue>) : SimpleJsonValue
    data class Arr(val items: List<SimpleJsonValue>) : SimpleJsonValue
    data class Str(val value: String) : SimpleJsonValue
    data object Primitive : SimpleJsonValue
}

private class SimpleJsonReader(private val src: String) {
    private var pos = 0

    fun readRootOrNull(): SimpleJsonValue? = runCatching {
        skipWs()
        readValue()
    }.getOrNull()

    private fun skipWs() {
        while (pos < src.length && src[pos].isWhitespace()) pos++
    }

    private fun readValue(): SimpleJsonValue {
        skipWs()
        if (pos >= src.length) error("Unexpected EOF")
        return when (src[pos]) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> SimpleJsonValue.Str(readString())
            else -> readPrimitive()
        }
    }

    private fun readObject(): SimpleJsonValue.Obj {
        pos++ // '{'
        val map = LinkedHashMap<String, SimpleJsonValue>()
        skipWs()
        if (pos < src.length && src[pos] == '}') {
            pos++
            return SimpleJsonValue.Obj(map)
        }
        while (pos < src.length) {
            skipWs()
            val key = readString()
            skipWs()
            if (pos >= src.length || src[pos] != ':') error("Expected ':'")
            pos++
            val value = readValue()
            map[key] = value
            skipWs()
            if (pos >= src.length) error("Expected ',' or '}'")
            when (src[pos]) {
                ',' -> pos++
                '}' -> {
                    pos++
                    return SimpleJsonValue.Obj(map)
                }
                else -> error("Unexpected char in object")
            }
        }
        error("Unterminated object")
    }

    private fun readArray(): SimpleJsonValue.Arr {
        pos++ // '['
        val list = ArrayList<SimpleJsonValue>()
        skipWs()
        if (pos < src.length && src[pos] == ']') {
            pos++
            return SimpleJsonValue.Arr(list)
        }
        while (pos < src.length) {
            list.add(readValue())
            skipWs()
            if (pos >= src.length) error("Expected ',' or ']'")
            when (src[pos]) {
                ',' -> pos++
                ']' -> {
                    pos++
                    return SimpleJsonValue.Arr(list)
                }
                else -> error("Unexpected char in array")
            }
        }
        error("Unterminated array")
    }

    private fun readString(): String {
        if (pos >= src.length || src[pos] != '"') error("Expected '\"'")
        pos++
        val sb = StringBuilder()
        while (pos < src.length) {
            val ch = src[pos++]
            if (ch == '"') return sb.toString()
            if (ch == '\\' && pos < src.length) {
                when (val esc = src[pos++]) {
                    '"', '\\', '/' -> sb.append(esc)
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    'u' -> {
                        if (pos + 4 <= src.length) {
                            val code = src.substring(pos, pos + 4).toInt(16)
                            sb.append(code.toChar())
                            pos += 4
                        }
                    }
                    else -> sb.append(esc)
                }
            } else {
                sb.append(ch)
            }
        }
        error("Unterminated string")
    }

    private fun readPrimitive(): SimpleJsonValue {
        val start = pos
        while (pos < src.length && !src[pos].isWhitespace() && src[pos] !in ",}]") {
            pos++
        }
        if (pos == start) error("Empty primitive")
        return SimpleJsonValue.Primitive
    }
}


