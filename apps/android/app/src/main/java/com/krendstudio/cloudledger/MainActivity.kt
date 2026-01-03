package com.krendstudio.cloudledger

import android.app.AlertDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.krendstudio.cloudledger.data.local.AppDatabase
import com.krendstudio.cloudledger.data.repository.LedgerRepository
import com.krendstudio.cloudledger.util.CrashReporter
import com.krendstudio.cloudledger.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels {
        val database = AppDatabase.getInstance(applicationContext)
        val repository = LedgerRepository(
            savedLedgerDao = database.savedLedgerDao(),
            userProfileDao = database.userProfileDao(),
            transactionDao = database.transactionDao(),
            ledgerMetaDao = database.ledgerMetaDao(),
            recurringTemplateDao = database.recurringTemplateDao(),
            syncStateDao = database.syncStateDao()
        )
        AppViewModel.Factory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReporter.init(applicationContext)
        if (CrashReporter.hasCrashLog(applicationContext)) {
            startActivity(android.content.Intent(this, CrashLogActivity::class.java))
            finish()
            return
        }
        setContent {
            CloudLedgerApp(viewModel = viewModel)
        }
        if (savedInstanceState == null) {
            showAuthMismatchDialogIfNeeded()
        }
    }

    private fun showAuthMismatchDialogIfNeeded() {
        val clearAction = buildAuthMismatchClearAction() ?: return
        AlertDialog.Builder(this)
            .setTitle("帳號狀態不一致")
            .setMessage("偵測到 Google 與 Firebase 登入不一致，是否要清除登入狀態重新登入？")
            .setPositiveButton("清除") { _, _ ->
                clearAction.invoke()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun buildAuthMismatchClearAction(): (() -> Unit)? {
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        val googleAccount = GoogleSignIn.getLastSignedInAccount(this)

        val mismatch = when {
            firebaseUser == null && googleAccount == null -> false
            firebaseUser == null || googleAccount == null -> true
            firebaseUser.email.isNullOrBlank() || googleAccount.email.isNullOrBlank() -> true
            firebaseUser.email != googleAccount.email -> true
            else -> false
        }

        if (!mismatch) return null

        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        return {
            FirebaseAuth.getInstance().signOut()
            GoogleSignIn.getClient(this, options).signOut()
            Thread {
                AppDatabase.getInstance(applicationContext).clearAllTables()
            }.start()
        }
    }
}
