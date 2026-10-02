package dev.pingrp.app.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.pingrp.app.PingApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val characterId = intent.getStringExtra("EXTRA_CHARACTER_ID") ?: return
        val app = context.applicationContext as? PingApp ?: return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.lifeCycleEngine.onTimerTriggered(characterId)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
