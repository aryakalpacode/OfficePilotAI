package com.officepilot.ai.util

object C {
    const val DB_NAME = "officepilot_db"
    const val GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/openai/"
    const val GROQ_BASE = "https://api.groq.com/openai/v1/"
    const val OPENROUTER_BASE = "https://openrouter.ai/api/"
    const val DDG_BASE = "https://html.duckduckgo.com/"
    const val WIKI_API = "https://en.wikipedia.org/w/api.php"
    const val WIKIMEDIA_API = "https://commons.wikimedia.org/w/api.php"

    const val GEMINI_MODEL = "gemini-2.5-flash"
    const val GROQ_MODEL = "llama-3.3-70b-versatile"
    const val OPENROUTER_MODEL = "deepseek/deepseek-v4-flash:free"

    const val MAX_OUTPUT_TOKENS = 8192
    const val TEMPERATURE = 0.4

    const val PREFS = "officepilot_prefs"
    const val PREF_GEMINI_KEY = "gemini_key"
    const val PREF_GROQ_KEY = "groq_key"
    const val PREF_OPENROUTER_KEY = "openrouter_key"
    const val PREF_SETUP_DONE = "setup_done"
    const val PREF_DEFAULT_FORMAT = "default_format"
    const val PREF_THEME = "theme"

    const val DOCS_DIR = "documents"
    const val WORKSPACE_DIR = "workspace"

    const val NOTIFICATION_CHANNEL = "doc_generation"
    const val SERVICE_ID = 2001
}
