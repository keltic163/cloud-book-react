package com.krendstudio.cloudledger.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.krendstudio.cloudledger.viewmodel.AppViewModel
import com.krendstudio.cloudledger.viewmodel.LedgerUiState
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    viewModel: AppViewModel,
    ledgerState: LedgerUiState
) {
    val scope = rememberCoroutineScope()
    var ledgerName by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .padding(20.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "開始使用", style = MaterialTheme.typography.headlineSmall)
        Text(text = "建立或加入帳本", style = MaterialTheme.typography.bodyMedium)

        if (ledgerState.savedLedgers.isNotEmpty()) {
            Text(text = "現有帳本", style = MaterialTheme.typography.titleMedium)
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ledgerState.savedLedgers) { ledger ->
                    Card {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(text = ledger.alias, style = MaterialTheme.typography.titleMedium)
                                Text(text = "ID: ${ledger.id}", style = MaterialTheme.typography.bodySmall)
                            }
                            Button(
                                onClick = {
                                    if (isBusy) return@Button
                                    isBusy = true
                                    scope.launch {
                                        viewModel.switchLedger(ledger.id)
                                            .onFailure { errorMessage = "切換失敗：${it.message}" }
                                        isBusy = false
                                    }
                                }
                            ) {
                                Text(text = "切換")
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(text = "加入帳本", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = inviteCode,
            onValueChange = { inviteCode = it },
            label = { Text(text = "邀請碼") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                if (isBusy) return@Button
                isBusy = true
                errorMessage = null
                scope.launch {
                    viewModel.joinLedger(inviteCode)
                        .onFailure { errorMessage = "加入失敗：${it.message}" }
                    isBusy = false
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "加入")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(text = "建立新帳本", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = ledgerName,
            onValueChange = { ledgerName = it },
            label = { Text(text = "帳本名稱") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                if (isBusy) return@Button
                isBusy = true
                errorMessage = null
                scope.launch {
                    viewModel.createLedger(ledgerName)
                        .onFailure { errorMessage = "建立失敗：${it.message}" }
                    isBusy = false
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "建立")
        }

        OutlinedButton(
            onClick = {
                scope.launch { viewModel.refreshUserProfile() }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "重新同步")
        }

        errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }
    }
}

