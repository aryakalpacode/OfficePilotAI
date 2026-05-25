package com.officepilot.ai.engine.ai

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.officepilot.ai.data.remote.AiApi
import com.officepilot.ai.data.remote.dto.*
import com.officepilot.ai.domain.model.AiProvider
import com.officepilot.ai.util.C
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Multi-provider AI engine. Tries Gemini first (500 req/day), then Groq (14K req/day),
 * then OpenRouter free models. All use OpenAI-compatible API format.
 */
@Singleton
class AiEngine @Inject constructor(
    @Named("gemini") private val geminiApi: AiApi,
    @Named("groq") private val groqApi: AiApi,
    @Named("openrouter") private val openRouterApi: AiApi,
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private const val TAG = "AiEngine"
    }

    /** Send a prompt to the AI. Tries providers in order until one succeeds. */
    suspend fun generate(
        systemPrompt: String,
        userPrompt: String,
        jsonMode: Boolean = true,
        maxTokens: Int = C.MAX_OUTPUT_TOKENS
    ): Result<String> {
        val providers = buildProviderList()
        var lastError: String? = null

        for ((provider, api, model, authHeader) in providers) {
            if (authHeader == null) continue
            Log.d(TAG, "Trying $provider with model $model")

            try {
                val format = if (jsonMode) ResponseFormat("json_object") else null
                val request = CompletionRequest(
                    model = model,
                    messages = listOf(
                        Msg("system", systemPrompt),
                        Msg("user", userPrompt)
                    ),
                    temperature = C.TEMPERATURE,
                    maxTokens = maxTokens,
                    responseFormat = format
                )

                val referer = if (provider == AiProvider.OPENROUTER) "https://officepilot-ai.app" else ""
                val xTitle = if (provider == AiProvider.OPENROUTER) "OfficePilot AI" else ""

                val response = api.complete(authHeader, referer, xTitle, request)

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.error != null) {
                        lastError = "${provider.label}: ${body.error.message}"
                        Log.w(TAG, "API error from $provider: ${body.error.message}")
                        continue
                    }
                    val content = body?.choices?.firstOrNull()?.message?.content
                    if (!content.isNullOrBlank()) {
                        Log.i(TAG, "✓ Success from ${provider.label} (${content.length} chars)")
                        return Result.success(content)
                    }
                    lastError = "${provider.label}: Empty response"
                } else {
                    val code = response.code()
                    val err = try { response.errorBody()?.string()?.take(300) } catch (_: Exception) { "" }
                    lastError = "${provider.label} HTTP $code: $err"
                    Log.w(TAG, lastError!!)
                    if (code == 429) {
                        delay(2000)
                        continue
                    }
                    if (code in listOf(401, 403)) continue // Skip bad keys
                }
            } catch (e: Exception) {
                lastError = "${provider.label}: ${e.message}"
                Log.e(TAG, "Exception from $provider", e)
            }
        }

        return Result.failure(Exception(lastError ?: "All AI providers failed"))
    }

    /** Send a multi-turn conversation. */
    suspend fun chat(
        systemPrompt: String,
        messages: List<Pair<String, String>>,
        jsonMode: Boolean = false,
        maxTokens: Int = C.MAX_OUTPUT_TOKENS
    ): Result<String> {
        val providers = buildProviderList()
        var lastError: String? = null

        for ((provider, api, model, authHeader) in providers) {
            if (authHeader == null) continue
            try {
                val msgList = mutableListOf(Msg("system", systemPrompt))
                messages.forEach { (role, content) -> msgList.add(Msg(role, content)) }

                val format = if (jsonMode) ResponseFormat("json_object") else null
                val request = CompletionRequest(model, msgList, C.TEMPERATURE, maxTokens, format)
                val referer = if (provider == AiProvider.OPENROUTER) "https://officepilot-ai.app" else ""
                val xTitle = if (provider == AiProvider.OPENROUTER) "OfficePilot AI" else ""

                val response = api.complete(authHeader, referer, xTitle, request)
                if (response.isSuccessful) {
                    val content = response.body()?.choices?.firstOrNull()?.message?.content
                    if (!content.isNullOrBlank()) return Result.success(content)
                    lastError = "${provider.label}: Empty response"
                } else {
                    lastError = "${provider.label} HTTP ${response.code()}"
                    if (response.code() == 429) { delay(2000); continue }
                    if (response.code() in listOf(401, 403)) continue
                }
            } catch (e: Exception) {
                lastError = "${provider.label}: ${e.message}"
            }
        }
        return Result.failure(Exception(lastError ?: "All providers failed"))
    }

    /** Test an API key against a provider. */
    suspend fun testKey(provider: AiProvider, key: String): Result<String> {
        val (api, model) = when (provider) {
            AiProvider.GEMINI -> geminiApi to C.GEMINI_MODEL
            AiProvider.GROQ -> groqApi to C.GROQ_MODEL
            AiProvider.OPENROUTER -> openRouterApi to C.OPENROUTER_MODEL
        }
        val auth = "Bearer $key"
        return try {
            val request = CompletionRequest(model, listOf(Msg("user", "Say OK")), 0.1, 10)
            val resp = api.complete(auth, "", "", request)
            if (resp.isSuccessful) {
                val content = resp.body()?.choices?.firstOrNull()?.message?.content
                if (content != null) Result.success("Key valid ✓")
                else Result.failure(Exception("Empty response"))
            } else {
                Result.failure(Exception("HTTP ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private data class ProviderConfig(
        val provider: AiProvider, val api: AiApi, val model: String, val authHeader: String?
    )

    private suspend fun buildProviderList(): List<ProviderConfig> {
        val prefs = dataStore.data.firstOrNull()
        val geminiKey = prefs?.get(stringPreferencesKey(C.PREF_GEMINI_KEY))
        val groqKey = prefs?.get(stringPreferencesKey(C.PREF_GROQ_KEY))
        val orKey = prefs?.get(stringPreferencesKey(C.PREF_OPENROUTER_KEY))

        return listOf(
            ProviderConfig(AiProvider.GEMINI, geminiApi, C.GEMINI_MODEL,
                geminiKey?.let { "Bearer $it" }),
            ProviderConfig(AiProvider.GROQ, groqApi, C.GROQ_MODEL,
                groqKey?.let { "Bearer $it" }),
            ProviderConfig(AiProvider.OPENROUTER, openRouterApi, C.OPENROUTER_MODEL,
                orKey?.let { "Bearer $it" })
        )
    }
}
