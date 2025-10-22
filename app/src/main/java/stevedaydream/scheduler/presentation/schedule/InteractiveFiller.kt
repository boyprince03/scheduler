// ▼▼▼▼▼▼▼▼▼▼▼▼ 完整修正版 ▼▼▼▼▼▼▼▼▼▼▼▼
// scheduler/domain/scheduling/InteractiveFiller.kt  <-- Note: File path corrected from presentation/schedule
package stevedaydream.scheduler.domain.scheduling

import android.util.Log
import stevedaydream.scheduler.data.model.*
import stevedaydream.scheduler.domain.scheduling.rules.RuleContext
import stevedaydream.scheduler.domain.scheduling.rules.SchedulingRule as SchedulingRuleInterface
import stevedaydream.scheduler.data.model.SchedulingRule as SchedulingRuleData
import javax.inject.Inject

// --- Result Data Class ---
data class FillingStepResult(
    val updatedAssignments: Map<String, Map<String, String>>,
    val updatedWorkQuotas: Map<String, Map<String, Int>>,
    val updatedOffQuota: Map<String, Int>,
    val violations: List<String>
)

// --- Filler Class ---
class InteractiveFiller @Inject constructor(
    private val ruleEngine: RuleEngine
) {

    /**
     * Fills assignments for a *specific* shift type based on current state.
     */
    fun fillSpecificShift(
        shiftToFill: ShiftType?,
        currentAssignments: Map<String, Map<String, String>>,
        currentWorkQuotas: Map<String, Map<String, Int>>,
        currentOffQuota: Map<String, Int>,
        dates: List<String>,
        manpowerPlan: ManpowerPlan,
        orderedUsers: List<User>,
        allUsers: List<User>,
        shiftTypes: List<ShiftType>,
        dbRules: List<SchedulingRuleData>,
        offShiftForNFiller: ShiftType? = null
    ): FillingStepResult {
        if (shiftToFill == null) {
            return FillingStepResult(
                currentAssignments,
                currentWorkQuotas,
                currentOffQuota,
                emptyList()
            )
        }

        val workingAssignments = currentAssignments.mapValues { it.value.toMutableMap() }.toMutableMap()
        val workingWorkQuotas = currentWorkQuotas.mapValues { it.value.toMutableMap() }.toMutableMap()
        val workingOffQuota = currentOffQuota.toMutableMap()
        val stepViolations = mutableListOf<String>()
        val userMap = allUsers.associateBy { it.id }
        val isFillingN = shiftToFill.name == "值班(夜)" && offShiftForNFiller != null
        val sortByShiftCount = shiftToFill.name == "值班(夜)" || shiftToFill.name == "值班(日)"

        Log.d("InteractiveFiller", "Starting to fill ${shiftToFill.name}...")

        dates.forEach { date ->
            val day = date.split("-").last()
            val requiredCount = manpowerPlan.dailyRequirements[day]?.requirements?.get(shiftToFill.id) ?: 0
            val assignedCount = workingAssignments.values.count { it[day] == shiftToFill.id }
            var needed = requiredCount - assignedCount

            if (needed > 0) {
                val availableUserIds = allUsers.map { it.id }
                    .filter { userId -> workingAssignments[userId]?.get(day) == null }

                val validCandidates = availableUserIds.filter { userId ->
                    val user = userMap[userId]
                    if (user == null) {
                        Log.w("FillSpecificShift", "User not found: $userId")
                        return@filter false
                    }
                    val hasQuota = (workingWorkQuotas[userId]?.get(shiftToFill.id) ?: 0) > 0
                    val passesHardRules = checkAllHardRulesRealtime( // Call the (now public) function
                        user, day, shiftToFill.id, workingAssignments, shiftTypes, dbRules
                    )
                    hasQuota && passesHardRules
                }

                val sortedCandidates = if (sortByShiftCount) {
                    validCandidates.sortedBy { userId ->
                        countAssignedShifts(userId, shiftToFill.id, workingAssignments)
                    }
                } else {
                    validCandidates.sortedBy { userId -> orderedUsers.indexOfFirst { it.id == userId } }
                }

                val usersToAssign = sortedCandidates.take(needed)
                usersToAssign.forEach { userId ->
                    val user = userMap[userId] ?: return@forEach

                    workingAssignments.getOrPut(userId) { mutableMapOf() }[day] = shiftToFill.id
                    workingWorkQuotas[userId]?.set(
                        shiftToFill.id,
                        (workingWorkQuotas[userId]?.get(shiftToFill.id) ?: 1) - 1
                    )
                    needed--
                    Log.d("InteractiveFiller", "Assigned ${shiftToFill.shortCode} to ${user.name} on day $day")

                    // ✅ N 班特殊處理：預填 OFF
                    if (isFillingN && offShiftForNFiller != null) {
                        val nextDayInt = day.toIntOrNull()?.plus(1) ?: 0
                        if (nextDayInt > 0 && nextDayInt <= dates.size) {
                            val nextDayKey = String.format("%02d", nextDayInt)
                            if (workingAssignments[userId]?.get(nextDayKey) == null) {
                                if ((workingOffQuota[userId] ?: 0) > 0) {
                                    if (checkAllHardRulesRealtime( // Call the (now public) function
                                            user, nextDayKey, offShiftForNFiller.id,
                                            workingAssignments, shiftTypes, dbRules
                                        )
                                    ) {
                                        workingAssignments[userId]?.set(nextDayKey, offShiftForNFiller.id)
                                        workingOffQuota[userId] = (workingOffQuota[userId] ?: 1) - 1
                                        Log.d("InteractiveFiller", "Pre-filled OFF for ${user.name} on day $nextDayKey")
                                    } else {
                                        stepViolations.add("【規則衝突】無法為 ${user.name} 在 $nextDayKey 預填夜班後的 OFF。")
                                    }
                                } else {
                                    stepViolations.add("【配額衝突】無法為 ${user.name} 在 $nextDayKey 預填夜班後的 OFF。")
                                }
                            }
                        }
                    }
                }

                if (needed > 0) {
                    stepViolations.add("【人力衝突】日期 $date 的 ${shiftToFill.name} 缺少 $needed 人力。")
                }
            }
        }

        Log.d("InteractiveFiller", "Finished filling ${shiftToFill.name}. Violations: ${stepViolations.size}")
        return FillingStepResult(
            updatedAssignments = workingAssignments.mapValues { it.value.toMap() },
            updatedWorkQuotas = workingWorkQuotas.mapValues { it.value.toMap() },
            updatedOffQuota = workingOffQuota.toMap(),
            violations = stepViolations
        )
    }

    /**
     * Fills all remaining empty slots with the OFF shift.
     */
    fun fillRemainingWithOff(
        currentAssignments: Map<String, Map<String, String>>,
        currentOffQuota: Map<String, Int>,
        allUsers: List<User>,
        dates: List<String>,
        offShift: ShiftType,
        shiftTypes: List<ShiftType>,
        dbRules: List<SchedulingRuleData>
    ): FillingStepResult {
        val workingAssignments = currentAssignments.mapValues { it.value.toMutableMap() }.toMutableMap()
        val workingOffQuota = currentOffQuota.toMutableMap()
        val stepViolations = mutableListOf<String>()
        val userMap = allUsers.associateBy { it.id }

        Log.d("InteractiveFiller", "Starting to fill remaining with OFF...")

        allUsers.forEach { user ->
            dates.forEach { date ->
                val day = date.split("-").last()
                if (workingAssignments[user.id]?.get(day) == null) {
                    if ((workingOffQuota[user.id] ?: 0) > 0) {
                        if (checkAllHardRulesRealtime( // Call the (now public) function
                                user, day, offShift.id, workingAssignments, shiftTypes, dbRules
                            )
                        ) {
                            workingAssignments.getOrPut(user.id) { mutableMapOf() }[day] = offShift.id
                            workingOffQuota[user.id] = (workingOffQuota[user.id] ?: 1) - 1
                        } else {
                            stepViolations.add("【OFF 規則衝突】無法為 ${user.name} 在 $date 填入 OFF。")
                        }
                    } else {
                        stepViolations.add("【OFF 配額衝突】${user.name} 在 $date 填入 OFF 時配額不足！")
                    }
                }
            }
        }

        workingOffQuota.forEach { (userId, quota) ->
            if (quota < 0) {
                stepViolations.add("【配額錯誤】${userMap[userId]?.name ?: userId} 的 OFF 配額變為負數！")
            }
        }

        Log.d("InteractiveFiller", "Finished filling OFF. Violations: ${stepViolations.size}")
        return FillingStepResult(
            updatedAssignments = workingAssignments.mapValues { it.value.toMap() },
            updatedWorkQuotas = emptyMap(), // ✅ 不改變工作配額
            updatedOffQuota = workingOffQuota.toMap(),
            violations = stepViolations
        )
    }

    // ✅ 檢查硬性規則 - 移除 private 修飾符
    internal fun checkAllHardRulesRealtime( // Changed from private to internal (or public/default)
        user: User,
        day: String,
        shiftIdToAssign: String,
        currentAssignments: Map<String, out Map<String, String>>,
        localShiftTypes: List<ShiftType>,
        dbRules: List<SchedulingRuleData>
    ): Boolean {
        val simulatedUserAssignments = currentAssignments[user.id]?.plus(day to shiftIdToAssign)
            ?: mapOf(day to shiftIdToAssign)
        val context = RuleContext(user, simulatedUserAssignments, localShiftTypes)

        for (dbRuleData in dbRules.filter { it.ruleType == "hard" && it.isEnabled }) {
            val ruleImpl: SchedulingRuleInterface? = ruleEngine.findRuleImplementation(dbRuleData.ruleName)
            if (ruleImpl != null) {
                val violation = ruleImpl.evaluate(context, dbRuleData.parameters)
                if (violation != null) {
                    return false // ✅ 違反規則
                }
            } else {
                Log.w("CheckHardRules", "找不到規則實作: ${dbRuleData.ruleName}")
            }
        }
        return true // ✅ 所有規則通過
    }

    // ✅ 計算已排班次數 - 保持 private 或改為 internal
    private fun countAssignedShifts( // Keep private or make internal if needed elsewhere
        userId: String,
        shiftId: String,
        userAssignments: Map<String, Map<String, String>>
    ): Int {
        return userAssignments[userId]?.values?.count { it == shiftId } ?: 0
    }
}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修正結束 ▲▲▲▲▲▲▲▲▲▲▲▲