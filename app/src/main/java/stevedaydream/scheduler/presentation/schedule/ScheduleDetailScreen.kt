// scheduler/presentation/schedule/ScheduleDetailScreen.kt

package stevedaydream.scheduler.presentation.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import stevedaydream.scheduler.data.model.Assignment
import stevedaydream.scheduler.data.model.ShiftType
import stevedaydream.scheduler.data.model.User
import stevedaydream.scheduler.presentation.common.LoadingIndicator
import stevedaydream.scheduler.util.DateUtils
import stevedaydream.scheduler.util.toComposeColor
// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.text.font.FontWeight
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleDetailScreen(
    viewModel: ScheduleDetailViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
    onEditClick: (month: String) -> Unit
    // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲
) {
    val uiState by viewModel.uiState.collectAsState()
    val schedule = uiState.schedule

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(schedule?.let { DateUtils.getDisplayMonth(it.month) } ?: "排班詳情") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
                    if (schedule != null) {
                        IconButton(onClick = { onEditClick(schedule.month) }) {
                            Icon(Icons.Default.Edit, contentDescription = "編輯班表")
                        }
                    }
                    // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲
                    IconButton(onClick = { /* TODO: 實作分享功能 */ }) {
                        Icon(Icons.Default.Share, contentDescription = "分享")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            LoadingIndicator(modifier = Modifier.padding(padding))
        } else if (schedule != null) {
            // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item {
                    ShiftLegend(
                        shiftTypes = uiState.shiftTypes,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                item { Divider() }

                item {
                    ScheduleDetailTable(
                        month = schedule.month,
                        users = uiState.users,
                        shiftTypes = uiState.shiftTypes,
                        assignments = uiState.assignments
                    )
                }

                // 新增：排班結果分析區塊
                if (schedule.generationMethod == "smart" && schedule.violatedRules.isNotEmpty()) {
                    item {
                        AnalysisCard(
                            violatedRules = schedule.violatedRules,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲
        }
    }
}

// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
@Composable
fun AnalysisCard(
    violatedRules: List<String>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "排班結果分析",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "本次智慧排班違反了以下 ${violatedRules.size} 條規則：",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            violatedRules.forEach { violation ->
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = violation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShiftLegend(
    shiftTypes: List<ShiftType>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "班別圖例",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            shiftTypes.forEach { shift ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(shift.color.toComposeColor(), shape = MaterialTheme.shapes.small)
                    )
                    Text(
                        text = "${shift.shortCode}: ${shift.name}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}


@Composable
fun ScheduleDetailTable(
    month: String,
    users: List<User>,
    shiftTypes: List<ShiftType>,
    assignments: List<Assignment>
) {
    val dates = DateUtils.getDatesInMonth(month)
    val scrollState = rememberScrollState()

    val assignmentMap = assignments.associate { it.userId to it.dailyShifts }
    val shiftTypeMap = shiftTypes.associateBy { it.id }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(scrollState)
    ) {
        // 表頭
        Row(modifier = Modifier.height(48.dp)) {
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
            dates.forEach { date ->
                val day = date.split("-").last()
                val dayOfWeek = DateUtils.getDayOfWeekText(date)
                val isWeekend = DateUtils.isWeekend(date)

                Surface(
                    modifier = Modifier
                        .width(60.dp)
                        .fillMaxHeight(),
                    color = if (isWeekend) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant,
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

        // 表身
        users.forEach { user ->
            Row(modifier = Modifier.height(56.dp)) {
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

                dates.forEach { date ->
                    val day = date.split("-").last()
                    val shiftId = assignmentMap[user.id]?.get(day)
                    val shift = shiftTypeMap[shiftId]

                    Surface(
                        modifier = Modifier
                            .width(60.dp)
                            .fillMaxHeight(),
                        color = shift?.color?.let { it.toComposeColor().copy(alpha = 0.2f) } ?: Color.Transparent,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = shift?.shortCode ?: "-", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}