package com.officepilot.ai.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.officepilot.ai.data.local.AppDatabase
import com.officepilot.ai.data.remote.AiApi
import com.officepilot.ai.data.remote.DdgApi
import com.officepilot.ai.util.C
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(C.PREFS)

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDataStore(@ApplicationContext c: Context): DataStore<Preferences> = c.dataStore

    @Provides @Singleton
    fun provideDb(@ApplicationContext c: Context): AppDatabase =
        Room.databaseBuilder(c, AppDatabase::class.java, C.DB_NAME)
            .fallbackToDestructiveMigration().build()

    @Provides @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "OfficePilotAI/1.0")
                .build()
            chain.proceed(req)
        }
        .build()

    @Provides @Singleton @Named("gemini")
    fun provideGeminiApi(client: OkHttpClient): AiApi = Retrofit.Builder()
        .baseUrl(C.GEMINI_BASE).client(client)
        .addConverterFactory(GsonConverterFactory.create()).build()
        .create(AiApi::class.java)

    @Provides @Singleton @Named("groq")
    fun provideGroqApi(client: OkHttpClient): AiApi = Retrofit.Builder()
        .baseUrl(C.GROQ_BASE).client(client)
        .addConverterFactory(GsonConverterFactory.create()).build()
        .create(AiApi::class.java)

    @Provides @Singleton @Named("openrouter")
    fun provideOpenRouterApi(client: OkHttpClient): AiApi = Retrofit.Builder()
        .baseUrl(C.OPENROUTER_BASE).client(client)
        .addConverterFactory(GsonConverterFactory.create()).build()
        .create(AiApi::class.java)

    @Provides @Singleton
    fun provideDdgApi(client: OkHttpClient): DdgApi = Retrofit.Builder()
        .baseUrl(C.DDG_BASE).client(client)
        .addConverterFactory(GsonConverterFactory.create()).build()
        .create(DdgApi::class.java)
}
