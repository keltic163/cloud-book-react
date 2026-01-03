package com.krendstudio.cloudledger.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.krendstudio.cloudledger.model.LedgerMember
import com.krendstudio.cloudledger.model.TransactionType
import com.krendstudio.cloudledger.ui.components.DropdownField
import com.krendstudio.cloudledger.util.DateUtils
import com.krendstudio.cloudledger.viewmodel.AppViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AddTransactionScreen(viewModel: AppViewModel) {
    val expenseCategories by viewModel.expenseCategories.collectAsState()
    val incomeCategories by viewModel.incomeCategories.collectAsState()
    val members by viewModel.members.collectAsState()
    val authState by viewModel.authState.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val prefs = remember {
        context.getSharedPreferences("cloudledger_prefs", Context.MODE_PRIVATE)
    }

    var type by remember { mutableStateOf(TransactionType.EXPENSE) }
    var amountText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var rewardsText by remember { mutableStateOf("0") }
    var category by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(selectedDate ?: LocalDate.now()) }
    var targetUserUid by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    var smartInput by remember { mutableStateOf("") }
    var isParsing by remember { mutableStateOf(false) }
    var parseError by remember { mutableStateOf<String?>(null) }
    val aiEnabled = remember { prefs.getBoolean("ai_enabled", false) }
    val aiKey = remember { prefs.getString("ai_key", null) }

    var isRecurring by remember { mutableStateOf(false) }
    var intervalMonthsText by remember { mutableStateOf("1") }
    var executeDayText by remember { mutableStateOf(date.dayOfMonth.toString()) }
    var totalRunsText by remember { mutableStateOf("12") }
    var limitedRuns by remember { mutableStateOf(false) }

    val availableCategories = if (type == TransactionType.EXPENSE) expenseCategories else incomeCategories
    androidx.compose.runtime.LaunchedEffect(availableCategories, type) {
        if (availableCategories.isNotEmpty() && category.isBlank()) {
            category = availableCategories.first()
        }
    }

    val fallbackMember = authState.user?.let {
        LedgerMember(uid = it.uid, displayName = it.displayName, photoUrl = it.photoUrl)
    }
    val availableMembers = if (members.isNotEmpty()) members else listOfNotNull(fallbackMember)
    androidx.compose.runtime.LaunchedEffect(availableMembers) {
        if (targetUserUid == null && availableMembers.isNotEmpty()) {
            targetUserUid = availableMembers.first().uid
        }
    }

    var isListening by remember { mutableStateOf(false) }
    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }
    DisposableEffect(speechRecognizer) {
        onDispose {
            speechRecognizer?.destroy()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            message = "需要麥克風權限才能語音輸入"
            return@rememberLauncherForActivityResult
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.TAIWAN.toLanguageTag())
        }
        speechRecognizer?.startListening(intent)
        isListening = true
    }

    val recognitionListener = remember {
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
            }

            override fun onError(error: Int) {
                isListening = false
                message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "語音輸入失敗：麥克風異常"
                    SpeechRecognizer.ERROR_CLIENT -> "語音輸入失敗：用戶端錯誤"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "語音輸入失敗：權限不足"
                    SpeechRecognizer.ERROR_NETWORK -> "語音輸入失敗：網路問題"
                    SpeechRecognizer.ERROR_NO_MATCH -> "語音輸入失敗：沒有辨識結果"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "語音輸入失敗：逾時未收到語音"
                    SpeechRecognizer.ERROR_SERVER -> "語音輸入失敗：伺服器錯誤"
                    else -> "語音輸入失敗：未知錯誤"
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                if (matches.isNotEmpty()) {
                    val newText = matches.first()
                    smartInput = if (smartInput.isBlank()) newText else "$smartInput $newText"
                } else {
                    message = "語音辨識失敗，請再試一次"
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    androidx.compose.runtime.LaunchedEffect(speechRecognizer) {
        speechRecognizer?.setRecognitionListener(recognitionListener)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "新增交易", style = MaterialTheme.typography.titleLarge)

        if (aiEnabled) {
            OutlinedTextField(
                value = smartInput,
                onValueChange = { smartInput = it },
                label = { Text(text = "智慧輸入") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        if (speechRecognizer == null) {
                            message = "此裝置不支援語音輸入"
                            return@OutlinedButton
                        }
                        if (isListening) {
                            speechRecognizer.stopListening()
                            isListening = false
                            return@OutlinedButton
                        }
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.TAIWAN.toLanguageTag())
                        }
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPermission) {
                            speechRecognizer.startListening(intent)
                            isListening = true
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                ) {
                    Text(text = if (isListening) "停止語音" else "語音輸入")
                }
                OutlinedButton(onClick = { smartInput = "" }) {
                    Text(text = "清空")
                }
                Button(
                    onClick = {
                        if (smartInput.isBlank()) {
                            message = "請輸入內容"
                            return@Button
                        }
                        isParsing = true
                        parseError = null
                        scope.launch {
                            viewModel.parseSmartInput(smartInput, availableCategories, aiKey)
                                .onSuccess { parsed ->
                                    amountText = parsed.amount.toString()
                                    type = parsed.type
                                    category = parsed.category.ifBlank { availableCategories.firstOrNull() ?: "其他" }
                                    description = parsed.description
                                    rewardsText = parsed.rewards.toString()
                                    parsed.date?.let { parsedDate ->
                                        DateUtils.parseLocalDate(parsedDate)?.let { date = it }
                                    }
                                    message = "已解析，請確認內容"
                                }
                                .onFailure {
                                    parseError = "解析失敗：${it.message}"
                                    message = parseError
                                }
                            isParsing = false
                        }
                    },
                    enabled = !isParsing
                ) {
                    Text(text = if (isParsing) "解析中..." else "解析")
                }
            }
        } else {
            Text(text = "AI 未啟用，請到設定頁開啟", style = MaterialTheme.typography.bodySmall)
        }

        parseError?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { type = TransactionType.EXPENSE }) {
                Text(text = "支出")
            }
            OutlinedButton(onClick = { type = TransactionType.INCOME }) {
                Text(text = "收入")
            }
        }

        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = { Text(text = "金額") },
            modifier = Modifier.fillMaxWidth()
        )

        DropdownField(
            label = "分類",
            options = availableCategories,
            selected = category,
            onSelected = { category = it }
        )

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text(text = "描述") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = rewardsText,
            onValueChange = { rewardsText = it },
            label = { Text(text = "回饋") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = date.format(DateTimeFormatter.ISO_DATE),
            onValueChange = {
                val parsed = DateUtils.parseLocalDate(it)
                if (parsed != null) {
                    date = parsed
                }
            },
            label = { Text(text = "日期(YYYY-MM-DD)") },
            modifier = Modifier.fillMaxWidth()
        )

        if (availableMembers.isNotEmpty()) {
            DropdownField(
                label = "記帳人",
                options = availableMembers.map { it.displayName },
                selected = availableMembers.firstOrNull { it.uid == targetUserUid }?.displayName ?: "",
                onSelected = { label ->
                    targetUserUid = availableMembers.firstOrNull { it.displayName == label }?.uid
                }
            )
        }

        Button(
            onClick = {
                val amount = amountText.toDoubleOrNull()
                if (amount == null) {
                    message = "請輸入正確金額"
                    return@Button
                }
                val rewards = rewardsText.toDoubleOrNull() ?: 0.0
                scope.launch {
                    viewModel.addTransaction(
                        amount = amount,
                        type = type,
                        category = category.ifBlank { availableCategories.firstOrNull() ?: "其他" },
                        description = description.ifBlank { "未命名" },
                        rewards = rewards,
                        date = date,
                        targetUserUid = targetUserUid
                    ).onFailure {
                        message = "儲存失敗：${it.message}"
                    }.onSuccess {
                        if (isRecurring) {
                            val interval = intervalMonthsText.toIntOrNull() ?: 1
                            val executeDay = executeDayText.toIntOrNull() ?: date.dayOfMonth
                            val nextRunAt = computeNextRunAt(date, interval, executeDay)
                            val totalRuns = if (limitedRuns) totalRunsText.toIntOrNull() else null
                            viewModel.createRecurringTemplate(
                                title = description.ifBlank { "未命名" },
                                amount = amount,
                                type = type,
                                category = category.ifBlank { availableCategories.firstOrNull() ?: "其他" },
                                intervalMonths = interval,
                                executeDay = executeDay,
                                nextRunAt = nextRunAt,
                                totalRuns = totalRuns,
                                remainingRuns = totalRuns
                            )
                        }
                        message = "已新增交易"
                        amountText = ""
                        description = ""
                        rewardsText = "0"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "儲存")
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "固定收支")
            Switch(checked = isRecurring, onCheckedChange = { isRecurring = it })
        }
        if (isRecurring) {
            OutlinedTextField(
                value = executeDayText,
                onValueChange = { executeDayText = it },
                label = { Text(text = "每月扣款日") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = intervalMonthsText,
                onValueChange = { intervalMonthsText = it },
                label = { Text(text = "每 N 個月") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "限制次數")
                Switch(checked = limitedRuns, onCheckedChange = { limitedRuns = it })
            }
            if (limitedRuns) {
                OutlinedTextField(
                    value = totalRunsText,
                    onValueChange = { totalRunsText = it },
                    label = { Text(text = "執行次數") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        message?.let { Text(text = it, color = MaterialTheme.colorScheme.primary) }
    }
}

private fun computeNextRunAt(baseDate: LocalDate, intervalMonths: Int, executeDay: Int): Long {
    val safeDay = executeDay.coerceIn(1, 31)
    val baseMonth = baseDate.withDayOfMonth(1)
    val candidate = baseMonth.plusMonths(0)
    val daysInMonth = candidate.lengthOfMonth()
    val day = safeDay.coerceAtMost(daysInMonth)
    var next = candidate.withDayOfMonth(day)
    if (next.isBefore(baseDate)) {
        val nextMonth = baseMonth.plusMonths(intervalMonths.toLong())
        val maxDay = nextMonth.lengthOfMonth()
        next = nextMonth.withDayOfMonth(safeDay.coerceAtMost(maxDay))
    }
    return next.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
