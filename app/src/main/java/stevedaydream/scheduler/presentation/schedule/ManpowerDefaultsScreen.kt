// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
package stevedaydream.scheduler.presentation.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import stevedaydream.scheduler.data.model.ShiftType
import stevedaydream.scheduler.util.DateUtils
import java.util.Calendar

@Composable
fun ManpowerDefaultsScreen(
    uiState: ManpowerUiState,
    onDefaultChange: (dayType: String, shiftTypeId: String, count: Int) -> Unit,
    onProceed: () -> Unit,
    // ✅ 新增 ViewModel 互動的 callbacks
    onDateClicked: (String) -> Unit,
    onAddHoliday: (String, String) -> Unit,
    onDismissHolidayDialog: () -> Unit
) {
    val defaults = uiState.manpowerPlan?.requirementDefaults

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            DefaultCategoryCard(
                title = "平日 (週一至週五)",
                shiftTypes = uiState.shiftTypes,
                values = defaults?.weekday ?: emptyMap(),
                onValueChange = { shiftTypeId, count ->
                    onDefaultChange("weekday", shiftTypeId, count)
                }
            )
        }
        item {
            DefaultCategoryCard(
                title = "週六",
                shiftTypes = uiState.shiftTypes,
                values = defaults?.saturday ?: emptyMap(),
                onValueChange = { shiftTypeId, count ->
                    onDefaultChange("saturday", shiftTypeId, count)
                }
            )
        }
        item {
            DefaultCategoryCard(
                title = "週日",
                shiftTypes = uiState.shiftTypes,
                values = defaults?.sunday ?: emptyMap(),
                onValueChange = { shiftTypeId, count ->
                    onDefaultChange("sunday", shiftTypeId, count)
                }
            )
        }
        item {
            // ✅ 使用新的小月曆卡片
            HolidayCalendarCard(
                month = uiState.manpowerPlan?.month ?: "",
                holidays = uiState.holidays,
                onDateClick = onDateClicked
            )
        }
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onProceed,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("套用範本並進入微調")
            }
        }
    }

    // ✅ 如果需要輸入假日名稱，則顯示對話框
    uiState.showHolidayNameDialogFor?.let { date ->
        HolidayNameDialog(
            date = date,
            onDismiss = onDismissHolidayDialog,
            onConfirm = { name -> onAddHoliday(date, name) }
        )
    }
}

/**
 * ✅ 新增：假日名稱輸入對話框
 */
@Composable
private fun HolidayNameDialog(
    date: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("設定 ${DateUtils.getDisplayDate(date)} 為特殊日") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名稱 (例如：盤點日)") },
                placeholder = { Text("若不填，預設為「特殊日」") }
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(name) }) { Text("確認") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}


/**
 * ✅ 新增：顯示小月曆的卡片
 */
@Composable
private fun HolidayCalendarCard(
    month: String,
    holidays: Map<String, String>,
    onDateClick: (String) -> Unit
) {
    val datesInMonth = remember(month) { DateUtils.getDatesInMonth(month) }
    val firstDayOfWeek = remember(month) { DateUtils.getDayOfWeek(datesInMonth.first()) } // 0=週日, 1=週一...
    val weekDays = listOf("日", "一", "二", "三", "四", "五", "六")

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "國定假日／特殊日", style = MaterialTheme.typography.titleLarge)
            Text(
                "從 API 自動帶入國定假日，您也可以手動點擊日期來新增或移除特殊日。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // 星期標頭
            Row(modifier = Modifier.fillMaxWidth()) {
                weekDays.forEach { day ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(day, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // 日期網格
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.heightIn(max = 300.dp), // 限制最大高度
                userScrollEnabled = false
            ) {
                // 根據第一天是星期幾，填入空白
                items(firstDayOfWeek) { Box(Modifier) }

                items(datesInMonth.size) { index ->
                    val date = datesInMonth[index]
                    val isHoliday = holidays.containsKey(date)
                    val dayNumber = date.split("-").last()

                    val borderColor = if (isHoliday) MaterialTheme.colorScheme.primary else Color.Transparent
                    val backgroundColor = if (isHoliday) MaterialTheme.colorScheme.primaryContainer else Color.Transparent

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .padding(2.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(backgroundColor)
                            .border(1.dp, borderColor, MaterialTheme.shapes.small)
                            .clickable { onDateClick(date) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = dayNumber.removePrefix("0"),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if(isHoliday) {
                                Text(
                                    text = holidays[date] ?: "",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun DefaultCategoryCard(
    title: String,
    shiftTypes: List<ShiftType>,
    values: Map<String, Int>,
    onValueChange: (shiftTypeId: String, count: Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Divider()
            shiftTypes.forEach { shiftType ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = shiftType.name, modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = values[shiftType.id]?.toString() ?: "",
                        onValueChange = {
                            onValueChange(shiftType.id, it.toIntOrNull() ?: 0)
                        },
                        modifier = Modifier.width(100.dp),
                        label = { Text("人數") },
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            }
        }
    }
}