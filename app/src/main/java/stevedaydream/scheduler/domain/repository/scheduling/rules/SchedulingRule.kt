package stevedaydream.scheduler.domain.scheduling.rules

import stevedaydream.scheduler.data.model.Assignment
import stevedaydream.scheduler.data.model.ShiftType
import stevedaydream.scheduler.data.model.User

/**
 * 規則驗證的上下文，包含所有必要的資料
 */
data class RuleContext(
    val user: User,
    val assignments: Map<String, String>, // Map<"day", "shiftId">
    val allShiftTypes: List<ShiftType>
)

/**
 * 規則驗證後的結果
 */
data class RuleViolation(
    val ruleName: String,
    val message: String
)

/**
 * 所有排班規則都必須實作的介面
 */
interface SchedulingRule {
    /**
     * 規則的唯一識別碼，需要與 Firestore 中的 ruleName 匹配
     */
    val name: String

    /**
     * 驗證規則
     * @param context 包含驗證所需資料的上下文
     * @param parameters 來自資料庫的規則設定
     * @return 如果違反規則，則回傳 RuleViolation 物件；否則回傳 null
     */
    fun evaluate(context: RuleContext, parameters: Map<String, String>): RuleViolation?
}
