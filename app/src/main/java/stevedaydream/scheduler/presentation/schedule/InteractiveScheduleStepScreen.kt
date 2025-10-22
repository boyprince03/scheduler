// 修改開始
// scheduler/presentation/schedule/InteractiveScheduleStepScreen.kt
package stevedaydream.scheduler.presentation.schedule

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items // 確保 items 被 import
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack // 使用 AutoMirrored
import androidx.compose.material.icons.filled.* // 保持 Filled icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import stevedaydream.scheduler.data.model.ShiftType
import stevedaydream.scheduler.data.model.User
import stevedaydream.scheduler.presentation.common.LoadingIndicator // 導入 LoadingIndicator
// ▼▼▼ 導入共用的 Composable ▼▼▼
import stevedaydream.scheduler.presentation.common.ShiftLegend
import stevedaydream.scheduler.presentation.common.ShiftSelectorDialog
// ▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲
import stevedaydream.scheduler.util.DateUtils
import stevedaydream.scheduler.util.toComposeColor
import androidx.compose.runtime.saveable.rememberSaveable
import stevedaydream.scheduler.domain.scheduling.rules.RuleViolation // Import RuleViolation

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InteractiveScheduleStepScreen(
    viewModel: InteractiveScheduleViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onComplete: () -> Unit // Callback when the whole process is complete
) {
    val uiState by viewModel.uiState.collectAsState()

    var showShiftSelector by remember { mutableStateOf(false) }
    var selectedUser by remember { mutableStateOf<User?>(null) }
    var selectedDay by remember { mutableStateOf<String?>(null) }

    // Navigate back when process is complete or error occurs without recovery
    LaunchedEffect(uiState.currentStep) {
        if (uiState.currentStep == ScheduleStep.COMPLETE || (uiState.currentStep == ScheduleStep.ERROR && uiState.errorMessage != null)) {
            // Delay slightly to allow user to see the final state/error message
            kotlinx.coroutines.delay(1500)
            if (uiState.currentStep == ScheduleStep.COMPLETE) {
                onComplete()
            } else {
                onBackClick() // Go back if there was an unrecoverable error
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.currentStep.description) },
                navigationIcon = {
                    // Only allow back navigation if not in a loading/finalizing state
                    if (uiState.currentStep !in listOf(
                            ScheduleStep.INITIALIZING,
                            ScheduleStep.FILLING_N,
                            ScheduleStep.FILLING_D,
                            ScheduleStep.FILLING_S,
                            ScheduleStep.FILLING_OFF,
                            ScheduleStep.FINALIZING,
                            ScheduleStep.COMPLETE,
                            ScheduleStep.ERROR // Allow back from error state maybe? Or handle separately
                        )
                    ) {
                        IconButton(onClick = onBackClick) { // Consider adding a confirmation dialog
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回 (放棄排班)") // Use AutoMirrored
                        }
                    } else {
                        Spacer(Modifier.width(48.dp)) // Placeholder to keep title centered
                    }
                },
                actions = {
                    // Show save button only during review steps
                    if (uiState.currentStep in listOf(
                            ScheduleStep.REVIEWING_N,
                            ScheduleStep.REVIEWING_D,
                            ScheduleStep.REVIEWING_S,
                            ScheduleStep.REVIEWING_OFF
                        )
                    ) {
                        Button(
                            onClick = { viewModel.saveStepAndProceed() },
                            enabled = !uiState.isLoading // Disable while the next step is processing
                        ) {
                            Text("儲存並繼續下一步")
                        }
                    } else if (uiState.currentStep == ScheduleStep.COMPLETE) {
                        Text("已完成", modifier = Modifier.padding(end = 16.dp))
                    } else if (uiState.currentStep == ScheduleStep.ERROR) {
                        Icon(Icons.Default.Warning, contentDescription = "錯誤", tint = MaterialTheme.colorScheme.error, modifier = Modifier.padding(end=16.dp))
                    } else {
                        // Show loading indicator in actions during processing steps
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp).padding(end = 16.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        // Handle overall loading/error states covering the whole screen
        when {
            uiState.isLoading && uiState.currentStep == ScheduleStep.INITIALIZING -> {
                LoadingIndicator(modifier = Modifier.padding(padding), message = uiState.currentStep.description)
            }
            uiState.currentStep == ScheduleStep.ERROR && uiState.errorMessage != null -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("錯誤: ${uiState.errorMessage}", color = MaterialTheme.colorScheme.error)
                }
            }
            // Show content for review steps or intermediate loading
            else -> {
                LazyColumn( // Use LazyColumn for scrollable content
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    item {
                        // ▼▼▼ 使用導入的 ShiftLegend ▼▼▼
                        ShiftLegend( // Reusable legend
                            shiftTypes = uiState.shiftTypes,
                            modifier = Modifier.padding(16.dp)
                        )
                        // ▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲
                    }
                    item { Divider() }

                    // Display the interactive table
                    item {
                        InteractiveScheduleTable(
                            month = uiState.month,
                            users = uiState.users,
                            shiftTypes = uiState.shiftTypes,
                            assignments = uiState.currentAssignments, // Show the currently reviewable assignments
                            userShiftCounts = uiState.userShiftCounts,
                            onCellClick = { user, day ->
                                selectedUser = user
                                selectedDay = day
                                showShiftSelector = true
                            }
                        )
                    }

                    // Placeholder for Rule Violations Section
                    item {
                        RuleViolationsSection(
                            hardViolations = uiState.hardRuleViolationsInStep,
                            softViolations = uiState.softRuleViolations,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    // Optional: Add a simple stats card if needed, though stats are in the table
                    item {
                        // Maybe a summary card here if desired
                    }
                }
            }
        }
    }

    // ▼▼▼ 使用導入的 ShiftSelectorDialog ▼▼▼
    if (showShiftSelector && selectedUser != null && selectedDay != null) {
        ShiftSelectorDialog( // Assuming this exists and works similarly
            shiftTypes = uiState.shiftTypes,
            // Pass the current shift from the assignments being reviewed
            currentShiftId = uiState.currentAssignments[selectedUser!!.id]?.get(selectedDay!!),
            onDismiss = { showShiftSelector = false },
            onSelect = { shiftId ->
                viewModel.updateAssignment(selectedUser!!.id, selectedDay!!, shiftId)
                showShiftSelector = false
            }
        )
    }
    // ▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InteractiveScheduleTable(
    month: String,
    users: List<User>, // 表格顯示的使用者列表
    shiftTypes: List<ShiftType>,
    assignments: Map<String, Map<String, String>>,
    userShiftCounts: Map<String, Map<String, Int>>,
    onCellClick: (User, String) -> Unit
) {
    val dates = DateUtils.getDatesInMonth(month)
    val scrollState = rememberScrollState()
    val shiftTypeMap = shiftTypes.associateBy { it.id }
    var expandedUserIds by rememberSaveable { mutableStateOf(emptySet<String>()) }

    Column(
        modifier = Modifier
            .fillMaxWidth() // Fill width within LazyColumn item
            .horizontalScroll(scrollState)
    ) {
        // --- Table Header (Date Row) ---
        Row(
            modifier = Modifier
                .height(48.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // Name Column Header
            Surface(
                // 稍微加寬以容納序號
                modifier = Modifier.width(140.dp).fillMaxHeight(), // Was 120.dp
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // 更新標頭文字
                    Text("序號 / 姓名 / 統計", style = MaterialTheme.typography.labelSmall)
                }
            }
            // Date Column Headers (保持不變)
            dates.forEach { date ->
                val day = date.split("-").last()
                val dayOfWeek = DateUtils.getDayOfWeekText(date)
                val isWeekend = DateUtils.isWeekend(date)

                Surface(
                    modifier = Modifier.width(55.dp).fillMaxHeight(),
                    color = if (isWeekend) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(day, style = MaterialTheme.typography.labelMedium)
                        Text(dayOfWeek, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // --- Table Body (User Rows) ---
        // ✅ 使用 forEachIndexed 取得索引
        users.forEachIndexed { index, user ->
            val isExpanded = user.id in expandedUserIds
            // ✅ 計算序號字元
            val serialChar = ('A' + index)

            // Main User Assignment Row
            Row(
                modifier = Modifier
                    .height(56.dp)
                    .clickable { // Click row to expand/collapse
                        expandedUserIds = if (isExpanded) expandedUserIds - user.id
                        else expandedUserIds + user.id
                    }
            ) {
                // Name Cell with Expander and Serial Number
                Surface(
                    // 稍微加寬以容納序號
                    modifier = Modifier.width(140.dp).fillMaxHeight(), // Was 120.dp
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        // ✅ 顯示序號
                        Text(
                            text = "$serialChar.",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(24.dp) // 給序號固定寬度
                        )
                        // Spacer(Modifier.width(4.dp)) // 序號和圖示間距 (可選)
                        Icon(
                            imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (isExpanded) "收合" else "展開",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp)) // 圖示和姓名間距
                        Text(
                            text = user.name,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                // Daily Shift Cells (保持不變)
                dates.forEach { date ->
                    val day = date.split("-").last()
                    val shiftId = assignments[user.id]?.get(day)
                    val shift = shiftTypeMap[shiftId]
                    Surface(
                        modifier = Modifier
                            .width(55.dp)
                            .fillMaxHeight()
                            .clickable { onCellClick(user, day) },
                        color = shift?.color?.toComposeColor()?.copy(alpha = 0.2f) ?: Color.Transparent,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = shift?.shortCode ?: "-",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            } // End User Row

            // Expanded Statistics Row (保持不變，但調整 padding)
            AnimatedVisibility(visible = isExpanded) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    FlowRow(
                        // ✅ 調整起始 padding 以對齊姓名欄
                        modifier = Modifier.padding(start = 140.dp + 8.dp, top = 8.dp, bottom = 8.dp, end = 8.dp), // Was 120.dp
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val counts = userShiftCounts[user.id]
                        if (counts.isNullOrEmpty()) {
                            Text("無統計資料", style = MaterialTheme.typography.bodySmall)
                        } else {
                            counts.entries.sortedBy { it.key }.forEach { (shiftName, count) ->
                                Text(
                                    text = "$shiftName: $count",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            } // End AnimatedVisibility
        } // End forEachIndexed user
    } // End Column
}


@Composable
fun RuleViolationsSection(
    hardViolations: List<String>, // Messages from the filling step
    softViolations: List<RuleViolation>, // Calculated for the current state
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (hardViolations.isNotEmpty()) {
            Text("填充步驟警告/衝突:", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
            hardViolations.forEach {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = "警告", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (softViolations.isNotEmpty()) {
            Text("軟性規則提醒:", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
            softViolations.forEach {
                Row(verticalAlignment = Alignment.Top) { // Use Top alignment for potentially multi-line messages
                    Icon(Icons.Default.Info, contentDescription = "提醒", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp).padding(top=2.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(it.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }

        if (hardViolations.isEmpty() && softViolations.isEmpty()) {
            Text("目前無明顯規則衝突。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

