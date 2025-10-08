// scheduler/presentation/schedule/ShiftTypeSettingsScreen.kt

package stevedaydream.scheduler.presentation.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import stevedaydream.scheduler.data.model.ShiftType
import stevedaydream.scheduler.presentation.common.ColorPickerDialog
import stevedaydream.scheduler.presentation.common.ConfirmDialog
import stevedaydream.scheduler.presentation.common.DividerWithText
import stevedaydream.scheduler.presentation.common.LoadingIndicator
import stevedaydream.scheduler.util.toComposeColor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftTypeSettingsScreen(
    orgId: String,
    groupId: String,
    viewModel: ShiftTypeSettingsViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentUser = uiState.currentUser

    var showEditDialog by remember { mutableStateOf(false) }
    var editingShiftType by remember { mutableStateOf<ShiftType?>(null) }
    var deletingShiftType by remember { mutableStateOf<ShiftType?>(null) }

    LaunchedEffect(orgId, groupId) {
        viewModel.loadData(orgId, groupId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("班別設定") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingShiftType = null // 確保是新增模式
                showEditDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "新增班別")
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
                // ... 範本區 & 自訂區 ... (維持不變)
                item { DividerWithText("從範本選擇 (加值功能)") }
                item { Spacer(Modifier.height(16.dp)); DividerWithText("群組自訂班別") }
                if (uiState.groupCustomShiftTypes.isEmpty()) {
                    item {
                        Text(
                            "此群組尚無自訂班別",
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    items(uiState.groupCustomShiftTypes, key = { it.id }) { shiftType ->
                        val canModify = shiftType.createdBy == currentUser?.id || currentUser?.role == "superuser"
                        ShiftTypeCard(
                            shiftType = shiftType,
                            canEdit = canModify,
                            canDelete = canModify,
                            onEditClick = {
                                editingShiftType = shiftType
                                showEditDialog = true
                            },
                            onDeleteClick = { deletingShiftType = shiftType }
                        )
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        EditShiftTypeDialog(
            shiftType = editingShiftType,
            onDismiss = { showEditDialog = false },
            onSave = { updatedShiftType ->
                if (editingShiftType == null) {
                    viewModel.addCustomShiftType(updatedShiftType)
                } else {
                    viewModel.updateShiftType(updatedShiftType)
                }
                showEditDialog = false
            }
        )
    }

    deletingShiftType?.let {
        ConfirmDialog(
            title = "確認刪除",
            message = "確定要刪除班別「${it.name}」嗎？",
            onConfirm = {
                viewModel.deleteShiftType(it)
                deletingShiftType = null
            },
            onDismiss = { deletingShiftType = null }
        )
    }
}


@Composable
fun ShiftTypeCard(
    shiftType: ShiftType,
    canEdit: Boolean,
    canDelete: Boolean,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                color = shiftType.color.toComposeColor(),
                shape = MaterialTheme.shapes.medium,
                shadowElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = shiftType.shortCode,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(shiftType.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${shiftType.startTime} - ${shiftType.endTime}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (canEdit) {
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Default.Edit, contentDescription = "編輯")
                }
            }
            if (canDelete) {
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.Delete, contentDescription = "刪除", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditShiftTypeDialog(
    shiftType: ShiftType?,
    onDismiss: () -> Unit,
    onSave: (ShiftType) -> Unit
) {
    var name by remember { mutableStateOf(shiftType?.name ?: "") }
    var shortCode by remember { mutableStateOf(shiftType?.shortCode ?: "") }
    var startTime by remember { mutableStateOf(shiftType?.startTime ?: "09:00") }
    var endTime by remember { mutableStateOf(shiftType?.endTime ?: "17:00") }
    var color by remember { mutableStateOf(shiftType?.color ?: "#4A90E2") }

    // Time Picker States
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    // Color Picker State
    var showColorPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (shiftType == null) "新增班別" else "編輯班別") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("班別名稱") })
                OutlinedTextField(value = shortCode, onValueChange = { shortCode = it }, label = { Text("代號 (1-2字)") }, singleLine = true)

                // 🔽🔽🔽 替換時間選擇器 🔽🔽🔽
                TimeSelector(label = "開始時間", time = startTime) { showStartTimePicker = true }
                TimeSelector(label = "結束時間", time = endTime) { showEndTimePicker = true }
                // 🔼🔼🔼

                // 🔽🔽🔽 替換顏色選擇器 🔽🔽🔽
                ColorSelector(label = "顏色", colorHex = color) { showColorPicker = true }
                // 🔼🔼🔼
            }
        },
        confirmButton = {
            Button(onClick = {
                val updated = (shiftType ?: ShiftType()).copy(
                    name = name.trim(),
                    shortCode = shortCode.trim(),
                    startTime = startTime,
                    endTime = endTime,
                    color = color
                )
                onSave(updated)
            }) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )

    // --- Picker Dialogs ---
    if (showStartTimePicker) {
        TimePickerDialog(
            initialTime = startTime,
            onDismiss = { showStartTimePicker = false },
            onConfirm = { hour, minute ->
                val calendar = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute) }
                startTime = timeFormat.format(calendar.time)
                showStartTimePicker = false
            }
        )
    }

    if (showEndTimePicker) {
        TimePickerDialog(
            initialTime = endTime,
            onDismiss = { showEndTimePicker = false },
            onConfirm = { hour, minute ->
                val calendar = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute) }
                endTime = timeFormat.format(calendar.time)
                showEndTimePicker = false
            }
        )
    }

    if (showColorPicker) {
        ColorPickerDialog(
            onDismiss = { showColorPicker = false },
            onColorSelected = { selectedColor ->
                color = selectedColor
                showColorPicker = false
            }
        )
    }
}

@Composable
private fun TimeSelector(label: String, time: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(time, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
    }
}

@Composable
private fun ColorSelector(label: String, colorHex: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(colorHex.toComposeColor(), shape = MaterialTheme.shapes.small)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = MaterialTheme.shapes.small
                )
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialTime: String,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    val (initialHour, initialMinute) = try {
        val parts = initialTime.split(":")
        parts[0].toInt() to parts[1].toInt()
    } catch (e: Exception) {
        9 to 0 // Default value
    }

    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("選擇時間", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(20.dp))
                TimePicker(state = timePickerState)
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            onConfirm(timePickerState.hour, timePickerState.minute)
                        }
                    ) { Text("確認") }
                }
            }
        }
    }
}

