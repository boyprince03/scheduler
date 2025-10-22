package stevedaydream.scheduler.domain.scheduling

import stevedaydream.scheduler.data.model.Assignment
import stevedaydream.scheduler.data.model.ShiftType
import stevedaydream.scheduler.data.model.User
import stevedaydream.scheduler.domain.scheduling.rules.RuleContext
import stevedaydream.scheduler.domain.scheduling.rules.RuleViolation
// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
// 引入 interface SchedulingRule
import stevedaydream.scheduler.domain.scheduling.rules.SchedulingRule as SchedulingRuleInterface
// 引入 data class SchedulingRule
import stevedaydream.scheduler.data.model.SchedulingRule as SchedulingRuleData

/**
 * 規則引擎
 * 負責執行一系列排班規則並回傳結果
 */
class RuleEngine(private val availableRules: List<SchedulingRuleInterface>) {

    // 建立一個 map 方便查詢實作
    private val ruleImplementationMap = availableRules.associateBy { it.name }

    /**
     * 根據規則名稱查找對應的規則實作 (Interface)
     * @param ruleName 規則名稱 (來自 SchedulingRuleData)
     * @return 找到的 SchedulingRuleInterface 實作，或 null
     */
    fun findRuleImplementation(ruleName: String): SchedulingRuleInterface? {
        return ruleImplementationMap[ruleName]
    }

    /**
     * 驗證單一使用者的班表是否符合所有啟用的規則
     * @param enabledDbRules 從資料庫來的、已啟用的規則列表 (Data class)
     */
    fun validate(
        user: User,
        assignment: Assignment,
        allShiftTypes: List<ShiftType>,
        enabledDbRules: List<SchedulingRuleData> // 接收 Data class
    ): List<RuleViolation> {
        val violations = mutableListOf<RuleViolation>()
        val context = RuleContext(user, assignment.dailyShifts, allShiftTypes)

        enabledDbRules.forEach { dbRule ->
            // 使用輔助函式查找實作
            val rule = findRuleImplementation(dbRule.ruleName)
            if (rule != null) {
                // 執行驗證
                val violation = rule.evaluate(context, dbRule.parameters)
                violation?.let { violations.add(it) }
            }
            // 可以加入 Log 警告找不到實作的情況
            // else { Log.w("RuleEngine", "找不到規則 ${dbRule.ruleName} 的實作") }
        }
        return violations
    }
}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲
