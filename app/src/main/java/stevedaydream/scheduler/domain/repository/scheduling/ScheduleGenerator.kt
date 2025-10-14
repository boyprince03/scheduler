// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
package stevedaydream.scheduler.domain.scheduling

import stevedaydream.scheduler.data.model.*
import stevedaydream.scheduler.domain.repository.scheduling.rules.MinRestBetweenShiftsRule
import stevedaydream.scheduler.domain.scheduling.rules.NightShiftFollowupRule
import stevedaydream.scheduler.domain.scheduling.rules.MaxConsecutiveWorkDaysRule
import stevedaydream.scheduler.util.DateUtils
import java.util.*
import kotlin.random.Random

/**
 * 排班生成器
 * 使用帶有權重和懲罰分數的演算法來生成更優的排班表
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
        manpowerPlan: ManpowerPlan? // 雖然此次未使用，但保留參數以備未來擴充
    ): ScheduleGenerationResult {
        val dates = DateUtils.getDatesInMonth(month)
        var totalScore = 0

        // 找出關鍵班別的 ID
        val offShift = shiftTypes.find { it.shortCode == "OFF" }
        val dayDutyShift = shiftTypes.find { it.name == "值班(日)" }
        val nightDutyShift = shiftTypes.find { it.name == "值班(夜)" }

        // 確保關鍵班別存在
        if (offShift == null || dayDutyShift == null || nightDutyShift == null) {
            return ScheduleGenerationResult(
                schedule = Schedule(orgId = orgId, groupId = groupId, month = month, status = "error"),
                assignments = emptyList(),
                score = -9999,
                violations = listOf("關鍵錯誤：找不到 'OFF', '值班(日)' 或 '值班(夜)' 的班別。")
            )
        }

        // 初始化每個使用者的空班表
        val userAssignments = users.associate { user ->
            user.id to mutableMapOf<String, String>()
        }.toMutableMap()

        // 1. 優先處理已批准的休假申請
        requests.filter { it.status == "approved" && it.type == "leave" }
            .forEach { request ->
                val day = request.date.split("-").last()
                userAssignments[request.userId]?.set(day, offShift.id)
            }

        // 2. 以「日」為單位，強制填滿 D 班和 N 班
        dates.forEach { date ->
            val day = date.split("-").last()

            // 找出當天可以排班的人 (尚未被排休假的人)
            val availableUsers = users.filter { user ->
                userAssignments[user.id]?.get(day) == null
            }.shuffled().toMutableList()

            // 如果當天可排班人數不足 2 人，無法滿足 D/N 班需求，記錄錯誤並跳到下一天
            if (availableUsers.size < 2) {
                // (可選) 在此處可以加入違規記錄，表示當天人力不足
                return@forEach
            }

            // 隨機指派一人上 D 班
            val dayDutyUser = availableUsers.removeAt(0)
            userAssignments[dayDutyUser.id]?.set(day, dayDutyShift.id)

            // 隨機指派一人上 N 班
            val nightDutyUser = availableUsers.removeAt(0)
            userAssignments[nightDutyUser.id]?.set(day, nightDutyShift.id)

            // 3. 剩下的人全部排休
            availableUsers.forEach { remainingUser ->
                userAssignments[remainingUser.id]?.set(day, offShift.id)
            }
        }

        // 4. 對最終生成的完整班表，進行事後規則檢查與計分
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

        // 5. 建立最終的 Schedule 和 Assignment 物件
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