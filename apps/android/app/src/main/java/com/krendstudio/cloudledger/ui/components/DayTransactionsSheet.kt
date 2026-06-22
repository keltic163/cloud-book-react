package com.krendstudio.cloudledger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.Close
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.krendstudio.cloudledger.model.LedgerMember
import com.krendstudio.cloudledger.model.Transaction
import com.krendstudio.cloudledger.model.TransactionType
import com.krendstudio.cloudledger.ui.components.DropdownField
import com.krendstudio.cloudledger.util.DateUtils
import com.krendstudio.cloudledger.util.formatNumber
import com.krendstudio.cloudledger.util.formatPlainNumber
import com.krendstudio.cloudledger.viewmodel.AppViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DayTransactionsSheet(
    date: LocalDate,
    transactions: List<Transaction>,
    membersById: Map<String, LedgerMember>,
    expenseCategories: List<String>,
    incomeCategories: List<String>,
    members: List<LedgerMember>,
    navBarHeight: androidx.compose.ui.unit.Dp,
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val dayTxs = transactions.filter { DateUtils.parseLocalDate(it.date) == date }
    val scope = rememberCoroutineScope()
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val sheetHalfRatio = 0.55f
        val sheetFullRatio = 0.9f
        val containerHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxHeight.toPx() }
        val navBarHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { navBarHeight.toPx() }
        val sheetContainerHeightPx = (containerHeightPx - navBarHeightPx).coerceAtLeast(0f)
        val minHeightPx = sheetContainerHeightPx * sheetHalfRatio
        val maxSheetPx = sheetContainerHeightPx * sheetFullRatio
        val scope = rememberCoroutineScope()
        val sheetHeightAnim = remember { Animatable(0f) }
        val sheetHeight = sheetHeightAnim.value.takeIf { it > 0f } ?: minHeightPx
        val draggableState = rememberDraggableState { delta ->
            val next = (sheetHeightAnim.value - delta).coerceIn(minHeightPx, maxSheetPx)
            scope.launch { sheetHeightAnim.snapTo(next) }
        }

        LaunchedEffect(sheetContainerHeightPx) {
            if (sheetHeightAnim.value <= 0f) {
                sheetHeightAnim.snapTo(0f)
                sheetHeightAnim.animateTo(minHeightPx, animationSpec = tween(durationMillis = 240))
            } else {
                val clamped = sheetHeightAnim.value.coerceIn(minHeightPx, maxSheetPx)
                if (clamped != sheetHeightAnim.value) {
                    sheetHeightAnim.snapTo(clamped)
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = navBarHeight)
                    .background(Color.Transparent)
                    .clickable { onDismiss() }
            )
            Surface(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(androidx.compose.ui.platform.LocalDensity.current) { sheetHeight.toDp() })
                    .align(Alignment.BottomCenter)
                    .clickable(enabled = false) { }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 24.dp + navBarHeight, start = 16.dp, end = 16.dp, top = 12.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .draggable(
                                orientation = Orientation.Vertical,
                                state = draggableState,
                                onDragStopped = {
                                    val mid = (minHeightPx + maxSheetPx) / 2f
                                    val target = if (sheetHeightAnim.value >= mid) maxSheetPx else minHeightPx
                                    scope.launch {
                                        sheetHeightAnim.animateTo(target, animationSpec = tween(durationMillis = 200))
                                    }
                                }
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.outlineVariant)
                                .align(Alignment.CenterHorizontally)
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("${date.monthValue}月${date.dayOfMonth}日 交易", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("${dayTxs.size} 筆交易", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, null) }
                        }
                    }

                    if (dayTxs.isEmpty()) {
                        Text("當日無紀錄", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 20.dp))
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            dayTxs.forEach { tx ->
                                DashboardTransactionRow(
                                    transaction = tx,
                                    member = membersById[tx.targetUserUid ?: tx.creatorUid],
                                    onClick = { editingTransaction = tx }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    editingTransaction?.let { tx ->
        EditTransactionDialog(
            transaction = tx,
            expenseCategories = expenseCategories,
            incomeCategories = incomeCategories,
            members = members,
            onDismiss = { editingTransaction = null },
            onSave = { updates ->
                updates?.let { payload ->
                    scope.launch {
                        viewModel.updateTransaction(tx.id, payload, tx.updatedAt ?: tx.createdAt)
                        editingTransaction = null
                    }
                } ?: run { editingTransaction = null }
            },
            onDelete = {
                editingTransaction = null
                scope.launch { viewModel.deleteTransaction(tx.id) }
            }
        )
    }
}

@Composable
private fun DashboardTransactionRow(
    transaction: Transaction,
    member: LedgerMember?,
    onClick: () -> Unit
) {
    val isExpense = transaction.type == TransactionType.EXPENSE
    val (chipBg, chipText) = getCategoryColors(transaction.category)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(chipBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(transaction.category.take(1), color = chipText, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = transaction.description,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (transaction.rewards > 0) {
                            Text(
                                text = "+${formatNumber(transaction.rewards)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFB45309),
                                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFFDE68A)).padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(text = transaction.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (isExpense) "-" else "+"}$${formatNumber(transaction.amount)}",
                    color = if (isExpense) Color(0xFFF43F5E) else Color(0xFF10B981),
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                member?.let {
                    if (!it.photoUrl.isNullOrBlank()) {
                        coil.compose.AsyncImage(model = it.photoUrl, contentDescription = null, modifier = Modifier.size(20.dp).clip(CircleShape))
                    } else {
                        Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant), contentAlignment = Alignment.Center) {
                            Text(text = it.displayName?.take(1) ?: "?", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
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
    var dateText by remember {
        mutableStateOf(date.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")))
    }
    var targetUserUid by remember { mutableStateOf(transaction.targetUserUid) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    )

    val categories = if (type == TransactionType.EXPENSE) expenseCategories else incomeCategories

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth(0.99f)
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
                            value = dateText,
                            onValueChange = {
                                dateText = it
                                val parsed = DateUtils.parseLocalDate(it)
                                if (parsed != null) {
                                    date = parsed
                                }
                            },
                            modifier = Modifier.weight(1f),
                            trailingIcon = Icons.Default.CalendarToday,
                            onTrailingIconClick = { showDatePicker = true }
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
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
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

    if (showDatePicker) {
        LaunchedEffect(showDatePicker) {
            if (showDatePicker) {
                datePickerState.selectedDateMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        }
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                        dateText = date.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
                    }
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
private fun CompactInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onTrailingIconClick: (() -> Unit)? = null
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
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth()
        ) { innerTextField ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) { innerTextField() }
                    if (trailingIcon != null) {
                        if (onTrailingIconClick != null) {
                            IconButton(onClick = onTrailingIconClick, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = trailingIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
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
}

private fun getCategoryColors(category: String): Pair<Color, Color> {
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
