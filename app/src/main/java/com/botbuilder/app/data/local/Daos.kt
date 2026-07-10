package com.botbuilder.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ReplyRuleDao {
    @Query("SELECT * FROM reply_rules ORDER BY priority ASC")
    fun observeAll(): Flow<List<ReplyRule>>

    @Query("SELECT * FROM reply_rules ORDER BY priority ASC")
    suspend fun getAllOnce(): List<ReplyRule>

    @Query("SELECT COUNT(*) FROM reply_rules")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: ReplyRule): Long

    @Delete
    suspend fun delete(rule: ReplyRule)

    @Query("DELETE FROM reply_rules")
    suspend fun deleteAll()
}

@Dao
interface ConversationLogDao {
    @Query("SELECT * FROM conversation_logs ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<ConversationLog>>

    @Query("SELECT COUNT(*) FROM conversation_logs WHERE timestamp >= :since")
    suspend fun countSince(since: Long): Int

    @Insert
    suspend fun insert(log: ConversationLog): Long

    @Query("DELETE FROM conversation_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM conversation_logs")
    suspend fun deleteAll()

    @Query("SELECT * FROM conversation_logs WHERE incomingMessage LIKE '%' || :q || '%' OR botReply LIKE '%' || :q || '%' ORDER BY timestamp DESC")
    suspend fun search(q: String): List<ConversationLog>
}

@Dao
interface KnownContactDao {
    @Query("SELECT * FROM known_contacts")
    suspend fun getAll(): List<KnownContact>

    @Query("SELECT COUNT(*) FROM known_contacts")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(contact: KnownContact)
}
