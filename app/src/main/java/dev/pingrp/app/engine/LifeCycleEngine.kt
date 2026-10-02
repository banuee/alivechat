package dev.pingrp.app.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.pingrp.app.data.api.AIApiService
import dev.pingrp.app.data.api.TriggerReason
import dev.pingrp.app.data.db.PingDatabaseHelper
import dev.pingrp.app.data.model.CharacterOnlineStatus
import dev.pingrp.app.data.model.Message
import dev.pingrp.app.data.model.MessageStatus
import dev.pingrp.app.data.model.SenderType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LifeCycleEngine(
    private val context: Context,
    private val dbHelper: PingDatabaseHelper,
    private val aiService: AIApiService,
    private val notificationHelper: NotificationHelper
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    @Volatile
    var currentlyOpenedCharacterId: String? = null

    fun onUserSentMessage(characterId: String, text: String) {
        scope.launch {
            cancelAlarm(characterId)

            val userMsg = Message(
                characterId = characterId,
                sender = SenderType.USER,
                text = text,
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.SENT
            )
            dbHelper.insertMessage(userMsg)

            // Simulate delivery & reading
            delay(600)
            dbHelper.markUserMessagesAsSeen(characterId)

            generateAndDeliverReply(characterId, TriggerReason.USER_MESSAGE)
        }
    }

    fun retry(characterId: String) {
        scope.launch {
            dbHelper.updateCharacterError(characterId, null)
            generateAndDeliverReply(characterId, TriggerReason.USER_MESSAGE)
        }
    }

    private suspend fun generateAndDeliverReply(characterId: String, trigger: TriggerReason) {
        val character = dbHelper.getCharacter(characterId) ?: return

        // Set status to typing
        dbHelper.updateCharacterStatus(characterId, CharacterOnlineStatus.TYPING)
        val readingDelay = ((1000..2000).random() * character.replyDelayMultiplier).toLong()
        delay(readingDelay)

        val history = dbHelper.getMessagesForCharacter(characterId)
        val result = aiService.generateResponse(
            character = character,
            history = history,
            trigger = trigger
        )

        result.onSuccess { plan ->
            dbHelper.updateCharacterError(characterId, null)

            // Process AI messages sequentially with typing delays
            for (i in plan.messages.indices) {
                dbHelper.updateCharacterStatus(characterId, CharacterOnlineStatus.TYPING)
                val delaySec = plan.typingDelaysSec.getOrElse(i) { 2 }
                val typingTimeMs = (delaySec * 1000L * character.replyDelayMultiplier).toLong().coerceIn(800L, 6000L)
                delay(typingTimeMs)

                val msgText = plan.messages[i]
                val charMsg = Message(
                    characterId = characterId,
                    sender = SenderType.CHARACTER,
                    text = msgText,
                    timestamp = System.currentTimeMillis(),
                    status = if (currentlyOpenedCharacterId == characterId) MessageStatus.SEEN else MessageStatus.DELIVERED,
                    isThought = false,
                    thoughtText = if (i == 0 && plan.innerThought.isNotBlank()) plan.innerThought else null
                )
                dbHelper.insertMessage(charMsg)

                if (currentlyOpenedCharacterId != characterId) {
                    notificationHelper.showMessageNotification(
                        characterId = characterId,
                        characterName = character.name,
                        messageText = msgText
                    )
                }
            }

            // Set final status and schedule next check
            val nextCheckSec = plan.nextCheckInSeconds.coerceIn(20, 1800)
            val nextCheckTime = System.currentTimeMillis() + (nextCheckSec * 1000L)
            dbHelper.updateCharacterStatus(characterId, plan.newStatus, nextCheckTime)
            scheduleAlarm(characterId, nextCheckTime)
        }.onFailure { error ->
            error.printStackTrace()
            // Honestly store error and show in UI
            dbHelper.updateCharacterError(characterId, error.message ?: "Неизвестная ошибка сети")
            dbHelper.updateCharacterStatus(characterId, CharacterOnlineStatus.OFFLINE)
        }
    }

    suspend fun onTimerTriggered(characterId: String) = withContext(Dispatchers.IO) {
        val character = dbHelper.getCharacter(characterId) ?: return@withContext
        val history = dbHelper.getMessagesForCharacter(characterId)
        val lastMsg = history.lastOrNull() ?: return@withContext

        val now = System.currentTimeMillis()
        val elapsedMinutes = ((now - lastMsg.timestamp) / 60000).coerceAtLeast(1)

        val triggerReason = if (lastMsg.sender == SenderType.CHARACTER) {
            TriggerReason.SCHEDULED_TIMER
        } else {
            TriggerReason.USER_MESSAGE
        }

        val result = aiService.generateResponse(
            character = character,
            history = history,
            trigger = triggerReason,
            lastSeenMinutesAgo = elapsedMinutes
        )

        result.onSuccess { plan ->
            dbHelper.updateCharacterError(characterId, null)

            if (plan.messages.isNotEmpty()) {
                dbHelper.updateCharacterStatus(characterId, CharacterOnlineStatus.TYPING)
                delay(1200)

                for (i in plan.messages.indices) {
                    val delaySec = plan.typingDelaysSec.getOrElse(i) { 2 }
                    delay((delaySec * 1000L).coerceIn(800L, 5000L))

                    val msgText = plan.messages[i]
                    val charMsg = Message(
                        characterId = characterId,
                        sender = SenderType.CHARACTER,
                        text = msgText,
                        timestamp = System.currentTimeMillis(),
                        status = if (currentlyOpenedCharacterId == characterId) MessageStatus.SEEN else MessageStatus.DELIVERED,
                        isThought = false,
                        thoughtText = if (i == 0 && plan.innerThought.isNotBlank()) plan.innerThought else null
                    )
                    dbHelper.insertMessage(charMsg)

                    if (currentlyOpenedCharacterId != characterId) {
                        notificationHelper.showMessageNotification(
                            characterId = characterId,
                            characterName = character.name,
                            messageText = msgText
                        )
                    }
                }
            }

            val nextCheckSec = plan.nextCheckInSeconds.coerceIn(30, 3600)
            val nextCheckTime = System.currentTimeMillis() + (nextCheckSec * 1000L)
            dbHelper.updateCharacterStatus(characterId, plan.newStatus, nextCheckTime)
            scheduleAlarm(characterId, nextCheckTime)
        }.onFailure { error ->
            error.printStackTrace()
            dbHelper.updateCharacterError(characterId, error.message)
            dbHelper.updateCharacterStatus(characterId, CharacterOnlineStatus.OFFLINE)
        }
    }

    private fun scheduleAlarm(characterId: String, triggerAtMillis: Long) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "dev.pingrp.app.ACTION_CHECK_CHAT"
            putExtra("EXTRA_CHARACTER_ID", characterId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            characterId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    private fun cancelAlarm(characterId: String) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "dev.pingrp.app.ACTION_CHECK_CHAT"
            putExtra("EXTRA_CHARACTER_ID", characterId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            characterId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
