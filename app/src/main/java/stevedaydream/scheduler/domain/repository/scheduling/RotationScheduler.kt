package stevedaydream.scheduler.domain.scheduling

import stevedaydream.scheduler.data.model.Group // 假設 Group 包含 userOrder 和 rotationState
import stevedaydream.scheduler.data.model.ShiftType
import stevedaydream.scheduler.data.model.User
import stevedaydream.scheduler.util.DateUtils
import java.util.Calendar
import javax.inject.Inject // ✅ 1. 引入 Inject

// RotationRuleConfig data class 保持不變
data class RotationRuleConfig(
    val shiftTypeId: String,
    val daysOfWeek: Set<Int> // 0=週日, 1=週一...6=週六
)

// RotationCalculationResult data class 保持不變
data class RotationCalculationResult(
    val preScheduledRotations: Map<String, Map<String, String>>, // Map<UserId, Map<Day, ShiftId>>
    val nextRotationState: Map<String, Int> // Map<ShiftTypeId, nextUserIndex>
)

// ▼▼▼▼▼▼▼▼▼▼▼▼ 修改開始 ▼▼▼▼▼▼▼▼▼▼▼▼
class RotationScheduler @Inject constructor() { // ✅ 2. 加入 @Inject constructor()
// ▲▲▲▲▲▲▲▲▲▲▲▲ 修改結束 ▲▲▲▲▲▲▲▲▲▲▲▲

    /**
     * 計算指定月份的輪替預排班表
     * (方法內部保持不變)
     */
    fun calculateRotations(
        month: String,
        orderedUsers: List<User>,
        shiftTypes: List<ShiftType>,
        rotationRules: Map<String, RotationRuleConfig>, // Key: ShiftTypeId
        initialRotationState: Map<String, Int> // Key: ShiftTypeId, Value: index in orderedUsers
    ): RotationCalculationResult {
        // ... (方法實作保持不變) ...
        val preScheduledRotations = mutableMapOf<String, MutableMap<String, String>>() // UserId -> Day -> ShiftId
        val currentRotationIndices = initialRotationState.toMutableMap() // 複製一份初始狀態來追蹤當前索引
        val dates = DateUtils.getDatesInMonth(month)
        val numUsers = orderedUsers.size

        if (numUsers == 0) {
            // 沒有使用者，直接返回空結果
            return RotationCalculationResult(emptyMap(), initialRotationState)
        }

        // 查找 N, D, S 班別 ID (用於硬性規則檢查)
        val nShiftId = shiftTypes.find { it.name == "值班(夜)" }?.id
        val dShiftId = shiftTypes.find { it.name == "值班(日)" }?.id
        val sShiftId = shiftTypes.find { it.name == "白班" }?.id

        // --- 遍歷日期進行輪替排班 ---
        dates.forEach { date ->
            val day = date.split("-").last()
            val dayOfWeek = DateUtils.getDayOfWeek(date) // 0=週日, 1=週一...

            // 找出今天需要輪替的規則 (按班別 ID 排序，假設 N 優先於 D)
            val rulesForToday = rotationRules.values
                .filter { dayOfWeek in it.daysOfWeek }
                .sortedBy { rule ->
                    when (rule.shiftTypeId) {
                        nShiftId -> 0 // N 班優先
                        dShiftId -> 1 // D 班次之
                        else -> 2 // 其他
                    }
                }

            // 儲存當天已指派的使用者，防止同一人被排多個輪替班
            val assignedUserToday = mutableSetOf<String>()

            // 依優先級處理今天的輪替規則
            rulesForToday.forEach { rule ->
                val shiftIdToAssign = rule.shiftTypeId
                var currentIndex = currentRotationIndices.getOrPut(shiftIdToAssign) { 0 } // 取得或初始化索引
                var candidateFound = false
                var attempts = 0 // 防止無限循環

                // 尋找下一個符合條件的輪值者
                while (!candidateFound && attempts < numUsers) {
                    val candidateUser = orderedUsers[currentIndex]

                    // 檢查條件：
                    // 1. 此人今天尚未被其他輪替班指派
                    // 2. 符合硬性規則 (例如 N->S/D)
                    val isAvailable = candidateUser.id !in assignedUserToday
                    val passesHardRule = checkRotationHardRules(
                        userId = candidateUser.id,
                        day = day,
                        shiftIdToAssign = shiftIdToAssign,
                        assignmentsSoFar = preScheduledRotations, // 只基於已排好的輪替班檢查
                        nShiftId = nShiftId,
                        dShiftId = dShiftId,
                        sShiftId = sShiftId
                    )

                    if (isAvailable && passesHardRule) {
                        // 找到人選！
                        // 記錄排班
                        preScheduledRotations.getOrPut(candidateUser.id) { mutableMapOf() }[day] = shiftIdToAssign
                        assignedUserToday.add(candidateUser.id) // 標記此人今天已排
                        candidateFound = true

                        // 更新該班別的下一個輪替索引 (循環)
                        currentRotationIndices[shiftIdToAssign] = (currentIndex + 1) % numUsers
                    } else {
                        // 換下一個人試試
                        currentIndex = (currentIndex + 1) % numUsers
                        attempts++
                    }
                } // end while finding candidate

                // 如果嘗試了所有人選都找不到（極少情況，可能規則衝突或全部不符），
                // 則該班別今天的輪替失敗，索引停留在原地，等待下一次輪替。
                // 可以在這裡加入警告日誌
                if (!candidateFound) {
                    println("⚠️ 警告：日期 $date 的班別 $shiftIdToAssign 找不到符合輪替條件的人員。")
                }

            } // end forEach rule for today
        } // end forEach date

        return RotationCalculationResult(preScheduledRotations, currentRotationIndices)
    }


    /**
     * 檢查輪替排班時的硬性規則 (簡化版，只檢查前一天)
     * (方法內部保持不變)
     */
    private fun checkRotationHardRules(
        userId: String, day: String, shiftIdToAssign: String,
        assignmentsSoFar: Map<String, Map<String, String>>, // Map<UserId, Map<Day, ShiftId>>
        nShiftId: String?, dShiftId: String?, sShiftId: String?
    ): Boolean {
        // ... (方法實作保持不變) ...
        // 檢查前一天
        val yesterdayInt = day.toIntOrNull()?.minus(1) ?: 0
        if (yesterdayInt > 0) {
            val yesterdayKey = String.format("%02d", yesterdayInt)
            val yesterdayShiftId = assignmentsSoFar[userId]?.get(yesterdayKey)

            // N 班後面不能接 D 或 S
            if (yesterdayShiftId == nShiftId && (shiftIdToAssign == dShiftId || shiftIdToAssign == sShiftId)) {
                return false
            }
            // (可以加入其他硬性規則，如 D 班後不能接 N 等)
        }
        return true
    }
}