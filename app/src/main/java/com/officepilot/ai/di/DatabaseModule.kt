package com.officepilot.ai.di

import com.officepilot.ai.data.local.AppDatabase
import com.officepilot.ai.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides fun convDao(db: AppDatabase): ConversationDao = db.conversationDao()
    @Provides fun msgDao(db: AppDatabase): MessageDao = db.messageDao()
    @Provides fun fileDao(db: AppDatabase): FileDao = db.fileDao()
}
