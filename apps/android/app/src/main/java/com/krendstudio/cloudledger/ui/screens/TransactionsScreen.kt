package com.krendstudio.cloudledger.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.krendstudio.cloudledger.model.LedgerMember
import com.krendstudio.cloudledger.model.Transaction
import com.krendstudio.cloudledger.model.TransactionType
import com.krendstudio.cloudledger.ui.components.DropdownField
import com.krendstudio.cloudledger.util.DateUtils
import com.krendstudio.cloudledger.viewmodel.AppViewModel
import kotlinx.coroutines.launch
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
    var editing by remember { mutableStateOf<Transaction?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val filtered = if (search.isBlank()) {
        transactions
    } else {
        val token = search.trim().lowercase()
        transactions.filter {
            it.description.lowercase().contains(token) ||
                it.category.lowercase().contains(token) ||
                it.amount.toString().contains(token)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "交易紀錄", style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text(text = "搜尋描述/分類/金額") },
            modifier = Modifier.fillMaxWidth()
        )

        message?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }

        if (filtered.isEmpty()) {
            Text(text = "沒有交易紀錄")
        } else {
            filtered.forEach { tx ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editing = tx }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = tx.description, style = MaterialTheme.typography.bodyLarge)
                        Text(text = "${tx.category} · ${tx.date}", style = MaterialTheme.typography.bodySmall)
                    }
                    val sign = if (tx.type == TransactionType.EXPENSE) "-" else "+"
                    Text(text = "$sign${tx.amount.toInt()}")
                }
            }
        }
    }

    editing?.let { tx ->
        val fallbackMember = authState.user?.let {
            LedgerMember(uid = it.uid, displayName = it.displayName, photoUrl = it.photoUrl)
        }
        val availableMembers = if (members.isNotEmpty()) members else listOfNotNull(fallbackMember)
        EditTransactionDialog(
            transaction = tx,
            expenseCategories = expenseCategories,
            incomeCategories = incomeCategories,
            members = availableMembers,
            onDismiss = { editing = null },
            onSave = { updates ->
                editing = null
                updates?.let { payload ->
                    val expectedUpdatedAt = tx.updatedAt ?: tx.createdAt
                    scope.launch {
                        val result = viewModel.updateTransaction(tx.id, payload, expectedUpdatedAt)
                        message = result.exceptionOrNull()?.message
                        if (result.isSuccess) {
                            message = null
                        }
                    }
                }
            },
            onDelete = {
                editing = null
                scope.launch { viewModel.deleteTransaction(tx.id) }
            }
        )
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
    var amountText by remember { mutableStateOf(transaction.amount.toString()) }
    var description by remember { mutableStateOf(transaction.description) }
    var rewardsText by remember { mutableStateOf(transaction.rewards.toString()) }
    var type by remember { mutableStateOf(transaction.type) }
    var category by remember { mutableStateOf(transaction.category) }
    var date by remember {
        mutableStateOf(DateUtils.parseLocalDate(transaction.date) ?: LocalDate.now())
    }
    var targetUserUid by remember { mutableStateOf(transaction.targetUserUid) }

    val categories = if (type == TransactionType.EXPENSE) expenseCategories else incomeCategories

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "編輯交易") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(text = "金額") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = rewardsText,
                    onValueChange = { rewardsText = it },
                    label = { Text(text = "回饋") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { type = TransactionType.EXPENSE }) {
                        Text(text = "支出")
                    }
                    OutlinedButton(onClick = { type = TransactionType.INCOME }) {
                        Text(text = "收入")
                    }
                }
                DropdownField(
                    label = "分類",
                    options = categories,
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
                    value = date.format(DateTimeFormatter.ISO_DATE),
                    onValueChange = {
                        val parsed = DateUtils.parseLocalDate(it)
                        if (parsed != null) date = parsed
                    },
                    label = { Text(text = "日期") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (members.isNotEmpty()) {
                    DropdownField(
                        label = "記帳人",
                        options = members.map { it.displayName },
                        selected = members.firstOrNull { it.uid == targetUserUid }?.displayName ?: "",
                        onSelected = { label ->
                            targetUserUid = members.firstOrNull { it.displayName == label }?.uid
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amount = amountText.toDoubleOrNull() ?: 0.0
                val rewards = rewardsText.toDoubleOrNull() ?: 0.0
                val updates = mapOf(
                    "amount" to amount,
                    "type" to type.name,
                    "category" to category,
                    "description" to description,
                    "rewards" to rewards,
                    "date" to date.format(DateTimeFormatter.ISO_DATE),
                    "targetUserUid" to targetUserUid
                )
                onSave(updates)
            }) {
                Text(text = "儲存")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDelete) {
                    Text(text = "刪除")
                }
                OutlinedButton(onClick = onDismiss) {
                    Text(text = "取消")
                }
            }
        }
    )
}
