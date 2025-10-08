package stevedaydream.scheduler

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import stevedaydream.scheduler.presentation.navigation.NavigationGraph
import stevedaydream.scheduler.presentation.navigation.Screen
import stevedaydream.scheduler.ui.theme.SchedulerTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                        startDestination = startDestination
                    )
                }
            }
        }
    }
}