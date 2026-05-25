package com.officepilot.ai.ui.screens.setup

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.officepilot.ai.R
import com.officepilot.ai.data.repository.PrefsRepository
import com.officepilot.ai.domain.model.AiProvider
import com.officepilot.ai.engine.ai.AiEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupState(
    val geminiKey: String = "", val groqKey: String = "",
    val geminiStatus: String = "", val groqStatus: String = "",
    val testing: Boolean = false, val canProceed: Boolean = false
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val prefs: PrefsRepository, private val ai: AiEngine
) : ViewModel() {
    private val _s = MutableStateFlow(SetupState())
    val state: StateFlow<SetupState> = _s.asStateFlow()

    fun setGemini(k: String) { _s.update { it.copy(geminiKey = k, geminiStatus = "", canProceed = k.isNotBlank() || it.groqKey.isNotBlank()) } }
    fun setGroq(k: String) { _s.update { it.copy(groqKey = k, groqStatus = "", canProceed = it.geminiKey.isNotBlank() || k.isNotBlank()) } }

    fun testGemini() { viewModelScope.launch {
        _s.update { it.copy(testing = true, geminiStatus = "Testing...") }
        val r = ai.testKey(AiProvider.GEMINI, _s.value.geminiKey)
        _s.update { it.copy(testing = false, geminiStatus = if (r.isSuccess) "✓ Valid" else "✗ ${r.exceptionOrNull()?.message}") }
    }}

    fun testGroq() { viewModelScope.launch {
        _s.update { it.copy(testing = true, groqStatus = "Testing...") }
        val r = ai.testKey(AiProvider.GROQ, _s.value.groqKey)
        _s.update { it.copy(testing = false, groqStatus = if (r.isSuccess) "✓ Valid" else "✗ ${r.exceptionOrNull()?.message}") }
    }}

    fun save(onDone: () -> Unit) { viewModelScope.launch {
        val s = _s.value
        if (s.geminiKey.isNotBlank()) prefs.setGeminiKey(s.geminiKey)
        if (s.groqKey.isNotBlank()) prefs.setGroqKey(s.groqKey)
        prefs.setSetupDone(true)
        onDone()
    }}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(vm: SetupViewModel = hiltViewModel(), onDone: () -> Unit) {
    val s by vm.state.collectAsState()
    val ctx = LocalContext.current

    Scaffold(topBar = { TopAppBar(title = { Text("OfficePilot AI") }) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally) {

            Icon(Icons.Default.Description, null, Modifier.size(64.dp), MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.setup_welcome), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.setup_desc), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(28.dp))

            // Gemini key
            Text("Google Gemini API Key (recommended)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            var showGemini by remember { mutableStateOf(false) }
            OutlinedTextField(s.geminiKey, vm::setGemini, Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("AIza...") },
                visualTransformation = if (showGemini) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { IconButton({ showGemini = !showGemini }) {
                    Icon(if (showGemini) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                }})
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton({ ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))) }) {
                    Text("Get Free Key ↗") }
                if (s.geminiKey.isNotBlank()) TextButton(vm::testGemini, enabled = !s.testing) { Text("Test") }
            }
            if (s.geminiStatus.isNotBlank()) Text(s.geminiStatus, style = MaterialTheme.typography.bodySmall,
                color = if (s.geminiStatus.startsWith("✓")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)

            Spacer(Modifier.height(20.dp))

            // Groq key
            Text("Groq API Key (optional, faster)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(6.dp))
            var showGroq by remember { mutableStateOf(false) }
            OutlinedTextField(s.groqKey, vm::setGroq, Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("gsk_...") },
                visualTransformation = if (showGroq) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { IconButton({ showGroq = !showGroq }) {
                    Icon(if (showGroq) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                }})
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton({ ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com/keys"))) }) {
                    Text("Get Free Key ↗") }
                if (s.groqKey.isNotBlank()) TextButton(vm::testGroq, enabled = !s.testing) { Text("Test") }
            }
            if (s.groqStatus.isNotBlank()) Text(s.groqStatus, style = MaterialTheme.typography.bodySmall,
                color = if (s.groqStatus.startsWith("✓")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)

            Spacer(Modifier.height(32.dp))

            Button({ vm.save(onDone) }, Modifier.fillMaxWidth().height(52.dp), enabled = s.canProceed) {
                Text(stringResource(R.string.save_continue), style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(12.dp))
            Text("Free tier: Gemini 500 req/day • Groq 14,400 req/day",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
