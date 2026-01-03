package com.krendstudio.cloudledger.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.res.Resources
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.krendstudio.cloudledger.R
import com.krendstudio.cloudledger.util.CrashReporter
import com.krendstudio.cloudledger.viewmodel.AppViewModel
import kotlinx.coroutines.launch

@Composable
fun WelcomeScreen(viewModel: AppViewModel) {
    val authState by viewModel.authState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var debugMessage by remember { mutableStateOf("") }
    var crashLog by remember { mutableStateOf<String?>(null) }
    var showCrashDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("cloudledger_prefs", android.content.Context.MODE_PRIVATE)
        val crashCode = prefs.getString("last_crash_code", null)
        if (!crashCode.isNullOrBlank()) {
            debugMessage = "lastCrash=$crashCode"
        }
        crashLog = CrashReporter.readCrashLog(context)
    }

    val googleSignInClient = remember {
        runCatching {
            val clientId = context.getString(R.string.default_web_client_id)
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(clientId)
                .requestEmail()
                .build()
                .let { options -> GoogleSignIn.getClient(context, options) }
        }.onFailure {
            errorMessage = "Google 登入初始化失敗"
            debugMessage = "clientIdLoadFailed=${it.javaClass.simpleName}"
        }.getOrNull()
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val token = account.idToken
            if (token.isNullOrBlank()) {
                errorMessage = "無法取得登入憑證"
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                viewModel.signInWithGoogle(token)
                    .onFailure { errorMessage = "登入失敗：${it.message}" }
            }
        } catch (e: ApiException) {
            errorMessage = "Google 登入失敗：${e.statusCode}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "CloudLedger 雲記", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val client = googleSignInClient
                if (client == null) {
                    errorMessage = "Google 登入不可用"
                    debugMessage = "clientNull=true"
                    return@Button
                }
                launcher.launch(client.signInIntent)
            },
            enabled = !authState.isLoading
        ) {
            Text(text = "使用 Google 登入")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(onClick = { viewModel.enterMockMode() }) {
            Text(text = "離線體驗 / Demo 模式")
        }

        errorMessage?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        if (!crashLog.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = { showCrashDialog = true }) {
                Text(text = "查看上次閃退紀錄")
            }
        }
    }

    if (showCrashDialog && !crashLog.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = { showCrashDialog = false },
            confirmButton = {
                Button(onClick = { showCrashDialog = false }) {
                    Text(text = "關閉")
                }
            },
            title = { Text(text = "閃退紀錄") },
            text = {
                Text(text = crashLog ?: "")
            }
        )
    }
}
