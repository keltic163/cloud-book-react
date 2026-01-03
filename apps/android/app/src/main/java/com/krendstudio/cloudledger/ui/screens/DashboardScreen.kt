package com.krendstudio.cloudledger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.krendstudio.cloudledger.model.TransactionType
import com.krendstudio.cloudledger.util.DateUtils
import com.krendstudio.cloudledger.viewmodel.AppViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun DashboardScreen(viewModel: AppViewModel) {
    val transactions by viewModel.transactions.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val announcement by viewModel.announcement.collectAsState()
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }

    LaunchedEffect(Unit) {
        if (selectedDate == null) {
            viewModel.setSelectedDate(LocalDate.now())
        }
    }

    val monthTransactions = transactions.filter { transaction ->
        val date = DateUtils.parseLocalDate(transaction.date)
        date != null && YearMonth.from(date) == currentMonth
    }

    val monthlyIncome = monthTransactions
        .filter { it.type == TransactionType.INCOME }
        .sumOf { it.amount + it.rewards }

    val monthlyExpense = monthTransactions
        .filter { it.type == TransactionType.EXPENSE }
        .sumOf { it.amount }

    val dailyTotals = monthTransactions.groupBy { DateUtils.parseLocalDate(it.date) }
        .mapValues { entry ->
            val items = entry.value
            val income = items.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
            val expense = items.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            income to expense
        }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        announcement?.let { banner ->
            val now = System.currentTimeMillis()
            if (banner.isEnabled && now in banner.startAt..banner.endAt) {
                val background = when (banner.type) {
                    "warning" -> Color(0xFFFFE3C0)
                    "error" -> Color(0xFFFFD6D6)
                    else -> Color(0xFFDDE9FF)
                }
                val textColor = when (banner.type) {
                    "warning" -> Color(0xFF8A4B00)
                    "error" -> Color(0xFF8A0000)
                    else -> Color(0xFF1E3A8A)
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(background, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(text = banner.text, color = textColor)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                Text(text = "上一月")
            }
            Text(
                text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.TAIWAN)} ${currentMonth.year}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Button(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                Text(text = "下一月")
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "本月總覽", style = MaterialTheme.typography.titleMedium)
                Text(text = "收入：$${monthlyIncome.toInt()}")
                Text(text = "支出：$${monthlyExpense.toInt()}")
                Text(text = "結餘：$${(monthlyIncome - monthlyExpense).toInt()}")
            }
        }

        CalendarGrid(
            yearMonth = currentMonth,
            selectedDate = selectedDate,
            dailyTotals = dailyTotals,
            onSelect = { viewModel.setSelectedDate(it) }
        )

        selectedDate?.let { date ->
            val dayTransactions = transactions.filter {
                DateUtils.parseLocalDate(it.date) == date
            }
            Text(text = "${date.monthValue}月${date.dayOfMonth}日交易", fontWeight = FontWeight.Bold)
            if (dayTransactions.isEmpty()) {
                Text(text = "當日沒有交易")
            } else {
                dayTransactions.forEach { tx ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = tx.description)
                        val sign = if (tx.type == TransactionType.EXPENSE) "-" else "+"
                        Text(text = "$sign${tx.amount.toInt()}")
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarGrid(
    yearMonth: YearMonth,
    selectedDate: LocalDate?,
    dailyTotals: Map<LocalDate?, Pair<Double, Double>>,
    onSelect: (LocalDate) -> Unit
) {
    val daysInMonth = yearMonth.lengthOfMonth()
    val firstDay = yearMonth.atDay(1).dayOfWeek.value % 7
    val totalCells = firstDay + daysInMonth
    val rows = (totalCells + 6) / 7

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach { label ->
                Text(text = label, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }

        var day = 1
        repeat(rows) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                repeat(7) { column ->
                    val index = it * 7 + column
                    if (index < firstDay || day > daysInMonth) {
                        Spacer(modifier = Modifier.weight(1f).height(40.dp))
                    } else {
                        val date = yearMonth.atDay(day)
                        val totals = dailyTotals[date]
                        val isSelected = date == selectedDate
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(2.dp)
                                .background(
                                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onSelect(date) }
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = day.toString(), fontWeight = FontWeight.Bold)
                            totals?.let { (income, expense) ->
                                if (income > 0) {
                                    Text(text = "+${income.toInt()}", style = MaterialTheme.typography.labelSmall)
                                }
                                if (expense > 0) {
                                    Text(text = "-${expense.toInt()}", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        day += 1
                    }
                }
            }
        }
    }
}
