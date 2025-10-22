// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
// scheduler/presentation/schedule/ScheduleScreen.kt
package stevedaydream.scheduler.presentation.schedule

// ... (其他 imports 保持不變) ...
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
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
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import stevedaydream.scheduler.domain.scheduling.ScheduleGenerator
// ✅ 1. 移除舊的 SchedulingStrategy Enum 定義
// enum class SchedulingStrategy(val displayName: String) { ... }
// ✅ 2. 引入 ScheduleGenerator 中的 SchedulingStrategyType
import stevedaydream.scheduler.domain.scheduling.SchedulingStrategyType

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
    // --- States (大部分保持不變) ---
    val group by viewModel.group.collectAsState()
    val canSchedule by viewModel.canSchedule.collectAsState()
    val isScheduler by viewModel.isScheduler.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val schedules by viewModel.schedules.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    // ✅ 3. selectedStrategy 現在直接使用 ViewModel 中的 SchedulingStrategyType
    val selectedStrategy by viewModel.selectedStrategy.collectAsState()
    val isCalculatingRotation by viewModel.isCalculatingRotation.collectAsState()
    val isPreviewGenerating by viewModel.isPreviewGenerating.collectAsState()
    val previewGeneratedSchedule by viewModel.previewGeneratedSchedule.collectAsState()

    var showMonthPicker by remember { mutableStateOf(false) }
    var selectedMonth by remember { mutableStateOf(DateUtils.getCurrentMonthString()) }
    var scheduleToDelete by remember { mutableStateOf<Schedule?>(null) }
    val context = LocalContext.current

    // --- LaunchedEffects (保持不變) ---
    LaunchedEffect(Unit) {
        viewModel.generateSuccess.collect {
            context.showToast("智慧排班已生成並儲存")
        }
    }
    LaunchedEffect(Unit) {
        viewModel.rotationCalculationResult.collect { result ->
            result.onSuccess {
                context.showToast("輪替預排班計算並儲存成功")
            }.onFailure { error ->
                context.showToast("輪替計算失敗: ${error.message}")
            }
        }
    }
    LaunchedEffect(Unit) {
        viewModel.previewError.collect { errorMessage ->
            context.showToast(errorMessage)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.groupName ?: "排班") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (isScheduler) {
                        IconButton(onClick = { viewModel.releaseScheduler() }) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "釋放排班權")
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
            group?.let { currentGroup ->
                SchedulerStatusCard(
                    group = currentGroup,
                    canSchedule = canSchedule,
                    isScheduler = isScheduler,
                    onClaimClick = { viewModel.claimScheduler() }
                )

                // ... (showJoinButton 邏輯不變) ...
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


                if (currentGroup.reservationStatus == "active" || currentGroup.reservationStatus == "closed") {
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

                if (isScheduler && currentGroup.reservationStatus != "active") {
                    SchedulerFunctionCard(
                        isGenerating = isGenerating,
                        isPreviewGenerating = isPreviewGenerating,
                        selectedMonth = selectedMonth,
                        // ✅ 4. 傳遞 SchedulingStrategyType
                        selectedStrategy = selectedStrategy,
                        onStrategyChange = { viewModel.selectStrategy(it) }, // ViewModel 已更新
                        onShowMonthPicker = { showMonthPicker = true },
                        onNavigateToShiftTypeSettings = { onNavigateToShiftTypeSettings(viewModel.currentOrgId, viewModel.currentGroupId) },
                        onNavigateToRules = { onNavigateToRules(viewModel.currentOrgId, viewModel.currentGroupId) },
                        onNavigateToManpower = { onNavigateToManpower(viewModel.currentOrgId, viewModel.currentGroupId, selectedMonth) },
                        onNavigateToManualSchedule = { onNavigateToManualSchedule(viewModel.currentOrgId, viewModel.currentGroupId, selectedMonth) },
                        onGenerate = { viewModel.generateSmartSchedule(selectedMonth) },
                        onGeneratePreview = { viewModel.generatePreviewSchedule(selectedMonth) }
                    )
                }

                // ... (啟動預約按鈕邏輯不變) ...
                if (isScheduler && currentGroup.reservationStatus == "inactive") {
                    Button(
                        onClick = { viewModel.toggleReservation(selectedMonth, "inactive") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isCalculatingRotation
                    ) {
                        if (isCalculatingRotation) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("計算輪替中...")
                        } else {
                            Icon(Icons.Default.EventAvailable, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("啟動 ${DateUtils.getDisplayMonth(selectedMonth)} 預約")
                        }
                    }
                }
            } // end group?.let

            ScheduleListSection(
                schedules = schedules,
                onScheduleClick = { schedule ->
                    onNavigateToScheduleDetail(viewModel.currentOrgId, viewModel.currentGroupId, schedule.id)
                },
                onDeleteClick = { schedule ->
                    scheduleToDelete = schedule
                }
            )
        } // end Column
    } // end Scaffold

    // --- ✅ 7. Preview Dialog (保持不變) ---
    if (previewGeneratedSchedule != null) {
        PreviewScheduleDialog(
            result = previewGeneratedSchedule!!,
            onDismiss = { viewModel.clearPreview() },
            onSave = { viewModel.savePreviewSchedule() }
        )
    }

    // --- Other Dialogs (MonthPicker, Delete Confirmation - 保持不變) ---
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
    scheduleToDelete?.let { schedule ->
        stevedaydream.scheduler.presentation.common.ConfirmDialog(
            title = "確認刪除",
            message = "您確定要刪除 ${DateUtils.getDisplayMonth(schedule.month)} 的班表草稿嗎？此操作無法復原。",
            onConfirm = {
                viewModel.deleteSchedule(schedule.id)
                scheduleToDelete = null
                context.showToast("班表已刪除")
            },
            onDismiss = {
                scheduleToDelete = null
            }
        )
    }
} // end ScheduleScreen Composable

// ... (ScheduleListSection, ScheduleCard, MonthPickerDialog, SchedulerStatusCard, ReservationStatusCard 保持不變) ...
@Composable
fun ScheduleListSection(  schedules: List<Schedule>,
                          onScheduleClick: (Schedule) -> Unit,
                          onDeleteClick: (Schedule) -> Unit ) { /* ... 內容不變 ... */
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
@Composable
fun ScheduleCard(
    schedule: Schedule,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) { /* ... 內容不變 ... */
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
                    if (schedule.totalScore != 0) {
                        Text(
                            text = "分數: ${schedule.totalScore}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (schedule.totalScore >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "ID: ${schedule.id.take(8).uppercase()} · ${if (schedule.generationMethod == "manual") "手動" else "智慧"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
@Composable
fun MonthPickerDialog(
    currentMonth: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) { /* ... 內容不變 ... */
    var selectedMonth by remember { mutableStateOf(currentMonth) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("選擇月份") },
        text = {
            Column {
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
                            containerColor = if (month == selectedMonth) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        )
                    ) {
                        Text(stevedaydream.scheduler.util.DateUtils.getDisplayMonth(month))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selectedMonth) }) { Text("確認") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
@Composable
fun SchedulerStatusCard(
    group: stevedaydream.scheduler.data.model.Group,
    canSchedule: Boolean,
    isScheduler: Boolean,
    onClaimClick: () -> Unit
) {
    var remainingTime by remember { mutableStateOf("") }
    if (isScheduler) {
        LaunchedEffect(key1 = group.schedulerLeaseExpiresAt) {
            val expiresAt = group.schedulerLeaseExpiresAt?.time ?: 0L
            while (System.currentTimeMillis() < expiresAt) {
                val remainingMillis = expiresAt - System.currentTimeMillis()
                if (remainingMillis <= 0) break
                val minutes = remainingMillis / 60000
                val seconds = (remainingMillis % 60000) / 1000
                remainingTime = String.format("剩下 %02d:%02d", minutes, seconds)
                kotlinx.coroutines.delay(1000)
            }
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
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "排班者狀態", style = MaterialTheme.typography.titleSmall)
            when {
                isScheduler -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("你正在排班中", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        }
                        if (remainingTime.isNotEmpty()) {
                            Text(
                                text = remainingTime,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                modifier = Modifier.padding(start = 32.dp)
                            )
                        }
                    }
                }
                group.isSchedulerActive() -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Text("${group.schedulerName} 正在排班")
                    }
                }
                canSchedule -> {
                    Button(onClick = onClaimClick, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Schedule, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("認領排班權")
                    }
                }
            }
        }
    }
}
@Composable
fun ReservationStatusCard(group: stevedaydream.scheduler.data.model.Group,
                          isScheduler: Boolean,
                          onToggleReservation: () -> Unit,
                          onNavigateToReservation: () -> Unit ) { /* ... 內容不變 ... */
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "${DateUtils.getDisplayMonth(group.reservationMonth ?: "")} 班表預約", style = MaterialTheme.typography.titleMedium)
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            if (group.reservationStatus == "active") {
                Button(onClick = onNavigateToReservation, modifier = Modifier.fillMaxWidth()) { Text("前往預約") }
            }
            if (isScheduler) {
                OutlinedButton(onClick = onToggleReservation, modifier = Modifier.fillMaxWidth()) { Text(buttonText) }
            }
        }
    }
}


// --- ✅ 修改 SchedulerFunctionCard ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SchedulerFunctionCard(
    isGenerating: Boolean,
    isPreviewGenerating: Boolean,
    selectedMonth: String,
    // ✅ 參數類型改為 SchedulingStrategyType
    selectedStrategy: SchedulingStrategyType,
    onStrategyChange: (SchedulingStrategyType) -> Unit,
    onShowMonthPicker: () -> Unit,
    onNavigateToShiftTypeSettings: () -> Unit,
    onNavigateToRules: () -> Unit,
    onNavigateToManpower: () -> Unit,
    onNavigateToManualSchedule: () -> Unit,
    onGenerate: () -> Unit,
    onGeneratePreview: () -> Unit
) {
    var strategyDropdownExpanded by remember { mutableStateOf(false) }
    // ✅ 使用 SchedulingStrategyType.values()
    val strategies = SchedulingStrategyType.values()
    val isAnyProcessRunning = isGenerating || isPreviewGenerating

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "排班功能", style = MaterialTheme.typography.titleMedium)

            // --- 排班策略選擇 ---
            ExposedDropdownMenuBox(
                expanded = strategyDropdownExpanded,
                onExpandedChange = { strategyDropdownExpanded = !strategyDropdownExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    // ✅ 根據 SchedulingStrategyType 顯示名稱
                    value = when(selectedStrategy) {
                        SchedulingStrategyType.GENERAL -> "通用設定"
                        SchedulingStrategyType.HOSPITAL_GREEDY -> "醫院設定 (貪婪法)"
                        SchedulingStrategyType.HOSPITAL_BACKTRACKING -> "醫院設定 (回溯法)"
                    },
                    onValueChange = {}, readOnly = true,
                    label = { Text("排班策略") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = strategyDropdownExpanded) },
                    modifier = Modifier.menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = strategyDropdownExpanded,
                    onDismissRequest = { strategyDropdownExpanded = false }
                ) {
                    strategies.forEach { strategy ->
                        DropdownMenuItem(
                            // ✅ 根據 SchedulingStrategyType 顯示名稱
                            text = { Text( when(strategy) {
                                SchedulingStrategyType.GENERAL -> "通用設定"
                                SchedulingStrategyType.HOSPITAL_GREEDY -> "醫院設定 (貪婪法)"
                                SchedulingStrategyType.HOSPITAL_BACKTRACKING -> "醫院設定 (回溯法)"
                            } ) },
                            onClick = {
                                onStrategyChange(strategy)
                                strategyDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // --- 功能按鈕 (保持不變) ---
            OutlinedButton(onClick = onShowMonthPicker, modifier = Modifier.fillMaxWidth()) { /* ... */
                Icon(Icons.Default.CalendarMonth, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("選擇月份: ${DateUtils.getDisplayMonth(selectedMonth)}")
            }
            OutlinedButton(onClick = onNavigateToShiftTypeSettings, modifier = Modifier.fillMaxWidth()) { /* ... */
                Icon(Icons.Default.Palette, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("班別設定")
            }
            OutlinedButton(onClick = onNavigateToRules, modifier = Modifier.fillMaxWidth()) { /* ... */
                Icon(Icons.Default.Rule, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("規則設定")
            }
            OutlinedButton(onClick = onNavigateToManpower, modifier = Modifier.fillMaxWidth()) { /* ... */
                Icon(Icons.Default.People, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("人力規劃儀表板")
            }
            OutlinedButton(onClick = onNavigateToManualSchedule, modifier = Modifier.fillMaxWidth()) { /* ... */
                Icon(Icons.Default.Edit, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("手動排班")
            }

            // --- 預覽按鈕 (保持不變) ---
            OutlinedButton(onClick = onGeneratePreview, modifier = Modifier.fillMaxWidth(), enabled = !isAnyProcessRunning) { /* ... */
                if (isPreviewGenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("預覽生成中...")
                } else {
                    Icon(Icons.Default.Visibility, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("預覽輪替與預排班")
                }
            }

            // --- 生成並儲存按鈕 (保持不變) ---
            Button(onClick = onGenerate, modifier = Modifier.fillMaxWidth(), enabled = !isAnyProcessRunning) { /* ... */
                if (isGenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("生成儲存中...")
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("生成並儲存班表")
                }
            }
        } // end Column
    } // end Card
} // end SchedulerFunctionCard

// --- Preview Dialog (保持不變) ---
@Composable
fun PreviewScheduleDialog( result: ScheduleGenerator.ScheduleGenerationResult,
                           onDismiss: () -> Unit,
                           onSave: () -> Unit) { /* ... 內容不變 ... */
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("預排班預覽 (${DateUtils.getDisplayMonth(result.schedule.month)})") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("班表分數: ${result.score}", style = MaterialTheme.typography.titleMedium) }
                if (result.warnings.isNotEmpty()) {
                    item { Text("警告 (${result.warnings.size}):", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary) }
                    items(result.warnings) { Text(" - $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
                }
                if (result.violations.isNotEmpty()) {
                    item { Text("規則違反 (${result.violations.size}):", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) }
                    items(result.violations) { Text(" - $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                }
                if (result.warnings.isEmpty() && result.violations.isEmpty()) {
                    item { Text("班表看起來不錯！沒有明顯的警告或規則違反。") }
                }
            }
        },
        confirmButton = { Button(onClick = onSave) { Text("儲存此班表") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("關閉預覽") } }
    )
}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲
