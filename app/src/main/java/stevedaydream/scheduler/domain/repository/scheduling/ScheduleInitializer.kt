// ▼▼▼▼▼▼▼▼▼▼▼▼ 新檔案開始 ▼▼▼▼▼▼▼▼▼▼▼▼
package stevedaydream.scheduler.domain.scheduling

import android.util.Log
import stevedaydream.scheduler.data.model.*
import stevedaydream.scheduler.domain.scheduling.rules.RuleContext // 引入 RuleContext
import stevedaydream.scheduler.domain.scheduling.rules.SchedulingRule as SchedulingRuleInterface // 引入 SchedulingRule Interface
import stevedaydream.scheduler.data.model.SchedulingRule as SchedulingRuleData // 引入 SchedulingRule Data class
import kotlin.math.floor

/**
 * 負責排班的第一階段：初始化
 * - 計算配額
 * - 套用固定輪班、已核准休假、使用者偏好
 */
class ScheduleInitializer(
    private val ruleEngine: RuleEngine // 注入 RuleEngine 以便檢查偏好
) {

    // 初始化結果的資料結構
    data class InitializationResult(
        val initialAssignments: MutableMap<String, MutableMap<String, String>>, // Map<UserId, Map<Day, ShiftId>>
        val remainingWorkQuotas: MutableMap<String, MutableMap<String, Int>>, // Map<UserId, Map<ShiftId, Count>>
        val remainingOffQuota: MutableMap<String, Int>, // Map<UserId, Count>
        val initialViolations: MutableList<String>
    )

    /**
     * 執行排班初始化
     */
    fun initializeSchedule(
        allUsers: List<User>,
        orderedUsers: List<User>,
        shiftTypes: List<ShiftType>,
        requests: List<Request>,
        reservations: List<Reservation>,
        dbRules: List<SchedulingRuleData>,
        manpowerPlan: ManpowerPlan,
        offShift: ShiftType,
        dates: List<String>,
        preScheduledRotations: Map<String, Map<String, String>>
    ): InitializationResult {

        val initialViolations = mutableListOf<String>()
        val userAssignments = mutableMapOf<String, MutableMap<String, String>>()
        allUsers.forEach { userAssignments[it.id] = mutableMapOf() }

        // 1. 計算配額
        val workShifts = shiftTypes.filter { it.shortCode != "OFF" }
        val (workQuotas, offQuota) = calculateAllQuotas(manpowerPlan, orderedUsers, workShifts, offShift, dates.size)
        val remainingWorkQuotas = workQuotas.mapValues { (_, quotas) -> quotas.toMutableMap() }.toMutableMap()
        val remainingOffQuota = offQuota.toMutableMap()

        // 2. 套用固定/預排項目 (嚴格模式)
        applyPreScheduledRotationsStrict(preScheduledRotations, userAssignments, remainingWorkQuotas, initialViolations)
        applyApprovedLeavesStrict(requests, offShift, userAssignments, remainingOffQuota, initialViolations)
        applyReservationsStrict(reservations, userAssignments, shiftTypes, remainingWorkQuotas, remainingOffQuota, dbRules, allUsers, initialViolations)

        return InitializationResult(
            initialAssignments = userAssignments,
            remainingWorkQuotas = remainingWorkQuotas,
            remainingOffQuota = remainingOffQuota,
            initialViolations = initialViolations
        )
    }

    // --- 從 ScheduleGenerator 搬移過來的輔助函式 ---

    private fun calculateAllQuotas(
        manpowerPlan: ManpowerPlan,
        orderedUsers: List<User>,
        workShifts: List<ShiftType>,
        offShift: ShiftType,
        daysInMonth: Int
    ): Pair<Map<String, Map<String, Int>>, Map<String, Int>> {
        val workQuotas = mutableMapOf<String, MutableMap<String, Int>>()
        orderedUsers.forEach { workQuotas[it.id] = mutableMapOf() }
        val offQuota = mutableMapOf<String, Int>()
        val numUsers = orderedUsers.size
        if (numUsers == 0) return emptyMap<String, Map<String, Int>>() to emptyMap()

        workShifts.forEach { shift ->
            var totalDemand = 0
            manpowerPlan.dailyRequirements.values.forEach { dailyReq ->
                totalDemand += dailyReq.requirements[shift.id] ?: 0
            }
            if (totalDemand > 0) {
                val baseQuota = floor(totalDemand.toDouble() / numUsers).toInt()
                val remainder = totalDemand % numUsers
                orderedUsers.forEachIndexed { index, user ->
                    val userQuota = if (index < remainder) baseQuota + 1 else baseQuota
                    workQuotas[user.id]?.set(shift.id, userQuota)
                }
            } else {
                orderedUsers.forEach { user -> workQuotas[user.id]?.set(shift.id, 0) }
            }
        }

        orderedUsers.forEach { user ->
            val totalWorkQuota = workQuotas[user.id]?.values?.sum() ?: 0
            offQuota[user.id] = (daysInMonth - totalWorkQuota).coerceAtLeast(0)
        }
        return workQuotas.mapValues { it.value.toMap() } to offQuota.toMap()
    }

    private fun applyPreScheduledRotationsStrict(
        preScheduledRotations: Map<String, Map<String, String>>,
        userAssignments: MutableMap<String, MutableMap<String, String>>,
        remainingWorkQuotas: MutableMap<String, MutableMap<String, Int>>,
        violations: MutableList<String>
    ) {
        preScheduledRotations.forEach { (userId, dailyShifts) ->
            dailyShifts.forEach { (day, shiftId) ->
                val existingAssignment = userAssignments[userId]?.get(day)
                if (existingAssignment == null) {
                    val quotas = remainingWorkQuotas[userId]
                    if (quotas != null && quotas.containsKey(shiftId)) {
                        if ((quotas[shiftId] ?: 0) > 0) {
                            userAssignments.getOrPut(userId) { mutableMapOf() }[day] = shiftId
                            quotas[shiftId] = (quotas[shiftId] ?: 1) - 1
                        } else {
                            violations.add("【輪替配額衝突】使用者 $userId 在 $day 的輪替班 $shiftId 因配額不足而未排入。")
                        }
                    } else {
                        userAssignments.getOrPut(userId) { mutableMapOf() }[day] = shiftId
                    }
                } else if (existingAssignment != shiftId) {
                    violations.add("【輪替衝突】使用者 $userId 在 $day 的輪替班 $shiftId 與先前排定的班別 ($existingAssignment) 衝突，未排入。")
                }
            }
        }
    }

    private fun applyApprovedLeavesStrict(
        requests: List<Request>,
        offShift: ShiftType,
        userAssignments: MutableMap<String, MutableMap<String, String>>,
        remainingOffQuota: MutableMap<String, Int>,
        violations: MutableList<String>
    ) {
        requests.filter { it.status == "approved" && it.type == "leave" }
            .forEach { request ->
                val day = request.date.split("-").last()
                val existingAssignment = userAssignments[request.userId]?.get(day)
                if (existingAssignment == null) {
                    if ((remainingOffQuota[request.userId] ?: 0) > 0) {
                        userAssignments.getOrPut(request.userId) { mutableMapOf() }[day] = offShift.id
                        remainingOffQuota[request.userId] = (remainingOffQuota[request.userId] ?: 1) - 1
                    } else {
                        violations.add("【休假配額衝突】${request.userName} 於 ${request.date} 的休假配額不足，未排入。")
                    }
                } else if (existingAssignment != offShift.id) {
                    violations.add("【休假衝突】${request.userName} 於 ${request.date} 的休假與輪替班($existingAssignment)衝突，未排入。")
                }
            }
    }

    private fun applyReservationsStrict(
        reservations: List<Reservation>,
        userAssignments: MutableMap<String, MutableMap<String, String>>,
        localShiftTypes: List<ShiftType>,
        remainingWorkQuotas: MutableMap<String, MutableMap<String, Int>>,
        remainingOffQuota: MutableMap<String, Int>,
        dbRules: List<SchedulingRuleData>,
        allUsers: List<User>, // 需要 allUsers 列表來查找 User 物件
        violations: MutableList<String> // Changed from violations to initialViolations
    ) {
        val offShiftId = localShiftTypes.find { it.shortCode == "OFF" }?.id ?: ""
        val shiftTypeMap = localShiftTypes.associateBy { it.id }
        val userMap = allUsers.associateBy { it.id } // 建立 User ID 到 User 物件的映射

        reservations.forEach { reservation ->
            reservation.dailyShifts.entries.sortedBy { entry -> entry.key }.forEach { (day, preferences) ->
                if (userAssignments[reservation.userId]?.get(day) == null && preferences.isNotEmpty()) {
                    var preferenceAssigned = false
                    for (shiftId in preferences) {
                        val isOffShift = shiftId == offShiftId
                        val currentQuota = if (isOffShift) {
                            remainingOffQuota[reservation.userId] ?: 0
                        } else {
                            remainingWorkQuotas[reservation.userId]?.get(shiftId) ?: 0
                        }

                        // 查找 User 物件
                        val user = userMap[reservation.userId]
                        if (user == null) {
                            Log.w("ApplyReservations", "找不到使用者 ${reservation.userId}")
                            continue // 跳過此預約
                        }

                        if (currentQuota > 0 && checkAllHardRulesRealtime(user, day, shiftId, userAssignments, localShiftTypes, dbRules)) {
                            userAssignments.getOrPut(reservation.userId) { mutableMapOf() }[day] = shiftId
                            if (isOffShift) {
                                remainingOffQuota[reservation.userId] = currentQuota - 1
                            } else {
                                remainingWorkQuotas[reservation.userId]?.set(shiftId, currentQuota - 1)
                            }
                            preferenceAssigned = true
                            Log.d("ApplyReservations", "Assigned preference ${shiftTypeMap[shiftId]?.name} for ${reservation.userName} on day $day.")
                            break
                        } else {
                            val reason = if (currentQuota <= 0) "quota limit" else "hard rule violation"
                            Log.d("ApplyReservations", "Skipped preference ${shiftTypeMap[shiftId]?.name} for ${reservation.userName} on day $day due to $reason.")
                        }
                    }
                    if (!preferenceAssigned) {
                        Log.w("ApplyReservations", "【Preference Conflict】No valid preference found for ${reservation.userName} on day $day.")
                        violations.add("【偏好衝突】使用者 ${reservation.userName} 在 $day 的所有偏好均無法滿足 (配額或規則限制)。") // Add violation message
                    }
                }
            }
        }
    }


    /**
     * 即時檢查所有硬性規則 (從 ScheduleGenerator 搬過來)
     * @param user 要檢查的 User 物件
     */
    private fun checkAllHardRulesRealtime(
        user: User, // 改為接收 User 物件
        day: String,
        shiftIdToAssign: String,
        currentAssignments: Map<String, Map<String, String>>,
        localShiftTypes: List<ShiftType>,
        dbRules: List<SchedulingRuleData>
    ): Boolean {
        val simulatedUserAssignments = currentAssignments[user.id]?.plus(day to shiftIdToAssign) ?: mapOf(day to shiftIdToAssign)
        val context = RuleContext(user, simulatedUserAssignments, localShiftTypes)

        for (dbRuleData in dbRules.filter { it.ruleType == "hard" && it.isEnabled }) {
            val ruleImpl: SchedulingRuleInterface? = ruleEngine.findRuleImplementation(dbRuleData.ruleName) // 使用 ruleEngine 查找
            if (ruleImpl != null) {
                val violation = ruleImpl.evaluate(context, dbRuleData.parameters)
                if (violation != null) {
                    return false
                }
            } else {
                Log.w("checkHardRules", "找不到規則 ${dbRuleData.ruleName} 的實作")
            }
        }
        return true
    }
}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 新檔案結束 ▲▲▲▲▲▲▲▲▲▲▲▲
