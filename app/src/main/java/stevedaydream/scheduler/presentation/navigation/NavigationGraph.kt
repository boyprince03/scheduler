// scheduler/presentation/navigation/NavigationGraph.kt
package stevedaydream.scheduler.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import stevedaydream.scheduler.presentation.admin.AdminScreen
import stevedaydream.scheduler.presentation.auth.LoginScreen
import stevedaydream.scheduler.presentation.group.GroupListScreen
import stevedaydream.scheduler.presentation.invite.InviteManagementScreen
import stevedaydream.scheduler.presentation.invite.JoinOrganizationScreen
import stevedaydream.scheduler.presentation.invite.ReviewJoinRequestsScreen
import stevedaydream.scheduler.presentation.organization.OrganizationListScreen
import stevedaydream.scheduler.presentation.qr.QRScannerScreen
import stevedaydream.scheduler.presentation.schedule.ManpowerScreen
import stevedaydream.scheduler.presentation.schedule.ManualScheduleScreen
import stevedaydream.scheduler.presentation.schedule.ScheduleDetailScreen
import stevedaydream.scheduler.presentation.schedule.ScheduleScreen
import stevedaydream.scheduler.presentation.schedule.SchedulingRulesScreen
import stevedaydream.scheduler.presentation.schedule.ShiftTypeSettingsScreen
import stevedaydream.scheduler.presentation.user.UserProfileScreen
import stevedaydream.scheduler.presentation.user.BasicInfoScreen

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object BasicInfo : Screen("basic_info") // ✅ 1. 新增 BasicInfo Screen
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
    object ManpowerDashboard : Screen("manpower_dashboard/{orgId}/{groupId}/{month}") {
        fun createRoute(orgId: String, groupId: String, month: String) =
            "manpower_dashboard/$orgId/$groupId/$month"
    }
    object Admin : Screen("admin") // ✅ 2. 新增 Admin Screen
    object UserProfile : Screen("user_profile")
    // 邀請管理
    object InviteManagement : Screen("invite_management/{orgId}") {
        fun createRoute(orgId: String) = "invite_management/$orgId"
    }

    // 加入組織
    object JoinOrganization : Screen("join_organization")

    // 審核申請
    object ReviewRequests : Screen("review_requests/{orgId}") {
        fun createRoute(orgId: String) = "review_requests/$orgId"
    }

    // QR Scanner
    object QRScanner : Screen("qr_scanner")
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
                onLoginSuccess = { isNewUser -> // ✅ 2. 修改 onLoginSuccess 的參數
                    val destination = if (isNewUser) {
                        Screen.BasicInfo.route
                    } else {
                        Screen.OrganizationList.route
                    }
                    navController.navigate(destination) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onGoogleSignInClick = onGoogleSignInClick
            )
        }

        // ✅ 3. 新增 BasicInfoScreen 的 composable
        composable(Screen.BasicInfo.route) {
            BasicInfoScreen(
                onSaveSuccess = {
                    navController.navigate(Screen.OrganizationList.route) {
                        popUpTo(Screen.BasicInfo.route) { inclusive = true }
                    }
                }
            )
        }
        // 邀請管理
        composable(Screen.InviteManagement.route) { backStackEntry ->
            val orgId = backStackEntry.arguments?.getString("orgId") ?: return@composable
            InviteManagementScreen(
                orgId = orgId,
                onBackClick = { navController.popBackStack() }
            )
        }

        // 加入組織
        composable(Screen.JoinOrganization.route) {
            JoinOrganizationScreen(
                navController = navController, // <-- 傳入 navController
                onJoinSuccess = { navController.popBackStack() },
                onBackClick = { navController.popBackStack() }
            )
        }

        // 審核申請
        composable(Screen.ReviewRequests.route) { backStackEntry ->
            val orgId = backStackEntry.arguments?.getString("orgId") ?: return@composable
            ReviewJoinRequestsScreen(
                orgId = orgId,
                onBackClick = { navController.popBackStack() }
            )
        }

        // QR Scanner
        composable(Screen.QRScanner.route) {
            QRScannerScreen(
                onCodeScanned = { code ->
                    // 導航回加入組織頁面並帶入掃描結果
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("scanned_code", code)
                    navController.popBackStack()
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.OrganizationList.route) {
            OrganizationListScreen(
                navController = navController, // ✅ 傳入 navController
                onOrganizationClick = { orgId ->
                    navController.navigate(Screen.GroupList.createRoute(orgId))
                },
                // ✅ 3. 新增 onAdminClick 導航事件
                onAdminClick = {
                    navController.navigate(Screen.Admin.route)
                }
            )
        }
        // ✅ 4. 新增 AdminScreen 的 composable
        composable(Screen.Admin.route) {
            AdminScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.GroupList.route) { backStackEntry ->
            val orgId = backStackEntry.arguments?.getString("orgId") ?: return@composable
            GroupListScreen(
                orgId = orgId,
                onGroupClick = { groupId ->
                    navController.navigate(Screen.Schedule.createRoute(orgId, groupId))
                },
                onBackClick = { navController.popBackStack() },
                // vvvvvvvvvvvv 更新的導航事件 vvvvvvvvvvvv
                onNavigateToInviteManagement = { org ->
                    navController.navigate(Screen.InviteManagement.createRoute(org))
                }
                // ^^^^^^^^^^^^ 更新的導航事件 ^^^^^^^^^^^^
            )
        }


        composable(Screen.Schedule.route) { backStackEntry ->
            val orgId = backStackEntry.arguments?.getString("orgId") ?: return@composable
            val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
            ScheduleScreen(
                onBackClick = { navController.popBackStack() },
                onNavigateToManualSchedule = { org, group, month ->
                    navController.navigate(Screen.ManualSchedule.createRoute(org, group, month))
                },
                onNavigateToManpower = { org, group, month ->
                    navController.navigate(Screen.ManpowerDashboard.createRoute(org, group, month))
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
        composable(Screen.ManpowerDashboard.route) { backStackEntry ->
            ManpowerScreen(
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
        // <-- 3. 新增 UserProfileScreen 的 composable 區塊
        composable(Screen.UserProfile.route) {
            UserProfileScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        // 新增 composable
        composable(Screen.QRScanner.route) {
            QRScannerScreen(
                onCodeScanned = { code ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("scanned_code", code)
                    navController.popBackStack()
                },
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}