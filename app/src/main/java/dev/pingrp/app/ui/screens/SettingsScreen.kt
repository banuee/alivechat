package dev.pingrp.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pingrp.app.BuildConfig
import dev.pingrp.app.data.api.AIApiService
import dev.pingrp.app.data.model.ApiProvider
import dev.pingrp.app.data.prefs.SettingsManager
import dev.pingrp.app.data.updater.UpdateRepository
import dev.pingrp.app.data.updater.UpdateState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentSettings: SettingsManager.AppSettings,
    aiService: AIApiService,
    updateRepo: UpdateRepository,
    onBackClick: () -> Unit,
    onSaveSettings: (SettingsManager.AppSettings) -> Unit,
    onSaveAndClose: (SettingsManager.AppSettings) -> Unit
) {
    var groqKey by remember { mutableStateOf(currentSettings.groqApiKey) }
    var geminiKey by remember { mutableStateOf(currentSettings.geminiApiKey) }
    var userName by remember { mutableStateOf(currentSettings.userName) }
    var defaultProvider by remember { mutableStateOf(currentSettings.defaultProvider) }
    var groqModel by remember { mutableStateOf(currentSettings.groqModel) }
    var geminiModel by remember { mutableStateOf(currentSettings.geminiModel) }

    var useProxy by remember { mutableStateOf(currentSettings.useProxy) }
    var proxyType by remember { mutableStateOf(currentSettings.proxyType) }
    var proxyHost by remember { mutableStateOf(currentSettings.proxyHost) }
    var proxyPortStr by remember { mutableStateOf(currentSettings.proxyPort.toString()) }
    var proxyUser by remember { mutableStateOf(currentSettings.proxyUser) }
    var proxyPass by remember { mutableStateOf(currentSettings.proxyPassword) }
    var showProxyPass by remember { mutableStateOf(false) }

    var groqBaseUrl by remember { mutableStateOf(currentSettings.groqBaseUrl) }
    var geminiBaseUrl by remember { mutableStateOf(currentSettings.geminiBaseUrl) }

    var showGroqKey by remember { mutableStateOf(false) }
    var showGeminiKey by remember { mutableStateOf(false) }

    var testStatusGroq by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var testStatusGemini by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var isTestingGroq by remember { mutableStateOf(false) }
    var isTestingGemini by remember { mutableStateOf(false) }

    val updateState by updateRepo.updateState.collectAsState()

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    fun buildCurrentSettings(): SettingsManager.AppSettings {
        val port = proxyPortStr.toIntOrNull() ?: 1080
        return SettingsManager.AppSettings(
            groqApiKey = groqKey.trim(),
            geminiApiKey = geminiKey.trim(),
            defaultProvider = defaultProvider,
            groqModel = groqModel.trim().ifBlank { SettingsManager.DEFAULT_GROQ_MODEL },
            geminiModel = geminiModel.trim().ifBlank { SettingsManager.DEFAULT_GEMINI_MODEL },
            userName = userName.trim().ifBlank { "Игрок" },
            useProxy = useProxy,
            proxyType = proxyType,
            proxyHost = proxyHost.trim(),
            proxyPort = port,
            proxyUser = proxyUser.trim(),
            proxyPassword = proxyPass.trim(),
            groqBaseUrl = groqBaseUrl.trim().ifBlank { SettingsManager.DEFAULT_GROQ_BASE_URL },
            geminiBaseUrl = geminiBaseUrl.trim().ifBlank { SettingsManager.DEFAULT_GEMINI_BASE_URL }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                title = {
                    Text(
                        text = "Настройки AliveChat",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    FilledTonalButton(
                        onClick = { onSaveAndClose(buildCurrentSettings()) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Готово")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. КАРТОЧКА ПРОКСИ VERCEL
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudQueue,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Сеть & Прокси (Vercel)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = "AliveChat по умолчанию маршрутизирует запросы через Vercel Reverse Proxy. Это позволяет обойти геоблокировки Groq и Gemini в Беларуси и РФ без включения системного VPN.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = groqBaseUrl,
                        onValueChange = { groqBaseUrl = it },
                        label = { Text("Groq Endpoint URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    )

                    OutlinedTextField(
                        value = geminiBaseUrl,
                        onValueChange = { geminiBaseUrl = it },
                        label = { Text("Gemini Endpoint URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                groqBaseUrl = SettingsManager.VERCEL_PROXY_GROQ_BASE_URL
                                geminiBaseUrl = SettingsManager.VERCEL_PROXY_GEMINI_BASE_URL
                                onSaveSettings(buildCurrentSettings())
                            },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text("Прокси Vercel", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                groqBaseUrl = SettingsManager.OFFICIAL_GROQ_BASE_URL
                                geminiBaseUrl = SettingsManager.OFFICIAL_GEMINI_BASE_URL
                                onSaveSettings(buildCurrentSettings())
                            },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text("Официальные API", fontSize = 12.sp)
                        }
                    }
                }
            }

            // 2. КАРТОЧКА API КЛЮЧЕЙ
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Ключи нейросетей (API)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = "Укажите ваши персональные ключи доступа. Ключи сохраняются локально на вашем устройстве.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Выбор провайдера по умолчанию
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Провайдер по умолчанию:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        FilterChip(
                            selected = defaultProvider == ApiProvider.GROQ,
                            onClick = { defaultProvider = ApiProvider.GROQ },
                            label = { Text("Groq") }
                        )
                        FilterChip(
                            selected = defaultProvider == ApiProvider.GEMINI,
                            onClick = { defaultProvider = ApiProvider.GEMINI },
                            label = { Text("Gemini") }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // GROQ KEY
                    OutlinedTextField(
                        value = groqKey,
                        onValueChange = {
                            groqKey = it
                            testStatusGroq = null
                        },
                        label = { Text("Groq API Key (gsk_...)") },
                        visualTransformation = if (showGroqKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showGroqKey = !showGroqKey }) {
                                Icon(
                                    imageVector = if (showGroqKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isTestingGroq = true
                                    onSaveSettings(buildCurrentSettings())
                                    testStatusGroq = aiService.testConnection(ApiProvider.GROQ)
                                    isTestingGroq = false
                                }
                            },
                            enabled = !isTestingGroq,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            if (isTestingGroq) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Проверить Groq")
                        }

                        testStatusGroq?.let { (success, msg) ->
                            Text(
                                text = if (success) "✓ Доступен" else "✗ Ошибка",
                                color = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    testStatusGroq?.let { (_, msg) ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // GEMINI KEY
                    OutlinedTextField(
                        value = geminiKey,
                        onValueChange = {
                            geminiKey = it
                            testStatusGemini = null
                        },
                        label = { Text("Google Gemini API Key (AIza...)") },
                        visualTransformation = if (showGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showGeminiKey = !showGeminiKey }) {
                                Icon(
                                    imageVector = if (showGeminiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isTestingGemini = true
                                    onSaveSettings(buildCurrentSettings())
                                    testStatusGemini = aiService.testConnection(ApiProvider.GEMINI)
                                    isTestingGemini = false
                                }
                            },
                            enabled = !isTestingGemini,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            if (isTestingGemini) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Проверить Gemini")
                        }

                        testStatusGemini?.let { (success, _) ->
                            Text(
                                text = if (success) "✓ Доступен" else "✗ Ошибка",
                                color = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    testStatusGemini?.let { (_, msg) ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 3. МОДЕЛИ НЕЙРОСЕТЕЙ
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Используемые модели",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedTextField(
                        value = groqModel,
                        onValueChange = { groqModel = it },
                        label = { Text("Groq модель") },
                        supportingText = { Text("Рекомендуется: qwen/qwen3.8-27b или llama-3.3-70b-versatile") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    )

                    OutlinedTextField(
                        value = geminiModel,
                        onValueChange = { geminiModel = it },
                        label = { Text("Gemini модель") },
                        supportingText = { Text("Рекомендуется: gemini-3.5-flash-lite") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    )
                }
            }

            // 4. СОБСТВЕННЫЙ ПРОКСИ (SOCKS5 / HTTP)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VpnLock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Кастомный прокси",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Switch(
                            checked = useProxy,
                            onCheckedChange = { useProxy = it }
                        )
                    }

                    Text(
                        text = "Для подключения через собственный SOCKS5/HTTP сервер (например, личный VPS с Amnezia/Shadowsocks).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    AnimatedVisibility(visible = useProxy) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = proxyType == "SOCKS",
                                    onClick = { proxyType = "SOCKS" },
                                    label = { Text("SOCKS5") }
                                )
                                FilterChip(
                                    selected = proxyType == "HTTP",
                                    onClick = { proxyType = "HTTP" },
                                    label = { Text("HTTP") }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = proxyHost,
                                    onValueChange = { proxyHost = it },
                                    label = { Text("Хост / IP") },
                                    placeholder = { Text("1.2.3.4") },
                                    singleLine = true,
                                    modifier = Modifier.weight(2f),
                                    shape = MaterialTheme.shapes.medium
                                )

                                OutlinedTextField(
                                    value = proxyPortStr,
                                    onValueChange = { proxyPortStr = it },
                                    label = { Text("Порт") },
                                    placeholder = { Text("1080") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.medium
                                )
                            }

                            OutlinedTextField(
                                value = proxyUser,
                                onValueChange = { proxyUser = it },
                                label = { Text("Логин (опционально)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium
                            )

                            OutlinedTextField(
                                value = proxyPass,
                                onValueChange = { proxyPass = it },
                                label = { Text("Пароль (опционально)") },
                                visualTransformation = if (showProxyPass) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showProxyPass = !showProxyPass }) {
                                        Icon(
                                            imageVector = if (showProxyPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = null
                                        )
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium
                            )
                        }
                    }
                }
            }

            // 5. ОБНОВЛЕНИЕ ПРИЛОЖЕНИЯ (GITHUB OTA)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Обновление AliveChat",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Текущая версия: v${BuildConfig.VERSION_NAME}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = { scope.launch { updateRepo.checkForUpdates() } },
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text("Проверить")
                        }
                    }

                    when (val s = updateState) {
                        is UpdateState.Checking -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text(
                                    text = "Поиск обновлений на GitHub...",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        is UpdateState.UpToDate -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981))
                                )
                                Text(
                                    text = "У вас установлена последняя версия",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        is UpdateState.Available -> {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Доступна новая версия: ${s.info.tagName}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = s.info.changelog,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Button(
                                        onClick = { scope.launch { updateRepo.downloadUpdate(s.info) } },
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text("Скачать и установить")
                                    }
                                }
                            }
                        }

                        is UpdateState.Downloading -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Загрузка обновления: ${(s.progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                LinearProgressIndicator(
                                    progress = { s.progress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        is UpdateState.ReadyToInstall -> {
                            Button(
                                onClick = { updateRepo.installApk(s.apkFile) },
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.DownloadDone, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Установить обновление")
                            }
                        }

                        is UpdateState.Error -> {
                            Text(
                                text = "Ошибка: ${s.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        is UpdateState.Idle -> {}
                    }
                }
            }

            // 6. ИМЯ ИГРОКА
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Профиль пользователя",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedTextField(
                        value = userName,
                        onValueChange = { userName = it },
                        label = { Text("Ваше имя в диалогах") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { onSaveAndClose(buildCurrentSettings()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Сохранить и закрыть", fontSize = 16.sp)
            }
        }
    }
}
