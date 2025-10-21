// ▼▼▼▼▼▼▼▼▼▼▼▼ 完整程式碼 ▼▼▼▼▼▼▼▼▼▼▼▼
package stevedaydream.scheduler.presentation.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items // 確保 items 被 import
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
// 移除不必要的 FirebaseAuth import
// import com.google.firebase.auth.FirebaseAuth
import stevedaydream.scheduler.data.model.Reservation
import stevedaydream.scheduler.data.model.ShiftType
import stevedaydream.scheduler.data.model.User
import stevedaydream.scheduler.presentation.common.LoadingIndicator // 確保 LoadingIndicator 被 import
import stevedaydream.scheduler.util.DateUtils
import stevedaydream.scheduler.util.toComposeColor
// 移除不必要的 Inject import
// import javax.inject.Inject

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
            LoadingIndicator(modifier = Modifier.padding(padding)) // 使用 LoadingIndicator
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // ✅ 使用限定名稱呼叫正確的 ShiftLegend
                ShiftLegendInReservation(shiftTypes = uiState.shiftTypes)
                Divider()
                // 排班表格
                ReservationTable(
                    month = uiState.month,
                    users = uiState.users,
                    shiftTypes = uiState.shiftTypes,
                    allReservations = uiState.allReservations,
                    myReservation = uiState.myReservation,
                    rotationSchedule = uiState.rotationSchedule,
                    myUserId = viewModel.currentUserId,
                    onCellClick = { day ->
                        selectedDay = day
                        showShiftSelector = true
                    }
                )
            }
        }
    }

    // 班別選擇 Dialog 修改：處理 List<String>
    if (showShiftSelector && selectedDay != null) {
        val offShift = uiState.shiftTypes.find { it.shortCode == "OFF" }
        val selectableShifts = uiState.shiftTypes.filter { it.shortCode != "OFF" }
        // 檢查當天是否有預排輪班
        val rotationShiftId = uiState.rotationSchedule[viewModel.currentUserId]?.get(selectedDay!!)
        val rotationShift = rotationShiftId?.let { uiState.shiftTypes.find { s -> s.id == it } }
        // ✅ 取得當前的偏好列表
        val currentPreferences = uiState.myReservation?.dailyShifts?.get(selectedDay!!) ?: emptyList()

        AlertDialog(
            onDismissRequest = { showShiftSelector = false },
            title = { Text("預約 ${uiState.month}-${selectedDay}") },
            text = {
                Column {
                    // 如果有預排輪班，顯示提示訊息且不允許修改
                    if (rotationShift != null) {
                        Text(
                            "此日已由系統預排 ${rotationShift.name} 輪班，無法自行修改。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        // 優先顯示請假選項
                        offShift?.let {
                            ShiftSelectorItem(
                                shift = it,
                                // ✅ isSelected 判斷是否為列表第一個
                                isSelected = currentPreferences.firstOrNull() == it.id
                            ) {
                                // ✅ 點擊時呼叫 viewModel 更新
                                viewModel.onCellClicked(selectedDay!!, it.id)
                                showShiftSelector = false
                            }
                        }
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        // 其他班別
                        selectableShifts.forEach { shift ->
                            ShiftSelectorItem(
                                shift = shift,
                                // ✅ isSelected 判斷是否為列表第一個
                                isSelected = currentPreferences.firstOrNull() == shift.id
                            ) {
                                // ✅ 點擊時呼叫 viewModel 更新
                                viewModel.onCellClicked(selectedDay!!, shift.id)
                                showShiftSelector = false
                            }
                        }
                        // ✅ 新增：清除選擇的按鈕
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = {
                            // ✅ 點擊時呼叫 viewModel 更新 (傳入 "" 表示清除)
                            viewModel.onCellClicked(selectedDay!!, "")
                            showShiftSelector = false
                        }) {
                            Text("清除選擇 / 不預約")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showShiftSelector = false }) { Text("關閉") } } // 改為關閉
        )
    }

    // ... (即時衝突提醒 Dialog 和 儲存後總結 Dialog 保持不變) ...
    uiState.instantConflict?.let { conflict ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissInstantConflict() },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("預約提醒") },
            text = { Text(conflict.message) },
            confirmButton = { Button(onClick = { viewModel.dismissInstantConflict() }) { Text("我知道了") } }
        )
    }

    uiState.saveSummary?.let { summary ->
        ReservationSummaryDialog(summary = summary, onDismiss = { viewModel.dismissSummaryDialog() })
    }
}

// ReservationTable 修改：讀取 dailyShifts 的 firstOrNull()
@Composable
fun ReservationTable(
    month: String,
    users: List<User>,
    shiftTypes: List<ShiftType>,
    allReservations: List<Reservation>, // dailyShifts 為 Map<String, List<String>>
    myReservation: Reservation?, // dailyShifts 為 Map<String, List<String>>
    rotationSchedule: Map<String, Map<String, String>>,
    myUserId: String?,
    onCellClick: (String) -> Unit
) {
    val dates = DateUtils.getDatesInMonth(month)
    val scrollState = rememberScrollState()
    val shiftTypeMap = shiftTypes.associateBy { it.id }

    // reservationMap 的 value 現在是 Reservation 物件
    val reservationMap = remember(allReservations, myReservation) {
        val map = allReservations.associateBy { it.userId }.toMutableMap()
        myReservation?.let { map[it.userId] = it }
        map
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(scrollState)
    ) {
        // 表頭 - 日期 (保持不變)
        Row(modifier = Modifier.height(48.dp)) {
            // 姓名欄
            Surface(modifier = Modifier.width(100.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceVariant, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                Box(contentAlignment = Alignment.Center) { Text("姓名", style = MaterialTheme.typography.labelSmall) }
            }
            // 日期欄
            dates.forEach { date ->
                val day = date.split("-").last()
                val dayOfWeek = DateUtils.getDayOfWeekText(date)
                val isWeekend = DateUtils.isWeekend(date)
                Surface(
                    modifier = Modifier.width(60.dp).fillMaxHeight(),
                    color = if (isWeekend) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(day, style = MaterialTheme.typography.labelMedium)
                        Text(dayOfWeek, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // 表身 - 使用者預約狀態
        users.forEach { user ->
            Row(modifier = Modifier.height(56.dp)) {
                // 姓名 (保持不變)
                Surface(modifier = Modifier.width(100.dp).fillMaxHeight(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
                        Text(user.name, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    }
                }

                // 班別 - 修改顯示邏輯
                dates.forEach { date ->
                    val day = date.split("-").last()
                    val isMyRow = user.id == myUserId

                    // 1. 檢查是否有預排輪班 (不變)
                    val rotationShiftId = rotationSchedule[user.id]?.get(day)
                    val rotationShift = rotationShiftId?.let { shiftTypeMap[it] }

                    // 2. ✅ 檢查是否有使用者預約 (取 firstOrNull)
                    val reservationShiftId = reservationMap[user.id]?.dailyShifts?.get(day)?.firstOrNull()
                    val reservationShift = reservationShiftId?.let { shiftTypeMap[it] }

                    // 決定顯示哪個班別以及樣式 (邏輯不變)
                    val displayShift = rotationShift ?: reservationShift
                    val displayShortCode = displayShift?.shortCode ?: "-"
                    val cellColor: Color
                    val textColor: Color
                    val isClickable = isMyRow && rotationShift == null // 自己的格子且沒有預排輪班才能點

                    when {
                        rotationShift != null -> { // 優先顯示輪班
                            cellColor = MaterialTheme.colorScheme.surfaceVariant // 灰色背景
                            textColor = MaterialTheme.colorScheme.onSurfaceVariant // 深灰色文字
                        }
                        reservationShift != null -> { // 顯示使用者預約
                            // ✅ 使用 ?. 安全調用 toComposeColor 並提供預設
                            cellColor = reservationShift.color?.toComposeColor()?.copy(alpha = 0.3f) ?: Color.Transparent
                            textColor = MaterialTheme.colorScheme.onSurface // 正常文字顏色
                        }
                        else -> { // 空白格
                            cellColor = Color.Transparent
                            textColor = MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .width(60.dp)
                            .fillMaxHeight()
                            .clickable(enabled = isClickable) { onCellClick(day) }, // <<-- 根據 isClickable 決定是否可點
                        color = cellColor,
                        border = BorderStroke(
                            1.dp,
                            // 自己的格子框線加粗
                            if (isMyRow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = displayShortCode,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isMyRow && reservationShift != null) FontWeight.Bold else FontWeight.Normal, // 自己的預約加粗
                                color = textColor // <<-- 使用計算出的文字顏色
                            )
                        }
                    }
                }
            }
        }
    }
}


// ✅ 新增：為 ShiftReservationScreen 定義一個獨立的 ShiftLegend
@Composable
fun ShiftLegendInReservation(shiftTypes: List<ShiftType>) {
    // 保持原有的 LazyColumn 實現
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
                    // ✅ 使用 ?. 安全調用 toComposeColor 並提供預設
                    color = shift.color?.toComposeColor() ?: Color.Gray,
                    shape = MaterialTheme.shapes.small
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = shift.shortCode,
                            color = Color.White, // 假設文字都是白色
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


// ShiftSelectorItem 保持不變 (isSelected 在 Dialog 中處理)
@Composable
private fun ShiftSelectorItem(shift: ShiftType, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        // ✅ 使用 ?. 安全調用 toComposeColor 並提供預設
        border = BorderStroke(1.dp, shift.color?.toComposeColor() ?: Color.Gray)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                // ✅ 使用 ?. 安全調用 toComposeColor 並提供預設
                color = shift.color?.toComposeColor() ?: Color.Gray,
                shape = MaterialTheme.shapes.small
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = shift.shortCode, color = Color.White) // 假設文字都是白色
                }
            }
            Column {
                Text(text = shift.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${shift.startTime} - ${shift.endTime}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ReservationSummaryDialog 保持不變
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
                if (summary.manpowerViolations.isEmpty() && summary.ruleViolations.isEmpty() && summary.usersToCoordinate.isEmpty()) {
                    item { Text("您的預約目前沒有明顯衝突。") }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("確認") } }
    )
}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲