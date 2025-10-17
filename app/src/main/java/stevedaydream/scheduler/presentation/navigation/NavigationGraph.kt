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
import stevedaydream.scheduler.presentation.members.MemberListScreen
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
import androidx.navigation.NavType
import androidx.navigation.navArgument
import stevedaydream.scheduler.presentation.schedule.ShiftReservationScreen

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object BasicInfo : Screen("basic_info")
    object OrganizationList : Screen("organization_list")
    object GroupList : Screen("group_list/{orgId}") {
        fun createRoute(orgId: String) = "group_list/$orgId"
    }
    object ShiftReservation : Screen("shift_reservation/{orgId}/{groupId}/{month}") {
        fun createRoute(orgId: String, groupId: String, month: String) = "shift_reservation/$orgId/$groupId/$month"
    }
    object MemberList : Screen("member_list/{orgId}") {
        fun createRoute(orgId: String) = "member_list/$orgId"
    }
    object Schedule : Screen("schedule/{orgId}/{groupId}") {
        fun createRoute(orgId: String, groupId: String) = "schedule/$orgId/$groupId"
    }
    object Request : Screen("request/{orgId}/{groupId}") {
        fun createRoute(orgId: String, groupId: String) = "request/$orgId/$groupId"
    }
    // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
    object ManualSchedule : Screen("manual_schedule/{orgId}/{groupId}/{month}?scheduleId={scheduleId}") {
        // 新增班表
        fun createRoute(orgId: String, groupId: String, month: String) =
            "manual_schedule/$orgId/$groupId/$month"
        // 編輯現有班表
        fun createRouteForEdit(orgId: String, groupId: String, month: String, scheduleId: String) =
            "manual_schedule/$orgId/$groupId/$month?scheduleId=$scheduleId"
    }
    // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲
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
    object Admin : Screen("admin")
    object UserProfile : Screen("user_profile")
    object InviteManagement : Screen("invite_management/{orgId}") {
        fun createRoute(orgId: String) = "invite_management/$orgId"
    }
    object JoinOrganization : Screen("join_organization")
    object ReviewRequests : Screen("review_requests/{orgId}") {
        fun createRoute(orgId: String) = "review_requests/$orgId"
    }
    object QRScanner : Screen("qr_scanner")
}



@Composable
fun NavigationGraph(
    navController: NavHostController,
    startDestination: String = Screen.Login.route,
    onGoogleSignInClick: () -> Unit = {}

) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = { isNewUser ->
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

        composable(Screen.BasicInfo.route) {
            BasicInfoScreen(
                onSaveSuccess = {
                    navController.navigate(Screen.OrganizationList.route) {
                        popUpTo(Screen.BasicInfo.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.InviteManagement.route) { backStackEntry ->
            val orgId = backStackEntry.arguments?.getString("orgId") ?: return@composable
            InviteManagementScreen(
                orgId = orgId,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.JoinOrganization.route) {
            JoinOrganizationScreen(
                navController = navController,
                onJoinSuccess = { navController.popBackStack() },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.ReviewRequests.route) { backStackEntry ->
            val orgId = backStackEntry.arguments?.getString("orgId") ?: return@composable
            ReviewJoinRequestsScreen(
                orgId = orgId,
                onBackClick = { navController.popBackStack() }
            )
        }

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

        composable(Screen.OrganizationList.route) {
            OrganizationListScreen(
                navController = navController,
                onOrganizationClick = { orgId ->
                    navController.navigate(Screen.GroupList.createRoute(orgId))
                },
                onAdminClick = {
                    navController.navigate(Screen.Admin.route)
                }
            )
        }
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
                onNavigateToInviteManagement = { org ->
                    navController.navigate(Screen.InviteManagement.createRoute(org))
                },
                onNavigateToMemberList = { org ->
                    navController.navigate(Screen.MemberList.createRoute(org))
                }
            )
        }

        composable(Screen.MemberList.route) { backStackEntry ->
            val orgId = backStackEntry.arguments?.getString("orgId") ?: return@composable
            MemberListScreen(
                orgId = orgId,
                onBackClick = { navController.popBackStack() }
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
                },
                // ▼▼▼▼▼▼▼▼▼▼▼▼ 修正點：補上 onNavigateToReservation 參數 ▼▼▼▼▼▼▼▼▼▼▼▼
                onNavigateToReservation = { org, group, month ->
                    navController.navigate(Screen.ShiftReservation.createRoute(org, group, month))
                }
            )
        }
        composable(Screen.ShiftReservation.route) { backStackEntry ->
            ShiftReservationScreen(
                onBackClick = { navController.popBackStack() }
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
            // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
            val orgId = backStackEntry.arguments?.getString("orgId") ?: return@composable
            val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
            val scheduleId = backStackEntry.arguments?.getString("scheduleId") ?: return@composable

            ScheduleDetailScreen(
                onBackClick = { navController.popBackStack() },
                onEditClick = { month ->
                    navController.navigate(Screen.ManualSchedule.createRouteForEdit(orgId, groupId, month, scheduleId))
                }
            )
            // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲
        }

        composable(
            route = Screen.ManualSchedule.route,
            arguments = listOf(
                navArgument("scheduleId") {
                    type = NavType.StringType
                    nullable = true // 關鍵：聲明參數可以為 null
                }
            )
        ) { backStackEntry ->
            ManualScheduleScreen(
                onBackClick = { navController.popBackStack() },
                onSaveSuccess = { navController.popBackStack() }
            )
        }
        composable(Screen.SchedulingRules.route) { backStackEntry ->
            val orgId = backStackEntry.arguments?.getString("orgId") ?: return@composable
            val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
            SchedulingRulesScreen(
                orgId = orgId,
                groupId = groupId,
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Screen.UserProfile.route) {
            UserProfileScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}