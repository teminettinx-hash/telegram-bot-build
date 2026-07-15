package com.botbuilder.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.botbuilder.app.data.local.AppDatabase
import com.botbuilder.app.data.local.SecureStore
import com.botbuilder.app.data.remote.TelegramApi
import com.botbuilder.app.databinding.ActivityMainBinding
import com.botbuilder.app.service.BotPollingService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var secureStore: SecureStore
    private lateinit var db: AppDatabase

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op: bot still works without notification permission, just less visible */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        try {
            secureStore = SecureStore(applicationContext)
            db = AppDatabase.getInstance(applicationContext)
        } catch (e: Exception) {
            // Surface init failures instead of the app silently doing nothing
            Log.e("MainActivity", "Init failed", e)
            Toast.makeText(this, "Startup error: ${e.message}", Toast.LENGTH_LONG).show()
        }

        requestNotificationPermissionIfNeeded()

        binding.buttonSaveToken.setOnClickListener { saveToken() }
        binding.buttonStartBot.setOnClickListener { startBot() }
        binding.buttonStopBot.setOnClickListener { stopBot() }

        // Restore saved token into the field so it's visible on relaunch
        secureStore.botToken?.let { binding.editTextToken.setText(it) }

        refreshStatus()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun saveToken() {
        val token = binding.editTextToken.text.toString().trim()
        val tokenPattern = Regex("""\d+:[A-Za-z0-9_-]{30,40}""")
        if (!tokenPattern.matches(token)) {
            setStatus("Invalid token format. It should look like 123456789:ABCdefGHIjklMNOpqrsTUVwxyz", isError = true)
            return
        }
        setStatus("Checking token with Telegram…", isError = false)
        lifecycleScope.launch {
            try {
                val api = TelegramApi.create()
                val me = api.getMe(token)
                if (me.ok && me.result != null) {
                    secureStore.botToken = token
                    setStatus("Connected as @${me.result.username}", isError = false)
                    Toast.makeText(this@MainActivity, "Bot token saved", Toast.LENGTH_SHORT).show()
                } else {
                    setStatus("Telegram rejected this token — double check it in BotFather", isError = true)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Token check failed", e)
                setStatus("Network error: ${e.message ?: "couldn't reach Telegram"}", isError = true)
            }
        }
    }

    private fun startBot() {
        if (secureStore.botToken.isNullOrBlank()) {
            setStatus("Save a valid bot token first", isError = true)
            return
        }
        try {
            val intent = Intent(this, BotPollingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            setStatus("Bot starting…", isError = false)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start service", e)
            setStatus("Couldn't start bot: ${e.message}", isError = true)
        }
        refreshStatus()
    }

    private fun stopBot() {
        stopService(Intent(this, BotPollingService::class.java))
        setStatus("Bot stopped", isError = false)
        refreshStatus()
    }

    private fun setStatus(text: String, isError: Boolean) {
        binding.textStatus.text = text
    }

    private fun refreshStatus() {
        lifecycleScope.launch {
            try {
                val ruleCount = db.replyRuleDao().count()
                binding.textRuleCount.text = ruleCount.toString()
                binding.textAiStatus.text = if (secureStore.aiEnabled) "ON" else "OFF"
            } catch (e: Exception) {
                Log.e("MainActivity", "Status refresh failed", e)
            }
        }
    }
}

