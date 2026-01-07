package com.krendstudio.cloudledger.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.krendstudio.cloudledger.R
import com.krendstudio.cloudledger.model.RecurringTemplate
import com.krendstudio.cloudledger.model.Transaction
import com.krendstudio.cloudledger.model.TransactionDraft
import com.krendstudio.cloudledger.model.TransactionType
import com.krendstudio.cloudledger.model.LedgerMember
import com.krendstudio.cloudledger.ui.components.DropdownField
import com.krendstudio.cloudledger.util.CsvUtils
import com.krendstudio.cloudledger.viewmodel.AppViewModel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
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
    val members by viewModel.members.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val prefs = remember { context.getSharedPreferences("cloudledger_prefs", Context.MODE_PRIVATE) }

    var joinCode by remember { mutableStateOf("") }
    var newLedgerName by remember { mutableStateOf("") }
    var newExpenseCategory by remember { mutableStateOf("") }
    var newIncomeCategory by remember { mutableStateOf("") }

    var aiKey by remember { mutableStateOf("") }
    var aiKeyStored by remember { mutableStateOf(prefs.contains("ai_key")) }
    var aiEnabled by remember { mutableStateOf(prefs.getBoolean("ai_enabled", false)) }
    var aiModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var aiModelSelected by remember { mutableStateOf(prefs.getString("ai_model", "") ?: "") }
    var aiTestMessage by remember { mutableStateOf("") }
    var aiTesting by remember { mutableStateOf(false) }
    var darkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }
    var message by remember { mutableStateOf<String?>(null) }
    var lastSyncAt by remember { mutableStateOf(prefs.getLong("last_sync_at", 0L)) }

    var showCategoryDialog by remember { mutableStateOf(false) }
    var showChangelogDialog by remember { mutableStateOf(false) }
    var showLedgerList by remember { mutableStateOf(false) }
    var pendingSwitchId by remember { mutableStateOf<String?>(null) }
    var pendingLeaveId by remember { mutableStateOf<String?>(null) }
    var editingLedgerId by remember { mutableStateOf<String?>(null) }
    var editLedgerAlias by remember { mutableStateOf("") }
    var editingRecurring by remember { mutableStateOf<RecurringTemplate?>(null) }

    var editTitle by remember { mutableStateOf("") }
    var editAmount by remember { mutableStateOf("") }
    var editType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var editCategory by remember { mutableStateOf("") }
    var editExecuteDay by remember { mutableStateOf("1") }
    var editIntervalMonths by remember { mutableStateOf("1") }
    var editLimitedRuns by remember { mutableStateOf(false) }
    var editTotalRuns by remember { mutableStateOf("12") }
    var syncing by remember { mutableStateOf(false) }

    val currentLedger = ledgerState.savedLedgers.firstOrNull { it.id == ledgerState.currentLedgerId }
    val fallbackMember = authState.user?.let {
        LedgerMember(uid = it.uid, displayName = it.displayName, photoUrl = it.photoUrl)
    }
    val memberList = remember(members, authState.user) {
        val base = if (members.isNotEmpty()) members else listOfNotNull(fallbackMember)
        base.distinctBy { it.uid }
    }

    LaunchedEffect(ledgerState.currentLedgerId) {
        showLedgerList = false
    }

    LaunchedEffect(editingRecurring) {
        editingRecurring?.let { template ->
            editTitle = template.title
            editAmount = template.amount.toString()
            editType = if (template.type == "INCOME") TransactionType.INCOME else TransactionType.EXPENSE
            editCategory = template.category
            editExecuteDay = template.executeDay.toString()
            editIntervalMonths = template.intervalMonths.toString()
            val totalRuns = template.totalRuns ?: template.remainingRuns
            editTotalRuns = (totalRuns ?: 12).toString()
            editLimitedRuns = totalRuns != null
        }
    }

    LaunchedEffect(editType) {
        val options = if (editType == TransactionType.INCOME) incomeCategories else expenseCategories
        if (options.isNotEmpty() && !options.contains(editCategory)) {
            editCategory = options.first()
        }
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { writeJson(context, it, transactions) } }
    val exportCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let { writeCsv(context, it, transactions) } }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            scope.launch {
                val drafts = readJson(context, it).ifEmpty { readCsv(context, it) }
                if (drafts.isEmpty()) message = "匯入資料為空" else {
                    viewModel.importTransactions(drafts).onSuccess { count -> message = "已匯入 $count 筆" }.onFailure { err -> message = "匯入失敗：${err.message}" }
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(text = "設定", style = MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { darkMode = !darkMode; prefs.edit().putBoolean("dark_mode", darkMode).apply() }) {
                        Icon(imageVector = if (darkMode) Icons.Filled.LightMode else Icons.Filled.DarkMode, contentDescription = null)
                    }
                    TextButton(onClick = {
                        if (!authState.isMockMode) {
                            val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                .requestIdToken(context.getString(R.string.default_web_client_id))
                                .requestEmail()
                                .build()
                            GoogleSignIn.getClient(context, options).signOut()
                        }
                        viewModel.signOut()
                    }) { Text(text = "登出", color = MaterialTheme.colorScheme.error) }
                }
            }
        }

        item {
            message?.let {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {
                    Text(text = it, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            SettingsCard(title = "資料同步", description = "顯示最後同步時間，可手動觸發同步。") {
                val lastSyncText = if (lastSyncAt == 0L) "—" else Instant.ofEpochMilli(lastSyncAt).atZone(ZoneId.systemDefault()).toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))
                Row(modifier = Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text(text = "上次同步：$lastSyncText", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = {
                        if (syncing) return@OutlinedButton
                        scope.launch {
                            syncing = true
                            viewModel.syncCurrentLedger(forceFull = true).onSuccess {
                                val now = System.currentTimeMillis()
                                prefs.edit().putLong("last_sync_at", now).apply()
                                lastSyncAt = now
                                message = "同步完成"
                            }
                            syncing = false
                        }
                    }, modifier = Modifier.height(36.dp), shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("立即同步")
                    }
                }
            }
        }

        item {
            SettingsCard(title = "帳本與分類", description = "管理您的帳本與分類設定。") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("目前帳本", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(currentLedger?.alias ?: "未選擇", fontWeight = FontWeight.SemiBold)
                                    ledgerState.currentLedgerId?.let { Text("ID: $it", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                }
                                Row {
                                    IconButton(onClick = { ledgerState.currentLedgerId?.let { clipboardManager.setText(AnnotatedString(it)); message = "已複製 ID" } }) { Icon(Icons.Filled.ContentCopy, null) }
                                    IconButton(onClick = { currentLedger?.let { editingLedgerId = it.id; editLedgerAlias = it.alias } }) { Icon(Icons.Filled.Edit, null) }
                                    IconButton(onClick = { currentLedger?.let { pendingLeaveId = it.id } }) { Icon(Icons.Filled.Logout, null, tint = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("目前帳本成員", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (memberList.isEmpty()) {
                            Text("尚無成員資料", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                memberList.forEach { member ->
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            val photoUrl = member.photoUrl
                                            if (!photoUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = photoUrl,
                                                    contentDescription = member.displayName ?: "Member",
                                                    modifier = Modifier.size(24.dp).clip(CircleShape)
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = member.displayName?.take(1) ?: "?",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            Text(
                                                text = member.displayName ?: "未命名",
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (ledgerState.savedLedgers.size > 1) {
                        TextButton(onClick = { showLedgerList = !showLedgerList }) { Text(if (showLedgerList) "隱藏其他帳本" else "切換其他帳本") }
                        if (showLedgerList) {
                            ledgerState.savedLedgers.filter { it.id != ledgerState.currentLedgerId }.forEach { ledger ->
                                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.clickable { pendingSwitchId = ledger.id }.padding(vertical = 2.dp)) {
                                    Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.SpaceBetween) {
                                        Text(ledger.alias, fontWeight = FontWeight.SemiBold)
                                        Text("切換", color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = newLedgerName, onValueChange = { newLedgerName = it }, placeholder = { Text("新帳本名稱") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp))
                        Button(onClick = { scope.launch { viewModel.createLedger(newLedgerName); newLedgerName = "" } }, shape = RoundedCornerShape(8.dp)) { Text("建立") }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = joinCode, onValueChange = { joinCode = it }, placeholder = { Text("貼上邀請 ID") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp))
                        Button(onClick = { scope.launch { viewModel.joinLedger(joinCode); joinCode = "" } }, shape = RoundedCornerShape(8.dp)) { Text("加入") }
                    }

                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.clickable { viewModel.requestOnboarding() }) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Column {
                                Text("初次設定 / 加入帳本", fontWeight = FontWeight.SemiBold)
                                Text("重新進入建立或加入帳本流程", style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                        }
                    }

                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.clickable { showCategoryDialog = true }) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Label, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Text("分類管理", fontWeight = FontWeight.SemiBold)
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                        }
                    }
                }
            }
        }

        item {
            SettingsCard(title = "固定收支", description = "管理自動產生的交易。") {
                if (recurringTemplates.isEmpty()) Text("尚無設定", style = MaterialTheme.typography.bodySmall) else {
                    recurringTemplates.forEach { template ->
                        RecurringRow(
                            template = template,
                            onToggle = { enabled ->
                                scope.launch {
                                    viewModel.toggleRecurringActive(template.id, enabled)
                                        .onSuccess { message = "已更新固定收支狀態" }
                                        .onFailure { message = "更新失敗：${it.message}" }
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    viewModel.deleteRecurringTemplate(template.id)
                                        .onSuccess { message = "已刪除固定收支" }
                                        .onFailure { message = "刪除失敗：${it.message}" }
                                }
                            },
                            onEdit = { editingRecurring = template }
                        )
                    }
                }
            }
        }

        item {
            SettingsCard(title = "資料備份", description = "匯出或匯入您的交易紀錄。") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ExportTile("JSON 備份", "完整資料", Icons.Outlined.Download, MaterialTheme.colorScheme.primary, { exportJsonLauncher.launch("cloudledger-backup.json") }, Modifier.weight(1f))
                    ExportTile("CSV 匯出", "Excel 用", Icons.Outlined.Description, MaterialTheme.colorScheme.tertiary, { exportCsvLauncher.launch("cloudledger-export.csv") }, Modifier.weight(1f))
                }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/csv")) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Outlined.UploadFile, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("匯入資料")
                }
            }
        }

        item {
            SettingsCard(title = "智慧輸入 (AI)", description = "設定 AI 鑰匙與模型。") {
                OutlinedTextField(
                    value = aiKey,
                    onValueChange = { aiKey = it },
                    label = { Text("Gemini API Key") },
                    placeholder = { Text(if (aiKeyStored && aiKey.isBlank()) "已儲存（已隱藏）" else "輸入 API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
                Button(
                    onClick = {
                        if (aiKey.isBlank()) return@Button
                        aiTesting = true
                        scope.launch {
                            viewModel.validateAiKey(aiKey)
                                .onSuccess { validation ->
                                    aiModels = validation.models
                                    if (validation.valid) {
                                        prefs.edit().putString("ai_key", aiKey).apply()
                                        aiKeyStored = true
                                        aiKey = ""
                                        message = "驗證成功"
                                    } else {
                                        message = "驗證失敗"
                                    }
                                }
                                .onFailure { message = "驗證失敗：${it.message}" }
                            aiTesting = false
                        }
                    },
                    enabled = !aiTesting,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (aiTesting) "驗證中..." else "儲存並驗證")
                }
                OutlinedButton(
                    onClick = {
                        prefs.edit().remove("ai_key").remove("ai_model").apply()
                        aiKey = ""
                        aiKeyStored = false
                        aiModels = emptyList()
                        aiModelSelected = ""
                        message = "已移除 API Key"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("移除 API Key")
                }
                if (aiModels.isNotEmpty()) {
                    DropdownField(
                        "選擇 AI 模型",
                        aiModels,
                        aiModelSelected,
                        { aiModelSelected = it; prefs.edit().putString("ai_model", it).apply() },
                        Modifier.fillMaxWidth()
                    )
                }
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("啟用 AI 功能")
                    Switch(checked = aiEnabled, onCheckedChange = { aiEnabled = it; prefs.edit().putBoolean("ai_enabled", it).apply() })
                }
            }
        }

        item {
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.clickable { showChangelogDialog = true }) {
                Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("更新紀錄", fontWeight = FontWeight.SemiBold)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                }
            }
        }

        item {
            SettingsCard(title = "聯繫作者", description = "有任何問題或建議，歡迎來信。") {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:chian0163@gmail.com")
                            putExtra(Intent.EXTRA_EMAIL, arrayOf("chian0163@gmail.com"))
                            putExtra(Intent.EXTRA_SUBJECT, "CloudLedger 意見回饋")
                        }
                        context.startActivity(Intent.createChooser(intent, "選擇寄信應用"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("寄信給作者")
                }
                Text("Email：chian0163@gmail.com", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("CloudLedger 雲記 v1.0.0 © 2025 KrendStudio", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("     ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("     ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    // --- Dialogs ---
    if (showCategoryDialog) {
        Dialog(onDismissRequest = { showCategoryDialog = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), Arrangement.spacedBy(16.dp)) {
                    Text("分類管理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    CategorySection(
                        title = "支出分類",
                        list = expenseCategories,
                        color = Color(0xFFE11D48),
                        onAdd = { scope.launch { viewModel.addCategory(TransactionType.EXPENSE, it) } },
                        onDelete = { scope.launch { viewModel.deleteCategory(TransactionType.EXPENSE, it) } }
                    )
                    CategorySection(
                        title = "收入分類",
                        list = incomeCategories,
                        color = Color(0xFF10B981),
                        onAdd = { scope.launch { viewModel.addCategory(TransactionType.INCOME, it) } },
                        onDelete = { scope.launch { viewModel.deleteCategory(TransactionType.INCOME, it) } }
                    )
                    Button(onClick = { showCategoryDialog = false }, modifier = Modifier.fillMaxWidth()) { Text("完成") }
                }
            }
        }
    }

    if (showChangelogDialog) {
        Dialog(onDismissRequest = { showChangelogDialog = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), Arrangement.spacedBy(12.dp)) {
                    Text("更新紀錄", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text("v1.0.0", fontWeight = FontWeight.Bold)
                        listOf(
                            "支援 Google 登入與帳本切換",
                            "新增匯入/匯出（JSON、CSV）",
                            "智慧輸入與語音記帳",
                            "離線可用與同步機制",
                            "主題切換與個人化設定"
                        ).forEach { note -> Text("- $note", style = MaterialTheme.typography.bodySmall) }
                    }
                    Button(onClick = { showChangelogDialog = false }, Modifier.fillMaxWidth()) { Text("關閉") }
                }
            }
        }
    }

    pendingSwitchId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingSwitchId = null },
            title = { Text("切換帳本") },
            text = { Text("確定要切換到此帳本嗎？") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        viewModel.switchLedger(id)
                            .onSuccess {
                                message = "已切換帳本"
                                viewModel.refreshUserProfile()
                            }
                            .onFailure { message = "切換失敗：${it.message}" }
                    }
                    pendingSwitchId = null
                }) { Text("確定") }
            },
            dismissButton = { TextButton(onClick = { pendingSwitchId = null }) { Text("取消") } }
        )
    }

    pendingLeaveId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingLeaveId = null },
            title = { Text("退出帳本") },
            text = { Text("確定要退出此帳本嗎？") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        viewModel.leaveLedger(id)
                            .onSuccess { message = "已退出帳本" }
                            .onFailure { message = "退出失敗：${it.message}" }
                    }
                    pendingLeaveId = null
                }) { Text("確定", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { pendingLeaveId = null }) { Text("取消") } }
        )
    }

    editingLedgerId?.let { id ->
        var alias by remember { mutableStateOf(editLedgerAlias) }
        AlertDialog(onDismissRequest = { editingLedgerId = null }, title = { Text("編輯帳本名稱") }, text = { OutlinedTextField(value = alias, onValueChange = { alias = it }, modifier = Modifier.fillMaxWidth()) }, confirmButton = { TextButton(onClick = { scope.launch { viewModel.updateLedgerAlias(id, alias) }; editingLedgerId = null }) { Text("儲存") } }, dismissButton = { TextButton(onClick = { editingLedgerId = null }) { Text("取消") } })
    }

    editingRecurring?.let { template ->
        Dialog(onDismissRequest = { editingRecurring = null }) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Column(
                    Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("編輯固定收支", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                    StatLabel("標題")
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    StatLabel("金額")
                    OutlinedTextField(
                        value = editAmount,
                        onValueChange = { editAmount = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = editType == TransactionType.EXPENSE,
                            onClick = { editType = TransactionType.EXPENSE },
                            label = { Text("支出") }
                        )
                        FilterChip(
                            selected = editType == TransactionType.INCOME,
                            onClick = { editType = TransactionType.INCOME },
                            label = { Text("收入") }
                        )
                    }

                    DropdownField(
                        label = "分類",
                        options = if (editType == TransactionType.INCOME) incomeCategories else expenseCategories,
                        selected = editCategory,
                        onSelected = { editCategory = it },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            StatLabel("每月扣款日")
                            DropdownField(
                                label = "",
                                options = (1..31).map { "${it} 號" },
                                selected = "${editExecuteDay} 號",
                                onSelected = { editExecuteDay = it.split(" ")[0] },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            StatLabel("每 N 個月")
                            OutlinedTextField(
                                value = editIntervalMonths,
                                onValueChange = { editIntervalMonths = it },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
                    }

                    StatLabel("執行次數")
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().height(40.dp)) {
                        Row(Modifier.padding(4.dp)) {
                            TabButtonSmall(text = "持續", selected = !editLimitedRuns, modifier = Modifier.weight(1f)) { editLimitedRuns = false }
                            TabButtonSmall(text = "指定次數", selected = editLimitedRuns, modifier = Modifier.weight(1f)) { editLimitedRuns = true }
                        }
                    }
                    if (editLimitedRuns) {
                        OutlinedTextField(
                            value = editTotalRuns,
                            onValueChange = { editTotalRuns = it },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { editingRecurring = null },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("取消") }
                        Button(
                            onClick = {
                                val amountValue = editAmount.toDoubleOrNull()
                                if (amountValue == null || editTitle.isBlank()) {
                                    message = "請輸入完整資訊"
                                    return@Button
                                }
                                val interval = editIntervalMonths.toIntOrNull()?.coerceAtLeast(1) ?: 1
                                val day = editExecuteDay.toIntOrNull()?.coerceIn(1, 31) ?: 1
                                val nextRunAt = computeNextRunAt(day, interval)
                                val updates = mutableMapOf<String, Any?>(
                                    "title" to editTitle.trim(),
                                    "amount" to amountValue,
                                    "type" to if (editType == TransactionType.INCOME) "income" else "expense",
                                    "category" to editCategory.ifBlank { "其他" },
                                    "intervalMonths" to interval,
                                    "executeDay" to day,
                                    "nextRunAt" to java.util.Date(nextRunAt),
                                    "updatedAt" to System.currentTimeMillis()
                                )
                                if (editLimitedRuns) {
                                    val total = editTotalRuns.toIntOrNull()?.coerceAtLeast(1) ?: 1
                                    updates["totalRuns"] = total
                                    updates["remainingRuns"] = total
                                } else {
                                    updates["totalRuns"] = null
                                    updates["remainingRuns"] = null
                                }
                                scope.launch {
                                    viewModel.updateRecurringTemplate(template.id, updates)
                                        .onSuccess {
                                            message = "固定收支已更新"
                                            editingRecurring = null
                                        }
                                        .onFailure { message = "更新失敗：${it.message}" }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("儲存") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategorySection(
    title: String,
    list: List<String>,
    color: Color,
    onAdd: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.Bold, color = color)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { cat ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = color.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(cat, fontSize = 12.sp, color = color)
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "刪除分類",
                            tint = color,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(14.dp)
                                .clickable { pendingDelete = cat }
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), placeholder = { Text("新增...") })
            IconButton(onClick = { if(text.isNotBlank()) onAdd(text); text = "" }) { Icon(Icons.Default.Add, null) }
        }
    }

    pendingDelete?.let { category ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("刪除分類") },
            text = { Text("確定要刪除「$category」嗎？") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(category)
                    pendingDelete = null
                }) { Text("刪除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SettingsCard(title: String, description: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            content()
        }
    }
}

@Composable
private fun ExportTile(title: String, subtitle: String, icon: ImageVector, tint: Color, onClick: () -> Unit, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier.clickable { onClick() }) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = tint); Text(title, fontWeight = FontWeight.Bold); Text(subtitle, fontSize = 10.sp)
        }
    }
}

@Composable
private fun RecurringRow(template: RecurringTemplate, onToggle: (Boolean) -> Unit, onDelete: () -> Unit, onEdit: () -> Unit) {
    val typeLabel = if (template.type == "income") "收入" else "支出"
    val interval = template.intervalMonths.coerceAtLeast(1)
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(12.dp), Arrangement.SpaceBetween, Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(template.title, fontWeight = FontWeight.Bold)
                Text("$typeLabel · ${template.category} · 每 $interval 個月 · ${template.executeDay} 號", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("下次執行：${formatRecurringDate(template.nextRunAt)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                template.remainingRuns?.let { Text("剩餘 $it 次", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Switch(
                    checked = template.isActive,
                    onCheckedChange = { onToggle(it) },
                    modifier = Modifier.scale(0.85f)
                )
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp), tint = Color.Red)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun TabButtonSmall(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(6.dp), color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, modifier = modifier.clickable { onClick() }) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(text, style = MaterialTheme.typography.labelMedium, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun computeNextRunAt(executeDay: Int, intervalMonths: Int): Long {
    val today = LocalDate.now()
    val baseMonth = today.withDayOfMonth(1)
    val daysInMonth = baseMonth.lengthOfMonth()
    val safeDay = executeDay.coerceIn(1, 31).coerceAtMost(daysInMonth)
    var next = baseMonth.withDayOfMonth(safeDay)
    if (next.isBefore(today)) {
        val nextMonth = baseMonth.plusMonths(intervalMonths.coerceAtLeast(1).toLong())
        val maxDay = nextMonth.lengthOfMonth()
        next = nextMonth.withDayOfMonth(executeDay.coerceIn(1, 31).coerceAtMost(maxDay))
    }
    return next.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

private fun formatRecurringDate(nextRunAt: Long): String {
    if (nextRunAt <= 0L) return "—"
    return Instant.ofEpochMilli(nextRunAt).atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
}

private fun writeJson(context: Context, uri: Uri, txs: List<Transaction>) {
    try {
        val json = JSONObject().apply {
            put("transactions", org.json.JSONArray().apply {
                txs.forEach { tx ->
                    put(JSONObject().apply {
                        put("id", tx.id)
                        put("amount", tx.amount)
                        put("type", tx.type.name)
                        put("category", tx.category)
                        put("description", tx.description)
                        put("rewards", tx.rewards)
                        put("date", tx.date)
                    })
                }
            })
        }
        context.contentResolver.openOutputStream(uri)?.use { it.write(json.toString().toByteArray()) }
    } catch (e: Exception) {}
}

private fun writeCsv(context: Context, uri: Uri, txs: List<Transaction>) {
    try {
        val csv = StringBuilder("Date,Type,Amount,Category,Description,Rewards\n")
        txs.forEach { csv.append("${it.date},${it.type.name},${it.amount},${it.category},${it.description},${it.rewards}\n") }
        context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toString().toByteArray()) }
    } catch (e: Exception) {}
}

private fun readJson(context: Context, uri: Uri): List<TransactionDraft> {
    return try {
        val jsonText = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return emptyList()
        val array = JSONObject(jsonText).getJSONArray("transactions")
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            TransactionDraft(obj.getDouble("amount"), TransactionType.valueOf(obj.getString("type")), obj.getString("category"), obj.getString("description"), obj.getDouble("rewards"), obj.getString("date"), null)
        }
    } catch (e: Exception) { emptyList() }
}

private fun readCsv(context: Context, uri: Uri): List<TransactionDraft> {
    return try {
        val lines = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readLines() } ?: return emptyList()
        lines.drop(1).mapNotNull { line ->
            val p = line.split(",")
            if (p.size < 6) null else TransactionDraft(p[2].toDouble(), TransactionType.valueOf(p[1]), p[3], p[4], p[5].toDouble(), p[0], null)
        }
    } catch (e: Exception) { emptyList() }
}
