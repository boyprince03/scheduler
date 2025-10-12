package stevedaydream.scheduler.presentation.user

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import stevedaydream.scheduler.data.model.Group
import stevedaydream.scheduler.presentation.common.InfoCard
import stevedaydream.scheduler.presentation.common.LoadingIndicator
import stevedaydream.scheduler.util.showToast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    viewModel: UserProfileViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showGroupSelectorDialog by remember { mutableStateOf(false) }

    // ✅ 1. 修改 LaunchedEffect 來監聽儲存結果
    LaunchedEffect(uiState.requestResult, uiState.saveResult) {
        uiState.requestResult?.onSuccess {
            context.showToast("申請已送出，請等候管理員核准")
            viewModel.clearRequestResult()
        }?.onFailure {
            context.showToast("申請失敗: ${it.message}")
            viewModel.clearRequestResult()
        }

        uiState.saveResult?.onSuccess {
            context.showToast("個人資料更新成功")
            viewModel.clearSaveResult()
        }?.onFailure {
            context.showToast("更新失敗: ${it.message}")
            viewModel.clearSaveResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("個人資料") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            LoadingIndicator(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    // ✅ 2. 傳遞更多參數給 UserInfoCard
                    uiState.currentUser?.let { user ->
                        UserInfoCard(
                            user = user,
                            uiState = uiState,
                            onNameChange = viewModel::onNameChange,
                            onEmployeeIdChange = viewModel::onEmployeeIdChange,
                            onEnableEdit = viewModel::enableEditMode,
                            onCancelEdit = viewModel::cancelEditMode,
                            onSave = viewModel::saveUserProfile
                        )
                    }
                }
                item {
                    OrganizationInfoCard(
                        organizationName = uiState.organization?.orgName,
                        groupName = uiState.currentGroup?.groupName,
                        onClick = { showGroupSelectorDialog = true }
                    )
                }
            }
        }
    }

    if (showGroupSelectorDialog) {
        JoinGroupDialog(
            allGroups = uiState.allGroups,
            currentGroup = uiState.currentGroup,
            onDismiss = { showGroupSelectorDialog = false },
            onSelectGroup = { selectedGroup ->
                viewModel.sendGroupJoinRequest(selectedGroup)
                showGroupSelectorDialog = false
            }
        )
    }
}

@Composable
private fun UserInfoCard(
    user: stevedaydream.scheduler.data.model.User,
    uiState: UserProfileUiState,
    onNameChange: (String) -> Unit,
    onEmployeeIdChange: (String) -> Unit,
    onEnableEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSave: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // -- 卡片標頭 --
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "基本資料",
                    style = MaterialTheme.typography.titleMedium
                )
                // ✅ 3. 只有在非編輯模式下顯示編輯按鈕
                if (!uiState.isEditing) {
                    IconButton(onClick = onEnableEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "編輯資料")
                    }
                }
            }
            Divider()

            // -- 姓名 --
            if (uiState.isEditing) {
                OutlinedTextField(
                    value = uiState.nameInput,
                    onValueChange = onNameChange,
                    label = { Text("姓名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            } else {
                InfoRow(icon = Icons.Default.Person, label = "姓名", value = user.name)
            }

            // -- Email (不可編輯) --
            InfoRow(icon = Icons.Default.Email, label = "Email", value = user.email)

            // -- 員工編號 --
            if (uiState.isEditing) {
                OutlinedTextField(
                    value = uiState.employeeIdInput,
                    onValueChange = onEmployeeIdChange,
                    label = { Text("員工編號 (選填)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            } else {
                InfoRow(icon = Icons.Default.Badge, label = "員工編號", value = user.employeeId.ifEmpty { "尚未設定" })
            }


            // ✅ 4. 新增儲存與取消按鈕
            if (uiState.isEditing) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onCancelEdit, enabled = !uiState.isSaving) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onSave, enabled = !uiState.isSaving) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("儲存")
                        }
                    }
                }
            }
        }
    }
}

// ... (OrganizationInfoCard 和 InfoRow 保持不變) ...
@Composable
private fun OrganizationInfoCard(
    organizationName: String?,
    groupName: String?,
    onClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("所屬單位", style = MaterialTheme.typography.titleMedium)
        InfoCard(
            title = "公司",
            description = organizationName ?: "尚未加入任何公司",
            icon = Icons.Default.Business
        )
        InfoCard(
            title = "組別",
            description = groupName ?: "尚未加入任何組別",
            icon = Icons.Default.Groups,
            onClick = onClick
        )
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = "$label:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
// ... (JoinGroupDialog 保持不變) ...
@Composable
private fun JoinGroupDialog(
    allGroups: List<Group>,
    currentGroup: Group?,
    onDismiss: () -> Unit,
    onSelectGroup: (Group) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("申請加入或更改組別") },
        text = {
            if (allGroups.isEmpty()) {
                Text("目前沒有可加入的組別。")
            } else {
                LazyColumn {
                    items(allGroups, key = { it.id }) { group ->
                        val isCurrentUserInGroup = group.id == currentGroup?.id
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = group.groupName,
                                    fontWeight = if (isCurrentUserInGroup) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            supportingContent = { Text("${group.memberIds.size} 位成員") },
                            trailingContent = {
                                if (isCurrentUserInGroup) {
                                    Icon(Icons.Default.Check, contentDescription = "目前組別")
                                }
                            },
                            modifier = Modifier.clickable { onSelectGroup(group) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}