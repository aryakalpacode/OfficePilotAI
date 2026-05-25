package com.officepilot.ai.engine.markdown

import com.officepilot.ai.domain.model.DocSection
import com.officepilot.ai.domain.model.DocumentPlan

object MarkdownParser {

    fun parse(markdown: String): DocumentPlan {
        if (markdown.isBlank()) {
            return DocumentPlan("Empty Document", "pdf", emptyList())
        }

        val lines = markdown.lines()
        var title = ""
        val sections = mutableListOf<DocSection>()

        var currentHeading = ""
        var currentBlockType = "text" // "text", "bullet_list", "numbered_list", "table", "code", "quote"
        val currentBlockLines = mutableListOf<String>()

        // Helper to flush current block
        fun flushBlock() {
            if (currentBlockLines.isEmpty()) return

            val blockContent = currentBlockLines.joinToString("\n").trim()
            if (blockContent.isNotBlank() || currentBlockType == "code" || currentBlockType == "table") {
                when (currentBlockType) {
                    "text" -> {
                        sections.add(DocSection(
                            heading = currentHeading,
                            content = blockContent,
                            type = "text"
                        ))
                        currentHeading = ""
                    }
                    "bullet_list" -> {
                        sections.add(DocSection(
                            heading = currentHeading,
                            content = blockContent,
                            type = "bullet_list"
                        ))
                        currentHeading = ""
                    }
                    "numbered_list" -> {
                        sections.add(DocSection(
                            heading = currentHeading,
                            content = blockContent,
                            type = "numbered_list"
                        ))
                        currentHeading = ""
                    }
                    "quote" -> {
                        sections.add(DocSection(
                            heading = currentHeading,
                            content = blockContent,
                            type = "quote"
                        ))
                        currentHeading = ""
                    }
                    "code" -> {
                        sections.add(DocSection(
                            heading = currentHeading,
                            content = currentBlockLines.joinToString("\n"), // Preserve formatting
                            type = "code"
                        ))
                        currentHeading = ""
                    }
                    "table" -> {
                        val tableData = parseMarkdownTable(currentBlockLines)
                        if (tableData.isNotEmpty()) {
                            sections.add(DocSection(
                                heading = currentHeading,
                                type = "table",
                                tableData = tableData
                            ))
                        }
                        currentHeading = ""
                    }
                }
            }
            currentBlockLines.clear()
        }

        var inCodeBlock = false

        for (rawLine in lines) {
            val line = rawLine.trim()

            // ─── Code Blocks ───
            if (line.startsWith("```")) {
                if (inCodeBlock) {
                    flushBlock()
                    inCodeBlock = false
                } else {
                    flushBlock()
                    inCodeBlock = true
                    currentBlockType = "code"
                }
                continue
            }

            if (inCodeBlock) {
                currentBlockLines.add(rawLine) // Keep spacing
                continue
            }

            // ─── Blank Line ───
            if (line.isEmpty()) {
                if (currentBlockType != "code") {
                    flushBlock()
                    currentBlockType = "text"
                }
                continue
            }

            // ─── Headings ───
            if (line.startsWith("#")) {
                flushBlock()
                val level = line.takeWhile { it == '#' }.length
                val text = line.drop(level).trim()
                if (level == 1 && title.isEmpty()) {
                    title = text
                } else {
                    currentHeading = text
                }
                currentBlockType = "text"
                continue
            }

            // ─── Horizontal Rule (Page Break) ───
            if (line == "---" || line == "***" || line == "___") {
                flushBlock()
                sections.add(DocSection(type = "pagebreak"))
                currentBlockType = "text"
                continue
            }

            // ─── Blockquote ───
            if (line.startsWith(">")) {
                if (currentBlockType != "quote") {
                    flushBlock()
                    currentBlockType = "quote"
                }
                val quoteText = line.drop(1).trim()
                currentBlockLines.add(quoteText)
                continue
            }

            // ─── Unordered List ───
            if (line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ")) {
                if (currentBlockType != "bullet_list") {
                    flushBlock()
                    currentBlockType = "bullet_list"
                }
                currentBlockLines.add(line.substring(2).trim())
                continue
            }

            // ─── Ordered List ───
            val numListMatch = Regex("^\\d+\\.\\s+(.*)").find(line)
            if (numListMatch != null) {
                if (currentBlockType != "numbered_list") {
                    flushBlock()
                    currentBlockType = "numbered_list"
                }
                currentBlockLines.add(numListMatch.groupValues[1].trim())
                continue
            }

            // ─── Table ───
            if (line.startsWith("|") && line.endsWith("|")) {
                if (line.contains("---")) {
                    if (currentBlockType != "table") {
                        currentBlockType = "table"
                    }
                    continue
                }
                if (currentBlockType != "table") {
                    flushBlock()
                    currentBlockType = "table"
                }
                currentBlockLines.add(line)
                continue
            }

            // ─── Inline Images ───
            val imgMatch = Regex("!\\[(.*?)]\\((.*?)\\)").find(line)
            if (imgMatch != null) {
                flushBlock()
                val alt = imgMatch.groupValues[1]
                val url = imgMatch.groupValues[2]
                sections.add(DocSection(
                    heading = alt,
                    type = "image",
                    imageUrl = url,
                    imageQuery = alt
                ))
                currentBlockType = "text"
                continue
            }

            // ─── Regular Text ───
            if (currentBlockType != "text") {
                flushBlock()
                currentBlockType = "text"
            }
            currentBlockLines.add(line)
        }

        flushBlock()

        // Clean empty sections
        val cleanSections = sections.filter {
            it.type == "pagebreak" || it.content.isNotBlank() || it.tableData.isNotEmpty() || it.imageUrl.isNotBlank()
        }

        return DocumentPlan(
            title = title.ifBlank { "Markdown Document" },
            format = "pdf",
            sections = cleanSections
        )
    }

    private fun parseMarkdownTable(lines: List<String>): List<List<String>> {
        val table = mutableListOf<List<String>>()
        for (line in lines) {
            val cells = line.split("|")
                .map { it.trim() }
                .filterIndexed { index, _ -> index > 0 && index < line.split("|").lastIndex }
            if (cells.isNotEmpty() && !cells.all { it.contains("---") }) {
                table.add(cells)
            }
        }
        return table
    }
}
