package stevedaydream.scheduler.presentation.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
    onLoginSuccess: () -> Unit,
    onGoogleSignInClick: () -> Unit // <-- 1. 添加这个参数
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    // Google Sign-In Launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            lifecycleOwner.lifecycleScope.launch {
                // 这里对 MainActivity 的引用是正确的，因为需要调用 handleGoogleSignInResult
                val mainActivity = context as? stevedaydream.scheduler.MainActivity
                mainActivity?.handleGoogleSignInResult(result.data)?.let { authResult ->
                    authResult.onSuccess {
                        viewModel.onLoginSuccess()
                    }.onFailure { error ->
                        viewModel.onLoginError(error.localizedMessage ?: "登入失敗")
                    }
                }
            }
        }
    }

    // 设置 Launcher 到 MainActivity
    LaunchedEffect(Unit) {
        (context as? stevedaydream.scheduler.MainActivity)?.setGoogleSignInLauncher { request ->
            googleSignInLauncher.launch(request)
        }
    }

    // 监听登录成功
    LaunchedEffect(uiState.isLoginSuccess) {
        if (uiState.isLoginSuccess) {
            onLoginSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ... (Logo 与标题部分不变)

            Spacer(modifier = Modifier.height(32.dp))

            // Google 登入按鈕
            Button(
                onClick = {
                    // <-- 2. 修改 onClick 的内容
                    viewModel.startGoogleSignIn()
                    onGoogleSignInClick() // 直接调用传入的回调函数
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("使用 Google 登入")
                }
            }

            // 測試用:匿名登入按鈕
            OutlinedButton(
                onClick = { viewModel.signInAnonymously() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading
            ) {
                Text("匿名登入 (測試)")
            }

            // 錯誤訊息
            if (uiState.error != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = uiState.error!!,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}