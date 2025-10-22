// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
package stevedaydream.scheduler.domain.scheduling

import android.util.Log
import stevedaydream.scheduler.data.model.*
// Explicit type aliases
import stevedaydream.scheduler.data.model.SchedulingRule as SchedulingRuleData
import stevedaydream.scheduler.domain.scheduling.rules.RuleViolation
import stevedaydream.scheduler.util.DateUtils
import java.util.*
import javax.inject.Inject // ✅ 引入 Inject

// SchedulingStrategyType Enum remains the same
enum class SchedulingStrategyType {
    HOSPITAL_BACKTRACKING,
    GENERAL,
    HOSPITAL_GREEDY
}

/**
 * 排班生成器 (協調器)
 * 負責協調 Initializer, Filler, Optimizer
 */
class ScheduleGenerator @Inject constructor( // ✅ 加入 @Inject constructor
    // 注入新的組件
    private val initializer: ScheduleInitializer,
    private val greedyFiller: GreedyScheduleFiller,
    private val backtrackingSolverFactory: BacktrackingSolverFactory, // ✅ 改為注入介面
    private val optimizer: ScheduleOptimizer,
    private val ruleEngine: RuleEngine // RuleEngine 可能 Initializer, Filler, Optimizer 都需要
) {

    // ScheduleGenerationResult remains the same
    data class ScheduleGenerationResult(
        val schedule: Schedule,
        val assignments: List<Assignment>,
        val score: Int,
        val violations: List<String>,
        val warnings: List<String>
    )

    /**
     * 生成排班表的主函數 (重構)
     */
    fun generateSchedule(
        orgId: String,
        groupId: String,
        month: String,
        users: List<User>, // 這裡應該是 allUsers
        shiftTypes: List<ShiftType>,
        requests: List<Request>,
        reservations: List<Reservation>,
        rules: List<SchedulingRuleData>, // Data class rules from DB
        manpowerPlan: ManpowerPlan?,
        strategy: SchedulingStrategyType = SchedulingStrategyType.HOSPITAL_GREEDY,
        orderedUsers: List<User>? = null, // 特定策略需要
        preScheduledRotations: Map<String, Map<String, String>>? = null // 特定策略需要
    ): ScheduleGenerationResult {
        val dates = DateUtils.getDatesInMonth(month)
        val offShift = shiftTypes.find { it.shortCode == "OFF" }

        // Basic checks
        if (offShift == null || manpowerPlan == null || manpowerPlan.dailyRequirements.isEmpty()) {
            return buildErrorResult(orgId, groupId, month, "錯誤：找不到 OFF 班別或未設定人力規劃。")
        }
        if (users.isEmpty()) {
            return buildErrorResult(orgId, groupId, month, "錯誤：沒有參與排班的使用者。")
        }

        // --- Phase 1: Initialization ---
        Log.d("ScheduleGenerator", "Phase 1: Initialization...")
        // 醫院策略需要 orderedUsers，通用策略不需要
        val usersForInit = orderedUsers ?: users
        val effectiveRotations = preScheduledRotations ?: emptyMap()

        val initResult = initializer.initializeSchedule(
            allUsers = users, // 傳遞所有使用者
            orderedUsers = usersForInit, // 傳遞排序後的使用者 (如果有的話)
            shiftTypes = shiftTypes,
            requests = requests,
            reservations = reservations,
            dbRules = rules, // Pass Data class rules
            manpowerPlan = manpowerPlan,
            offShift = offShift,
            dates = dates,
            preScheduledRotations = effectiveRotations
        )
        Log.d("ScheduleGenerator", "Phase 1 Complete. Initial Violations: ${initResult.initialViolations.size}")

        // --- Phase 2: Filling ---
        Log.d("ScheduleGenerator", "Phase 2: Filling using $strategy...")
        val filledAssignments: Map<String, Map<String, String>>
        var accumulatedViolations: List<String> = initResult.initialViolations.toList() // Start with initial violations

        when (strategy) {
            SchedulingStrategyType.HOSPITAL_BACKTRACKING -> {
                if (orderedUsers == null) return buildErrorResult(orgId, groupId, month, "錯誤：回溯排班策略缺少 orderedUsers。")

                // 準備 BacktrackingSolver 需要的資料
                val solverInitialQuotas = initResult.remainingWorkQuotas.mapValues { it.value.toMap() } +
                        initResult.remainingOffQuota.mapValues { mapOf(offShift.id to it.value) }
                            .mapValues { entry -> (initResult.remainingWorkQuotas[entry.key]?.toMap() ?: emptyMap()) + entry.value }

                // ✅ 使用注入的工廠介面創建 Solver 實例
                val solver = backtrackingSolverFactory.create(
                    orderedUsers, dates.size, dates,
                    initResult.initialAssignments.mapValues { it.value.toMap() }, // Pass immutable
                    solverInitialQuotas, // Pass immutable, combined quotas
                    shiftTypes, rules, ruleEngine // 傳遞 ruleEngine 給 Solver
                )

                if (solver.solve()) {
                    filledAssignments = solver.getSolution() ?: run {
                        // 雖然 solve 返回 true，但 getSolution 是 null，這是不預期的情況
                        Log.e("ScheduleGenerator", "Backtracking solve() was true but getSolution() returned null!")
                        return buildErrorResult(orgId, groupId, month, "回溯求解器內部錯誤", initResult.initialViolations)
                    }
                    // 回溯法成功找到解，理論上不應增加硬性規則的 violation
                    // 但可能需要加入 Solver 內部發現的無法解決的衝突 (e.g., 人力不足)
                } else {
                    // 回溯法找不到解
                    return buildErrorResult(orgId, groupId, month, "回溯求解器未能找到滿足所有硬性條件的班表", initResult.initialViolations)
                }
            }
            SchedulingStrategyType.HOSPITAL_GREEDY -> {
                if (orderedUsers == null) return buildErrorResult(orgId, groupId, month, "錯誤：醫院排班策略缺少 orderedUsers。")

                val fillResult = greedyFiller.fillSchedule(
                    initialResult = initResult, // 傳遞可變的狀態
                    dates = dates,
                    manpowerPlan = manpowerPlan,
                    orderedUsers = orderedUsers,
                    allUsers = users,
                    shiftTypes = shiftTypes,
                    dbRules = rules // Pass Data class rules
                )
                filledAssignments = fillResult.finalAssignments
                accumulatedViolations = fillResult.accumulatedViolations
            }
            SchedulingStrategyType.GENERAL -> {
                // 通用策略的填充邏輯 (如果需要，可以建立 GeneralScheduleFiller 或在此實現)
                // 這裡暫時使用與 Greedy 類似的填充，但不使用 orderedUsers 排序 S 班
                val fillResult = greedyFiller.fillSchedule(
                    initialResult = initResult,
                    dates = dates,
                    manpowerPlan = manpowerPlan,
                    orderedUsers = users, // 通用策略用 allUsers 排序
                    allUsers = users,
                    shiftTypes = shiftTypes,
                    dbRules = rules
                )
                filledAssignments = fillResult.finalAssignments
                accumulatedViolations = fillResult.accumulatedViolations
                Log.w("ScheduleGenerator", "General strategy filler not fully implemented, using greedy filler logic without specific ordering.")
                // return generateGeneralSchedule(orgId, groupId, month, users, shiftTypes, requests, reservations, rules, manpowerPlan, offShift, dates) // 舊的通用邏輯
            }
        }
        Log.d("ScheduleGenerator", "Phase 2 Complete. Accumulated Violations: ${accumulatedViolations.size}")


        // --- Phase 3: Optimization (Placeholder) ---
        Log.d("ScheduleGenerator", "Phase 3: Optimization...")
        // 分離硬性規則和軟性規則
        val hardRules = rules.filter { it.ruleType == "hard" }
        val softRules = rules.filter { it.ruleType == "soft" }

        val optResult = optimizer.optimizeSchedule(
            initialAssignments = filledAssignments,
            softRules = softRules, // Pass Data class soft rules
            hardRules = hardRules, // Pass Data class hard rules
            users = users,
            shiftTypes = shiftTypes,
            dates = dates
        )
        // 目前 optimizer 只是 placeholder，所以 finalAssignments == filledAssignments
        val finalAssignments = optResult.optimizedAssignments
        val finalScore = optResult.finalScore // 使用優化後的分數
        // 合併填充階段和優化階段可能產生的違規 (雖然優化階段理論上不應產生硬性違規)
        val finalViolations = (accumulatedViolations + optResult.optimizationDetails).distinct()
        Log.d("ScheduleGenerator", "Phase 3 Complete. Final Violations: ${finalViolations.size}, Final Score: $finalScore")


        // --- Finalize: Validate and Build Result ---
        // 最終驗證應該使用最終的 assignments 和所有 enabled rules (硬+軟)
        // 但 finalizeScheduleStrict 內部會做 validateAllUsers，所以這裡傳遞 optResult 即可
        // 注意：finalizeScheduleStrict 內部計算分數，會覆蓋 optResult.finalScore，這是預期行為
        return finalizeScheduleStrict(
            orgId = orgId,
            groupId = groupId,
            month = month,
            finalUserAssignments = finalAssignments, // 傳遞優化後的結果
            users = users, // 傳遞所有使用者
            passedShiftTypes = shiftTypes,
            dbRules = rules, // 傳遞所有規則給最終驗證
            violationsFromProcess = finalViolations // 傳遞所有累積的違規訊息
        )
    }

    // --- 保留 checkAllHardRulesRealtime, finalizeScheduleStrict, validateAllUsers, buildResult, countAssignedShifts ---
    // Note: checkAllHardRulesRealtime 已經搬到 Initializer 和 Filler 內部了，這裡可以移除

    /**
     * 嚴格最終化 (稍微修改以適應新流程)
     */
    private fun finalizeScheduleStrict(
        orgId: String, groupId: String, month: String,
        finalUserAssignments: Map<String, Map<String, String>>,
        users: List<User>,
        passedShiftTypes: List<ShiftType>, // Renamed from localShiftTypes for clarity
        dbRules: List<SchedulingRuleData>, // Expect Data class
        violationsFromProcess: List<String>
    ): ScheduleGenerationResult {
        // Post-validation and scoring using final assignments and all enabled rules
        val enabledDbRules = dbRules.filter { it.isEnabled }
        val (validationViolations, finalScore) = validateAllUsers(finalUserAssignments, users, passedShiftTypes, enabledDbRules)
        // Combine violations from the entire process (init, fill, optimize) and final validation
        val allViolationMessages = (violationsFromProcess + validationViolations.map { it.message }).distinct()

        // Build the final result object
        return buildResult(orgId, groupId, month, finalUserAssignments, users, finalScore, allViolationMessages, emptyList()) // warnings are currently unused
    }

    /**
     * 驗證所有使用者班表並計算分數 (最終驗證)
     */
    private fun validateAllUsers(
        assignments: Map<String, Map<String, String>>,
        users: List<User>,
        localShiftTypes: List<ShiftType>,
        enabledDbRules: List<SchedulingRuleData> // Expect enabled Data class rules
    ): Pair<List<RuleViolation>, Int> {
        val allViolations = mutableListOf<RuleViolation>()
        var totalScore = 0
        val userMap = users.associateBy { it.id }

        assignments.forEach { (userId, userAssignmentMap) ->
            val user = userMap[userId]
            if (user != null && userAssignmentMap.isNotEmpty()) {
                val assignmentObj = Assignment(userId = userId, dailyShifts = userAssignmentMap)
                // Use RuleEngine to validate against enabled rules
                val violations = ruleEngine.validate(user, assignmentObj, localShiftTypes, enabledDbRules)
                allViolations.addAll(violations)
                // Calculate score based on soft rule violations
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
     * 建立最終 ScheduleGenerationResult (保持不變)
     */
    private fun buildResult(
        orgId: String, groupId: String, month: String,
        assignments: Map<String, Map<String, String>>,
        users: List<User>,
        finalScore: Int, violationMessages: List<String>, warnings: List<String>
    ): ScheduleGenerationResult {
        val scheduleId = UUID.randomUUID().toString()
        val userMap = users.associateBy { it.id } // Create map for quick lookup
        val finalAssignmentObjects = assignments.mapNotNull { (userId, dailyShifts) ->
            userMap[userId]?.let { user -> // Find user from map
                Assignment(
                    id = UUID.randomUUID().toString(),
                    scheduleId = scheduleId,
                    userId = userId,
                    userName = user.name, // Get name from User object
                    dailyShifts = dailyShifts
                )
            }
        }
        val finalSchedule = Schedule(
            id = scheduleId,
            orgId = orgId, groupId = groupId, month = month, status = "draft",
            generatedAt = Date(), totalScore = finalScore, violatedRules = violationMessages,
            generationMethod = "smart" // Or determine based on strategy used
        )
        return ScheduleGenerationResult(finalSchedule, finalAssignmentObjects, finalScore, violationMessages, warnings)
    }

    /**
     * 計算指定班別次數 (保持不變)
     */
    private fun countAssignedShifts(userId: String, shiftId: String, userAssignments: Map<String, Map<String, String>>): Int {
        return userAssignments[userId]?.values?.count { it == shiftId } ?: 0
    }

    /**
     * 輔助函式：建立錯誤結果
     */
    private fun buildErrorResult(orgId: String, groupId: String, month: String, errorMsg: String, existingViolations: List<String> = emptyList()): ScheduleGenerationResult {
        return ScheduleGenerationResult(
            violations = existingViolations + errorMsg,
            schedule = Schedule(orgId = orgId, groupId = groupId, month = month, status = "error"),
            assignments = emptyList(),
            score = -9999,
            warnings = emptyList()
        )
    }
}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲