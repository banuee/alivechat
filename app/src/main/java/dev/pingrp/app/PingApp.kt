package dev.pingrp.app

import android.app.Application
import dev.pingrp.app.data.api.AIApiService
import dev.pingrp.app.data.db.PingDatabaseHelper
import dev.pingrp.app.data.prefs.SettingsManager
import dev.pingrp.app.data.updater.UpdateRepository
import dev.pingrp.app.engine.LifeCycleEngine
import dev.pingrp.app.engine.NotificationHelper

class PingApp : Application() {

    lateinit var settingsManager: SettingsManager
        private set
    lateinit var dbHelper: PingDatabaseHelper
        private set
    lateinit var aiService: AIApiService
        private set
    lateinit var notificationHelper: NotificationHelper
        private set
    lateinit var lifeCycleEngine: LifeCycleEngine
        private set
    lateinit var updateRepository: UpdateRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager(this)
        dbHelper = PingDatabaseHelper(this)
        aiService = AIApiService(settingsManager)
        notificationHelper = NotificationHelper(this)
        lifeCycleEngine = LifeCycleEngine(this, dbHelper, aiService, notificationHelper)
        updateRepository = UpdateRepository(this)
    }
}
