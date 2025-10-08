package stevedaydream.scheduler

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import stevedaydream.scheduler.presentation.navigation.NavigationGraph
import stevedaydream.scheduler.presentation.navigation.Screen
import stevedaydream.scheduler.ui.theme.SchedulerTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var auth: FirebaseAuth

    private lateinit var oneTapClient: SignInClient
    private lateinit var signInRequest: BeginSignInRequest

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化 Google One Tap
        oneTapClient = Identity.getSignInClient(this)
        signInRequest = BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    .setServerClientId(getString(R.string.default_web_client_id))
                    .setFilterByAuthorizedAccounts(false) // 顯示所有 Google 帳戶
                    .build()
            )
            .setAutoSelectEnabled(true) // 自動選擇唯一帳戶
            .build()

        setContent {
            SchedulerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    // 檢查使用者登入狀態
                    val startDestination = if (auth.currentUser != null) {
                        Screen.OrganizationList.route
                    } else {
                        Screen.Login.route
                    }

                    NavigationGraph(
                        navController = navController,
                        startDestination = startDestination,
                        onGoogleSignInClick = { onGoogleSignInClick() }
                    )
                }
            }
        }
    }

    // Google 登入觸發
    private fun onGoogleSignInClick() {
        lifecycleScope.launch {
            try {
                val result = oneTapClient.beginSignIn(signInRequest).await()

                // 啟動 Google Sign-In Intent
                val intentSenderRequest = IntentSenderRequest.Builder(
                    result.pendingIntent.intentSender
                ).build()

                // 通知 Compose 啟動 Activity Result
                googleSignInLauncher?.invoke(intentSenderRequest) // ✅ 已修正
            } catch (e: Exception) {
                // 如果 One Tap 失敗,可以改用傳統方式
                Toast.makeText(
                    this@MainActivity,
                    "登入失敗: ${e.localizedMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Activity Result Launcher (需要在 Compose 中設定)
    private var googleSignInLauncher: ((IntentSenderRequest) -> Unit)? = null

    fun setGoogleSignInLauncher(launcher: (IntentSenderRequest) -> Unit) {
        googleSignInLauncher = launcher
    }

    // 處理 Google 登入結果
    internal suspend fun handleGoogleSignInResult(data: android.content.Intent?): Result<Unit> {
        return try {
            val credential = oneTapClient.getSignInCredentialFromIntent(data)
            val idToken = credential.googleIdToken

            if (idToken != null) {
                // 使用 ID Token 登入 Firebase
                val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(firebaseCredential).await()
                Result.success(Unit)
            } else {
                Result.failure(Exception("無法取得 ID Token"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}