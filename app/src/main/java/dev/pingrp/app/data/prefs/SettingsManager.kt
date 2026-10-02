package dev.pingrp.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import dev.pingrp.app.data.model.ApiProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("alivechat_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_GROQ_API_KEY = "groq_api_key"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_DEFAULT_PROVIDER = "default_provider"
        private const val KEY_GROQ_MODEL = "groq_model"
        private const val KEY_GEMINI_MODEL = "gemini_model"
        private const val KEY_USER_NAME = "user_name"

        private const val KEY_USE_PROXY = "use_proxy"
        private const val KEY_PROXY_TYPE = "proxy_type"
        private const val KEY_PROXY_HOST = "proxy_host"
        private const val KEY_PROXY_PORT = "proxy_port"
        private const val KEY_PROXY_USER = "proxy_user"
        private const val KEY_PROXY_PASS = "proxy_pass"
        private const val KEY_GROQ_BASE_URL = "groq_base_url"
        private const val KEY_GEMINI_BASE_URL = "gemini_base_url"

        // Чистые значения по умолчанию без чужих паролей и ключей
        const val DEFAULT_GROQ_KEY = ""
        const val DEFAULT_GEMINI_KEY = ""
        const val DEFAULT_GROQ_MODEL = "qwen/qwen3.8-27b"
        const val DEFAULT_GEMINI_MODEL = "gemini-3.5-flash-lite"

        // Официальные и прокси-эндпоинты
        const val VERCEL_PROXY_GROQ_BASE_URL = "https://ping-proxy-eta.vercel.app/groq"
        const val VERCEL_PROXY_GEMINI_BASE_URL = "https://ping-proxy-eta.vercel.app/gemini"
        const val OFFICIAL_GROQ_BASE_URL = "https://api.groq.com"
        const val OFFICIAL_GEMINI_BASE_URL = "https://generativelanguage.googleapis.com"

        // По умолчанию используется надежный Vercel Reverse Proxy, работающий без VPN
        const val DEFAULT_GROQ_BASE_URL = VERCEL_PROXY_GROQ_BASE_URL
        const val DEFAULT_GEMINI_BASE_URL = VERCEL_PROXY_GEMINI_BASE_URL
    }

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<AppSettings> = _settingsFlow.asStateFlow()

    data class AppSettings(
        val groqApiKey: String,
        val geminiApiKey: String,
        val defaultProvider: ApiProvider,
        val groqModel: String,
        val geminiModel: String,
        val userName: String,
        val useProxy: Boolean = false,
        val proxyType: String = "SOCKS", // "HTTP" or "SOCKS"
        val proxyHost: String = "",
        val proxyPort: Int = 1080,
        val proxyUser: String = "",
        val proxyPassword: String = "",
        val groqBaseUrl: String = DEFAULT_GROQ_BASE_URL,
        val geminiBaseUrl: String = DEFAULT_GEMINI_BASE_URL
    )

    private fun loadSettings(): AppSettings {
        val providerStr = prefs.getString(KEY_DEFAULT_PROVIDER, ApiProvider.GROQ.name) ?: ApiProvider.GROQ.name
        val provider = try {
            ApiProvider.valueOf(providerStr)
        } catch (_: Exception) {
            ApiProvider.GROQ
        }

        return AppSettings(
            groqApiKey = prefs.getString(KEY_GROQ_API_KEY, DEFAULT_GROQ_KEY) ?: DEFAULT_GROQ_KEY,
            geminiApiKey = prefs.getString(KEY_GEMINI_API_KEY, DEFAULT_GEMINI_KEY) ?: DEFAULT_GEMINI_KEY,
            defaultProvider = provider,
            groqModel = prefs.getString(KEY_GROQ_MODEL, DEFAULT_GROQ_MODEL) ?: DEFAULT_GROQ_MODEL,
            geminiModel = prefs.getString(KEY_GEMINI_MODEL, DEFAULT_GEMINI_MODEL) ?: DEFAULT_GEMINI_MODEL,
            userName = prefs.getString(KEY_USER_NAME, "Игрок") ?: "Игрок",
            useProxy = prefs.getBoolean(KEY_USE_PROXY, false),
            proxyType = prefs.getString(KEY_PROXY_TYPE, "SOCKS") ?: "SOCKS",
            proxyHost = prefs.getString(KEY_PROXY_HOST, "") ?: "",
            proxyPort = prefs.getInt(KEY_PROXY_PORT, 1080),
            proxyUser = prefs.getString(KEY_PROXY_USER, "") ?: "",
            proxyPassword = prefs.getString(KEY_PROXY_PASS, "") ?: "",
            groqBaseUrl = prefs.getString(KEY_GROQ_BASE_URL, DEFAULT_GROQ_BASE_URL) ?: DEFAULT_GROQ_BASE_URL,
            geminiBaseUrl = prefs.getString(KEY_GEMINI_BASE_URL, DEFAULT_GEMINI_BASE_URL) ?: DEFAULT_GEMINI_BASE_URL
        )
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_GROQ_API_KEY, settings.groqApiKey)
            .putString(KEY_GEMINI_API_KEY, settings.geminiApiKey)
            .putString(KEY_DEFAULT_PROVIDER, settings.defaultProvider.name)
            .putString(KEY_GROQ_MODEL, settings.groqModel)
            .putString(KEY_GEMINI_MODEL, settings.geminiModel)
            .putString(KEY_USER_NAME, settings.userName)
            .putBoolean(KEY_USE_PROXY, settings.useProxy)
            .putString(KEY_PROXY_TYPE, settings.proxyType)
            .putString(KEY_PROXY_HOST, settings.proxyHost)
            .putInt(KEY_PROXY_PORT, settings.proxyPort)
            .putString(KEY_PROXY_USER, settings.proxyUser)
            .putString(KEY_PROXY_PASS, settings.proxyPassword)
            .putString(KEY_GROQ_BASE_URL, settings.groqBaseUrl)
            .putString(KEY_GEMINI_BASE_URL, settings.geminiBaseUrl)
            .apply()

        _settingsFlow.value = settings
    }

    fun getGroqKey(): String = prefs.getString(KEY_GROQ_API_KEY, DEFAULT_GROQ_KEY) ?: DEFAULT_GROQ_KEY
    fun getGeminiKey(): String = prefs.getString(KEY_GEMINI_API_KEY, DEFAULT_GEMINI_KEY) ?: DEFAULT_GEMINI_KEY
    fun getUserName(): String = prefs.getString(KEY_USER_NAME, "Игрок") ?: "Игрок"
    fun getGroqBaseUrl(): String = prefs.getString(KEY_GROQ_BASE_URL, DEFAULT_GROQ_BASE_URL) ?: DEFAULT_GROQ_BASE_URL
    fun getGeminiBaseUrl(): String = prefs.getString(KEY_GEMINI_BASE_URL, DEFAULT_GEMINI_BASE_URL) ?: DEFAULT_GEMINI_BASE_URL

    fun isProxyEnabled(): Boolean = prefs.getBoolean(KEY_USE_PROXY, false)
    fun getProxyType(): String = prefs.getString(KEY_PROXY_TYPE, "SOCKS") ?: "SOCKS"
    fun getProxyHost(): String = prefs.getString(KEY_PROXY_HOST, "") ?: ""
    fun getProxyPort(): Int = prefs.getInt(KEY_PROXY_PORT, 1080)
    fun getProxyUser(): String = prefs.getString(KEY_PROXY_USER, "") ?: ""
    fun getProxyPassword(): String = prefs.getString(KEY_PROXY_PASS, "") ?: ""
}
