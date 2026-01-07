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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.krendstudio.cloudledger.model.LedgerMember
import com.krendstudio.cloudledger.model.Transaction
import com.krendstudio.cloudledger.model.TransactionType
import com.krendstudio.cloudledger.ui.components.DropdownField
import com.krendstudio.cloudledger.util.DateUtils
import com.krendstudio.cloudledger.util.formatNumber
import com.krendstudio.cloudledger.util.formatPlainNumber
import com.krendstudio.cloudledger.viewmodel.AppViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TransactionsScreen(viewModel: AppViewModel) {
    val transactions by viewModel.transactions.collectAsState()
    val expenseCategories by viewModel.expenseCategories.collectAsState()
    val incomeCategories by viewModel.incomeCategories.collectAsState()
    val members by viewModel.members.collectAsState()
    val authState by viewModel.authState.collectAsState()

    var search by remember { mutableStateOf("") }
    var debouncedSearch by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Transaction?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(search) {
        val value = search
        delay(200)
        if (value == search) {
            debouncedSearch = value.trim().lowercase()
        }
    }

    val filtered = remember(transactions, debouncedSearch) {
        if (debouncedSearch.isBlank()) {
            transactions
        } else {
            val tokens = debouncedSearch.split(Regex("\\s+")).filter { it.isNotBlank() }
            transactions.filter { tx ->
                val desc = tx.description.lowercase()
                val cat = tx.category.lowercase()
                val amt = tx.amount.toString()
                tokens.all { token -> desc.contains(token) || cat.contains(token) || amt.contains(token) }
            }
        }
    }

    val fallbackMember = authState.user?.let {
        LedgerMember(uid = it.uid, displayName = it.displayName, photoUrl = it.photoUrl)
    }
    val availableMembers = members.ifEmpty { listOfNotNull(fallbackMember) }
    val membersById = remember(availableMembers) { availableMembers.associateBy { it.uid } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (transactions.isEmpty()) {
            EmptyTransactionState()
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "近期紀錄",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "總計 ${transactions.size} / 顯示 ${filtered.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "搜尋",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    BasicTextField(
                        value = search,
                        onValueChange = { search = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f)
                    ) { innerTextField ->
                        Box {
                            if (search.isBlank()) {
                                Text(
                                    text = "搜尋：描述 / 分類 / 金額",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            innerTextField()
                        }
                    }
                    if (search.isNotBlank()) {
                        IconButton(
                            onClick = { search = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "清除",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            message?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "沒有符合條件的紀錄", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.id }) { tx ->
                        val targetId = tx.targetUserUid ?: tx.creatorUid
                        val member = membersById[targetId]
                        TransactionRow(
                            transaction = tx,
                            member = member,
                            onClick = { editing = tx }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }

    editing?.let { tx ->
        EditTransactionDialog(
            transaction = tx,
            expenseCategories = expenseCategories,
            incomeCategories = incomeCategories,
            members = availableMembers,
            onDismiss = { editing = null },
            onSave = { updates ->
                updates?.let { payload ->
                    val expectedUpdatedAt = tx.updatedAt ?: tx.createdAt
                    scope.launch {
                        val result = viewModel.updateTransaction(tx.id, payload, expectedUpdatedAt)
                        if (result.isSuccess) {
                            message = null
                            editing = null
                        } else {
                            message = result.exceptionOrNull()?.message
                        }
                    }
                } ?: run { editing = null }
            },
            onDelete = {
                editing = null
                scope.launch { viewModel.deleteTransaction(tx.id) }
            }
        )
    }
}

@Composable
private fun CompactInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    trailingIcon: ImageVector? = null
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, 
            modifier = Modifier.padding(bottom = 6.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            readOnly = readOnly,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth()
        ) { innerTextField ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant, // 跟隨主題
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .heightIn(min = 22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        innerTextField()
                    }
                    if (trailingIcon != null) {
                        Icon(
                            imageVector = trailingIcon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditTransactionDialog(
    transaction: Transaction,
    expenseCategories: List<String>,
    incomeCategories: List<String>,
    members: List<LedgerMember>,
    onDismiss: () -> Unit,
    onSave: (Map<String, Any?>?) -> Unit,
    onDelete: () -> Unit
) {
    var amountText by remember { mutableStateOf(formatPlainNumber(transaction.amount)) }
    var description by remember { mutableStateOf(transaction.description) }
    var rewardsText by remember { mutableStateOf(formatPlainNumber(transaction.rewards)) }
    var type by remember { mutableStateOf(transaction.type) }
    var category by remember { mutableStateOf(transaction.category) }
    var date by remember {
        mutableStateOf(DateUtils.parseLocalDate(transaction.date) ?: LocalDate.now())
    }
    var targetUserUid by remember { mutableStateOf(transaction.targetUserUid) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val categories = if (type == TransactionType.EXPENSE) expenseCategories else incomeCategories

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f)) // 背景暗度
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface, // 改為主題色
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clickable(enabled = false) { }
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "編輯紀錄", 
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CompactInputField(
                            label = "金額",
                            value = amountText,
                            onValueChange = { amountText = it },
                            modifier = Modifier.weight(1f),
                            trailingIcon = Icons.Default.UnfoldMore
                        )
                        CompactInputField(
                            label = "點數 / 回饋",
                            value = rewardsText,
                            onValueChange = { rewardsText = it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DropdownField(
                            label = "類型",
                            options = listOf("支出", "收入"),
                            selected = if (type == TransactionType.EXPENSE) "支出" else "收入",
                            onSelected = { label ->
                                type = if (label == "支出") TransactionType.EXPENSE else TransactionType.INCOME
                            },
                            modifier = Modifier.weight(1f)
                        )
                        DropdownField(
                            label = "分類",
                            options = categories,
                            selected = category,
                            onSelected = { category = it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    CompactInputField(
                        label = "描述",
                        value = description,
                        onValueChange = { description = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                        CompactInputField(
                            label = "日期",
                            value = date.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")),
                            onValueChange = {
                                val parsed = DateUtils.parseLocalDate(it)
                                if (parsed != null) date = parsed
                            },
                            modifier = Modifier.weight(1f),
                            trailingIcon = Icons.Default.CalendarToday
                        )
                        if (members.isNotEmpty()) {
                            DropdownField(
                                label = "成員",
                                options = members.map { it.displayName },
                                selected = members.firstOrNull { it.uid == targetUserUid }?.displayName ?: "",
                                onSelected = { label ->
                                    targetUserUid = members.firstOrNull { it.displayName == label }?.uid
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant, // 跟隨主題
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text(text = "取消", fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = {
                                val updates = mapOf(
                                    "amount" to (amountText.toDoubleOrNull() ?: 0.0),
                                    "type" to type.name,
                                    "category" to category,
                                    "description" to description,
                                    "rewards" to (rewardsText.toDoubleOrNull() ?: 0.0),
                                    "date" to date.format(DateTimeFormatter.ISO_DATE),
                                    "targetUserUid" to targetUserUid
                                )
                                onSave(updates)
                            },
                            modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(text = "儲存", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    
                    Text(
                        text = "刪除紀錄",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .clickable { showDeleteConfirm = true }
                            .padding(top = 2.dp)
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("刪除紀錄") },
            text = { Text("確認要刪除此筆紀錄嗎？此動作無法復原。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) { Text("刪除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun EmptyTransactionState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Download, "尚無交易", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        Text("尚無交易紀錄", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TransactionRow(transaction: Transaction, member: LedgerMember?, onClick: () -> Unit) {
    val isExpense = transaction.type == TransactionType.EXPENSE
    val (chipBg, chipText) = categoryChipColor(transaction.category)
    val dateText = DateUtils.parseLocalDate(transaction.date)?.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")) ?: transaction.date

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(chipBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(transaction.category.take(1).ifBlank { "?" }, color = chipText, fontWeight = FontWeight.Bold)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(transaction.description, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                        if (transaction.rewards > 0) {
                            Text("+${formatNumber(transaction.rewards)} 點", style = MaterialTheme.typography.labelSmall, color = Color(0xFFB45309), modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFFDE68A)).padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(dateText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("·", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Text(transaction.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${if (isExpense) "-" else "+"}$${formatNumber(transaction.amount)}", color = if (isExpense) Color(0xFFF43F5E) else Color(0xFF10B981), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                member?.let {
                    val photoUrl = it.photoUrl
                    if (!photoUrl.isNullOrBlank()) {
                        AsyncImage(model = photoUrl, contentDescription = it.displayName ?: "User", modifier = Modifier.size(20.dp).clip(CircleShape))
                    } else {
                        Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            Text(text = it.displayName?.take(1) ?: "?", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

private fun categoryChipColor(category: String): Pair<Color, Color> {
    return when (category) {
        "餐飲" -> Color(0xFFFFEDD5) to Color(0xFFF97316)
        "交通" -> Color(0xFFDBEAFE) to Color(0xFF2563EB)
        "日常" -> Color(0xFFFCE7F3) to Color(0xFFDB2777)
        "居家" -> Color(0xFFEDE9FE) to Color(0xFF7C3AED)
        "社交" -> Color(0xFFDCFCE7) to Color(0xFF059669)
        "娛樂" -> Color(0xFFFEF9C3) to Color(0xFFCA8A04)
        "教育" -> Color(0xFFCFFAFE) to Color(0xFF0891B2)
        else -> Color(0xFFF1F5F9) to Color(0xFF64748B)
    }
}
