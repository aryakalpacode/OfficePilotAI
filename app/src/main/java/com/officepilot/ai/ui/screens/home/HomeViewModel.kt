package com.officepilot.ai.ui.screens.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.officepilot.ai.data.local.dao.ConversationDao
import com.officepilot.ai.data.local.dao.FileDao
import com.officepilot.ai.data.local.dao.MessageDao
import com.officepilot.ai.data.local.entity.ConversationEntity
import com.officepilot.ai.data.local.entity.GeneratedFileEntity
import com.officepilot.ai.data.local.entity.MessageEntity
import com.officepilot.ai.data.repository.PrefsRepository
import com.officepilot.ai.domain.model.*
import com.officepilot.ai.engine.DocOrchestrator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class HomeState(
    val messages: List<ChatMessage> = emptyList(),
    val genState: GenerationState = GenerationState.Idle,
    val inputText: String = "",
    val selectedFormat: DocFormat = DocFormat.PDF,
    val recentFiles: List<GeneratedFileEntity> = emptyList(),
    val conversationId: Long = 0
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val orchestrator: DocOrchestrator,
    private val convDao: ConversationDao,
    private val msgDao: MessageDao,
    private val fileDao: FileDao,
    private val prefs: PrefsRepository,
    @ApplicationContext private val ctx: Context
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        // Observe generation state
        viewModelScope.launch {
            orchestrator.state.collect { genState ->
                _state.update { it.copy(genState = genState) }
            }
        }
        // Load recent files
        viewModelScope.launch {
            fileDao.getAll().collect { files ->
                _state.update { it.copy(recentFiles = files.take(10)) }
            }
        }
        // Create/load conversation
        viewModelScope.launch {
            val conv = convDao.insert(ConversationEntity(title = "New Session"))
            _state.update { it.copy(conversationId = conv) }
        }
        // Default format
        viewModelScope.launch {
            prefs.defaultFormat.collect { fmt ->
                val format = DocFormat.entries.find { it.ext == fmt } ?: DocFormat.PDF
                _state.update { it.copy(selectedFormat = format) }
            }
        }
    }

    fun updateInput(text: String) { _state.update { it.copy(inputText = text) } }

    fun selectFormat(f: DocFormat) { _state.update { it.copy(selectedFormat = f) } }

    fun send() {
        val text = _state.value.inputText.trim()
        if (text.isBlank()) return
        val format = _state.value.selectedFormat
        val convId = _state.value.conversationId

        // Add user message to UI
        val userMsg = ChatMessage(role = "user", content = text)
        _state.update { it.copy(
            messages = it.messages + userMsg,
            inputText = ""
        )}

        viewModelScope.launch {
            // Save user message
            msgDao.insert(MessageEntity(conversationId = convId, role = "user", content = text))
            convDao.rename(convId, text.take(50))

            // Generate
            val result = orchestrator.generate(text, format)

            result.fold(
                onSuccess = { done ->
                    // Save assistant message
                    val assistMsg = ChatMessage(
                        role = "assistant",
                        content = "✅ Created **${done.fileName}**\n(${done.format.label} document)",
                        filePath = done.filePath,
                        fileFormat = done.format.ext
                    )
                    _state.update { it.copy(messages = it.messages + assistMsg) }
                    msgDao.insert(MessageEntity(
                        conversationId = convId, role = "assistant",
                        content = assistMsg.content, filePath = done.filePath, fileFormat = done.format.ext
                    ))
                    // Save file record
                    val file = File(done.filePath)
                    fileDao.insert(GeneratedFileEntity(
                        conversationId = convId, fileName = done.fileName,
                        filePath = done.filePath, format = done.format.ext,
                        sizeBytes = file.length()
                    ))
                },
                onFailure = { err ->
                    val errMsg = ChatMessage(role = "assistant", content = "❌ Error: ${err.message}")
                    _state.update { it.copy(messages = it.messages + errMsg) }
                    msgDao.insert(MessageEntity(
                        conversationId = convId, role = "assistant", content = errMsg.content
                    ))
                }
            )
            orchestrator.resetState()
        }
    }

    /** Chat without generating a document. */
    fun sendChat() {
        val text = _state.value.inputText.trim()
        if (text.isBlank()) return
        val convId = _state.value.conversationId

        val userMsg = ChatMessage(role = "user", content = text)
        _state.update { it.copy(messages = it.messages + userMsg, inputText = "") }

        viewModelScope.launch {
            msgDao.insert(MessageEntity(conversationId = convId, role = "user", content = text))

            val history = _state.value.messages.takeLast(10).map { it.role to it.content }
            val result = orchestrator.chat(text, history)

            val reply = result.getOrElse { "Sorry, I couldn't process that: ${it.message}" }
            val assistMsg = ChatMessage(role = "assistant", content = reply)
            _state.update { it.copy(messages = it.messages + assistMsg) }
            msgDao.insert(MessageEntity(conversationId = convId, role = "assistant", content = reply))
            orchestrator.resetState()
        }
    }

    fun openFile(path: String) {
        try {
            val file = File(path)
            if (!file.exists()) return
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val mime = when (file.extension.lowercase()) {
                "pdf" -> "application/pdf"
                "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                "html" -> "text/html"
                "csv" -> "text/csv"
                "zip" -> "application/zip"
                else -> "*/*"
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        } catch (_: Exception) { }
    }

    fun shareFile(path: String) {
        try {
            val file = File(path)
            if (!file.exists()) return
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(Intent.createChooser(intent, "Share ${file.name}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) { }
    }

    fun newSession() {
        viewModelScope.launch {
            val conv = convDao.insert(ConversationEntity(title = "New Session"))
            _state.update { it.copy(conversationId = conv, messages = emptyList()) }
            orchestrator.resetState()
        }
    }
}
