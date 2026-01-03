package com.krendstudio.cloudledger.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.krendstudio.cloudledger.model.ChangelogVersion
import com.krendstudio.cloudledger.model.RecurringTemplate
import com.krendstudio.cloudledger.model.TransactionDraft
import com.krendstudio.cloudledger.model.TransactionType
import com.krendstudio.cloudledger.util.CsvUtils
import com.krendstudio.cloudledger.viewmodel.AppViewModel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(viewModel: AppViewModel) {
    val authState by viewModel.authState.collectAsState()
    val ledgerState by viewModel.ledgerState.collectAsState()
    val expenseCategories by viewModel.expenseCategories.collectAsState()
    val incomeCategories by viewModel.incomeCategories.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val recurringTemplates by viewModel.recurringTemplates.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val prefs = remember {
        context.getSharedPreferences("cloudledger_prefs", Context.MODE_PRIVATE)
    }

    var joinCode by remember { mutableStateOf("") }
    var newLedgerName by remember { mutableStateOf("") }
    var newExpenseCategory by remember { mutableStateOf("") }
    var newIncomeCategory by remember { mutableStateOf("") }

    var aiKey by remember { mutableStateOf(prefs.getString("ai_key", "") ?: "") }
    var aiEnabled by remember { mutableStateOf(prefs.getBoolean("ai_enabled", false)) }
    var message by remember { mutableStateOf<String?>(null) }

    var changelog by remember { mutableStateOf<List<ChangelogVersion>>(emptyList()) }

    LaunchedEffect(Unit) {
        changelog = loadChangelog(context)
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { writeJson(context, it, transactions) }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let { writeCsv(context, it, transactions) }
    }

    val importCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val drafts = readCsv(context, uri)
            if (drafts.isEmpty()) {
                message = "匯入資料為空"
            } else {
                val result = viewModel.importTransactions(drafts)
                message = result.fold(
                    onSuccess = { "已匯入 $it 筆" },
                    onFailure = { "匯入失敗：${it.message}" }
                )
            }
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val drafts = readJson(context, uri)
            if (drafts.isEmpty()) {
                message = "匯入資料為空"
            } else {
                val result = viewModel.importTransactions(drafts)
                message = result.fold(
                    onSuccess = { "已匯入 $it 筆" },
                    onFailure = { "匯入失敗：${it.message}" }
                )
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Text(text = "設定", style = MaterialTheme.typography.titleLarge)
        }

        item {
            SectionHeader(title = "帳號")
            Text(text = authState.user?.displayName ?: "訪客")
            authState.user?.email?.let { Text(text = it) }
        }

        item {
            SectionHeader(title = "帳本管理")
            if (ledgerState.savedLedgers.isEmpty()) {
                Text(text = "尚無帳本")
            } else {
                ledgerState.savedLedgers.forEach { ledger ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = ledger.alias)
                            Text(text = ledger.id, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                scope.launch { viewModel.switchLedger(ledger.id) }
                            }) {
                                Text(text = "切換")
                            }
                            TextButton(onClick = {
                                scope.launch { viewModel.leaveLedger(ledger.id) }
                            }) {
                                Text(text = "退出")
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(1.dp))
            OutlinedTextField(
                value = joinCode,
                onValueChange = { joinCode = it },
                label = { Text(text = "邀請碼") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { scope.launch { viewModel.joinLedger(joinCode) } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "加入帳本")
            }
            OutlinedTextField(
                value = newLedgerName,
                onValueChange = { newLedgerName = it },
                label = { Text(text = "新帳本名稱") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { scope.launch { viewModel.createLedger(newLedgerName) } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "建立帳本")
            }
        }

        item {
            SectionHeader(title = "分類管理")
            Text(text = "支出分類", style = MaterialTheme.typography.titleSmall)
            expenseCategories.forEach { category ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = category)
                    TextButton(onClick = {
                        scope.launch { viewModel.deleteCategory(TransactionType.EXPENSE, category) }
                    }) {
                        Text(text = "刪除")
                    }
                }
            }
            OutlinedTextField(
                value = newExpenseCategory,
                onValueChange = { newExpenseCategory = it },
                label = { Text(text = "新增支出分類") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    scope.launch {
                        viewModel.addCategory(TransactionType.EXPENSE, newExpenseCategory)
                        newExpenseCategory = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "新增支出分類")
            }

            Spacer(modifier = Modifier.width(1.dp))

            Text(text = "收入分類", style = MaterialTheme.typography.titleSmall)
            incomeCategories.forEach { category ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = category)
                    TextButton(onClick = {
                        scope.launch { viewModel.deleteCategory(TransactionType.INCOME, category) }
                    }) {
                        Text(text = "刪除")
                    }
                }
            }
            OutlinedTextField(
                value = newIncomeCategory,
                onValueChange = { newIncomeCategory = it },
                label = { Text(text = "新增收入分類") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    scope.launch {
                        viewModel.addCategory(TransactionType.INCOME, newIncomeCategory)
                        newIncomeCategory = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "新增收入分類")
            }
            OutlinedButton(
                onClick = { scope.launch { viewModel.resetCategories() } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "重置分類")
            }
        }

        item {
            SectionHeader(title = "固定收支")
            if (recurringTemplates.isEmpty()) {
                Text(text = "尚無固定收支")
            } else {
                recurringTemplates.forEach { template ->
                    RecurringRow(template = template, onToggle = { active ->
                        scope.launch { viewModel.toggleRecurringActive(template.id, active) }
                    }, onDelete = {
                        scope.launch { viewModel.deleteRecurringTemplate(template.id) }
                    })
                }
            }
        }

        item {
            SectionHeader(title = "同步")
            OutlinedButton(
                onClick = { scope.launch { viewModel.syncCurrentLedger(forceFull = true) } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "重新同步帳本")
            }
        }

        item {
            SectionHeader(title = "資料匯入/匯出")
            Button(
                onClick = { exportJsonLauncher.launch("cloudledger-transactions.json") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "匯出 JSON")
            }
            Button(
                onClick = { exportCsvLauncher.launch("cloudledger-transactions.csv") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "匯出 CSV")
            }
            OutlinedButton(
                onClick = { importJsonLauncher.launch(arrayOf("application/json")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "匯入 JSON")
            }
            OutlinedButton(
                onClick = { importCsvLauncher.launch(arrayOf("text/csv", "text/comma-separated-values")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "匯入 CSV")
            }
            Text(text = "CSV 欄位：date,type,amount,category,description,rewards", style = MaterialTheme.typography.bodySmall)
        }

        item {
            SectionHeader(title = "AI 設定")
            OutlinedTextField(
                value = aiKey,
                onValueChange = { aiKey = it },
                label = { Text(text = "API Key") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    prefs.edit().putString("ai_key", aiKey).apply()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "儲存 API Key")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "啟用 AI")
                Switch(
                    checked = aiEnabled,
                    onCheckedChange = {
                        aiEnabled = it
                        prefs.edit().putBoolean("ai_enabled", it).apply()
                    }
                )
            }
        }

        item {
            SectionHeader(title = "更新紀錄")
            if (changelog.isEmpty()) {
                Text(text = "沒有更新紀錄")
            } else {
                changelog.forEach { version ->
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Text(text = "v${version.version}", style = MaterialTheme.typography.titleSmall)
                        version.notes.forEach { note ->
                            Text(text = "- $note", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        item {
            message?.let { Text(text = it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun RecurringRow(
    template: RecurringTemplate,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val date = Instant.ofEpochMilli(template.nextRunAt)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    val formatter = DateTimeFormatter.ISO_DATE
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(text = template.title, style = MaterialTheme.typography.titleSmall)
        Text(text = "${template.type} · ${template.category}")
        Text(text = "下次：${date.format(formatter)}")
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "啟用")
                Switch(checked = template.isActive, onCheckedChange = onToggle)
            }
            TextButton(onClick = onDelete) {
                Text(text = "刪除")
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(text = title, style = MaterialTheme.typography.titleMedium)
}

private fun loadChangelog(context: Context): List<ChangelogVersion> {
    return runCatching {
        val jsonText = context.assets.open("changelog.json").bufferedReader().use { it.readText() }
        val root = JSONObject(jsonText)
        val versions = root.getJSONArray("versions")
        buildList {
            for (i in 0 until versions.length()) {
                val obj = versions.getJSONObject(i)
                val version = obj.getString("version")
                val notesArray = obj.getJSONArray("notes")
                val notes = buildList {
                    for (n in 0 until notesArray.length()) {
                        add(notesArray.getString(n))
                    }
                }
                add(ChangelogVersion(version, notes))
            }
        }
    }.getOrDefault(emptyList())
}

private fun writeJson(context: Context, uri: Uri, transactions: List<com.krendstudio.cloudledger.model.Transaction>) {
    val json = buildString {
        append("{\"transactions\":[")
        transactions.forEachIndexed { index, tx ->
            append("{")
            append("\"id\":\"").append(escapeJson(tx.id)).append("\",")
            append("\"amount\":").append(tx.amount).append(",")
            append("\"type\":\"").append(tx.type.name).append("\",")
            append("\"category\":\"").append(escapeJson(tx.category)).append("\",")
            append("\"description\":\"").append(escapeJson(tx.description)).append("\",")
            append("\"rewards\":").append(tx.rewards).append(",")
            append("\"date\":\"").append(escapeJson(tx.date)).append("\"")
            append("}")
            if (index < transactions.size - 1) append(",")
        }
        append("]}")
    }
    context.contentResolver.openOutputStream(uri)?.use { output ->
        output.write(json.toByteArray(Charsets.UTF_8))
    }
}

private fun writeCsv(context: Context, uri: Uri, transactions: List<com.krendstudio.cloudledger.model.Transaction>) {
    val header = CsvUtils.encode(listOf("date", "type", "amount", "category", "description", "rewards"))
    val lines = buildString {
        append(header).append("\n")
        transactions.forEach { tx ->
            val row = CsvUtils.encode(
                listOf(
                    tx.date,
                    tx.type.name,
                    tx.amount.toString(),
                    tx.category,
                    tx.description,
                    tx.rewards.toString()
                )
            )
            append(row).append("\n")
        }
    }
    context.contentResolver.openOutputStream(uri)?.use { output ->
        output.write(lines.toByteArray(Charsets.UTF_8))
    }
}

private fun readCsv(context: Context, uri: Uri): List<TransactionDraft> {
    val input = context.contentResolver.openInputStream(uri) ?: return emptyList()
    val lines = input.bufferedReader().use { it.readLines() }
    if (lines.isEmpty()) return emptyList()
    return lines.drop(1).mapNotNull { line ->
        if (line.isBlank()) return@mapNotNull null
        val fields = CsvUtils.parseLine(line)
        if (fields.size < 6) return@mapNotNull null
        val date = fields[0]
        val type = runCatching { TransactionType.valueOf(fields[1]) }
            .getOrDefault(TransactionType.EXPENSE)
        val amount = fields[2].toDoubleOrNull() ?: return@mapNotNull null
        val category = fields[3]
        val description = fields[4]
        val rewards = fields[5].toDoubleOrNull() ?: 0.0
        TransactionDraft(
            amount = amount,
            type = type,
            category = category,
            description = description,
            rewards = rewards,
            date = date,
            targetUserUid = null
        )
    }
}

private fun readJson(context: Context, uri: Uri): List<TransactionDraft> {
    val input = context.contentResolver.openInputStream(uri) ?: return emptyList()
    val jsonText = input.bufferedReader().use { it.readText() }
    return runCatching {
        val root = JSONObject(jsonText)
        val array = root.optJSONArray("transactions") ?: return emptyList()
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val date = obj.optString("date")
                val type = runCatching { TransactionType.valueOf(obj.optString("type")) }
                    .getOrDefault(TransactionType.EXPENSE)
                val amount = obj.optDouble("amount", Double.NaN)
                if (amount.isNaN()) continue
                val category = obj.optString("category")
                val description = obj.optString("description")
                val rewards = obj.optDouble("rewards", 0.0)
                add(
                    TransactionDraft(
                        amount = amount,
                        type = type,
                        category = category,
                        description = description,
                        rewards = rewards,
                        date = date,
                        targetUserUid = null
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

private fun escapeJson(value: String): String {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
