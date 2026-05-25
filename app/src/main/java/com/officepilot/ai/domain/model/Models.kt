package com.officepilot.ai.domain.model

/** Output document format. */
enum class DocFormat(val ext: String, val mime: String, val label: String) {
    PDF("pdf", "application/pdf", "PDF"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Excel"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Word"),
    HTML("html", "text/html", "HTML"),
    CSV("csv", "text/csv", "CSV"),
    MD("md", "text/markdown", "Markdown"),
    JSON("json", "application/json", "JSON"),
    ZIP("zip", "application/zip", "ZIP")
}

/** AI provider for multi-provider fallback. */
enum class AiProvider(val label: String) {
    GEMINI("Google Gemini"),
    GROQ("Groq"),
    OPENROUTER("OpenRouter")
}

/** Current state of document generation. */
sealed class GenerationState {
    data object Idle : GenerationState()
    data class Thinking(val detail: String = "") : GenerationState()
    data class Searching(val query: String = "") : GenerationState()
    data class FetchingImages(val query: String = "") : GenerationState()
    data class Building(val format: DocFormat, val progress: Float = -1f) : GenerationState()
    data class Done(val filePath: String, val format: DocFormat, val fileName: String) : GenerationState()
    data class Error(val message: String) : GenerationState()
}

/** Structured document plan returned by the AI. */
data class DocumentPlan(
    val title: String = "",
    val format: String = "pdf",
    val sections: List<DocSection> = emptyList(),
    val searchQueries: List<String> = emptyList(),
    val imageQueries: List<String> = emptyList(),
    val needsData: Boolean = false
)

data class DocSection(
    val heading: String = "",
    val content: String = "",
    val type: String = "text", // text, table, chart, image, pagebreak, toc
    val tableData: List<List<String>> = emptyList(),
    val chartType: String = "", // bar, pie, line
    val chartData: Map<String, Double> = emptyMap(),
    val imageQuery: String = "",
    val imageUrl: String = ""
)

/** Chat message in the conversation. */
data class ChatMessage(
    val id: Long = 0,
    val role: String, // user, assistant, system, file
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val filePath: String? = null,
    val fileFormat: String? = null,
    val provider: String? = null
)

/** Search result from web. */
data class SearchResult(
    val title: String,
    val url: String,
    val snippet: String
)

/** Image result from Wikimedia/web. */
data class ImageResult(
    val title: String,
    val url: String,
    val thumbUrl: String,
    val width: Int = 0,
    val height: Int = 0,
    val license: String = ""
)
