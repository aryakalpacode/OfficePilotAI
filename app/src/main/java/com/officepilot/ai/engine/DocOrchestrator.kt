package com.officepilot.ai.engine

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.officepilot.ai.domain.model.*
import com.officepilot.ai.engine.ai.AiEngine
import com.officepilot.ai.engine.ai.DocPrompts
import com.officepilot.ai.engine.docs.*
import com.officepilot.ai.engine.search.SearchEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Main orchestrator that coordinates AI + Search + Doc Generation.
 *
 * Flow: User Request → AI (plan) → Web Search (research) → Image Fetch → Doc Engine → File
 */
@Singleton
class DocOrchestrator @Inject constructor(
    private val ai: AiEngine,
    private val search: SearchEngine,
    private val pdfEngine: PdfEngine,
    private val excelEngine: ExcelEngine,
    private val wordEngine: WordEngine,
    private val textDocEngine: TextDocEngine
) {
    companion object { private const val TAG = "DocOrchestrator" }

    private val gson = Gson()
    private val _state = MutableStateFlow<GenerationState>(GenerationState.Idle)
    val state: StateFlow<GenerationState> = _state

    /**
     * Main entry point. Takes user instruction and desired format, produces a file.
     */
    suspend fun generate(instruction: String, format: DocFormat): Result<GenerationState.Done> {
        _state.value = GenerationState.Thinking("Understanding your request...")

        try {
            // ─── Step 1: Ask AI to plan the document ───
            val systemPrompt = if (format == DocFormat.XLSX) DocPrompts.EXCEL_SYSTEM else DocPrompts.DOCUMENT_SYSTEM
            val userPrompt = buildString {
                appendLine("Create a ${format.label} document for the following request:")
                appendLine(instruction)
                appendLine()
                appendLine("Output format: ${format.ext}")
                appendLine("Make it THOROUGH and PROFESSIONAL. At least 5 detailed sections.")
                if (format == DocFormat.PDF || format == DocFormat.DOCX || format == DocFormat.HTML) {
                    appendLine("Include image queries for visual appeal where appropriate.")
                    appendLine("Include relevant charts/tables if the content involves data.")
                }
            }

            val aiResult = ai.generate(systemPrompt, userPrompt, jsonMode = true)
            if (aiResult.isFailure) {
                val err = aiResult.exceptionOrNull()?.message ?: "AI generation failed"
                _state.value = GenerationState.Error(err)
                return Result.failure(Exception(err))
            }

            val rawJson = aiResult.getOrThrow()
            Log.d(TAG, "AI response: ${rawJson.take(500)}")

            // ─── Step 2: Parse the plan ───
            val plan = parsePlan(rawJson, format)
            if (plan == null) {
                _state.value = GenerationState.Error("Failed to parse AI response")
                return Result.failure(Exception("Parse error"))
            }

            // ─── Step 3: Web search for enrichment (if AI requested) ───
            val enrichedPlan = if (plan.searchQueries.isNotEmpty()) {
                enrichWithSearch(plan)
            } else plan

            // ─── Step 4: Fetch images ───
            val images = mutableMapOf<String, ByteArray>()
            val imageQueries = enrichedPlan.sections
                .filter { it.type == "image" && it.imageQuery.isNotBlank() }
                .map { it.imageQuery }
                .plus(enrichedPlan.imageQueries)
                .distinct()
                .take(5)

            if (imageQueries.isNotEmpty()) {
                _state.value = GenerationState.FetchingImages(imageQueries.first())
                for (query in imageQueries) {
                    val results = search.searchImages(query, 1)
                    val img = results.firstOrNull()
                    if (img != null) {
                        val bytes = search.downloadImage(img.thumbUrl)
                        if (bytes != null) {
                            images[query] = bytes
                            Log.d(TAG, "Downloaded image for: $query (${bytes.size} bytes)")
                        }
                    }
                }
            }

            // ─── Step 5: Generate the document ───
            _state.value = GenerationState.Building(format)
            val filePath = when (format) {
                DocFormat.PDF -> pdfEngine.generate(enrichedPlan, images)
                DocFormat.XLSX -> {
                    try {
                        val jsonObj = JsonParser.parseString(rawJson).asJsonObject
                        if (jsonObj.has("sheets")) excelEngine.generate(jsonObj)
                        else {
                            val tables = enrichedPlan.sections.filter { it.type == "table" }.map { it.tableData }
                            if (tables.isNotEmpty()) excelEngine.generateFromSections(enrichedPlan.title, tables)
                            else excelEngine.generate(jsonObj)
                        }
                    } catch (e: Exception) {
                        val tables = enrichedPlan.sections.filter { it.type == "table" }.map { it.tableData }
                        excelEngine.generateFromSections(enrichedPlan.title, tables.ifEmpty { listOf(listOf(listOf("No data"))) })
                    }
                }
                DocFormat.DOCX -> wordEngine.generate(enrichedPlan, images)
                DocFormat.HTML -> textDocEngine.generateHtml(enrichedPlan, images)
                DocFormat.CSV -> textDocEngine.generateCsv(enrichedPlan)
                DocFormat.MD -> textDocEngine.generateMarkdown(enrichedPlan)
                DocFormat.JSON -> {
                    val dir = java.io.File(pdfEngine.javaClass.toString()).parentFile // hack
                    // Just save the raw JSON
                    val name = "${enrichedPlan.title.replace(Regex("[^a-zA-Z0-9]"), "_").take(40)}.json"
                    val file = java.io.File(android.os.Environment.getExternalStorageDirectory(), name) // fallback
                    textDocEngine.generateMarkdown(enrichedPlan) // fallback to MD
                }
                DocFormat.ZIP -> {
                    // Generate PDF + XLSX + DOCX and bundle
                    _state.value = GenerationState.Building(DocFormat.PDF, 0.3f)
                    val pdfPath = pdfEngine.generate(enrichedPlan, images)
                    _state.value = GenerationState.Building(DocFormat.DOCX, 0.6f)
                    val docxPath = wordEngine.generate(enrichedPlan, images)
                    _state.value = GenerationState.Building(DocFormat.ZIP, 0.9f)
                    textDocEngine.createZip(listOf(pdfPath, docxPath), enrichedPlan.title.replace(Regex("[^a-zA-Z0-9]"), "_").take(30))
                }
            }

            val fileName = java.io.File(filePath).name
            val done = GenerationState.Done(filePath, format, fileName)
            _state.value = done
            return Result.success(done)

        } catch (e: Exception) {
            Log.e(TAG, "Generation failed", e)
            val err = "Error: ${e.message}"
            _state.value = GenerationState.Error(err)
            return Result.failure(e)
        }
    }

    /** Parse AI response JSON into a DocumentPlan. */
    private fun parsePlan(json: String, format: DocFormat): DocumentPlan? {
        return try {
            // Try to extract JSON from markdown code blocks if present
            val cleanJson = json.trim().let { raw ->
                if (raw.startsWith("{")) raw
                else {
                    val match = Regex("```(?:json)?\\s*\\n?(\\{.*?})\\s*```", RegexOption.DOT_MATCHES_ALL).find(raw)
                    match?.groupValues?.get(1) ?: raw.substringAfter("{").let { "{$it" }.substringBeforeLast("}").let { "$it}" }
                }
            }
            val obj = JsonParser.parseString(cleanJson).asJsonObject
            val title = obj.get("title")?.asString ?: "Document"

            val sections = mutableListOf<DocSection>()
            obj.getAsJsonArray("sections")?.forEach { secJson ->
                val s = secJson.asJsonObject
                val type = s.get("type")?.asString ?: "text"
                val heading = s.get("heading")?.asString ?: ""
                val content = s.get("content")?.asString ?: ""
                val tableData = s.getAsJsonArray("tableData")?.map { row ->
                    row.asJsonArray.map { it.asString }
                } ?: emptyList()
                val chartType = s.get("chartType")?.asString ?: ""
                val chartData = s.getAsJsonObject("chartData")?.entrySet()?.associate {
                    it.key to it.value.asDouble
                } ?: emptyMap()
                val imageQuery = s.get("imageQuery")?.asString ?: ""
                val imageUrl = s.get("imageUrl")?.asString ?: ""

                sections.add(DocSection(heading, content, type, tableData, chartType, chartData, imageQuery, imageUrl))
            }

            val searchQueries = obj.getAsJsonArray("searchQueries")?.map { it.asString } ?: emptyList()
            val imageQueries = obj.getAsJsonArray("imageQueries")?.map { it.asString } ?: emptyList()

            DocumentPlan(title, format.ext, sections, searchQueries, imageQueries)
        } catch (e: Exception) {
            Log.e(TAG, "Plan parse failed", e)
            null
        }
    }

    /** Enrich document with web search results. */
    private suspend fun enrichWithSearch(plan: DocumentPlan): DocumentPlan {
        _state.value = GenerationState.Searching(plan.searchQueries.first())
        val allResults = mutableListOf<String>()

        for (query in plan.searchQueries.take(3)) {
            _state.value = GenerationState.Searching(query)
            val results = search.webSearch(query, 3)
            for (result in results.take(2)) {
                val text = search.scrapeUrl(result.url, 2000)
                if (text.isNotBlank()) {
                    allResults.add("Source: ${result.title} (${result.url})\n$text")
                }
            }
            // Also check Wikipedia
            val wiki = search.wikiSearch(query)
            if (wiki.isNotBlank()) allResults.add("Wikipedia:\n$wiki")
        }

        if (allResults.isEmpty()) return plan

        // Ask AI to incorporate research
        _state.value = GenerationState.Thinking("Incorporating research...")
        val enrichPrompt = "Here are web search results to incorporate:\n\n${allResults.joinToString("\n\n---\n\n").take(6000)}\n\nOriginal document title: ${plan.title}\nAdd relevant facts, statistics, and information as additional sections."

        val enrichResult = ai.generate(DocPrompts.ENRICH_WITH_SEARCH, enrichPrompt, jsonMode = true, maxTokens = 4096)
        if (enrichResult.isFailure) return plan

        return try {
            val json = JsonParser.parseString(enrichResult.getOrThrow()).asJsonObject
            val additional = json.getAsJsonArray("additionalSections")?.map { s ->
                val obj = s.asJsonObject
                DocSection(
                    heading = obj.get("heading")?.asString ?: "",
                    content = obj.get("content")?.asString ?: "",
                    type = obj.get("type")?.asString ?: "text",
                    tableData = obj.getAsJsonArray("tableData")?.map { r -> r.asJsonArray.map { it.asString } } ?: emptyList()
                )
            } ?: emptyList()

            plan.copy(sections = plan.sections + additional)
        } catch (e: Exception) {
            Log.w(TAG, "Enrichment parse failed", e)
            plan
        }
    }

    /** Simple chat (non-document conversation). */
    suspend fun chat(message: String, history: List<Pair<String, String>> = emptyList()): Result<String> {
        val msgs = history.toMutableList()
        msgs.add("user" to message)
        return ai.chat(DocPrompts.CHAT_SYSTEM, msgs, jsonMode = false, maxTokens = 2048)
    }

    fun resetState() { _state.value = GenerationState.Idle }
}
