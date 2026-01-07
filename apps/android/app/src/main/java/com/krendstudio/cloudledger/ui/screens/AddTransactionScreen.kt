package com.krendstudio.cloudledger.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.krendstudio.cloudledger.model.LedgerMember
import com.krendstudio.cloudledger.model.ParsedTransaction
import com.krendstudio.cloudledger.model.TransactionType
import com.krendstudio.cloudledger.ui.components.DropdownField
import com.krendstudio.cloudledger.util.DateUtils
import com.krendstudio.cloudledger.util.formatPlainNumber
import com.krendstudio.cloudledger.viewmodel.AppViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    viewModel: AppViewModel,
    initialMode: String? = null,
    startVoice: Boolean = false,
    onVoiceHandled: () -> Unit = {},
    onSaved: () -> Unit = {}
) {
    val expenseCategories by viewModel.expenseCategories.collectAsState()
    val incomeCategories by viewModel.incomeCategories.collectAsState()
    val members by viewModel.members.collectAsState()
    val authState by viewModel.authState.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("cloudledger_prefs", Context.MODE_PRIVATE) }
    val fieldHeight = 48.dp

    var mode by remember { mutableStateOf(initialMode ?: "manual") }
    var type by remember { mutableStateOf(TransactionType.EXPENSE) }
    var amountText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var rewardsText by remember { mutableStateOf("0") }
    var category by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(selectedDate ?: LocalDate.now()) }
    var targetUserUid by remember(authState.user) { mutableStateOf(authState.user?.uid) }

    var smartInput by remember { mutableStateOf("") }
    var smartStatus by remember { mutableStateOf<String?>(null) }
    var smartParsing by remember { mutableStateOf(false) }
    var aiEnabled by remember { mutableStateOf(prefs.getBoolean("ai_enabled", false)) }
    var aiKeyStored by remember { mutableStateOf(prefs.contains("ai_key")) }
    var pendingVoiceAfterPermission by remember { mutableStateOf(false) }
    var showVoicePrompt by remember { mutableStateOf(false) }
    var voicePending by remember { mutableStateOf(false) }
    var autoStartVoice by remember { mutableStateOf(false) }
    var saveStatus by remember { mutableStateOf<String?>(null) }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                smartInput = spoken
            }
        }
        voicePending = false
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingVoiceAfterPermission) {
            if (autoStartVoice) {
                launchSpeechInput(context, speechLauncher)
            } else {
                showVoicePrompt = true
            }
        } else if (!granted) {
            smartStatus = "需要麥克風權限才能使用語音輸入"
            voicePending = false
        }
        pendingVoiceAfterPermission = false
        autoStartVoice = false
    }
    
    var isRecurring by remember { mutableStateOf(false) }
    var intervalMonthsText by remember { mutableStateOf("1") }
    var executeDayText by remember { mutableStateOf(date.dayOfMonth.toString()) }
    var totalRunsText by remember { mutableStateOf("12") }
    var limitedRuns by remember { mutableStateOf(false) }

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    )

    val availableCategories = if (type == TransactionType.EXPENSE) expenseCategories else incomeCategories

    LaunchedEffect(mode) {
        aiEnabled = prefs.getBoolean("ai_enabled", false)
        aiKeyStored = prefs.contains("ai_key")
    }

    LaunchedEffect(initialMode) {
        initialMode?.let { mode = it }
    }

    LaunchedEffect(startVoice) {
        if (!startVoice) return@LaunchedEffect
        mode = "smart"
        smartStatus = null
        voicePending = true
        autoStartVoice = true
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            launchSpeechInput(context, speechLauncher)
        } else {
            pendingVoiceAfterPermission = true
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        onVoiceHandled()
    }
    
    LaunchedEffect(type) {
        if (availableCategories.isNotEmpty()) {
            category = availableCategories.first()
        }
    }

    val fallbackMember = authState.user?.let { LedgerMember(uid = it.uid, displayName = it.displayName, photoUrl = it.photoUrl) }
    val availableMembers = members.ifEmpty { listOfNotNull(fallbackMember) }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Row(Modifier.fillMaxSize()) {
                TabItem(label = "智慧輸入", icon = Icons.Outlined.AutoAwesome, selected = mode == "smart", modifier = Modifier.weight(1f)) { mode = "smart" }
                TabItem(label = "手動輸入", icon = Icons.Outlined.Edit, selected = mode == "manual", modifier = Modifier.weight(1f)) { mode = "manual" }
            }
        }

        if (mode == "manual") {
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TypeToggleButton(label = "支出", selected = type == TransactionType.EXPENSE, activeColor = Color(0xFFE11D48), modifier = Modifier.weight(1f)) { type = TransactionType.EXPENSE }
                    TypeToggleButton(label = "收入", selected = type == TransactionType.INCOME, activeColor = Color(0xFF10B981), modifier = Modifier.weight(1f)) { type = TransactionType.INCOME }
                }
            }

            StatLabel("金額")
            Box(modifier = Modifier.fillMaxWidth().height(fieldHeight).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$ ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    BasicTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                    )
                }
                if (amountText.isEmpty()) {
                    Text("0.00", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 16.sp, modifier = Modifier.padding(start = 22.dp))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DropdownField(label = "分類", options = availableCategories, selected = category, onSelected = { category = it }, modifier = Modifier.weight(1f), fieldHeight = fieldHeight)
                Column(Modifier.weight(1f)) {
                    StatLabel("日期")
                    Box(modifier = Modifier.fillMaxWidth().height(fieldHeight).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { showDatePicker = true }.padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(date.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
                            Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            StatLabel("描述")
            CompactTextField(value = description, onValueChange = { description = it }, placeholder = "這筆消費是為了什麼？", modifier = Modifier.fillMaxWidth())

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatLabel("回饋 / 點數")
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.clip(RoundedCornerShape(0.dp)).background(Color(0xFFF59E0B)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                            Text("選填", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    CompactTextField(value = rewardsText, onValueChange = { rewardsText = it }, modifier = Modifier.fillMaxWidth(), keyboardType = KeyboardType.Number)
                }
                DropdownField(label = "成員", options = availableMembers.map { it.displayName ?: "未知" }, selected = availableMembers.find { it.uid == targetUserUid }?.displayName ?: "", onSelected = { name -> targetUserUid = availableMembers.find { it.displayName == name }?.uid }, modifier = Modifier.weight(1f), fieldHeight = fieldHeight)
            }

            Surface(shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("設為週期性", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("每月自動記帳 (分期或固定費用)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = isRecurring, onCheckedChange = { isRecurring = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF6366F1)))
                    }
                    if (isRecurring) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                StatLabel("每月扣款日")
                                DropdownField(label = "", options = (1..31).map { "${it} 號" }, selected = "${executeDayText} 號", onSelected = { executeDayText = it.split(" ")[0] }, modifier = Modifier.fillMaxWidth(), fieldHeight = fieldHeight)
                            }
                            Column(Modifier.weight(1f)) {
                                StatLabel("每 N 個月")
                                CompactTextField(value = intervalMonthsText, onValueChange = { intervalMonthsText = it }, modifier = Modifier.fillMaxWidth(), keyboardType = KeyboardType.Number)
                            }
                        }
                        StatLabel("執行次數")
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().height(40.dp)) {
                            Row(Modifier.padding(4.dp)) {
                                TabButtonSmall(text = "持續", selected = !limitedRuns, modifier = Modifier.weight(1f)) { limitedRuns = false }
                                TabButtonSmall(text = "指定次數", selected = limitedRuns, modifier = Modifier.weight(1f)) { limitedRuns = true }
                            }
                        }
                        if (limitedRuns) {
                            CompactTextField(value = totalRunsText, onValueChange = { totalRunsText = it }, modifier = Modifier.fillMaxWidth(), keyboardType = KeyboardType.Number)
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    val rewards = rewardsText.toDoubleOrNull() ?: 0.0
                    saveStatus = null
                    scope.launch {
                        viewModel.addTransaction(
                            amount = amount,
                            type = type,
                            category = category,
                            description = description.ifBlank { "未命名" },
                            rewards = rewards,
                            date = date,
                            targetUserUid = targetUserUid
                        ).onSuccess {
                            if (isRecurring) {
                                val interval = intervalMonthsText.toIntOrNull() ?: 1
                                val executeDay = executeDayText.toIntOrNull() ?: date.dayOfMonth
                                val nextRunAt = computeNextRunAt(date, interval, executeDay)
                                val totalRunsNum = if (limitedRuns) totalRunsText.toIntOrNull() else null
                                
                                viewModel.createRecurringTemplate(
                                    title = description.ifBlank { "未命名" },
                                    amount = amount,
                                    type = type,
                                    category = category,
                                    intervalMonths = interval,
                                    executeDay = executeDay,
                                    nextRunAt = nextRunAt,
                                    totalRuns = totalRunsNum,
                                    remainingRuns = totalRunsNum
                                ).onFailure { saveStatus = "固定收支建立失敗：${it.message}" }
                            }
                            amountText = ""
                            description = ""
                            rewardsText = "0"
                            onSaved()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
            ) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("儲存交易", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            saveStatus?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        } else {
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("請描述交易內容", fontWeight = FontWeight.SemiBold)
                    Box {
                        OutlinedTextField(
                            value = smartInput,
                            onValueChange = { smartInput = it },
                            placeholder = { Text("例：昨天晚餐 義大利麵 500 折扣 20") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            shape = RoundedCornerShape(8.dp)
                        )
                        IconButton(
                            onClick = {
                                if (voicePending) return@IconButton
                                smartStatus = null
                                voicePending = true
                                autoStartVoice = false
                                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    showVoicePrompt = true
                                } else {
                                    pendingVoiceAfterPermission = true
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (voicePending) Color(0xFFE11D48)
                                    else MaterialTheme.colorScheme.surface
                                )
                        ) {
                            Icon(
                                Icons.Outlined.Mic,
                                contentDescription = "語音輸入",
                                tint = if (voicePending) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (!aiEnabled) {
                        Text("AI 尚未啟用，請先到設定頁面開啟。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (!aiKeyStored) {
                        Text("尚未設定 API Key，請先到設定頁面完成設定。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("AI 會自動解析金額、分類、描述與日期。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("長按畫面下方的「+」可快速開啟語音辨識，立即開始輸入。", style = MaterialTheme.typography.labelSmall, color = Color(0xFF6366F1))
                    Button(
                        onClick = {
                            val apiKey = prefs.getString("ai_key", null)
                            val categories = expenseCategories + incomeCategories
                            smartParsing = true
                            smartStatus = null
                            scope.launch {
                                viewModel.parseSmartInput(smartInput, categories, apiKey)
                                    .onSuccess { parsed ->
                                        applySmartParse(parsed, expenseCategories, incomeCategories)?.let { updated ->
                                            type = updated.type
                                            amountText = updated.amountText
                                            rewardsText = updated.rewardsText
                                            description = updated.description
                                            category = updated.category
                                            updated.date?.let { date = it }
                                        }
                                        smartStatus = "已套用到欄位，請確認後儲存"
                                        mode = "manual"
                                    }
                                    .onFailure { smartStatus = "解析失敗：${it.message}" }
                                smartParsing = false
                            }
                        },
                        enabled = !smartParsing && aiEnabled && aiKeyStored && smartInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (smartParsing) "解析中..." else "解析並套用")
                    }
                    smartStatus?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (showVoicePrompt) {
        AlertDialog(
            onDismissRequest = {
                showVoicePrompt = false
                voicePending = false
            },
            title = { Text("語音輸入") },
            text = { Text("準備開始語音辨識，請清楚說出交易內容。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showVoicePrompt = false
                        launchSpeechInput(context, speechLauncher)
                    }
                ) { Text("開始") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showVoicePrompt = false
                        voicePending = false
                    }
                ) { Text("取消") }
            }
        )
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
                    showDatePicker = false
                }) { Text("確定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun TabItem(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier = modifier.fillMaxHeight().clickable { onClick() }.background(Color.Transparent), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, modifier = Modifier.size(18.dp), tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            }
            if (selected) {
                Spacer(Modifier.height(4.dp))
                Box(Modifier.width(40.dp).height(3.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            }
        }
    }
}

@Composable
private fun TypeToggleButton(label: String, selected: Boolean, activeColor: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        modifier = modifier.clickable { onClick() }
    ) {
        Box(modifier = Modifier.height(36.dp), contentAlignment = Alignment.Center) {
            Text(label, color = if (selected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Composable
private fun StatLabel(label: String) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun CompactTextField(value: String, onValueChange: (String) -> Unit, placeholder: String = "", modifier: Modifier, keyboardType: KeyboardType = KeyboardType.Text) {
    Box(modifier = modifier.height(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { inner ->
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), style = MaterialTheme.typography.bodyMedium)
                }
                inner()
            }
        )
    }
}

@Composable
private fun TabButtonSmall(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(6.dp), color = if (selected) Color(0xFF6366F1) else Color.Transparent, modifier = modifier.clickable { onClick() }) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(text, style = MaterialTheme.typography.labelMedium, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun computeNextRunAt(baseDate: LocalDate, intervalMonths: Int, executeDay: Int): Long {
    val safeInterval = max(1, intervalMonths)
    val safeDay = executeDay.coerceIn(1, 31)
    val baseMonth = baseDate.withDayOfMonth(1)
    val daysInBase = baseMonth.lengthOfMonth()
    val baseRunDay = safeDay.coerceAtMost(daysInBase)
    var next = baseMonth.withDayOfMonth(baseRunDay)
    if (!next.isAfter(baseDate)) {
        val nextMonth = baseMonth.plusMonths(safeInterval.toLong())
        val maxDay = nextMonth.lengthOfMonth()
        next = nextMonth.withDayOfMonth(safeDay.coerceAtMost(maxDay))
    }
    return next.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

private fun launchSpeechInput(
    context: Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PROMPT, "開始說話")
    }
    if (intent.resolveActivity(context.packageManager) != null) {
        launcher.launch(intent)
    }
}

private data class SmartApplyResult(
    val type: TransactionType,
    val amountText: String,
    val rewardsText: String,
    val category: String,
    val description: String,
    val date: LocalDate?
)

private fun applySmartParse(
    parsed: ParsedTransaction,
    expenseCategories: List<String>,
    incomeCategories: List<String>
): SmartApplyResult? {
    val parsedType = parsed.type
    val categories = if (parsedType == TransactionType.INCOME) incomeCategories else expenseCategories
    val category = when {
        parsed.category.isNotBlank() && categories.contains(parsed.category) -> parsed.category
        categories.isNotEmpty() -> categories.first()
        else -> parsed.category
    }
    val date = parsed.date?.let { DateUtils.parseLocalDate(it) }
    return SmartApplyResult(
        type = parsedType,
        amountText = formatPlainNumber(parsed.amount),
        rewardsText = formatPlainNumber(parsed.rewards),
        category = category,
        description = parsed.description.ifBlank { "未命名" },
        date = date
    )
}
