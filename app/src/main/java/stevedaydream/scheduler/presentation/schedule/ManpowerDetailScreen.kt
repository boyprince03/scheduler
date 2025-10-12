package stevedaydream.scheduler.presentation.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import stevedaydream.scheduler.data.model.DailyRequirement
import stevedaydream.scheduler.util.DateUtils

@Composable
fun ManpowerDetailScreen(
    uiState: ManpowerUiState,
    onRequirementChange: (day: String, shiftTypeId: String, count: Int) -> Unit
) {
    val dates = DateUtils.getDatesInMonth(uiState.manpowerPlan?.month ?: "")
    val groupMemberCount = uiState.group?.memberIds?.size ?: 0
    val horizontalScrollState = rememberScrollState()

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // 表頭
        item {
            Row(
                Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .horizontalScroll(horizontalScrollState)
            ) {
                HeaderCell(text = "日期", width = 120.dp)
                uiState.shiftTypes.forEach { shiftType ->
                    HeaderCell(text = shiftType.name, width = 80.dp)
                }
                HeaderCell(text = "需求總計", width = 90.dp)
                HeaderCell(text = "可休假人數", width = 100.dp)
            }
        }

        // 表身
        items(dates.size) { index ->
            val date = dates[index]
            val day = date.split("-").last()
            val dailyReq = uiState.manpowerPlan?.dailyRequirements?.get(day)

            val totalRequired = dailyReq?.requirements?.values?.sum() ?: 0
            val availableForLeave = (groupMemberCount - totalRequired).coerceAtLeast(0)

            Row(
                Modifier
                    .horizontalScroll(horizontalScrollState)
                    .background(
                        if (dailyReq?.isHoliday == true) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                        else Color.Transparent
                    )
            ) {
                DateCell(date, dailyReq, width = 120.dp)
                uiState.shiftTypes.forEach { shiftType ->
                    RequirementInputCell(
                        value = dailyReq?.requirements?.get(shiftType.id)?.toString() ?: "",
                        onValueChange = {
                            onRequirementChange(day, shiftType.id, it.toIntOrNull() ?: 0)
                        },
                        width = 80.dp
                    )
                }
                CalculatedCell(text = totalRequired.toString(), width = 90.dp)
                CalculatedCell(
                    text = availableForLeave.toString(),
                    width = 100.dp,
                    isPositive = availableForLeave > 0
                )
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, width: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .height(56.dp)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun DateCell(date: String, dailyReq: DailyRequirement?, width: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .height(64.dp)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant))
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${date.split("-").last()}日 (${DateUtils.getDayOfWeekText(date)})", style = MaterialTheme.typography.bodyMedium)
            if (dailyReq?.isHoliday == true) {
                Text(dailyReq.holidayName ?: "國定假日", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, textAlign = TextAlign.Center)
            }
        }
    }
}

// ✅ ===== 修正點 =====
// 使用官方標準的 OutlinedTextField 並調整其參數
@Composable
private fun RequirementInputCell(value: String, onValueChange: (String) -> Unit, width: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .height(64.dp)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)),
        contentAlignment = Alignment.Center
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.width(70.dp),
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            shape = MaterialTheme.shapes.medium, // 使用圓角
            // 讓輸入框的背景透明，與 Box 融合
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
            )
        )
    }
}

@Composable
private fun CalculatedCell(text: String, width: androidx.compose.ui.unit.Dp, isPositive: Boolean = true) {
    Box(
        modifier = Modifier
            .width(width)
            .height(64.dp)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isPositive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ✅ 移除先前有問題的自訂 OutlinedTextField 函式