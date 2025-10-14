package stevedaydream.scheduler.presentation.organization

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import stevedaydream.scheduler.presentation.navigation.Screen
import stevedaydream.scheduler.util.showToast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizationListScreen(
    navController: NavHostController,
    viewModel: OrganizationListViewModel = hiltViewModel(),
    onOrganizationClick: (String) -> Unit,
    onAdminClick: () -> Unit
) {
    val organizationsInfo by viewModel.organizationsInfo.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val pendingRequests by viewModel.pendingRequests.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val pullToRefreshState = rememberPullToRefreshState()
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.refresh()
        }
    }

    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            pullToRefreshState.endRefresh()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.logoutEvent.collect {
            navController.navigate(Screen.Login.route) {
                popUpTo(navController.graph.startDestinationId) {
                    inclusive = true
                }
            }
        }
    }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的組織") },
                actions = {
                    IconButton(onClick = {
                        navController.navigate(Screen.JoinOrganization.route)
                    }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "加入組織")
                    }

                    val ownedOrgs = organizationsInfo.filter { it.organization.ownerId == currentUser?.id }.map { it.organization }
                    if (ownedOrgs.isNotEmpty() && (currentUser?.role == "org_admin" || currentUser?.role == "superuser")) {
                        BadgedBox(
                            badge = {
                                if (pendingRequests.isNotEmpty()) {
                                    Badge(
                                        Modifier.offset(x = (-16).dp, y = 16.dp)
                                    ) {
                                        Text("${pendingRequests.size}")
                                    }
                                }
                            }
                        ) {
                            IconButton(onClick = {
                                navController.navigate(Screen.ReviewRequests.createRoute(ownedOrgs.first().id))
                            }) {
                                Icon(Icons.Default.AssignmentInd, contentDescription = "審核申請")
                            }
                        }
                    }

                    if (currentUser?.role == "superuser") {
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
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = if (organizationsInfo.isEmpty()) Arrangement.Center else Arrangement.spacedBy(12.dp),
                horizontalAlignment = if (organizationsInfo.isEmpty()) Alignment.CenterHorizontally else Alignment.Start
            ) {
                if (organizationsInfo.isEmpty() && !isRefreshing) {
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

                items(organizationsInfo) { orgInfo ->
                    OrganizationCard(
                        organizationInfo = orgInfo,
                        onClick = { onOrganizationClick(orgInfo.organization.id) }
                    )
                }
            }

            PullToRefreshContainer(
                state = pullToRefreshState,
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
    organizationInfo: OrganizationWithMemberCount,
    onClick: () -> Unit
) {
    val organization = organizationInfo.organization
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

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
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = organization.orgName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "成員: ${organizationInfo.memberCount} 人",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "方案: ${organization.plan}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (organization.orgCode.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable {
                                clipboardManager.setText(AnnotatedString(organization.orgCode))
                                context.showToast("組織代碼已複製")
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "組織代碼: ${organization.orgCode}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "複製組織代碼",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
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