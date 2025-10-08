package stevedaydream.scheduler.presentation.schedule

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    orgId: String,
    groupId: String,
    viewModel: ScheduleViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val group by viewModel.group.collectAsState()
    val canSchedule by viewModel.canSchedule.collectAsState()
    val isScheduler by viewModel.isScheduler.collectAsState()

    LaunchedEffect(orgId, groupId) {
        viewModel.loadGroup(orgId, groupId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.groupName ?: "排班") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (isScheduler) {
                        IconButton(onClick = { viewModel.releaseScheduler() }) {
                            Icon(Icons.Default.ExitToApp, contentDescription = "釋放排班權")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 排班者狀態卡片
            group?.let { currentGroup ->
                SchedulerStatusCard(
                    group = currentGroup,
                    canSchedule = canSchedule,
                    isScheduler = isScheduler,
                    onClaimClick = { viewModel.claimScheduler() }
                )
            }

            // 功能區域
            if (isScheduler) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "排班功能",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Button(
                            onClick = { /* TODO: 啟動智慧排班 */ },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("開始智慧排班")
                        }
                        OutlinedButton(
                            onClick = { /* TODO: 手動排班 */ },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("手動排班")
                        }
                    }
                }
            }

            // 班表預覽區域 (待實作)
            Text(
                text = "班表內容將顯示在此處",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SchedulerStatusCard(
    group: stevedaydream.scheduler.data.model.Group,
    canSchedule: Boolean,
    isScheduler: Boolean,
    onClaimClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isScheduler -> MaterialTheme.colorScheme.primaryContainer
                group.isSchedulerActive() -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "排班者狀態",
                style = MaterialTheme.typography.titleSmall
            )

            when {
                isScheduler -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text("你正在排班中")
                    }
                }
                group.isSchedulerActive() -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Text("${group.schedulerName} 正在排班")
                    }
                }
                canSchedule -> {
                    Button(
                        onClick = onClaimClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("認領排班權")
                    }
                }
            }
        }
    }
}