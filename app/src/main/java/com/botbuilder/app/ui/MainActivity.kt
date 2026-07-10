package com.botbuilder.app.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.botbuilder.app.R
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        secureStore = SecureStore(applicationContext)
        db = AppDatabase.getInstance(applicationContext)

        binding.buttonSaveToken.setOnClickListener { saveToken() }
        binding.buttonStartBot.setOnClickListener { startBot() }
        binding.buttonStopBot.setOnClickListener { stopBot() }

        refreshStatus()
    }

    private fun saveToken() {
        val token = binding.editTextToken.text.toString().trim()
        val tokenPattern = Regex("""\d+:[A-Za-z0-9_-]{35}""")
        if (!tokenPattern.matches(token)) {
            binding.textStatus.text = "Invalid token format"
            return
        }
        lifecycleScope.launch {
            try {
                val api = TelegramApi.create()
                val me = api.getMe(token)
                if (me.ok && me.result != null) {
                    secureStore.botToken = token
                    binding.textStatus.text = "Connected as @${me.result.username}"
                } else {
                    binding.textStatus.text = "Telegram rejected this token"
                }
            } catch (e: Exception) {
                binding.textStatus.text = "Network error: ${e.message}"
            }
        }
    }

    private fun startBot() {
        val intent = Intent(this, BotPollingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        refreshStatus()
    }

    private fun stopBot() {
        stopService(Intent(this, BotPollingService::class.java))
        refreshStatus()
    }

    private fun refreshStatus() {
        lifecycleScope.launch {
            val ruleCount = db.replyRuleDao().count()
            binding.textRuleCount.text = "Auto-reply rules: $ruleCount"
            binding.textAiStatus.text = "AI mode: ${if (secureStore.aiEnabled) "ON" else "OFF"}"
        }
    }
}
