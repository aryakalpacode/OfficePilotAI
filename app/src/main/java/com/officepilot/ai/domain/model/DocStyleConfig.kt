package com.officepilot.ai.domain.model

data class DocStyleConfig(
    val themeName: String = "Classic Navy",
    val primaryColorHex: String = "#0D47A1",
    val secondaryColorHex: String = "#1565C0",
    val textColorHex: String = "#212121",
    val backgroundColorHex: String = "#FFFFFF",
    val fontFamily: String = "Sans-Serif", // "Sans-Serif", "Serif", "Monospace"
    val marginSize: Float = 50f, // Compact=30f, Normal=50f, Wide=70f
    val showPageNumbers: Boolean = true,
    val showHeader: Boolean = false,
    val headerText: String = "",
    val showFooter: Boolean = false,
    val footerText: String = "",
    val lineSpacing: Float = 1.3f, // 1.0, 1.3, 1.6, 2.0
    val tableStyle: String = "Striped", // "Clean", "HeaderColor", "Striped", "Grid"
    val enablePageBreaksBeforeH2: Boolean = false
) {
    companion object {
        val THEMES = listOf(
            DocStyleConfig("Classic Navy", "#0D47A1", "#1565C0", "#212121", "#FFFFFF"),
            DocStyleConfig("Emerald Garden", "#1B5E20", "#2E7D32", "#1A251E", "#FFFFFF"),
            DocStyleConfig("Crimson Velvet", "#880E4F", "#AD1457", "#261C20", "#FFFFFF"),
            DocStyleConfig("Sunset Terracotta", "#BF360C", "#D84315", "#2E221F", "#FFFFFF"),
            DocStyleConfig("Royal Purple", "#4A148C", "#6A1B9A", "#211C28", "#FFFFFF"),
            DocStyleConfig("Charcoal Minimal", "#212121", "#424242", "#212121", "#FFFFFF"),
            DocStyleConfig("Steel Blue", "#37474F", "#455A64", "#263238", "#FFFFFF"),
            DocStyleConfig("Warm Amber", "#FF6F00", "#FF8F00", "#261D15", "#FFFFFF")
        )
    }
}
