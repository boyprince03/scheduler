package stevedaydream.scheduler.presentation.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import stevedaydream.scheduler.data.model.ShiftType
import stevedaydream.scheduler.data.model.User
import stevedaydream.scheduler.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualScheduleScreen(
    viewModel: ManualScheduleViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onSaveSuccess: () -> Unit
) {
    val users by viewModel.users.collectAsState()
    val shiftTypes by viewModel.shiftTypes.collectAsState()
    val assignments by viewModel.assignments.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState() // ✅ 取得 isLoading 狀態

    var showShiftSelector by remember { mutableStateOf(false) }
    var selectedUser by remember { mutableStateOf<User?>(null) }
    var selectedDay by remember { mutableStateOf<String?>(null) }


    LaunchedEffect(Unit) {
        viewModel.saveSuccess.collect {
            onSaveSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("手動排班 - ${DateUtils.getDisplayMonth(viewModel.currentMonth)}") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.saveSchedule() },
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("儲存")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // 班別圖例
                ShiftLegend(shiftTypes = shiftTypes)

                Divider()

                // 排班表格
                ScheduleTable(
                    month = viewModel.currentMonth,
                    users = users,
                    shiftTypes = shiftTypes,
                    assignments = assignments,
                    onCellClick = { user, day ->
                        selectedUser = user
                        selectedDay = day
                        showShiftSelector = true
                    }
                )
            }
        }
    }

    // 班別選擇對話框
    if (showShiftSelector && selectedUser != null && selectedDay != null) {
        ShiftSelectorDialog(
            shiftTypes = shiftTypes,
            currentShiftId = assignments[selectedUser!!.id]?.get(selectedDay!!),
            onDismiss = { showShiftSelector = false },
            onSelect = { shiftId ->
                viewModel.updateAssignment(selectedUser!!.id, selectedDay!!, shiftId)
                showShiftSelector = false
            }
        )
    }
}

@Composable
fun ShiftLegend(shiftTypes: List<ShiftType>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        item {
            Text(
                text = "班別說明",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        items(shiftTypes) { shift ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier.size(32.dp),
                    color = Color(android.graphics.Color.parseColor(shift.color)),
                    shape = MaterialTheme.shapes.small
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = shift.shortCode,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Text(
                    text = "${shift.name} (${shift.startTime} - ${shift.endTime})",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun ScheduleTable(
    month: String,
    users: List<User>,
    shiftTypes: List<ShiftType>,
    assignments: Map<String, Map<String, String>>,
    onCellClick: (User, String) -> Unit
) {
    val dates = DateUtils.getDatesInMonth(month)
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(scrollState)
    ) {
        // 表頭 - 日期
        Row(modifier = Modifier.height(48.dp)) {
            // 姓名欄
            Surface(
                modifier = Modifier
                    .width(100.dp)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("姓名", style = MaterialTheme.typography.labelSmall)
                }
            }

            // 日期欄
            dates.forEach { date ->
                val day = date.split("-").last()
                val dayOfWeek = DateUtils.getDayOfWeekText(date)
                val isWeekend = DateUtils.isWeekend(date)

                Surface(
                    modifier = Modifier
                        .width(60.dp)
                        .fillMaxHeight(),
                    color = if (isWeekend)
                        MaterialTheme.colorScheme.errorContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = day,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = dayOfWeek,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 表身 - 使用者排班
        users.forEach { user ->
            Row(modifier = Modifier.height(56.dp)) {
                // 姓名
                Surface(
                    modifier = Modifier
                        .width(100.dp)
                        .fillMaxHeight(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Text(
                            text = user.name,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // 班別
                dates.forEach { date ->
                    val day = date.split("-").last()
                    val shiftId = assignments[user.id]?.get(day)
                    val shift = shiftTypes.find { it.id == shiftId }

                    Surface(
                        modifier = Modifier
                            .width(60.dp)
                            .fillMaxHeight()
                            .clickable { onCellClick(user, day) },
                        color = shift?.color?.let {
                            Color(android.graphics.Color.parseColor(it)).copy(alpha = 0.3f)
                        } ?: Color.Transparent,
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
            }
        }
    }
}

@Composable
fun ShiftSelectorDialog(
    shiftTypes: List<ShiftType>,
    currentShiftId: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("選擇班別") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                shiftTypes.forEach { shift ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(shift.id) },
                        color = if (shift.id == currentShiftId)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(
                            1.dp,
                            Color(android.graphics.Color.parseColor(shift.color))
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                color = Color(android.graphics.Color.parseColor(shift.color)),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = shift.shortCode,
                                        color = Color.White
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = shift.name,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "${shift.startTime} - ${shift.endTime}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}