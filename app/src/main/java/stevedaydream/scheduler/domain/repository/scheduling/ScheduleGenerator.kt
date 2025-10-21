// ▼▼▼▼▼▼▼▼▼▼▼▼ 完整程式碼 ▼▼▼▼▼▼▼▼▼▼▼▼
package stevedaydream.scheduler.domain.scheduling

import android.util.Log // 引入 Log
import stevedaydream.scheduler.data.model.*
import stevedaydream.scheduler.domain.repository.scheduling.rules.MinRestBetweenShiftsRule
// ✅ 修正 import，避免與 data class 衝突
// import stevedaydream.scheduler.domain.repository.scheduling.rules.SchedulingRule as RuleInterface
import stevedaydream.scheduler.domain.scheduling.rules.MaxConsecutiveWorkDaysRule

import stevedaydream.scheduler.domain.scheduling.rules.NightShiftFollowupRule
import stevedaydream.scheduler.domain.scheduling.rules.RuleContext // 引入 RuleContext
import stevedaydream.scheduler.domain.scheduling.rules.RuleViolation
// ✅ 正確 import SchedulingRule interface
import stevedaydream.scheduler.domain.scheduling.rules.SchedulingRule as SchedulingRuleInterface
import stevedaydream.scheduler.util.DateUtils
import java.util.*
import kotlin.math.floor

/**
 * 排班生成器，支援不同的排班策略
 */
class ScheduleGenerator {

    // --- Common Data Structures ---
    data class ScheduleGenerationResult(
        val schedule: Schedule,
        val assignments: List<Assignment>,
        val score: Int, // 最終基於 RuleEngine 的評分
        val violations: List<String>, // 強制條件衝突 + RuleEngine 驗證出的違規
        val warnings: List<String> // 其他非致命性問題 (目前較少使用)
    )

    // ✅ 使用 SchedulingRuleInterface
    private val allAvailableRules: List<SchedulingRuleInterface> = listOf(
        MaxConsecutiveWorkDaysRule(),
        MinRestBetweenShiftsRule(),
        NightShiftFollowupRule()
        // 可以根據需要加入更多規則
    )
    // 建立規則引擎實例 (用於最終驗證)
    private val ruleEngine = RuleEngine(allAvailableRules)

    // --- Entry Point ---
    /**
     * 生成排班表的主函數
     * @param strategy 排班策略 ("general", "hospital")
     * @param orderedUsers 醫院策略需要的排序後 User 列表
     * @param preScheduledRotations 醫院策略需要的輪替預排班 Map<UserId, Map<Day, ShiftId>>
     */
    fun generateSchedule(
        orgId: String,
        groupId: String,
        month: String,
        users: List<User>, // 所有參與排班的原始 User 列表
        shiftTypes: List<ShiftType>, // ✅ 在此處定義的 shiftTypes
        requests: List<Request>,
        reservations: List<Reservation>, // dailyShifts 為 Map<String, List<String>>
        rules: List<SchedulingRule>, // DB來的啟用規則 (data model)
        manpowerPlan: ManpowerPlan?,
        strategy: String = "general", // 新增：排班策略
        orderedUsers: List<User>? = null, // 新增：醫院策略參數
        preScheduledRotations: Map<String, Map<String, String>>? = null // 新增：醫院策略參數
    ): ScheduleGenerationResult {
        val dates = DateUtils.getDatesInMonth(month)
        val offShift = shiftTypes.find { it.shortCode == "OFF" }

        // 基本檢查
        if (offShift == null || manpowerPlan == null || manpowerPlan.dailyRequirements.isEmpty()) {
            return ScheduleGenerationResult(
                schedule = Schedule(orgId = orgId, groupId = groupId, month = month, status = "error"),
                assignments = emptyList(), score = -9999,
                violations = listOf("錯誤：找不到 OFF 班別或未設定人力規劃。"), warnings = emptyList()
            )
        }
        if (users.isEmpty()) {
            return ScheduleGenerationResult(
                schedule = Schedule(orgId = orgId, groupId = groupId, month = month, status = "error"),
                assignments = emptyList(), score = -9999,
                violations = listOf("錯誤：沒有參與排班的使用者。"), warnings = emptyList()
            )
        }


        // 根據策略選擇執行流程
        return when (strategy) {
            "hospital" -> {
                // 醫院策略需要額外參數
                if (orderedUsers == null || preScheduledRotations == null) {
                    ScheduleGenerationResult(
                        schedule = Schedule(orgId = orgId, groupId = groupId, month = month, status = "error"),
                        assignments = emptyList(), score = -9999,
                        violations = listOf("錯誤：醫院排班策略缺少必要參數 (orderedUsers 或 preScheduledRotations)。"), warnings = emptyList()
                    )
                } else {
                    generateHospitalScheduleRefined( // ✅ 呼叫新的 Refined 函數
                        orgId, groupId, month, users, shiftTypes, requests, reservations, rules,
                        manpowerPlan, offShift, dates, orderedUsers, preScheduledRotations
                    )
                }
            }
            else -> { // "general" or any other unknown strategy
                generateGeneralSchedule( // 通用策略保持不變
                    orgId, groupId, month, users, shiftTypes, requests, reservations, rules,
                    manpowerPlan, offShift, dates
                )
            }
        }
    }

    // --- Hospital Scheduling Strategy Implementation (Refined) ---
    /**
     * ✅ 新的醫院排班策略：逐步填充 + 衝突記錄 (無回溯)
     */
    private fun generateHospitalScheduleRefined(
        orgId: String, groupId: String, month: String,
        allUsers: List<User>, // 原始 User 列表
        passedShiftTypes: List<ShiftType>, // ✅ 重新命名傳入的 shiftTypes 避免衝突
        requests: List<Request>, reservations: List<Reservation>, // dailyShifts 為 Map<String, List<String>>
        dbRules: List<SchedulingRule>, // DB來的啟用規則 (data model)
        manpowerPlan: ManpowerPlan, offShift: ShiftType, dates: List<String>,
        orderedUsers: List<User>, // 已排序的 User 列表
        preScheduledRotations: Map<String, Map<String, String>> // Map<UserId, Map<Day, ShiftId>>
    ): ScheduleGenerationResult {

        val violations = mutableListOf<String>() // 儲存強制條件衝突訊息
        // 初始化 userAssignments Map: UserId -> Day -> ShiftId
        val userAssignments = mutableMapOf<String, MutableMap<String, String>>()
        allUsers.forEach { userAssignments[it.id] = mutableMapOf() }

        // 使用傳入的 passedShiftTypes
        val nShift = passedShiftTypes.find { it.name == "值班(夜)" }
        val sShift = passedShiftTypes.find { it.name == "白班" }
        val dShift = passedShiftTypes.find { it.name == "值班(日)" }
        val workShifts = listOfNotNull(nShift, sShift, dShift) // 過濾掉 null

        // --- 排班流程 ---

        // 【步驟 1：計算配額】
        val (workQuotas, offQuota) = calculateAllQuotas(manpowerPlan, orderedUsers, workShifts, offShift, dates.size)
        // ✅ Make remainingWorkQuotas accessible in later steps if needed outside this function scope
        // For now, it's used within this function and passed down.
        val remainingWorkQuotas = workQuotas.mapValues { (_, quotas) -> quotas.toMutableMap() }.toMutableMap()
        val remainingOffQuota = offQuota.toMutableMap()

        // 【步驟 2：填入輪替班】
        applyPreScheduledRotationsStrict(preScheduledRotations, userAssignments, remainingWorkQuotas, violations)

        // 【步驟 3：填入已核准休假】
        applyApprovedLeavesStrict(requests, offShift, userAssignments, remainingOffQuota, violations)

        // 【步驟 4：填入偏好 (預約)】 - 選項 B: 檢查配額
        // ✅ 傳遞正確的 shiftTypes 變數
        applyReservationsStrict(reservations, userAssignments, passedShiftTypes, remainingWorkQuotas, remainingOffQuota, dbRules, violations)

        // 【步驟 5：填入夜班 (N)】
        // ✅ 傳遞正確的 shiftTypes 變數
        assignShiftTypeStrict(
            dates, manpowerPlan, nShift, userAssignments, remainingWorkQuotas,
            orderedUsers, // 雖然 sortByShiftCount 為 true，但此參數仍需傳遞
            allUsers, passedShiftTypes, dbRules, violations, offShift, remainingOffQuota,
            sortByShiftCount = true // <--- 將 N班排序方式改為按班數
        )
        // 【步驟 6：填入值班 (D)】
        assignShiftTypeStrict(dates, manpowerPlan, dShift, userAssignments, remainingWorkQuotas, orderedUsers, allUsers, passedShiftTypes, dbRules, violations, offShift = null, remainingOffQuota = null, sortByShiftCount = true) // D班按次數排序

        // 【步驟 7：填入白班 (S)】
        assignShiftTypeStrict(dates, manpowerPlan, sShift, userAssignments, remainingWorkQuotas, orderedUsers, allUsers, passedShiftTypes, dbRules, violations, offShift = null, remainingOffQuota = null) // S班按 orderedUsers 排序

        // 【步驟 8：填入剩餘 OFF】
        // ✅ 傳遞正確的 shiftTypes 變數 and remainingWorkQuotas
        fillRemainingWithOffStrict(userAssignments, allUsers, dates, offShift, passedShiftTypes, dbRules, remainingOffQuota, remainingWorkQuotas, violations) // Pass shiftTypes, dbRules, and remainingWorkQuotas

        // 【步驟 9 & 10：驗證與產出】
        // ✅ 傳遞正確的 shiftTypes 變數
        return finalizeScheduleStrict(orgId, groupId, month, userAssignments, allUsers, passedShiftTypes, dbRules, violations)
    }

    // --- Refined Helper Functions ---

    /**
     * ✅ 計算所有工作班別和 OFF 班的配額
     * @return Pair<工作班別配額 Map<UserId, Map<ShiftId, Quota>>, OFF班配額 Map<UserId, Quota>>
     */
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

        // 計算工作班別配額
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

        // 計算 OFF 班配額
        orderedUsers.forEach { user ->
            val totalWorkQuota = workQuotas[user.id]?.values?.sum() ?: 0
            offQuota[user.id] = (daysInMonth - totalWorkQuota).coerceAtLeast(0) // 確保不為負
        }

        return workQuotas to offQuota
    }

    // 修改開始
    /**
     * ✅ 嚴格套用輪替班，衝突時記錄 Violation (修改：配額不足時不排入)
     */
    private fun applyPreScheduledRotationsStrict(
        preScheduledRotations: Map<String, Map<String, String>>,
        userAssignments: MutableMap<String, MutableMap<String, String>>,
        remainingWorkQuotas: MutableMap<String, MutableMap<String, Int>>,
        violations: MutableList<String>
    ) {
        preScheduledRotations.forEach { (userId, dailyShifts) ->
            dailyShifts.forEach { (day, shiftId) ->
                // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
                // 檢查當天是否已被排班 (例如：被休假或預約佔用)
                val existingAssignment = userAssignments[userId]?.get(day)
                if (existingAssignment == null) {
                    // 檢查配額是否足夠
                    val quotas = remainingWorkQuotas[userId]
                    if (quotas != null && quotas.containsKey(shiftId)) {
                        if ((quotas[shiftId] ?: 0) > 0) {
                            // 配額充足，排入班別並扣減配額
                            userAssignments[userId]?.set(day, shiftId)
                            quotas[shiftId] = (quotas[shiftId] ?: 1) - 1 // 扣減配額
                        } else {
                            // 配額不足，不排入班別，僅記錄衝突
                            violations.add("【輪替配額衝突】使用者 $userId 在 $day 的輪替班 $shiftId 因配額不足而未排入。")
                            // 注意：這裡不再強制 userAssignments[userId]?.set(day, shiftId)
                        }
                    } else {
                        // 非配額控管的輪替班別 (理論上較少見) 或找不到使用者配額記錄，直接排入
                        userAssignments[userId]?.set(day, shiftId)
                        // 可考慮在此處加入警告 Log
                        // Log.w("ScheduleGenerator", "使用者 $userId 的輪替班 $shiftId 非配額班別或找不到配額記錄，已直接排入。")
                    }
                } else if (existingAssignment != shiftId) { // 如果已存在且不是同一個班別
                    // 當天已被其他更高優先級 (如休假) 的班別佔用
                    violations.add("【輪替衝突】使用者 $userId 在 $day 的輪替班 $shiftId 與先前排定的班別 ($existingAssignment) 衝突，未排入。")
                }
                // 如果已存在且是同一個班別，則無需處理
                // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲
            }
        }
    }
// 修改結束

    /**
     * ✅ 嚴格套用休假，衝突時記錄 Violation
     */
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
                    // 檢查休假配額
                    if ((remainingOffQuota[request.userId] ?: 0) > 0) {
                        userAssignments[request.userId]?.set(day, offShift.id)
                        remainingOffQuota[request.userId] = (remainingOffQuota[request.userId] ?: 1) - 1 // 扣減配額
                    } else {
                        violations.add("【休假配額衝突】${request.userName} 於 ${request.date} 的休假配額不足，未排入。")
                    }
                } else if (existingAssignment != offShift.id) {
                    violations.add("【休假衝突】${request.userName} 於 ${request.date} 的休假與輪替班衝突，未排入。")
                }
                // 如果已存在的是 OFF，無需處理
            }
    }

    /**
     * ✅ 修改：嚴格套用預約偏好 (選項 B: 檢查配額)，處理 List<String>
     */
    private fun applyReservationsStrict(
        reservations: List<Reservation>, // dailyShifts is Map<String, List<String>>
        userAssignments: MutableMap<String, MutableMap<String, String>>,
        localShiftTypes: List<ShiftType>, // 使用局部變數名
        remainingWorkQuotas: MutableMap<String, MutableMap<String, Int>>,
        remainingOffQuota: MutableMap<String, Int>,
        dbRules: List<SchedulingRule>, // data model SchedulingRule
        violations: MutableList<String>
    ) {
        val offShiftId = localShiftTypes.find { it.shortCode == "OFF" }?.id ?: ""
        val shiftTypeMap = localShiftTypes.associateBy { it.id }

        reservations.forEach { reservation ->
            reservation.dailyShifts.entries.sortedBy { entry -> entry.key }.forEach { (day, preferences) ->
                if (userAssignments[reservation.userId]?.get(day) == null && preferences.isNotEmpty()) {
                    var preferenceAssigned = false
                    for (shiftId in preferences) {
                        val isOffShift = shiftId == offShiftId
                        // ✅ 修正 Line 313: 直接在 if/else 內部計算 currentQuota
                        val currentQuota = if (isOffShift) {
                            remainingOffQuota[reservation.userId] ?: 0
                        } else {
                            val workQuotaMap = remainingWorkQuotas[reservation.userId]
                            workQuotaMap?.get(shiftId) ?: 0
                        }

                        // 檢查硬性規則 和 配額
                        if (currentQuota > 0 && checkAllHardRulesRealtime(reservation.userId, day, shiftId, userAssignments, localShiftTypes, dbRules)) {
                            userAssignments[reservation.userId]?.set(day, shiftId)
                            // 扣減配額
                            if (isOffShift) {
                                remainingOffQuota[reservation.userId] = currentQuota - 1
                            } else {
                                remainingWorkQuotas[reservation.userId]?.let { quotas ->
                                    if (quotas is MutableMap) { quotas[shiftId] = currentQuota - 1 }
                                }
                            }
                            preferenceAssigned = true
                            Log.d("ApplyReservations", "Assigned preference ${shiftTypeMap[shiftId]?.name} for ${reservation.userName} on day $day.")
                            break // 找到符合的偏好就停止
                        } else {
                            val reason = if (currentQuota <= 0) "quota limit" else "hard rule violation"
                            Log.d("ApplyReservations", "Skipped preference ${shiftTypeMap[shiftId]?.name} for ${reservation.userName} on day $day due to $reason.")
                        }
                    } // End preference loop for the day
                    if (!preferenceAssigned) {
                        Log.w("ApplyReservations", "【Preference Conflict】No valid preference found for ${reservation.userName} on day $day.")
                    }
                } // End if spot is empty
            } // End loop through days
        } // End loop through reservations
    }


    /**
     * ✅ 通用的嚴格班別指派函式 (取代 assignRemainingShiftsHospital)
     * @param sortByShiftCount 是否按該班別已排次數排序 (用於 D 班)
     */
    private fun assignShiftTypeStrict(
        dates: List<String>, manpowerPlan: ManpowerPlan, shiftToAssign: ShiftType?,
        userAssignments: MutableMap<String, MutableMap<String, String>>,
        remainingWorkQuotas: MutableMap<String, MutableMap<String, Int>>,
        orderedUsers: List<User>, // 僅用於 S 班排序
        allUsers: List<User>,
        localShiftTypes: List<ShiftType>,
        dbRules: List<SchedulingRule>,
        violations: MutableList<String>,
        offShift: ShiftType?, // 用於預填 OFF
        remainingOffQuota: MutableMap<String, Int>?, // 用於預填 OFF
        // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改點 1: 更改預設值 (雖然呼叫時會明確指定) ▼▼▼▼▼▼▼▼▼▼▼▼
        sortByShiftCount: Boolean = false // D班 和 N班 設為 true, S班 設為 false
        // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲
    ) {
        if (shiftToAssign == null) return // 如果班別不存在 (例如 N,S,D 找不到)

        dates.forEach { date ->
            val day = date.split("-").last()
            val requiredCount = manpowerPlan.dailyRequirements[day]?.requirements?.get(shiftToAssign.id) ?: 0
            val assignedCount = userAssignments.values.count { it[day] == shiftToAssign.id }
            var needed = requiredCount - assignedCount

            if (needed > 0) {
                // 1. 找出當天未排班的人
                val availableUserIds = allUsers.map { it.id }
                    .filter { userId -> userAssignments[userId]?.get(day) == null }

                // 2. 篩選同時符合【硬性規則】和【配額】的候選人
                val validCandidates = availableUserIds.filter { userId ->
                    val hasQuota = (remainingWorkQuotas[userId]?.get(shiftToAssign.id) ?: 0) > 0
                    val passesHardRules = checkAllHardRulesRealtime(userId, day, shiftToAssign.id, userAssignments, localShiftTypes, dbRules)
                    hasQuota && passesHardRules
                }

                // ▼▼▼▼▼▼▼▼▼▼▼▼ 修改點 2: 核心排序邏輯 ▼▼▼▼▼▼▼▼▼▼▼▼
                // 3. 排序候選人
                val sortedCandidates = if (sortByShiftCount) {
                    // 按該班別已排次數升冪排序 (用於 N班 和 D班，以求平均)
                    validCandidates.sortedBy { userId -> countAssignedShifts(userId, shiftToAssign.id, userAssignments) }
                } else {
                    // 按管理員設定的順序排序 (用於 S班 或其他非輪替班)
                    validCandidates.sortedBy { userId -> orderedUsers.indexOfFirst { it.id == userId } }
                }
                // ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲

                // 4. 指派人選
                val usersToAssign = sortedCandidates.take(needed)
                usersToAssign.forEach { userId ->
                    userAssignments[userId]?.set(day, shiftToAssign.id)
                    remainingWorkQuotas[userId]?.let { quotas ->
                        if (quotas is MutableMap) { quotas[shiftToAssign.id] = (quotas[shiftToAssign.id] ?: 1) - 1 } // 扣減配額
                    }
                    needed--

                    // 預填 OFF (僅 N 班需要)
                    if (offShift != null && remainingOffQuota != null && shiftToAssign.name == "值班(夜)") {
                        // ... (預填 OFF 邏輯保持不變) ...
                        val nightFollowupRule = dbRules.find { it.ruleName == "夜班後續班別限制" }
                        val nextDayInt = day.toIntOrNull()?.plus(1) ?: 0
                        if (nextDayInt > 0 && nextDayInt <= dates.size) { // Ensure next day is within the month
                            val nextDayKey = String.format("%02d", nextDayInt)
                            val nextDayAssignment = userAssignments[userId]?.get(nextDayKey)
                            val nextShiftIsAlsoNOrAssigned = nextDayAssignment != null

                            if (!nextShiftIsAlsoNOrAssigned && (remainingOffQuota[userId] ?: 0) > 0) {
                                if (checkAllHardRulesRealtime(userId, nextDayKey, offShift.id, userAssignments, localShiftTypes, dbRules)) {
                                    userAssignments[userId]?.set(nextDayKey, offShift.id)
                                    remainingOffQuota[userId] = (remainingOffQuota[userId] ?: 1) - 1
                                } else {
                                    violations.add("【規則衝突】無法為 ${allUsers.find{it.id==userId}?.name} 在 $nextDayKey 預填夜班後的 OFF (違反規則)。")
                                }
                            } else if (!nextShiftIsAlsoNOrAssigned) {
                                violations.add("【配額衝突】無法為 ${allUsers.find{it.id==userId}?.name} 在 $nextDayKey 預填夜班後的 OFF (配額不足)。")
                            }
                        }
                    }
                } // end forEach usersToAssign

                // 5. 如果人數仍不足
                if (needed > 0) {
                    violations.add("【人力/規則/配額衝突】日期 $date 的 ${shiftToAssign.name} 缺少 $needed 人力 (找不到符合所有強制條件的人員)。")
                }
            } // end if needed > 0
        } // end forEach date
    }


    /**
     * ✅ 修改：嚴格填入剩餘 OFF，修正 remainingWorkQuotas 訪問
     */
    private fun fillRemainingWithOffStrict(
        userAssignments: MutableMap<String, MutableMap<String, String>>,
        allUsers: List<User>, dates: List<String>, offShift: ShiftType,
        localShiftTypes: List<ShiftType>, // ✅ Add shiftTypes parameter
        dbRules: List<SchedulingRule>,      // ✅ Add dbRules parameter
        remainingOffQuota: MutableMap<String, Int>,
        // ✅ Add remainingWorkQuotas as parameter
        remainingWorkQuotasParam: Map<String, Map<String, Int>>, // Use a different name to avoid shadowing if necessary
        violations: MutableList<String>
    ) {
        allUsers.forEach { user ->
            dates.forEach { date ->
                val day = date.split("-").last()
                if (userAssignments[user.id]?.get(day) == null) {
                    if ((remainingOffQuota[user.id] ?: 0) > 0) {
                        // 檢查填入 OFF 是否違反硬性規則 (例如連續休假過長)
                        // ✅ Pass shiftTypes and dbRules to checkAllHardRulesRealtime
                        if (checkAllHardRulesRealtime(user.id, day, offShift.id, userAssignments, localShiftTypes, dbRules)) {
                            userAssignments[user.id]?.set(day, offShift.id)
                            remainingOffQuota[user.id] = (remainingOffQuota[user.id] ?: 1) - 1
                        } else {
                            violations.add("【OFF 規則衝突】無法為 ${user.name} 在 $date 填入 OFF (違反硬性規則)。")
                            // Decide: Leave blank or force OFF despite rule? Current logic leaves blank.
                        }
                    } else {
                        // OFF 配額不足，這通常表示配額計算或之前的扣減有問題
                        violations.add("【OFF 配額嚴重衝突】${user.name} 在 $date 填入 OFF 時配額不足！班表可能有空格。")
                        // Leave the spot blank as quota is a hard constraint
                    }
                }
            }
        }
        // 最後檢查是否有人的 OFF 配額沒用完 (表示工作量可能超出預期)
        remainingOffQuota.forEach { (userId, quota) ->
            if (quota > 0) {
                Log.w("ScheduleGenerator", "使用者 ${allUsers.find{it.id==userId}?.name} 的 OFF 配額剩餘 $quota")
                // 可以考慮加入 Warning
            } else if (quota < 0) {
                violations.add("【OFF 配額計算錯誤】${allUsers.find{it.id==userId}?.name} 的 OFF 配額變為負數 $quota！")
            }
        }
        // 檢查工作配額是否有負數
        // ✅ 修正 Line 404: Use the passed parameter remainingWorkQuotasParam
        // ✅ 修正 Line 404 & 406: Explicitly define lambda parameters
        remainingWorkQuotasParam.forEach { (userId, quotas) ->
            quotas.forEach { (shiftId, quota) ->
                if (quota < 0) {
                    // ✅ 修正 Line 406: Use localShiftTypes
                    violations.add("【工作配額計算錯誤】${allUsers.find{it.id==userId}?.name} 的 ${localShiftTypes.find{it.id==shiftId}?.name} 配額變為負數 $quota！")
                }
            }
        }
    }


    /**
     * ✅【實現基礎版本】即時檢查所有硬性規則
     * 這個函式模擬將 shiftIdToAssign 分配給 userId 在 day 這天後，
     * 是否會違反任何已啟用的硬性規則。
     */
    private fun checkAllHardRulesRealtime(
        userId: String, day: String, shiftIdToAssign: String,
        currentAssignments: Map<String, Map<String, String>>,
        localShiftTypes: List<ShiftType>, // ✅ 使用局部變數名
        dbRules: List<SchedulingRule> // data model SchedulingRule
    ): Boolean {
        // 模擬加入新班別後的班表
        val simulatedAssignments = currentAssignments[userId]?.toMutableMap() ?: mutableMapOf()
        simulatedAssignments[day] = shiftIdToAssign

        // 建立 RuleContext 需要的 User 物件 (只需 ID)
        val user = User(id = userId)
        // ✅ 傳遞正確的 shiftTypes
        val context = RuleContext(user, simulatedAssignments, localShiftTypes)

        // 遍歷所有啟用的【硬性】規則
        for (dbRule in dbRules.filter { it.ruleType == "hard" && it.isEnabled }) {
            // 找到對應的規則實作
            // ✅ 使用 SchedulingRuleInterface
            val ruleImpl: SchedulingRuleInterface? = allAvailableRules.find { it.name == dbRule.ruleName }
            if (ruleImpl != null) {
                // 執行評估
                val violation = ruleImpl.evaluate(context, dbRule.parameters)
                if (violation != null) {
                    // Log.d("checkHardRules", "Violation for $userId on $day with $shiftIdToAssign: ${violation.message}")
                    return false // 只要有一個硬性規則違反，就返回 false
                }
            } else {
                Log.w("checkHardRules", "找不到規則 ${dbRule.ruleName} 的實作")
            }
        }

        // 如果所有硬性規則檢查都通過
        return true
    }


    /**
     * ✅ 嚴格最終化，合併 Violations
     */
    private fun finalizeScheduleStrict(
        orgId: String, groupId: String, month: String,
        finalUserAssignments: Map<String, Map<String, String>>,
        users: List<User>,
        localShiftTypes: List<ShiftType>, // ✅ 使用局部變數名
        dbRules: List<SchedulingRule>, // data model SchedulingRule
        violationsFromProcess: List<String> // 接收填充過程中的衝突
    ): ScheduleGenerationResult {
        // --- 事後評分與驗證 ---
        // ✅ 傳遞正確的 shiftTypes
        val (validationViolations, finalScore) = validateAllUsers(finalUserAssignments, users, localShiftTypes, dbRules)
        // 合併處理過程中的衝突和最終驗證的違規 (去重)
        val allViolationMessages = (violationsFromProcess + validationViolations.map { it.message }).distinct()

        // --- 建立最終結果 ---
        // warnings 列表可以保留用於記錄非強制性的問題，如果有的話
        // ✅ 傳遞正確的 shiftTypes (雖然 buildResult 內部目前沒用到)
        return buildResult(orgId, groupId, month, finalUserAssignments, users, finalScore, allViolationMessages, emptyList())
    }


    // --- Helper for Hospital & General: Count assigned shifts ---
    private fun countAssignedShifts(userId: String, shiftId: String, userAssignments: Map<String, Map<String, String>>): Int {
        return userAssignments[userId]?.values?.count { it == shiftId } ?: 0
    }

    // --- General Scheduling Strategy Implementation (舊版 - 需要相應修正) ---
    private fun generateGeneralSchedule(
        orgId: String, groupId: String, month: String,
        users: List<User>,
        passedShiftTypes: List<ShiftType>, // ✅ 使用新名稱
        requests: List<Request>, reservations: List<Reservation>, dbRules: List<SchedulingRule>,
        manpowerPlan: ManpowerPlan, offShift: ShiftType, dates: List<String>
    ): ScheduleGenerationResult {
        // --- 沿用舊版的邏輯 ---
        // ✅ 在此處初始化時使用 passedShiftTypes
        val nShift = passedShiftTypes.find { it.name == "值班(夜)" }
        val dShift = passedShiftTypes.find { it.name == "值班(日)" }
        val sShift = passedShiftTypes.find { it.name == "白班" }

        // 檢查關鍵班別是否存在
        if (nShift == null || dShift == null || sShift == null) {
            return ScheduleGenerationResult(
                schedule = Schedule(orgId = orgId, groupId = groupId, month = month, status = "error"),
                assignments = emptyList(), score = -9999,
                violations = listOf("錯誤：找不到關鍵班別(N,D,S)。"), warnings = emptyList()
            )
        }

        // 初始化 assignments
        val userAssignments = mutableMapOf<String, MutableMap<String, String>>()
        users.forEach { userAssignments[it.id] = mutableMapOf() }

        // 1. 最高優先級：排入已核准的休假
        applyApprovedLeaves(requests, offShift, userAssignments) // 通用策略不需要 warnings

        // 2. 次高優先級：排入成員預約的班表 (通用策略使用舊版 applyReservations)
        applyReservations(reservations, userAssignments) // 需要更新以處理 List<String>

        // 3. 第三優先級：排 N 班 (只管今天)
        dates.forEach { date ->
            val day = date.split("-").last()
            val requiredCount = manpowerPlan.dailyRequirements[day]?.requirements?.get(nShift.id) ?: 0
            if (requiredCount > 0) {
                // 找出今天尚未被排班的員工，隨機選取
                val availableUsers = users.filter { userAssignments[it.id]?.get(day) == null }.shuffled()
                val usersToAssign = availableUsers.take(requiredCount)
                usersToAssign.forEach { user -> userAssignments[user.id]?.set(day, nShift.id) }
            }
        }

        // 4. 第四 & 第五優先級：排 D 班和 S 班 (回頭看昨天)
        val shiftsToProcess = listOf(dShift, sShift) // D 優先於 S
        shiftsToProcess.forEach { shift ->
            dates.forEach { date ->
                val day = date.split("-").last()
                val requiredCount = manpowerPlan.dailyRequirements[day]?.requirements?.get(shift.id) ?: 0
                val alreadyAssigned = userAssignments.values.count { it[day] == shift.id }
                var needed = requiredCount - alreadyAssigned

                if (needed > 0) {
                    // 找出當天空閒，且昨天不是 N 班的員工，隨機選取
                    val availableUsers = users.filter { user ->
                        val isAvailableToday = userAssignments[user.id]?.get(day) == null
                        if (!isAvailableToday) return@filter false

                        val yesterdayInt = day.toIntOrNull()?.minus(1) ?: 0
                        if (yesterdayInt <= 0) return@filter true // 第一天

                        val yesterdayKey = String.format("%02d", yesterdayInt)
                        val yesterdayShiftId = userAssignments[user.id]?.get(yesterdayKey)

                        yesterdayShiftId != nShift.id // 關鍵：昨天不能是 N 班
                    }.shuffled()

                    val usersToAssign = availableUsers.take(needed)
                    usersToAssign.forEach { user -> userAssignments[user.id]?.set(day, shift.id) }
                }
            }
        }

        // 6. 最低優先級：將所有剩餘空格填為 OFF
        fillRemainingWithOff(userAssignments, users, dates, offShift) // 通用策略用舊版

        // 7 & 8. 事後評分 & 建立最終結果
        // 通用策略沒有預先記錄的 violations， warnings 也為空
        // ✅ 傳遞正確的 shiftTypes
        return finalizeScheduleStrict(orgId, groupId, month, userAssignments, users, passedShiftTypes, dbRules, emptyList())
    }


    // --- Common Helper Functions (Validate, BuildResult) ---

    // Validate using RuleEngine
    private fun validateAllUsers(
        assignments: Map<String, Map<String, String>>,
        users: List<User>,
        localShiftTypes: List<ShiftType>, // ✅ 使用局部變數名
        enabledDbRules: List<SchedulingRule> // data model SchedulingRule
    ): Pair<List<RuleViolation>, Int> {
        val allViolations = mutableListOf<RuleViolation>()
        var totalScore = 0

        users.forEach { user ->
            val userAssignmentMap = assignments[user.id] ?: emptyMap()
            if (userAssignmentMap.isNotEmpty()) {
                // ✅ 傳遞正確的 shiftTypes
                val context = RuleContext(user, userAssignmentMap, localShiftTypes)
                enabledDbRules.forEach { dbRule ->
                    // 找到對應的規則實作
                    // ✅ 使用 SchedulingRuleInterface
                    val ruleImpl: SchedulingRuleInterface? = allAvailableRules.find { it.name == dbRule.ruleName }
                    if (ruleImpl != null) {
                        // 執行驗證
                        val violation = ruleImpl.evaluate(context, dbRule.parameters)
                        violation?.let {
                            allViolations.add(it)
                            // 只累加軟性規則分數
                            if (dbRule.ruleType == "soft") {
                                totalScore += dbRule.penaltyScore
                            }
                        }
                    }
                }
            }
        }
        return Pair(allViolations, totalScore)
    }

    // Build the final result object
    private fun buildResult(
        orgId: String, groupId: String, month: String,
        assignments: Map<String, Map<String, String>>, // Map<UserId, Map<Day, ShiftId>>
        users: List<User>,
        finalScore: Int, violationMessages: List<String>, warnings: List<String>
    ): ScheduleGenerationResult {
        val scheduleId = UUID.randomUUID().toString()
        val finalAssignmentObjects = users.mapNotNull { user ->
            assignments[user.id]?.let { dailyShifts ->
                Assignment(
                    id = UUID.randomUUID().toString(),
                    scheduleId = scheduleId,
                    userId = user.id,
                    userName = user.name,
                    dailyShifts = dailyShifts // assignment 存儲最終的單一 shiftId
                )
            }
        }
        val finalSchedule = Schedule(
            id = scheduleId,
            orgId = orgId, groupId = groupId, month = month, status = "draft",
            generatedAt = Date(), totalScore = finalScore, violatedRules = violationMessages,
            generationMethod = "smart"
        )
        return ScheduleGenerationResult(finalSchedule, finalAssignmentObjects, finalScore, violationMessages, warnings)
    }

    // Helper for Hospital & General: Fill remaining spots with OFF (舊版，通用策略使用)
    private fun fillRemainingWithOff(
        userAssignments: MutableMap<String, MutableMap<String, String>>,
        allUsers: List<User>, dates: List<String>, offShift: ShiftType
    ) {
        allUsers.forEach { user ->
            dates.forEach { date ->
                val day = date.split("-").last()
                if (userAssignments[user.id]?.get(day) == null) {
                    userAssignments[user.id]?.set(day, offShift.id)
                }
            }
        }
    }
    // Helper for Hospital & General: Apply reservations (舊版，通用策略使用)
    // ✅ 修改 applyReservations 以處理 List<String>
    private fun applyReservations(
        reservations: List<Reservation>, // dailyShifts is Map<String, List<String>>
        userAssignments: MutableMap<String, MutableMap<String, String>>
    ) {
        reservations.forEach { reservation ->
            // ✅ 明確指定 forEach lambda 參數類型
            reservation.dailyShifts.forEach { (day, preferences) ->
                val firstPreference = preferences.firstOrNull() // 取第一個偏好
                if (firstPreference != null && userAssignments[reservation.userId]?.get(day) == null) {
                    userAssignments[reservation.userId]?.set(day, firstPreference)
                }
            }
        }
    }
    // Helper for Hospital & General: Apply approved leaves (舊版，通用策略使用)
    private fun applyApprovedLeaves(
        requests: List<Request>,
        offShift: ShiftType,
        userAssignments: MutableMap<String, MutableMap<String, String>>
    ) {
        requests.filter { it.status == "approved" && it.type == "leave" }
            .forEach { request ->
                val day = request.date.split("-").last()
                // 直接覆蓋
                userAssignments[request.userId]?.set(day, offShift.id)
            }
    }


}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲