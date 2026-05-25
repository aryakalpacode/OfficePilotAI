package com.officepilot.ai.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.officepilot.ai.data.local.dao.*
import com.officepilot.ai.data.local.entity.*

@Database(
    entities = [ConversationEntity::class, MessageEntity::class, GeneratedFileEntity::class],
    version = 1, exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun fileDao(): FileDao
}
