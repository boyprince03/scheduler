// ▼▼▼▼▼▼▼▼▼▼▼▼ 新檔案開始 ▼▼▼▼▼▼▼▼▼▼▼▼
package stevedaydream.scheduler.domain.scheduling

import stevedaydream.scheduler.data.model.*
import stevedaydream.scheduler.data.model.SchedulingRule as SchedulingRuleData

/**
 * 負責排班的第三階段：優化
 * - 根據軟性規則，透過交換等方式改善班表品質
 */
class ScheduleOptimizer(
    private val ruleEngine: RuleEngine // 可能需要 RuleEngine 來檢查硬性規則
) {

    // 優化結果的資料結構
    data class OptimizationResult(
        val optimizedAssignments: Map<String, Map<String, String>>, // 優化後的班表
        val initialScore: Int,
        val finalScore: Int,
        val optimizationDetails: List<String> // 可以記錄優化過程
    )

    /**
     * 執行排班優化
     * @param initialAssignments 從填充階段得到的班表
     * @param softRules 軟性規則列表 (需要定義如何傳入)
     * @param hardRules 硬性規則列表 (用於檢查交換合法性)
     * @param users 使用者列表
     * @param shiftTypes 班別列表
     * @param dates 日期列表
     * @return 優化結果
     */
    fun optimizeSchedule(
        initialAssignments: Map<String, Map<String, String>>,
        softRules: List<SchedulingRuleData>, // 假設軟性規則也用 SchedulingRuleData
        hardRules: List<SchedulingRuleData>,
        users: List<User>,
        shiftTypes: List<ShiftType>,
        dates: List<String>
        // 可能還需要 manpowerPlan, quotas 等資料來檢查合法性
    ): OptimizationResult {

        // --- 優化邏輯 (目前為空) ---
        // 1. 計算初始分數 (根據 softRules)
        val initialScore = calculateScore(initialAssignments, softRules, users, shiftTypes)

        // 2. 執行交換操作 (迴圈)
        //    - 選擇交換類型 (同日、異日、移動)
        //    - 選擇交換對象
        //    - 模擬交換
        //    - 檢查硬性規則 (使用 ruleEngine.validate 或 checkAllHardRulesRealtime)
        //    - 計算交換後分數
        //    - 如果合法且分數更低，則確認交換
        //    - 直到無法改進或達到迭代上限

        // 3. 返回結果
        val optimizedAssignments = initialAssignments // 目前直接返回
        val finalScore = initialScore // 目前分數不變

        return OptimizationResult(
            optimizedAssignments = optimizedAssignments,
            initialScore = initialScore,
            finalScore = finalScore,
            optimizationDetails = listOf("優化功能尚未實作")
        )
    }

    /**
     * 計算班表的軟性規則總分
     */
    private fun calculateScore(
        assignments: Map<String, Map<String, String>>,
        softRules: List<SchedulingRuleData>,
        users: List<User>,
        shiftTypes: List<ShiftType>
    ): Int {
        var totalScore = 0
        // 遍歷所有使用者
        users.forEach { user ->
            val userAssignmentMap = assignments[user.id] ?: emptyMap()
            if (userAssignmentMap.isNotEmpty()) {
                val assignmentObj = Assignment(userId = user.id, dailyShifts = userAssignmentMap)
                // 執行所有軟性規則
                val violations = ruleEngine.validate(user, assignmentObj, shiftTypes, softRules)
                violations.forEach { violation ->
                    // 累加懲罰分數
                    val ruleData = softRules.find { it.ruleName == violation.ruleName }
                    totalScore += ruleData?.penaltyScore ?: 0
                }
            }
        }
        return totalScore
    }

}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 新檔案結束 ▲▲▲▲▲▲▲▲▲▲▲▲
