package dev.pingrp.app.data.api

import dev.pingrp.app.data.model.AIPlanResponse
import dev.pingrp.app.data.model.ApiProvider
import dev.pingrp.app.data.model.Character
import dev.pingrp.app.data.model.CharacterOnlineStatus
import dev.pingrp.app.data.model.Message
import dev.pingrp.app.data.model.SenderType
import dev.pingrp.app.data.prefs.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.util.concurrent.TimeUnit

enum class TriggerReason {
    USER_MESSAGE,
    SCHEDULED_TIMER,
    USER_SEEN_AND_SILENT
}

class AIApiService(private val settingsManager: SettingsManager) {

    private fun getHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)

        if (settingsManager.isProxyEnabled() && settingsManager.getProxyHost().isNotBlank()) {
            val host = settingsManager.getProxyHost().trim()
            val port = settingsManager.getProxyPort()
            val user = settingsManager.getProxyUser().trim()
            val pass = settingsManager.getProxyPassword().trim()

            val type = if (settingsManager.getProxyType().equals("SOCKS", ignoreCase = true)) {
                Proxy.Type.SOCKS
            } else {
                Proxy.Type.HTTP
            }

            if (user.isNotBlank() && pass.isNotBlank()) {
                java.net.Authenticator.setDefault(object : java.net.Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(user, pass.toCharArray())
                    }
                })

                builder.proxyAuthenticator { _, response ->
                    val credential = okhttp3.Credentials.basic(user, pass)
                    response.request.newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build()
                }
            }

            try {
                val sa = InetSocketAddress(host, port)
                builder.proxy(Proxy(type, sa))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return builder.build()
    }

    private fun resolveGroqUrl(): String {
        var base = settingsManager.getGroqBaseUrl().trim().trimEnd('/')
        if (settingsManager.isProxyEnabled() && (base.contains("workers.dev") || base.contains("vercel.app"))) {
            base = "https://api.groq.com"
        }
        return if (base.endsWith("/openai/v1/chat/completions")) base else "$base/openai/v1/chat/completions"
    }

    private fun resolveGeminiUrl(model: String, apiKey: String): String {
        var base = settingsManager.getGeminiBaseUrl().trim().trimEnd('/')
        if (settingsManager.isProxyEnabled() && (base.contains("workers.dev") || base.contains("vercel.app"))) {
            base = "https://generativelanguage.googleapis.com"
        }
        return if (base.contains("generateContent")) {
            if (!base.contains("key=")) "$base?key=$apiKey" else base
        } else {
            "$base/v1beta/models/$model:generateContent?key=$apiKey"
        }
    }

    suspend fun generateResponse(
        character: Character,
        history: List<Message>,
        trigger: TriggerReason,
        lastSeenMinutesAgo: Long = 0L
    ): Result<AIPlanResponse> = withContext(Dispatchers.IO) {
        val provider = character.provider
        val prompt = buildSystemPrompt(character, trigger, lastSeenMinutesAgo)

        try {
            val result = when (provider) {
                ApiProvider.GROQ -> callGroq(character, history, prompt)
                ApiProvider.GEMINI -> callGemini(character, history, prompt)
            }
            Result.success(result)
        } catch (e: Exception) {
            e.printStackTrace()
            // Try fallback provider automatically
            try {
                val fallbackResult = when (provider) {
                    ApiProvider.GROQ -> callGemini(character, history, prompt)
                    ApiProvider.GEMINI -> callGroq(character, history, prompt)
                }
                Result.success(fallbackResult)
            } catch (fallbackEx: Exception) {
                fallbackEx.printStackTrace()
                val friendlyMessage = formatErrorMessage(e)
                Result.failure(RuntimeException(friendlyMessage, e))
            }
        }
    }

    private fun formatErrorMessage(e: Throwable): String {
        val msg = e.message ?: e.toString()
        return when {
            msg.contains("403") || msg.contains("Location not supported") || msg.contains("User location is not supported") ->
                "Ошибка 403: Регион заблокирован (Беларусь/РФ). Включите SOCKS5 прокси в настройках!"
            msg.contains("429") || msg.contains("Rate limit") || msg.contains("Resource has been exhausted") ->
                "Ошибка 429: Превышен лимит запросов в минуту. Подождите 30-60 секунд."
            msg.contains("503") || msg.contains("high demand") || msg.contains("UNAVAILABLE") ->
                "Ошибка 503: Модель временно перегружена Google. Попробуйте gemini-3.5-flash-lite или Groq."
            msg.contains("401") || msg.contains("API key") ->
                "Ошибка 401: Неверный API ключ. Проверьте настройки ключа."
            msg.contains("404") || msg.contains("is no longer available") ->
                "Ошибка модели: модель устарела. Используйте qwen/qwen3.8-27b или gemini-3.5-flash-lite."
            msg.contains("Failed to connect") || msg.contains("ConnectException") || msg.contains("timeout") ->
                "Ошибка сети/таймаут: сервер недоступен. Проверьте SOCKS5 прокси или интернет."
            else -> "Ошибка: $msg"
        }
    }

    private fun buildSystemPrompt(character: Character, trigger: TriggerReason, lastSeenMinutesAgo: Long): String {
        val triggerContext = when (trigger) {
            TriggerReason.USER_MESSAGE -> "Пользователь только что прислал сообщение. Ответь естественно."
            TriggerReason.SCHEDULED_TIMER -> "Прошло некоторое время ($lastSeenMinutesAgo мин), пользователь тебе пока не ответил. Ты сам решил проверить чат. Ты можешь написать что-то вдогонку, спросить куда он пропал, скинуть новую мысль, или решить помолчать (оставить messages пустым [])."
            TriggerReason.USER_SEEN_AND_SILENT -> "Пользователь прочитал твоё последнее сообщение $lastSeenMinutesAgo мин назад, но ничего не написал! Твоя реакция зависит от характера (обидеться, подколоть, или проигнорировать)."
        }

        return """
            Ты играешь роль в реалистичном чате мессенджера.
            Твое имя: ${character.name}
            Твоя личность и стиль: ${character.personality}
            Сценарий/контекст: ${character.scenario}
            Твой уровень настойчивости/привязчивости: ${character.patienceLevel} из 5 (1 - редко пишешь первым, 5 - часто строчишь сообщения и требуешь внимания).

            ТЕКУЩИЙ КОНТЕКСТ:
            $triggerContext

            ПРАВИЛА ОБЩЕНИЯ В МЕССЕНДЖЕРЕ:
            1. Пиши как живой человек в Telegram: живой язык, эмодзи по вкусу, сленг/сокращения если подходят персонажу.
            2. НЕ пиши огромные литературные простыни! Люди пишут короткими фразами, иногда разбивая мысль на 2-3 сообщения.
            3. Если ты занят(а) или уходишь, смени свой статус на 'AWAY', 'BUSY' или 'OFFLINE' и напиши об этом.
            4. Выставь таймер 'next_check_in_seconds' (от 30 до 900 секунд) — через сколько секунд ты снова заглянешь в телефон и проверишь чат, если собеседник молчит.

            ОБЯЗАТЕЛЬНЫЙ ФОРМАТ ОТВЕТА (ТОЛЬКО ЧИСТЫЙ JSON БЕЗ MARKDOWN):
            {
              "inner_thought": "Твоя краткая скрытая мысль (что ты на самом деле думаешь о собеседнике или ситуации)",
              "messages": ["текст первого сообщения", "текст второго сообщения (опционально)"],
              "typing_delays_sec": [2, 3],
              "character_status": "ONLINE",
              "next_check_in_seconds": 120,
              "action_if_no_reply": "ping_again"
            }
            Допустимые значения character_status: ONLINE, TYPING, AWAY, OFFLINE, BUSY.
        """.trimIndent()
    }

    private fun callGroq(character: Character, history: List<Message>, systemPrompt: String): AIPlanResponse {
        val apiKey = settingsManager.getGroqKey()
        if (apiKey.isBlank()) {
            throw IllegalStateException("API ключ Groq не указан. Перейдите в Настройки и укажите ваш ключ.")
        }
        val model = if (character.modelName.isNotBlank() && !character.modelName.contains("gemini")) character.modelName else "qwen/qwen3.8-27b"

        val jsonBody = JSONObject().apply {
            put("model", model)
            put("temperature", 0.8)
            put("max_tokens", 350)
            put("response_format", JSONObject().put("type", "json_object"))

            val messagesArr = JSONArray()
            messagesArr.put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })

            val recentHistory = history.takeLast(15)
            for (msg in recentHistory) {
                if (msg.isThought) continue
                messagesArr.put(JSONObject().apply {
                    put("role", if (msg.sender == SenderType.USER) "user" else "assistant")
                    put("content", msg.text)
                })
            }

            // Explicit reminder to guarantee JSON output
            messagesArr.put(JSONObject().apply {
                put("role", "system")
                put("content", "Respond ONLY with a valid JSON object matching the required schema: {\"inner_thought\":\"...\",\"messages\":[\"...\"],\"typing_delays_sec\":[2],\"character_status\":\"ONLINE\",\"next_check_in_seconds\":120,\"action_if_no_reply\":\"wait\"}")
            })

            put("messages", messagesArr)
        }

        val url = resolveGroqUrl()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        getHttpClient().newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: throw RuntimeException("Empty response from Groq")
            if (!response.isSuccessful) {
                try {
                    val errRoot = JSONObject(responseBody)
                    val errObj = errRoot.optJSONObject("error")
                    val failedGen = errObj?.optString("failed_generation", "") ?: ""
                    if (failedGen.isNotBlank()) {
                        return parseAiJsonResponse(failedGen)
                    }
                } catch (_: Exception) {}

                throw RuntimeException("Groq error (${response.code}): $responseBody")
            }
            val root = JSONObject(responseBody)
            val choices = root.getJSONArray("choices")
            val content = choices.getJSONObject(0).getJSONObject("message").getString("content")
            return parseAiJsonResponse(content)
        }
    }

    private fun callGemini(character: Character, history: List<Message>, systemPrompt: String): AIPlanResponse {
        val apiKey = settingsManager.getGeminiKey()
        if (apiKey.isBlank()) {
            throw IllegalStateException("API ключ Gemini не указан. Перейдите в Настройки и укажите ваш ключ.")
        }
        val model = if (character.modelName.isNotBlank() && character.modelName.contains("gemini")) {
            character.modelName
        } else {
            "gemini-3.5-flash-lite"
        }

        val jsonBody = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
            })

            val generationConfig = JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.85)
                put("maxOutputTokens", 400)
            }
            put("generationConfig", generationConfig)

            val contents = JSONArray()
            val recentHistory = history.takeLast(15)
            for (msg in recentHistory) {
                if (msg.isThought) continue
                contents.put(JSONObject().apply {
                    put("role", if (msg.sender == SenderType.USER) "user" else "model")
                    put("parts", JSONArray().put(JSONObject().put("text", msg.text)))
                })
            }
            if (contents.length() == 0) {
                contents.put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", "Привет!")))
                })
            }
            put("contents", contents)
        }

        val url = resolveGeminiUrl(model, apiKey)

        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        getHttpClient().newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: throw RuntimeException("Empty response from Gemini")
            if (!response.isSuccessful) {
                throw RuntimeException("Gemini error (${response.code}): $responseBody")
            }
            val root = JSONObject(responseBody)
            val candidates = root.getJSONArray("candidates")
            val parts = candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts")
            val text = parts.getJSONObject(0).getString("text")
            return parseAiJsonResponse(text)
        }
    }

    suspend fun testConnection(provider: ApiProvider): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            when (provider) {
                ApiProvider.GROQ -> {
                    val apiKey = settingsManager.getGroqKey()
                    if (apiKey.isBlank()) {
                        return@withContext Pair(false, "API ключ Groq не указан. Введите ключ выше.")
                    }
                    val url = resolveGroqUrl()
                    val json = JSONObject().apply {
                        put("model", "qwen/qwen3.8-27b")
                        put("max_tokens", 5)
                        put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "ping")))
                    }
                    val req = Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer $apiKey")
                        .addHeader("Content-Type", "application/json")
                        .post(json.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    getHttpClient().newCall(req).execute().use { resp ->
                        val body = resp.body?.string() ?: ""
                        if (resp.isSuccessful) {
                            Pair(true, "Groq доступен! Ответ успешно получен.")
                        } else {
                            Pair(false, formatErrorMessage(RuntimeException("Groq (${resp.code}): $body")))
                        }
                    }
                }
                ApiProvider.GEMINI -> {
                    val apiKey = settingsManager.getGeminiKey()
                    if (apiKey.isBlank()) {
                        return@withContext Pair(false, "API ключ Gemini не указан. Введите ключ выше.")
                    }
                    val url = resolveGeminiUrl("gemini-3.5-flash-lite", apiKey)
                    val json = JSONObject().apply {
                        val contents = JSONArray().put(
                            JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", "ping")))
                        )
                        put("contents", contents)
                        put("generationConfig", JSONObject().put("maxOutputTokens", 5))
                    }
                    val req = Request.Builder()
                        .url(url)
                        .addHeader("Content-Type", "application/json")
                        .post(json.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    getHttpClient().newCall(req).execute().use { resp ->
                        val body = resp.body?.string() ?: ""
                        if (resp.isSuccessful) {
                            Pair(true, "Gemini доступен! Ответ успешно получен.")
                        } else {
                            Pair(false, formatErrorMessage(RuntimeException("Gemini (${resp.code}): $body")))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, formatErrorMessage(e))
        }
    }

    private fun parseAiJsonResponse(rawJson: String): AIPlanResponse {
        try {
            var clean = rawJson.trim()
            if (clean.startsWith("```json")) {
                clean = clean.removePrefix("```json")
            }
            if (clean.startsWith("```")) {
                clean = clean.removePrefix("```")
            }
            if (clean.endsWith("```")) {
                clean = clean.removeSuffix("```")
            }
            clean = clean.trim()

            val obj = JSONObject(clean)
            val innerThought = obj.optString("inner_thought", "")
            val messagesList = mutableListOf<String>()
            val delaysList = mutableListOf<Int>()

            val msgsJson = obj.optJSONArray("messages")
            if (msgsJson != null) {
                for (i in 0 until msgsJson.length()) {
                    val m = msgsJson.optString(i, "").trim()
                    if (m.isNotEmpty()) {
                        messagesList.add(m)
                    }
                }
            } else if (obj.has("message")) {
                val single = obj.optString("message", "").trim()
                if (single.isNotEmpty()) messagesList.add(single)
            }

            val delaysJson = obj.optJSONArray("typing_delays_sec")
            if (delaysJson != null) {
                for (i in 0 until delaysJson.length()) {
                    delaysList.add(delaysJson.optInt(i, 2))
                }
            } else {
                messagesList.forEach { delaysList.add(2) }
            }

            val statusStr = obj.optString("character_status", "ONLINE").uppercase()
            val newStatus = try {
                CharacterOnlineStatus.valueOf(statusStr)
            } catch (_: Exception) {
                CharacterOnlineStatus.ONLINE
            }

            val nextCheckInSeconds = obj.optInt("next_check_in_seconds", 180)
            val actionIfNoReply = obj.optString("action_if_no_reply", "wait")

            return AIPlanResponse(
                innerThought = innerThought,
                messages = messagesList,
                typingDelaysSec = delaysList,
                newStatus = newStatus,
                nextCheckInSeconds = nextCheckInSeconds.coerceIn(15, 3600),
                actionIfNoReply = actionIfNoReply
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return AIPlanResponse(
                innerThought = "Сбой парсинга JSON: ${e.message}",
                messages = listOf(rawJson.take(200)),
                typingDelaysSec = listOf(1),
                newStatus = CharacterOnlineStatus.ONLINE,
                nextCheckInSeconds = 120,
                actionIfNoReply = "wait"
            )
        }
    }
}
