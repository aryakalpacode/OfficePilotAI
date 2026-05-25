package com.officepilot.ai.data.local.dao

import androidx.room.*
import com.officepilot.ai.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Insert suspend fun insert(c: ConversationEntity): Long
    @Update suspend fun update(c: ConversationEntity)
    @Query("DELETE FROM conversations WHERE id = :id") suspend fun deleteById(id: Long)
    @Query("SELECT * FROM conversations ORDER BY updated_at DESC") fun getAll(): Flow<List<ConversationEntity>>
    @Query("SELECT * FROM conversations ORDER BY updated_at DESC LIMIT :n") fun getRecent(n: Int): Flow<List<ConversationEntity>>
    @Query("SELECT * FROM conversations WHERE id = :id") suspend fun getById(id: Long): ConversationEntity?
    @Query("UPDATE conversations SET updated_at = :t WHERE id = :id") suspend fun touch(id: Long, t: Long = System.currentTimeMillis())
    @Query("UPDATE conversations SET title = :title WHERE id = :id") suspend fun rename(id: Long, title: String)
    @Query("DELETE FROM conversations") suspend fun deleteAll()
}

@Dao
interface MessageDao {
    @Insert suspend fun insert(m: MessageEntity): Long
    @Query("SELECT * FROM messages WHERE conversation_id = :cid ORDER BY timestamp ASC") fun getByConversation(cid: Long): Flow<List<MessageEntity>>
    @Query("SELECT * FROM messages WHERE conversation_id = :cid ORDER BY timestamp ASC") suspend fun getByConversationSync(cid: Long): List<MessageEntity>
    @Query("DELETE FROM messages WHERE conversation_id = :cid") suspend fun deleteByConversation(cid: Long)
}

@Dao
interface FileDao {
    @Insert suspend fun insert(f: GeneratedFileEntity): Long
    @Query("SELECT * FROM generated_files ORDER BY created_at DESC") fun getAll(): Flow<List<GeneratedFileEntity>>
    @Query("SELECT * FROM generated_files WHERE conversation_id = :cid") fun getByConversation(cid: Long): Flow<List<GeneratedFileEntity>>
    @Query("DELETE FROM generated_files WHERE id = :id") suspend fun deleteById(id: Long)
}
