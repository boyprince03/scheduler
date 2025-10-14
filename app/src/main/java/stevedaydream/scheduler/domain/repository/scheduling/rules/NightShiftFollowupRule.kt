package stevedaydream.scheduler.domain.scheduling.rules

import stevedaydream.scheduler.data.model.ShiftType

class NightShiftFollowupRule : SchedulingRule {
    // 規則的唯一名稱，需要與資料庫中的 ruleName 匹配
    override val name: String = "夜班後續班別限制"

    override fun evaluate(context: RuleContext, parameters: Map<String, String>): RuleViolation? {
        // 找出代表「值班(夜)」和「放假」的班別
        val shiftTypeMap = context.allShiftTypes.associateBy { it.id }
        val nightShift = context.allShiftTypes.find { it.name == "值班(夜)" } ?: return null
        val offShift = context.allShiftTypes.find { it.shortCode == "OFF" } ?: return null

        // 將班表按日期排序
        val sortedAssignments = context.assignments.entries.sortedBy { it.key.toInt() }

        for (i in 0 until sortedAssignments.size - 1) {
            val currentDay = sortedAssignments[i].key
            val currentShiftId = sortedAssignments[i].value
            val nextShiftId = sortedAssignments[i + 1].value

            // 如果當前班別是「值班(夜)」
            if (currentShiftId == nightShift.id) {
                // 檢查下一個班別是否為「值班(夜)」或「放假」
                if (nextShiftId != nightShift.id && nextShiftId != offShift.id) {
                    val nextShift = shiftTypeMap[nextShiftId]
                    val message = "${context.user.name}: 第${currentDay}天夜班後，隔天不能接 ${nextShift?.name ?: "未知"} 班"
                    return RuleViolation(name, message)
                }
            }
        }
        return null // 沒有違反規則
    }
}