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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class) // ✅ 新增 ExperimentalLayoutApi
@Composable
fun ScheduleDetailScreen(
    viewModel: ScheduleDetailViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val schedule by viewModel.schedule.collectAsState()
    val assignments by viewModel.assignments.collectAsState()
    val users by viewModel.users.collectAsState()
    val shiftTypes by viewModel.shiftTypes.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

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
                    IconButton(onClick = { /* TODO: 實作分享功能 */ }) {
                        Icon(Icons.Default.Share, contentDescription = "分享")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            LoadingIndicator(modifier = Modifier.padding(padding))
        } else if (schedule != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                ShiftLegend(
                    shiftTypes = shiftTypes,
                    modifier = Modifier.padding(16.dp)
                )
                Divider()

                ScheduleDetailTable(
                    month = schedule!!.month,
                    users = users,
                    shiftTypes = shiftTypes,
                    assignments = assignments
                )
            }
        }
    }
}


@OptIn(ExperimentalLayoutApi::class) // ✅ 標記為實驗性 API
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
        // 🔽🔽🔽 使用官方內建的 FlowRow 🔽🔽🔽
        FlowRow(
            // 使用新的參數來設定間距
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
        // 🔼🔼🔼 到此為止 🔼🔼🔼
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