package com.krendstudio.cloudledger.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.krendstudio.cloudledger.model.TransactionType
import com.krendstudio.cloudledger.util.DateUtils
import com.krendstudio.cloudledger.viewmodel.AppViewModel
import java.time.YearMonth

@Composable
fun StatsScreen(viewModel: AppViewModel) {
    val transactions by viewModel.transactions.collectAsState()
    val expenseCategories by viewModel.expenseCategories.collectAsState()
    val incomeCategories by viewModel.incomeCategories.collectAsState()

    var yearMode by remember { mutableStateOf(false) }
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }

    val filtered = transactions.filter { tx ->
        val date = DateUtils.parseLocalDate(tx.date) ?: return@filter false
        if (yearMode) {
            date.year == currentMonth.year
        } else {
            YearMonth.from(date) == currentMonth
        }
    }

    val totalIncome = filtered
        .filter { it.type == TransactionType.INCOME }
        .sumOf { it.amount + it.rewards }
    val totalExpense = filtered
        .filter { it.type == TransactionType.EXPENSE }
        .sumOf { it.amount }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "統計", style = MaterialTheme.typography.titleLarge)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                Text(text = "上一期")
            }
            OutlinedButton(onClick = { yearMode = !yearMode }) {
                Text(text = if (yearMode) "切換月" else "切換年")
            }
            OutlinedButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                Text(text = "下一期")
            }
        }

        Surface(tonalElevation = 2.dp) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = if (yearMode) "${currentMonth.year} 年" else "${currentMonth.monthValue} 月", fontWeight = FontWeight.Bold)
                Text(text = "總收入：$${totalIncome.toInt()}")
                Text(text = "總支出：$${totalExpense.toInt()}")
                Text(text = "結餘：$${(totalIncome - totalExpense).toInt()}")
            }
        }

        CategoryBreakdown(
            transactions = filtered,
            expenseCategories = expenseCategories,
            incomeCategories = incomeCategories
        )
    }
}

@Composable
private fun CategoryBreakdown(
    transactions: List<com.krendstudio.cloudledger.model.Transaction>,
    expenseCategories: List<String>,
    incomeCategories: List<String>
) {
    val expenseTotals = expenseCategories.associateWith { category ->
        transactions.filter { it.type == TransactionType.EXPENSE && it.category == category }
            .sumOf { it.amount }
    }.filterValues { it > 0 }

    val incomeTotals = incomeCategories.associateWith { category ->
        transactions.filter { it.type == TransactionType.INCOME && it.category == category }
            .sumOf { it.amount }
    }.filterValues { it > 0 }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "支出分類", fontWeight = FontWeight.Bold)
        if (expenseTotals.isEmpty()) {
            Text(text = "沒有支出資料")
        } else {
            expenseTotals.forEach { (category, amount) ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = category)
                    Text(text = "$${amount.toInt()}")
                }
            }
        }

        Text(text = "收入分類", fontWeight = FontWeight.Bold)
        if (incomeTotals.isEmpty()) {
            Text(text = "沒有收入資料")
        } else {
            incomeTotals.forEach { (category, amount) ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = category)
                    Text(text = "$${amount.toInt()}")
                }
            }
        }
    }
}
