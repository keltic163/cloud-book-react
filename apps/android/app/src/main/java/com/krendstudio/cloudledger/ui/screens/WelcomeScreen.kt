package com.krendstudio.cloudledger.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var crashLog by remember { mutableStateOf<String?>(null) }
    var showCrashDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF6366F1), Color(0xFF7C3AED))
                )
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 10.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.logo),
                    contentDescription = "Logo",
                    modifier = Modifier.size(72.dp)
                )
                Text(
                    text = "CloudLedger 雲記",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = "您的智慧共享記帳本，輕鬆管理財務。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B)
                )

                Button(
                    onClick = {
                        val client = googleSignInClient
                        if (client == null) {
                            errorMessage = "Google 登入不可用"
                            return@Button
                        }
                        launcher.launch(client.signInIntent)
                    },
                    enabled = !authState.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2563EB),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(text = if (authState.isLoading) "登入中..." else "使用 Google 帳號登入")
                }

                OutlinedButton(
                    onClick = { viewModel.enterMockMode() },
                    enabled = !authState.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFFF1F5F9),
                        contentColor = Color(0xFF475569)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(text = "試用演示模式 (無需登入)")
                }

                Text(
                    text = "演示模式資料僅儲存於本機，清除快取後消失。",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8)
                )

                errorMessage?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error)
                }

                if (!crashLog.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { showCrashDialog = true }) {
                        Text(text = "查看上次閃退紀錄")
                    }
                }
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
            text = { Text(text = crashLog ?: "") }
        )
    }
}
