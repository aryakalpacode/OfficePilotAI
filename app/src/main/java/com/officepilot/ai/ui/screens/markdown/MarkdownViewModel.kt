package com.officepilot.ai.ui.screens.markdown

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.officepilot.ai.data.local.dao.FileDao
import com.officepilot.ai.data.local.entity.GeneratedFileEntity
import com.officepilot.ai.domain.model.DocFormat
import com.officepilot.ai.domain.model.DocStyleConfig
import com.officepilot.ai.engine.DocOrchestrator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed class ConversionState {
    data object Idle : ConversionState()
    data object Converting : ConversionState()
    data class Success(val filePath: String, val fileName: String, val format: DocFormat) : ConversionState()
    data class Error(val message: String) : ConversionState()
}

@HiltViewModel
class MarkdownViewModel @Inject constructor(
    private val orchestrator: DocOrchestrator,
    private val fileDao: FileDao,
    @ApplicationContext private val ctx: Context
) : ViewModel() {

    private val _markdownText = MutableStateFlow(DEFAULT_SAMPLE)
    val markdownText: StateFlow<String> = _markdownText.asStateFlow()

    private val _styleConfig = MutableStateFlow(DocStyleConfig())
    val styleConfig: StateFlow<DocStyleConfig> = _styleConfig.asStateFlow()

    private val _selectedFormat = MutableStateFlow(DocFormat.PDF)
    val selectedFormat: StateFlow<DocFormat> = _selectedFormat.asStateFlow()

    private val _conversionState = MutableStateFlow<ConversionState>(ConversionState.Idle)
    val conversionState: StateFlow<ConversionState> = _conversionState.asStateFlow()

    val recentFiles: StateFlow<List<GeneratedFileEntity>> = fileDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateMarkdown(text: String) {
        _markdownText.value = text
    }

    fun updateStyle(config: DocStyleConfig) {
        _styleConfig.value = config
    }

    fun selectFormat(format: DocFormat) {
        _selectedFormat.value = format
    }

    fun resetState() {
        _conversionState.value = ConversionState.Idle
    }

    fun convert() {
        val markdown = _markdownText.value
        val format = _selectedFormat.value
        val style = _styleConfig.value

        if (markdown.isBlank()) {
            _conversionState.value = ConversionState.Error("Markdown text cannot be empty!")
            return
        }

        _conversionState.value = ConversionState.Converting

        viewModelScope.launch {
            val result = orchestrator.convertMarkdown(markdown, format, style)
            result.fold(
                onSuccess = { path ->
                    val file = File(path)
                    val name = file.name
                    
                    // Save file history record
                    fileDao.insert(
                        GeneratedFileEntity(
                            conversationId = null,
                            fileName = name,
                            filePath = path,
                            format = format.ext,
                            sizeBytes = file.length()
                        )
                    )
                    
                    _conversionState.value = ConversionState.Success(path, name, format)
                },
                onFailure = { err ->
                    _conversionState.value = ConversionState.Error(err.message ?: "Conversion failed")
                }
            )
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
        } catch (_: Exception) {}
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
        } catch (_: Exception) {}
    }

    fun loadSample(sample: String) {
        _markdownText.value = sample
    }

    companion object {
        val DEFAULT_SAMPLE = """
# Annual Sales & Strategy Report

## Executive Summary
This document provides an in-depth review of our performance during the past fiscal year, detailing key milestones, regional contributions, and the action plan for Q3 and Q4. We have seen tremendous growth in enterprise sales, driven by our custom integrations and streamlined onboarding process.

> "Our focus remains on delivering reliable, automated office processing solutions to power teams of all sizes around the world."

---

## Performance Overview
We achieved double-digit growth in three of our major markets. Below is the breakdown of the quarterly milestones and operational achievements.

- **Q1 Launch**: Successfully deployed our cloud-native workflow platform.
- **Q2 Integration**: Completed the native MS Office and PDF rendering engine upgrades.
- **Q3 Scaling**: Handled over 10 million automated report transactions with 99.99% uptime.
- **Q4 Automation**: Unveiled AI-driven assistants for document templating and formatting.

---

## Regional Sales Breakdown
Below is the verified revenue report for the continental divisions. These metrics include software-as-a-service subscriptions and priority support plans.

| Quarter | North America | Europe | Asia-Pacific |
|:---|:---|:---|:---|
| Q1 Revenue | $2.4M | $1.8M | $1.2M |
| Q2 Revenue | $3.1M | $2.2M | $1.5M |
| Q3 Revenue | $3.8M | $2.9M | $1.9M |
| Q4 Revenue | $4.5M | $3.4M | $2.2M |

---

## System Configurations
To build the report automatically on our localized build runners, developers can utilize the following build automation script snippet.

```bash
# Clean the workspace and compile project
./gradlew clean assembleRelease

# Run unit tests
./gradlew test

# Bundle assets and output beautiful reports
python tools/build_docs.py --input report.md --output final.pdf --theme "Classic Navy"
```

## Future Directives
We aim to transition into fully automated workflows by Q1 next year. Our core engineering deliverables will prioritize security, scalability, and custom branding templates for enterprise clients.
        """.trimIndent()

        val RESUME_SAMPLE = """
# Jane Doe
### Senior Product Designer • San Francisco, CA • jane.doe@example.com

## Professional Summary
Accomplished Senior Product Designer with over 8 years of experience building mobile and web applications. Expert in typography, branding, design systems, and rapid prototyping. Passionate about translating complex user journeys into elegant, intuitive interfaces.

---

## Key Expertise
- **Design Systems**: Material 3, iOS HIG, Figma Component Libraries.
- **Visual Design**: High-fidelity UI mockups, responsive grid layouts, custom icons.
- **Prototyping**: Framer, Principle, interactive micro-interactions.
- **Research**: User personas, A/B testing, cognitive mapping, quantitative surveys.

---

## Experience

### Senior UI/UX Designer | Acme Corp (2022 - Present)
- Directed the redesign of the flagship mobile app, leading to a **35% increase in user retention** and a 4.8 App Store rating.
- Designed and maintained the multi-platform design system, saving the engineering team 15+ hours weekly in front-end development.
- Mentored 4 junior designers and collaborated closely with product managers and engineering directors to implement polished interfaces.

### Interaction Designer | TechWave Inc (2018 - 2022)
- Created interactive prototypes for SaaS web applications, validating concepts through intensive weekly user research labs.
- Scaled the onboarding experience to reduce drop-off rates by **45%** inside the first 30 days.
- Designed custom charts and dashboard reports for data-heavy analytical services.

---

## Education
- **B.S. in Graphic Communication** | Stanford University (2014 - 2018)
- **Advanced HCI Certification** | Nielsen Norman Group (2019)
        """.trimIndent()

        val TECH_DOC_SAMPLE = """
# API Integration & SDK Guide

## Introduction
Welcome to the Developer SDK Documentation. This manual guides you through configuring our security tokens, authenticating API requests, and listening to live Webhook events.

---

## Getting Started
First, initialize the SDK using your client credentials and regional target gateway.

```kotlin
import com.developer.sdk.Gateway
import com.developer.sdk.Config

val config = Config(
    apiKey = "sk_live_92A83df01aB",
    environment = Config.Environment.PRODUCTION,
    timeoutMs = 5000
)

val client = Gateway.initialize(config)
println("Gateway is fully connected and ready.")
```

---

## Authentication Protocols
All REST requests must be secured with a bearer token in the headers of your request.

| Header | Value Type | Description | Required |
|:---|:---|:---|:---|
| Authorization | String | Bearer <api_token> | Yes |
| Content-Type | String | application/json | Yes |
| X-Idempotency | String | Unique UUID v4 string | No |

---

## Response Error Codes
Our API returns standardized HTTP response codes to denote success or specific errors:

1. **200 OK**: The action was processed successfully.
2. **400 Bad Request**: Missing parameters or malformed body parameters.
3. **401 Unauthorized**: Invalid or expired authentication bearer token.
4. **404 Not Found**: The requested resource ID does not exist on this cluster.
5. **500 Internal Error**: An unexpected issue occurred on our database node.
        """.trimIndent()
    }
}
