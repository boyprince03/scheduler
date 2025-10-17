// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
package stevedaydream.scheduler.presentation.admin

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
import kotlinx.coroutines.flow.collectLatest
import stevedaydream.scheduler.data.model.Organization
import stevedaydream.scheduler.presentation.common.ConfirmDialog
import stevedaydream.scheduler.presentation.common.DividerWithText
import stevedaydream.scheduler.util.showToast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    viewModel: AdminViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    var orgToDelete by remember { mutableStateOf<Organization?>(null) }
    var showDeleteAllDialog by remember { mutableStateOf(false) } // 新增：控制刪除全部對話框的狀態
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.generationState.collectLatest { result ->
            result.onSuccess {
                context.showToast("完整情境建立成功！")
            }.onFailure { error ->
                context.showToast("建立失敗: ${error.message}")
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.deleteState.collectLatest { result ->
            result.onSuccess {
                context.showToast("組織已刪除")
                orgToDelete = null
            }.onFailure { error ->
                context.showToast("刪除失敗: ${error.message}")
            }
        }
    }

    // 新增：監聽全部刪除的結果
    LaunchedEffect(Unit) {
        viewModel.deleteAllState.collectLatest { result ->
            result.onSuccess { count ->
                context.showToast("成功刪除 $count 個測試組織")
            }.onFailure { error ->
                context.showToast("刪除失敗: ${error.message}")
            }
            showDeleteAllDialog = false
        }
    }


    // 監聽單元測試結果
    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { message ->
            context.showToast(message)
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("超級管理員儀表板") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                FullScenarioGenerator()
            }

            item {
                DividerWithText("或")
            }

            item {
                UnitTestSection(
                    organizationsInfo = uiState.organizationsInfo,
                    onGenerateOrgAndGroup = { orgName, groupName ->
                        viewModel.createOrgAndGroup(orgName, groupName)
                    },
                    onGenerateUser = { orgId, groupId, name, email ->
                        viewModel.createUserAndAssign(orgId, groupId, name, email)
                    },
                    onAssignLeave = { orgId ->
                        viewModel.assignRandomLeave(orgId)
                    }
                )
            }


            item {
                Divider(modifier = Modifier.padding(vertical = 16.dp))
            }

            item {
                Text(
                    "管理現有組織",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (uiState.isLoading) {
                item { CircularProgressIndicator(modifier = Modifier.wrapContentSize(Alignment.Center)) }
            } else {
                items(uiState.organizationsInfo, key = { it.organization.id }) { orgInfo ->
                    OrganizationManagementCard(
                        organizationInfo = orgInfo,
                        onDeleteClick = { orgToDelete = orgInfo.organization }
                    )
                }
            }

            // 新增：危險區域
            item {
                DangerZoneCard(onDeleteAllClick = { showDeleteAllDialog = true })
            }
        }
    }

    orgToDelete?.let { org ->
        ConfirmDialog(
            title = "確認刪除",
            message = "您確定要永久刪除「${org.orgName}」嗎？此操作無法復原，將會刪除所有相關資料。",
            confirmText = "確認刪除",
            onConfirm = {
                viewModel.deleteOrganization(org.id)
                orgToDelete = null
            },
            onDismiss = { orgToDelete = null }
        )
    }

    // 新增：刪除所有測試資料的確認對話框
    if (showDeleteAllDialog) {
        ConfirmDialog(
            title = "⚠️ 確認刪除所有測試資料",
            message = "您確定要永久刪除所有由測試功能建立的組織嗎？此操作無法復原。",
            confirmText = "全部刪除",
            onConfirm = {
                viewModel.deleteAllTestData()
            },
            onDismiss = { showDeleteAllDialog = false }
        )
    }
}

@Composable
private fun FullScenarioGenerator(
    viewModel: AdminViewModel = hiltViewModel()
) {
    var orgName by remember { mutableStateOf("完整模擬公司") }
    var testMemberEmail by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.generationState.collect {
            isGenerating = false
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "1. 完整情境模擬",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                "點擊按鈕將會建立一組完整的組織資料，包含人力規劃、預約班表、排班草稿等。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = orgName,
                onValueChange = { orgName = it },
                label = { Text("新組織的名稱") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = testMemberEmail,
                onValueChange = { testMemberEmail = it },
                label = { Text("測試成員 Email (選填)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                onClick = {
                    isGenerating = true
                    viewModel.createTestData(orgName, testMemberEmail)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isGenerating
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("生成中...")
                } else {
                    Icon(Icons.Default.Build, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("一鍵生成完整情境")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitTestSection(
    organizationsInfo: List<OrganizationAdminInfo>,
    onGenerateOrgAndGroup: (String, String) -> Unit,
    onGenerateUser: (String, String?, String, String) -> Unit,
    onAssignLeave: (String) -> Unit
) {
    var newOrgName by remember { mutableStateOf("單元測試組織") }
    var newGroupName by remember { mutableStateOf("測試部門") }
    var newUserName by remember { mutableStateOf("測試人員") }
    var newUserEmail by remember { mutableStateOf("test@example.com") }

    var selectedOrg by remember { mutableStateOf<OrganizationAdminInfo?>(null) }
    var selectedGroup by remember { mutableStateOf<stevedaydream.scheduler.data.model.Group?>(null) }
    var isOrgDropdownExpanded by remember { mutableStateOf(false) }
    var isGroupDropdownExpanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("2. 單元測試功能", style = MaterialTheme.typography.headlineSmall)

            // 2-1: 生成組織和群組
            Divider()
            Text("2-1: 生成組織和群組", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = newOrgName, onValueChange = { newOrgName = it }, label = { Text("組織名稱") })
            OutlinedTextField(value = newGroupName, onValueChange = { newGroupName = it }, label = { Text("群組名稱") })
            Button(onClick = { onGenerateOrgAndGroup(newOrgName, newGroupName) }) { Text("執行 2-1") }

            // 2-2: 生成使用者
            Divider()
            Text("2-2: 生成使用者並指派", style = MaterialTheme.typography.titleMedium)
            ExposedDropdownMenuBox(expanded = isOrgDropdownExpanded, onExpandedChange = { isOrgDropdownExpanded = !isOrgDropdownExpanded }) {
                OutlinedTextField(
                    value = selectedOrg?.organization?.orgName ?: "選擇組織",
                    onValueChange = {}, readOnly = true,
                    modifier = Modifier.menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isOrgDropdownExpanded) }
                )
                ExposedDropdownMenu(expanded = isOrgDropdownExpanded, onDismissRequest = { isOrgDropdownExpanded = false }) {
                    organizationsInfo.forEach { orgInfo ->
                        DropdownMenuItem(text = { Text(orgInfo.organization.orgName) }, onClick = {
                            selectedOrg = orgInfo
                            selectedGroup = null
                            isOrgDropdownExpanded = false
                        })
                    }
                }
            }
            ExposedDropdownMenuBox(expanded = isGroupDropdownExpanded, onExpandedChange = { isGroupDropdownExpanded = !isGroupDropdownExpanded }) {
                OutlinedTextField(
                    value = selectedGroup?.groupName ?: "選擇群組 (選填)",
                    onValueChange = {}, readOnly = true,
                    modifier = Modifier.menuAnchor(),
                    enabled = selectedOrg != null,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isGroupDropdownExpanded) }
                )
                ExposedDropdownMenu(expanded = isGroupDropdownExpanded, onDismissRequest = { isGroupDropdownExpanded = false }) {
                    selectedOrg?.groups?.forEach { group ->
                        DropdownMenuItem(text = { Text(group.groupName) }, onClick = {
                            selectedGroup = group
                            isGroupDropdownExpanded = false
                        })
                    }
                }
            }
            OutlinedTextField(value = newUserName, onValueChange = { newUserName = it }, label = { Text("使用者名稱") })
            OutlinedTextField(value = newUserEmail, onValueChange = { newUserEmail = it }, label = { Text("使用者 Email") })
            Button(onClick = {
                selectedOrg?.let { org ->
                    onGenerateUser(org.organization.id, selectedGroup?.id, newUserName, newUserEmail)
                }
            }, enabled = selectedOrg != null) { Text("執行 2-2") }

            // 2-3: 指定預假
            Divider()
            Text("2-3: 為群組內人員指定預假", style = MaterialTheme.typography.titleMedium)
            Text("為「${selectedOrg?.organization?.orgName ?: "所選組織"}」的所有群組成員隨機預約 5 天假。", style = MaterialTheme.typography.bodySmall)
            Button(onClick = {
                selectedOrg?.let { onAssignLeave(it.organization.id) }
            }, enabled = selectedOrg != null) { Text("執行 2-3") }
        }
    }
}

@Composable
private fun OrganizationManagementCard(
    organizationInfo: OrganizationAdminInfo,
    onDeleteClick: () -> Unit
) {
    val organization = organizationInfo.organization
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(organization.orgName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "成員數: ${organizationInfo.memberCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "ID: ${organization.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Default.Delete, contentDescription = "刪除組織", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun DangerZoneCard(onDeleteAllClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "危險區域",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                "此區塊的操作將會永久刪除大量資料且無法復原，請謹慎使用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onDeleteAllClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("一鍵刪除所有測試資料")
            }
        }
    }
}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲