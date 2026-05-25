package com.officepilot.ai.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.officepilot.ai.data.local.dao.ConversationDao
import com.officepilot.ai.data.repository.PrefsRepository
import com.officepilot.ai.domain.model.DocFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: PrefsRepository,
    private val convDao: ConversationDao
) : ViewModel() {
    val geminiKey = prefs.geminiKey.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val groqKey = prefs.groqKey.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val defaultFormat = prefs.defaultFormat.stateIn(viewModelScope, SharingStarted.Lazily, "pdf")

    fun setDefaultFormat(f: String) { viewModelScope.launch { prefs.setDefaultFormat(f) } }
    fun clearHistory() { viewModelScope.launch { convDao.deleteAll() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel(), onBack: () -> Unit) {
    val gKey by vm.geminiKey.collectAsState()
    val grKey by vm.groqKey.collectAsState()
    val fmt by vm.defaultFormat.collectAsState()
    val ctx = LocalContext.current

    Scaffold(topBar = {
        TopAppBar(title = { Text("Settings") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Back") } })
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            // API Keys
            Text("API Keys", Modifier.padding(16.dp, 12.dp), style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary)
            ListItem(headlineContent = { Text("Google Gemini") },
                supportingContent = { Text(if (gKey.isNotBlank()) "••••${gKey.takeLast(6)}" else "Not set") },
                leadingContent = { Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey")))
                })
            ListItem(headlineContent = { Text("Groq") },
                supportingContent = { Text(if (grKey.isNotBlank()) "••••${grKey.takeLast(6)}" else "Not set (optional)") },
                leadingContent = { Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.secondary) })

            HorizontalDivider(Modifier.padding(horizontal = 16.dp))

            // Default format
            Text("Default Output Format", Modifier.padding(16.dp, 12.dp), style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary)
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("pdf", "xlsx", "docx", "html").forEach { f ->
                    FilterChip(fmt == f, { vm.setDefaultFormat(f) },
                        label = { Text(f.uppercase()) })
                }
            }

            HorizontalDivider(Modifier.padding(16.dp))

            // Data
            var showClear by remember { mutableStateOf(false) }
            ListItem(headlineContent = { Text("Clear History") },
                leadingContent = { Icon(Icons.Default.DeleteSweep, null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable { showClear = true })

            if (showClear) {
                AlertDialog({ showClear = false }, confirmButton = {
                    Button({ vm.clearHistory(); showClear = false },
                        colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error)) { Text("Clear") }
                }, dismissButton = { TextButton({ showClear = false }) { Text("Cancel") } },
                    title = { Text("Clear all history?") },
                    text = { Text("This deletes all conversations. Generated files will remain.") })
            }

            HorizontalDivider(Modifier.padding(16.dp))
            ListItem(headlineContent = { Text("About") },
                supportingContent = { Text("OfficePilot AI v1.0 — AI-powered document secretary") },
                leadingContent = { Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary) })
            Spacer(Modifier.height(32.dp))
        }
    }
}
