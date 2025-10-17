package stevedaydream.scheduler.presentation.members

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import stevedaydream.scheduler.data.model.User
import stevedaydream.scheduler.presentation.common.LoadingIndicator
import stevedaydream.scheduler.presentation.common.StatusChip
import stevedaydream.scheduler.util.showToast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberListScreen(
    orgId: String,
    viewModel: MemberListViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var userToEdit by remember { mutableStateOf<User?>(null) }

    LaunchedEffect(orgId) {
        viewModel.loadData(orgId)
    }

    LaunchedEffect(uiState.updateResult) {
        uiState.updateResult?.onSuccess {
            context.showToast("狀態更新成功")
            viewModel.clearUpdateResult()
            userToEdit = null // 關閉對話框
        }?.onFailure {
            context.showToast("更新失敗: ${it.message}")
            viewModel.clearUpdateResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("成員管理") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
        Column(modifier = Modifier.padding(padding)) {
            SortOptions(
                selectedOption = uiState.sortOption,
                onOptionSelected = { viewModel.onSortChange(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (uiState.isLoading) {
                LoadingIndicator()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.membersInfo, key = { it.user.id }) { memberInfo ->
                        MemberCard(
                            orgId = orgId,
                            memberInfo = memberInfo,
                            canEdit = uiState.currentUser?.role in listOf("org_admin", "superuser"),
                            onEditClick = { userToEdit = memberInfo.user }
                        )
                    }
                }
            }
        }
        // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲
    }

    userToEdit?.let { user ->
        EditStatusDialog(
            orgId = orgId,
            user = user,
            onDismiss = { userToEdit = null },
            onConfirm = { newStatus ->
                viewModel.updateUserStatus(orgId, user.id, newStatus)
            }
        )
    }
}

// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortOptions(
    selectedOption: SortOption,
    onOptionSelected: (SortOption) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val options = SortOption.values()

    Box(modifier = modifier) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedOption.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("排序依據") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.displayName) },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲

@Composable
private fun MemberCard(
    orgId: String,
    memberInfo: MemberWithGroupInfo,
    canEdit: Boolean,
    onEditClick: () -> Unit
) {
    val member = memberInfo.user
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canEdit, onClick = onEditClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(40.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(member.name, style = MaterialTheme.typography.titleMedium)
                Text(member.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Groups, contentDescription = "群組", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                    Text(memberInfo.groupName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
            val status = member.employmentStatus[orgId] ?: "在職"
            StatusChip(label = status)
        }
    }
}

@Composable
private fun EditStatusDialog(
    orgId: String,
    user: User,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val statuses = listOf("active" to "在職", "inactive" to "離職", "leave" to "留職停薪")
    val currentStatus = user.employmentStatus[orgId] ?: "active"
    var selectedStatus by remember { mutableStateOf(currentStatus) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("編輯 ${user.name} 的狀態") },
        text = {
            Column {
                statuses.forEach { (statusCode, statusName) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedStatus = statusCode }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (selectedStatus == statusCode) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (selectedStatus == statusCode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(statusName)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedStatus) }) {
                Text("儲存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}