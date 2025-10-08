package stevedaydream.scheduler.domain.repository.scheduling.rules

import stevedaydream.scheduler.domain.scheduling.rules.RuleContext
import stevedaydream.scheduler.domain.scheduling.rules.RuleViolation
import stevedaydream.scheduler.domain.scheduling.rules.SchedulingRule
import java.text.SimpleDateFormat
import java.util.Locale

// ✅ 讓這個 class 實作 SchedulingRule 介面
class MinRestBetweenShiftsRule : SchedulingRule {
    // ✅ 實作介面要求的 name 屬性
    override val name: String = "輪班間隔需大於N小時"

    // ✅ 實作介面要求的 evaluate 函式
    override fun evaluate(context: RuleContext, parameters: Map<String, String>): RuleViolation? {
        val minHours = parameters["minHours"]?.toIntOrNull() ?: 11
        val shiftTypeMap = context.allShiftTypes.associateBy { it.id }
        val sortedShifts = context.assignments.entries.sortedBy { it.key.toInt() }

        for (i in 0 until sortedShifts.size - 1) {
            val currentShiftId = sortedShifts[i].value
            val nextShiftId = sortedShifts[i + 1].value

            val currentShift = shiftTypeMap[currentShiftId] ?: continue
            val nextShift = shiftTypeMap[nextShiftId] ?: continue

            // 如果其中一個是休假，就跳過
            if (currentShift.shortCode == "OFF" || nextShift.shortCode == "OFF") continue

            try {
                // 計算兩個班次結束和開始之間的時間差
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val currentEndTime = timeFormat.parse(currentShift.endTime)!!.time
                val nextStartTime = timeFormat.parse(nextShift.startTime)!!.time

                var diffMillis = nextStartTime - currentEndTime

                // 處理跨日班次 (例如 17:00-01:00)
                if (diffMillis < 0) {
                    diffMillis += 24 * 60 * 60 * 1000 // 加一天
                }

                val diffHours = diffMillis / (60 * 60 * 1000)

                if (diffHours < minHours) {
                    val day = sortedShifts[i].key
                    return RuleViolation(name, "${context.user.name}: 第${day}天與隔天班次間隔僅 ${diffHours} 小時")
                }

            } catch (e: Exception) {
                // 時間格式錯誤，忽略此規則
                continue
            }
        }
        return null
    }
}