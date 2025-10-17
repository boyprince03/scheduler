// scheduler/presentation/schedule/ScheduleScreen.kt
package stevedaydream.scheduler.presentation.schedule

import android.R.attr.text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import stevedaydream.scheduler.data.model.Schedule
import stevedaydream.scheduler.util.DateUtils
import stevedaydream.scheduler.util.showToast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onNavigateToRules: (String, String) -> Unit,
    onNavigateToManualSchedule: (String, String, String) -> Unit,
    onNavigateToShiftTypeSettings: (String, String) -> Unit,
    onNavigateToScheduleDetail: (String, String, String) -> Unit,
    onNavigateToManpower: (String, String, String) -> Unit,
    onNavigateToReservation: (String, String, String) -> Unit
) {
    val group by viewModel.group.collectAsState()
    val canSchedule by viewModel.canSchedule.collectAsState()
    val isScheduler by viewModel.isScheduler.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val schedules by viewModel.schedules.collectAsState()
    // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
    val currentUser by viewModel.currentUser.collectAsState()
    // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲

    var showMonthPicker by remember { mutableStateOf(false) }
    var selectedMonth by remember { mutableStateOf(DateUtils.getCurrentMonthString()) }
    var scheduleToDelete by remember { mutableStateOf<Schedule?>(null) }
    val context = LocalContext.current

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
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (isScheduler) {
                        IconButton(onClick = { viewModel.releaseScheduler() }) {
                            Icon(Icons.Filled.ExitToApp, contentDescription = "釋放排班權")

                        }
                    }
                }
            )
        }
    ) { padding ->
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(scrollState),
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

                // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
                // 檢查是否符合顯示「加入群組」按鈕的條件
                val showJoinButton = isScheduler &&
                        currentUser != null &&
                        (currentUser?.role == "org_admin" || currentUser?.role == "superuser") &&
                        !currentGroup.memberIds.contains(currentUser!!.id)

                if (showJoinButton) {
                    OutlinedButton(
                        onClick = { viewModel.addSchedulerToGroup() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.GroupAdd, contentDescription = "加入群組")
                        Spacer(Modifier.width(8.dp))
                        Text("加入此群組以參與排班")
                    }
                }
                // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲

                // 只有當預約功能關閉時，才顯示排班功能
                if (currentGroup.reservationStatus == "inactive") {
                    if (isScheduler) {
                        SchedulerFunctionCard(
                            isGenerating = isGenerating,
                            selectedMonth = selectedMonth,
                            onShowMonthPicker = { showMonthPicker = true },
                            onNavigateToShiftTypeSettings = { onNavigateToShiftTypeSettings(viewModel.currentOrgId, viewModel.currentGroupId) },
                            onNavigateToRules = { onNavigateToRules(viewModel.currentOrgId, viewModel.currentGroupId) },
                            onNavigateToManpower = { onNavigateToManpower(viewModel.currentOrgId, viewModel.currentGroupId, selectedMonth) },
                            onNavigateToManualSchedule = { onNavigateToManualSchedule(viewModel.currentOrgId, viewModel.currentGroupId, selectedMonth) },
                            onGenerate = { viewModel.generateSmartSchedule(selectedMonth) }
                        )
                    }
                } else {
                    // 顯示預約卡片給所有人
                    ReservationStatusCard(
                        group = currentGroup,
                        isScheduler = isScheduler,
                        onToggleReservation = {
                            viewModel.toggleReservation(currentGroup.reservationMonth ?: selectedMonth, currentGroup.reservationStatus)
                        },
                        onNavigateToReservation = {
                            onNavigateToReservation(viewModel.currentOrgId, viewModel.currentGroupId, currentGroup.reservationMonth!!)
                        }
                    )
                }

                if (isScheduler && currentGroup.reservationStatus == "inactive") {
                    Button(onClick = { viewModel.toggleReservation(selectedMonth, "inactive") }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.EventAvailable, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("啟動 ${DateUtils.getDisplayMonth(selectedMonth)} 預約")
                    }
                }
            }

            ScheduleListSection(
                schedules = schedules,
                onScheduleClick = { schedule ->
                    onNavigateToScheduleDetail(viewModel.currentOrgId, viewModel.currentGroupId, schedule.id)
                },
                onDeleteClick = { schedule ->
                    scheduleToDelete = schedule
                }
            )
        }
    }

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
    // ✅ 顯示刪除確認對話框
    scheduleToDelete?.let { schedule ->
        stevedaydream.scheduler.presentation.common.ConfirmDialog(
            title = "確認刪除",
            message = "您確定要刪除 ${DateUtils.getDisplayMonth(schedule.month)} 的班表草稿嗎？此操作無法復原。",
            onConfirm = {
                viewModel.deleteSchedule(schedule.id)
                scheduleToDelete = null // 關閉對話框
                context.showToast("班表已刪除")
            },
            onDismiss = {
                scheduleToDelete = null // 關閉對話框
            }
        )
    }
}


@Composable
fun ScheduleListSection(
    schedules: List<Schedule>,
    onScheduleClick: (Schedule) -> Unit,
    onDeleteClick: (Schedule) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
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

            if (schedules.isEmpty()) {
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
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    schedules.forEach { schedule ->
                        ScheduleCard(
                            schedule = schedule,
                            onClick = { onScheduleClick(schedule) },
                            onDeleteClick = { onDeleteClick(schedule) }
                        )
                    }
                }
            }
        }
    }
}

// 排班表卡片組件
@Composable
fun ScheduleCard(
    schedule: Schedule,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
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
                contentDescription = "查看詳情",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        Divider()
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // ✅ 顯示流水號和生成時間
            Text(
                text = "ID: ${schedule.id.take(8).uppercase()}  ·  ${DateUtils.timestampToDateString(schedule.generatedAt.time)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ✅ 僅在草稿狀態下顯示刪除按鈕
            if (schedule.status == "draft") {
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "刪除草稿",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}


// 月份選擇對話框
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

@Composable
fun SchedulerStatusCard(
    group: stevedaydream.scheduler.data.model.Group,
    canSchedule: Boolean,
    isScheduler: Boolean,
    onClaimClick: () -> Unit
) {
    // 狀態：用來存放格式化後的剩餘時間字串
    var remainingTime by remember { mutableStateOf("") }

    // 當 isScheduler 為 true 且 group 物件的到期時間改變時，啟動或重啟此計時器
    if (isScheduler) {
        LaunchedEffect(key1 = group.schedulerLeaseExpiresAt) {
            val expiresAt = group.schedulerLeaseExpiresAt?.time ?: 0L
            // 只要還沒到期，就持續更新
            while (System.currentTimeMillis() < expiresAt) {
                val remainingMillis = expiresAt - System.currentTimeMillis()
                if (remainingMillis <= 0) break // 時間到就跳出迴圈

                // 計算剩餘的分鐘和秒數
                val minutes = remainingMillis / 60000
                val seconds = (remainingMillis % 60000) / 1000

                // 格式化字串，例如："剩下 01:59"
                remainingTime = String.format("剩下 %02d:%02d", minutes, seconds)

                // 每秒更新一次
                kotlinx.coroutines.delay(1000)
            }
            // 迴圈結束後，顯示租約已到期
            remainingTime = "租約已到期"
        }
    }


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
                    // 將原本的 Row 改為 Column，以便垂直排列文字
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text("你正在排班中", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        }
                        // 顯示剩餘時間
                        if (remainingTime.isNotEmpty()) {
                            Text(
                                text = remainingTime,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                modifier = Modifier.padding(start = 32.dp) // 對齊上方圖示
                            )
                        }
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
/**
 * 預約狀態卡片
 */
@Composable
fun ReservationStatusCard(
    group: stevedaydream.scheduler.data.model.Group,
    isScheduler: Boolean,
    onToggleReservation: () -> Unit,
    onNavigateToReservation: () -> Unit
) {
    val statusText = when (group.reservationStatus) {
        "active" -> "預約進行中"
        "closed" -> "預約已關閉"
        else -> "未知狀態"
    }
    val buttonText = when (group.reservationStatus) {
        "active" -> "關閉預約"
        "closed" -> "重新開啟"
        else -> "管理預約"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${DateUtils.getDisplayMonth(group.reservationMonth ?: "")} 班表預約",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )

            if (group.reservationStatus == "active") {
                Button(onClick = onNavigateToReservation, modifier = Modifier.fillMaxWidth()) {
                    Text("前往預約")
                }
            }

            if (isScheduler) {
                OutlinedButton(onClick = onToggleReservation, modifier = Modifier.fillMaxWidth()) {
                    Text(buttonText)
                }
            }
        }
    }
}

/**
 * 將原有的排班功能區塊獨立成一個 Composable
 */
@Composable
private fun SchedulerFunctionCard(
    isGenerating: Boolean,
    selectedMonth: String,
    onShowMonthPicker: () -> Unit,
    onNavigateToShiftTypeSettings: () -> Unit,
    onNavigateToRules: () -> Unit,
    onNavigateToManpower: () -> Unit,
    onNavigateToManualSchedule: () -> Unit,
    onGenerate: () -> Unit
) {
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

            OutlinedButton(
                onClick = onShowMonthPicker,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("選擇月份: ${DateUtils.getDisplayMonth(selectedMonth)}")
            }
            OutlinedButton(
                onClick = onNavigateToShiftTypeSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Palette, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("班別設定")
            }
            OutlinedButton(
                onClick = onNavigateToRules,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Rule, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("規則設定")
            }
            OutlinedButton(
                onClick = onNavigateToManpower,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.People, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("人力規劃儀表板")
            }
            OutlinedButton(
                onClick = onNavigateToManualSchedule,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("手動排班")
            }
            Button(
                onClick = onGenerate,
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
        }
    }
}