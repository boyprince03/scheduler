package stevedaydream.scheduler.domain.scheduling.rules

class MaxConsecutiveWorkDaysRule : SchedulingRule {
    override val name: String = "連續上班不超過N天"

    override fun evaluate(context: RuleContext, parameters: Map<String, String>): RuleViolation? {
        val maxDays = parameters["maxDays"]?.toIntOrNull() ?: 6 // 從參數讀取，預設為 6
        val offShift = context.allShiftTypes.find { it.shortCode == "OFF" } ?: return null

        var maxConsecutive = 0
        var currentConsecutive = 0

        context.assignments.entries.sortedBy { it.key.toInt() }.forEach { (_, shiftId) ->
            if (shiftId != offShift.id) {
                currentConsecutive++
            } else {
                maxConsecutive = maxOf(maxConsecutive, currentConsecutive)
                currentConsecutive = 0
            }
        }
        val finalConsecutive = maxOf(maxConsecutive, currentConsecutive)

        return if (finalConsecutive > maxDays) {
            RuleViolation(name, "${context.user.name}: 連續上班 ${finalConsecutive} 天，超過上限 ${maxDays} 天")
        } else {
            null
        }
    }
}