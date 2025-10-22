// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
package stevedaydream.scheduler.domain.scheduling

import android.util.Log
import stevedaydream.scheduler.data.model.*
import stevedaydream.scheduler.domain.repository.scheduling.rules.MinRestBetweenShiftsRule
// Explicit type aliases to resolve ambiguity
import stevedaydream.scheduler.data.model.SchedulingRule as SchedulingRuleData
import stevedaydream.scheduler.domain.scheduling.rules.SchedulingRule as SchedulingRuleInterface
import stevedaydream.scheduler.domain.scheduling.rules.MaxConsecutiveWorkDaysRule
import stevedaydream.scheduler.domain.scheduling.rules.NightShiftFollowupRule
import stevedaydream.scheduler.domain.scheduling.rules.RuleContext
import stevedaydream.scheduler.domain.scheduling.rules.RuleViolation
import stevedaydream.scheduler.util.DateUtils
import java.util.*
import kotlin.math.floor


// SchedulingStrategyType Enum remains the same
enum class SchedulingStrategyType {
    HOSPITAL_BACKTRACKING, // 醫院策略 - 回溯法
    GENERAL, // 通用策略
    HOSPITAL_GREEDY // 醫院策略 - 貪婪法
}

/**
 * 排班生成器，支援不同的排班策略
 */
class ScheduleGenerator {

    // ScheduleGenerationResult remains the same
    data class ScheduleGenerationResult(
        val schedule: Schedule,
        val assignments: List<Assignment>,
        val score: Int,
        val violations: List<String>,
        val warnings: List<String>
    )

    // Rule engine setup remains the same, using the Interface
    private val allAvailableRules: List<SchedulingRuleInterface> = listOf(
        MaxConsecutiveWorkDaysRule(),
        MinRestBetweenShiftsRule(),
        NightShiftFollowupRule()
        // Add other rule implementations here
    )
    private val ruleEngine = RuleEngine(allAvailableRules) // RuleEngine expects List<SchedulingRuleInterface>

    /**
     * 生成排班表的主函數
     * @param rules DB來的啟用規則 (data model)
     */
    fun generateSchedule(
        orgId: String,
        groupId: String,
        month: String,
        users: List<User>,
        shiftTypes: List<ShiftType>,
        requests: List<Request>,
        reservations: List<Reservation>,
        rules: List<SchedulingRuleData>, // Expect Data class from DB
        manpowerPlan: ManpowerPlan?,
        strategy: SchedulingStrategyType = SchedulingStrategyType.HOSPITAL_GREEDY,
        orderedUsers: List<User>? = null,
        preScheduledRotations: Map<String, Map<String, String>>? = null
    ): ScheduleGenerationResult {
        val dates = DateUtils.getDatesInMonth(month)
        val offShift = shiftTypes.find { it.shortCode == "OFF" }

        // Basic checks remain the same
        if (offShift == null || manpowerPlan == null || manpowerPlan.dailyRequirements.isEmpty()) {
            return ScheduleGenerationResult( violations = listOf("錯誤：找不到 OFF 班別或未設定人力規劃。"), schedule = Schedule(orgId = orgId, groupId = groupId, month = month, status = "error"), assignments = emptyList(), score = -9999, warnings = emptyList() )
        }
        if (users.isEmpty()) {
            return ScheduleGenerationResult( violations = listOf("錯誤：沒有參與排班的使用者。"), schedule = Schedule(orgId = orgId, groupId = groupId, month = month, status = "error"), assignments = emptyList(), score = -9999, warnings = emptyList() )
        }

        // Strategy selection remains the same
        return when (strategy) {
            SchedulingStrategyType.HOSPITAL_BACKTRACKING -> {
                if (orderedUsers == null || preScheduledRotations == null) {
                    ScheduleGenerationResult( violations = listOf("錯誤：回溯排班策略缺少必要參數 (orderedUsers 或 preScheduledRotations)。"), schedule = Schedule(orgId = orgId, groupId = groupId, month = month, status = "error"), assignments = emptyList(), score = -9999, warnings = emptyList() )
                } else {
                    Log.d("ScheduleGenerator", "使用 HOSPITAL_BACKTRACKING 策略")
                    generateWithBacktrackingSolver(
                        orgId, groupId, month, users, shiftTypes, requests, reservations, rules, // Pass Data class rules
                        manpowerPlan, offShift, dates, orderedUsers, preScheduledRotations
                    )
                }
            }
            SchedulingStrategyType.HOSPITAL_GREEDY -> {
                if (orderedUsers == null || preScheduledRotations == null) {
                    ScheduleGenerationResult( violations = listOf("錯誤：醫院排班策略缺少必要參數 (orderedUsers 或 preScheduledRotations)。"), schedule = Schedule(orgId = orgId, groupId = groupId, month = month, status = "error"), assignments = emptyList(), score = -9999, warnings = emptyList() )
                } else {
                    Log.d("ScheduleGenerator", "使用 HOSPITAL_GREEDY 策略")
                    generateHospitalScheduleGreedy(
                        orgId, groupId, month, users, shiftTypes, requests, reservations, rules, // Pass Data class rules
                        manpowerPlan, offShift, dates, orderedUsers, preScheduledRotations
                    )
                }
            }
            SchedulingStrategyType.GENERAL -> {
                Log.d("ScheduleGenerator", "使用 GENERAL 策略")
                generateGeneralSchedule(
                    orgId, groupId, month, users, shiftTypes, requests, reservations, rules, // Pass Data class rules
                    manpowerPlan, offShift, dates
                )
            }
        }
    }

    /**
     * 使用 BacktrackingSolver 生成班表的函數
     * @param dbRules DB來的啟用規則 (data model)
     */
    private fun generateWithBacktrackingSolver(
        orgId: String, groupId: String, month: String,
        allUsers: List<User>, shiftTypes: List<ShiftType>,
        requests: List<Request>, reservations: List<Reservation>,
        dbRules: List<SchedulingRuleData>, // Expect Data class
        manpowerPlan: ManpowerPlan,
        offShift: ShiftType, dates: List<String>,
        orderedUsers: List<User>, preScheduledRotations: Map<String, Map<String, String>>
    ): ScheduleGenerationResult {

        // --- Prepare data for Solver ---

        // 1. Calculate initial quotas (remains the same)
        val workShifts = shiftTypes.filter { it.shortCode != "OFF" }
        val (workQuotas, offQuota) = calculateAllQuotas(manpowerPlan, orderedUsers, workShifts, offShift, dates.size)
        // Correct initialization for initialQuotas
        val initialQuotas: Map<String, Map<String, Int>> = workQuotas.mapValues { it.value } +
                orderedUsers.associate { user ->
                    user.id to mapOf(offShift.id to (offQuota[user.id] ?: 0))
                }.mapValues { entry ->
                    (workQuotas[entry.key] ?: emptyMap()) + entry.value // Merge work and off quotas
                }

        // 2. Build initial schedule (including rotations, leaves, prefs)
        val initialSchedule = mutableMapOf<String, MutableMap<String, String>>()
        allUsers.forEach { initialSchedule[it.id] = mutableMapOf() } // Initialize user rows
        val initialViolations = mutableListOf<String>()

        // Use MutableMaps for strict application functions
        val mutableWorkQuotas = initialQuotas.mapValues { it.value.toMutableMap() }.toMutableMap()
        val mutableOffQuota = offQuota.toMutableMap()

        // 2a. Apply rotations
        applyPreScheduledRotationsStrict(preScheduledRotations, initialSchedule, mutableWorkQuotas, initialViolations)

        // 2b. Apply leaves
        applyApprovedLeavesStrict(requests, offShift, initialSchedule, mutableOffQuota, initialViolations)
        // Update mutableWorkQuotas with final off quotas
        mutableWorkQuotas.forEach { (userId, quotas) ->
            quotas[offShift.id] = mutableOffQuota[userId] ?: 0
        }

        // (Optional) 2c. Apply strict reservations if needed before solving
        // applyReservationsStrict(reservations, initialSchedule, shiftTypes, mutableWorkQuotas, mutableOffQuota, dbRules, initialViolations)

        // --- Create and run Solver ---
        val solver = BacktrackingSolver(
            users = orderedUsers,
            numDays = dates.size,
            dates = dates,
            initialSchedule = initialSchedule.mapValues { it.value.toMap() }, // Pass immutable map
            initialQuotas = mutableWorkQuotas.mapValues { it.value.toMap() }, // Pass immutable map reflecting applied leaves/rotations
            shiftTypes = shiftTypes,
            dbRules = dbRules, // Pass Data class rules to solver
            ruleEngine = ruleEngine
        )

        val solveSuccess = solver.solve()

        // --- Process Solver result ---
        if (solveSuccess) {
            val finalAssignmentsMap = solver.getSolution()
            if (finalAssignmentsMap != null) {
                return finalizeScheduleStrict(
                    orgId, groupId, month, finalAssignmentsMap, allUsers, shiftTypes, dbRules, initialViolations // Pass Data class rules
                )
            } else {
                return ScheduleGenerationResult( violations = initialViolations + "回溯求解器成功但未返回班表", schedule = Schedule(orgId = orgId, groupId = groupId, month = month, status = "error"), assignments = emptyList(), score = -9998, warnings = emptyList() )
            }
        } else {
            return ScheduleGenerationResult( violations = initialViolations + "回溯求解器未能找到滿足所有硬性條件的班表", schedule = Schedule(orgId = orgId, groupId = groupId, month = month, status = "error"), assignments = emptyList(), score = -9999, warnings = emptyList() )
        }
    }

    /**
     * 醫院排班策略 - 貪婪法
     * @param dbRules DB來的啟用規則 (data model)
     */
    private fun generateHospitalScheduleGreedy(
        orgId: String, groupId: String, month: String,
        allUsers: List<User>, passedShiftTypes: List<ShiftType>,
        requests: List<Request>, reservations: List<Reservation>,
        dbRules: List<SchedulingRuleData>, // Expect Data class
        manpowerPlan: ManpowerPlan,
        offShift: ShiftType, dates: List<String>,
        orderedUsers: List<User>, preScheduledRotations: Map<String, Map<String, String>>
    ): ScheduleGenerationResult {

        val violations = mutableListOf<String>()
        val userAssignments = mutableMapOf<String, MutableMap<String, String>>()
        allUsers.forEach { userAssignments[it.id] = mutableMapOf() }

        val nShift = passedShiftTypes.find { it.name == "值班(夜)" }
        val sShift = passedShiftTypes.find { it.name == "白班" }
        val dShift = passedShiftTypes.find { it.name == "值班(日)" }
        val workShifts = listOfNotNull(nShift, sShift, dShift) // Ensure only non-null shifts

        // --- Scheduling Flow (Greedy Steps) ---
        val (workQuotas, offQuota) = calculateAllQuotas(manpowerPlan, orderedUsers, workShifts, offShift, dates.size)
        // Correct initialization
        val remainingWorkQuotas = workQuotas.mapValues { it.value.toMutableMap() }.toMutableMap()
        val remainingOffQuota = offQuota.toMutableMap()

        // Apply pre-scheduled items strictly
        applyPreScheduledRotationsStrict(preScheduledRotations, userAssignments, remainingWorkQuotas, violations)
        applyApprovedLeavesStrict(requests, offShift, userAssignments, remainingOffQuota, violations)
        // Pass Data class dbRules
        applyReservationsStrict(reservations, userAssignments, passedShiftTypes, remainingWorkQuotas, remainingOffQuota, dbRules, violations)

        // Assign shifts strictly based on need, quota, and rules
        // Pass Data class dbRules
        assignShiftTypeStrict(dates, manpowerPlan, nShift, userAssignments, remainingWorkQuotas, orderedUsers, allUsers, passedShiftTypes, dbRules, violations, offShift, remainingOffQuota, sortByShiftCount = true)
        assignShiftTypeStrict(dates, manpowerPlan, dShift, userAssignments, remainingWorkQuotas, orderedUsers, allUsers, passedShiftTypes, dbRules, violations, offShift = null, remainingOffQuota = null, sortByShiftCount = true)
        assignShiftTypeStrict(dates, manpowerPlan, sShift, userAssignments, remainingWorkQuotas, orderedUsers, allUsers, passedShiftTypes, dbRules, violations, offShift = null, remainingOffQuota = null, sortByShiftCount = false)

        // Fill remaining spots with OFF, checking quotas and rules
        // Pass Data class dbRules
        fillRemainingWithOffStrict(userAssignments, allUsers, dates, offShift, passedShiftTypes, dbRules, remainingOffQuota, remainingWorkQuotas.toMap(), violations) // Pass immutable copy of work quotas

        // Finalize (validate and build result)
        // Pass Data class dbRules
        return finalizeScheduleStrict(orgId, groupId, month, userAssignments, allUsers, passedShiftTypes, dbRules, violations)
    }

    // --- calculateAllQuotas (remains the same) ---
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

        // Return immutable maps
        return workQuotas.mapValues { it.value.toMap() } to offQuota.toMap()
    }

    // --- applyPreScheduledRotationsStrict (remains the same) ---
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
                            userAssignments.getOrPut(userId) { mutableMapOf() }[day] = shiftId // Ensure user map exists
                            quotas[shiftId] = (quotas[shiftId] ?: 1) - 1
                        } else {
                            violations.add("【輪替配額衝突】使用者 $userId 在 $day 的輪替班 $shiftId 因配額不足而未排入。")
                        }
                    } else { // Shift not under quota control
                        userAssignments.getOrPut(userId) { mutableMapOf() }[day] = shiftId
                    }
                } else if (existingAssignment != shiftId) {
                    violations.add("【輪替衝突】使用者 $userId 在 $day 的輪替班 $shiftId 與先前排定的班別 ($existingAssignment) 衝突，未排入。")
                }
            }
        }
    }


    // --- applyApprovedLeavesStrict (remains the same) ---
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
                        userAssignments.getOrPut(request.userId) { mutableMapOf() }[day] = offShift.id // Ensure user map exists
                        remainingOffQuota[request.userId] = (remainingOffQuota[request.userId] ?: 1) - 1
                    } else {
                        violations.add("【休假配額衝突】${request.userName} 於 ${request.date} 的休假配額不足，未排入。")
                    }
                } else if (existingAssignment != offShift.id) {
                    violations.add("【休假衝突】${request.userName} 於 ${request.date} 的休假與輪替班($existingAssignment)衝突，未排入。")
                }
            }
    }

    /**
     * 嚴格套用預約偏好, 檢查配額和硬性規則
     * @param dbRules DB來的啟用規則 (data model)
     */
    private fun applyReservationsStrict(
        reservations: List<Reservation>,
        userAssignments: MutableMap<String, MutableMap<String, String>>,
        localShiftTypes: List<ShiftType>,
        remainingWorkQuotas: MutableMap<String, MutableMap<String, Int>>,
        remainingOffQuota: MutableMap<String, Int>,
        dbRules: List<SchedulingRuleData>, // Expect Data class
        violations: MutableList<String>
    ) {
        val offShiftId = localShiftTypes.find { it.shortCode == "OFF" }?.id ?: ""
        val shiftTypeMap = localShiftTypes.associateBy { it.id }

        reservations.forEach { reservation ->
            reservation.dailyShifts.entries.sortedBy { entry -> entry.key }.forEach { (day, preferences) ->
                // Check if the spot is already filled (by rotation or leave)
                if (userAssignments[reservation.userId]?.get(day) == null && preferences.isNotEmpty()) {
                    var preferenceAssigned = false
                    for (shiftId in preferences) {
                        val isOffShift = shiftId == offShiftId
                        val currentQuota = if (isOffShift) {
                            remainingOffQuota[reservation.userId] ?: 0
                        } else {
                            remainingWorkQuotas[reservation.userId]?.get(shiftId) ?: 0
                        }

                        // Check quota and hard rules
                        if (currentQuota > 0 && checkAllHardRulesRealtime(reservation.userId, day, shiftId, userAssignments, localShiftTypes, dbRules)) {
                            userAssignments.getOrPut(reservation.userId) { mutableMapOf() }[day] = shiftId // Assign
                            // Deduct quota
                            if (isOffShift) {
                                remainingOffQuota[reservation.userId] = currentQuota - 1
                            } else {
                                remainingWorkQuotas[reservation.userId]?.set(shiftId, currentQuota - 1)
                            }
                            preferenceAssigned = true
                            Log.d("ApplyReservations", "Assigned preference ${shiftTypeMap[shiftId]?.name} for ${reservation.userName} on day $day.")
                            break // Stop after assigning the highest valid preference
                        } else {
                            val reason = if (currentQuota <= 0) "quota limit" else "hard rule violation"
                            Log.d("ApplyReservations", "Skipped preference ${shiftTypeMap[shiftId]?.name} for ${reservation.userName} on day $day due to $reason.")
                        }
                    }
                    if (!preferenceAssigned) {
                        Log.w("ApplyReservations", "【Preference Conflict】No valid preference found for ${reservation.userName} on day $day.")
                        // Optionally add to violations list
                        // violations.add("【偏好衝突】使用者 ${reservation.userName} 在 $day 的所有偏好均無法滿足 (配額或規則限制)。")
                    }
                }
            }
        }
    }


    /**
     * 通用的嚴格班別指派函式
     * @param dbRules DB來的啟用規則 (data model)
     */
    private fun assignShiftTypeStrict(
        dates: List<String>, manpowerPlan: ManpowerPlan, shiftToAssign: ShiftType?,
        userAssignments: MutableMap<String, MutableMap<String, String>>,
        remainingWorkQuotas: MutableMap<String, MutableMap<String, Int>>,
        orderedUsers: List<User>,
        allUsers: List<User>,
        localShiftTypes: List<ShiftType>,
        dbRules: List<SchedulingRuleData>, // Expect Data class
        violations: MutableList<String>,
        offShift: ShiftType?,
        remainingOffQuota: MutableMap<String, Int>?,
        sortByShiftCount: Boolean = false
    ) {
        if (shiftToAssign == null) return

        dates.forEach { date ->
            val day = date.split("-").last()
            val requiredCount = manpowerPlan.dailyRequirements[day]?.requirements?.get(shiftToAssign.id) ?: 0
            val assignedCount = userAssignments.values.count { it[day] == shiftToAssign.id }
            var needed = requiredCount - assignedCount

            if (needed > 0) {
                // Find users not assigned today
                val availableUserIds = allUsers.map { it.id }
                    .filter { userId -> userAssignments[userId]?.get(day) == null }

                // Filter candidates by quota and hard rules
                val validCandidates = availableUserIds.filter { userId ->
                    val hasQuota = (remainingWorkQuotas[userId]?.get(shiftToAssign.id) ?: 0) > 0
                    val passesHardRules = checkAllHardRulesRealtime(userId, day, shiftToAssign.id, userAssignments, localShiftTypes, dbRules)
                    hasQuota && passesHardRules
                }

                // Sort candidates
                val sortedCandidates = if (sortByShiftCount) {
                    validCandidates.sortedBy { userId -> countAssignedShifts(userId, shiftToAssign.id, userAssignments) }
                } else {
                    validCandidates.sortedBy { userId -> orderedUsers.indexOfFirst { it.id == userId } }
                }

                // Assign users
                val usersToAssign = sortedCandidates.take(needed)
                usersToAssign.forEach { userId ->
                    userAssignments.getOrPut(userId) { mutableMapOf() }[day] = shiftToAssign.id
                    remainingWorkQuotas[userId]?.let { quotas ->
                        quotas[shiftToAssign.id] = (quotas[shiftToAssign.id] ?: 1) - 1
                    }
                    needed--

                    // Pre-fill OFF for N shift if applicable
                    if (offShift != null && remainingOffQuota != null && shiftToAssign.name == "值班(夜)") {
                        val nextDayInt = day.toIntOrNull()?.plus(1) ?: 0
                        if (nextDayInt > 0 && nextDayInt <= dates.size) {
                            val nextDayKey = String.format("%02d", nextDayInt)
                            // Check if next day is already assigned (could be rotation, leave, or another N)
                            val nextDayAssignment = userAssignments[userId]?.get(nextDayKey)
                            if (nextDayAssignment == null) { // Only prefill if empty
                                if ((remainingOffQuota[userId] ?: 0) > 0) {
                                    if (checkAllHardRulesRealtime(userId, nextDayKey, offShift.id, userAssignments, localShiftTypes, dbRules)) {
                                        userAssignments[userId]?.set(nextDayKey, offShift.id)
                                        remainingOffQuota[userId] = (remainingOffQuota[userId] ?: 1) - 1
                                    } else {
                                        violations.add("【規則衝突】無法為 ${allUsers.find { it.id == userId }?.name} 在 $nextDayKey 預填夜班後的 OFF (違反規則)。")
                                    }
                                } else {
                                    violations.add("【配額衝突】無法為 ${allUsers.find { it.id == userId }?.name} 在 $nextDayKey 預填夜班後的 OFF (配額不足)。")
                                }
                            }
                        }
                    }
                } // end forEach usersToAssign

                // Check if still needed
                if (needed > 0) {
                    violations.add("【人力/規則/配額衝突】日期 $date 的 ${shiftToAssign.name} 缺少 $needed 人力 (找不到符合所有強制條件的人員)。")
                }
            } // end if needed > 0
        } // end forEach date
    }


    /**
     * 嚴格填入剩餘 OFF, 檢查配額和硬性規則
     * @param dbRules DB來的啟用規則 (data model)
     */
    private fun fillRemainingWithOffStrict(
        userAssignments: MutableMap<String, MutableMap<String, String>>,
        allUsers: List<User>, dates: List<String>, offShift: ShiftType,
        localShiftTypes: List<ShiftType>,
        dbRules: List<SchedulingRuleData>, // Expect Data class
        remainingOffQuota: MutableMap<String, Int>,
        remainingWorkQuotasParam: Map<String, Map<String, Int>>, // Immutable map
        violations: MutableList<String>
    ) {
        allUsers.forEach { user ->
            dates.forEach { date ->
                val day = date.split("-").last()
                if (userAssignments[user.id]?.get(day) == null) { // If spot is empty
                    if ((remainingOffQuota[user.id] ?: 0) > 0) {
                        // Check hard rules before filling OFF
                        if (checkAllHardRulesRealtime(user.id, day, offShift.id, userAssignments, localShiftTypes, dbRules)) {
                            userAssignments.getOrPut(user.id) { mutableMapOf() }[day] = offShift.id
                            remainingOffQuota[user.id] = (remainingOffQuota[user.id] ?: 1) - 1
                        } else {
                            violations.add("【OFF 規則衝突】無法為 ${user.name} 在 $date 填入 OFF (違反硬性規則)。班表可能有空格。")
                            // Spot remains blank
                        }
                    } else {
                        violations.add("【OFF 配額嚴重衝突】${user.name} 在 $date 填入 OFF 時配額不足！班表可能有空格。")
                        // Spot remains blank
                    }
                }
            }
        }

        // Final quota checks (remain the same)
        remainingOffQuota.forEach { (userId, quota) ->
            if (quota > 0) {
                Log.w("ScheduleGenerator", "使用者 ${allUsers.find{it.id==userId}?.name} 的 OFF 配額剩餘 $quota")
            } else if (quota < 0) {
                violations.add("【OFF 配額計算錯誤】${allUsers.find{it.id==userId}?.name} 的 OFF 配額變為負數 $quota！")
            }
        }
        remainingWorkQuotasParam.forEach { (userId, quotas) ->
            quotas.forEach { (shiftId, quota) ->
                if (quota < 0) {
                    violations.add("【工作配額計算錯誤】${allUsers.find{it.id==userId}?.name} 的 ${localShiftTypes.find{it.id==shiftId}?.name} 配額變為負數 $quota！")
                }
            }
        }
    }


    /**
     * 即時檢查所有硬性規則
     * @param dbRules DB來的啟用規則 (data model)
     */
    private fun checkAllHardRulesRealtime(
        userId: String, day: String, shiftIdToAssign: String,
        currentAssignments: Map<String, Map<String, String>>, // Should be immutable Map
        localShiftTypes: List<ShiftType>,
        dbRules: List<SchedulingRuleData> // Expect Data class
    ): Boolean {
        // Create a temporary map reflecting the potential assignment
        val simulatedUserAssignments = currentAssignments[userId]?.plus(day to shiftIdToAssign) ?: mapOf(day to shiftIdToAssign)

        val user = User(id = userId) // Rule context only needs user ID usually
        val context = RuleContext(user, simulatedUserAssignments, localShiftTypes)

        // Iterate through enabled HARD rules (Data class)
        for (dbRuleData in dbRules.filter { it.ruleType == "hard" && it.isEnabled }) {
            // Find the corresponding implementation (Interface)
            val ruleImpl: SchedulingRuleInterface? = allAvailableRules.find { it.name == dbRuleData.ruleName }
            if (ruleImpl != null) {
                // Evaluate using the implementation
                val violation = ruleImpl.evaluate(context, dbRuleData.parameters)
                if (violation != null) {
                    // Log.d("checkHardRules", "Violation for $userId on $day with $shiftIdToAssign: ${violation.message}")
                    return false // Violation found
                }
            } else {
                Log.w("checkHardRules", "找不到規則 ${dbRuleData.ruleName} 的實作")
            }
        }
        return true // All hard rules passed
    }

    /**
     * 嚴格最終化，合併 Violations
     * @param dbRules DB來的啟用規則 (data model)
     */
    private fun finalizeScheduleStrict(
        orgId: String, groupId: String, month: String,
        finalUserAssignments: Map<String, Map<String, String>>, // Should be immutable
        users: List<User>,
        localShiftTypes: List<ShiftType>,
        dbRules: List<SchedulingRuleData>, // Expect Data class
        violationsFromProcess: List<String>
    ): ScheduleGenerationResult {
        // Post-validation and scoring (uses RuleEngine with Interface rules)
        val (validationViolations, finalScore) = validateAllUsers(finalUserAssignments, users, localShiftTypes, dbRules)
        // Combine violations from process and final validation
        val allViolationMessages = (violationsFromProcess + validationViolations.map { it.message }).distinct()

        // Build final result
        return buildResult(orgId, groupId, month, finalUserAssignments, users, finalScore, allViolationMessages, emptyList())
    }

    /**
     * Counts assigned shifts for a user (remains the same)
     */
    private fun countAssignedShifts(userId: String, shiftId: String, userAssignments: Map<String, Map<String, String>>): Int {
        return userAssignments[userId]?.values?.count { it == shiftId } ?: 0
    }

    /**
     * 通用排班策略實現 (舊版邏輯)
     * @param dbRules DB來的啟用規則 (data model)
     */
    private fun generateGeneralSchedule(
        orgId: String, groupId: String, month: String,
        users: List<User>,
        passedShiftTypes: List<ShiftType>,
        requests: List<Request>, reservations: List<Reservation>,
        dbRules: List<SchedulingRuleData>, // Expect Data class
        manpowerPlan: ManpowerPlan, offShift: ShiftType, dates: List<String>
    ): ScheduleGenerationResult {
        val nShift = passedShiftTypes.find { it.name == "值班(夜)" }
        val dShift = passedShiftTypes.find { it.name == "值班(日)" }
        val sShift = passedShiftTypes.find { it.name == "白班" }

        if (nShift == null || dShift == null || sShift == null) {
            return ScheduleGenerationResult( schedule = Schedule(orgId = orgId, groupId = groupId, month = month, status = "error"), assignments = emptyList(), score = -9999, violations = listOf("錯誤：找不到關鍵班別(N,D,S)。"), warnings = emptyList() )
        }

        val userAssignments = mutableMapOf<String, MutableMap<String, String>>()
        users.forEach { userAssignments[it.id] = mutableMapOf() }

        // 1. Apply leaves (old version)
        applyApprovedLeaves(requests, offShift, userAssignments)

        // 2. Apply reservations (old version)
        applyReservations(reservations, userAssignments) // Needs update for List<String>

        // 3. Assign N shifts (simple logic)
        dates.forEach { date ->
            val day = date.split("-").last()
            val requiredCount = manpowerPlan.dailyRequirements[day]?.requirements?.get(nShift.id) ?: 0
            if (requiredCount > 0) {
                val availableUsers = users.filter { userAssignments[it.id]?.get(day) == null }.shuffled()
                val usersToAssign = availableUsers.take(requiredCount)
                usersToAssign.forEach { user -> userAssignments.getOrPut(user.id) { mutableMapOf() }[day] = nShift.id }
            }
        }

        // 4 & 5. Assign D and S shifts (simple logic, checking yesterday N)
        listOf(dShift, sShift).forEach { shift ->
            dates.forEach { date ->
                val day = date.split("-").last()
                val requiredCount = manpowerPlan.dailyRequirements[day]?.requirements?.get(shift.id) ?: 0
                val alreadyAssigned = userAssignments.values.count { it[day] == shift.id }
                var needed = requiredCount - alreadyAssigned

                if (needed > 0) {
                    val availableUsers = users.filter { user ->
                        val isAvailableToday = userAssignments[user.id]?.get(day) == null
                        if (!isAvailableToday) return@filter false
                        val yesterdayInt = day.toIntOrNull()?.minus(1) ?: 0
                        if (yesterdayInt <= 0) return@filter true
                        val yesterdayKey = String.format("%02d", yesterdayInt)
                        userAssignments[user.id]?.get(yesterdayKey) != nShift.id
                    }.shuffled()

                    val usersToAssign = availableUsers.take(needed)
                    usersToAssign.forEach { user -> userAssignments.getOrPut(user.id) { mutableMapOf() }[day] = shift.id }
                }
            }
        }

        // 6. Fill remaining with OFF (old version)
        fillRemainingWithOff(userAssignments, users, dates, offShift)

        // 7 & 8. Finalize (using strict version now)
        return finalizeScheduleStrict(orgId, groupId, month, userAssignments.mapValues { it.value.toMap() }, users, passedShiftTypes, dbRules, emptyList()) // Pass Data class rules
    }


    /**
     * 驗證所有使用者班表並計算分數
     * @param enabledDbRules DB來的啟用規則 (data model)
     */
    private fun validateAllUsers(
        assignments: Map<String, Map<String, String>>, // Should be immutable
        users: List<User>,
        localShiftTypes: List<ShiftType>,
        enabledDbRules: List<SchedulingRuleData> // Expect Data class
    ): Pair<List<RuleViolation>, Int> {
        val allViolations = mutableListOf<RuleViolation>()
        var totalScore = 0

        users.forEach { user ->
            val userAssignmentMap = assignments[user.id] ?: emptyMap()
            if (userAssignmentMap.isNotEmpty()) {
                // Create Assignment object for RuleEngine
                val assignmentObj = Assignment(userId = user.id, dailyShifts = userAssignmentMap)
                // RuleEngine expects Data class rules list
                val violations = ruleEngine.validate(user, assignmentObj, localShiftTypes, enabledDbRules)
                allViolations.addAll(violations)
                // Calculate score based on soft rule violations from Data class rules
                violations.forEach { violation ->
                    val ruleData = enabledDbRules.find { it.ruleName == violation.ruleName }
                    if (ruleData?.ruleType == "soft") {
                        totalScore += ruleData.penaltyScore
                    }
                }
            }
        }
        return Pair(allViolations, totalScore)
    }

    /**
     * 建立最終 ScheduleGenerationResult (remains the same)
     */
    private fun buildResult(
        orgId: String, groupId: String, month: String,
        assignments: Map<String, Map<String, String>>, // Should be immutable
        users: List<User>,
        finalScore: Int, violationMessages: List<String>, warnings: List<String>
    ): ScheduleGenerationResult {
        val scheduleId = UUID.randomUUID().toString()
        val finalAssignmentObjects = users.mapNotNull { user ->
            assignments[user.id]?.let { dailyShifts ->
                Assignment(
                    id = UUID.randomUUID().toString(), // Generate new assignment ID
                    scheduleId = scheduleId,
                    userId = user.id,
                    userName = user.name,
                    dailyShifts = dailyShifts
                )
            }
        }
        val finalSchedule = Schedule(
            id = scheduleId,
            orgId = orgId, groupId = groupId, month = month, status = "draft",
            generatedAt = Date(), totalScore = finalScore, violatedRules = violationMessages,
            generationMethod = "smart" // Or determine based on strategy
        )
        return ScheduleGenerationResult(finalSchedule, finalAssignmentObjects, finalScore, violationMessages, warnings)
    }

    // --- Old Helper Functions (used by General Strategy) ---
    private fun fillRemainingWithOff(
        userAssignments: MutableMap<String, MutableMap<String, String>>,
        allUsers: List<User>, dates: List<String>, offShift: ShiftType
    ) {
        allUsers.forEach { user ->
            dates.forEach { date ->
                val day = date.split("-").last()
                if (userAssignments[user.id]?.get(day) == null) {
                    userAssignments.getOrPut(user.id) { mutableMapOf() }[day] = offShift.id
                }
            }
        }
    }

    private fun applyReservations( // Needs update for List<String> if used
        reservations: List<Reservation>,
        userAssignments: MutableMap<String, MutableMap<String, String>>
    ) {
        reservations.forEach { reservation ->
            reservation.dailyShifts.forEach { (day, preferences) ->
                val firstPreference = preferences.firstOrNull()
                if (firstPreference != null && userAssignments[reservation.userId]?.get(day) == null) {
                    userAssignments.getOrPut(reservation.userId) { mutableMapOf() }[day] = firstPreference
                }
            }
        }
    }

    private fun applyApprovedLeaves(
        requests: List<Request>,
        offShift: ShiftType,
        userAssignments: MutableMap<String, MutableMap<String, String>>
    ) {
        requests.filter { it.status == "approved" && it.type == "leave" }
            .forEach { request ->
                val day = request.date.split("-").last()
                userAssignments.getOrPut(request.userId) { mutableMapOf() }[day] = offShift.id // Direct assignment/overwrite
            }
    }
}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲

