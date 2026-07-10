package com.botbuilder.app.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.botbuilder.app.data.local.AppDatabase
import com.botbuilder.app.data.local.SecureStore
import com.botbuilder.app.data.repository.BotRepository
import kotlinx.coroutines.*

class BotPollingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: BotRepository
    private var pollingJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getInstance(applicationContext)
        val secureStore = SecureStore(applicationContext)
        repository = BotRepository(db, secureStore)
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (pollingJob?.isActive != true) {
            pollingJob = scope.launch { pollLoop() }
        }
        return START_STICKY
    }

    private suspend fun CoroutineScope.pollLoop() {
        var consecutiveFailures = 0
        while (isActive) {
            try {
                val updates = repository.pollOnce()
                updates.forEach { repository.handleUpdate(it) }
                consecutiveFailures = 0
            } catch (e: Exception) {
                consecutiveFailures++
                // Exponential backoff on repeated failures, capped at 60s
                val backoffMs = (2000L * consecutiveFailures).coerceAtMost(60_000L)
                delay(backoffMs)
            }
        }
    }

    private fun buildNotification(): Notification {
        val channelId = "bot_polling_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Bot Running", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Bot is running")
            .setContentText("Listening for Telegram messages")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        pollingJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIF_ID = 1001
    }
}
