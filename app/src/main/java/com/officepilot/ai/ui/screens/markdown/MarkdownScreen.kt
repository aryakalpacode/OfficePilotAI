package com.officepilot.ai.ui.screens.markdown

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.officepilot.ai.domain.model.DocFormat
import com.officepilot.ai.domain.model.DocStyleConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownScreen(
    vm: MarkdownViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val markdown by vm.markdownText.collectAsState()
    val style by vm.styleConfig.collectAsState()
    val format by vm.selectedFormat.collectAsState()
    val conversion by vm.conversionState.collectAsState()

    var showStylePanel by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Article, "MD Icon", Modifier.size(26.dp), MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Markdown Workshop", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showStylePanel = !showStylePanel }) {
                        Icon(
                            imageVector = if (showStylePanel) Icons.Default.Tune else Icons.Default.FilterList,
                            contentDescription = "Toggle Customizer",
                            tint = if (showStylePanel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { pad ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ─── TEMPLATES / SAMPLE LOADERS ───
                Column {
                    Text(
                        text = "Load A Sample Template",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SuggestionChip(
                            onClick = { vm.loadSample(MarkdownViewModel.DEFAULT_SAMPLE) },
                            label = { Text("📄 Sales Report") }
                        )
                        SuggestionChip(
                            onClick = { vm.loadSample(MarkdownViewModel.RESUME_SAMPLE) },
                            label = { Text("👤 Resume / CV") }
                        )
                        SuggestionChip(
                            onClick = { vm.loadSample(MarkdownViewModel.TECH_DOC_SAMPLE) },
                            label = { Text("⚙️ Tech Integration") }
                        )
                    }
                }

                // ─── STYLE CUSTOMIZER PANEL ───
                AnimatedVisibility(
                    visible = showStylePanel,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    StyleCustomizerCard(
                        style = style,
                        onStyleChange = vm::updateStyle
                    )
                }

                // ─── MARKDOWN INPUT FIELD ───
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Write or Paste Markdown",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${markdown.lines().size} lines",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = markdown,
                        onValueChange = vm::updateMarkdown,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 250.dp, max = 500.dp),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        ),
                        placeholder = { Text("Start typing markdown here... Use # for headers, - for lists, etc.") },
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // ─── OUTPUT FORMAT CHIPS ───
                Column {
                    Text(
                        text = "Choose Export Format",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(DocFormat.PDF, DocFormat.DOCX, DocFormat.HTML, DocFormat.ZIP, DocFormat.MD, DocFormat.CSV).forEach { fmt ->
                            FilterChip(
                                selected = format == fmt,
                                onClick = { vm.selectFormat(fmt) },
                                label = { Text(fmt.label) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = when (fmt) {
                                            DocFormat.PDF -> Icons.Default.PictureAsPdf
                                            DocFormat.DOCX -> Icons.Default.Article
                                            DocFormat.HTML -> Icons.Default.Language
                                            DocFormat.ZIP -> Icons.Default.FolderZip
                                            DocFormat.CSV -> Icons.Default.Storage
                                            else -> Icons.Default.InsertDriveFile
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                // ─── ACTION BUTTONS ───
                Button(
                    onClick = { vm.convert() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Construction, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Render Beautiful Document", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Spacer(Modifier.height(40.dp))
            }

            // ─── CONVERSION STATES OVERLAYS / DIALOGS ───
            when (val state = conversion) {
                is ConversionState.Converting -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(16.dp))
                                Text("Parsing & Formatting...", fontWeight = FontWeight.SemiBold)
                                Text("Converting Markdown instantly", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                is ConversionState.Success -> {
                    AlertDialog(
                        onDismissRequest = { vm.resetState() },
                        icon = { Icon(Icons.Default.CheckCircle, "Success", tint = Color(0xFF10B981), modifier = Modifier.size(40.dp)) },
                        title = { Text("Conversion Complete", textAlign = TextAlign.Center) },
                        text = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                Text(state.fileName, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                Spacer(Modifier.height(4.dp))
                                Text("Format: ${state.format.label} Document", style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = { vm.openFile(state.filePath) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.OpenInNew, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Open Document")
                            }
                        },
                        dismissButton = {
                            OutlinedButton(
                                onClick = { vm.shareFile(state.filePath) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Share, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Share / Send")
                            }
                        }
                    )
                }
                is ConversionState.Error -> {
                    AlertDialog(
                        onDismissRequest = { vm.resetState() },
                        icon = { Icon(Icons.Default.ErrorOutline, "Error", tint = MaterialTheme.colorScheme.error) },
                        title = { Text("Failed to Render") },
                        text = { Text(state.message) },
                        confirmButton = {
                            TextButton(onClick = { vm.resetState() }) {
                                Text("Dismiss")
                            }
                        }
                    )
                }
                else -> {}
            }
        }
    }
}

@Composable
fun StyleCustomizerCard(
    style: DocStyleConfig,
    onStyleChange: (DocStyleConfig) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.primary)
                Text("Theme & Document Styles", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }

            // ─── THEME SELECTOR ───
            Column {
                Text("Select Style Palette", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DocStyleConfig.THEMES.forEach { theme ->
                        val isSelected = style.themeName == theme.themeName
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onStyleChange(
                                        style.copy(
                                            themeName = theme.themeName,
                                            primaryColorHex = theme.primaryColorHex,
                                            secondaryColorHex = theme.secondaryColorHex,
                                            textColorHex = theme.textColorHex,
                                            backgroundColorHex = theme.backgroundColorHex
                                        )
                                    )
                                }
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .padding(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.parseColor(theme.primaryColorHex))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                        shape = CircleShape
                                    )
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(theme.themeName, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }

            Divider()

            // ─── TYPOGRAPHY SELECTOR ───
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Typography Font", style = MaterialTheme.typography.labelMedium)
                    Text("Applies to headlines and body", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Sans-Serif", "Serif", "Monospace").forEach { font ->
                        val isSelected = style.fontFamily == font
                        ElevatedFilterChip(
                            selected = isSelected,
                            onClick = { onStyleChange(style.copy(fontFamily = font)) },
                            label = { Text(font, fontSize = 11.sp) }
                        )
                    }
                }
            }

            Divider()

            // ─── MARGINS & SPACING ───
            Column {
                Text("Page Margins", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Compact" to 30f, "Normal" to 50f, "Wide" to 70f).forEach { (label, size) ->
                        val isSelected = style.marginSize == size
                        FilterChip(
                            selected = isSelected,
                            onClick = { onStyleChange(style.copy(marginSize = size)) },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Line Spacing", style = MaterialTheme.typography.labelMedium)
                    Text("Line gap height multiplier", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1.0f, 1.3f, 1.6f, 2.0f).forEach { space ->
                        val isSelected = style.lineSpacing == space
                        ElevatedFilterChip(
                            selected = isSelected,
                            onClick = { onStyleChange(style.copy(lineSpacing = space)) },
                            label = { Text("${space}x", fontSize = 11.sp) }
                        )
                    }
                }
            }

            Divider()

            // ─── HEADERS & FOOTERS ───
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Include Running Header", style = MaterialTheme.typography.labelMedium)
                        Text("Add custom text at the top of pages", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = style.showHeader,
                        onCheckedChange = { onStyleChange(style.copy(showHeader = it)) }
                    )
                }
                if (style.showHeader) {
                    OutlinedTextField(
                        value = style.headerText,
                        onValueChange = { onStyleChange(style.copy(headerText = it)) },
                        label = { Text("Header Text") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Include Running Footer", style = MaterialTheme.typography.labelMedium)
                        Text("Add signature text at bottom left", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = style.showFooter,
                        onCheckedChange = { onStyleChange(style.copy(showFooter = it)) }
                    )
                }
                if (style.showFooter) {
                    OutlinedTextField(
                        value = style.footerText,
                        onValueChange = { onStyleChange(style.copy(footerText = it)) },
                        label = { Text("Footer Text") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Page Numbers", style = MaterialTheme.typography.labelMedium)
                        Text("Print 'Page X' at the bottom right", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = style.showPageNumbers,
                        onCheckedChange = { onStyleChange(style.copy(showPageNumbers = it)) }
                    )
                }
            }

            Divider()

            // ─── EXTRA LAYOUT CONFIGS ───
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Page Breaks at Headlines", style = MaterialTheme.typography.labelMedium)
                        Text("Insert automatic page break before ## elements", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = style.enablePageBreaksBeforeH2,
                        onCheckedChange = { onStyleChange(style.copy(enablePageBreaksBeforeH2 = it)) }
                    )
                }

                Column {
                    Text("Table Grid Style", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Striped", "Grid", "HeaderColor", "Clean").forEach { tblStyle ->
                            val isSelected = style.tableStyle == tblStyle
                            FilterChip(
                                selected = isSelected,
                                onClick = { onStyleChange(style.copy(tableStyle = tblStyle)) },
                                label = { Text(tblStyle, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Color.Companion.parseColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        Color.DarkGray
    }
}
