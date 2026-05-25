package com.officepilot.ai.data.remote.dto

import com.google.gson.annotations.SerializedName

/** OpenAI-compatible chat completion request (works with Gemini, Groq, OpenRouter). */
data class CompletionRequest(
    val model: String,
    val messages: List<Msg>,
    val temperature: Double = 0.4,
    @SerializedName("max_tokens") val maxTokens: Int = 8192,
    @SerializedName("response_format") val responseFormat: ResponseFormat? = null
)

data class Msg(val role: String, val content: String)

data class ResponseFormat(val type: String = "json_object")

data class CompletionResponse(
    val choices: List<Choice>? = null,
    val error: ApiError? = null
)

data class Choice(val message: Msg? = null, @SerializedName("finish_reason") val finishReason: String? = null)

data class ApiError(val message: String? = null, val code: Any? = null)
