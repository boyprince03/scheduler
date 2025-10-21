// scheduler/util/TestDataGenerator.kt
// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改 generateGroups, generateCompleteTestDataSet ▼▼▼▼▼▼▼▼▼▼▼▼
package stevedaydream.scheduler.util

import stevedaydream.scheduler.data.model.*
import java.util.*
import kotlin.math.absoluteValue

// ... (getHospitalNursePractitionerRules 保持不變) ...
private fun getHospitalNursePractitionerRules(): List<SchedulingRule> {
    return listOf(
        SchedulingRule(
            id = "template-hnp-consecutive-work-6",
            ruleName = "連續上班不超過N天",
            description = "每人當月不可連續上班超過6天。",
            ruleType = "hard", // 硬性規則
            penaltyScore = -1000,
            isEnabled = true,
            isTemplate = true, // 標示為範本
            parameters = mapOf("maxDays" to "6")
        ),
        SchedulingRule(
            id = "template-hnp-night-shift-followup",
            ruleName = "夜班後續班別限制",
            description = "值班(夜)後面只能是值班(夜)或OFF。",
            ruleType = "hard",
            penaltyScore = -1000,
            isEnabled = true,
            isTemplate = true
        ),
        SchedulingRule(
            id = "template-hnp-min-rest-12h",
            ruleName = "輪班間隔需大於N小時",
            description = "每個班別中間至少要間隔12小時。",
            ruleType = "hard",
            penaltyScore = -1000,
            isEnabled = true,
            isTemplate = true,
            parameters = mapOf("minHours" to "12")
        )
    )
}

/**
 * 測試資料產生器 (已更新)
 * 用於開發階段快速產生符合最新資料模型的測試資料
 */
object TestDataGenerator {

    // ... (generateOrganization, generateUsers 保持不變) ...
    /**
     * 產生測試組織
     */
    fun generateOrganization(
        id: String = UUID.randomUUID().toString(),
        name: String = "測試組織",
        ownerId: String,
        orgCode: String
    ): Organization {
        return Organization(
            id = id,
            orgName = name,
            displayName = "$name (總部)",
            orgCode = orgCode,
            location = "測試地區",
            ownerId = ownerId,
            plan = "free",
            createdAt = Date(),
            requireApproval = true,
            isActive = true,
            features = Features(
                advancedRules = false,
                excelExport = false,
                apiAccess = false
            )
        )
    }

    /**
     * 產生測試使用者列表
     */
    fun generateUsers(
        orgId: String,
        count: Int = 10
    ): List<User> {
        val names = listOf(
            "王小明", "李小華", "張大同", "陳美麗", "林志明",
            "黃淑芬", "吳建國", "劉雅婷", "蔡文傑", "鄭淑珍"
        )

        return names.take(count).mapIndexed { index, name ->
            val userId = "test-user-${UUID.randomUUID().toString().take(8)}"
            User(
                id = userId,
                orgIds = listOf(orgId),
                currentOrgId = orgId,
                email = "testuser${index + 1}@example.com",
                name = name,
                role = if (index == 0) "org_admin" else "member",
                employeeId = "E${1000 + index}",
                joinedAt = Date(System.currentTimeMillis() - (index * 86400000L)),
                employmentStatus = mapOf(orgId to "active") // 新增 employmentStatus
            )
        }
    }

    /**
     * 產生測試群組 (加入 rotationState)
     */
    fun generateGroups(
        orgId: String,
        userIds: List<String>,
        shiftTypes: List<ShiftType> // 新增參數以取得 D, N 班 ID
    ): List<Group> {
        // 找出 D 班和 N 班的 ID
        val dShiftId = shiftTypes.find { it.name == "值班(日)" }?.id
        val nShiftId = shiftTypes.find { it.name == "值班(夜)" }?.id

        // 設定預設輪替狀態
        val defaultRotationState = mutableMapOf<String, Int>()
        dShiftId?.let { defaultRotationState[it] = 0 } // 週六 D 班從 0 號開始 (王小明)
        nShiftId?.let { defaultRotationState[it] = 1 } // 週日 N 班從 1 號開始 (李小華)

        return listOf(
            Group(
                id = "group-${UUID.randomUUID().toString().take(8)}",
                orgId = orgId,
                groupName = "日班團隊",
                memberIds = userIds.take(5),
                // 加入預設輪替狀態
                rotationState = defaultRotationState.toMap()
            ),
            Group(
                id = "group-${UUID.randomUUID().toString().take(8)}",
                orgId = orgId,
                groupName = "夜班團隊",
                memberIds = userIds.drop(5).take(5),
                // 加入預設輪替狀態
                rotationState = defaultRotationState.toMap()
            )
        )
    }

    // ... (generateShiftTypes, generateRequests, generateConsistentScheduleForGroup, generateManpowerPlan 保持不變) ...
    /**
     * 產生測試班別類型
     */
    fun generateShiftTypes(orgId: String): List<ShiftType> {
        return listOf(
            ShiftType(
                id = "off", orgId = orgId, name = "放假", shortCode = "OFF",
                startTime = "00:00", endTime = "00:00", color = "#D0021B" // 紅色
            ),
            ShiftType(
                id = "day-s", orgId = orgId, name = "白班", shortCode = "S",
                startTime = "09:00", endTime = "17:00", color = "#4A90E2" // 淺藍色
            ),
            ShiftType(
                id = "night-n", orgId = orgId, name = "值班(夜)", shortCode = "N",
                startTime = "21:00", endTime = "09:00", color = "#000000" // 黑色
            ),
            ShiftType(
                id = "day-d", orgId = orgId, name = "值班(日)", shortCode = "D",
                startTime = "09:00", endTime = "21:00", color = "#7ED321" // 綠色
            )
        )
    }

    /**
     * 產生測試請求 (請假/偏好)
     */
    fun generateRequests(
        orgId: String,
        users: List<User>,
        month: String = DateUtils.getCurrentMonthString()
    ): List<Request> {
        val dates = DateUtils.getDatesInMonth(month)
        return users.take(2).flatMapIndexed { userIndex, user ->
            listOf(
                Request(
                    id = "request-${UUID.randomUUID().toString().take(8)}",
                    orgId = orgId, userId = user.id, userName = user.name, date = dates[userIndex + 5],
                    type = "leave", details = mapOf("reason" to "家庭事務"), status = "approved", createdAt = Date()
                ),
                Request(
                    id = "request-${UUID.randomUUID().toString().take(8)}",
                    orgId = orgId, userId = user.id, userName = user.name, date = dates[userIndex + 10],
                    type = "shift_preference", details = mapOf("shiftId" to "day-s"), status = "pending", createdAt = Date()
                )
            )
        }
    }
    /**
     * 為指定的群組和使用者產生一份連貫的班表和班表分配資料
     */
    private fun generateConsistentScheduleForGroup(
        orgId: String,
        group: Group,
        users: List<User>,
        month: String = DateUtils.getCurrentMonthString()
    ): Pair<Schedule, List<Assignment>> {
        val dates = DateUtils.getDatesInMonth(month)
        val workShifts = listOf("day-s", "night-n", "day-d")
        val allShiftIds = workShifts + "off"
        val violations = mutableListOf<String>()

        val assignments = users.map { user ->
            val dailyShifts = mutableMapOf<String, String>()
            dates.forEach { date ->
                val day = date.split("-").last()
                val dayInt = day.toInt()

                // 為王小明刻意製造連七的班表
                if (user.name == "王小明" && dayInt in 2..8) {
                    dailyShifts[day] = "day-d" // 連續上值班(日)
                } else {
                    // 其他人隨機排班
                    val shiftIndex = (dayInt + user.id.hashCode().absoluteValue) % allShiftIds.size
                    dailyShifts[day] = allShiftIds[shiftIndex]
                }
            }
            Assignment(
                id = "assignment-${user.id}-${UUID.randomUUID().toString().take(4)}",
                scheduleId = "", // 會在後面統一設定
                userId = user.id,
                userName = user.name,
                dailyShifts = dailyShifts
            )
        }

        // 檢查規則違反 (簡化版)
        assignments.find { it.userName == "王小明" }?.let { assignment ->
            var consecutiveDays = 0
            var maxConsecutive = 0
            dates.forEach { date ->
                val day = date.split("-").last()
                if (assignment.dailyShifts[day] != "off") {
                    consecutiveDays++
                } else {
                    maxConsecutive = maxOf(maxConsecutive, consecutiveDays)
                    consecutiveDays = 0
                }
            }
            maxConsecutive = maxOf(maxConsecutive, consecutiveDays)
            if (maxConsecutive > 6) {
                violations.add("${assignment.userName}: 連續上班 ${maxConsecutive} 天，超過上限 6 天")
            }
        }

        val schedule = Schedule(
            id = "schedule-${group.id}-${UUID.randomUUID().toString().take(4)}",
            orgId = orgId,
            groupId = group.id,
            month = month,
            status = "draft",
            generatedAt = Date(),
            totalScore = if (violations.isNotEmpty()) -50 else 0,
            violatedRules = violations
        )

        // 將最終的 scheduleId 回填到 assignments
        val finalAssignments = assignments.map { it.copy(scheduleId = schedule.id) }

        return schedule to finalAssignments
    }

    /**
     * 新增：產生測試用的人力規劃
     */
    private fun generateManpowerPlan(
        orgId: String,
        groupId: String,
        month: String
    ): ManpowerPlan {
        val requirementDefaults = RequirementDefaults(
            weekday = mapOf("day-s" to 2, "day-d" to 1),
            saturday = mapOf("day-d" to 1, "night-n" to 1),
            sunday = mapOf("day-d" to 1, "night-n" to 1)
        )
        val dates = DateUtils.getDatesInMonth(month)
        val dailyRequirements = dates.associate { date ->
            val day = date.split("-").last()
            val dayOfWeek = DateUtils.getDayOfWeek(date)
            val template = when (dayOfWeek) {
                6 -> requirementDefaults.saturday
                0 -> requirementDefaults.sunday
                else -> requirementDefaults.weekday
            }
            day to DailyRequirement(date = date, requirements = template)
        }

        return ManpowerPlan(
            id = "${orgId}_${groupId}_${month}",
            orgId = orgId,
            groupId = groupId,
            month = month,
            requirementDefaults = requirementDefaults,
            dailyRequirements = dailyRequirements,
            updatedAt = Date()
        )
    }

    /**
     * 新增：產生測試用的預約班表 (保持 public 但不再被 generateCompleteTestDataSet 呼叫)
     * ✅ 修改：生成 Map<String, List<String>>
     */
    fun generateReservations( // 保持 public
        orgId: String,
        groupId: String,
        users: List<User>, // 接收 User 列表
        month: String
    ): List<Reservation> {
        val dates = DateUtils.getDatesInMonth(month)
        // 只為前三位使用者產生預約
        return users.take(3).map { user ->
            // ✅ Line 313: 將 "off" 包裝成 listOf("off")
            val dailyShifts: Map<String, List<String>> = dates.shuffled().take(5).associate { date ->
                val day = date.split("-").last()
                day to listOf("off") // 隨機預約 5 天假，值為 List<String>
            }
            Reservation(
                id = "res-${user.id}-${UUID.randomUUID().toString().take(4)}",
                orgId = orgId,
                groupId = groupId,
                month = month,
                userId = user.id,
                userName = user.name,
                dailyShifts = dailyShifts, // 使用 Map<String, List<String>>
                updatedAt = Date()
            )
        }
    }


    /**
     * 完整的測試資料集
     */
    data class TestDataSet(
        val organization: Organization,
        val users: List<User>,
        val groups: List<Group>,
        val shiftTypes: List<ShiftType>,
        val requests: List<Request>,
        val rules: List<SchedulingRule>,
        val schedules: List<Schedule>,
        val assignments: List<Assignment>,
        val manpowerPlans: List<ManpowerPlan> // 移除 reservations
        // val reservations: List<Reservation> // 移除
    )

    /**
     * 產生一份完整的測試資料集 (移除 reservations)
     */
    fun generateCompleteTestDataSet(
        orgName: String,
        ownerId: String,
        testMemberEmail: String
    ): TestDataSet {
        val orgCode = (1..8).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString("")
        val org = generateOrganization(name = orgName, ownerId = ownerId, orgCode = orgCode)
        var users = generateUsers(org.id)
        val shiftTypes = generateShiftTypes(org.id) // 先產生班別
        var groups = generateGroups(org.id, users.map { it.id }, shiftTypes) // 傳入班別以設定 rotationState
        val month = DateUtils.getCurrentMonthString()

        if (testMemberEmail.isNotBlank()) {
            val testMemberId = "test-member-${UUID.randomUUID().toString().take(8)}"
            val testMember = User(
                id = testMemberId,
                orgIds = listOf(org.id),
                currentOrgId = org.id,
                email = testMemberEmail,
                name = "測試成員",
                role = "member",
                joinedAt = Date(),
                employmentStatus = mapOf(org.id to "active")
            )
            users = users + testMember
            // 將測試成員加入第一個群組
            groups = groups.mapIndexed { index, group ->
                if (index == 0) group.copy(memberIds = group.memberIds + testMemberId) else group
            }
        }

        val requests = generateRequests(org.id, users, month)
        val rules = getHospitalNursePractitionerRules()

        val schedulesAndAssignments = groups.map { group ->
            val groupUsers = users.filter { it.id in group.memberIds }
            generateConsistentScheduleForGroup(org.id, group, groupUsers, month)
        }
        val schedules = schedulesAndAssignments.map { it.first }
        val assignments = schedulesAndAssignments.flatMap { it.second }

        // 產生人力規劃
        val manpowerPlans = groups.map { generateManpowerPlan(org.id, it.id, month) }
        // 不再產生預約資料
        // val reservations = groups.flatMap { group -> ... }

        return TestDataSet(
            organization = org,
            users = users,
            groups = groups,
            shiftTypes = shiftTypes,
            requests = requests,
            rules = rules,
            schedules = schedules,
            assignments = assignments,
            manpowerPlans = manpowerPlans
            // reservations = reservations // 移除
        )
    }
}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲