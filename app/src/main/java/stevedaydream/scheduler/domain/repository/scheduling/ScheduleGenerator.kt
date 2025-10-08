package stevedaydream.scheduler.domain.scheduling

import stevedaydream.scheduler.data.model.*
// ✅ 這裡的 import 路徑需要修正

import stevedaydream.scheduler.domain.repository.scheduling.rules.MinRestBetweenShiftsRule
import stevedaydream.scheduler.domain.scheduling.rules.MaxConsecutiveWorkDaysRule
import stevedaydream.scheduler.util.DateUtils
import java.util.*
import kotlin.random.Random
// ✅ 引入新的規則


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
    // ✅ 建立所有可用規則的實例
    private val allAvailableRules = listOf(
        MaxConsecutiveWorkDaysRule(),
        MinRestBetweenShiftsRule()
        // ... 在這裡加入更多規則
    )
    // ✅ 建立規則引擎
    private val ruleEngine = RuleEngine(allAvailableRules)

    fun generateSchedule(
        orgId: String,
        groupId: String,
        month: String,
        users: List<User>,
        shiftTypes: List<ShiftType>,
        requests: List<Request>,
        rules: List<SchedulingRule>
    ): ScheduleGenerationResult {
        val dates = DateUtils.getDatesInMonth(month)
        val violations = mutableListOf<String>()
        var totalScore = 0

        val workShifts = shiftTypes.filter { it.shortCode != "OFF" }
        val offShift = shiftTypes.find { it.shortCode == "OFF" }
        if (offShift == null) {
            // 如果沒有定義 "OFF" 班別，這是一個嚴重錯誤，無法繼續排班
            // 回傳一個失敗的結果
            return ScheduleGenerationResult(
                schedule = Schedule(
                    orgId = orgId,
                    groupId = groupId,
                    month = month,
                    status = "error"
                ),
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

        // 建立請求查詢表以提高效率
        val requestMap = requests.filter { it.status == "approved" && it.type == "shift_preference" }
            .associateBy { "${it.userId}-${it.date}" }

        // 2. 為每一天分配班次
        dates.forEach { date ->
            val day = date.split("-").last()

            // 找出當天還沒有被指派班次的員工
            val usersToAssign = users.filter { user ->
                userAssignments[user.id]?.get(day) == null
            }

            // 為每位員工找到最佳班次
            usersToAssign.forEach { user ->
                val bestShift = workShifts.maxByOrNull { shift ->
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
                } ?: workShifts.random() // 如果沒有最佳選擇，隨機選一個

                userAssignments[user.id]?.set(day, bestShift.id)
            }
        }

        // 3. 檢查規則違反與計分
        val enabledDbRules = rules.filter { it.isEnabled }
        val allViolations = mutableListOf<String>()

        // 為每位使用者建立 Assignment 物件以便驗證
        val userAssignmentObjects = users.map { user ->
            Assignment(
                scheduleId = "temp", // 臨時 ID
                userId = user.id,
                userName = user.name,
                dailyShifts = userAssignments[user.id]!!
            )
        }

        userAssignmentObjects.forEach { assignment ->
            val user = users.find { it.id == assignment.userId }!!

            // ✅ 使用規則引擎進行驗證
            val violations = ruleEngine.validate(user, assignment, shiftTypes, enabledDbRules)
            if (violations.isNotEmpty()) {
                allViolations.addAll(violations.map { it.message })

                // 計算懲罰分數
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