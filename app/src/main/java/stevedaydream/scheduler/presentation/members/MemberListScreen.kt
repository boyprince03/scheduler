// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
// scheduler/presentation/members/MemberListScreen.kt
package stevedaydream.scheduler.presentation.members

import androidx.compose.runtime.collectAsState // <<-- 加入這個 import
// ... 其他 imports 保持不變 ...
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import stevedaydream.scheduler.data.model.User
import stevedaydream.scheduler.presentation.common.LoadingIndicator
import stevedaydream.scheduler.presentation.common.StatusChip
import stevedaydream.scheduler.util.showToast
import kotlin.math.abs

// ... (DragDropState 資料類別保持不變) ...
data class DragDropState(
    val isDragging: Boolean = false,
    val dragPosition: Offset = Offset.Zero, // 手指當前位置
    val dragOffset: Offset = Offset.Zero,   // 手指相對於項目左上角的偏移
    val draggedItemIndex: Int? = null,    // 正在拖曳的項目 *當前* 在列表中的索引
    val draggedItemHeight: Float = 0f,   // 被拖曳項目的高度
    val initialDragPosition: Offset = Offset.Zero // 手指初始長按位置
)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberListScreen(
    // 不再需要 orgId 和 groupId 參數
    viewModel: MemberListViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    // 使用 collectAsState
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var userToEdit by remember { mutableStateOf<User?>(null) }
    val canEdit = uiState.currentUser?.role in listOf("org_admin", "superuser")

    // 從 ViewModel 或 SavedStateHandle 取得 orgId (假設 ViewModel 暴露了它)
    // 如果 ViewModel 沒有直接暴露，你可能需要修改 ViewModel 或使用 SavedStateHandle
    // 這裡我們先假設 ViewModel 有 currentOrgId
    val orgId = viewModel.currentOrgId // <<-- 從 ViewModel 取得 orgId

    // ... (拖曳狀態管理 和 LaunchedEffects 保持不變) ...
    var dragDropState by remember { mutableStateOf(DragDropState()) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var overScrollJob by remember { mutableStateOf<Job?>(null) }
    val hapticFeedback = LocalHapticFeedback.current // 取得震動回饋實例
    val density = LocalDensity.current // 用於 DP 轉 PX

    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    LaunchedEffect(uiState.updateResult) {
        uiState.updateResult?.onSuccess {
            context.showToast("狀態更新成功")
            viewModel.clearUpdateResult()
            userToEdit = null
        }?.onFailure {
            context.showToast("更新失敗: ${it.message}")
            viewModel.clearUpdateResult()
        }
    }

    LaunchedEffect(uiState.saveOrderResult) {
        uiState.saveOrderResult?.onSuccess {
            context.showToast("排序已儲存")
            viewModel.clearSaveOrderResult()
        }?.onFailure {
            context.showToast("儲存排序失敗: ${it.message}")
            viewModel.clearSaveOrderResult()
        }
    }

    Scaffold(
        topBar = {
            // ... (TopAppBar 保持不變) ...
            TopAppBar(
                title = { Text(uiState.currentGroup?.groupName ?: "成員管理") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (uiState.sortOption == SortOption.CUSTOM && uiState.hasOrderChanged && canEdit) {
                        IconButton(onClick = { viewModel.saveUserOrder() }) {
                            Icon(Icons.Default.Save, contentDescription = "儲存排序")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            SortOptions(
                // ... (SortOptions 保持不變) ...
                selectedOption = uiState.sortOption,
                onOptionSelected = {
                    if (!dragDropState.isDragging) {
                        viewModel.onSortChange(it)
                    } else {
                        context.showToast("請先完成排序操作")
                    }
                },
                canSelectCustomSort = canEdit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (uiState.isLoading && uiState.orderedMembersInfo.isEmpty()) {
                LoadingIndicator()
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            // --- 拖曳偵測 (使用上一版本的修正邏輯) ---
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    if (uiState.sortOption == SortOption.CUSTOM && canEdit) {
                                        listState.layoutInfo.visibleItemsInfo
                                            .firstOrNull { item -> offset.y.toInt() in item.offset..(item.offset + item.size) }
                                            ?.also { itemInfo ->
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                dragDropState = dragDropState.copy(
                                                    isDragging = true,
                                                    draggedItemIndex = itemInfo.index,
                                                    draggedItemHeight = itemInfo.size.toFloat(),
                                                    initialDragPosition = offset,
                                                    dragOffset = offset - Offset(itemInfo.offset.toFloat(), itemInfo.offset.toFloat()) // 使用修正後的 Y
                                                )
                                            }
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    if (dragDropState.isDragging && dragDropState.draggedItemIndex != null) {
                                        change.consume()
                                        dragDropState = dragDropState.copy(
                                            dragPosition = dragDropState.dragPosition + dragAmount
                                        )

                                        // --- 自動滾動 ---
                                        val viewportStartOffset = listState.layoutInfo.viewportStartOffset
                                        val viewportEndOffset = listState.layoutInfo.viewportEndOffset
                                        val draggedItemInfo = listState.layoutInfo.visibleItemsInfo.find { it.index == dragDropState.draggedItemIndex }
                                        val currentItemVisualOffset = draggedItemInfo?.offset ?: 0
                                        val dragVisualY = currentItemVisualOffset + (dragDropState.dragPosition.y - dragDropState.initialDragPosition.y) + dragDropState.draggedItemHeight / 2f
                                        val scrollThresholdPx = with(density) { 60.dp.toPx() }

                                        overScrollJob?.cancel()

                                        if (dragVisualY > viewportEndOffset - scrollThresholdPx) {
                                            val scrollSpeedFactor = (dragVisualY - (viewportEndOffset - scrollThresholdPx)) / scrollThresholdPx
                                            val scrollAmount = dragAmount.y + abs(dragAmount.y) * scrollSpeedFactor * 2f
                                            overScrollJob = scope.launch { listState.scrollBy(scrollAmount) }
                                        } else if (dragVisualY < viewportStartOffset + scrollThresholdPx) {
                                            val scrollSpeedFactor = ((viewportStartOffset + scrollThresholdPx) - dragVisualY) / scrollThresholdPx
                                            val scrollAmount = dragAmount.y - abs(dragAmount.y) * scrollSpeedFactor * 2f
                                            overScrollJob = scope.launch { listState.scrollBy(scrollAmount) }
                                        }

                                        // --- 項目交換 ---
                                        val currentDraggedItemIndex = dragDropState.draggedItemIndex!!
                                        val currentDraggedItem = listState.layoutInfo.visibleItemsInfo.find { it.index == currentDraggedItemIndex } ?: return@detectDragGesturesAfterLongPress
                                        val dragDisplacementY = dragDropState.dragPosition.y - dragDropState.initialDragPosition.y
                                        val currentItemTopY = currentDraggedItem.offset + dragDisplacementY
                                        val currentItemBottomY = currentItemTopY + currentDraggedItem.size

                                        val targetItem = listState.layoutInfo.visibleItemsInfo.find { otherItem ->
                                            otherItem.index != currentDraggedItemIndex &&
                                                    when {
                                                        dragAmount.y > 0 && otherItem.index > currentDraggedItemIndex ->
                                                            currentItemBottomY >= otherItem.offset + otherItem.size / 2f
                                                        dragAmount.y < 0 && otherItem.index < currentDraggedItemIndex ->
                                                            currentItemTopY <= otherItem.offset + otherItem.size / 2f
                                                        else -> false
                                                    }
                                        }

                                        if (targetItem != null) {
                                            viewModel.moveMemberOrder(currentDraggedItemIndex, targetItem.index)
                                            dragDropState = dragDropState.copy(draggedItemIndex = targetItem.index)
                                        }
                                    }
                                },
                                onDragEnd = {
                                    if (dragDropState.isDragging) {
                                        overScrollJob?.cancel()
                                        dragDropState = DragDropState()
                                        if(uiState.hasOrderChanged) {
                                            // context.showToast("排序已變更，記得儲存")
                                        }
                                    }
                                },
                                onDragCancel = {
                                    if (dragDropState.isDragging) {
                                        overScrollJob?.cancel()
                                        dragDropState = DragDropState()
                                    }
                                }
                            )
                        },
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(uiState.orderedMembersInfo, key = { _, item -> item.user.id }) { index, memberInfo ->
                        val dragDisplacementY = if (index == dragDropState.draggedItemIndex) {
                            dragDropState.dragPosition.y - dragDropState.initialDragPosition.y
                        } else {
                            0f
                        }

                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    translationY = dragDisplacementY
                                    shadowElevation = if (index == dragDropState.draggedItemIndex) 8.dp.toPx() else 0f
                                    alpha = if (index == dragDropState.draggedItemIndex) 0.95f else 1.0f
                                }
                        ) {
                            MemberCard(
                                index = index,
                                sortOption = uiState.sortOption,
                                orgId = orgId, // <<-- 傳入從 ViewModel 取得的 orgId
                                memberInfo = memberInfo,
                                canEditStatus = canEdit,
                                canReorder = canEdit && uiState.sortOption == SortOption.CUSTOM,
                                onEditStatusClick = { userToEdit = memberInfo.user }
                            )
                        }
                    }
                }
            }
        }
    }

    userToEdit?.let { user ->
        EditStatusDialog(
            orgId = orgId, // <<-- 傳入從 ViewModel 取得的 orgId
            user = user,
            onDismiss = { userToEdit = null },
            onConfirm = { newStatus ->
                viewModel.updateUserStatus(user.id, newStatus)
            }
        )
    }
}


// ... (SortOptions, MemberCard, EditStatusDialog 保持不變，但確認它們使用了正確的 orgId) ...
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortOptions(
    selectedOption: SortOption,
    onOptionSelected: (SortOption) -> Unit,
    canSelectCustomSort: Boolean,
    modifier: Modifier = Modifier
) {
    // ... (內容不變) ...
    var expanded by remember { mutableStateOf(false) }
    val options = SortOption.values().filter { it != SortOption.CUSTOM || canSelectCustomSort }

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

@Composable
private fun MemberCard(
    index: Int,
    sortOption: SortOption,
    orgId: String, // <<-- 參數保留
    memberInfo: MemberWithGroupInfo,
    canEditStatus: Boolean,
    canReorder: Boolean,
    onEditStatusClick: () -> Unit
) {
    // ... (內容不變，確認 status 使用了 orgId) ...
    val member = memberInfo.user
    val cardModifier = Modifier.fillMaxWidth()

    Card(
        modifier = cardModifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (sortOption == SortOption.CUSTOM) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = ('A' + index).toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    if (canReorder) {
                        Icon(
                            Icons.Default.DragHandle,
                            contentDescription = "拖曳排序",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(40.dp))
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(memberInfo.user.name, style = MaterialTheme.typography.titleMedium)
                Text(memberInfo.user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Groups, contentDescription = "群組", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                    Text(memberInfo.groupName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
            val status = memberInfo.user.employmentStatus[orgId] ?: "在職" // <<-- 確認使用 orgId
            Box(modifier = Modifier.clickable(enabled = canEditStatus, onClick = onEditStatusClick)) {
                StatusChip(label = status)
            }
        }
    }
}


@Composable
private fun EditStatusDialog(
    orgId: String, // <<-- 參數保留
    user: User,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    // ... (內容不變，確認 currentStatus 使用了 orgId) ...
    val statuses = listOf("active" to "在職", "inactive" to "離職", "leave" to "留職停薪")
    val currentStatus = user.employmentStatus[orgId] ?: "active" // <<-- 確認使用 orgId
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
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲