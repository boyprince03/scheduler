// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
package stevedaydream.scheduler.domain.scheduling

import stevedaydream.scheduler.data.model.*
import stevedaydream.scheduler.domain.repository.scheduling.rules.MinRestBetweenShiftsRule
import stevedaydream.scheduler.domain.scheduling.rules.MaxConsecutiveWorkDaysRule
import stevedaydream.scheduler.domain.scheduling.rules.NightShiftFollowupRule
import stevedaydream.scheduler.domain.scheduling.rules.RuleViolation
import stevedaydream.scheduler.util.DateUtils
import java.util.*

/**
 * 具備彈性的優先級排班生成器
 * 透過「回溯檢查」機制，允許連續 N 班，同時阻止 N 接 D/S 的情況
 */
class ScheduleGenerator {

    data class ScheduleGenerationResult(
        val schedule: Schedule,
        val assignments: List<Assignment>,
        val score: Int,
        val violations: List<String>
    )

    private val allAvailableRules = listOf(
        MaxConsecutiveWorkDaysRule(),
        MinRestBetweenShiftsRule(),
        NightShiftFollowupRule()
    )
    private val ruleEngine = RuleEngine(allAvailableRules)

    fun generateSchedule(
        orgId: String,
        groupId: String,
        month: String,
        users: List<User>,
        shiftTypes: List<ShiftType>,
        requests: List<Request>,
        rules: List<SchedulingRule>,
        manpowerPlan: ManpowerPlan?
    ): ScheduleGenerationResult {
        val dates = DateUtils.getDatesInMonth(month)

        val offShift = shiftTypes.find { it.shortCode == "OFF" }
        val nShift = shiftTypes.find { it.name == "值班(夜)" }
        val dShift = shiftTypes.find { it.name == "值班(日)" }
        val sShift = shiftTypes.find { it.name == "白班" }

        if (offShift == null || nShift == null || dShift == null || sShift == null || manpowerPlan?.dailyRequirements?.isEmpty() != false) {
            return ScheduleGenerationResult(
                schedule = Schedule(orgId = orgId, groupId = groupId, month = month, status = "error"),
                assignments = emptyList(), score = -9999,
                violations = listOf("錯誤：找不到關鍵班別(OFF,N,D,S)或未設定人力規劃。")
            )
        }

        val userAssignments = users.associate { it.id to mutableMapOf<String, String>() }

        // --- 1. 最高優先級：排入已核准的休假 ---
        requests.filter { it.status == "approved" && it.type == "leave" }
            .forEach { request ->
                val day = request.date.split("-").last()
                userAssignments[request.userId]?.set(day, offShift.id)
            }

        // --- 2. 第二優先級：排 N 班 (只管今天) ---
        dates.forEach { date ->
            val day = date.split("-").last()
            val requiredCount = manpowerPlan.dailyRequirements[day]?.requirements?.get(nShift.id) ?: 0
            if (requiredCount == 0) return@forEach

            val availableUsers = users.filter { userAssignments[it.id]?.get(day) == null }.shuffled()
            val usersToAssign = availableUsers.take(requiredCount)
            usersToAssign.forEach { user -> userAssignments[user.id]?.set(day, nShift.id) }
        }

        // --- 3. 第三 & 第四優先級：排 D 班和 S 班 (回頭看昨天) ---
        val shiftsToProcess = listOf(dShift, sShift)
        shiftsToProcess.forEach { shift ->
            dates.forEach { date ->
                val day = date.split("-").last()
                val requiredCount = manpowerPlan.dailyRequirements[day]?.requirements?.get(shift.id) ?: 0
                if (requiredCount == 0) return@forEach

                // 找出當天空閒，且昨天不是 N 班的員工
                val availableUsers = users.filter { user ->
                    val isAvailableToday = userAssignments[user.id]?.get(day) == null
                    if (!isAvailableToday) return@filter false

                    val yesterdayInt = day.toIntOrNull()?.minus(1) ?: 0
                    if (yesterdayInt <= 0) return@filter true // 如果是第一天，就沒有昨天

                    val yesterdayKey = String.format("%02d", yesterdayInt)
                    val yesterdayShiftId = userAssignments[user.id]?.get(yesterdayKey)

                    yesterdayShiftId != nShift.id // 關鍵：昨天不能是 N 班
                }.shuffled()

                val usersToAssign = availableUsers.take(requiredCount)
                usersToAssign.forEach { user -> userAssignments[user.id]?.set(day, shift.id) }
            }
        }

        // --- 5. 最低優先級：將所有剩餘空格填為 OFF ---
        users.forEach { user ->
            dates.forEach { date ->
                val day = date.split("-").last()
                if (userAssignments[user.id]?.get(day) == null) {
                    userAssignments[user.id]?.set(day, offShift.id)
                }
            }
        }

        // --- 6. 事後評分 ---
        val (finalViolations, finalScore) = validateAllUsers(userAssignments, users, shiftTypes, rules)
        val violationMessages = finalViolations.map { it.message }

        // --- 7. 建立最終結果 ---
        return buildResult(orgId, groupId, month, userAssignments, users, finalScore, violationMessages)
    }

    private fun validateAllUsers(
        assignments: Map<String, Map<String, String>>,
        users: List<User>,
        shiftTypes: List<ShiftType>,
        rules: List<SchedulingRule>
    ): Pair<List<RuleViolation>, Int> {
        val allViolations = mutableListOf<RuleViolation>()
        var totalScore = 0
        val enabledDbRules = rules.filter { it.isEnabled }
        users.forEach { user ->
            val userAssignment = Assignment(dailyShifts = assignments[user.id] ?: emptyMap())
            val violations = ruleEngine.validate(user, userAssignment, shiftTypes, enabledDbRules)
            if (violations.isNotEmpty()) {
                allViolations.addAll(violations)
                violations.forEach { violation ->
                    val dbRule = enabledDbRules.find { it.ruleName == violation.ruleName }
                    dbRule?.let { totalScore += it.penaltyScore }
                }
            }
        }
        return Pair(allViolations, totalScore)
    }

    private fun buildResult(
        orgId: String,
        groupId: String,
        month: String,
        assignments: Map<String, Map<String, String>>,
        users: List<User>,
        finalScore: Int,
        violationMessages: List<String>
    ): ScheduleGenerationResult {
        val finalAssignmentObjects = users.map { user ->
            Assignment(
                id = UUID.randomUUID().toString(),
                scheduleId = "temp",
                userId = user.id,
                userName = user.name,
                dailyShifts = assignments[user.id] ?: emptyMap()
            )
        }
        val finalSchedule = Schedule(
            id = UUID.randomUUID().toString(),
            orgId = orgId, groupId = groupId, month = month, status = "draft",
            generatedAt = Date(), totalScore = finalScore, violatedRules = violationMessages,
            generationMethod = "smart"
        )
        val finalAssignmentsWithId = finalAssignmentObjects.map { it.copy(scheduleId = finalSchedule.id) }
        return ScheduleGenerationResult(finalSchedule, finalAssignmentsWithId, finalScore, violationMessages)
    }
}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲