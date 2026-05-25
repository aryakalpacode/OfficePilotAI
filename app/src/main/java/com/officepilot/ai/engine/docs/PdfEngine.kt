package com.officepilot.ai.engine.docs

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfDocument.PageInfo
import com.officepilot.ai.domain.model.DocSection
import com.officepilot.ai.domain.model.DocStyleConfig
import com.officepilot.ai.domain.model.DocumentPlan
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Advanced PDF generation engine supporting full customisation.
 * Processes plan sections with customizable styles (themes, typography, page numbers, layouts).
 */
@Singleton
class PdfEngine @Inject constructor(@ApplicationContext private val ctx: Context) {

    companion object {
        const val PAGE_W = 595 // A4 width in points
        const val PAGE_H = 842 // A4 height in points
        const val TITLE_SIZE = 24f
        const val HEADING_SIZE = 16f
        const val SUBHEADING_SIZE = 13f
        const val BODY_SIZE = 10.5f
        const val SMALL_SIZE = 8.5f
        const val CELL_PADDING = 5f
    }

    fun generate(
        plan: DocumentPlan,
        images: Map<String, ByteArray> = emptyMap(),
        style: DocStyleConfig = DocStyleConfig()
    ): String {
        val doc = PdfDocument()
        var pageNum = 1
        
        // Colors
        val primaryColor = Color.parseColor(style.primaryColorHex)
        val secondaryColor = Color.parseColor(style.secondaryColorHex)
        val textColor = Color.parseColor(style.textColorHex)
        val bgColor = Color.parseColor(style.backgroundColorHex)
        
        // Typography
        val typeface = when (style.fontFamily) {
            "Serif" -> Typeface.SERIF
            "Monospace" -> Typeface.MONOSPACE
            else -> Typeface.DEFAULT
        }
        val boldTypeface = Typeface.create(typeface, Typeface.BOLD)
        val italicTypeface = Typeface.create(typeface, Typeface.ITALIC)
        val monospaceTypeface = Typeface.MONOSPACE

        // Paints
        val titlePaint = Paint().apply { color = primaryColor; textSize = TITLE_SIZE; this.typeface = boldTypeface; isAntiAlias = true }
        val headingPaint = Paint().apply { color = primaryColor; textSize = HEADING_SIZE; this.typeface = boldTypeface; isAntiAlias = true }
        val subheadingPaint = Paint().apply { color = secondaryColor; textSize = SUBHEADING_SIZE; this.typeface = boldTypeface; isAntiAlias = true }
        val bodyPaint = Paint().apply { color = textColor; textSize = BODY_SIZE; this.typeface = typeface; isAntiAlias = true }
        val italicPaint = Paint().apply { color = textColor; textSize = BODY_SIZE; this.typeface = italicTypeface; isAntiAlias = true }
        val codePaint = Paint().apply { color = Color.parseColor("#333333"); textSize = BODY_SIZE - 0.5f; this.typeface = monospaceTypeface; isAntiAlias = true }
        val smallPaint = Paint().apply { color = Color.parseColor("#757575"); textSize = SMALL_SIZE; this.typeface = typeface; isAntiAlias = true }
        val accentPaint = Paint().apply { color = primaryColor; style = Paint.Style.FILL; isAntiAlias = true }
        val dividerPaint = Paint().apply { color = Color.parseColor("#E0E0E0"); strokeWidth = 1f; style = Paint.Style.STROKE }
        
        // Layout Configs
        val margin = style.marginSize
        val contentW = PAGE_W - 2 * margin
        var page = doc.startPage(PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
        var canvas = page.canvas
        var y = margin

        // Draw Background
        canvas.drawColor(bgColor)

        // Draw Header decoration if showHeader is true
        if (style.showHeader) {
            drawHeader(canvas, style.headerText, smallPaint, margin, dividerPaint)
            y += 25f
        }

        // ─── Cover / Title ───
        y += 40f
        y = drawWrappedText(canvas, plan.title, titlePaint, margin, y, contentW, style.lineSpacing)
        y += 8f
        
        // Accent line under title
        canvas.drawLine(margin, y, PAGE_W - margin, y, Paint(accentPaint).apply { strokeWidth = 2.5f; style = Paint.Style.STROKE })
        y += 18f

        // Author / Generation Date
        val dateStr = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(Date())
        canvas.drawText("Generated on $dateStr", margin, y, smallPaint)
        y += 35f

        // ─── Sections ───
        for (section in plan.sections) {
            // Check page boundary
            if (y > PAGE_H - margin - 50) {
                drawPageDecorations(canvas, pageNum, style, smallPaint, dividerPaint)
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
                canvas = page.canvas
                canvas.drawColor(bgColor)
                y = margin
                if (style.showHeader) {
                    drawHeader(canvas, style.headerText, smallPaint, margin, dividerPaint)
                    y += 25f
                }
            }

            // Force Pagebreak if configured before H2 headings
            if (style.enablePageBreaksBeforeH2 && section.heading.isNotBlank() && y > margin + 100f) {
                drawPageDecorations(canvas, pageNum, style, smallPaint, dividerPaint)
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
                canvas = page.canvas
                canvas.drawColor(bgColor)
                y = margin
                if (style.showHeader) {
                    drawHeader(canvas, style.headerText, smallPaint, margin, dividerPaint)
                    y += 25f
                }
            }

            when (section.type) {
                "pagebreak" -> {
                    drawPageDecorations(canvas, pageNum, style, smallPaint, dividerPaint)
                    doc.finishPage(page)
                    pageNum++
                    page = doc.startPage(PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
                    canvas = page.canvas
                    canvas.drawColor(bgColor)
                    y = margin
                    if (style.showHeader) {
                        drawHeader(canvas, style.headerText, smallPaint, margin, dividerPaint)
                        y += 25f
                    }
                }
                "text", "bullet_list", "numbered_list", "quote", "code" -> {
                    if (section.heading.isNotBlank()) {
                        y += 10f
                        y = drawWrappedText(canvas, section.heading, headingPaint, margin, y, contentW, style.lineSpacing)
                        y += 6f
                    }
                    if (section.content.isNotBlank()) {
                        when (section.type) {
                            "bullet_list" -> {
                                val items = section.content.split("\n").filter { it.isNotBlank() }
                                for (item in items) {
                                    if (y > PAGE_H - margin - 25) {
                                        drawPageDecorations(canvas, pageNum, style, smallPaint, dividerPaint)
                                        doc.finishPage(page); pageNum++
                                        page = doc.startPage(PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
                                        canvas = page.canvas; canvas.drawColor(bgColor); y = margin
                                        if (style.showHeader) { drawHeader(canvas, style.headerText, smallPaint, margin, dividerPaint); y += 25f }
                                    }
                                    y = drawWrappedText(canvas, "  •   ${item.trim()}", bodyPaint, margin + 15f, y, contentW - 15f, style.lineSpacing)
                                    y += 2f
                                }
                            }
                            "numbered_list" -> {
                                val items = section.content.split("\n").filter { it.isNotBlank() }
                                for ((idx, item) in items.withIndex()) {
                                    if (y > PAGE_H - margin - 25) {
                                        drawPageDecorations(canvas, pageNum, style, smallPaint, dividerPaint)
                                        doc.finishPage(page); pageNum++
                                        page = doc.startPage(PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
                                        canvas = page.canvas; canvas.drawColor(bgColor); y = margin
                                        if (style.showHeader) { drawHeader(canvas, style.headerText, smallPaint, margin, dividerPaint); y += 25f }
                                    }
                                    y = drawWrappedText(canvas, "  ${idx + 1}.  ${item.trim()}", bodyPaint, margin + 15f, y, contentW - 15f, style.lineSpacing)
                                    y += 2f
                                }
                            }
                            "quote" -> {
                                // Draw quote block
                                val lines = section.content.split("\n")
                                var blockHeight = 0f
                                for (l in lines) {
                                    blockHeight += measureTextLinesHeight(l, italicPaint, contentW - 24f, style.lineSpacing) + 3f
                                }
                                if (y + blockHeight + 16f > PAGE_H - margin) {
                                    drawPageDecorations(canvas, pageNum, style, smallPaint, dividerPaint)
                                    doc.finishPage(page); pageNum++
                                    page = doc.startPage(PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
                                    canvas = page.canvas; canvas.drawColor(bgColor); y = margin
                                    if (style.showHeader) { drawHeader(canvas, style.headerText, smallPaint, margin, dividerPaint); y += 25f }
                                }
                                
                                val quoteBgPaint = Paint().apply { color = Color.parseColor("#F5F7FA"); style = Paint.Style.FILL }
                                canvas.drawRect(margin, y - 4f, margin + contentW, y + blockHeight + 4f, quoteBgPaint)
                                val barPaint = Paint().apply { color = primaryColor; style = Paint.Style.FILL }
                                canvas.drawRect(margin, y - 4f, margin + 5f, y + blockHeight + 4f, barPaint)
                                
                                var qY = y + 8f
                                for (line in lines) {
                                    qY = drawWrappedText(canvas, line.trim(), italicPaint, margin + 18f, qY, contentW - 24f, style.lineSpacing)
                                    qY += 2f
                                }
                                y = qY + 12f
                            }
                            "code" -> {
                                // Draw code block
                                val lines = section.content.split("\n")
                                var blockHeight = 0f
                                for (l in lines) {
                                    blockHeight += measureTextLinesHeight(l, codePaint, contentW - 20f, 1.2f) + 3f
                                }
                                if (y + blockHeight + 16f > PAGE_H - margin) {
                                    drawPageDecorations(canvas, pageNum, style, smallPaint, dividerPaint)
                                    doc.finishPage(page); pageNum++
                                    page = doc.startPage(PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
                                    canvas = page.canvas; canvas.drawColor(bgColor); y = margin
                                    if (style.showHeader) { drawHeader(canvas, style.headerText, smallPaint, margin, dividerPaint); y += 25f }
                                }
                                
                                val codeBgPaint = Paint().apply { color = Color.parseColor("#F4F6F9"); style = Paint.Style.FILL }
                                canvas.drawRect(margin, y - 4f, margin + contentW, y + blockHeight + 4f, codeBgPaint)
                                
                                var cY = y + 8f
                                for (line in lines) {
                                    cY = drawWrappedText(canvas, line, codePaint, margin + 12f, cY, contentW - 20f, 1.2f)
                                    cY += 2f
                                }
                                y = cY + 12f
                            }
                            else -> {
                                // Paragraph
                                val result = drawWrappedTextMultiPage(doc, canvas, page, pageNum, style,
                                    section.content, bodyPaint, margin, y, contentW, dividerPaint, smallPaint)
                                canvas = result.canvas; page = result.page; pageNum = result.pageNum; y = result.y
                            }
                        }
                        y += 10f
                    }
                }
                "table" -> {
                    if (section.heading.isNotBlank()) {
                        y += 10f; y = drawWrappedText(canvas, section.heading, headingPaint, margin, y, contentW, style.lineSpacing); y += 6f
                    }
                    val result = drawTable(doc, canvas, page, pageNum, style, section.tableData, margin, y, dividerPaint, smallPaint)
                    canvas = result.canvas; page = result.page; pageNum = result.pageNum; y = result.y
                    y += 15f
                }
                "image" -> {
                    val imgKey = section.imageQuery.ifBlank { section.imageUrl }
                    val imgBytes = images[imgKey] ?: try {
                        // Support local downloads or cached content in asset/disk if needed, otherwise fallback to check
                        null
                    } catch (_: Exception) { null }

                    if (imgBytes != null) {
                        if (y > PAGE_H - margin - 220) {
                            drawPageDecorations(canvas, pageNum, style, smallPaint, dividerPaint)
                            doc.finishPage(page); pageNum++
                            page = doc.startPage(PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
                            canvas = page.canvas; canvas.drawColor(bgColor); y = margin
                            if (style.showHeader) { drawHeader(canvas, style.headerText, smallPaint, margin, dividerPaint); y += 25f }
                        }
                        if (section.heading.isNotBlank()) {
                            y += 8f; y = drawWrappedText(canvas, section.heading, subheadingPaint, margin, y, contentW, style.lineSpacing); y += 6f
                        }
                        y = drawImage(canvas, imgBytes, margin, y, contentW, 200f)
                        y += 15f
                    }
                }
            }
        }

        drawPageDecorations(canvas, pageNum, style, smallPaint, dividerPaint)
        doc.finishPage(page)

        // Save
        val dir = File(ctx.filesDir, "documents").apply { mkdirs() }
        val safeTitle = plan.title.replace(Regex("[^a-zA-Z0-9 ]"), "").replace(" ", "_").take(30).ifBlank { "Document" }
        val file = File(dir, "$safeTitle.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file.absolutePath
    }

    private fun drawHeader(canvas: Canvas, text: String, paint: Paint, margin: Float, linePaint: Paint) {
        val y = margin - 15f
        canvas.drawText(text.uppercase(Locale.getDefault()), margin, y, paint)
        canvas.drawLine(margin, y + 5f, PAGE_W - margin, y + 5f, linePaint)
    }

    private fun drawPageDecorations(canvas: Canvas, num: Int, style: DocStyleConfig, paint: Paint, linePaint: Paint) {
        val y = PAGE_H - 30f
        
        // Horizontal footer line
        if (style.showFooter || style.showPageNumbers) {
            canvas.drawLine(style.marginSize, y - 10f, PAGE_W - style.marginSize, y - 10f, linePaint)
        }
        
        // Custom Footer Text
        if (style.showFooter && style.footerText.isNotBlank()) {
            canvas.drawText(style.footerText, style.marginSize, y, paint)
        }
        
        // Page Numbering
        if (style.showPageNumbers) {
            val numStr = "Page $num"
            val width = paint.measureText(numStr)
            canvas.drawText(numStr, PAGE_W - style.marginSize - width, y, paint)
        }
    }

    private fun drawWrappedText(
        canvas: Canvas, text: String, paint: Paint, x: Float, startY: Float, maxW: Float, lineSpacing: Float
    ): Float {
        var y = startY
        val words = text.split(Regex("\\s+"))
        var line = ""
        for (word in words) {
            val test = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(test) > maxW && line.isNotEmpty()) {
                canvas.drawText(line, x, y, paint)
                y += paint.textSize * lineSpacing
                line = word
            } else {
                line = test
            }
        }
        if (line.isNotBlank()) {
            canvas.drawText(line, x, y, paint)
            y += paint.textSize * lineSpacing
        }
        return y
    }

    private fun measureTextLinesHeight(text: String, paint: Paint, maxW: Float, lineSpacing: Float): Float {
        val words = text.split(Regex("\\s+"))
        var linesCount = 0
        var line = ""
        for (word in words) {
            val test = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(test) > maxW && line.isNotEmpty()) {
                linesCount++
                line = word
            } else {
                line = test
            }
        }
        if (line.isNotBlank()) linesCount++
        return linesCount * paint.textSize * lineSpacing
    }

    private data class PageState(val canvas: Canvas, val page: PdfDocument.Page, val pageNum: Int, val y: Float)

    private fun drawWrappedTextMultiPage(
        doc: PdfDocument, c: Canvas, p: PdfDocument.Page, pn: Int, style: DocStyleConfig,
        text: String, paint: Paint, x: Float, startY: Float, maxW: Float, linePaint: Paint, labelPaint: Paint
    ): PageState {
        var canvas = c; var page = p; var pageNum = pn; var y = startY
        val paragraphs = text.split("\n")
        val margin = style.marginSize
        val bgColor = Color.parseColor(style.backgroundColorHex)

        for (para in paragraphs) {
            val words = para.split(" ")
            var line = ""
            for (word in words) {
                val test = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(test) > maxW && line.isNotEmpty()) {
                    if (y > PAGE_H - margin - 25) {
                        drawPageDecorations(canvas, pageNum, style, labelPaint, linePaint)
                        doc.finishPage(page); pageNum++
                        page = doc.startPage(PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
                        canvas = page.canvas; canvas.drawColor(bgColor); y = margin
                        if (style.showHeader) { drawHeader(canvas, style.headerText, labelPaint, margin, linePaint); y += 25f }
                    }
                    canvas.drawText(line, x, y, paint)
                    y += paint.textSize * style.lineSpacing
                    line = word
                } else {
                    line = test
                }
            }
            if (line.isNotBlank()) {
                if (y > PAGE_H - margin - 25) {
                    drawPageDecorations(canvas, pageNum, style, labelPaint, linePaint)
                    doc.finishPage(page); pageNum++
                    page = doc.startPage(PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
                    canvas = page.canvas; canvas.drawColor(bgColor); y = margin
                    if (style.showHeader) { drawHeader(canvas, style.headerText, labelPaint, margin, linePaint); y += 25f }
                }
                canvas.drawText(line, x, y, paint)
                y += paint.textSize * style.lineSpacing
            }
            y += 4f // spacing between paragraphs
        }
        return PageState(canvas, page, pageNum, y)
    }

    private fun drawTable(
        doc: PdfDocument, c: Canvas, p: PdfDocument.Page, pn: Int, style: DocStyleConfig,
        data: List<List<String>>, x: Float, startY: Float, linePaint: Paint, labelPaint: Paint
    ): PageState {
        if (data.isEmpty()) return PageState(c, p, pn, startY)
        var canvas = c; var page = p; var pageNum = pn; var y = startY
        val cols = data.maxOf { it.size }
        val contentW = PAGE_W - 2 * style.marginSize
        val colW = contentW / cols
        val rowH = 22f
        val margin = style.marginSize
        val bgColor = Color.parseColor(style.backgroundColorHex)
        val primaryColor = Color.parseColor(style.primaryColorHex)
        val textColor = Color.parseColor(style.textColorHex)

        val tableBorderPaint = Paint().apply { color = Color.parseColor("#CCCCCC"); style = Paint.Style.STROKE; strokeWidth = 0.8f }
        val tableHeaderBg = Paint().apply { color = primaryColor.adjustAlpha(0.12f); style = Paint.Style.FILL }
        val tableStripedBg = Paint().apply { color = Color.parseColor("#F7F9FB"); style = Paint.Style.FILL }
        
        val headerTextPaint = Paint().apply { color = primaryColor; textSize = BODY_SIZE - 0.5f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true }
        val rowTextPaint = Paint().apply { color = textColor; textSize = BODY_SIZE - 0.5f; isAntiAlias = true }

        for ((rowIdx, row) in data.withIndex()) {
            if (y + rowH > PAGE_H - margin) {
                drawPageDecorations(canvas, pageNum, style, labelPaint, linePaint)
                doc.finishPage(page); pageNum++
                page = doc.startPage(PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
                canvas = page.canvas; canvas.drawColor(bgColor); y = margin
                if (style.showHeader) { drawHeader(canvas, style.headerText, labelPaint, margin, linePaint); y += 25f }
            }
            
            // Draw backgrounds based on style
            if (rowIdx == 0) {
                canvas.drawRect(x, y, x + contentW, y + rowH, tableHeaderBg)
            } else if (style.tableStyle == "Striped" && rowIdx % 2 == 1) {
                canvas.drawRect(x, y, x + contentW, y + rowH, tableStripedBg)
            }
            
            // Cells
            for ((colIdx, cell) in row.withIndex()) {
                val cx = x + colIdx * colW
                
                // Draw border
                if (style.tableStyle == "Grid" || rowIdx == 0) {
                    canvas.drawRect(cx, y, cx + colW, y + rowH, tableBorderPaint)
                } else if (style.tableStyle == "Striped" || style.tableStyle == "HeaderColor") {
                    canvas.drawLine(cx, y + rowH, cx + colW, y + rowH, tableBorderPaint)
                }
                
                val paint = if (rowIdx == 0) headerTextPaint else rowTextPaint
                val text = cell.take(28)
                canvas.drawText(text, cx + CELL_PADDING, y + rowH - CELL_PADDING - 2f, paint)
            }
            y += rowH
        }
        return PageState(canvas, page, pageNum, y)
    }

    private fun drawImage(canvas: Canvas, bytes: ByteArray, x: Float, y: Float, maxW: Float, maxH: Float): Float {
        return try {
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return y
            val scale = minOf(maxW / bmp.width, maxH / bmp.height, 1f)
            val dw = bmp.width * scale
            val dh = bmp.height * scale
            val left = x + (maxW - dw) / 2 // Center
            canvas.drawBitmap(bmp, null, RectF(left, y, left + dw, y + dh), null)
            bmp.recycle()
            y + dh + 5f
        } catch (e: Exception) { y }
    }

    private fun Int.adjustAlpha(factor: Float): Int {
        val alpha = Math.round(Color.alpha(this) * factor)
        val red = Color.red(this)
        val green = Color.green(this)
        val blue = Color.blue(this)
        return Color.argb(alpha, red, green, blue)
    }
}
