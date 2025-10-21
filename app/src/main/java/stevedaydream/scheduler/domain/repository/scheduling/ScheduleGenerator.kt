// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
package stevedaydream.scheduler.domain.scheduling

import stevedaydream.scheduler.data.model.*
import stevedaydream.scheduler.domain.repository.scheduling.rules.MinRestBetweenShiftsRule
import stevedaydream.scheduler.domain.scheduling.rules.MaxConsecutiveWorkDaysRule
import stevedaydream.scheduler.domain.scheduling.rules.NightShiftFollowupRule
import stevedaydream.scheduler.domain.scheduling.rules.RuleContext // 引入 RuleContext
import stevedaydream.scheduler.domain.scheduling.rules.RuleViolation
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
        val score: Int,
        val violations: List<String>,
        val warnings: List<String> // 新增：用於記錄非致命性問題 (如配額超標、人力不足)
    )

    // 定義所有可用的規則實例
    private val allAvailableRules = listOf(
        MaxConsecutiveWorkDaysRule(),
        MinRestBetweenShiftsRule(),
        NightShiftFollowupRule()
        // 可以根據需要加入更多規則
    )
    // 建立規則引擎實例
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
        shiftTypes: List<ShiftType>,
        requests: List<Request>,
        reservations: List<Reservation>,
        rules: List<SchedulingRule>, // DB來的啟用規則
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
                    generateHospitalSchedule(
                        orgId, groupId, month, users, shiftTypes, requests, reservations, rules,
                        manpowerPlan, offShift, dates, orderedUsers, preScheduledRotations
                    )
                }
            }
            else -> { // "general" or any other unknown strategy
                generateGeneralSchedule(
                    orgId, groupId, month, users, shiftTypes, requests, reservations, rules,
                    manpowerPlan, offShift, dates
                )
            }
        }
    }

    // --- Hospital Scheduling Strategy Implementation ---
    /**
     * 醫院排班策略的具體實現
     */
    private fun generateHospitalSchedule(
        orgId: String, groupId: String, month: String,
        allUsers: List<User>, // 原始 User 列表
        shiftTypes: List<ShiftType>,
        requests: List<Request>, reservations: List<Reservation>, dbRules: List<SchedulingRule>, // DB來的啟用規則
        manpowerPlan: ManpowerPlan, offShift: ShiftType, dates: List<String>,
        orderedUsers: List<User>, // 已排序的 User 列表
        preScheduledRotations: Map<String, Map<String, String>> // Map<UserId, Map<Day, ShiftId>>
    ): ScheduleGenerationResult {

        val warnings = mutableListOf<String>() // 儲存警告訊息
        // 初始化 userAssignments Map: UserId -> Day -> ShiftId
        val userAssignments = mutableMapOf<String, MutableMap<String, String>>()
        allUsers.forEach { userAssignments[it.id] = mutableMapOf() }

        // 找出 N, S, D 班別 (確保按 N -> S -> D 順序)
        val nShift = shiftTypes.find { it.name == "值班(夜)" }
        val sShift = shiftTypes.find { it.name == "白班" }
        val dShift = shiftTypes.find { it.name == "值班(日)" }
        val workShifts = listOfNotNull(nShift, sShift, dShift) // 過濾掉 null

        // 【前置計算】班別平均配額
        val shiftQuotas = calculateShiftQuotas(manpowerPlan, orderedUsers, workShifts, dates.size)
        // 維護剩餘配額 (複製一份初始配額，用於後續扣減)
        val remainingQuotas = shiftQuotas.mapValues { (_, quotas) -> quotas.toMutableMap() }.toMutableMap()

        // --- 排班流程 ---

        // 【步驟 1：最高優先級】填入「輪替預排程」班別
        applyPreScheduledRotations(preScheduledRotations, userAssignments, remainingQuotas, warnings)

        // 【步驟 2：次高優先級】處理「已核准休假」
        applyApprovedLeaves(requests, offShift, userAssignments, warnings) // 傳入 warnings List

        // 【步驟 3】處理「預約/偏好班別」
        applyReservations(reservations, userAssignments)

        // 【步驟 4：核心排班】依 N → S → D 順序填滿剩餘人力需求
        assignRemainingShiftsHospital(
            dates, manpowerPlan, workShifts, userAssignments, remainingQuotas,
            orderedUsers, allUsers, shiftTypes, warnings // 傳入 warnings List
        )

        // 【步驟 5：收尾】填入 OFF
        fillRemainingWithOff(userAssignments, allUsers, dates, offShift)

        // 【步驟 6 & 7：驗證與產出】
        return finalizeSchedule(orgId, groupId, month, userAssignments, allUsers, shiftTypes, dbRules, warnings)
    }

    // Helper for Hospital: Calculate shift quotas
    /**
     * 計算每個使用者、每種班別的目標排班次數
     */
    private fun calculateShiftQuotas(
        manpowerPlan: ManpowerPlan,
        orderedUsers: List<User>,
        workShifts: List<ShiftType>, // 只計算 N, S, D 等工作班別
        daysInMonth: Int // 這個參數目前沒用到，但保留可能未來擴充
    ): Map<String, Map<String, Int>> { // Map<UserId, Map<ShiftId, Quota>>
        val quotas = mutableMapOf<String, MutableMap<String, Int>>()
        orderedUsers.forEach { quotas[it.id] = mutableMapOf() } // 初始化 Map 結構
        val numUsers = orderedUsers.size
        if (numUsers == 0) return emptyMap() // 如果沒有使用者，直接返回空 Map

        workShifts.forEach { shift ->
            // 計算該班別整月總需求
            var totalDemand = 0
            manpowerPlan.dailyRequirements.values.forEach { dailyReq ->
                totalDemand += dailyReq.requirements[shift.id] ?: 0
            }

            if (totalDemand > 0) {
                // 計算基礎配額和餘數
                val baseQuota = floor(totalDemand.toDouble() / numUsers).toInt()
                val remainder = totalDemand % numUsers

                // 根據排序分配配額 (前面的人多分一個)
                orderedUsers.forEachIndexed { index, user ->
                    val userQuota = if (index < remainder) baseQuota + 1 else baseQuota
                    quotas[user.id]?.set(shift.id, userQuota)
                }
            } else {
                // 如果某班別整月無需求，則所有人的配額皆為 0
                orderedUsers.forEach { user ->
                    quotas[user.id]?.set(shift.id, 0)
                }
            }
        }
        return quotas
    }

    // Helper for Hospital: Apply pre-scheduled rotations
    /**
     * 將預排好的輪替班別填入 assignments，並扣除剩餘配額
     */
    private fun applyPreScheduledRotations(
        preScheduledRotations: Map<String, Map<String, String>>, // Map<UserId, Map<Day, ShiftId>>
        userAssignments: MutableMap<String, MutableMap<String, String>>,
        remainingQuotas: MutableMap<String, MutableMap<String, Int>>,
        warnings: MutableList<String>
    ) {
        preScheduledRotations.forEach { (userId, dailyShifts) ->
            dailyShifts.forEach { (day, shiftId) ->
                // 檢查該儲存格是否已被佔用 (理論上輪替是最高優先級，不應發生)
                if (userAssignments[userId]?.get(day) == null) {
                    userAssignments[userId]?.set(day, shiftId)
                    // 扣除剩餘配額
                    remainingQuotas[userId]?.let { quotas ->
                        if (quotas.containsKey(shiftId)) {
                            quotas[shiftId] = (quotas[shiftId] ?: 0) - 1
                        }
                    }
                } else {
                    // 記錄極不可能發生的衝突
                    warnings.add("警告：使用者 $userId 在 $day 的輪替班 $shiftId 與先前排班衝突（極不可能發生）。")
                }
            }
        }
    }

    // Helper for Hospital & General: Apply approved leaves
    /**
     * 處理已核准的休假請求
     * @param warnings 可選，僅醫院策略需要記錄與輪替的衝突
     */
    private fun applyApprovedLeaves(
        requests: List<Request>,
        offShift: ShiftType,
        userAssignments: MutableMap<String, MutableMap<String, String>>,
        warnings: MutableList<String>? = null // 設為可選參數
    ) {
        requests.filter { it.status == "approved" && it.type == "leave" }
            .forEach { request ->
                val day = request.date.split("-").last() // 取日期的 "日" 部分
                val existingAssignment = userAssignments[request.userId]?.get(day)

                if (existingAssignment == null) {
                    // 如果格子是空的，填入 OFF
                    userAssignments[request.userId]?.set(day, offShift.id)
                } else if (warnings != null && existingAssignment != offShift.id) {
                    // 醫院策略下，如果格子已被輪替班佔據，記錄警告
                    warnings.add("警告：${request.userName} 於 ${request.date} 的休假與輪替班衝突，休假未排入。")
                }
                // 如果已存在的是 OFF (可能是手動或其他請求)，則無需處理
            }
    }

    // Helper for Hospital & General: Apply reservations
    /**
     * 處理預約/偏好的班別請求
     */
    private fun applyReservations(
        reservations: List<Reservation>,
        userAssignments: MutableMap<String, MutableMap<String, String>>
    ) {
        reservations.forEach { reservation ->
            reservation.dailyShifts.forEach { (day, shiftId) ->
                // 確保這個位置是空的 (未被輪替或休假佔據)
                if (userAssignments[reservation.userId]?.get(day) == null) {
                    userAssignments[reservation.userId]?.set(day, shiftId)
                    // 注意：預約班別不影響剩餘配額，配額是針對自動排班部分的目標
                }
            }
        }
    }

    // Helper for Hospital: Assign remaining shifts (N -> S -> D) respecting quotas and rules
    /**
     * 核心排班邏輯 (醫院策略)：按 N -> S -> D 順序填滿剩餘人力需求
     * 考慮硬性規則和剩餘配額，並特殊處理 D 班的平均分散
     */
    private fun assignRemainingShiftsHospital(
        dates: List<String>, manpowerPlan: ManpowerPlan, workShifts: List<ShiftType>, // N, S, D
        userAssignments: MutableMap<String, MutableMap<String, String>>,
        remainingQuotas: MutableMap<String, MutableMap<String, Int>>,
        orderedUsers: List<User>, // 用於 N, S 班排序
        allUsers: List<User>, // 所有參與排班的使用者
        shiftTypes: List<ShiftType>, // 所有班別，用於規則檢查
        warnings: MutableList<String>
    ) {
        val shiftTypeMap = shiftTypes.associateBy { it.id } // 方便查找 ShiftType 物件
        val userMap = allUsers.associateBy { it.id } // 方便查找 User 物件
        val dShift = shiftTypes.find { it.name == "值班(日)" } // D班需要特殊處理

        // 依序處理 N, S, D 班
        workShifts.forEach { shiftToAssign ->
            dates.forEach { date ->
                val day = date.split("-").last() // 取日期的 "日" 部分
                // 計算當天還需要多少人
                val requiredCount = manpowerPlan.dailyRequirements[day]?.requirements?.get(shiftToAssign.id) ?: 0
                val assignedCount = userAssignments.values.count { it[day] == shiftToAssign.id }
                var needed = requiredCount - assignedCount

                if (needed > 0) {
                    // 1. 找出當天未排班的人
                    val availableUserIds = allUsers.map { it.id }
                        .filter { userId -> userAssignments[userId]?.get(day) == null }

                    // 2. 篩選符合硬性規則的人 (例如 N 後不能接 S/D)
                    val ruleCompliantCandidates = availableUserIds.filter { userId ->
                        checkHardRules(userId, day, shiftToAssign.id, userAssignments, shiftTypes)
                    }

                    // --- 指派邏輯 ---
                    // 分為符合配額和不符合配額兩組
                    val candidatesWithQuota = ruleCompliantCandidates.filter { userId ->
                        (remainingQuotas[userId]?.get(shiftToAssign.id) ?: 0) > 0
                    }
                    val candidatesWithoutQuota = ruleCompliantCandidates.filter { userId ->
                        userId !in candidatesWithQuota.toSet() // 確保不重複
                    }

                    // 排序策略
                    // D班：優先選 D 班次數少的人
                    val sortedCandidatesWithQuota = if (shiftToAssign == dShift) {
                        candidatesWithQuota.sortedBy { userId -> countAssignedShifts(userId, dShift.id, userAssignments) }
                    } else {
                        // N, S班：按管理員設定的順序
                        candidatesWithQuota.sortedBy { userId -> orderedUsers.indexOfFirst { it.id == userId } }
                    }
                    // 不符合配額的人也按相同邏輯排序
                    val sortedCandidatesWithoutQuota = if (shiftToAssign == dShift) {
                        candidatesWithoutQuota.sortedBy { userId -> countAssignedShifts(userId, dShift.id, userAssignments) }
                    } else {
                        candidatesWithoutQuota.sortedBy { userId -> orderedUsers.indexOfFirst { it.id == userId } }
                    }


                    // 3. 優先從符合配額的人中指派
                    val assignedFromQuota = sortedCandidatesWithQuota.take(needed)
                    assignedFromQuota.forEach { userId ->
                        userAssignments[userId]?.set(day, shiftToAssign.id)
                        remainingQuotas[userId]?.let { it[shiftToAssign.id] = (it[shiftToAssign.id] ?: 0) - 1 }
                        needed--
                    }

                    // 4. 如果人數不足，從不符合配額但符合規則的人中指派 (放寬配額限制)
                    if (needed > 0) {
                        val assignedWithoutQuota = sortedCandidatesWithoutQuota.take(needed)
                        assignedWithoutQuota.forEach { userId ->
                            userAssignments[userId]?.set(day, shiftToAssign.id)
                            remainingQuotas[userId]?.let { it[shiftToAssign.id] = (it[shiftToAssign.id] ?: 0) - 1 }
                            warnings.add("警告：${userMap[userId]?.name} 於 $date 的 ${shiftToAssign.name} 超出配額。")
                            needed--
                        }
                    }

                    // 5. 如果人數仍不足 (連符合規則的人都不夠)，記錄人力不足警告
                    if (needed > 0) {
                        warnings.add("警告：$date 的 ${shiftToAssign.name} 缺少 $needed 人力 (找不到符合規則的人)。")
                    }
                }
            }
        }
    }

    // Helper: Count assigned shifts for a user
    /**
     * 計算指定使用者已被排定某班別的次數
     */
    private fun countAssignedShifts(userId: String, shiftId: String, userAssignments: Map<String, Map<String, String>>): Int {
        return userAssignments[userId]?.values?.count { it == shiftId } ?: 0
    }


    // Helper: Check basic hard rules (shift continuity) before assignment
    /**
     * 檢查基本的硬性規則 (目前只檢查 N->S/D 銜接)
     * 完整的規則檢查 (如休息時間、連續上班) 由 RuleEngine 在最後進行
     */
    private fun checkHardRules(
        userId: String, day: String, shiftIdToAssign: String,
        currentAssignments: Map<String, Map<String, String>>, shiftTypes: List<ShiftType>
    ): Boolean {
        // 取得 N, D, S 班的 ID
        val nShiftId = shiftTypes.find { it.name == "值班(夜)" }?.id
        val dShiftId = shiftTypes.find { it.name == "值班(日)" }?.id
        val sShiftId = shiftTypes.find { it.name == "白班" }?.id

        // 檢查前一天
        val yesterdayInt = day.toIntOrNull()?.minus(1) ?: 0
        if (yesterdayInt > 0) { // 確保不是第一天
            val yesterdayKey = String.format("%02d", yesterdayInt) // 格式化為 "01", "02" ...
            val yesterdayShiftId = currentAssignments[userId]?.get(yesterdayKey)

            // N 班後面不能接 D 或 S
            if (yesterdayShiftId == nShiftId && (shiftIdToAssign == dShiftId || shiftIdToAssign == sShiftId)) {
                return false // 違反規則
            }
        }

        // 可以加入更多硬性規則檢查，但目前只檢查 N->D/S
        return true // 符合規則
    }

    // Helper for Hospital & General: Fill remaining spots with OFF
    /**
     * 將所有剩餘的空格填為 OFF 班
     */
    private fun fillRemainingWithOff(
        userAssignments: MutableMap<String, MutableMap<String, String>>,
        allUsers: List<User>, dates: List<String>, offShift: ShiftType
    ) {
        allUsers.forEach { user ->
            dates.forEach { date ->
                val day = date.split("-").last()
                // 如果格子是空的 (null)
                if (userAssignments[user.id]?.get(day) == null) {
                    userAssignments[user.id]?.set(day, offShift.id)
                }
            }
        }
    }

    // --- General Scheduling Strategy Implementation ---
    /**
     * 通用排班策略的實現 (保留原始邏輯)
     */
    private fun generateGeneralSchedule(
        orgId: String, groupId: String, month: String,
        users: List<User>, shiftTypes: List<ShiftType>,
        requests: List<Request>, reservations: List<Reservation>, dbRules: List<SchedulingRule>,
        manpowerPlan: ManpowerPlan, offShift: ShiftType, dates: List<String>
    ): ScheduleGenerationResult {
        // --- 沿用舊版的邏輯 ---
        val nShift = shiftTypes.find { it.name == "值班(夜)" }
        val dShift = shiftTypes.find { it.name == "值班(日)" }
        val sShift = shiftTypes.find { it.name == "白班" }

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

        // 2. 次高優先級：排入成員預約的班表
        applyReservations(reservations, userAssignments)

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
        fillRemainingWithOff(userAssignments, users, dates, offShift)

        // 7 & 8. 事後評分 & 建立最終結果
        return finalizeSchedule(orgId, groupId, month, userAssignments, users, shiftTypes, dbRules, mutableListOf()) // 通用策略無警告
    }


    // --- Common Helper Functions ---

    // Finalize: Validate and build result
    /**
     * 最終步驟：驗證班表、計算分數並打包成 ScheduleGenerationResult
     * @param warnings 接收來自排班流程中的警告訊息
     */
    private fun finalizeSchedule(
        orgId: String, groupId: String, month: String,
        finalUserAssignments: Map<String, Map<String, String>>, // 改為不可變 Map
        users: List<User>, shiftTypes: List<ShiftType>, dbRules: List<SchedulingRule>,
        warnings: List<String> // 接收警告列表
    ): ScheduleGenerationResult {
        // --- 7. 事後評分 ---
        val (finalViolations, finalScore) = validateAllUsers(finalUserAssignments, users, shiftTypes, dbRules)
        val violationMessages = finalViolations.map { it.message }

        // --- 8. 建立最終結果 ---
        return buildResult(orgId, groupId, month, finalUserAssignments, users, finalScore, violationMessages, warnings)
    }

    // Validate using RuleEngine
    /**
     * 使用 RuleEngine 驗證所有使用者的班表
     * @return Pair<違規列表, 總扣分>
     */
    private fun validateAllUsers(
        assignments: Map<String, Map<String, String>>,
        users: List<User>,
        shiftTypes: List<ShiftType>,
        enabledDbRules: List<SchedulingRule> // 只傳入已啟用的規則
    ): Pair<List<RuleViolation>, Int> {
        val allViolations = mutableListOf<RuleViolation>()
        var totalScore = 0

        users.forEach { user ->
            val userAssignmentMap = assignments[user.id] ?: emptyMap()
            // 如果使用者沒有任何排班 (例如新加入或完全休假)，跳過驗證避免 RuleEngine 出錯
            if (userAssignmentMap.isNotEmpty()) {
                // 建立規則上下文
                val context = RuleContext(user, userAssignmentMap, shiftTypes)
                // 遍歷所有啟用的規則
                enabledDbRules.forEach { dbRule ->
                    // 找到該規則的實作
                    val ruleImpl = allAvailableRules.find { it.name == dbRule.ruleName }
                    if (ruleImpl != null) {
                        // 執行評估
                        val violation = ruleImpl.evaluate(context, dbRule.parameters)
                        violation?.let {
                            allViolations.add(it) // 加入違規列表
                            totalScore += dbRule.penaltyScore // 累加扣分
                        }
                    }
                }
            }
        }
        return Pair(allViolations, totalScore)
    }

    // Build the final result object
    /**
     * 將最終計算結果打包成 ScheduleGenerationResult 物件
     */
    private fun buildResult(
        orgId: String, groupId: String, month: String,
        assignments: Map<String, Map<String, String>>, // Map<UserId, Map<Day, ShiftId>>
        users: List<User>,
        finalScore: Int, violationMessages: List<String>, warnings: List<String> // 新增 warnings
    ): ScheduleGenerationResult {
        // 產生 Schedule 物件的 ID
        val scheduleId = UUID.randomUUID().toString()

        // 將 Map 轉換為 List<Assignment>
        val finalAssignmentObjects = users.mapNotNull { user ->
            assignments[user.id]?.let { dailyShifts ->
                Assignment(
                    id = UUID.randomUUID().toString(), // 為每個 Assignment 產生獨立 ID
                    scheduleId = scheduleId, // 關聯到 Schedule
                    userId = user.id,
                    userName = user.name,
                    dailyShifts = dailyShifts
                )
            }
        }

        // 建立 Schedule 物件
        val finalSchedule = Schedule(
            id = scheduleId,
            orgId = orgId, groupId = groupId, month = month, status = "draft", // 初始狀態為草稿
            generatedAt = Date(), totalScore = finalScore, violatedRules = violationMessages,
            generationMethod = "smart" // 標記為智慧排班生成
        )

        // 返回最終結果
        return ScheduleGenerationResult(finalSchedule, finalAssignmentObjects, finalScore, violationMessages, warnings)
    }
}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲