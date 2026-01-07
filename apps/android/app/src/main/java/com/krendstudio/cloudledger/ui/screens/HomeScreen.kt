package com.krendstudio.cloudledger.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.krendstudio.cloudledger.R
import com.krendstudio.cloudledger.viewmodel.AppViewModel
import com.krendstudio.cloudledger.viewmodel.LedgerUiState
import kotlinx.coroutines.launch
import java.time.LocalDate

private data class HomeTab(val label: String, val icon: ImageVector)

@Composable
fun HomeScreen(viewModel: AppViewModel, ledgerState: LedgerUiState) {
    val authState by viewModel.authState.collectAsState()
    val daySheetOpen by viewModel.daySheetOpen.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val scope = rememberCoroutineScope()
    val tabs = listOf(
        HomeTab("首頁", Icons.Outlined.Home),
        HomeTab("紀錄", Icons.Outlined.List),
        HomeTab("記帳", Icons.Outlined.AddCircleOutline),
        HomeTab("統計", Icons.Outlined.PieChart),
        HomeTab("設定", Icons.Outlined.Settings)
    )
    var selectedIndex by remember { mutableStateOf(0) }
    var pendingSmartVoice by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background) // 改為主題背景
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface, // 跟隨主題
                tonalElevation = 0.dp
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    val showName = maxWidth >= 480.dp
                    val logoSize = if (maxWidth >= 480.dp) 38.dp else 34.dp
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(R.drawable.logo),
                                contentDescription = "Logo",
                                modifier = Modifier
                                    .size(logoSize)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "CloudLedger",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = {
                                    if (syncing) return@OutlinedButton
                                    syncing = true
                                    scope.launch {
                                        viewModel.syncCurrentLedger(forceFull = true)
                                        syncing = false
                                    }
                                },
                                modifier = Modifier.height(36.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(text = if (syncing) "同步中..." else "立即同步", style = MaterialTheme.typography.labelMedium)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(
                                modifier = Modifier.clickable { selectedIndex = 4 },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val photoUrl = authState.user?.photoUrl
                                if (!photoUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = photoUrl,
                                        contentDescription = "User",
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = authState.user?.displayName?.take(1) ?: "訪",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                                if (showName) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = authState.user?.displayName ?: "訪客",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 48.dp)
                ) {
                    when (selectedIndex) {
                        0 -> DashboardScreen(viewModel)
                        1 -> TransactionsScreen(viewModel)
                        2 -> AddTransactionScreen(
                            viewModel = viewModel,
                            initialMode = if (pendingSmartVoice) "smart" else null,
                            startVoice = pendingSmartVoice,
                            onVoiceHandled = { pendingSmartVoice = false }
                        ) { selectedIndex = 0 }
                        3 -> StatsScreen(viewModel)
                        else -> SettingsScreen(viewModel)
                    }
                }
            }
        }

        // Bottom Navigation Bar
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(64.dp)
                .zIndex(2f),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f), // 跟隨主題
            tonalElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavItem(
                    label = "首頁",
                    icon = tabs[0].icon,
                    selected = selectedIndex == 0,
                    onClick = { selectedIndex = 0 },
                    modifier = Modifier.weight(1f)
                )
                BottomNavItem(
                    label = "紀錄",
                    icon = tabs[1].icon,
                    selected = selectedIndex == 1,
                    onClick = { selectedIndex = 1 },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.weight(1f))

                BottomNavItem(
                    label = "統計",
                    icon = tabs[3].icon,
                    selected = selectedIndex == 3,
                    onClick = { selectedIndex = 3 },
                    modifier = Modifier.weight(1f)
                )
                BottomNavItem(
                    label = "設定",
                    icon = tabs[4].icon,
                    selected = selectedIndex == 4,
                    onClick = { selectedIndex = 4 },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        AddButton(
            selected = selectedIndex == 2,
            onClick = {
                if (daySheetOpen) {
                    viewModel.setSelectedDate(selectedDate ?: LocalDate.now())
                    viewModel.setDaySheetOpen(false)
                }
                selectedIndex = 2
            },
            onLongClick = {
                if (daySheetOpen) {
                    viewModel.setSelectedDate(selectedDate ?: LocalDate.now())
                    viewModel.setDaySheetOpen(false)
                }
                pendingSmartVoice = true
                selectedIndex = 2
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-16).dp)
                .zIndex(3f)
        )
    }
}

@Composable
private fun BottomNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon, 
            contentDescription = label, 
            tint = color,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label, 
            style = MaterialTheme.typography.labelSmall, 
            color = color,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 11.sp
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AddButton(
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary, // 跟隨主題
            shadowElevation = 8.dp,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.AddCircleOutline,
                    contentDescription = "新增",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "記帳",
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 11.sp
        )
    }
}
