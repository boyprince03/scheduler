package stevedaydream.scheduler.util

import stevedaydream.scheduler.data.model.*
import java.util.*
import kotlin.math.absoluteValue

/**
 * 測試資料產生器 (已更新)
 * 用於開發階段快速產生符合最新資料模型的測試資料
 */
object TestDataGenerator {

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
     * 產生測試群組
     */
    fun generateGroups(
        orgId: String,
        userIds: List<String>
    ): List<Group> {
        return listOf(
            Group(
                id = "group-${UUID.randomUUID().toString().take(8)}",
                orgId = orgId,
                groupName = "日班團隊",
                memberIds = userIds.take(5)
            ),
            Group(
                id = "group-${UUID.randomUUID().toString().take(8)}",
                orgId = orgId,
                groupName = "夜班團隊",
                memberIds = userIds.drop(5).take(5)
            )
        )
    }

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

    // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
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
    // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲

    /**
     * 產生測試排班規則
     */
    fun generateSchedulingRules(orgId: String): List<SchedulingRule> {
        return listOf(
            SchedulingRule(
                id = "rule-consecutive-work", orgId = orgId, ruleName = "連續上班不超過N天",
                description = "避免員工因連續工作過多天而過勞。", ruleType = "soft", penaltyScore = -50,
                isEnabled = true, parameters = mapOf("maxDays" to "6")
            ),
            SchedulingRule(
                id = "rule-rest-between-shifts", orgId = orgId, ruleName = "輪班間隔需大於N小時",
                description = "確保員工在兩次輪班之間有足夠的休息時間。", ruleType = "hard", penaltyScore = -1000,
                isEnabled = true, parameters = mapOf("minHours" to "11")
            )
        )
    }

    /**
     * 產生測試班表
     */
    fun generateSchedule(
        orgId: String,
        groupId: String,
        month: String = DateUtils.getCurrentMonthString()
    ): Schedule {
        return Schedule(
            id = "schedule-${UUID.randomUUID().toString().take(8)}",
            orgId = orgId, groupId = groupId, month = month, status = "draft",
            generatedAt = Date(), totalScore = -50, violatedRules = listOf("王小明: 連續上班 7 天，超過上限 6 天")
        )
    }

    /**
     * 產生測試班表分配
     */
    fun generateAssignments(
        scheduleId: String,
        users: List<User>,
        month: String = DateUtils.getCurrentMonthString()
    ): List<Assignment> {
        val dates = DateUtils.getDatesInMonth(month)
        val shiftTypes = listOf("off", "day-s", "night-n", "day-d") // 使用更新後的班別 ID
        return users.map { user ->
            val dailyShifts = dates.associate { date ->
                val day = date.split("-").last()
                val dayInt = day.toInt()
                val shiftIndex = (dayInt + user.id.hashCode().absoluteValue) % shiftTypes.size
                day to shiftTypes[shiftIndex]
            }
            Assignment(
                id = "assignment-${user.id}-${UUID.randomUUID().toString().take(4)}",
                scheduleId = scheduleId, userId = user.id, userName = user.name, dailyShifts = dailyShifts
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
        val assignments: List<Assignment>
    )

    /**
     * 產生一份完整的測試資料集
     */
    fun generateCompleteTestDataSet(
        orgName: String,
        ownerId: String,
        testMemberEmail: String
    ): TestDataSet {
        val orgCode = (1..8).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString("")
        val org = generateOrganization(name = orgName, ownerId = ownerId, orgCode = orgCode)
        var users = generateUsers(org.id)
        var groups = generateGroups(org.id, users.map { it.id })

        // 如果提供了測試成員 Email，則建立並加入
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

        val shiftTypes = generateShiftTypes(org.id)
        val requests = generateRequests(org.id, users)
        val rules = generateSchedulingRules(org.id)
        val schedules = groups.map { group -> generateSchedule(org.id, group.id) }
        val assignments = schedules.flatMap { schedule ->
            val group = groups.find { it.id == schedule.groupId }
            val groupUsers = users.filter { it.id in (group?.memberIds ?: emptyList()) }
            generateAssignments(schedule.id, groupUsers, schedule.month)
        }

        return TestDataSet(
            organization = org,
            users = users,
            groups = groups,
            shiftTypes = shiftTypes,
            requests = requests,
            rules = rules,
            schedules = schedules,
            assignments = assignments
        )
    }
}