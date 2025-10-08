package stevedaydream.scheduler.domain.scheduling

import stevedaydream.scheduler.data.model.Assignment
import stevedaydream.scheduler.data.model.ShiftType
import stevedaydream.scheduler.data.model.User
import stevedaydream.scheduler.domain.scheduling.rules.RuleContext
import stevedaydream.scheduler.domain.scheduling.rules.RuleViolation
import stevedaydream.scheduler.domain.scheduling.rules.SchedulingRule as RuleInterface

/**
 * 規則引擎
 * 負責執行一系列排班規則並回傳結果
 */
class RuleEngine(private val availableRules: List<RuleInterface>) {

    /**
     * 驗證單一使用者的班表是否符合所有啟用的規則
     * @param user 要驗證的使用者
     * @param assignment 該使用者的班表分配
     * @param allShiftTypes 所有的班別類型
     * @param enabledDbRules 從資料庫來的、已啟用的規則列表
     * @return 違反規則的列表
     */
    fun validate(
        user: User,
        assignment: Assignment,
        allShiftTypes: List<ShiftType>,
        enabledDbRules: List<stevedaydream.scheduler.data.model.SchedulingRule>
    ): List<RuleViolation> {
        val violations = mutableListOf<RuleViolation>()
        val context = RuleContext(user, assignment.dailyShifts, allShiftTypes)

        // 建立一個 map 方便查詢
        val ruleImplementationMap = availableRules.associateBy { it.name }

        enabledDbRules.forEach { dbRule ->
            // 找到對應的規則實作
            val rule = ruleImplementationMap[dbRule.ruleName]
            if (rule != null) {
                // 執行驗證
                val violation = rule.evaluate(context, dbRule.parameters)
                violation?.let { violations.add(it) }
            }
        }
        return violations
    }
}