package com.krendstudio.cloudledger.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.krendstudio.cloudledger.model.LedgerMember
import com.krendstudio.cloudledger.model.Transaction
import com.krendstudio.cloudledger.model.TransactionType
import com.krendstudio.cloudledger.util.DateUtils
import com.krendstudio.cloudledger.util.formatNumber
import com.krendstudio.cloudledger.viewmodel.AppViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private enum class TimeRange { MONTH, YEAR }

@Composable
fun StatsScreen(viewModel: AppViewModel) {
    val transactions by viewModel.transactions.collectAsState()
    val expenseCategories by viewModel.expenseCategories.collectAsState()
    val incomeCategories by viewModel.incomeCategories.collectAsState()
    val members by viewModel.members.collectAsState()
    val authState by viewModel.authState.collectAsState()

    var timeRange by remember { mutableStateOf(TimeRange.MONTH) }
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var viewType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var showFilter by remember { mutableStateOf(false) }

    var selectedMemberId by remember { mutableStateOf("all") }
    var filterCategory by remember { mutableStateOf("all") }
    var keyword by remember { mutableStateOf("") }

    val fallbackMember = authState.user?.let {
        LedgerMember(uid = it.uid, displayName = it.displayName, photoUrl = it.photoUrl)
    }
    val availableMembers = members.ifEmpty { listOfNotNull(fallbackMember) }
    val currentCategories = if (viewType == TransactionType.EXPENSE) expenseCategories else incomeCategories

    // 核心過濾邏輯
    val filteredBase = transactions.filter { tx ->
        val targetId = tx.targetUserUid ?: tx.creatorUid
        if (selectedMemberId != "all" && targetId != selectedMemberId) return@filter false
        if (keyword.isNotBlank() && !tx.description.contains(keyword.trim(), ignoreCase = true)) return@filter false
        if (filterCategory != "all" && tx.category != filterCategory) return@filter false
        true
    }

    val activeTransactions = filteredBase.filter { tx ->
        val date = DateUtils.parseLocalDate(tx.date) ?: return@filter false
        if (timeRange == TimeRange.MONTH) { YearMonth.from(date) == currentMonth } else { date.year == currentMonth.year }
    }

    val totalIncome = activeTransactions.sumOf { tx ->
        val income = if (tx.type == TransactionType.INCOME) tx.amount else 0.0
        income + tx.rewards
    }
    val totalExpense = activeTransactions.sumOf { if (it.type == TransactionType.EXPENSE) it.amount else 0.0 }

    val categoryStats = buildCategoryStats(activeTransactions, viewType, incomeCategories)
    val memberStats = buildMemberStats(activeTransactions, availableMembers, viewType)
    val yearlyData = if (timeRange == TimeRange.YEAR) buildYearlyData(filteredBase, currentMonth.year) else null

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. 時段與過濾卡片
        Surface(
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { currentMonth = if (timeRange == TimeRange.MONTH) currentMonth.minusMonths(1) else currentMonth.minusYears(1) }) { Icon(Icons.Default.KeyboardArrowLeft, null) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = if (timeRange == TimeRange.MONTH) "${currentMonth.year} 年" else "年度統計", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = if (timeRange == TimeRange.MONTH) "${currentMonth.monthValue} 月" else "${currentMonth.year} 年", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Row {
                        IconButton(onClick = { showFilter = !showFilter }) { Icon(Icons.Default.FilterList, null, tint = if(showFilter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
                        IconButton(onClick = { currentMonth = if (timeRange == TimeRange.MONTH) currentMonth.plusMonths(1) else currentMonth.plusYears(1) }) { Icon(Icons.Default.KeyboardArrowRight, null) }
                    }
                }

                if (showFilter) {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 4.dp)) {
                        // 時段切換與重置
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Row(modifier = Modifier.padding(4.dp)) {
                                    TabButtonCompact(text = "月報表", selected = timeRange == TimeRange.MONTH) { timeRange = TimeRange.MONTH }
                                    TabButtonCompact(text = "年趨勢", selected = timeRange == TimeRange.YEAR) { timeRange = TimeRange.YEAR }
                                }
                            }
                            TextButton(
                                onClick = { 
                                    selectedMemberId = "all"
                                    filterCategory = "all"
                                    keyword = ""
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("重置條件", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        // 搜尋框 (優化高度控制)
                        BasicTextField(
                            value = keyword,
                            onValueChange = { keyword = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { innerTextField ->
                                Surface(
                                    shape = RoundedCornerShape(24.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Box(Modifier.weight(1f)) {
                                            if (keyword.isEmpty()) {
                                                Text(
                                                    "搜尋備註關鍵字...",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                }
                            }
                        )

                        // 成員選擇
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("成員", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChipWithIcon(label = "全部", selected = selectedMemberId == "all") { selectedMemberId = "all" }
                                availableMembers.forEach { m ->
                                    FilterChipWithIcon(
                                        label = m.displayName ?: "未知", 
                                        selected = selectedMemberId == m.uid,
                                        photoUrl = m.photoUrl
                                    ) { selectedMemberId = m.uid }
                                }
                            }
                        }

                        // 分類選擇
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if(viewType == TransactionType.EXPENSE) "支出分類" else "收入分類", 
                                    style = MaterialTheme.typography.labelMedium, 
                                    fontWeight = FontWeight.Bold, 
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text("(依下方分析模式連動)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 10.sp)
                            }
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChipWithIcon(label = "全部", selected = filterCategory == "all") { filterCategory = "all" }
                                currentCategories.forEach { cat ->
                                    FilterChipWithIcon(label = cat, selected = filterCategory == cat) { filterCategory = cat }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. 總計摘要
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("總收入 (含回饋)", totalIncome, Color(0xFF10B981), modifier = Modifier.weight(1f))
            StatCard("總支出", totalExpense, Color(0xFFE11D48), modifier = Modifier.weight(1f))
            StatCard("本月結餘", totalIncome - totalExpense, Color(0xFF3B82F6), modifier = Modifier.weight(1f))
        }

        // 3. 趨勢圖表
        if (timeRange == TimeRange.YEAR && yearlyData != null) {
            Text("年度收支趨勢", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Surface(shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surface) {
                YearlyTrendChart(yearlyData)
            }
        }

        // 4. 類型切換 Tabs
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().height(44.dp)) {
            Row(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                TabButton(text = "支出分析", selected = viewType == TransactionType.EXPENSE, modifier = Modifier.weight(1f)) { viewType = TransactionType.EXPENSE }
                TabButton(text = "收入與回饋", selected = viewType == TransactionType.INCOME, modifier = Modifier.weight(1f)) { viewType = TransactionType.INCOME }
            }
        }

        // 5. 分類佔比卡片
        StatsCategoryCard(viewType, categoryStats)

        // 6. 成員排行卡片
        if (availableMembers.size > 1) {
            StatsMemberCard(viewType, memberStats)
        }
        
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FilterChipWithIcon(label: String, selected: Boolean, photoUrl: String? = null, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (photoUrl != null) {
                AsyncImage(model = photoUrl, contentDescription = null, modifier = Modifier.size(18.dp).clip(CircleShape))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun TabButtonCompact(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatCard(label: String, amount: Double, color: Color, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, color.copy(alpha = 0.1f)), color = color.copy(alpha = 0.05f)) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 10.sp, color = color.copy(alpha = 0.8f))
            Text("$${formatNumber(amount)}", color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun TabButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        modifier = modifier.clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(text, style = MaterialTheme.typography.labelMedium, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Composable
private fun StatsCategoryCard(type: TransactionType, stats: List<CategoryStat>) {
    Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.History, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(8.dp))
                Text("${if(type == TransactionType.EXPENSE) "支出" else "收入"}類別佔比", fontWeight = FontWeight.Bold)
            }
            if (stats.isEmpty()) {
                Text("尚無資料", color = Color.Gray, modifier = Modifier.padding(vertical = 20.dp))
            } else {
                val total = stats.sumOf { it.amount }
                stats.forEachIndexed { index, stat ->
                    val color = getStatColor(index)
                    val percent = if (total > 0) (stat.amount / total * 100).toInt() else 0
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stat.category, fontSize = 13.sp)
                            Text("$percent% ($${formatNumber(stat.amount)})", fontSize = 13.sp, color = Color.Gray)
                        }
                        LinearProgressIndicator(
                            progress = { if(total > 0) (stat.amount / total).toFloat() else 0f },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                            color = color,
                            trackColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsMemberCard(type: TransactionType, stats: List<MemberStat>) {
    Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ShowChart, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("成員${if(type == TransactionType.EXPENSE) "支出" else "收入"}排行", fontWeight = FontWeight.Bold)
            }
            val total = stats.sumOf { it.amount }
            stats.forEachIndexed { index, stat ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}", color = Color.LightGray, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                    Box(modifier = Modifier.size(36.dp)) {
                        if (!stat.photoUrl.isNullOrBlank()) {
                            AsyncImage(model = stat.photoUrl, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape))
                        } else {
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Person, null, modifier = Modifier.size(20.dp), tint = Color.Gray)
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(stat.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Column(horizontalAlignment = Alignment.End) {
                        Text("$${formatNumber(stat.amount)}", fontWeight = FontWeight.Bold)
                        val percent = if (total > 0) (stat.amount / total * 100).toInt() else 0
                        Text("$percent%", fontSize = 11.sp, color = Color.LightGray)
                    }
                }
            }
        }
    }
}

@Composable
private fun YearlyTrendChart(data: List<MonthData>) {
    val maxVal = data.maxOf { maxOf(it.income, it.expense) }.coerceAtLeast(1.0)
    Row(
        modifier = Modifier.fillMaxWidth().height(160.dp).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        data.forEach { month ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.fillMaxHeight().weight(1f), contentAlignment = Alignment.BottomCenter) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Box(Modifier.fillMaxHeight((month.income / maxVal).toFloat()).width(4.dp).background(Color(0xFF10B981), CircleShape))
                        Box(Modifier.fillMaxHeight((month.expense / maxVal).toFloat()).width(4.dp).background(Color(0xFFE11D48), CircleShape))
                    }
                }
                Text(text = "${month.month}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun getStatColor(index: Int): Color {
    val colors = listOf(Color(0xFF3B82F6), Color(0xFFF97316), Color(0xFF10B981), Color(0xFFEC4899), Color(0xFF8B5CF6))
    return colors[index % colors.size]
}

data class CategoryStat(val category: String, val amount: Double)
data class MemberStat(val name: String, val amount: Double, val photoUrl: String?)
data class MonthData(val month: Int, val income: Double, val expense: Double)

private fun buildCategoryStats(transactions: List<Transaction>, viewType: TransactionType, incomeCategories: List<String>): List<CategoryStat> {
    return if (viewType == TransactionType.EXPENSE) {
        transactions.filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.category }
            .map { (cat, list) ->
                CategoryStat(cat, list.sumOf { it.amount })
            }
            .sortedByDescending { it.amount }
    } else {
        val stats = incomeCategories.map { cat ->
            val amount = transactions.filter { it.type == TransactionType.INCOME && it.category == cat }
                .sumOf { it.amount }
            CategoryStat(cat, amount)
        }.toMutableList()
        val totalRewards = transactions.sumOf { it.rewards }
        if (totalRewards > 0) {
            stats.add(CategoryStat("點券折抵", totalRewards))
        }
        stats.filter { it.amount > 0 }.sortedByDescending { it.amount }
    }
}

private fun buildMemberStats(transactions: List<Transaction>, members: List<LedgerMember>, viewType: TransactionType): List<MemberStat> {
    val source = if (viewType == TransactionType.EXPENSE) {
        transactions.filter { it.type == TransactionType.EXPENSE }
    } else {
        transactions
    }
    return source.groupBy { it.targetUserUid ?: it.creatorUid }
        .map { (uid, list) ->
            val member = members.find { it.uid == uid }
            val sum = if (viewType == TransactionType.INCOME) {
                val income = list.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
                val rewards = list.sumOf { it.rewards }
                income + rewards
            } else {
                list.sumOf { it.amount }
            }
            MemberStat(member?.displayName ?: "未知", sum, member?.photoUrl)
        }.sortedByDescending { it.amount }
}

private fun buildYearlyData(transactions: List<Transaction>, year: Int): List<MonthData> {
    return (1..12).map { month ->
        val monthTxs = transactions.filter { 
            val d = DateUtils.parseLocalDate(it.date)
            d != null && d.year == year && d.monthValue == month
        }
        MonthData(
            month = month,
            income = monthTxs.sumOf { tx ->
                val income = if (tx.type == TransactionType.INCOME) tx.amount else 0.0
                income + tx.rewards
            },
            expense = monthTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        )
    }
}
