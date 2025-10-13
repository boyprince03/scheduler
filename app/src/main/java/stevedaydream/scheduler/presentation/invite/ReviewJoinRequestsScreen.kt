package stevedaydream.scheduler.presentation.invite

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import stevedaydream.scheduler.util.DateUtils
import stevedaydream.scheduler.util.showToast
import stevedaydream.scheduler.util.toReadableTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewJoinRequestsScreen(
    orgId: String,
    viewModel: ReviewRequestsViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val requests by viewModel.requests.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val context = LocalContext.current

    var reviewingRequest by remember { mutableStateOf<OrganizationJoinRequest?>(null) }
    var selectedGroupId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(orgId) {
        viewModel.loadDataForUser(orgId) // 改成呼叫這個新函式

    }

    LaunchedEffect(viewModel.reviewResult) {
        viewModel.reviewResult.collect { result ->
            result.onSuccess {
                context.showToast("處理完成")
            }.onFailure { error ->
                context.showToast("處理失敗: ${error.message}")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("審核加入申請") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (requests.isEmpty()) {
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
                        Icons.Default.Inbox,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "目前沒有待審核的申請",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                // 待審核的申請
                val pendingRequests = requests.filter { it.status == "pending" }
                if (pendingRequests.isNotEmpty()) {
                    item {
                        Text(
                            "待審核 (${pendingRequests.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(pendingRequests, key = { it.id }) { request ->
                        JoinRequestCard(
                            request = request,
                            onReview = {
                                reviewingRequest = it
                                selectedGroupId = null
                            }
                        )
                    }
                }

                // 已處理的申請
                val processedRequests = requests.filter { it.status != "pending" }
                if (processedRequests.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "已處理 (${processedRequests.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(processedRequests, key = { it.id }) { request ->
                        ProcessedRequestCard(request)
                    }
                }
            }
        }
    }

    // 審核對話框
    reviewingRequest?.let { request ->
        AlertDialog(
            onDismissRequest = { reviewingRequest = null },
            title = { Text("審核申請") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "申請人: ${request.userName}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Email: ${request.userEmail}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (request.message.isNotEmpty()) {
                        Text(
                            "訊息: ${request.message}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        "申請時間: ${DateUtils.timestampToDateString(request.requestedAt.time)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Divider()

                    Text(
                        "指定加入的群組 (選填):",
                        style = MaterialTheme.typography.labelMedium
                    )

                    // 群組選擇
                    // 從 viewModel.groups 中過濾出屬於當前 request 的群組
                    val relevantGroups = groups.filter { it.orgId == request.orgId }

                    if (relevantGroups.isEmpty()) {
                        Text(
                            "此組織尚無群組",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        relevantGroups.forEach { group ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedGroupId = group.id }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedGroupId == group.id,
                                    onClick = { selectedGroupId = group.id }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(group.groupName)
                            }
                        }
                        TextButton(
                            onClick = { selectedGroupId = null },
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text("不指定群組")
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            viewModel.processRequest(
                                orgId = orgId,
                                requestId = request.id,
                                approve = false,
                                targetGroupId = null
                            )
                            reviewingRequest = null
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("拒絕")
                    }
                    Button(
                        onClick = {
                            viewModel.processRequest(
                                orgId = orgId,
                                requestId = request.id,
                                approve = true,
                                targetGroupId = selectedGroupId
                            )
                            reviewingRequest = null
                        }
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("核准")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { reviewingRequest = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun JoinRequestCard(
    request: OrganizationJoinRequest,
    onReview: (OrganizationJoinRequest) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        request.userName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        request.userEmail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (request.message.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            request.message,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        request.requestedAt.time.toReadableTime(), // <-- .time
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        when (request.joinMethod) {
                            "qrcode" -> "QR Code"
                            "email" -> "Email"
                            else -> "邀請碼"
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { onReview(request) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("審核")
            }
        }
    }
}

@Composable
private fun ProcessedRequestCard(request: OrganizationJoinRequest) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (request.status == "approved")
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (request.status == "approved")
                    Icons.Default.CheckCircle
                else
                    Icons.Default.Cancel,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (request.status == "approved")
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    request.userName,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    if (request.status == "approved") "已核准" else "已拒絕",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                request.processedAt?.time?.toReadableTime() ?: "", // <-- .time?
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}