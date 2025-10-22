// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
package stevedaydream.scheduler.domain.scheduling

import android.util.Log
import stevedaydream.scheduler.data.model.*
import stevedaydream.scheduler.domain.scheduling.rules.RuleContext
import stevedaydream.scheduler.domain.scheduling.rules.SchedulingRule as SchedulingRuleInterface
import stevedaydream.scheduler.data.model.SchedulingRule as SchedulingRuleData


/**
 * 負責排班的第二階段：填充剩餘空格 (使用貪婪法)
 */
class GreedyScheduleFiller(
    private val ruleEngine: RuleEngine // RuleEngine 已注入
) {

    // ... (FillingResult data class 保持不變) ...
    data class FillingResult(
        val finalAssignments: Map<String, Map<String, String>>,
        val accumulatedViolations: List<String>
    )


    // ... (fillSchedule 方法保持不變) ...
    fun fillSchedule(
        initialResult: ScheduleInitializer.InitializationResult,
        dates: List<String>,
        manpowerPlan: ManpowerPlan,
        orderedUsers: List<User>,
        allUsers: List<User>,
        shiftTypes: List<ShiftType>,
        dbRules: List<SchedulingRuleData>
    ): FillingResult {
        val userAssignments = initialResult.initialAssignments
        val remainingWorkQuotas = initialResult.remainingWorkQuotas
        val remainingOffQuota = initialResult.remainingOffQuota
        val violations = initialResult.initialViolations
        val nShift = shiftTypes.find { it.name == "值班(夜)" }
        val sShift = shiftTypes.find { it.name == "白班" }
        val dShift = shiftTypes.find { it.name == "值班(日)" }
        val offShift = shiftTypes.find { it.shortCode == "OFF" }

        if (nShift != null && offShift != null) {
            assignShiftTypeStrict(dates, manpowerPlan, nShift, userAssignments, remainingWorkQuotas, orderedUsers, allUsers, shiftTypes, dbRules, violations, offShift, remainingOffQuota, sortByShiftCount = true)
        }
        if (dShift != null) {
            assignShiftTypeStrict(dates, manpowerPlan, dShift, userAssignments, remainingWorkQuotas, orderedUsers, allUsers, shiftTypes, dbRules, violations, offShift = null, remainingOffQuota = null, sortByShiftCount = true)
        }
        if (sShift != null) {
            assignShiftTypeStrict(dates, manpowerPlan, sShift, userAssignments, remainingWorkQuotas, orderedUsers, allUsers, shiftTypes, dbRules, violations, offShift = null, remainingOffQuota = null, sortByShiftCount = false)
        }
        if (offShift != null) {
            fillRemainingWithOffStrict(userAssignments, allUsers, dates, offShift, shiftTypes, dbRules, remainingOffQuota, remainingWorkQuotas.toMap(), violations)
        }

        return FillingResult(
            finalAssignments = userAssignments.mapValues { it.value.toMap() },
            accumulatedViolations = violations.toList()
        )
    }


    // ... (assignShiftTypeStrict 方法保持不變, 內部呼叫 checkAllHardRulesRealtime) ...
    private fun assignShiftTypeStrict(
        dates: List<String>, manpowerPlan: ManpowerPlan, shiftToAssign: ShiftType?,
        userAssignments: MutableMap<String, MutableMap<String, String>>,
        remainingWorkQuotas: MutableMap<String, MutableMap<String, Int>>,
        orderedUsers: List<User>,
        allUsers: List<User>,
        localShiftTypes: List<ShiftType>,
        dbRules: List<SchedulingRuleData>,
        violations: MutableList<String>,
        offShift: ShiftType?,
        remainingOffQuota: MutableMap<String, Int>?,
        sortByShiftCount: Boolean = false
    ) {
        if (shiftToAssign == null) return
        val userMap = allUsers.associateBy { it.id }

        dates.forEach { date ->
            val day = date.split("-").last()
            val requiredCount = manpowerPlan.dailyRequirements[day]?.requirements?.get(shiftToAssign.id) ?: 0
            val assignedCount = userAssignments.values.count { it[day] == shiftToAssign.id }
            var needed = requiredCount - assignedCount

            if (needed > 0) {
                val availableUserIds = allUsers.map { it.id }
                    .filter { userId -> userAssignments[userId]?.get(day) == null }

                val validCandidates = availableUserIds.filter { userId ->
                    val user = userMap[userId]
                    if (user == null) {
                        Log.w("AssignShift", "找不到使用者 $userId")
                        return@filter false
                    }
                    val hasQuota = (remainingWorkQuotas[userId]?.get(shiftToAssign.id) ?: 0) > 0
                    // ✅ 呼叫 checkAllHardRulesRealtime
                    val passesHardRules = checkAllHardRulesRealtime(user, day, shiftToAssign.id, userAssignments, localShiftTypes, dbRules)
                    hasQuota && passesHardRules
                }

                val sortedCandidates = if (sortByShiftCount) {
                    validCandidates.sortedBy { userId -> countAssignedShifts(userId, shiftToAssign.id, userAssignments) }
                } else {
                    validCandidates.sortedBy { userId -> orderedUsers.indexOfFirst { it.id == userId } }
                }

                val usersToAssign = sortedCandidates.take(needed)
                usersToAssign.forEach { userId ->
                    val user = userMap[userId] ?: return@forEach
                    userAssignments.getOrPut(userId) { mutableMapOf() }[day] = shiftToAssign.id
                    remainingWorkQuotas[userId]?.set(shiftToAssign.id, (remainingWorkQuotas[userId]?.get(shiftToAssign.id) ?: 1) - 1)
                    needed--

                    // 預填 OFF (N 班)
                    if (offShift != null && remainingOffQuota != null && shiftToAssign.name == "值班(夜)") {
                        val nextDayInt = day.toIntOrNull()?.plus(1) ?: 0
                        if (nextDayInt > 0 && nextDayInt <= dates.size) {
                            val nextDayKey = String.format("%02d", nextDayInt)
                            val nextDayAssignment = userAssignments[userId]?.get(nextDayKey)
                            if (nextDayAssignment == null) {
                                if ((remainingOffQuota[userId] ?: 0) > 0) {
                                    // ✅ 呼叫 checkAllHardRulesRealtime
                                    if (checkAllHardRulesRealtime(user, nextDayKey, offShift.id, userAssignments, localShiftTypes, dbRules)) {
                                        userAssignments[userId]?.set(nextDayKey, offShift.id)
                                        remainingOffQuota[userId] = (remainingOffQuota[userId] ?: 1) - 1
                                    } else {
                                        violations.add("【規則衝突】無法為 ${user.name} 在 $nextDayKey 預填夜班後的 OFF (違反規則)。")
                                    }
                                } else {
                                    violations.add("【配額衝突】無法為 ${user.name} 在 $nextDayKey 預填夜班後的 OFF (配額不足)。")
                                }
                            }
                        }
                    }
                } // end forEach usersToAssign

                if (needed > 0) {
                    violations.add("【人力/規則/配額衝突】日期 $date 的 ${shiftToAssign.name} 缺少 $needed 人力 (找不到符合所有強制條件的人員)。")
                }
            } // end if needed > 0
        } // end forEach date
    } // end assignShiftTypeStrict


    // ... (fillRemainingWithOffStrict 方法保持不變, 內部呼叫 checkAllHardRulesRealtime) ...
    private fun fillRemainingWithOffStrict(
        userAssignments: MutableMap<String, MutableMap<String, String>>,
        allUsers: List<User>, dates: List<String>, offShift: ShiftType,
        localShiftTypes: List<ShiftType>,
        dbRules: List<SchedulingRuleData>,
        remainingOffQuota: MutableMap<String, Int>,
        remainingWorkQuotasParam: Map<String, Map<String, Int>>,
        violations: MutableList<String>
    ) {
        val userMap = allUsers.associateBy { it.id }
        allUsers.forEach { user ->
            dates.forEach { date ->
                val day = date.split("-").last()
                if (userAssignments[user.id]?.get(day) == null) {
                    if ((remainingOffQuota[user.id] ?: 0) > 0) {
                        // ✅ 呼叫 checkAllHardRulesRealtime
                        if (checkAllHardRulesRealtime(user, day, offShift.id, userAssignments, localShiftTypes, dbRules)) {
                            userAssignments.getOrPut(user.id) { mutableMapOf() }[day] = offShift.id
                            remainingOffQuota[user.id] = (remainingOffQuota[user.id] ?: 1) - 1
                        } else {
                            violations.add("【OFF 規則衝突】無法為 ${user.name} 在 $date 填入 OFF (違反硬性規則)。班表可能有空格。")
                        }
                    } else {
                        violations.add("【OFF 配額嚴重衝突】${user.name} 在 $date 填入 OFF 時配額不足！班表可能有空格。")
                    }
                }
            }
        }
        remainingOffQuota.forEach { (userId, quota) ->
            if (quota > 0) {
                Log.w("GreedyFiller", "使用者 ${userMap[userId]?.name ?: userId} 的 OFF 配額剩餘 $quota")
            } else if (quota < 0) {
                violations.add("【OFF 配額計算錯誤】${userMap[userId]?.name ?: userId} 的 OFF 配額變為負數 $quota！")
            }
        }
        remainingWorkQuotasParam.forEach { (userId, quotas) ->
            quotas.forEach { (shiftId, quota) ->
                if (quota < 0) {
                    violations.add("【工作配額計算錯誤】${userMap[userId]?.name ?: userId} 的 ${localShiftTypes.find{it.id==shiftId}?.name} 配額變為負數 $quota！")
                }
            }
        }
    }


    // ... (countAssignedShifts 方法保持不變) ...
    private fun countAssignedShifts(userId: String, shiftId: String, userAssignments: Map<String, Map<String, String>>): Int {
        return userAssignments[userId]?.values?.count { it == shiftId } ?: 0
    }


    /**
     * 即時檢查所有硬性規則 - 修改版本
     * @param user 要檢查的 User 物件
     */
    private fun checkAllHardRulesRealtime(
        user: User,
        day: String,
        shiftIdToAssign: String,
        currentAssignments: Map<String, Map<String, String>>, // 使用不可變 Map
        localShiftTypes: List<ShiftType>,
        dbRules: List<SchedulingRuleData>
    ): Boolean {
        // 模擬 assignment
        val simulatedUserAssignments = currentAssignments[user.id]?.plus(day to shiftIdToAssign) ?: mapOf(day to shiftIdToAssign)
        val context = RuleContext(user, simulatedUserAssignments, localShiftTypes)

        // 迭代硬性規則
        for (dbRuleData in dbRules.filter { it.ruleType == "hard" && it.isEnabled }) {
            // ✅ 使用注入的 ruleEngine 查找實作
            val ruleImpl: SchedulingRuleInterface? = ruleEngine.findRuleImplementation(dbRuleData.ruleName)
            if (ruleImpl != null) {
                val violation = ruleImpl.evaluate(context, dbRuleData.parameters)
                if (violation != null) {
                    return false // 違反規則
                }
            } else {
                Log.w("checkHardRules", "在 GreedyScheduleFiller 中找不到規則 ${dbRuleData.ruleName} 的實作")
            }
        }
        return true // 所有規則通過
    }
}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲

