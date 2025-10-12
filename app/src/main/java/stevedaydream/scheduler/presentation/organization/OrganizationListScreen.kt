package stevedaydream.scheduler.presentation.organization

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Logout // ✅ 引入圖示
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController // ✅ 引入 NavController
import androidx.navigation.compose.rememberNavController // ✅ 引入 rememberNavController
import stevedaydream.scheduler.data.model.Organization
import stevedaydream.scheduler.presentation.navigation.Screen // ✅ 引入 Screen

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun OrganizationListScreen(
    navController: NavHostController, // ✅ 傳入 NavController
    viewModel: OrganizationListViewModel = hiltViewModel(),
    onOrganizationClick: (String) -> Unit,
    onAdminClick: () -> Unit
) {
    val organizations by viewModel.organizations.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) } // ✅ 新增狀態來控制登出對話框

    LaunchedEffect(Unit) {
        viewModel.loadOrganizations()
    }
    val pullRefreshState = rememberPullRefreshState(isRefreshing, { viewModel.refresh() })

    // ✅ 監聽登出事件
    LaunchedEffect(Unit) {
        viewModel.logoutEvent.collect {
            // 登出成功後導航至登入頁面，並清除所有舊的頁面堆疊
            navController.navigate(Screen.Login.route) {
                popUpTo(navController.graph.startDestinationId) {
                    inclusive = true
                }
            }
        }
    }

    // ✅ 顯示登出確認對話框
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("確認登出") },
            text = { Text("您確定要登出嗎？這將會清除所有本地快取資料。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout()
                    }
                ) {
                    Text("登出")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 假設 আপনি從 ViewModel 獲取了當前使用者的資訊
    val currentUser by viewModel.currentUser.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的組織") },
                actions = {
                    // 加入組織按鈕
                    IconButton(onClick = {
                        navController.navigate(Screen.JoinOrganization.route)
                    }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "加入組織")
                    }

                    // 審核申請按鈕 (僅組織管理員)
                    if (currentUser?.role == "org_admin" && currentUser?.orgId?.isNotEmpty() == true) {
                        IconButton(onClick = {
                            navController.navigate(Screen.ReviewRequests.createRoute(currentUser.orgId))
                        }) {
                            Icon(Icons.Default.AssignmentInd, contentDescription = "審核申請")
                        }
                    }
                    if (currentUser?.role == "superuser") {
                        // ✅ 2. 修改 onClick，呼叫新的導航事件
                        IconButton(onClick = onAdminClick) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = "管理後台"
                            )
                        }
                    }
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "登出"
                        )
                    }
                    // 新增個人資料頁面入口
                    IconButton(onClick = { navController.navigate(Screen.UserProfile.route) }) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "個人資料"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = "建立組織")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pullRefresh(pullRefreshState)
        ) {
            // ✅ 我們不再使用 if/else 來切換元件
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = if (organizations.isEmpty()) Arrangement.Center else Arrangement.spacedBy(12.dp),
                horizontalAlignment = if (organizations.isEmpty()) Alignment.CenterHorizontally else Alignment.Start
            ) {
                // 如果列表是空的，就在 LazyColumn 內部顯示一個 item 作為空狀態
                if (organizations.isEmpty() && !isRefreshing) {
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Business,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "尚未加入任何組織",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "點擊右下角按鈕建立新組織",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 正常顯示組織列表
                items(organizations) { org ->
                    OrganizationCard(
                        organization = org,
                        onClick = { onOrganizationClick(org.id) }
                    )
                }
            }

            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }

    if (showCreateDialog) {
        CreateOrganizationDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { orgName ->
                viewModel.createOrganization(orgName)
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun OrganizationCard(
    organization: Organization,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Business,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = organization.orgName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "方案: ${organization.plan}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CreateOrganizationDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var orgName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("建立新組織") },
        text = {
            OutlinedTextField(
                value = orgName,
                onValueChange = { orgName = it },
                label = { Text("組織名稱") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (orgName.isNotBlank()) {
                        onCreate(orgName.trim())
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