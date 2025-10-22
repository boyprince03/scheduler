// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改確認 ▼▼▼▼▼▼▼▼▼▼▼▼
package stevedaydream.scheduler.domain.scheduling

import android.util.Log
import stevedaydream.scheduler.data.model.*
import stevedaydream.scheduler.domain.scheduling.rules.RuleContext
import stevedaydream.scheduler.domain.scheduling.rules.RuleViolation
import stevedaydream.scheduler.data.model.SchedulingRule as SchedulingRuleData
import stevedaydream.scheduler.domain.scheduling.rules.SchedulingRule as SchedulingRuleInterface
import javax.inject.Inject // ✅ 確認引入 Inject

/**
 * BacktrackingSolver 的工廠介面
 */
interface BacktrackingSolverFactory {
    fun create(
        users: List<User>,
        numDays: Int,
        dates: List<String>,
        initialSchedule: Map<String, Map<String, String>>,
        initialQuotas: Map<String, Map<String, Int>>,
        shiftTypes: List<ShiftType>,
        dbRules: List<SchedulingRuleData>,
        ruleEngine: RuleEngine // Factory 需要知道 RuleEngine
    ): BacktrackingSolver
}

/**
 * BacktrackingSolverFactory 的實作
 */
class BacktrackingSolverFactoryImpl @Inject constructor() : BacktrackingSolverFactory { // ✅ 確認有 @Inject constructor()
    override fun create(
        users: List<User>,
        numDays: Int,
        dates: List<String>,
        initialSchedule: Map<String, Map<String, String>>,
        initialQuotas: Map<String, Map<String, Int>>,
        shiftTypes: List<ShiftType>,
        dbRules: List<SchedulingRuleData>,
        ruleEngine: RuleEngine
    ): BacktrackingSolver {
        return BacktrackingSolver(
            users, numDays, dates, initialSchedule, initialQuotas, shiftTypes, dbRules, ruleEngine
        )
    }
}


/**
 * 使用回溯法 (Backtracking) 的排班求解器 (類別本身保持不變)
 */
class BacktrackingSolver(
    private val users: List<User>,
    private val numDays: Int,
    private val dates: List<String>,
    private val initialSchedule: Map<String, Map<String, String>>,
    private val initialQuotas: Map<String, Map<String, Int>>,
    private val shiftTypes: List<ShiftType>,
    private val dbRules: List<SchedulingRuleData>,
    private val ruleEngine: RuleEngine
) {
    // ... (BacktrackingSolver 的其餘內容不變) ...
    private val TAG = "BacktrackingSolver"
    private val EMPTY = "."
    private val schedule: MutableMap<String, MutableMap<String, String>> = initialSchedule.mapValues { it.value.toMutableMap() }.toMutableMap()
    private val remainingQuotas: MutableMap<String, MutableMap<String, Int>> = initialQuotas.mapValues { it.value.toMutableMap() }.toMutableMap()
    private val shiftTryOrder: List<String> = shiftTypes.filter { it.shortCode != "OFF" }.map { it.id } + (shiftTypes.find { it.shortCode == "OFF" }?.id ?: "")
    private var solutionFound = false
    private var finalSchedule: Map<String, Map<String, String>>? = null

    fun solve(): Boolean {
        Log.d(TAG, "--- Backtracking Solver 啟動 ---")
        solutionFound = false
        finalSchedule = null
        if (users.isEmpty() || numDays == 0) {
            Log.e(TAG, "使用者列表或天數為空，無法求解")
            return false
        }
        val success = backtrackSolve(0, 0)
        if (success) {
            Log.d(TAG, "--- 成功找到解！ ---")
            finalSchedule = schedule.mapValues { it.value.toMap() }
        } else {
            Log.w(TAG, "--- 未找到可行解 ---")
        }
        return success
    }

    fun getSolution(): Map<String, Map<String, String>>? {
        return finalSchedule
    }

    private fun backtrackSolve(dateIndex: Int, userIndex: Int): Boolean {
        var nextDateIndex = dateIndex
        var nextUserIndex = userIndex
        if (nextUserIndex >= users.size) {
            nextDateIndex++
            nextUserIndex = 0
        }
        if (nextDateIndex >= numDays) {
            solutionFound = true
            return true
        }
        val currentDate = dates[nextDateIndex].split("-").last()
        val currentUser = users[nextUserIndex]
        if (schedule[currentUser.id]?.get(currentDate) != null && schedule[currentUser.id]?.get(currentDate) != EMPTY) {
            return backtrackSolve(nextDateIndex, nextUserIndex + 1)
        }
        for (shiftId in shiftTryOrder) {
            if (shiftId.isBlank()) continue
            if (isValid(currentUser.id, currentDate, shiftId)) {
                schedule.getOrPut(currentUser.id) { mutableMapOf() }[currentDate] = shiftId
                remainingQuotas[currentUser.id]?.let { quotas ->
                    if (quotas.containsKey(shiftId)) {
                        quotas[shiftId] = (quotas[shiftId] ?: 1) - 1
                    }
                }
                if (backtrackSolve(nextDateIndex, nextUserIndex + 1)) {
                    return true
                }
                schedule[currentUser.id]?.set(currentDate, EMPTY)
                remainingQuotas[currentUser.id]?.let { quotas ->
                    if (quotas.containsKey(shiftId)) {
                        quotas[shiftId] = (quotas[shiftId] ?: 0) + 1
                    }
                }
            }
        }
        return false
    }

    private fun isValid(userId: String, day: String, shiftId: String): Boolean {
        val userQuotas = remainingQuotas[userId]
        if (userQuotas != null && userQuotas.containsKey(shiftId)) {
            if ((userQuotas[shiftId] ?: 0) <= 0) {
                return false
            }
        }
        val currentUser = users.find { it.id == userId } ?: return false
        val assignmentsForUser = schedule[userId] ?: emptyMap()
        val simulatedAssignment = Assignment(userId = userId, dailyShifts = assignmentsForUser + mapOf(day to shiftId))
        val hardRules = dbRules.filter { it.ruleType == "hard" && it.isEnabled }
        val violations = ruleEngine.validate(currentUser, simulatedAssignment, shiftTypes, hardRules)
        return violations.isEmpty()
    }

    private fun printSchedule() {
        val header = " ".repeat(10) + dates.joinToString(" ") { it.split("-").last() }
        Log.d(TAG, header)
        Log.d(TAG, "-".repeat(header.length))
        users.forEach { user ->
            val userRow = schedule[user.id]
            val rowStr = dates.map { date ->
                val day = date.split("-").last()
                val shiftId = userRow?.get(day) ?: EMPTY
                shiftTypes.find { it.id == shiftId }?.shortCode ?: shiftId
            }.joinToString("  ")
            Log.d(TAG, "${user.name.padEnd(8)} | $rowStr")
        }
    }
}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改確認 ▲▲▲▲▲▲▲▲▲▲▲▲