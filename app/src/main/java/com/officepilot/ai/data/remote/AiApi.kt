package com.officepilot.ai.data.remote

import com.officepilot.ai.data.remote.dto.CompletionRequest
import com.officepilot.ai.data.remote.dto.CompletionResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/** Unified OpenAI-compatible API interface — works for Gemini, Groq, and OpenRouter. */
interface AiApi {
    @POST("chat/completions")
    suspend fun complete(
        @Header("Authorization") auth: String,
        @Header("HTTP-Referer") referer: String = "",
        @Header("X-Title") title: String = "",
        @Body request: CompletionRequest
    ): Response<CompletionResponse>
}

/** DuckDuckGo HTML search. */
interface DdgApi {
    @GET("html/")
    suspend fun search(@Query("q") q: String): Response<ResponseBody>
}
