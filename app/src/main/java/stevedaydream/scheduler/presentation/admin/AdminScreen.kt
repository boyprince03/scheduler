// scheduler/presentation/admin/AdminScreen.kt
package stevedaydream.scheduler.presentation.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Delete
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
import stevedaydream.scheduler.util.showToast

// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    viewModel: AdminViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    var orgName by remember { mutableStateOf("自動生成測試公司") }
    var testMemberEmail by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var orgToDelete by remember { mutableStateOf<Organization?>(null) }
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.generationState.collectLatest { result ->
            isGenerating = false
            result.onSuccess {
                context.showToast("測試資料建立成功！")
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


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("超級管理員儀表板") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                GeneratorSection(
                    orgName = orgName,
                    onOrgNameChange = { orgName = it },
                    testMemberEmail = testMemberEmail,
                    onTestMemberEmailChange = { testMemberEmail = it },
                    isGenerating = isGenerating,
                    onGenerateClick = {
                        isGenerating = true
                        viewModel.createTestData(orgName, testMemberEmail)
                    }
                )
            }

            item {
                Divider(modifier = Modifier.padding(vertical = 16.dp))
            }

            item {
                Text(
                    "管理現有組織",
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            if (uiState.isLoading) {
                item { CircularProgressIndicator() }
            } else {
                items(uiState.organizationsInfo, key = { it.organization.id }) { orgInfo ->
                    OrganizationManagementCard(
                        organizationInfo = orgInfo,
                        onDeleteClick = { orgToDelete = orgInfo.organization }
                    )
                }
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
}

@Composable
private fun GeneratorSection(
    orgName: String,
    onOrgNameChange: (String) -> Unit,
    testMemberEmail: String,
    onTestMemberEmailChange: (String) -> Unit,
    isGenerating: Boolean,
    onGenerateClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "測試資料生成器",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            "點擊按鈕將會建立一組完整的組織資料，包含使用者、群組、班別、排班表等。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = orgName,
            onValueChange = onOrgNameChange,
            label = { Text("新組織的名稱") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = testMemberEmail,
            onValueChange = onTestMemberEmailChange,
            label = { Text("測試成員 Email (選填)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onGenerateClick,
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
                Icon(Icons.Default.AddCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("一鍵生成測試資料")
            }
        }
    }
}

@Composable
private fun OrganizationManagementCard(
    organizationInfo: OrganizationAdminInfo, // 已更新為接收 OrganizationAdminInfo
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
                // 顯示組織名稱
                Text(organization.orgName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                // 顯示成員數
                Text(
                    "成員數: ${organizationInfo.memberCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                // 顯示組織 ID
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