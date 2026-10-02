package dev.pingrp.app.data.model

import java.util.UUID

enum class SenderType {
    USER,
    CHARACTER,
    SYSTEM
}

enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    SEEN
}

enum class CharacterOnlineStatus {
    ONLINE,
    TYPING,
    AWAY,
    OFFLINE,
    BUSY
}

enum class ApiProvider {
    GROQ,
    GEMINI
}

data class Character(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val avatarUrl: String = "",
    val personality: String,
    val scenario: String = "",
    val onlineStatus: CharacterOnlineStatus = CharacterOnlineStatus.ONLINE,
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val nextCheckTimestamp: Long = 0L,
    val patienceLevel: Int = 3, // 1 (rarely pings) to 5 (clingy / frequent pings)
    val replyDelayMultiplier: Float = 1.0f,
    val provider: ApiProvider = ApiProvider.GROQ,
    val modelName: String = "qwen/qwen3.8-27b",
    val unreadCount: Int = 0,
    val lastMessageText: String = "",
    val lastMessageTime: Long = 0L,
    val lastErrorMessage: String? = null
)

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val characterId: String,
    val sender: SenderType,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENT,
    val isThought: Boolean = false,
    val thoughtText: String? = null
)

data class AIPlanResponse(
    val innerThought: String = "",
    val messages: List<String> = emptyList(),
    val typingDelaysSec: List<Int> = emptyList(),
    val newStatus: CharacterOnlineStatus = CharacterOnlineStatus.ONLINE,
    val nextCheckInSeconds: Int = 0,
    val actionIfNoReply: String = "wait"
)
