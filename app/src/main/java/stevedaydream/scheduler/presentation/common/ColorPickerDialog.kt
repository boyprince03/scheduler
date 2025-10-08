package stevedaydream.scheduler.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import stevedaydream.scheduler.util.toComposeColor

@Composable
fun ColorPickerDialog(
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit
) {
    val predefinedColors = listOf(
        "#4A90E2", "#6C63FF", "#F5A623", "#7ED321", "#D0021B",
        "#BD10E0", "#4A4A4A", "#9B9B9B", "#00B8D9", "#50E3C2",
        "#B8E986", "#417505", "#F8E71C", "#FF9F00", "#FF453A"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("選擇顏色") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(predefinedColors) { colorHex ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(colorHex.toComposeColor())
                            .clickable { onColorSelected(colorHex) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}