package com.krendstudio.cloudledger

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.krendstudio.cloudledger.ui.navigation.AppNavGraph
import com.krendstudio.cloudledger.viewmodel.AppViewModel

@Composable
fun CloudLedgerApp(viewModel: AppViewModel) {
    val authState by viewModel.authState.collectAsState()
    val ledgerState by viewModel.ledgerState.collectAsState()

    MaterialTheme {
        Surface(modifier = Modifier) {
            AppNavGraph(
                authState = authState,
                ledgerState = ledgerState,
                viewModel = viewModel
            )
        }
    }
}
