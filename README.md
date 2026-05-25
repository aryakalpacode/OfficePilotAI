# OfficePilot AI 📄

**Your AI-Powered Office Secretary** — Create professional PDFs, Excel spreadsheets, Word documents, and more using free AI models.

## Features

### 📄 Document Generation
- **PDF** — Multi-page reports with tables, charts, images, page numbers
- **Excel (.xlsx)** — Multi-sheet workbooks with formulas, formatting, filters
- **Word (.docx)** — Professional documents with headings, tables, images
- **HTML** — Rich web reports with CSS styling and embedded charts
- **CSV** — Data export
- **ZIP** — Bundle multiple file formats together

### 🧠 AI-Powered (FREE, no credits)
- **Google Gemini 2.5 Flash** — 500 req/day, best structured output
- **Groq (Llama 3.3 70B)** — 14,400 req/day, fastest inference
- Auto-fallback between providers

### 🔍 Web Research
- DuckDuckGo web search (no API key)
- Wikipedia integration
- Wikimedia Commons images (free, high quality)

### What it can do
- "Create a 30-page annual report with revenue charts"
- "Analyze this sales data and make an Excel dashboard"  
- "Write a research paper on quantum computing with citations"
- "Generate invoices for 15 clients and ZIP them"
- "Make a project proposal with tables and images"

## Setup
1. Get free API key at [Google AI Studio](https://aistudio.google.com/app/apikey)
2. Optional: Get [Groq key](https://console.groq.com/keys) for faster processing
3. Install APK → Enter key → Start creating documents

## Tech Stack
- Kotlin + Jetpack Compose + Material 3
- Apache POI (Excel/Word generation)
- Android PdfDocument (native PDF)
- Retrofit + OkHttp (networking)
- Room (history), Hilt (DI), DataStore (prefs)
- Jsoup (web scraping), Coil (images)
