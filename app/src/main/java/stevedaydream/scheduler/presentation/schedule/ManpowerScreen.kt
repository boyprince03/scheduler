package stevedaydream.scheduler.presentation.schedule

import androidx.compose.foundation.layout.Box // ✅ 1. 匯入 Box
import androidx.compose.foundation.layout.padding // ✅ 1. 匯入 padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier // ✅ 1. 匯入 Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import stevedaydream.scheduler.presentation.common.LoadingIndicator
import stevedaydream.scheduler.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManpowerScreen(
    viewModel: ManpowerViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (uiState.currentStep == ManpowerStep.DETAILS) {
                        TextButton(onClick = { viewModel.savePlan() }) {
                            Text("儲存")
                        }
                    }
                }
            )
        }
    ) { innerPadding -> // ✅ 2. Scaffold 提供了一個名為 innerPadding 的 PaddingValues
        Box(
            // ✅ 3. 將這個 innerPadding 應用到 Box 的 Modifier.padding() 上
            modifier = Modifier.padding(innerPadding)
        ) {
            if (uiState.isLoading) {
                LoadingIndicator()
            } else {
                when (uiState.currentStep) {
                    ManpowerStep.DEFAULTS -> ManpowerDefaultsScreen(
                        uiState = uiState,
                        onDefaultChange = viewModel::updateDefaultRequirement,
                        onProceed = viewModel::applyDefaultsAndProceed
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