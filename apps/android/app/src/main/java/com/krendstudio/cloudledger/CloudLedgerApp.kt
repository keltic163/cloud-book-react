package com.krendstudio.cloudledger

import android.app.Activity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import com.krendstudio.cloudledger.ui.navigation.AppNavGraph
import com.krendstudio.cloudledger.ui.theme.CloudLedgerTheme
import com.krendstudio.cloudledger.viewmodel.AppViewModel

@Composable
fun CloudLedgerApp(viewModel: AppViewModel) {
    val authState by viewModel.authState.collectAsState()
    val ledgerState by viewModel.ledgerState.collectAsState()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("cloudledger_prefs", android.content.Context.MODE_PRIVATE) }
    val darkModeState = remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }

    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "dark_mode") {
                darkModeState.value = prefs.getBoolean("dark_mode", false)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val isDark = darkModeState.value
    CloudLedgerTheme(darkTheme = isDark) {
        val surfaceColor = MaterialTheme.colorScheme.surface
        val view = LocalView.current
        if (!view.isInEditMode) {
            SideEffect {
                val window = (view.context as Activity).window
                // 真正的全螢幕透明，不強行設色
                window.statusBarColor = surfaceColor.toArgb()
                window.navigationBarColor = surfaceColor.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDark
            }
        }
        
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            AppNavGraph(
                authState = authState,
                ledgerState = ledgerState,
                viewModel = viewModel
            )
        }
    }
}
