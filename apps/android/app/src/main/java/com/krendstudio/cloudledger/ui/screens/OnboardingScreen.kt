package com.krendstudio.cloudledger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.krendstudio.cloudledger.ui.components.AppTextField
import com.krendstudio.cloudledger.viewmodel.AppViewModel
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(viewModel: AppViewModel) {
    val ledgerState by viewModel.ledgerState.collectAsState()
    val scope = rememberCoroutineScope()
    var ledgerName by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshUserProfile()
    }

    fun runAsync(block: suspend () -> Unit) {
        if (busy) return
        error = null
        busy = true
        scope.launch {
            runCatching { block() }
                .onFailure { error = "操作失敗，請稍後再試。" }
            busy = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFDE68A),
                        Color(0xFFFCE7F3),
                        Color(0xFFDBEAFE)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = Color.White.copy(alpha = 0.92f),
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFF59E0B)
                        ) {
                            Box(
                                modifier = Modifier.size(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "CL", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = "開始使用 CloudLedger", fontWeight = FontWeight.Bold)
                            Text(text = "建立新帳本或加入現有帳本", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                        }
                    }

                    // 固定顯示：加入帳本
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = "加入帳本", fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                            AppTextField(
                                value = inviteCode,
                                onValueChange = { inviteCode = it },
                                label = "邀請碼",
                                placeholder = "輸入邀請碼"
                            )
                            Button(
                                onClick = {
                                    runAsync {
                                        val ok = viewModel.joinLedger(inviteCode).isSuccess
                                        if (ok) {
                                            viewModel.clearOnboardingRequest()
                                        } else {
                                            error = "加入帳本失敗，請確認邀請碼是否正確。"
                                        }
                                    }
                                },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF0F172A),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(text = "加入帳本")
                            }
                        }
                    }

                    // 固定顯示：建立新帳本
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = "建立新帳本", fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                            AppTextField(
                                value = ledgerName,
                                onValueChange = { ledgerName = it },
                                label = "帳本名稱",
                                placeholder = "帳本名稱（可留空）"
                            )
                            Button(
                                onClick = {
                                    runAsync {
                                        viewModel.createLedger(ledgerName).onSuccess {
                                            viewModel.clearOnboardingRequest()
                                        }
                                    }
                                },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF59E0B),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(text = "建立帳本")
                            }
                        }
                    }

                    // 現有帳本選擇
                    if (ledgerState.savedLedgers.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(text = "選擇現有帳本", fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                                ledgerState.savedLedgers.forEach { ledger ->
                                    OutlinedButton(
                                        onClick = {
                                            runAsync {
                                                viewModel.switchLedger(ledger.id).onSuccess {
                                                    viewModel.clearOnboardingRequest()
                                                }
                                            }
                                        },
                                        enabled = !busy,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(text = ledger.alias, fontWeight = FontWeight.SemiBold)
                                                Text(text = "ID: ${ledger.id}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                                            }
                                            Text(text = "切換", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    error?.let {
                        Surface(
                            color = Color(0xFFFEF2F2),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = it,
                                color = Color(0xFFDC2626),
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
