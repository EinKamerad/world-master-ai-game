package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ai.ConnectionProbeResult
import com.example.data.preferences.AiCredentialsSnapshot
import com.example.data.preferences.AiProviderType
import com.example.data.preferences.AntigravityPresets
import com.example.data.preferences.AppSettings
import com.example.data.preferences.LocalServerPresets
import com.example.data.preferences.OpenAiCompatiblePresets
import com.example.data.preferences.maskSecretPreview

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiConnectionsTab(
    settings: AppSettings,
    credentials: AiCredentialsSnapshot,
    isTestingConnection: Boolean,
    probeResult: ConnectionProbeResult?,
    isFetchingModels: Boolean,
    discoveredModels: List<String>,
    isGoogleAuthLoading: Boolean,
    onSelectProvider: (AiProviderType) -> Unit,
    onSetAutoFallback: (Boolean) -> Unit,
    onSetFirebaseModel: (String) -> Unit,
    onSaveAntigravity: (baseUrl: String, model: String, token: String) -> Unit,
    onSignInGoogleOAuthClick: () -> Unit,
    onSignOutGoogleOAuthClick: () -> Unit,
    onSaveGoogleOAuthManual: (model: String, projectId: String, manualToken: String) -> Unit,
    onSaveGeminiKeys: (keysRaw: String, model: String) -> Unit,
    onSaveOpenRouter: (keysRaw: String, model: String) -> Unit,
    onSaveOpenAiCompatible: (baseUrl: String, model: String, keysRaw: String) -> Unit,
    onSaveAnthropic: (keysRaw: String, model: String) -> Unit,
    onSaveLocalServer: (baseUrl: String, model: String, apiKey: String) -> Unit,
    onFetchModelsClick: () -> Unit,
    onTestConnectionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Active Status & Connection Tester Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Активный канал: ${settings.aiProvider.shortBadge}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Модель: ${settings.currentEffectiveModel()}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    Button(
                        onClick = onTestConnectionClick,
                        enabled = !isTestingConnection,
                        modifier = Modifier.testTag("test_ai_connection_button")
                    ) {
                        if (isTestingConnection) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Тест...")
                        } else {
                            Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Проверить")
                        }
                    }
                }

                if (probeResult != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (probeResult.ok) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (probeResult.ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = if (probeResult.ok) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                },
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = probeResult.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (probeResult.ok) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                }
                            )
                        }
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Авто-резерв на встроенный Firebase ИИ",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Если на внешнем ключе или прокси кончился лимит (429) или пропала связь, ход не пропадёт и выполнится через встроенный Gemini",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.autoFallbackToFirebase,
                        onCheckedChange = onSetAutoFallback,
                        modifier = Modifier.testTag("auto_fallback_switch")
                    )
                }
            }
        }

        // 2. Provider Selector Grid
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Выберите источник ИИ и лимитов:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AiProviderType.entries.forEach { provider ->
                        FilterChip(
                            selected = settings.aiProvider == provider,
                            onClick = { onSelectProvider(provider) },
                            label = { Text(provider.shortBadge) },
                            modifier = Modifier.testTag("provider_chip_${provider.name}")
                        )
                    }
                }
                Text(
                    text = settings.aiProvider.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 3. Selected Provider Detailed Configuration Card
        when (settings.aiProvider) {
            AiProviderType.FIREBASE_AI_LOGIC -> FirebaseAiConfigCard(
                selectedModel = settings.modelName,
                onSelectModel = onSetFirebaseModel
            )

            AiProviderType.ANTIGRAVITY_PROXY -> AntigravityConfigCard(
                baseUrlInit = settings.antigravityBaseUrl,
                modelInit = settings.antigravityModel,
                savedToken = credentials.antigravityToken,
                isFetchingModels = isFetchingModels,
                discoveredModels = discoveredModels,
                onFetchModels = onFetchModelsClick,
                onSave = onSaveAntigravity
            )

            AiProviderType.GOOGLE_OAUTH_ACCOUNT -> GoogleOAuthConfigCard(
                modelInit = settings.googleOAuthModel,
                projectIdInit = settings.googleCloudProjectId,
                credentials = credentials,
                isGoogleAuthLoading = isGoogleAuthLoading,
                isFetchingModels = isFetchingModels,
                discoveredModels = discoveredModels,
                onSignInClick = onSignInGoogleOAuthClick,
                onSignOutClick = onSignOutGoogleOAuthClick,
                onFetchModels = onFetchModelsClick,
                onSaveManual = onSaveGoogleOAuthManual
            )

            AiProviderType.GEMINI_API_KEY -> MultiKeyCloudConfigCard(
                title = "Свои ключи Google AI Studio (Gemini API)",
                description = "Вставьте один или сразу несколько ключей AIza... (каждый с новой строки). При ошибке лимита 429 приложение автоматически переключится на следующий ключ в списке!",
                keysInit = credentials.geminiApiKeysRaw,
                poolCount = credentials.geminiKeysPool().size,
                modelInit = settings.geminiKeyModel,
                presetModels = listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash"),
                isFetchingModels = isFetchingModels,
                discoveredModels = discoveredModels,
                onFetchModels = onFetchModelsClick,
                onSave = onSaveGeminiKeys
            )

            AiProviderType.OPENROUTER -> MultiKeyCloudConfigCard(
                title = "OpenRouter (Бесплатные :free и 300+ моделей)",
                description = "Создайте бесплатный ключ на openrouter.ai и используйте десятки моделей с суффиксом :free (DeepSeek V3/R1, Qwen, Llama) или платные модели по единому балансу. Поддерживается пул из нескольких ключей.",
                keysInit = credentials.openRouterApiKeysRaw,
                poolCount = credentials.openRouterKeysPool().size,
                modelInit = settings.openRouterModel,
                presetModels = listOf(
                    "deepseek/deepseek-chat-v3-0324:free",
                    "deepseek/deepseek-r1:free",
                    "qwen/qwen3-235b-a22b:free",
                    "meta-llama/llama-3.3-70b-instruct:free"
                ),
                isFetchingModels = isFetchingModels,
                discoveredModels = discoveredModels,
                onFetchModels = onFetchModelsClick,
                onSave = onSaveOpenRouter
            )

            AiProviderType.OPENAI_COMPATIBLE -> OpenAiCompatibleConfigCard(
                baseUrlInit = settings.openAiCompatibleBaseUrl,
                modelInit = settings.openAiCompatibleModel,
                keysInit = credentials.openAiCompatibleApiKeysRaw,
                poolCount = credentials.openAiCompatibleKeysPool().size,
                isFetchingModels = isFetchingModels,
                discoveredModels = discoveredModels,
                onFetchModels = onFetchModelsClick,
                onSave = onSaveOpenAiCompatible
            )

            AiProviderType.ANTHROPIC_CLAUDE -> MultiKeyCloudConfigCard(
                title = "Anthropic Claude API (Messages API)",
                description = "Прямая работа с моделями Claude (Sonnet / Haiku) через ваши ключи sk-ant-... с поддержкой ротации нескольких ключей.",
                keysInit = credentials.anthropicApiKeysRaw,
                poolCount = credentials.anthropicKeysPool().size,
                modelInit = settings.anthropicModel,
                presetModels = listOf(
                    "claude-3-7-sonnet-latest",
                    "claude-3-5-sonnet-latest",
                    "claude-3-5-haiku-latest"
                ),
                isFetchingModels = false,
                discoveredModels = emptyList(),
                onFetchModels = null,
                onSave = onSaveAnthropic
            )

            AiProviderType.LOCAL_SERVER -> LocalServerConfigCard(
                baseUrlInit = settings.localServerBaseUrl,
                modelInit = settings.localServerModel,
                apiKeyInit = credentials.localServerApiKey,
                isFetchingModels = isFetchingModels,
                discoveredModels = discoveredModels,
                onFetchModels = onFetchModelsClick,
                onSave = onSaveLocalServer
            )
        }

        // 4. Hardware Keystore Security Badge
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Защита ключей: введённые API-ключи и OAuth-токены шифруются аппаратным ключом AndroidKeyStore (AES-256-GCM), исключены из облачных бэкапов и не записываются в логи.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FirebaseAiConfigCard(
    selectedModel: String,
    onSelectModel: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Встроенный облачный Gemini (без ключей)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Работает сразу из коробки через защищённую аттестацию Firebase App Check. Поддерживает потоковый ответ и живой веб-поиск Google.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedModel == "gemini-3.8-flash",
                    onClick = { onSelectModel("gemini-3.8-flash") },
                    label = { Text("Gemini 3.8 Flash (Рекомендуется)") }
                )
                FilterChip(
                    selected = selectedModel == "gemini-3.5-flash-lite",
                    onClick = { onSelectModel("gemini-3.5-flash-lite") },
                    label = { Text("Gemini 3.5 Flash-Lite (Экономный)") }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AntigravityConfigCard(
    baseUrlInit: String,
    modelInit: String,
    savedToken: String,
    isFetchingModels: Boolean,
    discoveredModels: List<String>,
    onFetchModels: () -> Unit,
    onSave: (baseUrl: String, model: String, token: String) -> Unit
) {
    var baseUrl by rememberSaveable(baseUrlInit) { mutableStateOf(baseUrlInit) }
    var model by rememberSaveable(modelInit) { mutableStateOf(modelInit) }
    var token by rememberSaveable(savedToken) { mutableStateOf(savedToken) }
    var showSecret by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Шлюз Антигравити API (Antigravity Proxy)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Подключите ваш локальный или серверный antigravity-proxy / CLIProxyAPI (например bunx antigravity-proxy на порту 3000), чтобы расходовать квоты Антигравити на модели Gemini и Claude.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("Быстрые пресеты адреса:", style = MaterialTheme.typography.labelMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AntigravityPresets.forEach { preset ->
                    AssistChip(
                        onClick = {
                            baseUrl = preset.baseUrl
                            if (model.isBlank()) model = preset.defaultModel
                        },
                        label = { Text(preset.name, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL шлюза Антигравити (/v1)") },
                placeholder = { Text("http://10.0.2.2:3000/v1 или https://your-proxy.example.com/v1") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("antigravity_url_input")
            )

            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Токен / API-ключ прокси (если настроен)") },
                placeholder = { Text("Оставьте пустым для локального прокси без пароля") },
                singleLine = true,
                visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showSecret = !showSecret }) {
                        Icon(
                            imageVector = if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showSecret) "Скрыть токен" else "Показать токен"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("ID модели в Антигравити") },
                placeholder = { Text("gemini-2.5-flash, gemini-2.5-pro, claude-3-7-sonnet...") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("antigravity_model_input")
            )

            ModelSuggestionsSection(
                presets = listOf("gemini-2.5-flash", "gemini-2.5-pro", "claude-3-7-sonnet", "claude-3-5-sonnet"),
                discovered = discoveredModels,
                selectedModel = model,
                isFetching = isFetchingModels,
                onFetchClick = {
                    onSave(baseUrl, model, token)
                    onFetchModels()
                },
                onSelectModel = { chosen ->
                    model = chosen
                    onSave(baseUrl, chosen, token)
                }
            )

            Button(
                onClick = { onSave(baseUrl, model, token) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_antigravity_button")
            ) {
                Text("Сохранить настройки Антигравити")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoogleOAuthConfigCard(
    modelInit: String,
    projectIdInit: String,
    credentials: AiCredentialsSnapshot,
    isGoogleAuthLoading: Boolean,
    isFetchingModels: Boolean,
    discoveredModels: List<String>,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onFetchModels: () -> Unit,
    onSaveManual: (model: String, projectId: String, manualToken: String) -> Unit
) {
    var model by rememberSaveable(modelInit) { mutableStateOf(modelInit) }
    var projectId by rememberSaveable(projectIdInit) { mutableStateOf(projectIdInit) }
    var manualToken by rememberSaveable(credentials.googleOAuthAccessToken) {
        mutableStateOf(credentials.googleOAuthAccessToken)
    }
    var showToken by remember { mutableStateOf(false) }

    val hasToken = credentials.googleOAuthAccessToken.isNotBlank()

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Аккаунт Google / Gemini (OAuth 2.0)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Авторизуйтесь под своим аккаунтом Google, чтобы отправлять запросы напрямую в Generative Language API от имени вашего аккаунта и расходовать его персональные квоты.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (hasToken) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (credentials.googleOAuthEmail.isNotBlank()) {
                                    "Аккаунт: ${credentials.googleOAuthEmail}"
                                } else {
                                    "OAuth-токен активен (${maskSecretPreview(credentials.googleOAuthAccessToken)})"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = if (credentials.isGoogleOAuthTokenLikelyFresh()) {
                                    "Сессия свежая • Автопродление включено"
                                } else {
                                    "Токен будет автоматически обновлён при следующем запросе"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                        IconButton(onClick = onSignOutClick) {
                            Icon(
                                imageVector = Icons.Default.Logout,
                                contentDescription = "Выйти из Google OAuth",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onSignInClick,
                enabled = !isGoogleAuthLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("google_oauth_signin_button")
            ) {
                if (isGoogleAuthLoading) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Подключение к Google...")
                } else {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (hasToken) "Сменить / обновить аккаунт Google" else "Войти через Google (OAuth 2.0)")
                }
            }

            OutlinedTextField(
                value = projectId,
                onValueChange = { projectId = it },
                label = { Text("Google Cloud Project ID (опционально для квот)") },
                placeholder = { Text("gen-lang-client-0798860006") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = manualToken,
                onValueChange = { manualToken = it },
                label = { Text("Или вставьте OAuth Access Token вручную (ya29...)") },
                placeholder = { Text("Например из gcloud auth print-access-token") },
                singleLine = true,
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showToken = !showToken }) {
                        Icon(
                            imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showToken) "Скрыть токен" else "Показать токен"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Модель Gemini") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            ModelSuggestionsSection(
                presets = listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash"),
                discovered = discoveredModels,
                selectedModel = model,
                isFetching = isFetchingModels,
                onFetchClick = {
                    onSaveManual(model, projectId, manualToken)
                    onFetchModels()
                },
                onSelectModel = { chosen ->
                    model = chosen
                    onSaveManual(chosen, projectId, manualToken)
                }
            )

            OutlinedButton(
                onClick = { onSaveManual(model, projectId, manualToken) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Сохранить параметры OAuth / Проекта")
            }
        }
    }
}

@Composable
private fun MultiKeyCloudConfigCard(
    title: String,
    description: String,
    keysInit: String,
    poolCount: Int,
    modelInit: String,
    presetModels: List<String>,
    isFetchingModels: Boolean,
    discoveredModels: List<String>,
    onFetchModels: (() -> Unit)?,
    onSave: (keysRaw: String, model: String) -> Unit
) {
    var keysRaw by rememberSaveable(keysInit) { mutableStateOf(keysInit) }
    var model by rememberSaveable(modelInit) { mutableStateOf(modelInit) }
    var showKeys by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "Ключей в пуле: $poolCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = keysRaw,
                onValueChange = { keysRaw = it },
                label = { Text("API-ключ (или несколько ключей — по одному на строке)") },
                minLines = 2,
                maxLines = 5,
                visualTransformation = if (showKeys) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKeys = !showKeys }) {
                        Icon(
                            imageVector = if (showKeys) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showKeys) "Скрыть ключи" else "Показать ключи"
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("api_keys_pool_input")
            )

            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("ID модели") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cloud_model_input")
            )

            ModelSuggestionsSection(
                presets = presetModels,
                discovered = discoveredModels,
                selectedModel = model,
                isFetching = isFetchingModels,
                onFetchClick = onFetchModels?.let { fetch ->
                    {
                        onSave(keysRaw, model)
                        fetch()
                    }
                },
                onSelectModel = { chosen ->
                    model = chosen
                    onSave(keysRaw, chosen)
                }
            )

            Button(
                onClick = { onSave(keysRaw, model) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_cloud_keys_button")
            ) {
                Text("Сохранить ключи и модель")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OpenAiCompatibleConfigCard(
    baseUrlInit: String,
    modelInit: String,
    keysInit: String,
    poolCount: Int,
    isFetchingModels: Boolean,
    discoveredModels: List<String>,
    onFetchModels: () -> Unit,
    onSave: (baseUrl: String, model: String, keysRaw: String) -> Unit
) {
    var baseUrl by rememberSaveable(baseUrlInit) { mutableStateOf(baseUrlInit) }
    var model by rememberSaveable(modelInit) { mutableStateOf(modelInit) }
    var keysRaw by rememberSaveable(keysInit) { mutableStateOf(keysInit) }
    var showKeys by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Groq / DeepSeek / Mistral / OpenAI",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Ключей: $poolCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Text("Быстрый выбор сервиса:", style = MaterialTheme.typography.labelMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OpenAiCompatiblePresets.forEach { preset ->
                    AssistChip(
                        onClick = {
                            baseUrl = preset.baseUrl
                            model = preset.defaultModel
                        },
                        label = { Text(preset.name, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL API (/v1)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = keysRaw,
                onValueChange = { keysRaw = it },
                label = { Text("API-ключ (можно несколько через перенос строки)") },
                minLines = 2,
                maxLines = 4,
                visualTransformation = if (showKeys) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKeys = !showKeys }) {
                        Icon(
                            imageVector = if (showKeys) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showKeys) "Скрыть ключи" else "Показать ключи"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("ID модели") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            ModelSuggestionsSection(
                presets = listOf("llama-3.3-70b-versatile", "deepseek-chat", "mistral-small-latest", "gpt-4o-mini"),
                discovered = discoveredModels,
                selectedModel = model,
                isFetching = isFetchingModels,
                onFetchClick = {
                    onSave(baseUrl, model, keysRaw)
                    onFetchModels()
                },
                onSelectModel = { chosen ->
                    model = chosen
                    onSave(baseUrl, chosen, keysRaw)
                }
            )

            Button(
                onClick = { onSave(baseUrl, model, keysRaw) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Сохранить провайдера и ключи")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocalServerConfigCard(
    baseUrlInit: String,
    modelInit: String,
    apiKeyInit: String,
    isFetchingModels: Boolean,
    discoveredModels: List<String>,
    onFetchModels: () -> Unit,
    onSave: (baseUrl: String, model: String, apiKey: String) -> Unit
) {
    var baseUrl by rememberSaveable(baseUrlInit) { mutableStateOf(baseUrlInit) }
    var model by rememberSaveable(modelInit) { mutableStateOf(modelInit) }
    var apiKey by rememberSaveable(apiKeyInit) { mutableStateOf(apiKeyInit) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Computer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Локальный ИИ на ПК (Ollama / LM Studio / Kobold)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Играйте вообще без лимитов и облаков! На реальном телефоне замените 10.0.2.2 на Wi-Fi IP вашего компьютера (например http://192.168.1.45:11434/v1).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                LocalServerPresets.forEach { preset ->
                    AssistChip(
                        onClick = {
                            baseUrl = preset.baseUrl
                            model = preset.defaultModel
                        },
                        label = { Text(preset.name, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Адрес локального сервера (/v1)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Название локальной модели") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("Пароль / ключ сервера (необязательно)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            ModelSuggestionsSection(
                presets = listOf("llama3.2", "qwen2.5:14b", "gemma3:12b", "mistral-nemo"),
                discovered = discoveredModels,
                selectedModel = model,
                isFetching = isFetchingModels,
                onFetchClick = {
                    onSave(baseUrl, model, apiKey)
                    onFetchModels()
                },
                onSelectModel = { chosen ->
                    model = chosen
                    onSave(baseUrl, chosen, apiKey)
                }
            )

            Button(
                onClick = { onSave(baseUrl, model, apiKey) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Сохранить локальный сервер")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelSuggestionsSection(
    presets: List<String>,
    discovered: List<String>,
    selectedModel: String,
    isFetching: Boolean,
    onFetchClick: (() -> Unit)?,
    onSelectModel: (String) -> Unit
) {
    val combined = remember(presets, discovered) {
        if (discovered.isNotEmpty()) discovered else presets
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (discovered.isNotEmpty()) {
                    "Найдено моделей на сервере (${discovered.size}):"
                } else {
                    "Популярные модели:"
                },
                style = MaterialTheme.typography.labelMedium
            )
            if (onFetchClick != null) {
                AssistChip(
                    onClick = onFetchClick,
                    enabled = !isFetching,
                    leadingIcon = {
                        if (isFetching) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    },
                    label = { Text(if (isFetching) "Загрузка..." else "Загрузить модели", style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            combined.take(18).forEach { m ->
                FilterChip(
                    selected = selectedModel == m,
                    onClick = { onSelectModel(m) },
                    label = {
                        Text(
                            text = m,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
            }
        }
    }
}
