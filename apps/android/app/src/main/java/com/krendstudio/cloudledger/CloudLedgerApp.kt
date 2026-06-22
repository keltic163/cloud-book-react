package com.krendstudio.cloudledger

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
    val firstRunAck = remember { mutableStateOf(prefs.getBoolean("first_run_ack", false)) }

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

        if (!firstRunAck.value) {
            val termsUrl = stringResource(id = R.string.consent_terms_url)
            val privacyUrl = stringResource(id = R.string.consent_privacy_url)
            val acceptedTerms = remember { mutableStateOf(false) }
            val acceptedPrivacy = remember { mutableStateOf(false) }
            val canAccept = acceptedTerms.value && acceptedPrivacy.value

            AlertDialog(
                onDismissRequest = { },
                title = { Text(stringResource(id = R.string.consent_dialog_title)) },
                text = {
                    Column(Modifier.fillMaxWidth()) {
                        Text(stringResource(id = R.string.consent_prompt))
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = acceptedTerms.value,
                                onCheckedChange = { acceptedTerms.value = it }
                            )
                            TextButton(onClick = {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(termsUrl))) }
                            }) {
                                Text(stringResource(id = R.string.consent_terms_label))
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = acceptedPrivacy.value,
                                onCheckedChange = { acceptedPrivacy.value = it }
                            )
                            TextButton(onClick = {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl))) }
                            }) {
                                Text(stringResource(id = R.string.consent_privacy_label))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        prefs.edit().putBoolean("first_run_ack", true).apply()
                        firstRunAck.value = true
                    }, enabled = canAccept) { Text(stringResource(id = R.string.consent_accept_label)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        (context as? Activity)?.finish()
                    }) { Text(stringResource(id = R.string.consent_decline_label)) }
                }
            )
        }
    }
}
