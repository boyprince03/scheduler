@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class) // <--- 加入 ExperimentalLayoutApi
package stevedaydream.scheduler.presentation.common

import androidx.compose.foundation.BorderStroke // <--- 新增
import androidx.compose.foundation.background // <--- 新增
import androidx.compose.foundation.border // <--- 新增
import androidx.compose.foundation.clickable // <--- 新增
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn // <--- 新增
import androidx.compose.foundation.lazy.items // <--- 新增
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip // <--- 新增
import androidx.compose.ui.graphics.Color // <--- 新增
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import stevedaydream.scheduler.data.model.ShiftType // <--- 新增: 導入 ShiftType
import stevedaydream.scheduler.util.toComposeColor // <--- 新增: 導入 toComposeColor

/**
 * 載入指示器
 */
@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    message: String = "載入中..."
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 空白狀態顯示
 */
@Composable
fun EmptyState(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Inbox,
    title: String,
    description: String? = null,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            if (actionLabel != null && onActionClick != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onActionClick) {
                    Text(actionLabel)
                }
            }
        }
    }
}

/**
 * 錯誤狀態顯示
 */
@Composable
fun ErrorState(
    modifier: Modifier = Modifier,
    error: String,
    onRetry: (() -> Unit)? = null
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = "發生錯誤",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            onRetry?.let {
                Button(onClick = it) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("重試")
                }
            }
        }
    }
}

/**
 * 確認對話框
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "確認",
    dismissText: String = "取消",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        }
    )
}

/**
 * 資訊卡片
 */
@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    title: String,
    description: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        onClick = onClick ?: {}
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (onClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, // Use AutoMirrored
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 標籤 Chip
 */
@Composable
fun StatusChip(
    label: String,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = containerColor
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor
        )
    }
}

/**
 * 分隔線帶文字
 */
@Composable
fun DividerWithText(
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ✅ 改用 Divider
        HorizontalDivider(modifier = Modifier.weight(1f)) // Use HorizontalDivider
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(modifier = Modifier.weight(1f)) // Use HorizontalDivider
    }
}

// ▼▼▼▼▼▼▼▼▼▼▼▼ 新增 ShiftLegend ▼▼▼▼▼▼▼▼▼▼▼▼
/**
 * 班別圖例 (用於顯示班別顏色和名稱)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShiftLegend(
    shiftTypes: List<ShiftType>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "班別圖例",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            shiftTypes.forEach { shift ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(shift.color.toComposeColor(), shape = MaterialTheme.shapes.small)
                    )
                    Text(
                        text = "${shift.shortCode}: ${shift.name}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 新增 ShiftLegend ▲▲▲▲▲▲▲▲▲▲▲▲

// ▼▼▼▼▼▼▼▼▼▼▼▼ 新增 ShiftSelectorDialog ▼▼▼▼▼▼▼▼▼▼▼▼
/**
 * 班別選擇對話框 (用於手動排班和互動排班)
 */
@Composable
fun ShiftSelectorDialog(
    shiftTypes: List<ShiftType>,
    currentShiftId: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit // 傳遞班別 ID，空字串表示清除
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("選擇班別") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 班別選項
                shiftTypes.forEach { shift ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(shift.id) }, // 點擊時傳遞 shift.id
                        color = if (shift.id == currentShiftId)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(
                            1.dp,
                            // 處理顏色解析錯誤
                            try { shift.color.toComposeColor() } catch (e: Exception) { MaterialTheme.colorScheme.outline }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                // 處理顏色解析錯誤
                                color = try { shift.color.toComposeColor() } catch (e: Exception) { Color.Gray },
                                shape = MaterialTheme.shapes.small
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = shift.shortCode,
                                        color = Color.White // 假設白色文字永遠可讀
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = shift.name,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "${shift.startTime} - ${shift.endTime}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 清除按鈕
                Spacer(Modifier.height(8.dp))
                HorizontalDivider() // Use HorizontalDivider
                TextButton(
                    onClick = { onSelect("") }, // 點擊時傳遞空字串
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("清除選擇 / 設為未排班")
                }
            }
        },
        confirmButton = {}, // 確認按鈕留空，因為點擊選項即選擇
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 新增 ShiftSelectorDialog ▲▲▲▲▲▲▲▲▲▲▲▲