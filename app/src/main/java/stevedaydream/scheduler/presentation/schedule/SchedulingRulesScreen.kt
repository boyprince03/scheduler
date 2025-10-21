// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
package stevedaydream.scheduler.presentation.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.filled.* // 移除舊的
import androidx.compose.material.icons.automirrored.filled.ArrowBack // 使用 AutoMirrored
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import stevedaydream.scheduler.data.model.RotationSetting
import stevedaydream.scheduler.data.model.SchedulingRule
import stevedaydream.scheduler.data.model.ShiftType
import stevedaydream.scheduler.presentation.common.ConfirmDialog
import stevedaydream.scheduler.presentation.common.DividerWithText
import stevedaydream.scheduler.presentation.common.LoadingIndicator
import stevedaydream.scheduler.util.showToast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulingRulesScreen(
    orgId: String,
    groupId: String,
    viewModel: SchedulingRulesViewModel = hiltViewModel(), // 確保注入
    onBackClick: () -> Unit
) {
    // ✅ 使用 collectAsState() 正確收集狀態
    val uiState by viewModel.uiState.collectAsState()
    val currentUser = uiState.currentUser
    val isSuperuser = currentUser?.role == "superuser"
    // ✅ 從 viewModel 呼叫 canModifyRotationSettings()
    val canModifyRotation = viewModel.canModifyRotationSettings()
    val context = LocalContext.current

    var showAddCustomRuleDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<SchedulingRule?>(null) }
    var deletingRule by remember { mutableStateOf<SchedulingRule?>(null) }
    var showAddTemplateDialog by remember { mutableStateOf(false) }
    var showEditRotationDialog by remember { mutableStateOf(false) }
    var editingRotationShiftType by remember { mutableStateOf<ShiftType?>(null) }


    LaunchedEffect(orgId, groupId) {
        viewModel.loadData(orgId, groupId) // ✅ 呼叫 viewModel 的方法
    }

    LaunchedEffect(uiState.saveRotationResult) { // ✅ 監聽 uiState 的屬性
        uiState.saveRotationResult?.onSuccess {
            context.showToast("輪替規則已儲存")
            viewModel.clearSaveRotationResult() // ✅ 呼叫 viewModel 的方法
        }?.onFailure {
            context.showToast("儲存失敗: ${it.message}")
            viewModel.clearSaveRotationResult() // ✅ 呼叫 viewModel 的方法
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("排班規則儀表板") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        // ✅ 使用 AutoMirrored 圖示
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (canModifyRotation) {
                        TextButton(
                            onClick = { viewModel.saveRotationSettings() }, // ✅ 呼叫 viewModel 的方法
                            enabled = !uiState.isLoading // ✅ 監聽 uiState 的屬性
                        ) {
                            Text("儲存輪替")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            // ✅ 從 viewModel 呼叫 canAddCustomRules()
            if (viewModel.canAddCustomRules()) {
                FloatingActionButton(onClick = { showAddCustomRuleDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "新增自訂規則")
                }
            }
        }
    ) { padding ->
        if (uiState.isLoading) { // ✅ 監聽 uiState 的屬性
            LoadingIndicator(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (canModifyRotation) {
                    item {
                        RotationSettingsCard(
                            shiftTypes = uiState.availableShiftTypes, // ✅ 監聽 uiState 的屬性
                            rotationSettings = uiState.rotationSettings?.rules ?: emptyMap(), // ✅ 監聽 uiState 的屬性
                            onEditClick = { shiftType ->
                                editingRotationShiftType = shiftType
                                showEditRotationDialog = true
                            },
                            onRemoveClick = { shiftTypeId ->
                                viewModel.removeRotationSetting(shiftTypeId) // ✅ 呼叫 viewModel 的方法
                            }
                        )
                    }
                }

                item { Spacer(Modifier.height(8.dp)); DividerWithText("一般排班規則") }
                if (uiState.organizationRules.isEmpty()) { // ✅ 監聽 uiState 的屬性
                    item { EmptyRuleState("您的組織尚未啟用任何範本規則") }
                } else {
                    // ✅ 修正 items 的 key 和 lambda 參數
                    items(items = uiState.organizationRules, key = { rule -> rule.id }) { rule: SchedulingRule ->
                        RuleCard(
                            rule = rule,
                            canToggle = isSuperuser || currentUser?.role == "org_admin",
                            canEdit = false,
                            canDelete = false,
                            onToggle = { isEnabled -> viewModel.toggleRule(rule, isEnabled) }, // ✅ 呼叫 viewModel 的方法
                            onEditClick = {},
                            onDeleteClick = {}
                        )
                    }
                }

                item { Spacer(Modifier.height(16.dp)); DividerWithText("群組自訂規則") }
                if (uiState.groupCustomRules.isEmpty()) { // ✅ 監聽 uiState 的屬性
                    item { EmptyRuleState("此群組尚無自訂規則，點擊右下角新增") }
                } else {
                    // ✅ 修正 items 的 key 和 lambda 參數
                    items(items = uiState.groupCustomRules, key = { rule -> rule.id }) { rule: SchedulingRule ->
                        val canModify = rule.createdBy == currentUser?.id || isSuperuser
                        RuleCard(
                            rule = rule,
                            canToggle = canModify,
                            canEdit = canModify,
                            canDelete = canModify,
                            onToggle = { isEnabled -> viewModel.toggleRule(rule, isEnabled) }, // ✅ 呼叫 viewModel 的方法
                            onEditClick = { editingRule = rule },
                            onDeleteClick = { deletingRule = rule }
                        )
                    }
                }

                if (isSuperuser) {
                    item {
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DividerWithText("超級管理員：規則範本庫", modifier = Modifier.weight(1f))
                            IconButton(onClick = { showAddTemplateDialog = true }) {
                                Icon(Icons.Default.AddCircle, contentDescription = "新增範本")
                            }
                        }
                    }
                    if (uiState.ruleTemplates.isEmpty()) { // ✅ 監聽 uiState 的屬性
                        item { EmptyRuleState("範本庫是空的") }
                    } else {
                        // ✅ 修正 items 的 key 和 lambda 參數
                        items(items = uiState.ruleTemplates, key = { template -> "template-${template.id}" }) { template: SchedulingRule ->
                            RuleCard(
                                rule = template,
                                canToggle = false,
                                canEdit = true,
                                canDelete = true,
                                onToggle = {},
                                onEditClick = { editingRule = template },
                                onDeleteClick = { deletingRule = template }
                            )
                        }
                    }
                }
            }
        }
    }

    // --- Dialog implementations ---
    if (showAddCustomRuleDialog) {
        EditRuleDialog(
            title = "新增自訂規則",
            onDismiss = { showAddCustomRuleDialog = false },
            onSave = { rule ->
                viewModel.addCustomRule(rule) // ✅ 呼叫 viewModel 的方法
                showAddCustomRuleDialog = false
            }
        )
    }

    if (showAddTemplateDialog) {
        EditRuleDialog(
            title = "新增規則範本",
            onDismiss = { showAddTemplateDialog = false },
            onSave = { rule ->
                viewModel.addRuleTemplate(rule) // ✅ 呼叫 viewModel 的方法
                showAddTemplateDialog = false
            }
        )
    }

    editingRule?.let { ruleToEdit ->
        EditRuleDialog(
            title = "編輯規則",
            rule = ruleToEdit,
            onDismiss = { editingRule = null },
            onSave = { updatedRule ->
                if (updatedRule.isTemplate) {
                    viewModel.updateRuleTemplate(updatedRule) // ✅ 呼叫 viewModel 的方法
                } else {
                    viewModel.updateCustomRule(updatedRule) // ✅ 呼叫 viewModel 的方法
                }
                editingRule = null
            }
        )
    }

    deletingRule?.let { ruleToDelete ->
        ConfirmDialog(
            title = "確認刪除",
            message = "您確定要刪除規則「${ruleToDelete.ruleName}」嗎？此操作無法復原。",
            onConfirm = {
                if (ruleToDelete.isTemplate) {
                    viewModel.deleteRuleTemplate(ruleToDelete.id) // ✅ 呼叫 viewModel 的方法
                } else {
                    viewModel.deleteRule(ruleToDelete) // ✅ 呼叫 viewModel 的方法
                }
                deletingRule = null
            },
            onDismiss = { deletingRule = null }
        )
    }

    if (showEditRotationDialog && editingRotationShiftType != null) {
        val currentSetting = uiState.rotationSettings?.rules?.get(editingRotationShiftType!!.id) // ✅ 監聽 uiState 的屬性
        EditRotationSettingDialog(
            shiftType = editingRotationShiftType!!,
            initialSetting = currentSetting,
            onDismiss = { showEditRotationDialog = false },
            onSave = { newSetting ->
                viewModel.updateRotationSetting(editingRotationShiftType!!.id, newSetting) // ✅ 呼叫 viewModel 的方法
                showEditRotationDialog = false
            }
        )
    }
}

// --- RotationSettingsCard (保持不變) ---
@Composable
fun RotationSettingsCard(
    shiftTypes: List<ShiftType>,
    rotationSettings: Map<String, RotationSetting>,
    onEditClick: (ShiftType) -> Unit,
    onRemoveClick: (ShiftTypeId: String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("輪替班別設定", style = MaterialTheme.typography.titleLarge)
            Text(
                "設定需要按成員順序輪流排班的班別及日期。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) // ✅ 使用 HorizontalDivider

            if (rotationSettings.isNotEmpty()) {
                rotationSettings.forEach { (shiftTypeId, setting) ->
                    val shiftType = shiftTypes.find { it.id == shiftTypeId }
                    if (shiftType != null) {
                        RotationRuleItem(
                            shiftType = shiftType,
                            setting = setting,
                            onEditClick = { onEditClick(shiftType) },
                            onRemoveClick = { onRemoveClick(shiftTypeId) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            var showAddMenu by remember { mutableStateOf(false) }
            val availableToAdd = shiftTypes.filter { it.id !in rotationSettings.keys }

            Box {
                OutlinedButton(
                    onClick = { showAddMenu = true },
                    enabled = availableToAdd.isNotEmpty()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("新增輪替班別")
                }
                DropdownMenu(
                    expanded = showAddMenu,
                    onDismissRequest = { showAddMenu = false }
                ) {
                    if (availableToAdd.isEmpty()) {
                        DropdownMenuItem(text = { Text("無可用班別") }, onClick = { showAddMenu = false })
                    } else {
                        availableToAdd.forEach { shift ->
                            DropdownMenuItem(
                                text = { Text(shift.name) },
                                onClick = {
                                    onEditClick(shift)
                                    showAddMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- RotationRuleItem (保持不變) ---
@Composable
private fun RotationRuleItem(
    shiftType: ShiftType,
    setting: RotationSetting,
    onEditClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    // ... (保持不變) ...
    val weekDays = listOf("日", "一", "二", "三", "四", "五", "六")
    val daysText = setting.daysOfWeek.sorted().joinToString(", ") { weekDays[it] }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(shiftType.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text("每週 $daysText", style = MaterialTheme.typography.bodyMedium)
        IconButton(onClick = onEditClick) {
            Icon(Icons.Default.Edit, contentDescription = "編輯", tint = MaterialTheme.colorScheme.secondary)
        }
        IconButton(onClick = onRemoveClick) {
            Icon(Icons.Default.Delete, contentDescription = "移除", tint = MaterialTheme.colorScheme.error)
        }
    }
}


// --- 新增：編輯輪替規則的 Dialog ---
@Composable
private fun EditRotationSettingDialog(
    shiftType: ShiftType,
    initialSetting: RotationSetting?,
    onDismiss: () -> Unit,
    onSave: (RotationSetting) -> Unit
) {
    var selectedDays by remember { mutableStateOf(initialSetting?.daysOfWeek ?: emptySet()) }
    var nextStartIndex by remember { mutableStateOf(initialSetting?.nextStartIndex?.toString() ?: "0") }
    // 星期列表保持不變
    val weekDays = listOf("週日", "週一", "週二", "週三", "週四", "週五", "週六")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("設定 ${shiftType.name} 輪替規則") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("選擇輪替的星期:", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround // 平均分布
                ) {
                    weekDays.forEachIndexed { index, dayName ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // ▼▼▼▼▼▼▼▼▼▼▼▼ 修正點 ▼▼▼▼▼▼▼▼▼▼▼▼
                            // 將 drop(1) 改為 takeLast(1) 以確保取得最後一個字元
                            Text(dayName.takeLast(1), style = MaterialTheme.typography.bodySmall)
                            // ▲▲▲▲▲▲▲▲▲▲▲▲ 修正結束 ▲▲▲▲▲▲▲▲▲▲▲▲
                            Checkbox(
                                checked = index in selectedDays,
                                onCheckedChange = { isChecked ->
                                    selectedDays = if (isChecked) {
                                        selectedDays + index
                                    } else {
                                        selectedDays - index
                                    }
                                }
                            )
                        }
                    }
                }
                Divider()
                OutlinedTextField(
                    value = nextStartIndex,
                    onValueChange = { nextStartIndex = it.filter { char -> char.isDigit() } }, // 只允許數字
                    label = { Text("下次起始成員序號 (0=${'A'}, 1=${'B'}...)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalSetting = RotationSetting(
                        daysOfWeek = selectedDays,
                        nextStartIndex = nextStartIndex.toIntOrNull()?.coerceAtLeast(0) ?: 0 // 確保是正整數
                    )
                    onSave(finalSetting)
                },
                enabled = selectedDays.isNotEmpty() // 必須至少選一天
            ) {
                Text("儲存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}


// --- 其他 Composable (RuleCard, EditRuleDialog, EmptyRuleState) 保持不變 ---
@Composable
fun RuleCard(
    rule: SchedulingRule,
    canToggle: Boolean,
    canEdit: Boolean,
    canDelete: Boolean,
    onToggle: (Boolean) -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    // ... (保持不變) ...
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (rule.isEnabled || rule.isTemplate) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(rule.ruleName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (!rule.isTemplate) { // Templates don't have a toggle
                    Switch(checked = rule.isEnabled, onCheckedChange = onToggle, enabled = canToggle)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                rule.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "類型: ${if (rule.ruleType == "hard") "硬性" else "軟性"} / 分數: ${rule.penaltyScore}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (canEdit) {
                    IconButton(onClick = onEditClick, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "編輯", tint = MaterialTheme.colorScheme.secondary)
                    }
                }
                if (canDelete) {
                    IconButton(onClick = onDeleteClick, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "刪除", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun EditRuleDialog(
    title: String,
    rule: SchedulingRule? = null, // null for new rule
    onDismiss: () -> Unit,
    onSave: (SchedulingRule) -> Unit
) {
    // ... (保持不變) ...
    var name by remember { mutableStateOf(rule?.ruleName ?: "") }
    var description by remember { mutableStateOf(rule?.description ?: "") }
    var penaltyScore by remember { mutableStateOf(rule?.penaltyScore?.toString() ?: "-50") }
    var ruleType by remember { mutableStateOf(rule?.ruleType ?: "soft") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("規則名稱") },
                    enabled = rule == null // Cannot edit name
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("規則描述") }
                )
                OutlinedTextField(
                    value = penaltyScore,
                    onValueChange = { penaltyScore = it },
                    label = { Text("懲罰分數 (負值)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("規則類型:", modifier = Modifier.weight(1f))
                    RadioButton(selected = ruleType == "soft", onClick = { ruleType = "soft" })
                    Text("軟性")
                    Spacer(Modifier.width(8.dp))
                    RadioButton(selected = ruleType == "hard", onClick = { ruleType = "hard" })
                    Text("硬性")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val updatedRule = (rule ?: SchedulingRule()).copy(
                    ruleName = name,
                    description = description,
                    penaltyScore = penaltyScore.toIntOrNull() ?: -50,
                    ruleType = ruleType
                )
                onSave(updatedRule)
            }) { Text("儲存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun EmptyRuleState(message: String) {
    // ... (保持不變) ...
    Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    )
}