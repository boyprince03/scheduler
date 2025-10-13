// scheduler/presentation/user/UserProfileScreen.kt
package stevedaydream.scheduler.presentation.user

import androidx.compose.animation.AnimatedVisibility
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
import stevedaydream.scheduler.data.model.GroupJoinRequest
import stevedaydream.scheduler.data.model.Organization
import stevedaydream.scheduler.presentation.common.ConfirmDialog
import stevedaydream.scheduler.presentation.common.InfoCard
import stevedaydream.scheduler.presentation.common.LoadingIndicator
import stevedaydream.scheduler.presentation.common.StatusChip
import stevedaydream.scheduler.util.showToast



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    viewModel: UserProfileViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var orgToLeave by remember { mutableStateOf<Organization?>(null) }

    LaunchedEffect(uiState.requestResult) {
        uiState.requestResult?.onSuccess {
            context.showToast("申請已送出，請等候管理員核准")
            viewModel.clearRequestResult()
        }?.onFailure {
            context.showToast("申請失敗: ${it.message}")
            viewModel.clearRequestResult()
        }
    }

    LaunchedEffect(uiState.saveResult) {
        uiState.saveResult?.onSuccess {
            context.showToast("個人資料更新成功")
            viewModel.clearSaveResult()
        }?.onFailure {
            context.showToast("更新失敗: ${it.message}")
            viewModel.clearSaveResult()
        }
    }

    LaunchedEffect(uiState.leaveOrgResult) {
        uiState.leaveOrgResult?.onSuccess {
            context.showToast("已退出組織")
            viewModel.clearLeaveOrgResult()
        }?.onFailure {
            context.showToast("退出失敗: ${it.message}")
            viewModel.clearLeaveOrgResult()
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
                    UserInfoCard(
                        user = uiState.currentUser,
                        uiState = uiState,
                        onNameChange = viewModel::onNameChange,
                        onEmployeeIdChange = viewModel::onEmployeeIdChange,
                        onEnableEdit = viewModel::enableEditMode,
                        onCancelEdit = viewModel::cancelEditMode,
                        onSave = viewModel::saveUserProfile
                    )
                }

                item {
                    Text("所屬單位", style = MaterialTheme.typography.titleMedium)
                }

                if (uiState.organizationsInfo.isEmpty()) {
                    item {
                        InfoCard(
                            title = "尚未加入任何組織",
                            description = "您可以透過邀請碼加入，或建立一個新的組織",
                            icon = Icons.Default.Business
                        )
                    }
                } else {
                    items(uiState.organizationsInfo, key = { it.organization.id }) { orgInfo ->
                        // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
                        OrganizationAndGroupCard(
                            orgInfo = orgInfo,
                            currentUser = uiState.currentUser,
                            pendingRequests = uiState.pendingGroupRequests,
                            updatingGroupRequests = uiState.updatingGroupRequests, // 傳入新的狀態
                            onJoinGroupClick = { group ->
                                viewModel.sendGroupJoinRequest(orgInfo.organization.id, group)
                            },
                            onCancelRequestClick = { request ->
                                viewModel.cancelGroupJoinRequest(request)
                            },
                            onLeaveOrgClick = { orgToLeave = orgInfo.organization }
                        )
                        // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲
                    }
                }
            }
        }
    }

    orgToLeave?.let { org ->
        ConfirmDialog(
            title = "確認退出組織",
            message = "您確定要退出「${org.orgName}」嗎？此操作將會移除您在此組織的所有資料。",
            confirmText = "確認退出",
            onConfirm = {
                viewModel.leaveOrganization(org.id)
                orgToLeave = null
            },
            onDismiss = { orgToLeave = null }
        )
    }
}

// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
@Composable
private fun OrganizationAndGroupCard(
    orgInfo: UserOrganizationInfo,
    currentUser: stevedaydream.scheduler.data.model.User?,
    pendingRequests: List<GroupJoinRequest>,
    updatingGroupRequests: Set<String>, // 新增參數
    onCancelRequestClick: (GroupJoinRequest) -> Unit,
    onJoinGroupClick: (Group) -> Unit,
    onLeaveOrgClick: () -> Unit
) {
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲
    var isExpanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            // Organization Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(Icons.Default.Business, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = orgInfo.organization.displayName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    StatusChip(label = orgInfo.userStatus)
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "收合" else "展開"
                )
            }


            // Expandable Group List
            AnimatedVisibility(visible = isExpanded) {
                Column {
                    Divider()
                    if (orgInfo.groups.isEmpty()) {
                        Text(
                            "此組織尚無任何群組",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    } else {
                        // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
                        orgInfo.groups.forEach { group ->
                            val isMember = currentUser?.let { user -> group.memberIds.contains(user.id) } ?: false
                            val pendingRequest = pendingRequests.find { it.targetGroupId == group.id && it.status == "pending" }
                            val isUpdating = updatingGroupRequests.contains(group.id) // 檢查是否正在更新

                            GroupListItem(
                                group = group,
                                isMember = isMember,
                                isUpdating = isUpdating, // 傳入更新狀態
                                pendingRequest = pendingRequest,
                                onJoinClick = { onJoinGroupClick(group) },
                                onCancelClick = {
                                    pendingRequest?.let { onCancelRequestClick(it) }
                                }
                            )
                        }
                        // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲
                    }
                    Divider()
                    TextButton(
                        onClick = onLeaveOrgClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        Text("退出組織", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
@Composable
private fun GroupListItem(
    group: Group,
    isMember: Boolean,
    isUpdating: Boolean, // 新增參數
    pendingRequest: GroupJoinRequest?,
    onJoinClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            if (isMember) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isMember) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(group.groupName, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${group.memberIds.size} 位成員",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Action Button Area
        Box(contentAlignment = Alignment.Center, modifier = Modifier.sizeIn(minWidth = 120.dp, minHeight = 40.dp)) {
            if (isUpdating) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                when {
                    isMember -> {
                        // 已是成員，不顯示按鈕
                    }
                    pendingRequest != null -> {
                        OutlinedButton(
                            onClick = onCancelClick,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 16.dp)
                        ) {
                            Text("取消申請")
                        }
                    }
                    else -> {
                        Button(onClick = onJoinClick, contentPadding = PaddingValues(horizontal = 16.dp)) {
                            Text("申請加入")
                        }
                    }
                }
            }
        }
    }
}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲


@Composable
private fun UserInfoCard(
    user: stevedaydream.scheduler.data.model.User?,
    uiState: UserProfileUiState,
    onNameChange: (String) -> Unit,
    onEmployeeIdChange: (String) -> Unit,
    onEnableEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSave: () -> Unit
) {
    LaunchedEffect(user) {
        if (user != null && !uiState.isEditing) {
            onNameChange(user.name)
            onEmployeeIdChange(user.employeeId)
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "基本資料",
                    style = MaterialTheme.typography.titleMedium
                )
                if (user != null && !uiState.isEditing) {
                    IconButton(onClick = onEnableEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "編輯資料")
                    }
                }
            }
            Divider()

            if (user == null) {
                Text(
                    text = "尚未完成基本資料設定",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
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

                InfoRow(icon = Icons.Default.Email, label = "Email", value = user.email)

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