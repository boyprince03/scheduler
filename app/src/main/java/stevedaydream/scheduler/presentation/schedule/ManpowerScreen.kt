package stevedaydream.scheduler.presentation.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import stevedaydream.scheduler.data.model.DailyRequirement
import stevedaydream.scheduler.data.model.ShiftType
import stevedaydream.scheduler.presentation.common.LoadingIndicator
import stevedaydream.scheduler.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManpowerScreen(
    viewModel: ManpowerViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${DateUtils.getDisplayMonth(viewModel.month)} 人力規劃") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.savePlan() }) {
                        Text("儲存")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            LoadingIndicator(modifier = Modifier.padding(padding))
        } else {
            Column(modifier = Modifier
                .fillMaxSize()
                .padding(padding)) {
                ManpowerTable(
                    uiState = uiState,
                    onRequirementChange = viewModel::updateRequirement
                )
            }
        }
    }
}

@Composable
fun ManpowerTable(
    uiState: ManpowerUiState,
    onRequirementChange: (String, String, Int) -> Unit
) {
    val dates = DateUtils.getDatesInMonth(uiState.manpowerPlan?.month ?: "")
    val groupMemberCount = uiState.group?.memberIds?.size ?: 0
    val horizontalScrollState = rememberScrollState()

    // 結合了 LazyColumn 和 HorizontalScroll 來建立一個可雙向捲動的表格
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
                // 日期欄
                DateCell(date, dailyReq, width = 120.dp)

                // 班別需求輸入欄
                uiState.shiftTypes.forEach { shiftType ->
                    RequirementInputCell(
                        value = dailyReq?.requirements?.get(shiftType.id)?.toString() ?: "",
                        onValueChange = {
                            onRequirementChange(day, shiftType.id, it.toIntOrNull() ?: 0)
                        },
                        width = 80.dp
                    )
                }

                // 計算結果欄
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

// 為了重用性，將 Cell 拆分成獨立的 Composable
@Composable
private fun RowScope.HeaderCell(text: String, width: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .height(56.dp)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RowScope.DateCell(date: String, dailyReq: DailyRequirement?, width: androidx.compose.ui.unit.Dp) {
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
                Text(dailyReq.holidayName ?: "國定假日", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
private fun RowScope.RequirementInputCell(value: String, onValueChange: (String) -> Unit, width: androidx.compose.ui.unit.Dp) {
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
            modifier = Modifier
                .width(60.dp)
                .height(50.dp),
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )
    }
}

@Composable
private fun RowScope.CalculatedCell(text: String, width: androidx.compose.ui.unit.Dp, isPositive: Boolean = true) {
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