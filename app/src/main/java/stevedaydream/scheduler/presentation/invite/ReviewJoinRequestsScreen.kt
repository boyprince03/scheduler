// scheduler/presentation/invite/ReviewJoinRequestsScreen.kt
package stevedaydream.scheduler.presentation.invite

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
import stevedaydream.scheduler.data.model.GroupJoinRequest
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

    // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
    var reviewingRequest by remember { mutableStateOf<ReviewableRequest?>(null) }
    var selectedGroupId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(orgId) {
        viewModel.loadDataForUser(orgId)
    }
    // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲

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
                // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
                val pendingRequests = requests.filter {
                    when (it) {
                        is ReviewableRequest.OrgJoin -> it.request.status == "pending"
                        is ReviewableRequest.GroupJoin -> it.request.status == "pending"
                    }
                }
                if (pendingRequests.isNotEmpty()) {
                    item {
                        Text(
                            "待審核 (${pendingRequests.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(pendingRequests, key = {
                        when (it) {
                            is ReviewableRequest.OrgJoin -> "org-${it.request.id}"
                            is ReviewableRequest.GroupJoin -> "group-${it.request.id}"
                        }
                    }) { request ->
                        when (request) {
                            is ReviewableRequest.OrgJoin -> JoinRequestCard(
                                request = request.request,
                                onReview = {
                                    reviewingRequest = request
                                    selectedGroupId = null
                                }
                            )
                            is ReviewableRequest.GroupJoin -> GroupJoinRequestCard(
                                request = request.request,
                                onReview = { reviewingRequest = request }
                            )
                        }
                    }
                }

                val processedRequests = requests.filter {
                    when (it) {
                        is ReviewableRequest.OrgJoin -> it.request.status != "pending"
                        is ReviewableRequest.GroupJoin -> it.request.status != "pending"
                    }
                }
                if (processedRequests.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "已處理/取消 (${processedRequests.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(processedRequests, key = {
                        when (it) {
                            is ReviewableRequest.OrgJoin -> "proc-org-${it.request.id}"
                            is ReviewableRequest.GroupJoin -> "proc-group-${it.request.id}"
                        }
                    }) { request ->
                        when (request) {
                            is ReviewableRequest.OrgJoin -> ProcessedRequestCard(request.request)
                            is ReviewableRequest.GroupJoin -> ProcessedGroupRequestCard(request.request)
                        }
                    }
                }
                // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲
            }
        }
    }

    // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
    reviewingRequest?.let { request ->
        when (request) {
            is ReviewableRequest.OrgJoin -> OrgReviewDialog(
                request = request.request,
                groupsInOrg = groups.filter { it.orgId == request.request.orgId },
                selectedGroupId = selectedGroupId,
                onGroupIdChange = { selectedGroupId = it },
                onDismiss = { reviewingRequest = null },
                onProcess = { approve ->
                    viewModel.processOrgRequest(request.request, approve, selectedGroupId)
                    reviewingRequest = null
                }
            )
            is ReviewableRequest.GroupJoin -> GroupReviewDialog(
                request = request.request,
                onDismiss = { reviewingRequest = null },
                onProcess = { approve ->
                    viewModel.processGroupRequest(request.request, approve)
                    reviewingRequest = null
                }
            )
        }
    }
    // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲
}

@Composable
private fun OrgReviewDialog(
    request: OrganizationJoinRequest,
    groupsInOrg: List<stevedaydream.scheduler.data.model.Group>,
    selectedGroupId: String?,
    onGroupIdChange: (String?) -> Unit,
    onDismiss: () -> Unit,
    onProcess: (approve: Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("審核組織申請") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("申請人: ${request.userName}", style = MaterialTheme.typography.titleMedium)
                Text("Email: ${request.userEmail}", style = MaterialTheme.typography.bodyMedium)
                if (request.message.isNotEmpty()) {
                    Text("訊息: ${request.message}", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "申請時間: ${DateUtils.timestampToDateString(request.requestedAt.time)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Divider()
                Text("核准後直接加入群組 (選填):", style = MaterialTheme.typography.labelMedium)
                if (groupsInOrg.isEmpty()) {
                    Text(
                        "此組織尚無群組",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    groupsInOrg.forEach { group ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onGroupIdChange(group.id) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedGroupId == group.id, onClick = { onGroupIdChange(group.id) })
                            Spacer(Modifier.width(8.dp))
                            Text(group.groupName)
                        }
                    }
                    TextButton(onClick = { onGroupIdChange(null) }, modifier = Modifier.padding(start = 8.dp)) {
                        Text("不指定群組")
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onProcess(false) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("拒絕")
                }
                Button(onClick = { onProcess(true) }) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("核准")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun GroupReviewDialog(
    request: GroupJoinRequest,
    onDismiss: () -> Unit,
    onProcess: (approve: Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("審核群組申請") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = buildString {
                        append("申請人: ${request.userName}\n")
                        append("想加入: ${request.targetGroupName}")
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    "申請時間: ${DateUtils.timestampToDateString(request.requestedAt.time)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onProcess(false) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("拒絕")
                }
                Button(onClick = { onProcess(true) }) {
                    Text("核准")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
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
private fun GroupJoinRequestCard(
    request: GroupJoinRequest,
    onReview: (GroupJoinRequest) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("群組申請", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                request.userName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "申請加入「${request.targetGroupName}」",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                request.requestedAt.time.toReadableTime(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    val containerColor = when(request.status) {
        "approved" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        "rejected" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        "canceled" -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val icon = when(request.status) {
        "approved" -> Icons.Default.CheckCircle
        "rejected" -> Icons.Default.Cancel
        "canceled" -> Icons.Default.Info
        else -> Icons.Default.Help
    }
    val tintColor = when(request.status) {
        "approved" -> MaterialTheme.colorScheme.primary
        "rejected" -> MaterialTheme.colorScheme.error
        "canceled" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    val statusText = when(request.status) {
        "approved" -> "已核准"
        "rejected" -> "已拒絕"
        "canceled" -> "申請人已取消"
        else -> "未知"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = tintColor
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    request.userName,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                request.processedAt?.time?.toReadableTime() ?: request.requestedAt.time.toReadableTime(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProcessedGroupRequestCard(request: GroupJoinRequest) {
    val containerColor = when(request.status) {
        "approved" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        "rejected" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        "canceled" -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val icon = when(request.status) {
        "approved" -> Icons.Default.CheckCircle
        "rejected" -> Icons.Default.Cancel
        "canceled" -> Icons.Default.Info
        else -> Icons.Default.Help
    }
    val tintColor = when(request.status) {
        "approved" -> MaterialTheme.colorScheme.primary
        "rejected" -> MaterialTheme.colorScheme.error
        "canceled" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    val statusText = when(request.status) {
        "approved" -> "已核准加入 ${request.targetGroupName}"
        "rejected" -> "已拒絕加入 ${request.targetGroupName}"
        "canceled" -> "已取消申請 ${request.targetGroupName}"
        else -> "未知"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = tintColor
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    request.userName,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}