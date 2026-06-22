package com.krendstudio.cloudledger.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalConfiguration
import androidx.activity.compose.BackHandler
import coil.compose.AsyncImage
import com.krendstudio.cloudledger.R
import com.krendstudio.cloudledger.model.LedgerMember
import com.krendstudio.cloudledger.model.Transaction
import com.krendstudio.cloudledger.model.TransactionType
import com.krendstudio.cloudledger.ui.components.DropdownField
import com.krendstudio.cloudledger.ui.components.AdBanner
import com.krendstudio.cloudledger.util.DateUtils
import com.krendstudio.cloudledger.util.formatNumber
import com.krendstudio.cloudledger.util.formatPlainNumber
import com.krendstudio.cloudledger.viewmodel.AppViewModel
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: AppViewModel) {
    val transactions by viewModel.transactions.collectAsState()
    val expenseCategories by viewModel.expenseCategories.collectAsState()
    val incomeCategories by viewModel.incomeCategories.collectAsState()
    val members by viewModel.members.collectAsState()
    val authState by viewModel.authState.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val announcement by viewModel.announcement.collectAsState()
    val isAdFree by viewModel.isAdFree.collectAsState()
    
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var calendarMonth by remember { mutableStateOf(currentMonth) }
    var showMonthPicker by remember { mutableStateOf(false) }
    val calendarOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    BackHandler(enabled = showMonthPicker) {
        showMonthPicker = false
    }

    val fallbackMember = authState.user?.let {
        LedgerMember(uid = it.uid, displayName = it.displayName, photoUrl = it.photoUrl)
    }
    val availableMembers = members.ifEmpty { listOfNotNull(fallbackMember) }
    val membersById = remember(availableMembers) { availableMembers.associateBy { it.uid } }

    val monthTransactions = transactions.filter { transaction ->
        val date = DateUtils.parseLocalDate(transaction.date)
        date != null && YearMonth.from(date) == calendarMonth
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

    val rewardStats = remember(monthTransactions, transactions) {
        val monthRewards = monthTransactions.sumOf { it.rewards }
        val totalRewards = transactions.sumOf { it.rewards }
        monthRewards to totalRewards
    }

    @Suppress("UnusedBoxWithConstraintsScope")
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxHeightPx = with(density) { maxHeight.toPx() }
        val calendarWidthPx = with(density) { maxWidth.toPx() }
        val navBarHeight = 64.dp
        val calendarThreshold = calendarWidthPx * 0.2f

        fun animateCalendarMonthChange(deltaMonths: Int) {
            scope.launch {
                val direction = if (deltaMonths < 0) 1f else -1f
                calendarOffset.animateTo(
                    targetValue = calendarWidthPx * direction,
                    animationSpec = tween(durationMillis = 360)
                )
                calendarMonth = calendarMonth.plusMonths(deltaMonths.toLong())
                currentMonth = calendarMonth
                calendarOffset.snapTo(calendarWidthPx * -direction)
                calendarOffset.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 360)
                )
            }
        }

        fun animateCalendarToMonth(target: YearMonth) {
            val delta = target.monthValue - calendarMonth.monthValue + (target.year - calendarMonth.year) * 12
            if (delta != 0) {
                animateCalendarMonthChange(delta)
            } else {
                calendarMonth = target
                currentMonth = target
            }
        }

        fun handleAssetMonthChange(target: YearMonth) {
            currentMonth = target
            animateCalendarToMonth(target)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            announcement?.let { banner ->
                if (banner.isEnabled && System.currentTimeMillis() in (banner.startAt ?: 0L)..(banner.endAt ?: Long.MAX_VALUE)) {
                    AnnouncementBannerView(banner.text ?: "", banner.type ?: "info")
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(calendarOffset.value.roundToInt(), 0) }
                        .pointerInput(currentMonth, calendarWidthPx) {
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { _, dragAmount ->
                                    scope.launch {
                                        calendarOffset.snapTo(calendarOffset.value + dragAmount)
                                    }
                                },
                                onDragEnd = {
                                    val currentOffset = calendarOffset.value
                                    when {
                                        currentOffset > calendarThreshold -> animateCalendarMonthChange(-1)
                                        currentOffset < -calendarThreshold -> animateCalendarMonthChange(1)
                                        else -> scope.launch {
                                            calendarOffset.animateTo(
                                                targetValue = 0f,
                                                animationSpec = tween(durationMillis = 260)
                                            )
                                        }
                                    }
                                },
                                onDragCancel = {
                                    scope.launch {
                                        calendarOffset.animateTo(
                                            targetValue = 0f,
                                            animationSpec = tween(durationMillis = 260)
                                        )
                                    }
                                }
                            )
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                    CalendarHeader(
                        currentMonth = calendarMonth,
                        onMonthChange = { target -> animateCalendarToMonth(target) },
                        onOpenPicker = { showMonthPicker = true },
                        onPrevClick = { animateCalendarMonthChange(-1) },
                        onNextClick = { animateCalendarMonthChange(1) }
                    )
                    CalendarWeekdayLabels()
                    CalendarGrid(
                        yearMonth = calendarMonth,
                        selectedDate = selectedDate,
                        dailyTotals = dailyTotals,
                        onSelect = { day ->
                                viewModel.setSelectedDate(day)
                                viewModel.setDaySheetOpen(true)
                            }
                        )
                    }
                }
            }

            AssetChangeCard(
                currentMonth = currentMonth,
                inc = monthlyIncome,
                exp = monthlyExpense,
                onMonthChange = { target -> handleAssetMonthChange(target) }
            )
            if (!isAdFree) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    AdBanner(
                        adUnitId = stringResource(id = R.string.admob_banner_id),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            PointRewardCard(rewardStats.first, rewardStats.second)
            Spacer(modifier = Modifier.height(4.dp))
        }

    }

    if (showMonthPicker) {
        MonthPickerDialog(
            currentMonth = calendarMonth,
            onDismiss = { showMonthPicker = false },
            onConfirm = { selected ->
                calendarMonth = selected
                currentMonth = selected
                showMonthPicker = false
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
                        AsyncImage(model = it.photoUrl, contentDescription = null, modifier = Modifier.size(20.dp).clip(CircleShape))
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
private fun PointRewardCard(month: Double, total: Double) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val bgColor = if (isDark) Color(0xFF1C1917) else Color(0xFFFFF7ED)
    val borderColor = if (isDark) Color(0xFF451A03) else Color(0xFFFDE68A)
    val accentColor = if (isDark) Color(0xFFF59E0B) else Color(0xFFB45309)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(if (isDark) Color(0xFF78350F) else Color(0xFFFDE68A)), contentAlignment = Alignment.Center) {
                    Text(text = "✦", color = accentColor, fontSize = 14.sp)
                }
                Spacer(Modifier.width(10.dp))
                Text(text = "點券折抵", color = accentColor, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Text(text = "$${formatNumber(month)}", color = accentColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(text = "歷史累計: $${formatNumber(total)}", color = accentColor.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun androidx.compose.ui.graphics.Color.luminance(): Float {
    return (0.299f * red + 0.587f * green + 0.114f * blue)
}

@Composable
private fun AssetChangeCard(currentMonth: YearMonth, inc: Double, exp: Double, onMonthChange: (YearMonth) -> Unit) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val brush = if (isDark) {
        Brush.linearGradient(colors = listOf(Color(0xFF1F2937), Color(0xFF0F172A)))
    } else {
        Brush.linearGradient(colors = listOf(Color(0xFFEEF2FF), Color(0xFFE0E7FF)))
    }
    val textColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val assetOffset = remember { Animatable(0f) }

    Surface(shape = RoundedCornerShape(8.dp), color = Color.Transparent, shadowElevation = 4.dp) {
        @Suppress("UnusedBoxWithConstraintsScope")
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush, shape = RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 20.dp)
        ) {
            val assetWidthPx = with(density) { maxWidth.toPx() }

            fun animateMonthChange(deltaMonths: Int) {
                scope.launch {
                    val direction = if (deltaMonths < 0) 1f else -1f
                    assetOffset.animateTo(
                        targetValue = assetWidthPx * direction,
                        animationSpec = tween(durationMillis = 360)
                    )
                    onMonthChange(currentMonth.plusMonths(deltaMonths.toLong()))
                    assetOffset.snapTo(assetWidthPx * -direction)
                    assetOffset.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 360)
                    )
                }
            }

            IconButton(
                onClick = { animateMonthChange(-1) },
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text("<", color = if (isDark) Color(0xFF94A3B8) else MaterialTheme.colorScheme.primary)
            }
            IconButton(
                onClick = { animateMonthChange(1) },
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Text(">", color = if (isDark) Color(0xFF94A3B8) else MaterialTheme.colorScheme.primary)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .align(Alignment.TopCenter)
            ) {
                Text(
                    "${currentMonth.monthValue}月資產變化",
                    textAlign = TextAlign.Center,
                    color = if (isDark) Color(0xFF94A3B8) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp, end = 28.dp, top = 36.dp)
                    .offset { IntOffset(assetOffset.value.roundToInt(), 0) },
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("${if (inc - exp >= 0) "+" else ""}${formatNumber(inc - exp)}", color = if (inc - exp >= 0) Color(0xFF10B981) else Color(0xFFE11D48), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), Arrangement.SpaceBetween) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF10B981))); Spacer(Modifier.width(6.dp)); Text("收入", color = if (isDark) Color(0xFF94A3B8) else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall) }
                        Text("$${formatNumber(inc)}", color = textColor, fontWeight = FontWeight.SemiBold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFE11D48))); Spacer(Modifier.width(6.dp)); Text("支出", color = if (isDark) Color(0xFF94A3B8) else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall) }
                        Text("$${formatNumber(exp)}", color = textColor, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarHeader(
    currentMonth: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    onOpenPicker: () -> Unit,
    onPrevClick: () -> Unit,
    onNextClick: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text("收支日曆", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .clickable { onOpenPicker() }
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            IconButton(onClick = onPrevClick, Modifier.size(32.dp)) { Text("<", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("${currentMonth.year} ${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.TAIWAN)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            IconButton(onClick = onNextClick, Modifier.size(32.dp)) { Text(">", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun MonthPickerDialog(
    currentMonth: YearMonth,
    onDismiss: () -> Unit,
    onConfirm: (YearMonth) -> Unit
) {
    val yearRange = remember(currentMonth) { (currentMonth.year - 5..currentMonth.year + 5).toList() }
    val monthRange = remember { (1..12).toList() }
    var yearIndex by remember(currentMonth) { mutableStateOf(yearRange.indexOf(currentMonth.year).coerceAtLeast(0)) }
    var monthIndex by remember(currentMonth) { mutableStateOf((currentMonth.monthValue - 1).coerceIn(0, 11)) }
    val yearListState = rememberLazyListState(initialFirstVisibleItemIndex = yearIndex.coerceAtLeast(0))
    val monthListState = rememberLazyListState(initialFirstVisibleItemIndex = monthIndex.coerceAtLeast(0))
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
            shape = AlertDialogDefaults.shape
        ),
        title = { Text("選擇年月") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("年份", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        WheelPicker(
                            options = yearRange.map { it.toString() },
                            selectedIndex = yearIndex,
                            listState = yearListState,
                            onSelectedIndexChange = { yearIndex = it }
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("月份", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        WheelPicker(
                            options = monthRange.map { it.toString().padStart(2, '0') },
                            selectedIndex = monthIndex,
                            listState = monthListState,
                            onSelectedIndexChange = { monthIndex = it }
                        )
                    }
                }
                TextButton(
                    onClick = {
                        yearIndex = yearRange.indexOf(currentMonth.year).coerceAtLeast(0)
                        monthIndex = (currentMonth.monthValue - 1).coerceIn(0, 11)
                        scope.launch {
                            yearListState.animateScrollToItem(yearIndex)
                            monthListState.animateScrollToItem(monthIndex)
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("回到本月")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val year = yearRange.getOrNull(yearIndex) ?: currentMonth.year
                val month = monthRange.getOrNull(monthIndex) ?: currentMonth.monthValue
                onConfirm(YearMonth.of(year, month.coerceIn(1, 12)))
            }) { Text("確定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun WheelPicker(
    options: List<String>,
    selectedIndex: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSelectedIndexChange: (Int) -> Unit
) {
    val itemHeight = 36.dp
    val visibleItems = 5
    val pickerHeight = itemHeight * visibleItems
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collectLatest { (index, offset) ->
                val next = if (offset > itemHeightPx * 0.5f) index + 1 else index
                onSelectedIndexChange(next.coerceIn(0, options.lastIndex))
            }
    }

    Box(modifier = Modifier.height(pickerHeight).width(90.dp)) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = itemHeight * 2),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(options) { index, value ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = value,
                        style = if (isSelected) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodySmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(itemHeight)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.15f))
        )
    }
}

@Composable
private fun CalendarWeekdayLabels() {
    Row(modifier = Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
        listOf("日", "一", "二", "三", "四", "五", "六").forEach { label ->
            Text(text = label, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CalendarGrid(yearMonth: YearMonth, selectedDate: LocalDate?, dailyTotals: Map<LocalDate?, Pair<Double, Double>>, onSelect: (LocalDate) -> Unit) {
    val daysInMonth = yearMonth.lengthOfMonth()
    val firstDay = yearMonth.atDay(1).dayOfWeek.value % 7
    val rows = (firstDay + daysInMonth + 6) / 7
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        var day = 1
        repeat(rows) { r ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { c ->
                    val index = r * 7 + c
                    if (index < firstDay || day > daysInMonth) {
                        Spacer(Modifier.weight(1f).height(48.dp))
                    } else {
                        val date = yearMonth.atDay(day)
                        val isToday = date == LocalDate.now()
                        val isSelected = date == selectedDate
                        val totals = dailyTotals[date]
                        Column(
                            modifier = Modifier.weight(1f).height(48.dp).padding(1.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else if (isToday) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { onSelect(date) }.padding(vertical = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = day.toString(), style = MaterialTheme.typography.labelSmall, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontSize = 10.sp)
                            totals?.let { (inc, exp) ->
                                if (inc > 0) Text(text = "+${formatCompact(inc)}", color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else Color(0xFF10B981), fontSize = 8.sp, lineHeight = 8.sp)
                                if (exp > 0) Text(text = "-${formatCompact(exp)}", color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.error, fontSize = 8.sp, lineHeight = 8.sp)
                            }
                        }
                        day++
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactInputField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, trailingIcon: ImageVector? = null) {
    Column(modifier) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        BasicTextField(
            value = value, onValueChange = onValueChange, singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface), cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).heightIn(min = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { innerTextField() }
                        if (trailingIcon != null) Icon(trailingIcon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
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
    var amountText by remember { mutableStateOf(formatPlainNumber(transaction.amount)) }
    var description by remember { mutableStateOf(transaction.description) }
    var rewardsText by remember { mutableStateOf(formatPlainNumber(transaction.rewards)) }
    var type by remember { mutableStateOf(transaction.type) }
    var category by remember { mutableStateOf(transaction.category) }
    var date by remember { mutableStateOf(DateUtils.parseLocalDate(transaction.date) ?: LocalDate.now()) }
    var targetUserUid by remember { mutableStateOf(transaction.targetUserUid) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val categories = if (type == TransactionType.EXPENSE) expenseCategories else incomeCategories

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)).clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(0.92f).clickable(enabled = false) { }
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState())
                ) {
                    Text("編輯紀錄", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CompactInputField("金額", amountText, { amountText = it }, Modifier.weight(1f), Icons.Default.UnfoldMore)
                        CompactInputField("點數", rewardsText, { rewardsText = it }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DropdownField("類型", listOf("支出", "收入"), if (type == TransactionType.EXPENSE) "支出" else "收入", { type = if (it == "支出") TransactionType.EXPENSE else TransactionType.INCOME }, Modifier.weight(1f))
                        DropdownField("分類", categories, category, { category = it }, Modifier.weight(1f))
                    }
                    CompactInputField("描述", description, { description = it }, Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                        CompactInputField("日期", date.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")), { DateUtils.parseLocalDate(it)?.let { d -> date = d } }, Modifier.weight(1f), Icons.Default.CalendarToday)
                        DropdownField("成員", members.map { it.displayName }, members.find { it.uid == targetUserUid }?.displayName ?: "", { label -> targetUserUid = members.find { it.displayName == label }?.uid }, Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onDismiss, modifier = Modifier.weight(1f).height(42.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) { Text("取消") }
                        Button(onClick = { onSave(mapOf("amount" to (amountText.toDoubleOrNull() ?: 0.0), "type" to type.name, "category" to category, "description" to description, "rewards" to (rewardsText.toDoubleOrNull() ?: 0.0), "date" to date.format(DateTimeFormatter.ISO_DATE), "targetUserUid" to targetUserUid)) }, modifier = Modifier.weight(1f).height(42.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("儲存", color = MaterialTheme.colorScheme.onPrimary) }
                    }
                    Text("刪除紀錄", color = MaterialTheme.colorScheme.error, modifier = Modifier.clickable { showDeleteConfirm = true }.padding(top = 2.dp))
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("確認刪除") },
            text = { Text("確定要刪除這筆紀錄嗎？此動作無法復原。") },
            confirmButton = { TextButton(onClick = { showDeleteConfirm = false; onDelete() }) { Text("刪除") } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } }
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun AnnouncementBannerView(text: String, type: String) {
    val bg = when (type) {
        "warning" -> Color(0xFFFFEDD5)
        "error" -> Color(0xFFFEE2E2)
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val txt = when (type) {
        "warning" -> Color(0xFF9A3412)
        "error" -> Color(0xFF991B1B)
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(vertical = 6.dp, horizontal = 16.dp)
    ) {
        Text(
            text = text,
            color = txt,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .fillMaxWidth()
                .basicMarquee(
                    animationMode = MarqueeAnimationMode.Immediately,
                    spacing = MarqueeSpacing(24.dp)
                )
        )
    }
}

private fun formatCompact(v: Double): String {
    val a = kotlin.math.abs(v)
    return if (a >= 1000) String.format(Locale.US, "%.1fk", a / 1000.0).replace(".0k", "k") else a.toInt().toString()
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
