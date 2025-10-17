// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.firebase.auth.FirebaseAuth
import stevedaydream.scheduler.data.model.Reservation
import stevedaydream.scheduler.data.model.ShiftType
import stevedaydream.scheduler.data.model.User
import stevedaydream.scheduler.presentation.common.LoadingIndicator
import stevedaydream.scheduler.util.DateUtils
import stevedaydream.scheduler.util.toComposeColor
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftReservationScreen(
    viewModel: ShiftReservationViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showShiftSelector by remember { mutableStateOf(false) }
    var selectedDay by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${DateUtils.getDisplayMonth(uiState.month)} 預約班表") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.saveReservation() },
                        enabled = !uiState.isSaving
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("儲存預約")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            LoadingIndicator(modifier = Modifier.padding(padding))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // 班別圖例
                ShiftLegend(shiftTypes = uiState.shiftTypes)
                Divider()
                // 排班表格
                ReservationTable(
                    month = uiState.month,
                    users = uiState.users,
                    shiftTypes = uiState.shiftTypes,
                    allReservations = uiState.allReservations,
                    myReservation = uiState.myReservation, // ✅ 傳入最新的 myReservation
                    myUserId = viewModel.currentUserId,
                    onCellClick = { day ->
                        selectedDay = day
                        showShiftSelector = true
                    }
                )
            }
        }
    }

    // 班別選擇 Dialog
    if (showShiftSelector && selectedDay != null) {
        val offShift = uiState.shiftTypes.find { it.shortCode == "OFF" }
        val selectableShifts = uiState.shiftTypes.filter { it.shortCode != "OFF" }

        AlertDialog(
            onDismissRequest = { showShiftSelector = false },
            title = { Text("預約 ${uiState.month}-${selectedDay}") },
            text = {
                Column {
                    // 優先顯示請假選項
                    offShift?.let {
                        ShiftSelectorItem(shift = it, isSelected = uiState.myReservation?.dailyShifts?.get(selectedDay!!) == it.id) {
                            viewModel.onCellClicked(selectedDay!!, it.id)
                            showShiftSelector = false
                        }
                    }
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    // 其他班別
                    selectableShifts.forEach { shift ->
                        ShiftSelectorItem(shift = shift, isSelected = uiState.myReservation?.dailyShifts?.get(selectedDay!!) == shift.id) {
                            viewModel.onCellClicked(selectedDay!!, shift.id)
                            showShiftSelector = false
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showShiftSelector = false }) { Text("取消") } }
        )
    }

    // 即時衝突提醒 Dialog
    uiState.instantConflict?.let { conflict ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissInstantConflict() },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("預約提醒") },
            text = { Text(conflict.message) },
            confirmButton = { Button(onClick = { viewModel.dismissInstantConflict() }) { Text("我知道了") } }
        )
    }

    // 儲存後總結 Dialog
    uiState.saveSummary?.let { summary ->
        ReservationSummaryDialog(summary = summary, onDismiss = { viewModel.dismissSummaryDialog() })
    }
}

@Composable
fun ReservationTable(
    month: String,
    users: List<User>,
    shiftTypes: List<ShiftType>,
    allReservations: List<Reservation>,
    myReservation: Reservation?, // ✅ 接收 myReservation
    myUserId: String?,
    onCellClick: (String) -> Unit
) {
    val dates = DateUtils.getDatesInMonth(month)
    val scrollState = rememberScrollState()
    val shiftTypeMap = shiftTypes.associateBy { it.id }

    // ✅ 建立一個可變的 Map，並優先使用 myReservation 的資料
    val reservationMap = remember(allReservations, myReservation) {
        val map = allReservations.associateBy { it.userId }.toMutableMap()
        myReservation?.let {
            map[it.userId] = it
        }
        map
    }


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
                    color = if (isWeekend) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(day, style = MaterialTheme.typography.labelMedium)
                        Text(dayOfWeek, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // 表身 - 使用者預約狀態
        users.forEach { user ->
            Row(modifier = Modifier.height(56.dp)) {
                // 姓名
                Surface(
                    modifier = Modifier
                        .width(100.dp)
                        .fillMaxHeight(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
                        Text(user.name, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    }
                }

                // 班別
                dates.forEach { date ->
                    val day = date.split("-").last()
                    val shiftId = reservationMap[user.id]?.dailyShifts?.get(day)
                    val shift = shiftTypeMap[shiftId]
                    val isMyRow = user.id == myUserId

                    Surface(
                        modifier = Modifier
                            .width(60.dp)
                            .fillMaxHeight()
                            .clickable(enabled = isMyRow) { onCellClick(day) },
                        color = shift?.color?.let {
                            it.toComposeColor().copy(alpha = 0.3f)
                        } ?: Color.Transparent,
                        border = BorderStroke(
                            1.dp,
                            // 如果是登入者的格子，給予更明顯的框線
                            if (isMyRow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = shift?.shortCode ?: "-",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isMyRow) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShiftSelectorItem(shift: ShiftType, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, shift.color.toComposeColor())
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 顯示顏色的方塊與班別代號
            Surface(
                modifier = Modifier.size(40.dp),
                color = shift.color.toComposeColor(),
                shape = MaterialTheme.shapes.small
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = shift.shortCode,
                        color = Color.White
                    )
                }
            }
            // 顯示班別名稱與時間
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

@Composable
private fun ReservationSummaryDialog(summary: ReservationSaveSummary, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Info, contentDescription = null) },
        title = { Text("預約完成，請注意以下衝突") },
        text = {
            LazyColumn {
                if (summary.manpowerViolations.isNotEmpty()) {
                    item { Text("人力配置衝突:", fontWeight = FontWeight.Bold) }
                    items(summary.manpowerViolations) { Text(" - $it") }
                    item { Spacer(Modifier.height(8.dp)) }
                }
                if (summary.ruleViolations.isNotEmpty()) {
                    item { Text("排班規則衝突:", fontWeight = FontWeight.Bold) }
                    items(summary.ruleViolations) { Text(" - $it") }
                    item { Spacer(Modifier.height(8.dp)) }
                }
                if (summary.usersToCoordinate.isNotEmpty()) {
                    item { Text("需協調成員:", fontWeight = FontWeight.Bold) }
                    item { Text(summary.usersToCoordinate.joinToString(", ")) }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("確認") } }
    )
}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲