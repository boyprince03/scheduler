// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
package stevedaydream.scheduler.presentation.schedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext // ✅ 1. 匯入 LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.collectLatest // ✅ 2. 匯入 collectLatest
import stevedaydream.scheduler.presentation.common.LoadingIndicator
import stevedaydream.scheduler.util.DateUtils
import stevedaydream.scheduler.util.showToast // ✅ 3. 匯入 showToast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManpowerScreen(
    viewModel: ManpowerViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current // ✅ 4. 取得 context

    // ✅ 5. 監聽儲存成功事件
    LaunchedEffect(Unit) {
        viewModel.saveSuccessEvent.collectLatest {
            context.showToast("人力規劃儲存成功")
            onBackClick() // 返回上一頁
        }
    }

    // ✅ 6. (可選) 監聽儲存失敗事件，只顯示 Toast，不返回
    LaunchedEffect(uiState.saveResult) {
        uiState.saveResult?.onFailure { error ->
            context.showToast("儲存失敗: ${error.message}")
            // 這裡可以選擇是否清除 saveResult 狀態，避免重複顯示 Toast
            // viewModel.clearSaveResult() // 如果 ViewModel 有提供此方法
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val titleText = when(uiState.currentStep) {
                        ManpowerStep.DEFAULTS -> "設定人力範本"
                        ManpowerStep.DETAILS -> "${DateUtils.getDisplayMonth(viewModel.month)} 人力微調"
                    }
                    Text(titleText)
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.currentStep == ManpowerStep.DETAILS) {
                            viewModel.returnToDefaults()
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (uiState.currentStep == ManpowerStep.DETAILS) {
                        // ✅ 7. 讓按鈕在儲存中 (isLoading) 時禁用
                        TextButton(
                            onClick = { viewModel.savePlan() },
                            enabled = !uiState.isLoading // 當 isLoading 為 true 時禁用
                        ) {
                            // ✅ 8. 儲存中顯示進度指示器
                            if (uiState.isLoading && uiState.saveResult == null) { // 僅在開始儲存且尚未有結果時顯示
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            } else {
                                Text("儲存")
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier.padding(innerPadding)
        ) {
            // ✅ 9. isLoading 邏輯調整：只在初始載入時顯示全螢幕 Loading
            if (uiState.isLoading && uiState.manpowerPlan == null) { // 初始載入
                LoadingIndicator()
            } else { // 初始載入完成後，即使在儲存中也顯示內容
                when (uiState.currentStep) {
                    ManpowerStep.DEFAULTS -> ManpowerDefaultsScreen(
                        uiState = uiState,
                        onDefaultChange = viewModel::updateDefaultRequirement,
                        onProceed = viewModel::applyDefaultsAndProceed,
                        onDateClicked = viewModel::onDateClicked,
                        onAddHoliday = viewModel::addHoliday,
                        onDismissHolidayDialog = viewModel::dismissHolidayNameDialog
                    )
                    ManpowerStep.DETAILS -> ManpowerDetailScreen(
                        uiState = uiState,
                        onRequirementChange = viewModel::updateRequirement
                    )
                }
            }
        }
    }
}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲