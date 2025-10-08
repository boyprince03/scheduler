package stevedaydream.scheduler.presentation.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import stevedaydream.scheduler.data.model.Schedule // ✅ 引入正確的 Schedule model

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onNavigateToRules: (String, String) -> Unit,
    onNavigateToManualSchedule: (String, String, String) -> Unit,
    onNavigateToShiftTypeSettings: (String, String) -> Unit,
    onNavigateToScheduleDetail: (String, String, String) -> Unit

) {
    val group by viewModel.group.collectAsState()
    val canSchedule by viewModel.canSchedule.collectAsState()
    val isScheduler by viewModel.isScheduler.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState() // ✅ 新增
    // ✅ 新增這一行來收集排班表列表
    val schedules by viewModel.schedules.collectAsState()

    var showMonthPicker by remember { mutableStateOf(false) } // ✅ 新增
    var selectedMonth by remember { mutableStateOf(stevedaydream.scheduler.util.DateUtils.getCurrentMonthString()) } // ✅ 新增




    // ✅ 監聽生成成功事件
    LaunchedEffect(Unit) {
        viewModel.generateSuccess.collect {
            // 可以顯示成功訊息或導航到排班檢視頁面
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.groupName ?: "排班") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (isScheduler) {
                        IconButton(onClick = { viewModel.releaseScheduler() }) {
                            Icon(Icons.Default.ExitToApp, contentDescription = "釋放排班權")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 排班者狀態卡片
            group?.let { currentGroup ->
                SchedulerStatusCard(
                    group = currentGroup,
                    canSchedule = canSchedule,
                    isScheduler = isScheduler,
                    onClaimClick = { viewModel.claimScheduler() }
                )
            }

            // 功能區域 - ✅ 修改這一段
            if (isScheduler) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "排班功能",
                            style = MaterialTheme.typography.titleMedium
                        )

                        // 月份選擇
                        OutlinedButton(
                            onClick = { showMonthPicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("選擇月份: ${stevedaydream.scheduler.util.DateUtils.getDisplayMonth(selectedMonth)}")
                        }

                        // 智慧排班
                        Button(
                            onClick = { viewModel.generateSmartSchedule(selectedMonth) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isGenerating
                        ) {
                            if (isGenerating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("生成中...")
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("開始智慧排班")
                            }
                        }

                        // 手動排班
                        OutlinedButton(
                            onClick = {
                                onNavigateToManualSchedule(viewModel.currentOrgId, viewModel.currentGroupId, selectedMonth)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("手動排班")
                        }
                        OutlinedButton(
                            onClick = { onNavigateToShiftTypeSettings(viewModel.currentOrgId, viewModel.currentGroupId) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Palette, contentDescription = null) // 使用一個不同的圖示
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("班別設定")
                        }
                        // ✅ 新增規則設定按鈕
                        OutlinedButton(
                            onClick = { onNavigateToRules(viewModel.currentOrgId, viewModel.currentGroupId) }, // 傳入 groupId
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Rule, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("規則設定")
                        }
                    }
                }
            }

            // ✅ 現在 'schedules' 變數已定義，可以正確傳遞
            ScheduleListSection(
                schedules = schedules,
                onScheduleClick = { schedule ->
                    // ✅ 呼叫導航函式
                    onNavigateToScheduleDetail(viewModel.currentOrgId, viewModel.currentGroupId, schedule.id)
                }
            )
        }
    }
    // ✅ 月份選擇對話框
    if (showMonthPicker) {
        MonthPickerDialog(
            currentMonth = selectedMonth,
            onDismiss = { showMonthPicker = false },
            onConfirm = { month ->
                selectedMonth = month
                showMonthPicker = false
            }
        )
    }
}
@Composable
fun ScheduleListSection(
    schedules: List<Schedule>,
    onScheduleClick: (Schedule) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            // 🔽🔽🔽 修改這裡 🔽🔽🔽
            // 移除 Column 的 verticalArrangement，因為 LazyColumn 會自行處理
        ) {
            // ... Row 標題部分維持不變
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "排班表",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${schedules.size} 個",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 檢查列表是否為空
            if (schedules.isEmpty()) {
                // ... 空白狀態維持不變
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "尚無排班表",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // 🔽🔽🔽 修改這裡：將 forEach 換成 LazyColumn 🔽🔽🔽
                // 增加一個分隔線
                Divider(modifier = Modifier.padding(vertical = 8.dp))

                LazyColumn(
                    // 關鍵！設定最大高度，讓內容超出時可以捲動
                    modifier = Modifier.heightIn(max = 400.dp),
                    // 為列表項目之間增加間距
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(schedules, key = { it.id }) { schedule ->
                        ScheduleCard(
                            schedule = schedule,
                            onClick = { onScheduleClick(schedule) }
                        )
                    }
                }
                // 🔼🔼🔼 到此為止 🔼🔼🔼
            }
        }
    }
}

// ✅ 新增排班表卡片組件
@Composable
fun ScheduleCard(
    schedule: Schedule,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stevedaydream.scheduler.util.DateUtils.getDisplayMonth(schedule.month),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 狀態標籤
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = when (schedule.status) {
                            "published" -> MaterialTheme.colorScheme.primaryContainer
                            "draft" -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            text = when (schedule.status) {
                                "published" -> "已發布"
                                "draft" -> "草稿"
                                else -> "未知"
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = when (schedule.status) {
                                "published" -> MaterialTheme.colorScheme.onPrimaryContainer
                                "draft" -> MaterialTheme.colorScheme.onSecondaryContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    // 分數
                    if (schedule.totalScore != 0) {
                        Text(
                            text = "分數: ${schedule.totalScore}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (schedule.totalScore >= 0)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error
                        )
                    }
                }

                // 違反規則
                if (schedule.violatedRules.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "${schedule.violatedRules.size} 個規則違反",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // 生成時間
                Text(
                    text = "生成於 ${stevedaydream.scheduler.util.DateUtils.timestampToDateString(schedule.generatedAt.time)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// 🔼🔼🔼 到此為止 🔼🔼🔼
// ✅ 新增月份選擇對話框
@Composable
fun MonthPickerDialog(
    currentMonth: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var selectedMonth by remember { mutableStateOf(currentMonth) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("選擇月份") },
        text = {
            Column {
                // 簡化版:顯示未來6個月
                repeat(6) { index ->
                    val month = stevedaydream.scheduler.util.DateUtils.addMonths(
                        stevedaydream.scheduler.util.DateUtils.getCurrentMonthString(),
                        index
                    )
                    OutlinedButton(
                        onClick = { selectedMonth = month },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (month == selectedMonth)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                Color.Transparent
                        )
                    ) {
                        Text(stevedaydream.scheduler.util.DateUtils.getDisplayMonth(month))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedMonth) }) {
                Text("確認")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

// 🔼🔼🔼 到此為止 🔼🔼🔼

@Composable
fun SchedulerStatusCard(
    group: stevedaydream.scheduler.data.model.Group,
    canSchedule: Boolean,
    isScheduler: Boolean,
    onClaimClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isScheduler -> MaterialTheme.colorScheme.primaryContainer
                group.isSchedulerActive() -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "排班者狀態",
                style = MaterialTheme.typography.titleSmall
            )

            when {
                isScheduler -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text("你正在排班中")
                    }
                }
                group.isSchedulerActive() -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Text("${group.schedulerName} 正在排班")
                    }
                }
                canSchedule -> {
                    Button(
                        onClick = onClaimClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("認領排班權")
                    }
                }
            }
        }
    }
}