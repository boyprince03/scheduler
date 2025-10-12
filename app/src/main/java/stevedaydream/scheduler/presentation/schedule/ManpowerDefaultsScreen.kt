package stevedaydream.scheduler.presentation.schedule

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import stevedaydream.scheduler.data.model.ShiftType

@Composable
fun ManpowerDefaultsScreen(
    uiState: ManpowerUiState,
    onDefaultChange: (dayType: String, shiftTypeId: String, count: Int) -> Unit,
    onProceed: () -> Unit
) {
    val defaults = uiState.manpowerPlan?.requirementDefaults

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            DefaultCategoryCard(
                title = "平日 (週一至週五)",
                shiftTypes = uiState.shiftTypes,
                values = defaults?.weekday ?: emptyMap(),
                onValueChange = { shiftTypeId, count ->
                    onDefaultChange("weekday", shiftTypeId, count)
                }
            )
        }
        item {
            DefaultCategoryCard(
                title = "週六",
                shiftTypes = uiState.shiftTypes,
                values = defaults?.saturday ?: emptyMap(),
                onValueChange = { shiftTypeId, count ->
                    onDefaultChange("saturday", shiftTypeId, count)
                }
            )
        }
        item {
            DefaultCategoryCard(
                title = "週日",
                shiftTypes = uiState.shiftTypes,
                values = defaults?.sunday ?: emptyMap(),
                onValueChange = { shiftTypeId, count ->
                    onDefaultChange("sunday", shiftTypeId, count)
                }
            )
        }
        item {
            DefaultCategoryCard(
                title = "國定假日／特殊日",
                shiftTypes = uiState.shiftTypes,
                values = defaults?.holiday ?: emptyMap(),
                onValueChange = { shiftTypeId, count ->
                    onDefaultChange("holiday", shiftTypeId, count)
                }
            )
        }
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onProceed,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("套用範本並進入微調")
            }
        }
    }
}

@Composable
private fun DefaultCategoryCard(
    title: String,
    shiftTypes: List<ShiftType>,
    values: Map<String, Int>,
    onValueChange: (shiftTypeId: String, count: Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Divider()
            shiftTypes.forEach { shiftType ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = shiftType.name, modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = values[shiftType.id]?.toString() ?: "",
                        onValueChange = {
                            onValueChange(shiftType.id, it.toIntOrNull() ?: 0)
                        },
                        modifier = Modifier.width(100.dp),
                        label = { Text("人數") },
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            }
        }
    }
}