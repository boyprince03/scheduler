package stevedaydream.scheduler.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import stevedaydream.scheduler.presentation.auth.LoginScreen
import stevedaydream.scheduler.presentation.group.GroupListScreen
import stevedaydream.scheduler.presentation.organization.OrganizationListScreen
import stevedaydream.scheduler.presentation.schedule.ManualScheduleScreen
import stevedaydream.scheduler.presentation.schedule.ScheduleDetailScreen
import stevedaydream.scheduler.presentation.schedule.ScheduleScreen
import stevedaydream.scheduler.presentation.schedule.SchedulingRulesScreen
import stevedaydream.scheduler.presentation.schedule.ShiftTypeSettingsScreen

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
    object ManualSchedule : Screen("manual_schedule/{orgId}/{groupId}/{month}") {
        fun createRoute(orgId: String, groupId: String, month: String) =
            "manual_schedule/$orgId/$groupId/$month"
    }
    object ScheduleDetail : Screen("schedule_detail/{orgId}/{groupId}/{scheduleId}") {
        fun createRoute(orgId: String, groupId: String, scheduleId: String) =
            "schedule_detail/$orgId/$groupId/$scheduleId"
    }
    object SchedulingRules : Screen("scheduling_rules/{orgId}/{groupId}") {
        fun createRoute(orgId: String, groupId: String) = "scheduling_rules/$orgId/$groupId"
    }
    object ShiftTypeSettings : Screen("shift_type_settings/{orgId}/{groupId}") {
        fun createRoute(orgId: String, groupId: String) = "shift_type_settings/$orgId/$groupId"
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
                // ✅ 移除 orgId, groupId 的傳遞
                onBackClick = { navController.popBackStack() },
                onNavigateToManualSchedule = { org, group, month ->
                    navController.navigate(Screen.ManualSchedule.createRoute(org, group, month))
                },
                onNavigateToScheduleDetail = { org, group, scheduleId ->
                    navController.navigate(Screen.ScheduleDetail.createRoute(org, group, scheduleId))
                },
                onNavigateToRules = { org, group ->
                    navController.navigate(Screen.SchedulingRules.createRoute(org, group))
                },
                onNavigateToShiftTypeSettings = { org, group ->
                    navController.navigate(Screen.ShiftTypeSettings.createRoute(org, group))
                }
            )
        }
        composable(Screen.ShiftTypeSettings.route) { backStackEntry ->
            val orgId = backStackEntry.arguments?.getString("orgId") ?: return@composable
            val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
            ShiftTypeSettingsScreen(
                orgId = orgId,
                groupId = groupId,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.ScheduleDetail.route) { backStackEntry ->
            // 雖然 ViewModel 會處理參數，但保留它們以備不時之需
            val orgId = backStackEntry.arguments?.getString("orgId") ?: return@composable
            val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
            val scheduleId = backStackEntry.arguments?.getString("scheduleId") ?: return@composable

            ScheduleDetailScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.ManualSchedule.route) { backStackEntry ->
            ManualScheduleScreen(
                // ✅ 移除 orgId, groupId, month 的傳遞
                onBackClick = { navController.popBackStack() },
                onSaveSuccess = { navController.popBackStack() }
            )
        }
        // ✅ 新增這個 composable 區塊
        composable(Screen.SchedulingRules.route) { backStackEntry ->
            val orgId = backStackEntry.arguments?.getString("orgId") ?: return@composable
            val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
            SchedulingRulesScreen(
                orgId = orgId,
                groupId = groupId,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}