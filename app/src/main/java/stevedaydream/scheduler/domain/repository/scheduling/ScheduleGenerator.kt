package stevedaydream.scheduler.domain.scheduling

import stevedaydream.scheduler.data.model.*
import stevedaydream.scheduler.domain.repository.scheduling.rules.MinRestBetweenShiftsRule
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

    private val PENALTY_CONSECUTIVE_WORK = 50
    private val PENALTY_NO_WEEKLY_REST = 200
    private val BONUS_SHIFT_PREFERENCE = 10
    private val allAvailableRules = listOf(
        MaxConsecutiveWorkDaysRule(),
        MinRestBetweenShiftsRule()
        // ... 在這裡加入更多規則
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
        manpowerPlan: ManpowerPlan? // 接收人力規劃作為參數
    ): ScheduleGenerationResult {
        val dates = DateUtils.getDatesInMonth(month)
        var totalScore = 0

        val workShifts = shiftTypes.filter { it.shortCode != "OFF" }
        val offShift = shiftTypes.find { it.shortCode == "OFF" }
        if (offShift == null) {
            return ScheduleGenerationResult(
                schedule = Schedule(orgId = orgId, groupId = groupId, month = month, status = "error"),
                assignments = emptyList(),
                score = -9999,
                violations = listOf("關鍵錯誤：找不到代號為 'OFF' 的休假班別。")
            )
        }

        // ✅ 修正點 1: 確保 userAssignments 變數被正確定義
        val userAssignments = users.associate { user ->
            user.id to mutableMapOf<String, String>()
        }.toMutableMap()

        // 1. 優先處理已批准的休假申請 (高優先級)
        requests.filter { it.status == "approved" && it.type == "leave" }
            .forEach { request ->
                val day = request.date.split("-").last()
                userAssignments[request.userId]?.set(day, offShift.id)
            }

        // 建立請求查詢表以提高效率
        val requestMap = requests.filter { it.status == "approved" && it.type == "shift_preference" }
            .associateBy { "${it.userId}-${it.date}" }

        // 2. 為每一天分配班次
        dates.forEach { date ->
            val day = date.split("-").last()
            val dailyPlan = manpowerPlan?.dailyRequirements?.get(day)

            // 2a. (新邏輯) 優先滿足當天的最低人力需求
            dailyPlan?.requirements?.forEach { (shiftTypeId, requiredCount) ->
                // 找出當天還沒被排任何班(包括休假)的員工
                val availableUsers = users.shuffled().filter { user ->
                    userAssignments[user.id]?.get(day) == null
                }

                // 指派 `requiredCount` 位員工到 `shiftTypeId` 班別
                availableUsers.take(requiredCount).forEach { user ->
                    userAssignments[user.id]?.set(day, shiftTypeId)
                }
            }

            // 2b. (舊邏輯，調整後) 為當天剩餘的員工分配班次
            val usersToAssign = users.filter { user ->
                userAssignments[user.id]?.get(day) == null
            }

            // 為每位員工找到最佳班次
            usersToAssign.forEach { user ->
                // 計算當天可休假人數上限
                val totalRequired = dailyPlan?.requirements?.values?.sum() ?: 0
                val maxLeaveSlots = (users.size - totalRequired).coerceAtLeast(0)
                val currentLeaveCount = userAssignments.values.count { it[day] == offShift.id }

                val availableShifts = if (currentLeaveCount < maxLeaveSlots) {
                    // 如果還可以休假，則休假班別也納入考慮
                    shiftTypes
                } else {
                    // 否則只考慮上班的班別
                    workShifts
                }

                val bestShift = availableShifts.maxByOrNull { shift ->
                    var score = 0
                    // 檢查班次偏好
                    val requestKey = "${user.id}-${date}"
                    requestMap[requestKey]?.let { request ->
                        if (request.details["shiftId"] == shift.id) {
                            score += BONUS_SHIFT_PREFERENCE
                        }
                    }
                    // 增加隨機性以避免每次結果都一樣
                    score += Random.nextInt(5)
                    score
                } ?: workShifts.randomOrNull() ?: offShift // 如果沒有最佳選擇，隨機選一個工作班別，再不行就休假

                userAssignments[user.id]?.set(day, bestShift.id)
            }
        }

        // 3. 檢查規則違反與計分
        val enabledDbRules = rules.filter { it.isEnabled }
        val allViolations = mutableListOf<String>()

        val userAssignmentObjects = users.map { user ->
            Assignment(
                scheduleId = "temp",
                userId = user.id,
                userName = user.name,
                dailyShifts = userAssignments[user.id]!!
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

        // 4. 建立最終的 Schedule 和 Assignment 物件
        val finalSchedule = Schedule(
            id = UUID.randomUUID().toString(),
            orgId = orgId,
            groupId = groupId,
            month = month,
            status = "draft",
            generatedAt = Date(),
            totalScore = totalScore,
            violatedRules = allViolations
        )

        val finalAssignments = userAssignmentObjects.map {
            it.copy(scheduleId = finalSchedule.id, id = UUID.randomUUID().toString())
        }

        return ScheduleGenerationResult(finalSchedule, finalAssignments, totalScore, allViolations)
    }
}