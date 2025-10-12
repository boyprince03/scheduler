package stevedaydream.scheduler.presentation.admin

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.collectLatest
import stevedaydream.scheduler.util.showToast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    viewModel: AdminViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    var orgName by remember { mutableStateOf("自動生成測試公司") }
    var testMemberEmail by remember { mutableStateOf("") } // <-- 1. 新增狀態來儲存 Email
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.generationState.collectLatest { result ->
            isLoading = false
            result.onSuccess {
                context.showToast("測試資料建立成功！")
                onBackClick() // 成功後返回上一頁
            }.onFailure { error ->
                context.showToast("建立失敗: ${error.message}")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("超級管理員儀表板") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "測試資料生成器",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                "點擊按鈕將會建立一組完整的組織資料，包含使用者、群組、班別、排班表等。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = orgName,
                onValueChange = { orgName = it },
                label = { Text("新組織的名稱") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // <-- 2. 新增 Email 輸入框
            OutlinedTextField(
                value = testMemberEmail,
                onValueChange = { testMemberEmail = it },
                label = { Text("測試成員 Email (選填)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )


            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    isLoading = true
                    // 3. 傳遞 Email 到 ViewModel
                    viewModel.createTestData(orgName, testMemberEmail)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("生成中...")
                } else {
                    Icon(Icons.Default.AddCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("一鍵生成測試資料")
                }
            }
        }
    }
}