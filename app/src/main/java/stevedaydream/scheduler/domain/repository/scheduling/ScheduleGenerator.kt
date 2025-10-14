// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
package stevedaydream.scheduler.domain.scheduling

import stevedaydream.scheduler.data.model.*
import stevedaydream.scheduler.domain.repository.scheduling.rules.MinRestBetweenShiftsRule
import stevedaydream.scheduler.domain.scheduling.rules.MaxConsecutiveWorkDaysRule
import stevedaydream.scheduler.domain.scheduling.rules.NightShiftFollowupRule
import stevedaydream.scheduler.util.DateUtils
import java.util.*

/**
 * 排班生成器
 * 使用以人力規劃為驅動的演算法，並提供備用方案
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
        var totalScore = 0

        val offShift = shiftTypes.find { it.shortCode == "OFF" }
        if (offShift == null) {
            return ScheduleGenerationResult(
                schedule = Schedule(orgId = orgId, groupId = groupId, month = month, status = "error"),
                assignments = emptyList(),
                score = -9999,
                violations = listOf("關鍵錯誤：找不到代號為 'OFF' 的休假班別。")
            )
        }

        val userAssignments = users.associate { user ->
            user.id to mutableMapOf<String, String>()
        }.toMutableMap()

        // 1. 優先處理已批准的休假申請
        requests.filter { it.status == "approved" && it.type == "leave" }
            .forEach { request ->
                val day = request.date.split("-").last()
                userAssignments[request.userId]?.set(day, offShift.id)
            }

        // ✅ 2. 檢查人力規劃是否存在且有效
        val isManpowerPlanValid = manpowerPlan?.dailyRequirements?.isNotEmpty() == true

        if (isManpowerPlanValid) {
            // ✅ 2a. 如果人力規劃有效，則根據規劃進行排班
            dates.forEach { date ->
                val day = date.split("-").last()
                val dailyPlan = manpowerPlan!!.dailyRequirements[day]
                val availableUsers = users.filter { user -> userAssignments[user.id]?.get(day) == null }.shuffled().toMutableList()

                dailyPlan?.requirements?.forEach { (shiftTypeId, requiredCount) ->
                    val usersToAssign = availableUsers.take(requiredCount)
                    usersToAssign.forEach { user ->
                        userAssignments[user.id]?.set(day, shiftTypeId)
                        availableUsers.remove(user)
                    }
                }
                availableUsers.forEach { user -> userAssignments[user.id]?.set(day, offShift.id) }
            }
        } else {
            // ✅ 2b. 如果人力規劃無效或不存在，則執行備用方案 (每日一D一N)
            val dayDutyShift = shiftTypes.find { it.name == "值班(日)" }
            val nightDutyShift = shiftTypes.find { it.name == "值班(夜)" }

            if (dayDutyShift != null && nightDutyShift != null) {
                dates.forEach { date ->
                    val day = date.split("-").last()
                    val availableUsers = users.filter { user -> userAssignments[user.id]?.get(day) == null }.shuffled().toMutableList()
                    if (availableUsers.size >= 2) {
                        val dayDutyUser = availableUsers.removeAt(0)
                        userAssignments[dayDutyUser.id]?.set(day, dayDutyShift.id)
                        val nightDutyUser = availableUsers.removeAt(0)
                        userAssignments[nightDutyUser.id]?.set(day, nightDutyShift.id)
                    }
                    availableUsers.forEach { user -> userAssignments[user.id]?.set(day, offShift.id) }
                }
            }
        }

        // 3. 對最終生成的完整班表進行事後規則檢查與計分
        val enabledDbRules = rules.filter { it.isEnabled }
        val allViolations = mutableListOf<String>()

        val userAssignmentObjects = users.map { user ->
            Assignment(
                scheduleId = "temp",
                userId = user.id,
                userName = user.name,
                dailyShifts = userAssignments[user.id] ?: emptyMap()
            )
        }

        userAssignmentObjects.forEach { assignment ->
            val user = users.find { it.id == assignment.userId }!!
            val violations = ruleEngine.validate(user, assignment, shiftTypes, enabledDbRules)
            if (violations.isNotEmpty()) {
                allViolations.addAll(violations.map { it.message })
                violations.forEach { violation ->
                    val dbRule = enabledDbRules.find { it.ruleName == violation.ruleName }
                    dbRule?.let { totalScore += it.penaltyScore }
                }
            }
        }

        // ✅ 如果沒有使用人力規劃，則在違規列表中加入提示
        if (!isManpowerPlanValid) {
            allViolations.add(0, "注意：未找到有效人力規劃，已使用備用規則排班。")
        }

        // 4. 建立最終的 Schedule 和 Assignment 物件
        val finalSchedule = Schedule(
            id = UUID.randomUUID().toString(),
            orgId = orgId,
            groupId = groupId,
            month = month,
            status = "draft",
            generatedAt = Date(),
            totalScore = totalScore,
            violatedRules = allViolations,
            generationMethod = "smart"
        )

        val finalAssignments = userAssignmentObjects.map {
            it.copy(scheduleId = finalSchedule.id, id = UUID.randomUUID().toString())
        }

        return ScheduleGenerationResult(finalSchedule, finalAssignments, totalScore, allViolations)
    }
}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲