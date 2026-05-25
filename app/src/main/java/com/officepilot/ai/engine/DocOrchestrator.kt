package com.officepilot.ai.engine

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.officepilot.ai.domain.model.*
import com.officepilot.ai.engine.ai.AiEngine
import com.officepilot.ai.engine.ai.DocPrompts
import com.officepilot.ai.engine.docs.*
import com.officepilot.ai.engine.markdown.MarkdownParser
import com.officepilot.ai.engine.search.SearchEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

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

    /** Convert raw markdown string to any supported format with full styling customisation. */
    suspend fun convertMarkdown(
        markdown: String,
        format: DocFormat,
        style: DocStyleConfig = DocStyleConfig()
    ): Result<String> {
        _state.value = GenerationState.Building(format, 0.2f)
        return try {
            val plan = MarkdownParser.parse(markdown)
            _state.value = GenerationState.Building(format, 0.5f)
            
            val filePath = when (format) {
                DocFormat.PDF -> pdfEngine.generate(plan, emptyMap(), style)
                DocFormat.DOCX -> wordEngine.generate(plan, emptyMap(), style)
                DocFormat.HTML -> textDocEngine.generateHtml(plan, emptyMap(), style)
                DocFormat.MD -> textDocEngine.generateMarkdown(plan)
                DocFormat.CSV -> textDocEngine.generateCsv(plan)
                DocFormat.ZIP -> {
                    _state.value = GenerationState.Building(DocFormat.PDF, 0.4f)
                    val pdfPath = pdfEngine.generate(plan, emptyMap(), style)
                    _state.value = GenerationState.Building(DocFormat.DOCX, 0.7f)
                    val docxPath = wordEngine.generate(plan, emptyMap(), style)
                    _state.value = GenerationState.Building(DocFormat.HTML, 0.85f)
                    val htmlPath = textDocEngine.generateHtml(plan, emptyMap(), style)
                    
                    textDocEngine.createZip(listOf(pdfPath, docxPath, htmlPath), plan.title.replace(Regex("[^a-zA-Z0-9]"), "_").take(30))
                }
                else -> pdfEngine.generate(plan, emptyMap(), style)
            }
            
            _state.value = GenerationState.Done(filePath, format, java.io.File(filePath).name)
            Result.success(filePath)
        } catch (e: Exception) {
            Log.e(TAG, "Markdown conversion failed", e)
            _state.value = GenerationState.Error(e.message ?: "Unknown error during conversion")
            Result.failure(e)
        } finally {
            _state.value = GenerationState.Idle
        }
    }

    suspend fun generate(instruction: String, format: DocFormat): Result<GenerationState.Done> {
        _state.value = GenerationState.Thinking("Understanding your request...")

        try {
            val systemPrompt = if (format == DocFormat.XLSX) DocPrompts.EXCEL_SYSTEM else DocPrompts.DOCUMENT_SYSTEM
            val userPrompt = buildString {
                appendLine("Create a ${format.label} document for the following request:")
                appendLine(instruction)
                appendLine()
                appendLine("Output format: ${format.ext}")
                appendLine("Make it THOROUGH and PROFESSIONAL. At least 5 detailed sections.")
                appendLine("IMPORTANT: Respond with ONLY a valid JSON object. No markdown, no code blocks, no extra text.")
                if (format == DocFormat.PDF || format == DocFormat.DOCX || format == DocFormat.HTML) {
                    appendLine("Include image queries for visual appeal where appropriate.")
                    appendLine("Include relevant charts/tables if the content involves data.")
                }
            }

            // Try with JSON mode first, fallback to non-JSON mode
            var aiResult = ai.generate(systemPrompt, userPrompt, jsonMode = true)
            if (aiResult.isFailure) {
                // Retry without json mode (some providers don't support it)
                Log.w(TAG, "JSON mode failed, retrying without: ${aiResult.exceptionOrNull()?.message}")
                aiResult = ai.generate(systemPrompt, userPrompt, jsonMode = false)
            }
            if (aiResult.isFailure) {
                val err = aiResult.exceptionOrNull()?.message ?: "AI generation failed"
                _state.value = GenerationState.Error(err)
                return Result.failure(Exception(err))
            }

            val rawResponse = aiResult.getOrThrow()
            Log.d(TAG, "AI raw response (first 800 chars): ${rawResponse.take(800)}")

            // ─── Parse the plan (robust) ───
            val plan = parsePlan(rawResponse, format)
            if (plan == null) {
                // Last resort: ask AI to fix its own output
                Log.w(TAG, "Parse failed, asking AI to fix...")
                val fixResult = ai.generate(
                    "You previously generated malformed JSON. Fix it and return ONLY valid JSON.",
                    "Fix this into valid JSON with keys 'title' and 'sections' array:\n\n${rawResponse.take(3000)}",
                    jsonMode = false
                )
                val fixedPlan = fixResult.getOrNull()?.let { parsePlan(it, format) }
                if (fixedPlan == null) {
                    // Ultimate fallback: create a simple plan from the raw text
                    Log.w(TAG, "Fix also failed, using raw text fallback")
                    val fallbackPlan = DocumentPlan(
                        title = instruction.take(50),
                        format = format.ext,
                        sections = listOf(
                            DocSection(heading = "Content", content = rawResponse.take(5000), type = "text")
                        )
                    )
                    return generateFromPlan(fallbackPlan, format, rawResponse)
                }
                return generateFromPlan(fixedPlan, format, rawResponse)
            }

            return generateFromPlan(plan, format, rawResponse)

        } catch (e: Exception) {
            Log.e(TAG, "Generation failed", e)
            val err = "Error: ${e.message}"
            _state.value = GenerationState.Error(err)
            return Result.failure(e)
        }
    }

    /** Generate from a successfully parsed plan. */
    private suspend fun generateFromPlan(
        plan: DocumentPlan, format: DocFormat, rawJson: String
    ): Result<GenerationState.Done> {
        // ─── Web search enrichment ───
        val enrichedPlan = if (plan.searchQueries.isNotEmpty()) {
            try { enrichWithSearch(plan) } catch (e: Exception) {
                Log.w(TAG, "Enrichment failed, using original plan", e); plan
            }
        } else plan

        // ─── Fetch images ───
        val images = mutableMapOf<String, ByteArray>()
        val imageQueries = enrichedPlan.sections
            .filter { it.type == "image" && it.imageQuery.isNotBlank() }
            .map { it.imageQuery }
            .plus(enrichedPlan.imageQueries)
            .distinct().take(5)

        if (imageQueries.isNotEmpty()) {
            _state.value = GenerationState.FetchingImages(imageQueries.first())
            for (query in imageQueries) {
                try {
                    val results = search.searchImages(query, 1)
                    val img = results.firstOrNull()
                    if (img != null) {
                        val bytes = search.downloadImage(img.thumbUrl)
                        if (bytes != null) images[query] = bytes
                    }
                } catch (e: Exception) { Log.w(TAG, "Image fetch failed: $query", e) }
            }
        }

        // ─── Generate the document ───
        _state.value = GenerationState.Building(format)
        val filePath = when (format) {
            DocFormat.PDF -> pdfEngine.generate(enrichedPlan, images)
            DocFormat.XLSX -> {
                try {
                    val cleanJson = extractJson(rawJson)
                    val jsonObj = JsonParser.parseString(cleanJson).asJsonObject
                    if (jsonObj.has("sheets")) excelEngine.generate(jsonObj)
                    else {
                        val tables = enrichedPlan.sections.filter { it.type == "table" && it.tableData.isNotEmpty() }.map { it.tableData }
                        if (tables.isNotEmpty()) excelEngine.generateFromSections(enrichedPlan.title, tables)
                        else excelEngine.generate(jsonObj)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Excel from JSON failed, using sections", e)
                    val tables = enrichedPlan.sections.filter { it.type == "table" && it.tableData.isNotEmpty() }.map { it.tableData }
                    excelEngine.generateFromSections(enrichedPlan.title, tables.ifEmpty { listOf(listOf(listOf("Data", "Value"), listOf(enrichedPlan.title, ""))) })
                }
            }
            DocFormat.DOCX -> wordEngine.generate(enrichedPlan, images)
            DocFormat.HTML -> textDocEngine.generateHtml(enrichedPlan, images)
            DocFormat.CSV -> textDocEngine.generateCsv(enrichedPlan)
            DocFormat.MD -> textDocEngine.generateMarkdown(enrichedPlan)
            DocFormat.JSON -> textDocEngine.generateMarkdown(enrichedPlan)
            DocFormat.ZIP -> {
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
    }

    /**
     * ROBUST JSON parser. Handles all these cases:
     * 1. Clean JSON: {"title": ...}
     * 2. Markdown wrapped: ```json\n{...}\n```
     * 3. Text before/after JSON: "Here is your document:\n{...}\nHope this helps!"
     * 4. Multiple code blocks
     * 5. Escaped newlines and special chars
     */
    private fun parsePlan(response: String, format: DocFormat): DocumentPlan? {
        val jsonStr = extractJson(response)
        if (jsonStr == null) {
            Log.e(TAG, "Could not extract JSON from response")
            return null
        }

        return try {
            val obj = JsonParser.parseString(jsonStr).asJsonObject
            val title = obj.get("title")?.asString ?: obj.get("suggestedFileName")?.asString ?: "Document"

            val sections = mutableListOf<DocSection>()
            val sectionsArr = obj.getAsJsonArray("sections")
            if (sectionsArr != null) {
                for (secElement in sectionsArr) {
                    try {
                        val s = secElement.asJsonObject
                        val type = s.get("type")?.asString ?: "text"
                        val heading = s.get("heading")?.asString ?: ""
                        val content = s.get("content")?.asString ?: ""

                        val tableData: List<List<String>> = try {
                            s.getAsJsonArray("tableData")?.map { row ->
                                row.asJsonArray.map { cell ->
                                    // Handle numbers, booleans, nulls in table cells
                                    when {
                                        cell.isJsonNull -> ""
                                        cell.isJsonPrimitive -> cell.asString
                                        else -> cell.toString()
                                    }
                                }
                            } ?: emptyList()
                        } catch (e: Exception) { emptyList() }

                        val chartData: Map<String, Double> = try {
                            val cd = s.get("chartData")
                            when {
                                cd == null || cd.isJsonNull -> emptyMap()
                                cd.isJsonObject -> cd.asJsonObject.entrySet().associate { entry ->
                                    entry.key to (entry.value.asDouble)
                                }
                                else -> emptyMap()
                            }
                        } catch (e: Exception) { emptyMap() }

                        val chartType = s.get("chartType")?.asString ?: ""
                        val imageQuery = s.get("imageQuery")?.asString ?: ""
                        val imageUrl = s.get("imageUrl")?.asString ?: ""

                        sections.add(DocSection(heading, content, type, tableData, chartType, chartData, imageQuery, imageUrl))
                    } catch (e: Exception) {
                        Log.w(TAG, "Skipping malformed section", e)
                    }
                }
            }

            // Also handle "sheets" for Excel format
            if (sections.isEmpty() && obj.has("sheets")) {
                val sheets = obj.getAsJsonArray("sheets")
                sheets?.forEach { sheetEl ->
                    try {
                        val sheet = sheetEl.asJsonObject
                        val name = sheet.get("name")?.asString ?: "Data"
                        val headers = sheet.getAsJsonArray("headers")?.map { it.asString } ?: emptyList()
                        val rows = sheet.getAsJsonArray("rows")?.map { r ->
                            r.asJsonArray.map { c -> if (c.isJsonNull) "" else c.asString }
                        } ?: emptyList()
                        val tableData = if (headers.isNotEmpty()) listOf(headers) + rows else rows
                        sections.add(DocSection(heading = name, type = "table", tableData = tableData))
                    } catch (e: Exception) { Log.w(TAG, "Skipping malformed sheet", e) }
                }
            }

            if (sections.isEmpty()) {
                Log.w(TAG, "No sections parsed from JSON")
                return null
            }

            val searchQueries = try {
                obj.getAsJsonArray("searchQueries")?.map { it.asString } ?: emptyList()
            } catch (e: Exception) { emptyList() }

            val imageQueries = try {
                obj.getAsJsonArray("imageQueries")?.map { it.asString } ?: emptyList()
            } catch (e: Exception) { emptyList() }

            Log.i(TAG, "Parsed plan: '$title' with ${sections.size} sections")
            DocumentPlan(title, format.ext, sections, searchQueries, imageQueries)

        } catch (e: Exception) {
            Log.e(TAG, "JSON parse exception", e)
            null
        }
    }

    /**
     * Extract a JSON object from potentially messy AI output.
     * Handles markdown code blocks, text before/after, etc.
     */
    private fun extractJson(raw: String): String? {
        val trimmed = raw.trim()

        // 1. Already clean JSON
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            if (isValidJson(trimmed)) return trimmed
        }

        // 2. Extract from markdown code block: ```json ... ``` or ``` ... ```
        val codeBlockPatterns = listOf(
            Regex("```json\\s*\n(.*?)```", RegexOption.DOT_MATCHES_ALL),
            Regex("```\\s*\n(\\{.*?})\\s*```", RegexOption.DOT_MATCHES_ALL),
            Regex("```(\\{.*?})```", RegexOption.DOT_MATCHES_ALL)
        )
        for (pattern in codeBlockPatterns) {
            val match = pattern.find(trimmed)
            if (match != null) {
                val candidate = match.groupValues[1].trim()
                if (isValidJson(candidate)) return candidate
            }
        }

        // 3. Find the outermost { ... } using bracket matching
        val startIdx = trimmed.indexOf('{')
        if (startIdx >= 0) {
            var depth = 0
            var inString = false
            var escape = false
            for (i in startIdx until trimmed.length) {
                val c = trimmed[i]
                if (escape) { escape = false; continue }
                if (c == '\\') { escape = true; continue }
                if (c == '"' && !escape) { inString = !inString; continue }
                if (inString) continue
                when (c) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            val candidate = trimmed.substring(startIdx, i + 1)
                            if (isValidJson(candidate)) return candidate
                        }
                    }
                }
            }
            // Even if depth didn't reach 0, try adding closing braces
            if (depth > 0) {
                val candidate = trimmed.substring(startIdx) + "}".repeat(depth)
                if (isValidJson(candidate)) return candidate
            }
        }

        // 4. Nothing worked
        Log.e(TAG, "Could not extract JSON. Raw response starts with: ${trimmed.take(200)}")
        return null
    }

    private fun isValidJson(str: String): Boolean {
        return try {
            val el = JsonParser.parseString(str)
            el.isJsonObject
        } catch (e: JsonSyntaxException) { false }
    }

    /** Enrich document with web search results. */
    private suspend fun enrichWithSearch(plan: DocumentPlan): DocumentPlan {
        _state.value = GenerationState.Searching(plan.searchQueries.firstOrNull() ?: "")
        val allResults = mutableListOf<String>()

        for (query in plan.searchQueries.take(3)) {
            _state.value = GenerationState.Searching(query)
            try {
                val results = search.webSearch(query, 3)
                for (result in results.take(2)) {
                    val text = search.scrapeUrl(result.url, 2000)
                    if (text.isNotBlank()) {
                        allResults.add("Source: ${result.title} (${result.url})\n$text")
                    }
                }
                val wiki = search.wikiSearch(query)
                if (wiki.isNotBlank()) allResults.add("Wikipedia:\n$wiki")
            } catch (e: Exception) { Log.w(TAG, "Search failed for: $query", e) }
        }

        if (allResults.isEmpty()) return plan

        _state.value = GenerationState.Thinking("Incorporating research...")
        val enrichPrompt = "Here are web search results to incorporate:\n\n${allResults.joinToString("\n\n---\n\n").take(6000)}\n\nOriginal document title: ${plan.title}\nAdd relevant facts as additional sections. Respond ONLY with valid JSON."

        val enrichResult = ai.generate(DocPrompts.ENRICH_WITH_SEARCH, enrichPrompt, jsonMode = false, maxTokens = 4096)
        if (enrichResult.isFailure) return plan

        return try {
            val jsonStr = extractJson(enrichResult.getOrThrow()) ?: return plan
            val json = JsonParser.parseString(jsonStr).asJsonObject
            val additional = json.getAsJsonArray("additionalSections")?.mapNotNull { s ->
                try {
                    val obj = s.asJsonObject
                    DocSection(
                        heading = obj.get("heading")?.asString ?: "",
                        content = obj.get("content")?.asString ?: "",
                        type = obj.get("type")?.asString ?: "text",
                        tableData = try { obj.getAsJsonArray("tableData")?.map { r -> r.asJsonArray.map { it.asString } } ?: emptyList() } catch (_: Exception) { emptyList() }
                    )
                } catch (e: Exception) { null }
            } ?: emptyList()

            plan.copy(sections = plan.sections + additional)
        } catch (e: Exception) {
            Log.w(TAG, "Enrichment parse failed", e)
            plan
        }
    }

    suspend fun chat(message: String, history: List<Pair<String, String>> = emptyList()): Result<String> {
        val msgs = history.toMutableList()
        msgs.add("user" to message)
        return ai.chat(DocPrompts.CHAT_SYSTEM, msgs, jsonMode = false, maxTokens = 2048)
    }

    fun resetState() { _state.value = GenerationState.Idle }
}
