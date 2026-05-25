package com.officepilot.ai.ui.screens.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.officepilot.ai.domain.model.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: HomeViewModel = hiltViewModel(), onSettings: () -> Unit) {
    val s by vm.state.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Auto-scroll
    LaunchedEffect(s.messages.size) {
        if (s.messages.isNotEmpty()) scope.launch { listState.animateScrollToItem(s.messages.size - 1) }
    }

    val isGenerating = s.genState !is GenerationState.Idle && s.genState !is GenerationState.Done && s.genState !is GenerationState.Error

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Description, null, Modifier.size(28.dp), MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("OfficePilot AI", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(vm::newSession) { Icon(Icons.Default.Add, "New") }
                    IconButton(onSettings) { Icon(Icons.Default.Settings, "Settings") }
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {

            // ─── Status Bar ───
            val genState = s.genState
            if (genState !is GenerationState.Idle) {
                StatusBar(genState)
            }

            // ─── Messages ───
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                if (s.messages.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Description, null, Modifier.size(56.dp),
                                    MaterialTheme.colorScheme.primary.copy(0.4f))
                                Spacer(Modifier.height(12.dp))
                                Text("What can I create for you?", style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                Text("Try: \"Create a sales report with Q1 revenue $50K, Q2 $72K\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                items(s.messages) { msg ->
                    MessageBubble(msg, onOpen = { vm.openFile(it) }, onShare = { vm.shareFile(it) })
                }

                // Loading
                if (isGenerating) {
                    item {
                        val pulse = rememberInfiniteTransition(label = "p")
                        val alpha by pulse.animateFloat(0.4f, 1f,
                            infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a")
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Generating...", Modifier.alpha(alpha),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // ─── Format Selector ───
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DocFormat.entries.filter { it != DocFormat.JSON && it != DocFormat.MD }.forEach { fmt ->
                    FilterChip(
                        selected = s.selectedFormat == fmt,
                        onClick = { vm.selectFormat(fmt) },
                        label = { Text(fmt.label, fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(when (fmt) {
                                DocFormat.PDF -> Icons.Default.PictureAsPdf
                                DocFormat.XLSX -> Icons.Default.TableChart
                                DocFormat.DOCX -> Icons.Default.Article
                                DocFormat.HTML -> Icons.Default.Language
                                DocFormat.CSV -> Icons.Default.Storage
                                DocFormat.ZIP -> Icons.Default.FolderZip
                                else -> Icons.Default.InsertDriveFile
                            }, null, Modifier.size(16.dp))
                        }
                    )
                }
            }

            // ─── Input ───
            Surface(tonalElevation = 2.dp) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
                    OutlinedTextField(
                        s.inputText, vm::updateInput, Modifier.weight(1f),
                        placeholder = { Text("Describe what you need...") },
                        maxLines = 5, enabled = !isGenerating
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = vm::send,
                        enabled = s.inputText.isNotBlank() && !isGenerating
                    ) { Icon(Icons.Default.Send, "Generate") }
                }
            }
        }
    }
}

@Composable
private fun StatusBar(state: GenerationState) {
    val (icon, text, color) = when (state) {
        is GenerationState.Thinking -> Triple(Icons.Default.Psychology, state.detail.ifBlank { "Thinking..." }, MaterialTheme.colorScheme.primary)
        is GenerationState.Searching -> Triple(Icons.Default.Search, "Searching: ${state.query}", MaterialTheme.colorScheme.secondary)
        is GenerationState.FetchingImages -> Triple(Icons.Default.Image, "Finding images...", MaterialTheme.colorScheme.tertiary)
        is GenerationState.Building -> Triple(Icons.Default.Construction, "Building ${state.format.label}...", MaterialTheme.colorScheme.primary)
        is GenerationState.Done -> Triple(Icons.Default.CheckCircle, "Done! ${state.fileName}", Color(0xFF10B981))
        is GenerationState.Error -> Triple(Icons.Default.ErrorOutline, state.message, MaterialTheme.colorScheme.error)
        else -> return
    }
    Surface(color = color.copy(0.1f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp), color)
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (state is GenerationState.Building && state.progress >= 0) {
                Spacer(Modifier.width(8.dp))
                LinearProgressIndicator(progress = { state.progress }, Modifier.weight(1f).height(3.dp), color = color)
            } else if (state !is GenerationState.Done && state !is GenerationState.Error) {
                Spacer(Modifier.width(8.dp))
                LinearProgressIndicator(Modifier.weight(1f).height(3.dp), color = color)
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage, onOpen: (String) -> Unit, onShare: (String) -> Unit) {
    val isUser = msg.role == "user"
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Icon(Icons.Default.SmartToy, null, Modifier.size(28.dp).padding(top = 4.dp), MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.widthIn(max = 320.dp)) {
            Surface(
                shape = RoundedCornerShape(if (isUser) 16.dp else 4.dp, 16.dp, 16.dp, if (isUser) 4.dp else 16.dp),
                color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = if (isUser) 0.dp else 1.dp
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(msg.content, color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium)

                    // File action buttons
                    if (msg.filePath != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { onOpen(msg.filePath) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                                Icon(Icons.Default.OpenInNew, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Open", fontSize = 12.sp)
                            }
                            OutlinedButton(onClick = { onShare(msg.filePath) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                                Icon(Icons.Default.Share, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Share", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
