package stevedaydream.scheduler.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import stevedaydream.scheduler.presentation.auth.LoginScreen
import stevedaydream.scheduler.presentation.group.GroupListScreen
import stevedaydream.scheduler.presentation.organization.OrganizationListScreen
import stevedaydream.scheduler.presentation.schedule.ScheduleScreen

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object OrganizationList : Screen("organization_list")
    object GroupList : Screen("group_list/{orgId}") {
        fun createRoute(orgId: String) = "group_list/$orgId"
    }
    object Schedule : Screen("schedule/{orgId}/{groupId}") {
        fun createRoute(orgId: String, groupId: String) = "schedule/$orgId/$groupId"
    }
    object Request : Screen("request/{orgId}/{groupId}") {
        fun createRoute(orgId: String, groupId: String) = "request/$orgId/$groupId"
    }
}

@Composable
fun NavigationGraph(
    navController: NavHostController,
    startDestination: String = Screen.Login.route,
    onGoogleSignInClick: () -> Unit = {} // 这个参数已经存在，很好
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.OrganizationList.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onGoogleSignInClick = onGoogleSignInClick // <-- 新增这一行，将参数传递下去
            )
        }

        composable(Screen.OrganizationList.route) {
            OrganizationListScreen(
                navController = navController, // ✅ 傳入 navController
                onOrganizationClick = { orgId ->
                    navController.navigate(Screen.GroupList.createRoute(orgId))
                }
            )
        }

        composable(Screen.GroupList.route) { backStackEntry ->
            val orgId = backStackEntry.arguments?.getString("orgId") ?: return@composable
            GroupListScreen(
                orgId = orgId,
                onGroupClick = { groupId ->
                    navController.navigate(Screen.Schedule.createRoute(orgId, groupId))
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.Schedule.route) { backStackEntry ->
            val orgId = backStackEntry.arguments?.getString("orgId") ?: return@composable
            val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
            ScheduleScreen(
                orgId = orgId,
                groupId = groupId,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}