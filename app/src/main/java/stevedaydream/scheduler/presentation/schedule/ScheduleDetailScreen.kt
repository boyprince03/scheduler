// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
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
import stevedaydream.scheduler.data.model.ManpowerPlan
import stevedaydream.scheduler.data.model.SchedulingRule
import stevedaydream.scheduler.data.model.ShiftType
import stevedaydream.scheduler.data.model.User
import stevedaydream.scheduler.presentation.common.LoadingIndicator
import stevedaydream.scheduler.util.DateUtils
import stevedaydream.scheduler.util.toComposeColor
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import stevedaydream.scheduler.presentation.schedule.ScheduleStatistics

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleDetailScreen(
    viewModel: ScheduleDetailViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onEditClick: (month: String) -> Unit
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
                    if (schedule != null) {
                        IconButton(onClick = { onEditClick(schedule.month) }) {
                            Icon(Icons.Default.Edit, contentDescription = "編輯班表")
                        }
                    }
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
                        assignments = uiState.assignments,
                        manpowerPlan = uiState.manpowerPlan
                    )
                }

                // ✅ 修正點：在這裡加入班表數據統計卡片
                item {
                    ScheduleStatisticsCard(
                        stats = uiState.statistics,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                item {
                    SchedulingRulesCard(
                        enabledRules = uiState.enabledRules,
                        violatedRuleMessages = schedule.violatedRules,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("無法載入班表資料")
            }
        }
    }
}

// ... 其他 Composable 函式 (SchedulingRulesCard, StatItem, ShiftLegend 等) 保持不變 ...
// (此處省略未變更的程式碼以保持簡潔)

// ✅ 將 AnalysisCard 重新命名並重寫為 SchedulingRulesCard
@Composable
fun SchedulingRulesCard(
    enabledRules: List<SchedulingRule>,
    violatedRuleMessages: List<String>,
    modifier: Modifier = Modifier
) {
    // 建立一個查詢較快速的 Set
    val violatedRuleNames = remember(violatedRuleMessages) {
        // 從違規訊息中解析出規則名稱 (假設訊息格式為 "使用者: 違規詳情")
        // 這裡我們用一個比較寬鬆的比對方式
        val names = mutableSetOf<String>()
        for (rule in enabledRules) {
            if (violatedRuleMessages.any { msg -> msg.contains(rule.ruleName) || msg.contains(rule.description.take(5)) }) {
                names.add(rule.ruleName)
            }
        }
        names
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "班表規則說明",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "本次排班啟用 ${enabledRules.size} 條規則：",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))

            enabledRules.forEach { rule ->
                val isViolated = rule.ruleName in violatedRuleNames
                RuleInfoRow(rule = rule, isViolated = isViolated)
            }
        }
    }
}
@Composable
private fun RuleInfoRow(rule: SchedulingRule, isViolated: Boolean) {
    val icon = if (isViolated) Icons.Default.Warning else Icons.Default.CheckCircle
    val tint = if (isViolated) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val textColor = if (isViolated) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp).padding(top = 2.dp)
        )
        Column {
            Text(
                text = rule.ruleName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = rule.description,
                style = MaterialTheme.typography.bodySmall,
                color = textColor
            )
        }
    }
}


/**
 * ✅ 新增：班表數據統計卡片
 */
@Composable
fun ScheduleStatisticsCard(
    stats: ScheduleStatistics,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "班表數據統計",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))

            // 全體統計
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatItem(
                    icon = Icons.Default.Bedtime,
                    label = "總休假天數",
                    value = "${stats.actualOffDays}",
                    subValue = "(目標: ${stats.targetOffDays})",
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    icon = Icons.Default.Info,
                    label = "總值班天數",
                    value = "${stats.totalDutyDays}",
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    icon = Icons.Default.BarChart,
                    label = "日均人力",
                    value = "%.1f".format(stats.averageDailyManpower),
                    modifier = Modifier.weight(1f)
                )
            }

            Divider(modifier = Modifier.padding(vertical = 12.dp))

            // 個人統計
            Text(
                "我的本月統計",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatItem(
                    icon = Icons.Default.Person,
                    label = "我的工時",
                    value = "%.1f".format(stats.currentUserWorkHours),
                    subValue = "小時",
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    icon = Icons.Default.Bedtime,
                    label = "我的休假",
                    value = "${stats.currentUserOffDays}",
                    subValue = "天",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * ✅ 新增：單個統計項目的 UI 元件
 */
@Composable
fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    subValue: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            subValue?.let {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }
}


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
    assignments: List<Assignment>,
    manpowerPlan: ManpowerPlan? // ✅ 新增 manpowerPlan 參數
) {
    val dates = DateUtils.getDatesInMonth(month)
    val scrollState = rememberScrollState()

    val assignmentMap = assignments.associate { it.userId to it.dailyShifts }
    val shiftTypeMap = shiftTypes.associateBy { it.id }

    // ✅ 建立一個方便查詢的假日地圖
    val holidayMap = remember(manpowerPlan) {
        manpowerPlan?.dailyRequirements?.values
            ?.filter { it.isHoliday }
            ?.associate { it.date to (it.holidayName ?: "假日") }
            ?: emptyMap()
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(scrollState)
    ) {
        // 表頭
        Column(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
            // ✅ 新增：國定假日列
            Row(modifier = Modifier.height(36.dp)) {
                Spacer(modifier = Modifier.width(100.dp)) // 對齊姓名欄的寬度
                dates.forEach { date ->
                    Surface(
                        modifier = Modifier
                            .width(60.dp)
                            .fillMaxHeight(),
                        color = Color.Transparent, // 背景由外層 Column 控制
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 2.dp)) {
                            Text(
                                text = holidayMap[date] ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // 日期列 (原表頭)
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
                    val isHoliday = holidayMap.containsKey(date)

                    Surface(
                        modifier = Modifier
                            .width(60.dp)
                            .fillMaxHeight(),
                        // ✅ 假日也使用醒目顏色
                        color = if (isWeekend || isHoliday) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant,
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
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲