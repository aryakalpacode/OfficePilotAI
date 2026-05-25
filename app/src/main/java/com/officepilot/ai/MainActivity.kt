package com.officepilot.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.officepilot.ai.data.repository.PrefsRepository
import com.officepilot.ai.ui.screens.home.HomeScreen
import com.officepilot.ai.ui.screens.markdown.MarkdownScreen
import com.officepilot.ai.ui.screens.settings.SettingsScreen
import com.officepilot.ai.ui.screens.setup.SetupScreen
import com.officepilot.ai.ui.theme.OfficePilotTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var prefs: PrefsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val setupDone = runBlocking { prefs.setupDone.firstOrNull() ?: false }

        setContent {
            OfficePilotTheme {
                val nav = rememberNavController()
                NavHost(nav, startDestination = if (setupDone) "home" else "setup") {
                    composable("setup") {
                        SetupScreen(onDone = { nav.navigate("home") { popUpTo("setup") { inclusive = true } } })
                    }
                    composable("home") {
                        HomeScreen(
                            onSettings = { nav.navigate("settings") },
                            onMarkdownWorkshop = { nav.navigate("markdown") }
                        )
                    }
                    composable("markdown") {
                        MarkdownScreen(onBack = { nav.popBackStack() })
                    }
                    composable("settings") {
                        SettingsScreen(onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}
