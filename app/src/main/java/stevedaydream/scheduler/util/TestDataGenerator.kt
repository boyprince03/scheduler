package stevedaydream.scheduler.util

import stevedaydream.scheduler.data.model.*
import java.util.*

/**
 * 測試資料產生器
 * 用於開發階段快速產生測試資料
 */
object TestDataGenerator {

    /**
     * 產生測試組織
     */
    fun generateOrganization(
        id: String = UUID.randomUUID().toString(),
        name: String = "測試公司",
        ownerId: String = "test-owner-001"
    ): Organization {
        return Organization(
            id = id,
            orgName = name,
            ownerId = ownerId,
            createdAt = System.currentTimeMillis(),
            plan = "free",
            // ✅ 修正：使用新的 Features 巢狀類別來傳入參數
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
            User(
                id = "user-${index + 1}",
                orgId = orgId,
                email = "${name.replace("", "")}@test.com",
                name = name,
                role = if (index == 0) "org_admin" else "member",
                joinedAt = System.currentTimeMillis() - (index * 86400000L)
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
                id = "group-001",
                orgId = orgId,
                groupName = "早班組",
                memberIds = userIds.take(5)
            ),
            Group(
                id = "group-002",
                orgId = orgId,
                groupName = "晚班組",
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
                id = "shift-day",
                orgId = orgId,
                name = "早班",
                shortCode = "D",
                startTime = "09:00",
                endTime = "17:00",
                color = "#4A90E2"
            ),
            ShiftType(
                id = "shift-evening",
                orgId = orgId,
                name = "晚班",
                shortCode = "E",
                startTime = "17:00",
                endTime = "01:00",
                color = "#F5A623"
            ),
            ShiftType(
                id = "shift-night",
                orgId = orgId,
                name = "大夜班",
                shortCode = "N",
                startTime = "01:00",
                endTime = "09:00",
                color = "#7B68EE"
            ),
            ShiftType(
                id = "shift-off",
                orgId = orgId,
                name = "休假",
                shortCode = "OFF",
                startTime = "00:00",
                endTime = "00:00",
                color = "#9E9E9E"
            )
        )
    }

    /**
     * 產生測試請求
     */
    fun generateRequests(
        orgId: String,
        users: List<User>,
        month: String = DateUtils.getCurrentMonthString()
    ): List<Request> {
        val dates = DateUtils.getDatesInMonth(month)

        return users.take(3).flatMapIndexed { userIndex, user ->
            dates.take(5).mapIndexed { dateIndex, date ->
                Request(
                    id = "request-${userIndex}-${dateIndex}",
                    orgId = orgId,
                    userId = user.id,
                    userName = user.name,
                    date = date,
                    type = if (dateIndex % 2 == 0) "leave" else "shift_preference",
                    details = mapOf(
                        "reason" to "個人事務",
                        "shiftType" to if (dateIndex % 2 == 0) "OFF" else "D"
                    ),
                    status = when (dateIndex % 3) {
                        0 -> "pending"
                        1 -> "approved"
                        else -> "rejected"
                    },
                    createdAt = System.currentTimeMillis() - (dateIndex * 3600000L)
                )
            }
        }
    }

    /**
     * 產生測試排班規則
     */
    fun generateSchedulingRules(orgId: String): List<SchedulingRule> {
        return listOf(
            SchedulingRule(
                id = "rule-001",
                orgId = orgId,
                ruleName = "輪班間隔需大於11小時",
                ruleType = "hard",
                penaltyScore = -1000,
                isEnabled = true,
                isPremiumFeature = false
            ),
            SchedulingRule(
                id = "rule-002",
                orgId = orgId,
                ruleName = "每週至少休息1天",
                ruleType = "hard",
                penaltyScore = -1000,
                isEnabled = true,
                isPremiumFeature = false
            ),
            SchedulingRule(
                id = "rule-003",
                orgId = orgId,
                ruleName = "連續上班不超過6天",
                ruleType = "soft",
                penaltyScore = -50,
                isEnabled = true,
                isPremiumFeature = false
            ),
            SchedulingRule(
                id = "rule-004",
                orgId = orgId,
                ruleName = "避免連續夜班",
                ruleType = "soft",
                penaltyScore = -30,
                isEnabled = true,
                isPremiumFeature = true
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
            orgId = orgId,
            groupId = groupId,
            month = month,
            status = "draft",
            generatedAt = System.currentTimeMillis(),
            totalScore = 850,
            violatedRules = listOf()
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
        val shiftTypes = listOf("shift-day", "shift-evening", "shift-night", "shift-off")

        return users.map { user ->
            val dailyShifts = dates.associate { date ->
                val day = date.split("-").last()
                val dayInt = day.toInt()

                // 簡單的排班邏輯:每4天一個循環
                val shiftIndex = (dayInt + user.id.hashCode()) % 4
                day to shiftTypes[shiftIndex]
            }

            Assignment(
                id = "assignment-${user.id}",
                scheduleId = scheduleId,
                userId = user.id,
                userName = user.name,
                dailyShifts = dailyShifts
            )
        }
    }

    /**
     * 產生完整的測試資料集
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

    fun generateCompleteTestDataSet(
        orgName: String = "測試公司",
        ownerId: String = "test-owner-001"
    ): TestDataSet {
        val org = generateOrganization(name = orgName, ownerId = ownerId)
        val users = generateUsers(org.id)
        val groups = generateGroups(org.id, users.map { it.id })
        val shiftTypes = generateShiftTypes(org.id)
        val requests = generateRequests(org.id, users)
        val rules = generateSchedulingRules(org.id)

        val schedules = groups.map { group ->
            generateSchedule(org.id, group.id)
        }

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