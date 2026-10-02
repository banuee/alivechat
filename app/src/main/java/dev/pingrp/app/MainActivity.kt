package dev.pingrp.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import dev.pingrp.app.data.model.Character
import dev.pingrp.app.ui.screens.*
import dev.pingrp.app.ui.theme.PingTheme

sealed class Screen {
    object ChatList : Screen()
    data class Chat(val characterId: String) : Screen()
    data class CharacterDetail(val characterId: String? = null) : Screen()
    object Settings : Screen()
}

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    private var initialCharacterId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        handleIntent(intent)

        val app = application as PingApp

        setContent {
            PingTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var currentScreen by remember {
                        mutableStateOf<Screen>(
                            initialCharacterId?.let { Screen.Chat(it) } ?: Screen.ChatList
                        )
                    }

                    // Observe database updates
                    val dbUpdateTrigger by app.dbHelper.databaseUpdates.collectAsState()
                    val characters = remember(dbUpdateTrigger) {
                        app.dbHelper.getAllCharacters()
                    }

                    // Track opened chat for notification suppression and seen marking
                    DisposableEffect(currentScreen) {
                        val charId = (currentScreen as? Screen.Chat)?.characterId
                        app.lifeCycleEngine.currentlyOpenedCharacterId = charId
                        if (charId != null) {
                            app.dbHelper.markMessagesAsSeen(charId)
                        }
                        onDispose {
                            app.lifeCycleEngine.currentlyOpenedCharacterId = null
                        }
                    }

                    when (val screen = currentScreen) {
                        is Screen.ChatList -> {
                            ChatListScreen(
                                characters = characters,
                                onCharacterClick = { charId ->
                                    currentScreen = Screen.Chat(charId)
                                },
                                onAddCharacterClick = {
                                    currentScreen = Screen.CharacterDetail(null)
                                },
                                onSettingsClick = {
                                    currentScreen = Screen.Settings
                                },
                                onDeleteCharacter = { charId ->
                                    app.dbHelper.deleteCharacter(charId)
                                }
                            )
                        }

                        is Screen.Chat -> {
                            val character = remember(screen.characterId, dbUpdateTrigger) {
                                app.dbHelper.getCharacter(screen.characterId)
                            }
                            val messages = remember(screen.characterId, dbUpdateTrigger) {
                                app.dbHelper.getMessagesForCharacter(screen.characterId)
                            }

                            if (character != null) {
                                ChatScreen(
                                    character = character,
                                    messages = messages,
                                    onBackClick = { currentScreen = Screen.ChatList },
                                    onCharacterInfoClick = {
                                        currentScreen = Screen.CharacterDetail(character.id)
                                    },
                                    onOpenSettings = {
                                        currentScreen = Screen.Settings
                                    },
                                    onRetry = {
                                        app.lifeCycleEngine.retry(character.id)
                                    },
                                    onSendMessage = { text ->
                                        app.lifeCycleEngine.onUserSentMessage(character.id, text)
                                    }
                                )
                            } else {
                                currentScreen = Screen.ChatList
                            }
                        }

                        is Screen.CharacterDetail -> {
                            val character = remember(screen.characterId) {
                                screen.characterId?.let { app.dbHelper.getCharacter(it) }
                            }

                            CharacterDetailScreen(
                                character = character,
                                onBackClick = {
                                    currentScreen = if (screen.characterId != null) {
                                        Screen.Chat(screen.characterId)
                                    } else {
                                        Screen.ChatList
                                    }
                                },
                                onSaveCharacter = { updatedChar ->
                                    if (screen.characterId == null) {
                                        app.dbHelper.insertCharacter(updatedChar)
                                        currentScreen = Screen.Chat(updatedChar.id)
                                    } else {
                                        app.dbHelper.updateCharacter(updatedChar)
                                        currentScreen = Screen.Chat(updatedChar.id)
                                    }
                                }
                            )
                        }

                        is Screen.Settings -> {
                            val settings by app.settingsManager.settingsFlow.collectAsState()

                            SettingsScreen(
                                currentSettings = settings,
                                aiService = app.aiService,
                                updateRepo = app.updateRepository,
                                onBackClick = { currentScreen = Screen.ChatList },
                                onSaveSettings = { newSettings ->
                                    app.settingsManager.saveSettings(newSettings)
                                },
                                onSaveAndClose = { newSettings ->
                                    app.settingsManager.saveSettings(newSettings)
                                    currentScreen = Screen.ChatList
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val targetCharId = intent?.getStringExtra("OPEN_CHARACTER_ID")
        if (targetCharId != null) {
            initialCharacterId = targetCharId
        }
    }
}
