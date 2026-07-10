package com.botbuilder.app.data.repository

import com.botbuilder.app.data.local.*
import com.botbuilder.app.data.remote.*

class BotRepository(
    private val db: AppDatabase,
    private val secureStore: SecureStore,
    private val telegramApi: TelegramApi = TelegramApi.create(),
    private val aiProvider: AiProvider = AiProviderRouter()
) {
    private var lastUpdateOffset: Long? = null

    /** Fetches new updates via long polling. Call in a loop from the foreground service. */
    suspend fun pollOnce(): List<TgUpdate> {
        val token = secureStore.botToken ?: return emptyList()
        val response = telegramApi.getUpdates(token, lastUpdateOffset)
        val updates = response.result.orEmpty()
        if (updates.isNotEmpty()) {
            lastUpdateOffset = updates.maxOf { it.updateId } + 1
        }
        return updates
    }

    /** Full pipeline: match rules -> AI fallback -> send -> log. */
    suspend fun handleUpdate(update: TgUpdate) {
        val message = update.message ?: return
        val text = message.text ?: return
        val chatId = message.chat.id
        val username = message.chat.username ?: message.chat.firstName

        // Track contact for broadcast eligibility
        db.knownContactDao().upsert(KnownContact(chatId = chatId, username = username, lastSeen = System.currentTimeMillis()))

        val rules = db.replyRuleDao().getAllOnce()
        val matchedRule = rules.firstOrNull { it.matches(text) }

        val (replyText, usedAi) = when {
            matchedRule != null -> matchedRule.answer to false
            secureStore.aiEnabled -> {
                val aiReply = callAi(text, rules)
                (aiReply ?: "Sorry, I didn't understand that.") to (aiReply != null)
            }
            else -> "Sorry, I didn't understand that." to false
        }

        val token = secureStore.botToken ?: return
        telegramApi.sendMessage(token, SendMessageBody(chatId = chatId, text = replyText))

        db.conversationLogDao().insert(
            ConversationLog(
                chatId = chatId,
                username = username,
                incomingMessage = text,
                botReply = replyText,
                repliedByAi = usedAi
            )
        )
    }

    /** Builds the AI system prompt by appending the user's saved reply rules as reference data,
     *  so AI answers (e.g. prices) stay in sync with what the user already configured. */
    private fun buildSystemPrompt(baseRules: List<ReplyRule>): String {
        val base = secureStore.aiSystemPrompt ?: "You are a helpful assistant."
        if (baseRules.isEmpty()) return base

        val reference = baseRules.joinToString("\n") { rule ->
            "- ${rule.label}: ${rule.answer}"
        }
        return """
            $base

            Reference information — use this to answer accurately when relevant:
            $reference

            Only use the above when relevant. If the question isn't covered, answer naturally and helpfully.
        """.trimIndent()
    }

    private suspend fun callAi(userMessage: String, rules: List<ReplyRule>): String? {
        val apiKey = secureStore.aiApiKey ?: return null
        val config = AiConfig(
            provider = secureStore.aiProvider ?: "gemini",
            apiKey = apiKey,
            baseUrl = secureStore.aiBaseUrl,
            model = secureStore.aiModel ?: defaultModelFor(secureStore.aiProvider),
            temperature = secureStore.aiTemperature,
            maxTokens = secureStore.aiMaxTokens,
            systemPrompt = buildSystemPrompt(rules)
        )
        return aiProvider.getReply(userMessage, config).getOrNull()
    }

    private fun defaultModelFor(provider: String?): String = when (provider) {
        "openai" -> "gpt-4o-mini"
        "gemini" -> "gemini-1.5-flash"
        "anthropic" -> "claude-3-5-haiku-20241022"
        "openrouter" -> "openai/gpt-4o-mini"
        else -> "gpt-4o-mini"
    }
}
