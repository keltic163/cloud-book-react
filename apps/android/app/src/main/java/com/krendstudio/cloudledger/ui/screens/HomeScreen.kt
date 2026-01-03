package com.krendstudio.cloudledger.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.krendstudio.cloudledger.R
import com.krendstudio.cloudledger.viewmodel.AppViewModel
import com.krendstudio.cloudledger.viewmodel.LedgerUiState

private data class HomeTab(val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: AppViewModel, ledgerState: LedgerUiState) {
    val context = LocalContext.current
    val tabs = listOf(
        HomeTab("首頁"),
        HomeTab("記帳"),
        HomeTab("紀錄"),
        HomeTab("統計"),
        HomeTab("設定")
    )
    var selectedIndex by remember { mutableStateOf(0) }
    val ledgerLabel = ledgerState.currentLedgerId ?: "尚未選擇帳本"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = "CloudLedger")
                        Text(
                            text = "帳本：$ledgerLabel",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                actions = {
                    TextButton(onClick = {
                        viewModel.signOut()
                        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestIdToken(context.getString(R.string.default_web_client_id))
                            .requestEmail()
                            .build()
                        GoogleSignIn.getClient(context, options).signOut()
                    }) {
                        Text(text = "登出")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedIndex == index,
                        onClick = { selectedIndex = index },
                        label = { Text(text = tab.label) },
                        icon = {}
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            when (selectedIndex) {
                0 -> DashboardScreen(viewModel)
                1 -> AddTransactionScreen(viewModel)
                2 -> TransactionsScreen(viewModel)
                3 -> StatsScreen(viewModel)
                else -> SettingsScreen(viewModel)
            }
        }
    }
}
