// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
// scheduler/presentation/group/GroupListScreen.kt
package stevedaydream.scheduler.presentation.group

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import stevedaydream.scheduler.data.model.Group

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupListScreen(
    orgId: String,
    viewModel: GroupListViewModel = hiltViewModel(),
    onGroupClick: (String) -> Unit, // <<-- 恢復 onGroupClick 參數，用於導航到 ScheduleScreen
    onBackClick: () -> Unit,
    onNavigateToInviteManagement: (String) -> Unit,
    onNavigateToMemberList: (orgId: String, groupId: String) -> Unit // 保持管理成員的導航
) {
    val groups by viewModel.groups.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(orgId) {
        viewModel.loadGroups(orgId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("群組列表") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 移除 TopAppBar 上的管理成員按鈕
                    IconButton(onClick = { onNavigateToInviteManagement(orgId) }) {
                        Icon(Icons.Default.GroupAdd, contentDescription = "邀請成員")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "建立群組")
            }
        }
    ) { padding ->
        if (groups.isEmpty()) {
            // ... (空白狀態不變) ...
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("尚未建立任何群組")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(groups) { group ->
                    GroupCard(
                        group = group,
                        // 點擊卡片主體，觸發 onGroupClick (導航到 ScheduleScreen)
                        onClick = { onGroupClick(group.id) },
                        // 傳入管理成員的回呼函數
                        onManageMembersClick = { onNavigateToMemberList(orgId, group.id) }
                    )
                }
            }
        }
    }
    if (showCreateDialog) {
        CreateGroupDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { groupName ->
                viewModel.createGroup(orgId, groupName)
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun GroupCard(
    group: Group,
    onClick: () -> Unit, // 卡片主體點擊事件 (去排班頁面)
    onManageMembersClick: () -> Unit // 管理成員按鈕點擊事件
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        // 移除 Card 的 clickable，讓 Row 處理點擊
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            // 將 clickable 移到 Row 上，這樣管理按鈕可以獨立點擊
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Groups,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.groupName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${group.memberIds.size} 位成員",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (group.isSchedulerActive()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = "排班中: ${group.schedulerName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
            // 新增：管理成員的 IconButton
            IconButton(onClick = onManageMembersClick) {
                Icon(Icons.Default.ManageAccounts, contentDescription = "管理成員")
            }
        }
    }
}

// CreateGroupDialog 保持不變
@Composable
fun CreateGroupDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var groupName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("建立新群組") },
        text = {
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text("群組名稱") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (groupName.isNotBlank()) {
                        onCreate(groupName.trim())
                    }
                }
            ) {
                Text("建立")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲