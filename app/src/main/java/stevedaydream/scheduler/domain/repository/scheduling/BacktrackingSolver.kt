// ▼▼▼▼▼▼▼▼▼▼▼▼ 新檔案開始 ▼▼▼▼▼▼▼▼▼▼▼▼
package stevedaydream.scheduler.domain.scheduling

import android.util.Log
import stevedaydream.scheduler.data.model.*
import stevedaydream.scheduler.domain.scheduling.rules.RuleContext
import stevedaydream.scheduler.domain.scheduling.rules.RuleViolation
// 保持 import data class SchedulingRule
import stevedaydream.scheduler.data.model.SchedulingRule as SchedulingRuleData
// 引入 interface SchedulingRule
import stevedaydream.scheduler.domain.scheduling.rules.SchedulingRule as SchedulingRuleInterface


/**
 * 使用回溯法 (Backtracking) 的排班求解器
 */
class BacktrackingSolver(
    private val users: List<User>,
    private val numDays: Int,
    private val dates: List<String>, // yyyy-MM-dd 格式
    private val initialSchedule: Map<String, Map<String, String>>, // 初始班表 (含輪班/偏好) Map<UserId, Map<Day(01-31), ShiftId>>
    private val initialQuotas: Map<String, Map<String, Int>>, // 初始配額 Map<UserId, Map<ShiftId, Count>>
    private val shiftTypes: List<ShiftType>,
    private val dbRules: List<SchedulingRuleData>, // DB來的啟用規則
    private val ruleEngine: RuleEngine // 注入現有的 RuleEngine
) {
    private val TAG = "BacktrackingSolver"

    // --- 班別常數 ---
    private val EMPTY = "." // 空格

    // --- 內部狀態 ---
    // 可變的班表狀態 Map<UserId, MutableMap<Day(01-31), ShiftId>>
    private val schedule: MutableMap<String, MutableMap<String, String>> = initialSchedule.mapValues { it.value.toMutableMap() }.toMutableMap()
    // 可變的剩餘配額狀態 Map<UserId, MutableMap<ShiftId, Count>>
    private val remainingQuotas: MutableMap<String, MutableMap<String, Int>> = initialQuotas.mapValues { it.value.toMutableMap() }.toMutableMap()
    // 班別嘗試順序 (可以從外部傳入或設定)
    private val shiftTryOrder: List<String> = shiftTypes.filter { it.shortCode != "OFF" }.map { it.id } + (shiftTypes.find { it.shortCode == "OFF" }?.id ?: "")

    private var solutionFound = false
    private var finalSchedule: Map<String, Map<String, String>>? = null // 儲存找到的解

    /**
     * 啟動求解過程
     * @return 求解成功則返回 true，否則 false
     */
    fun solve(): Boolean {
        Log.d(TAG, "--- Backtracking Solver 啟動 ---")
        solutionFound = false // 重置狀態
        finalSchedule = null
        if (users.isEmpty() || numDays == 0) {
            Log.e(TAG, "使用者列表或天數為空，無法求解")
            return false
        }
        val success = backtrackSolve(0, 0) // 從 (第0天, 第0位user) 開始
        if (success) {
            Log.d(TAG, "--- 成功找到解！ ---")
            // printSchedule() // 可以取消註解來印出班表
            finalSchedule = schedule.mapValues { it.value.toMap() } // 將 MutableMap 轉回不可變 Map
        } else {
            Log.w(TAG, "--- 未找到可行解 ---")
        }
        return success
    }

    /**
     * 獲取求解後的班表 (如果成功)
     */
    fun getSolution(): Map<String, Map<String, String>>? {
        return finalSchedule
    }

    /**
     * 核心回溯遞迴函式
     * 嘗試填充班表格子 (dateIndex, userIndex)
     * @param dateIndex 當前處理的日期索引 (0 to numDays-1)
     * @param userIndex 當前處理的使用者索引 (0 to users.size-1)
     * @return 如果找到解則返回 true
     */
    private fun backtrackSolve(dateIndex: Int, userIndex: Int): Boolean {
        // --- 找到下一個要處理的格子 ---
        var nextDateIndex = dateIndex
        var nextUserIndex = userIndex

        // 如果 userIndex 超出範圍，換到下一天，userIndex 從 0 開始
        if (nextUserIndex >= users.size) {
            nextDateIndex++
            nextUserIndex = 0
        }

        // --- 遞迴終止條件 ---
        // 如果 dateIndex 超出範圍，表示所有格子都填完了
        if (nextDateIndex >= numDays) {
            solutionFound = true // 標記找到解
            return true // 找到解，返回 True
        }

        // --- 取得當前格子對應的日期和使用者 ---
        val currentDate = dates[nextDateIndex].split("-").last() // "01", "02", ...
        val currentUser = users[nextUserIndex]

        // --- 如果格子已被預填 (輪班/休假/偏好)，直接跳到下一個格子 ---
        if (schedule[currentUser.id]?.get(currentDate) != null && schedule[currentUser.id]?.get(currentDate) != EMPTY) {
            return backtrackSolve(nextDateIndex, nextUserIndex + 1)
        }

        // --- 嘗試填充當前空格 ---
        // (可以加入讀取偏好的邏輯，優先嘗試偏好的班別)
        for (shiftId in shiftTryOrder) {
            if (shiftId.isBlank()) continue // 跳過空的 shiftId (例如 OFF 不存在時)

            // 檢查這個班別是否合法 (配額 + 規則)
            if (isValid(currentUser.id, currentDate, shiftId)) {
                // --- 合法 -> 嘗試填入 (Do) ---
                schedule.getOrPut(currentUser.id) { mutableMapOf() }[currentDate] = shiftId
                // 扣減配額 (如果是配額班別)
                remainingQuotas[currentUser.id]?.let { quotas ->
                    if (quotas.containsKey(shiftId)) {
                        quotas[shiftId] = (quotas[shiftId] ?: 1) - 1
                    }
                }
                // Log.d(TAG, "嘗試: User ${currentUser.name}, Day $currentDate, Shift ${shiftTypes.find{it.id==shiftId}?.shortCode}")

                // --- 遞迴呼叫，處理下一個格子 ---
                if (backtrackSolve(nextDateIndex, nextUserIndex + 1)) {
                    return true // 如果後續遞迴成功找到解，直接返回 true
                }

                // --- 後續遞迴失敗 -> 撤銷操作 (Undo) ---
                // Log.d(TAG, "撤銷: User ${currentUser.name}, Day $currentDate, Shift ${shiftTypes.find{it.id==shiftId}?.shortCode}")
                schedule[currentUser.id]?.set(currentDate, EMPTY) // 恢復空格
                // 還原配額
                remainingQuotas[currentUser.id]?.let { quotas ->
                    if (quotas.containsKey(shiftId)) {
                        quotas[shiftId] = (quotas[shiftId] ?: 0) + 1
                    }
                }
            } else {
                // Log.d(TAG, "無效: User ${currentUser.name}, Day $currentDate, Shift ${shiftTypes.find{it.id==shiftId}?.shortCode}")
            }
        } // --- 結束嘗試所有班別 ---

        // --- 如果當前格子所有班別都嘗試失敗 ---
        return false // 返回 false，觸發上一層的回溯
    }

    /**
     * 檢查將 shift 分配給 user 在 day 是否合法
     * 1. 檢查配額
     * 2. 檢查硬性規則 (使用當前 schedule 狀態)
     * @param userId 使用者 ID
     * @param day 日期 ("01", "02", ...)
     * @param shiftId 要檢查的班別 ID
     * @return 是否合法
     */
    private fun isValid(userId: String, day: String, shiftId: String): Boolean {
        // 1. 檢查配額
        val userQuotas = remainingQuotas[userId]
        if (userQuotas != null && userQuotas.containsKey(shiftId)) {
            if ((userQuotas[shiftId] ?: 0) <= 0) {
                // Log.d(TAG, "配額檢查失敗: $userId 的 $shiftId 配額不足")
                return false
            }
        }
        // else: 如果 shiftId 不是配額控管的班別，跳過配額檢查

        // 2. 檢查硬性規則 (需要 RuleEngine 支援傳入當前狀態)
        //    假設 RuleEngine 的 validate 方法或其內部規則實現可以處理 Map<String, String>
        //    這裡需要確保 RuleEngine 的實現是正確的
        val currentUser = users.find { it.id == userId } ?: return false // 找不到 User
        val assignmentsForUser = schedule[userId] ?: emptyMap()

        // 創建一個模擬的 Assignment 物件供 RuleEngine 使用
        // 注意：這裡只包含了單一使用者的班表，如果規則需要全局資訊，RuleEngine 需要調整
        val simulatedAssignment = Assignment(userId = userId, dailyShifts = assignmentsForUser + mapOf(day to shiftId))

        // 呼叫 RuleEngine 進行驗證 (只檢查硬性規則)
        val hardRules = dbRules.filter { it.ruleType == "hard" && it.isEnabled }
        val violations = ruleEngine.validate(currentUser, simulatedAssignment, shiftTypes, hardRules)

        if (violations.isNotEmpty()) {
            // Log.d(TAG, "硬性規則檢查失敗: $userId 在 $day 日排 $shiftId -> ${violations.joinToString { it.message }}")
            return false
        }

        return true
    }

    /**
     * (輔助) 打印當前班表狀態 (用於 Debug)
     */
    private fun printSchedule() {
        val header = " ".repeat(10) + dates.joinToString(" ") { it.split("-").last() }
        Log.d(TAG, header)
        Log.d(TAG, "-".repeat(header.length))
        users.forEach { user ->
            val userRow = schedule[user.id]
            val rowStr = dates.map { date ->
                val day = date.split("-").last()
                val shiftId = userRow?.get(day) ?: EMPTY
                shiftTypes.find { it.id == shiftId }?.shortCode ?: shiftId
            }.joinToString("  ")
            Log.d(TAG, "${user.name.padEnd(8)} | $rowStr")
        }
    }
}
// ▲▲▲▲▲▲▲▲▲▲▲▲ 新檔案結束 ▲▲▲▲▲▲▲▲▲▲▲▲
