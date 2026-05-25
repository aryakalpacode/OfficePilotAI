package com.officepilot.ai.engine.docs

import android.content.Context
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFColor
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Excel XLSX generation engine using Apache POI.
 * Supports multi-sheet workbooks, formulas, formatting, and charts.
 */
@Singleton
class ExcelEngine @Inject constructor(@ApplicationContext private val ctx: Context) {

    /** Generate an Excel file from a structured JSON plan. */
    fun generate(excelPlan: JsonObject): String {
        val workbook = XSSFWorkbook()
        val title = excelPlan.get("title")?.asString ?: "Workbook"
        val fileName = (excelPlan.get("suggestedFileName")?.asString ?: title.replace(" ", "_")).take(40) + ".xlsx"

        // Styles
        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.DARK_BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            val font = workbook.createFont().apply {
                bold = true; color = IndexedColors.WHITE.index; fontHeightInPoints = 11
            }
            setFont(font)
            alignment = HorizontalAlignment.CENTER
            borderBottom = BorderStyle.THIN
        }

        val dataStyle = workbook.createCellStyle().apply {
            borderBottom = BorderStyle.THIN; borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN; borderTop = BorderStyle.THIN
            val font = workbook.createFont().apply { fontHeightInPoints = 10 }
            setFont(font)
        }

        val numberStyle = workbook.createCellStyle().apply {
            cloneStyleFrom(dataStyle)
            dataFormat = workbook.createDataFormat().getFormat("#,##0.00")
        }

        val altRowStyle = workbook.createCellStyle().apply {
            cloneStyleFrom(dataStyle)
            fillForegroundColor = IndexedColors.PALE_BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
        }

        val sheetsArr = excelPlan.getAsJsonArray("sheets")
        if (sheetsArr == null || sheetsArr.isEmpty) {
            // Fallback: create single sheet from sections
            createFallbackSheet(workbook, excelPlan, headerStyle, dataStyle, numberStyle, altRowStyle)
        } else {
            for (sheetJson in sheetsArr) {
                val s = sheetJson.asJsonObject
                val sheetName = s.get("name")?.asString ?: "Sheet"
                val sheet = workbook.createSheet(sheetName)

                // Headers
                val headers = s.getAsJsonArray("headers")
                if (headers != null && !headers.isEmpty) {
                    val headerRow = sheet.createRow(0)
                    headers.forEachIndexed { idx, h ->
                        val cell = headerRow.createCell(idx)
                        cell.setCellValue(h.asString)
                        cell.cellStyle = headerStyle
                    }
                    // Auto-filter
                    sheet.setAutoFilter(CellRangeAddress(0, 0, 0, headers.size() - 1))
                }

                // Data rows
                val rows = s.getAsJsonArray("rows")
                if (rows != null) {
                    rows.forEachIndexed { rowIdx, rowJson ->
                        val row = sheet.createRow(rowIdx + 1)
                        val cells = rowJson.asJsonArray
                        cells.forEachIndexed { colIdx, cellJson ->
                            val cell = row.createCell(colIdx)
                            val value = cellJson.asString
                            // Try to parse as number
                            val numVal = value.replace(",", "").toDoubleOrNull()
                            if (numVal != null) {
                                cell.setCellValue(numVal)
                                cell.cellStyle = if (rowIdx % 2 == 1) altRowStyle else numberStyle
                            } else {
                                cell.setCellValue(value)
                                cell.cellStyle = if (rowIdx % 2 == 1) altRowStyle else dataStyle
                            }
                        }
                    }
                }

                // Formulas
                val formulas = s.getAsJsonObject("formulas")
                formulas?.entrySet()?.forEach { (cellRef, formula) ->
                    try {
                        val col = cellRef[0] - 'A'
                        val rowNum = cellRef.substring(1).toInt() - 1
                        val row = sheet.getRow(rowNum) ?: sheet.createRow(rowNum)
                        val cell = row.getCell(col) ?: row.createCell(col)
                        cell.cellFormula = formula.asString
                    } catch (_: Exception) { }
                }

                // Column widths
                val widths = s.getAsJsonArray("columnWidths")
                if (widths != null) {
                    widths.forEachIndexed { idx, w ->
                        sheet.setColumnWidth(idx, w.asInt * 256)
                    }
                } else {
                    // Auto-size
                    val colCount = headers?.size() ?: rows?.firstOrNull()?.asJsonArray?.size() ?: 0
                    for (i in 0 until colCount) {
                        sheet.autoSizeColumn(i)
                    }
                }

                // Freeze header row
                sheet.createFreezePane(0, 1)
            }
        }

        // Save
        val dir = File(ctx.filesDir, "documents").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { workbook.write(it) }
        workbook.close()
        return file.absolutePath
    }

    /** Generate Excel from document plan sections (when AI returns doc format). */
    fun generateFromSections(title: String, sections: List<List<List<String>>>): String {
        val workbook = XSSFWorkbook()
        val headerFont = workbook.createFont().apply { bold = true; fontHeightInPoints = 11 }
        val headerStyle = workbook.createCellStyle().apply {
            setFont(headerFont); fillForegroundColor = IndexedColors.LIGHT_CORNFLOWER_BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
        }

        for ((idx, tableData) in sections.withIndex()) {
            val sheet = workbook.createSheet("Sheet ${idx + 1}")
            for ((rowIdx, rowData) in tableData.withIndex()) {
                val row = sheet.createRow(rowIdx)
                for ((colIdx, cellVal) in rowData.withIndex()) {
                    val cell = row.createCell(colIdx)
                    val numVal = cellVal.replace(",", "").toDoubleOrNull()
                    if (numVal != null) cell.setCellValue(numVal) else cell.setCellValue(cellVal)
                    if (rowIdx == 0) cell.cellStyle = headerStyle
                }
            }
            for (i in 0 until (tableData.firstOrNull()?.size ?: 0)) sheet.autoSizeColumn(i)
            sheet.createFreezePane(0, 1)
        }

        val dir = File(ctx.filesDir, "documents").apply { mkdirs() }
        val fileName = "${title.replace(Regex("[^a-zA-Z0-9]"), "_").take(40)}.xlsx"
        val file = File(dir, fileName)
        FileOutputStream(file).use { workbook.write(it) }
        workbook.close()
        return file.absolutePath
    }

    private fun createFallbackSheet(wb: XSSFWorkbook, plan: JsonObject,
                                     hs: CellStyle, ds: CellStyle, ns: CellStyle, as2: CellStyle) {
        val sheet = wb.createSheet("Data")
        val sections = plan.getAsJsonArray("sections") ?: return
        var rowNum = 0
        for (sec in sections) {
            val s = sec.asJsonObject
            val type = s.get("type")?.asString ?: "text"
            if (type == "table") {
                val td = s.getAsJsonArray("tableData") ?: continue
                for ((ri, r) in td.withIndex()) {
                    val row = sheet.createRow(rowNum++)
                    r.asJsonArray.forEachIndexed { ci, c ->
                        val cell = row.createCell(ci)
                        val v = c.asString
                        val n = v.replace(",", "").toDoubleOrNull()
                        if (n != null) cell.setCellValue(n) else cell.setCellValue(v)
                        cell.cellStyle = if (ri == 0) hs else ds
                    }
                }
                rowNum++
            }
        }
    }
}
