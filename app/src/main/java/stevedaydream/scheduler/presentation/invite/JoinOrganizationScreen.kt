package stevedaydream.scheduler.presentation.invite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import stevedaydream.scheduler.data.model.OrganizationJoinRequest
import stevedaydream.scheduler.presentation.common.DividerWithText
import stevedaydream.scheduler.presentation.navigation.Screen
import stevedaydream.scheduler.util.showToast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinOrganizationScreen(
    navController: androidx.navigation.NavController,
    viewModel: JoinOrganizationViewModel = hiltViewModel(),
    onJoinSuccess: () -> Unit,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var inviteCode by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    LaunchedEffect(uiState.joinResult) {
        uiState.joinResult?.onSuccess {
            context.showToast("申請已送出,請等候審核")
            onJoinSuccess()
        }?.onFailure { error ->
            context.showToast("申請失敗: ${error.message}")
        }
    }
    // 處理掃描結果
    LaunchedEffect(Unit) {
        navController.currentBackStackEntry
            ?.savedStateHandle
            ?.getStateFlow<String?>("scanned_code", null)
            ?.collect { code ->
                if (code != null) {
                    inviteCode = code
                    viewModel.searchByInviteCode(code)
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.remove<String>("scanned_code")
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("加入組織") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "加入方式",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // 方式一: 掃描 QR Code
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { navController.navigate(Screen.QRScanner.route) }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            "掃描 QR Code",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "使用相機掃描組織提供的 QR Code",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            DividerWithText("或")

            // 方式二: 輸入邀請碼
            OutlinedTextField(
                value = inviteCode,
                onValueChange = {
                    inviteCode = it.uppercase()
                    if (it.length == 8) {
                        viewModel.searchByInviteCode(it.uppercase())
                    }
                },
                label = { Text("輸入邀請碼") },
                placeholder = { Text("8位邀請碼") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    if (inviteCode.isNotEmpty()) {
                        IconButton(onClick = { inviteCode = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "清除")
                        }
                    }
                }
            )

            // 顯示查詢到的組織資訊
            if (uiState.foundOrganization != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "找到組織!",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        Divider()
                        Spacer(Modifier.height(8.dp))

                        Text(
                            uiState.foundOrganization!!.displayName.ifEmpty {
                                uiState.foundOrganization!!.orgName
                            },
                            style = MaterialTheme.typography.headlineSmall
                        )
                        if (uiState.foundOrganization!!.location.isNotEmpty()) {
                            Text(
                                "地點: ${uiState.foundOrganization!!.location}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "組織代碼: ${uiState.foundOrganization!!.orgCode}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = message,
                            onValueChange = { message = it },
                            label = { Text("申請訊息 (選填)") },
                            placeholder = { Text("說明您的加入原因") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )

                        Spacer(Modifier.height(12.dp))

                        Button(
                            onClick = {
                                viewModel.submitJoinRequest(
                                    inviteCode = inviteCode,
                                    message = message
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isLoading
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("送出申請")
                        }
                    }
                }
            } else if (inviteCode.length == 8 && !uiState.isLoading) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            "找不到此邀請碼,請確認後重試",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // 顯示待審核的申請
            if (uiState.pendingRequests.isNotEmpty()) {
                Divider()
                Text(
                    "您的申請記錄",
                    style = MaterialTheme.typography.titleMedium
                )
                uiState.pendingRequests.forEach { request ->
                    PendingRequestCard(request)
                }
            }
        }
    }
}

@Composable
private fun PendingRequestCard(request: OrganizationJoinRequest) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (request.status) {
                "pending" -> MaterialTheme.colorScheme.secondaryContainer
                "approved" -> MaterialTheme.colorScheme.primaryContainer
                "rejected" -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (request.status) {
                    "pending" -> Icons.Default.Schedule
                    "approved" -> Icons.Default.CheckCircle
                    "rejected" -> Icons.Default.Cancel
                    else -> Icons.Default.Help
                },
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = when (request.status) {
                    "pending" -> MaterialTheme.colorScheme.secondary
                    "approved" -> MaterialTheme.colorScheme.primary
                    "rejected" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    request.orgName,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    when (request.status) {
                        "pending" -> "審核中"
                        "approved" -> "已核准"
                        "rejected" -> "已拒絕"
                        else -> "未知狀態"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}