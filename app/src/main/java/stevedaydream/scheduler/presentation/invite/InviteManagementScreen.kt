package stevedaydream.scheduler.presentation.invite

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import stevedaydream.scheduler.data.model.OrganizationInvite
import stevedaydream.scheduler.util.DateUtils
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import stevedaydream.scheduler.util.QRCodeGenerator
import stevedaydream.scheduler.util.showToast


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteManagementScreen(
    orgId: String,
    viewModel: InviteManagementViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val invites by viewModel.invites.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedInvite by remember { mutableStateOf<OrganizationInvite?>(null) }

    LaunchedEffect(orgId) {
        viewModel.loadInvites(orgId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("邀請碼管理") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "新增邀請碼")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(invites, key = { it.id }) { invite ->
                InviteCard(
                    invite = invite,
                    onShowQRCode = { selectedInvite = it },
                    onCopyCode = {
                        // 複製邀請碼到剪貼簿
                    },
                    onDeactivate = { viewModel.deactivateInvite(orgId, it.id) }
                )
            }
        }
    }

    if (showCreateDialog) {
        CreateInviteDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { inviteType, expiryDays, usageLimit, targetGroupId ->
                viewModel.createInvite(orgId, inviteType, expiryDays, usageLimit, targetGroupId)
                showCreateDialog = false
            }
        )
    }

    selectedInvite?.let { invite ->
        QRCodeDisplayDialog(
            invite = invite,
            onDismiss = { selectedInvite = null }
        )
    }
}

@Composable
fun InviteCard(
    invite: OrganizationInvite,
    onShowQRCode: (OrganizationInvite) -> Unit,
    onCopyCode: (OrganizationInvite) -> Unit,
    onDeactivate: (OrganizationInvite) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (invite.isValid())
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = invite.inviteCode,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = when (invite.inviteType) {
                            "qrcode" -> "QR Code"
                            "email" -> "Email 邀請"
                            else -> "一般邀請"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (invite.isValid()) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "有效",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                } else {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = "已失效",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))

            // 使用資訊
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "已使用: ${invite.usedCount}/${invite.usageLimit ?: "∞"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    invite.expiresAt?.let {
                        Text(
                            text = "到期: ${DateUtils.getDisplayDate(DateUtils.timestampToDateString(it.time))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 操作按鈕
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (invite.inviteType == "qrcode" && invite.isValid()) {
                    OutlinedButton(
                        onClick = { onShowQRCode(invite) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.QrCode, contentDescription = null)
                        Spacer(Modifier.width(width = 4.dp))
//                        Text("顯示QR")
                    }
                }

                OutlinedButton(
                    onClick = { onCopyCode(invite) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
//                    Text("複製")
                }

                if (invite.isValid()) {
                    OutlinedButton(
                        onClick = { onDeactivate(invite) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
//                        Text("停用")
                    }
                }
            }
        }
    }
}

@Composable
fun CreateInviteDialog(
    onDismiss: () -> Unit,
    onCreate: (inviteType: String, expiryDays: Int?, usageLimit: Int?, targetGroupId: String?) -> Unit
) {
    var inviteType by remember { mutableStateOf("general") }
    var expiryDays by remember { mutableStateOf("") }
    var usageLimit by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("建立邀請碼") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("邀請類型", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = inviteType == "general",
                        onClick = { inviteType = "general" }
                    )
                    Text("一般", modifier = Modifier.clickable { inviteType = "general" })
                    Spacer(Modifier.width(16.dp))
                    RadioButton(
                        selected = inviteType == "qrcode",
                        onClick = { inviteType = "qrcode" }
                    )
                    Text("QR Code", modifier = Modifier.clickable { inviteType = "qrcode" })
                }

                OutlinedTextField(
                    value = expiryDays,
                    onValueChange = { expiryDays = it },
                    label = { Text("有效天數 (選填)") },
                    placeholder = { Text("不填表示永久有效") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                OutlinedTextField(
                    value = usageLimit,
                    onValueChange = { usageLimit = it },
                    label = { Text("使用次數限制 (選填)") },
                    placeholder = { Text("不填表示無限制") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(
                        inviteType,
                        expiryDays.toIntOrNull(),
                        usageLimit.toIntOrNull(),
                        null
                    )
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

@Composable
fun QRCodeDisplayDialog(
    invite: OrganizationInvite,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // 生成 QR Code
    val qrBitmap = remember(invite.inviteCode) {
        QRCodeGenerator.generateInviteQRCode(
            inviteCode = invite.inviteCode,
            size = 800
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "邀請 QR Code",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // QR Code 圖片
                if (qrBitmap != null) {
                    Card(
                        modifier = Modifier.size(280.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        ),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        )
                    }
                } else {
                    // QR Code 生成失敗
                    Box(
                        modifier = Modifier
                            .size(280.dp)
                            .background(MaterialTheme.colorScheme.errorContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "QR Code 生成失敗",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                Divider()

                // 邀請碼文字
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "邀請碼",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        invite.inviteCode,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // 使用說明
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "掃描此 QR Code 或輸入邀請碼即可加入組織",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }

                // 分享按鈕
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            // 複製邀請碼到剪貼簿
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("邀請碼", invite.inviteCode)
                            clipboard.setPrimaryClip(clip)
                            context.showToast("邀請碼已複製")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("複製")
                    }

                    OutlinedButton(
                        onClick = {
                            // 分享 QR Code 圖片
                            qrBitmap?.let { bitmap ->
                                shareQRCode(context, bitmap, invite.inviteCode)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("分享")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("關閉")
            }
        }
    )
}

/**
 * 分享 QR Code 圖片
 */
private fun shareQRCode(context: android.content.Context, bitmap: Bitmap, inviteCode: String) {
    try {
        // 儲存圖片到快取
        val cachePath = java.io.File(context.cacheDir, "images")
        cachePath.mkdirs()
        val file = java.io.File(cachePath, "qr_code_$inviteCode.png")
        val fileOutputStream = java.io.FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
        fileOutputStream.close()

        // 建立分享 Intent
        val fileUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(android.content.Intent.EXTRA_STREAM, fileUri)
            putExtra(android.content.Intent.EXTRA_TEXT, "組織邀請碼: $inviteCode")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            android.content.Intent.createChooser(shareIntent, "分享邀請 QR Code")
        )
    } catch (e: Exception) {
        e.printStackTrace()
        context.showToast("分享失敗")
    }
}