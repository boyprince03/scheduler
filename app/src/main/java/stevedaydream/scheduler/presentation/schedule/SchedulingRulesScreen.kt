// scheduler/presentation/schedule/SchedulingRulesScreen.kt

package stevedaydream.scheduler.presentation.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import stevedaydream.scheduler.data.model.SchedulingRule
import stevedaydream.scheduler.presentation.common.ConfirmDialog
import stevedaydream.scheduler.presentation.common.DividerWithText
import stevedaydream.scheduler.presentation.common.LoadingIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulingRulesScreen(
    orgId: String,
    groupId: String,
    viewModel: SchedulingRulesViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentUser = uiState.currentUser
    val isSuperuser = currentUser?.role == "superuser"

    // Dialog states
    var showAddCustomRuleDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<SchedulingRule?>(null) }
    var deletingRule by remember { mutableStateOf<SchedulingRule?>(null) }
    var showAddTemplateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(orgId, groupId) {
        viewModel.loadData(orgId, groupId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("排班規則儀表板") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            if (viewModel.canAddCustomRules()) {
                FloatingActionButton(onClick = { showAddCustomRuleDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "新增自訂規則")
                }
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            LoadingIndicator(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // --- 組織層級規則 ---
                item { DividerWithText("組織層級規則") }
                if (uiState.organizationRules.isEmpty()) {
                    item { EmptyRuleState("您的組織尚未啟用任何範本規則") }
                } else {
                    items(uiState.organizationRules, key = { it.id }) { rule ->
                        RuleCard(
                            rule = rule,
                            canToggle = isSuperuser || currentUser?.role == "org_admin",
                            canEdit = false, // Org rules cannot be edited
                            canDelete = false,
                            onToggle = { isEnabled -> viewModel.toggleRule(rule, isEnabled) },
                            onEditClick = {},
                            onDeleteClick = {}
                        )
                    }
                }

                // --- 群組自訂規則 ---
                item { Spacer(Modifier.height(16.dp)); DividerWithText("群組自訂規則") }
                if (uiState.groupCustomRules.isEmpty()) {
                    item { EmptyRuleState("此群組尚無自訂規則，點擊右下角新增") }
                } else {
                    items(uiState.groupCustomRules, key = { it.id }) { rule ->
                        val canModify = rule.createdBy == currentUser?.id || isSuperuser
                        RuleCard(
                            rule = rule,
                            canToggle = canModify,
                            canEdit = canModify,
                            canDelete = canModify,
                            onToggle = { isEnabled -> viewModel.toggleRule(rule, isEnabled) },
                            onEditClick = { editingRule = rule },
                            onDeleteClick = { deletingRule = rule }
                        )
                    }
                }

                // --- Superuser 專區: 管理範本 ---
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
                    if (uiState.ruleTemplates.isEmpty()) {
                        item { EmptyRuleState("範本庫是空的") }
                    } else {
                        items(uiState.ruleTemplates, key = { "template-${it.id}" }) { template ->
                            RuleCard(
                                rule = template,
                                canToggle = false, // Templates don't have an enabled state
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
                viewModel.addCustomRule(rule)
                showAddCustomRuleDialog = false
            }
        )
    }

    if (showAddTemplateDialog) {
        EditRuleDialog(
            title = "新增規則範本",
            onDismiss = { showAddTemplateDialog = false },
            onSave = { rule ->
                viewModel.addRuleTemplate(rule)
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
                    viewModel.updateRuleTemplate(updatedRule)
                } else {
                    viewModel.updateCustomRule(updatedRule)
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
                    viewModel.deleteRuleTemplate(ruleToDelete.id)
                } else {
                    viewModel.deleteRule(ruleToDelete)
                }
                deletingRule = null
            },
            onDismiss = { deletingRule = null }
        )
    }
}

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